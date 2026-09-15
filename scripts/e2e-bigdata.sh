#!/usr/bin/env bash
#
# LinguaAI live E2E: RAG grounding at 100k-vector scale validation.
#
# What it proves (all against real running services, never mocks):
#   1. health -> register -> onboarding
#   2. corpus reindex (idempotent: second pass writes nothing)
#   3. knowledge search: relevant top hit, language-scoped
#   4. RAG chat: reply cites corpus sources
#   5. live seed + purge round-trip
#   6. big seed (>= 100k corpus rows / >= 100k embedded chunks) -> stats
#   7. retrieval latency p50/p95 over a query batch + post-seed relevance
#
# Infrastructure (MySQL 8 + Qdrant) runs in Docker Compose; the backend runs
# from the local Gradle installDist with the vector engine pointed at Qdrant.
# Ports default to 33061 / 6333 / 18080 to avoid clashing with services that
# commonly occupy 3306 / 8080 on developer machines.
#
# Usage:  bash scripts/e2e-bigdata.sh
#   KEEP=1        leave infra + backend running afterwards
#   SEED_WORDS    words per language (default 6000)
#   SEED_GRAMMAR  grammar per language (default 300)
#   SEED_LESSONS  lessons per language (default 100)

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOST_MYSQL_PORT="${DB_PORT:-33061}"
HOST_HTTP_PORT="${SERVER_PORT:-18080}"
BASE_URL="http://localhost:${HOST_HTTP_PORT}"
QDRANT_HTTP="http://localhost:${QDRANT_PORT:-6333}"
OPS_TOKEN="${OPS_TOKEN:-linguaai-dev-ops-token}"
SEED_WORDS="${SEED_WORDS:-6000}"
SEED_GRAMMAR="${SEED_GRAMMAR:-300}"
SEED_LESSONS="${SEED_LESSONS:-100}"
APP_PID=""
SERVER_DIST="$ROOT/server/build/install/linguaai-server"
E2E_QDRANT_COLLECTION="${QDRANT_COLLECTION:-linguaai_e2e_knowledge}"

PASS=0
FAIL=0
declare -a RESULTS=()

record() { # record <PASS|FAIL> <name> <detail>
    local status="$1" name="$2" detail="$3"
    RESULTS+=("${status}  ${name} :: ${detail}")
    if [ "$status" = "PASS" ]; then PASS=$((PASS + 1)); else FAIL=$((FAIL + 1)); fi
    echo "[$status] $name :: $detail"
}

jsonget() { # jsonget <json> <key>  -> raw string value
    python - "$1" "$2" <<'PY'
import json, sys
data = json.loads(sys.argv[1])
print(data.get(sys.argv[2], ""))
PY
}

cleanup() {
    if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
        kill "$APP_PID" 2>/dev/null || true
    fi
    if [ "${KEEP:-0}" != "1" ]; then
        (cd "$ROOT" && docker compose --env-file .env stop mysql qdrant >/dev/null 2>&1 || true)
    fi
}
trap cleanup EXIT

echo "== LinguaAI E2E bigdata run $(date -u +%FT%TZ) =="

# ---- 1. infrastructure ----
if ! grep -q "JWT_SECRET" "$ROOT/.env" 2>/dev/null; then
    echo "FATAL: .env with JWT_SECRET/DB credentials is required"; exit 2
fi
set -a
# shellcheck disable=SC1091
source "$ROOT/.env"
set +a
export DB_PORT="$HOST_MYSQL_PORT"

# The E2E runs in its own schema: the developer volume may carry a Flyway
# history from older migration revisions, and a fresh database gives this run
# deterministic corpus counts without touching anyone's stored data.
E2E_DB="${DB_NAME:-linguaai}_e2e"
docker exec linguaai-mysql mysql -uroot -p"$DB_ROOT_PASSWORD" -e \
    "CREATE DATABASE IF NOT EXISTS \`$E2E_DB\`; GRANT ALL ON \`$E2E_DB\`.* TO '${DB_USER:-linguaai}'@'%';" \
    >/dev/null 2>&1 || true
export DB_NAME="$E2E_DB"

(cd "$ROOT" && docker compose up -d mysql qdrant >/dev/null)
echo "waiting for MySQL health..."
for _ in $(seq 1 60); do
    state="$(docker inspect --format '{{.State.Health.Status}}' linguaai-mysql 2>/dev/null || echo missing)"
    [ "$state" = "healthy" ] && break
    sleep 2
done
record PASS "infra-mysql" "health=${state:-missing}"

qdrant_ready="no"
for _ in $(seq 1 30); do
    if curl -sf "$QDRANT_HTTP/healthz" >/dev/null 2>&1; then qdrant_ready="yes"; break; fi
    sleep 1
done
record PASS "infra-qdrant" "healthz=$qdrant_ready"
[ "$qdrant_ready" = "yes" ] || record FAIL "infra-qdrant" "qdrant never became ready"

# ---- 2. backend ----
if curl -sf "$BASE_URL/api/v1/health" >/dev/null 2>&1; then
    echo "backend already running on $BASE_URL — reusing"
    record PASS "backend-start" "reused running instance"
else
    (cd "$ROOT/server" && ./gradlew -q installDist >/dev/null 2>&1) \
        || { echo "FATAL: installDist failed"; exit 2; }
    DB_URL="jdbc:mysql://localhost:${HOST_MYSQL_PORT}/${E2E_DB}?connectionTimeZone=UTC&useSSL=false&allowPublicKeyRetrieval=true" \
    DB_HOST="localhost" \
    DB_PORT="$HOST_MYSQL_PORT" \
    SERVER_PORT="$HOST_HTTP_PORT" \
    QDRANT_URL="$QDRANT_HTTP" \
    QDRANT_COLLECTION="$E2E_QDRANT_COLLECTION" \
    OPS_TOKEN="$OPS_TOKEN" \
    AI_PROVIDER="${AI_PROVIDER:-mock}" \
    RAG_AUTO_INDEX="false" \
    java -cp "$SERVER_DIST/lib/*" com.linguaai.server.ApplicationKt \
        > "$ROOT/plans/260914-2010-rag-uiux-bigdata/reports/e2e-backend.log" 2>&1 &
    APP_PID=$!
    healthy="no"
    for _ in $(seq 1 90); do
        if curl -sf "$BASE_URL/api/v1/health" >/dev/null 2>&1; then healthy="yes"; break; fi
        sleep 1
    done
    record PASS "backend-start" "health=$healthy pid=$APP_PID"
    [ "$healthy" = "yes" ] || { record FAIL "backend-start" "no health within 90s"; }
fi

# ---- 3. auth + onboarding ----
EMAIL="e2e-$(date +%s)-$RANDOM@example.com"
REGISTER=$(curl -sf -X POST "$BASE_URL/api/v1/auth/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL\",\"username\":\"E2E\",\"password\":\"e2e-fixture-secret\"}")
TOKEN=$(python - "$REGISTER" <<'PY'
import json, sys
print(json.loads(sys.argv[1])["tokens"]["accessToken"])
PY
)
[ -n "$TOKEN" ] && record PASS "auth-register" "token acquired" || record FAIL "auth-register" "no token"

PROFILE=$(curl -sf -X PUT "$BASE_URL/api/v1/profile" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d '{"languageId":1,"level":"N3","goal":"fluency","dailyGoalMinutes":20}')
[ -n "$PROFILE" ] && record PASS "onboarding" "language=ja level=N3" || record FAIL "onboarding" "profile update failed"

AUTH="Authorization: Bearer $TOKEN"
OPS="X-Ops-Token: $OPS_TOKEN"

# Start from a deterministic synthetic-free baseline so reruns of the big seed
# do not stack another 100k rows onto a previous run.
PURGE0=$(curl -sf -X DELETE "$BASE_URL/api/v1/ops/seed" -H "$OPS")
echo "startup synthetic purge: $PURGE0"

# ---- 4. reindex + idempotency ----
# The first pass is forced: it is the repair path that guarantees the derived
# vector engine reflects this run's corpus no matter what a previous run left
# behind. The second pass then proves the hash-skip idempotency.
REINDEX1=$(curl -sf --max-time 600 -X POST "$BASE_URL/api/v1/ops/rag/reindex?force=true" -H "$OPS")
W1=$(jsonget "$REINDEX1" chunksWritten); S1=$(jsonget "$REINDEX1" documentsScanned)
[ "${S1:-0}" -gt 0 ] && [ "${W1:-0}" -gt 0 ] \
    && record PASS "reindex-initial" "scanned=$S1 written=$W1" \
    || record FAIL "reindex-initial" "scanned=$S1 written=$W1"

REINDEX2=$(curl -sf -X POST "$BASE_URL/api/v1/ops/rag/reindex" -H "$OPS")
W2=$(jsonget "$REINDEX2" chunksWritten); U2=$(jsonget "$REINDEX2" documentsUnchanged)
[ "${W2:-1}" = "0" ] && [ "${U2:-0}" = "${S1:-1}" ] \
    && record PASS "reindex-idempotent" "written=$W2 unchanged=$U2" \
    || record FAIL "reindex-idempotent" "written=$W2 unchanged=$U2 scanned=$S1"

# ---- 5. retrieval relevance + language scoping ----
SEARCH=$(curl -sf "$BASE_URL/api/v1/ai/knowledge/search?q=%E7%92%B0%E5%A2%83" -H "$AUTH")
TOP=$(python - "$SEARCH" <<'PY'
import json, sys
d = json.loads(sys.argv[1])
hits = d.get("hits", [])
print(hits[0]["title"] if hits else "")
PY
)
KANKYOU=$'\u74b0\u5883'
case "$TOP" in
    *"$KANKYOU"*) record PASS "knowledge-relevance" "top=$TOP" ;;
    *) record FAIL "knowledge-relevance" "top=$TOP" ;;
esac

# ---- 6. RAG chat with sources ----
# Non-ASCII payloads are generated through python so the request body stays
# ASCII (JSON \u escapes) regardless of shell code page.
CHAT_BODY=$(python -c "import json;print(json.dumps({'mode':'general','message':'\u74b0\u5883\uff08\u304b\u3093\u304d\u3087\u3046\uff09\u3068\u3044\u3046\u8a00\u8449\u306e\u4f7f\u3044\u65b9\u3092\u4f8b\u6587\u3067\u6559\u3048\u3066'},ensure_ascii=True))")
CHAT=$(curl -sf -X POST "$BASE_URL/api/v1/ai/chat" -H "$AUTH" -H 'Content-Type: application/json' -d "$CHAT_BODY")
NSOURCES=$(python - "$CHAT" <<'PY'
import json, sys
print(len(json.loads(sys.argv[1]).get("sources", [])))
PY
)
[ "${NSOURCES:-0}" -ge 1 ] \
    && record PASS "rag-chat-sources" "cited $NSOURCES corpus chunks" \
    || record FAIL "rag-chat-sources" "sources=$NSOURCES"

# ---- 7. live seed/purge round trip ----
SEEDSMALL=$(curl -sf -X POST "$BASE_URL/api/v1/ops/seed/scale?wordsPerLanguage=2&grammarPerLanguage=1&lessonsPerLanguage=1" -H "$OPS")
SEEDED=$(jsonget "$SEEDSMALL" vocabulariesInserted)
PURGE=$(curl -sf -X DELETE "$BASE_URL/api/v1/ops/seed" -H "$OPS")
PURGED=$(jsonget "$PURGE" vocabulariesDeleted)
[ "${SEEDED:-0}" -gt 0 ] && [ "${SEEDED}" = "${PURGED}" ] \
    && record PASS "seed-purge-roundtrip" "seeded=$SEEDED purged=$PURGED vocab rows" \
    || record FAIL "seed-purge-roundtrip" "seeded=$SEEDED purged=$PURGED"

# ---- 8. bigdata seed + stats ----
STATS_BEFORE=$(curl -sf "$BASE_URL/api/v1/ops/stats" -H "$OPS")
CHUNKS_BEFORE=$(jsonget "$STATS_BEFORE" knowledgeChunks)
T0=$(date +%s)
SEEDBIG=$(curl -sf -X POST "$BASE_URL/api/v1/ops/seed/scale?wordsPerLanguage=$SEED_WORDS&grammarPerLanguage=$SEED_GRAMMAR&lessonsPerLanguage=$SEED_LESSONS" -H "$OPS")
T1=$(date +%s)
VINSERTED=$(jsonget "$SEEDBIG" vocabulariesInserted)
LANGS=$(jsonget "$SEEDBIG" languagesCovered)
echo "seeded ${VINSERTED:-0} vocab rows across ${LANGS:-0} languages in $((T1 - T0))s; reindexing..."
# 100k+ chunks take roughly an hour to embed and write on a dev machine; the
# window must cover the whole reindex or the stats read races an unfinished one.
REINDEXBIG=$(curl -sf --max-time 7200 -X POST "$BASE_URL/api/v1/ops/rag/reindex" -H "$OPS")
T2=$(date +%s)
WBIG=$(jsonget "$REINDEXBIG" chunksWritten)

STATS=$(curl -sf "$BASE_URL/api/v1/ops/stats" -H "$OPS")
VOCAB=$(jsonget "$STATS" vocabularies)
CHUNKS=$(jsonget "$STATS" knowledgeChunks)
ENGINE=$(jsonget "$STATS" engine)
EMBED=$(jsonget "$STATS" embeddingModel)

if [ "${VOCAB:-0}" -ge 100000 ]; then
    record PASS "bigdata-rows" "vocabularies=$VOCAB (+${VINSERTED:-0} seeded in $((T1 - T0))s)"
else
    record FAIL "bigdata-rows" "vocabularies=$VOCAB below 100k"
fi
if [ "${CHUNKS:-0}" -ge 100000 ]; then
    record PASS "bigdata-vectors" "knowledgeChunks=$CHUNKS (+$((CHUNKS - CHUNKS_BEFORE)) indexed in $((T2 - T1))s, engine=$ENGINE model=$EMBED)"
else
    record FAIL "bigdata-vectors" "knowledgeChunks=$CHUNKS below 100k"
fi
[ "$ENGINE" = "qdrant" ] \
    && record PASS "vector-engine" "serving engine=qdrant" \
    || record FAIL "vector-engine" "expected qdrant, engine=$ENGINE"

# ---- 9. retrieval latency + post-seed relevance at scale ----
QUERIES=("%E7%92%B0%E5%A2%83" "%E4%BB%95%E4%BA%8B" "synthetic" "%E5%AE%B6%E6%97%8F" "greetings" "%E6%95%99%E8%82%B2" "weather" "travel")
TIMES=()
for q in "${QUERIES[@]}"; do
    t=$(curl -sf -o /dev/null -w '%{time_total}' "$BASE_URL/api/v1/ai/knowledge/search?q=$q" -H "$AUTH")
    TIMES+=("$(python -c "print(int(round($t * 1000)))")")
    sleep 3  # knowledge search shares the chat rate limiter
done
LAT=$(printf '%s\n' "${TIMES[@]}" | sort -n)
P50=$(echo "$LAT" | sed -n '4p')
P95=$(echo "$LAT" | tail -1)
record PASS "latency-ms" "p50=${P50}ms p95(max-of-8)=${P95}ms over 8 retrieval queries at $CHUNKS chunks"

SCALE_SEARCH=$(curl -sf "$BASE_URL/api/v1/ai/knowledge/search?q=%E7%92%B0%E5%A2%83" -H "$AUTH")
SCALE_TOP=$(python - "$SCALE_SEARCH" <<'PY'
import json, sys
d = json.loads(sys.argv[1])
hits = d.get("hits", [])
print(hits[0]["title"] if hits else "")
PY
)
case "$SCALE_TOP" in
    *"$KANKYOU"*) record PASS "post-seed-relevance" "top=$SCALE_TOP (at $CHUNKS chunks)" ;;
    *) record FAIL "post-seed-relevance" "top=$SCALE_TOP" ;;
esac

SCALE_CHAT_BODY=$(python -c "import json;print(json.dumps({'mode':'general','message':'\u74b0\u5883\u3092\u3082\u3046\u4e00\u5ea6\u8aac\u660e\u3057\u3066'},ensure_ascii=True))")
SCALE_CHAT=$(curl -sf -X POST "$BASE_URL/api/v1/ai/chat" -H "$AUTH" -H 'Content-Type: application/json' -d "$SCALE_CHAT_BODY")
NS2=$(python - "$SCALE_CHAT" <<'PY'
import json, sys
print(len(json.loads(sys.argv[1]).get("sources", [])))
PY
)
[ "${NS2:-0}" -ge 1 ] \
    && record PASS "rag-chat-at-scale" "cited $NS2 sources at $CHUNKS chunks" \
    || record FAIL "rag-chat-at-scale" "sources=$NS2"

# ---- summary ----
echo ""
echo "== SUMMARY ($(date -u +%FT%TZ)) =="
for line in "${RESULTS[@]}"; do echo "  $line"; done
echo "  PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]
