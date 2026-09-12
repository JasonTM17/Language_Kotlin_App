import io
import os
import sys

BASE = os.path.join("src", "main", "kotlin", "com", "linguaai", "server")

# (relative file, constant block, list of (old, new) replacements)
JOBS = [
    (
        "ai/PromptBuilder.kt",
        [
            "",
            "/** Characters kept per line when evicting messages into the rolling summary. */",
            "private const val SUMMARY_LINE_LENGTH = 120",
        ],
        [("message.content.take(120)", "message.content.take(SUMMARY_LINE_LENGTH)")],
    ),
    (
        "db/DatabaseFactory.kt",
        [
            "",
            "/** HikariCP pool size. Sized for a single instance; scale with it. */",
            "private const val MAX_POOL_SIZE = 10",
        ],
        [("maximumPoolSize = 10", "maximumPoolSize = MAX_POOL_SIZE")],
    ),
    (
        "plugins/ServerPlugins.kt",
        [
            "",
            "/** Short request id for logs — long enough to correlate, short enough to read. */",
            "private const val REQUEST_ID_LENGTH = 8",
        ],
        [("toString().take(8)", "toString().take(REQUEST_ID_LENGTH)")],
    ),
    (
        "repository/AuthRepository.kt",
        [
            "",
            "/** Daily-goal fallback when the learner has not chosen one. */",
            "private const val DEFAULT_DAILY_GOAL_MINUTES = 10",
        ],
        [("request.dailyGoalMinutes ?: 10", "request.dailyGoalMinutes ?: DEFAULT_DAILY_GOAL_MINUTES")],
    ),
    (
        "repository/ContentRepository.kt",
        [
            "",
            "/** Characters of a question prompt kept for list previews. */",
            "private const val PROMPT_PREVIEW_LENGTH = 80",
        ],
        [("[QuizQuestions.prompt].take(80)", "[QuizQuestions.prompt].take(PROMPT_PREVIEW_LENGTH)")],
    ),
    (
        "routes/AiRoutes.kt",
        [
            "",
            "/** Connect timeout for server-to-server calls, in milliseconds. */",
            "private const val CONNECT_TIMEOUT_MILLIS = 5_000L",
        ],
        [("connectTimeoutMillis = 5_000L", "connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS")],
    ),
    (
        "ai/OpenAiCompatibleProvider.kt",
        [
            "",
            "/** HTTP 429, spelled out so the rate-limit branch reads without a lookup. */",
            "private const val HTTP_TOO_MANY_REQUESTS = 429",
        ],
        [("response.status.value == 429", "response.status.value == HTTP_TOO_MANY_REQUESTS")],
    ),
]


def insert_after_imports(lines, block):
    last = -1
    for i, line in enumerate(lines):
        if line.startswith("import ") or line.startswith("import\t"):
            last = i
    if last < 0:
        return None
    return lines[: last + 1] + block + lines[last + 1 :]


for rel, block, replacements in JOBS:
    path = os.path.join(BASE, rel)
    if not os.path.exists(path):
        print("MISSING", rel)
        continue
    src = io.open(path, encoding="utf-8").read()
    lines = src.split("\n")
    new_lines = insert_after_imports(lines, block)
    if new_lines is None:
        print("NO IMPORTS", rel)
        continue
    out = "\n".join(new_lines)
    total = 0
    for old, new in replacements:
        total += out.count(old)
        out = out.replace(old, new)
    io.open(path, "w", encoding="utf-8", newline="\n").write(out)
    print("%-40s replaced %d" % (rel, total))
