# Multilingual vocabulary catalogue

LinguaAI keeps two kinds of vocabulary data in MySQL:

- 900 curated seed rows used by the original learning flows.
- 1,000,000 imported dictionary rows across 17 language codes. Imported rows
  are marked with a `WIKTIONARY...` category so they can be audited or removed
  without touching curated content or learner progress.

The import is a data pipeline, not a claim that the catalogue is a frequency-
ranked curriculum. Source glosses and POS labels are preserved where they fit
the existing vocabulary contract; meanings may be English glosses even when
the headword is in another language. Examples are not invented when the source
does not provide them.

## Source and attribution

The source is [tdulcet/compact-dictionaries](https://github.com/tdulcet/compact-dictionaries),
whose compact JSON dictionaries are derived from Wiktionary through
[Kaikki/Wiktextract](https://kaikki.org/). The source data carries
[CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) and
[GFDL 1.3](https://www.gnu.org/licenses/fdl-1.3.html) licensing obligations.
Downstream distributions must retain attribution, the applicable license
notices and share-alike terms. The repository manifest records the exact raw
URL and SHA-256 for every language file.

The manifest and importer are:

- [`scripts/multilingual-vocabulary-manifest.json`](../../scripts/multilingual-vocabulary-manifest.json)
- [`scripts/import-multilingual-vocabulary.py`](../../scripts/import-multilingual-vocabulary.py)

Raw source files are deliberately not Git assets. They are cached under
`.cache/multilingual-vocabulary`, which is ignored by Git, and the importer
refuses to process a file whose checksum does not match the manifest.

## Quota contract

| Code | Imported rows |
| --- | ---: |
| `en` | 195,085 |
| `es` | 120,000 |
| `fr` | 90,000 |
| `de` | 90,000 |
| `it` | 80,000 |
| `pt` | 70,000 |
| `ru` | 70,000 |
| `zh` | 70,000 |
| `ja` | 60,000 |
| `ko` | 35,000 |
| `tr` | 35,000 |
| `nl` | 24,500 |
| `ar` | 24,269 |
| `th` | 16,507 |
| `vi` | 9,139 |
| `hi` | 7,000 |
| `id` | 3,500 |
| **Total** | **1,000,000** |

Arabic, Thai and Vietnamese are capped at the valid rows available under the
strict no-truncation contract. The released quota is allocated to English,
whose verified source has sufficient valid coverage. This keeps the total exact
without corrupting definitions.

## Run and verify

From the repository root:

```bash
python scripts/import-multilingual-vocabulary.py --self-test
python scripts/import-multilingual-vocabulary.py --dry-run
python scripts/import-multilingual-vocabulary.py --import
```

The import downloads each source once, streams JSONL entries, rejects empty,
whitespace-only or oversized fields, and uses `(language_id, word)` uniqueness
plus `INSERT IGNORE` for safe retries. A repeated `--import` should report zero
new rows once every quota is satisfied.

After the database count audit, run the authenticated ops reindex against the
Compose backend:

```bash
curl -X POST http://localhost:8080/api/v1/ops/rag/reindex?force=true \
  -H 'X-Ops-Token: <local OPS_TOKEN>'
```

Use `force=true` when creating or repairing a fresh Qdrant collection; it
rebuilds the derived vector engine even when the canonical SQL chunks already
match. Keep the token out of shell history and logs. The reindex is derived
state and can be repeated safely; MySQL remains the canonical corpus.

For a large load, set `QDRANT_URL=http://qdrant:6333` and keep
`RAG_AUTO_INDEX=false` while importing. Start the backend, run the importer,
then perform the explicit reindex and verify `/api/v1/ops/stats` reports
`engine: "qdrant"`. Do not use SQL brute-force retrieval as the scale path for
the million-row catalogue.
