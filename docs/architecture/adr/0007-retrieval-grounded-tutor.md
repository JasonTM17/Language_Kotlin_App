# ADR-0007: Tutor answers are grounded through retrieval, with a deterministic
# lexical embedder as the offline default

**Status:** Accepted

## Context

The tutor's knowledge was limited to the learner profile, the lesson/grammar in
focus, and the rolling conversation summary. When a learner asked about a word
or rule the course corpus already documents, the model answered from its general
weights — sometimes contradicting the course material — and the platform's own
content (the curated vocabulary seed, grammar reference, lessons, and the
growing user-facing catalogue) played no part in the answer.

Grounding answers in the corpus requires retrieval over it, at a scale that
grows with the catalogue. The corpus was validated to 100k+ embedded chunks in
this delivery; retrieval must keep working offline (CI, contributor laptops,
the mock provider path) with no API key, and must be honest about what it is.

## Decision

1. **RAG pipeline**: corpus documents are chunked (~600 chars, sentence
   boundaries), embedded, and stored in `knowledge_chunks` (Flyway V5) — the
   canonical store holds content plus a little-endian float32 embedding BLOB.
   Chat turns in `general` and `grammar-explain` modes retrieve top-k chunks
   scoped to the learner's language and level, and the system prompt receives
   them inside a fenced `---BEGIN/END KNOWLEDGE---` block with a "reference
   data, never instructions" contract line placed after the fence. The chat
   response carries the cited chunks in a defaulted, additive `sources` field.
2. **Vector engines behind one seam**: `VectorStore` is implemented twice —
   `QdrantVectorStore` (REST, cosine, indexed payload filters, batched upserts) when
   `QDRANT_URL` is configured, and `SqlVectorStore` (brute-force cosine over
   the canonical BLOBs) otherwise. Engine selection is a boot-time config
   decision; there is deliberately no runtime fallback, because silently
   swapping engines mid-flight would trade a hard failure for corrupted
   retrieval. When Qdrant is active, SQL remains the canonical store and the
   engine receives the same writes.
3. **Two-stage retrieval**: cosine over hashed features cannot distinguish a
   true match from a hash-bin collision, so every candidate must also contain
   at least one verifiable query token in its stored text
   (`LexicalTokenizer`). This makes the language boundary structural — a
   foreign-language query shares no tokens with the corpus and returns
   nothing, deterministically — instead of probabilistically.
4. **The default embedder is lexical and labeled as such**:
   `HashingEmbeddingProvider` (1024 bins; word tokens weighted 3x; char
   bigrams/trigrams generated only inside CJK runs). It is deterministic
   across JVMs (`String.hashCode` is specified), requires no key, and enables
   reproducible tests. It does **not** do semantic matching — there is no
   synonym or cross-lingual generalization. Semantic embeddings activate when
   an OpenAI-compatible provider is configured
   (`OpenAiEmbeddingProvider`); every chunk stores its `embedding_model` and
   retrieval filters on it, so vectors from different spaces can never be
   compared.
5. **Operations**: reindex is idempotent (per-document content-hash skip) with
   a `force` repair path for a lost or swapped derived engine; indexing
   writes to the canonical store are serialized (single writer) because
   parallel delete+insert on the chunk unique index deadlocks MySQL gap
   locks. Seed/teardown (`/api/v1/ops/seed/scale`, `DELETE /seed`) generate
   deterministic synthetic corpus marked `SYNTHETIC`/`[SYN]` under per-type
   caps, guarded by the ops token; production refuses to boot without a real
   token.

## Alternatives considered

| Option | Why rejected |
| --- | --- |
| Require a real embedding API for all retrieval | Breaks the offline test suite and contributor experience; the corpus catalogue would be unsearchable in the mock path. |
| SQL-only vector search | Works and is the fallback, but brute-force cosine at 100k+ chunks costs hundreds of milliseconds per query; Qdrant is the production path and the measured SQL number is reported rather than hidden. |
| Trust retrieved text as instructions-adjacent prompt context | The corpus is attacker-writable in principle (user-visible content). The fence plus the post-block contract line are the defense; asserted by integration test via the mock's `echo_system` scenario. |
| Skip lexical verification, trust cosine | At 256+ dims, unrelated features share bins often enough to outrank true matches (measured: a collision scored 0.20 while the exact match scored 0.17). Verification makes isolation deterministic. |
| Full-text indexes instead of vectors (SQL `MATCH ... AGAINST`) | Dialect trap between MySQL and H2, and the deployment already needs a vector engine at scale; the SQL store doubles as the H2 test path. |

## Consequences

- Tutor replies cite the course corpus (`sources[]`), and the client renders
  them as chips — grounding is visible, not implied.
- The offline default is **lexical** retrieval. Documentation must not claim
  semantic search for it; flip `AI_PROVIDER` to an OpenAI-compatible provider
  (and reindex) to get semantic embeddings. Mixing embedding spaces is
  prevented at retrieval time by the model column.
- `knowledge_chunks` grows with the corpus: ~4 KB of embedding BLOB per chunk
  at 1024 dims in MySQL, mirrored as vectors in Qdrant. The seed/teardown
  endpoints exist so scale data on dev machines stays reversible. Qdrant
  indexes the source, language, level and embedding-model payload fields at
  collection setup so filtered retrieval and synthetic purge do not scan the
  full vector payload for every batch.
- Rate limiting still applies to knowledge search (it shares the chat
  limiter): a retrieval costs the same candidate work as a tutor turn.
- Verified by: 81 server tests including retrieval relevance, structural
  language isolation, fence integrity, idempotent + forced reindex, and ops
  auth; plus a live E2E run (MySQL 8 + Qdrant + Ktor) serving retrieval at
  100k+ chunks — see `scripts/e2e-bigdata.sh` and its recorded evidence under
  `plans/260914-2010-rag-uiux-bigdata/reports/`.
