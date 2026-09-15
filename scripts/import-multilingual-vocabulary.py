#!/usr/bin/env python3
"""Import one million real multilingual dictionary entries into LinguaAI.

The raw source files are external compact JSONL dictionaries. They are cached
outside Git, verified by SHA-256, normalized in a bounded stream, and inserted
through the MySQL container in idempotent batches. No third-party dump is
bundled in the application or downloaded by the app at runtime.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import urllib.parse
import urllib.request

# Manifest URLs may only point at these hosts; anything else is refused before
# a request is built (the manifest itself is untrusted input).
ALLOWED_SOURCE_HOSTS = {"gitlab.com"}
from dataclasses import dataclass
from pathlib import Path
from typing import Iterator


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = ROOT / "scripts" / "multilingual-vocabulary-manifest.json"
DEFAULT_CACHE = ROOT / ".cache" / "multilingual-vocabulary"
BATCH_SIZE = 500
MAX_WORD_LENGTH = 120
MAX_MEANING_LENGTH = 500
MAX_READING_LENGTH = 200
MAX_PRONUNCIATION_LENGTH = 200
SAFE_IDENTIFIER = re.compile(r"^[A-Za-z0-9_]+$")


@dataclass(frozen=True)
class LanguageSpec:
    language_id: int
    code: str
    name: str
    levels: tuple[str, ...]
    quota: int
    source_url: str
    sha256: str


@dataclass(frozen=True)
class VocabularyRow:
    language_id: int
    level: str
    word: str
    reading: str | None
    pronunciation: str | None
    meaning: str
    example: str | None
    example_translation: str | None
    category: str


def load_manifest(path: Path) -> tuple[dict, list[LanguageSpec]]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    languages = [
        LanguageSpec(
            language_id=int(item["id"]),
            code=item["code"],
            name=item["name"],
            levels=tuple(item["levels"]),
            quota=int(item["quota"]),
            source_url=item["sourceUrl"],
            sha256=item["sha256"].lower(),
        )
        for item in raw["languages"]
    ]
    validate_manifest(raw["source"], languages)
    return raw["source"], languages


def validate_manifest(source: dict, languages: list[LanguageSpec]) -> None:
    if not languages:
        raise ValueError("manifest has no languages")
    codes = [spec.code for spec in languages]
    ids = [spec.language_id for spec in languages]
    if len(set(codes)) != len(codes) or len(set(ids)) != len(ids):
        raise ValueError("manifest language codes and ids must be unique")
    if sum(spec.quota for spec in languages) != 1_000_000:
        raise ValueError("manifest quotas must sum to exactly 1,000,000")
    if not source.get("license") or not source.get("repositoryUrl"):
        raise ValueError("manifest must carry source license and repository URL")
    for spec in languages:
        if not SAFE_IDENTIFIER.fullmatch(spec.code):
            raise ValueError(f"unsafe language code: {spec.code}")
        if not spec.levels or spec.quota <= 0:
            raise ValueError(f"invalid level/quota for {spec.code}")
        if not re.fullmatch(r"[0-9a-f]{64}", spec.sha256):
            raise ValueError(f"invalid SHA-256 for {spec.code}")


def download_and_verify(spec: LanguageSpec, cache_dir: Path) -> Path:
    cache_dir.mkdir(parents=True, exist_ok=True)
    destination = cache_dir / f"dictionary-{spec.code}.json"
    if destination.exists() and sha256_file(destination) == spec.sha256:
        return destination

    # SSRF guard: the manifest is external data, so its URLs are validated
    # against an explicit scheme/host allowlist before any request is made.
    parsed = urllib.parse.urlparse(spec.source_url)
    if parsed.scheme != "https" or parsed.hostname not in ALLOWED_SOURCE_HOSTS:
        raise ValueError(
            f"refusing to fetch {spec.source_url!r}: host not in {sorted(ALLOWED_SOURCE_HOSTS)}"
        )

    partial = destination.with_suffix(destination.suffix + ".part")
    request = urllib.request.Request(
        spec.source_url,
        headers={"User-Agent": "LinguaAI multilingual vocabulary importer/1.0"},
    )
    print(f"download {spec.code}: {spec.source_url}", flush=True)
    try:
        with urllib.request.urlopen(request, timeout=120) as response, partial.open("wb") as output:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                output.write(chunk)
    except Exception as urllib_error:
        partial.unlink(missing_ok=True)
        curl = shutil.which("curl.exe") or shutil.which("curl")
        if curl is None:
            raise urllib_error
        print("urllib download failed; retrying with curl", flush=True)
        result = subprocess.run(
            [
                curl,
                "--fail",
                "--location",
                "--retry",
                "3",
                "--retry-delay",
                "2",
                "--connect-timeout",
                "20",
                "--max-time",
                "600",
                "--silent",
                "--show-error",
                "--output",
                str(partial),
                spec.source_url,
            ],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            text=True,
            timeout=660,
        )
        if result.returncode != 0:
            partial.unlink(missing_ok=True)
            detail = (result.stderr or "").strip()[-500:]
            raise RuntimeError(f"curl download failed for {spec.code} (exit {result.returncode}): {detail}")

    actual = sha256_file(partial)
    if actual != spec.sha256:
        partial.unlink(missing_ok=True)
        raise ValueError(f"checksum mismatch for {spec.code}: expected {spec.sha256}, got {actual}")
    partial.replace(destination)
    return destination


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def compact_text(value: object, limit: int) -> str | None:
    if not isinstance(value, str):
        return None
    normalized = " ".join(value.split())
    if not normalized or len(normalized) > limit:
        return None
    return normalized


def normalize_entry(entry: dict, spec: LanguageSpec, ordinal: int) -> VocabularyRow | None:
    raw_word = entry.get("")
    if not isinstance(raw_word, str):
        return None
    word = raw_word.strip()
    if not word or len(word) > MAX_WORD_LENGTH or any(character.isspace() for character in word):
        return None
    raw_definitions = entry.get("d")
    if not isinstance(raw_definitions, list):
        return None
    definitions = [compact_text(item, MAX_MEANING_LENGTH) for item in raw_definitions]
    if any(isinstance(item, str) and item.strip() and value is None for item, value in zip(raw_definitions, definitions)):
        return None
    definitions = [item for item in definitions if item]
    if not definitions:
        return None
    meaning = " / ".join(definitions)
    if len(meaning) > MAX_MEANING_LENGTH:
        return None
    raw_pos = entry.get("p")
    parts_of_speech = [compact_text(item, 24) for item in raw_pos] if isinstance(raw_pos, list) else []
    if isinstance(raw_pos, list) and any(isinstance(item, str) and item.strip() and value is None for item, value in zip(raw_pos, parts_of_speech)):
        return None
    parts_of_speech = [item for item in parts_of_speech if item]
    category = "WIKTIONARY" if not parts_of_speech else "WIKTIONARY:" + ",".join(parts_of_speech)
    if len(category) > 80:
        return None
    forms = entry.get("f")
    reading = None
    if isinstance(forms, list):
        form_values = [compact_text(item, MAX_READING_LENGTH) for item in forms]
        if any(isinstance(item, str) and item.strip() and value is None for item, value in zip(forms, form_values)):
            return None
        reading = next((value for value in form_values if value), None)
    pronunciation = compact_text(entry.get("i"), MAX_PRONUNCIATION_LENGTH)
    raw_pronunciation = entry.get("i")
    if isinstance(raw_pronunciation, str) and raw_pronunciation.strip() and pronunciation is None:
        return None
    return VocabularyRow(
        language_id=spec.language_id,
        level=spec.levels[ordinal % len(spec.levels)],
        word=word,
        reading=reading,
        pronunciation=pronunciation,
        meaning=meaning,
        example=None,
        example_translation=None,
        category=category,
    )


def iter_rows(path: Path, spec: LanguageSpec, stop_after: int | None) -> tuple[Iterator[VocabularyRow], list[int]]:
    stats = [0, 0, 0]

    def generate() -> Iterator[VocabularyRow]:
        seen: set[str] = set()
        with path.open("r", encoding="utf-8") as source:
            for line in source:
                stats[0] += 1
                try:
                    entry = json.loads(line)
                except json.JSONDecodeError:
                    stats[2] += 1
                    continue
                if not isinstance(entry, dict):
                    stats[2] += 1
                    continue
                row = normalize_entry(entry, spec, stats[1])
                if row is None or row.word in seen:
                    stats[2] += 1
                    continue
                seen.add(row.word)
                stats[1] += 1
                yield row
                if stop_after is not None and stats[1] >= stop_after:
                    break

    return generate(), stats


def sql_literal(value: str | None) -> str:
    if value is None:
        return "NULL"
    escaped = (
        value.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\x00", "\\0")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    )
    return f"'{escaped}'"


def row_sql(row: VocabularyRow) -> str:
    values = (
        row.language_id,
        row.level,
        row.word,
        row.reading,
        row.pronunciation,
        row.meaning,
        row.example,
        row.example_translation,
        row.category,
    )
    rendered = [str(values[0])] + [sql_literal(value) for value in values[1:]]
    return "(" + ",".join(rendered) + ")"


class MysqlSink:
    def __init__(self, compose_file: Path, service: str, database: str | None):
        if database is not None and not SAFE_IDENTIFIER.fullmatch(database):
            raise ValueError("database must contain only letters, digits and underscores")
        target = f'"{database}"' if database else '"$MYSQL_DATABASE"'
        shell = (
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=socket -uroot '
            '--default-character-set=utf8mb4 --binary-mode --batch --skip-column-names '
            f'{target}'
        )
        self.process = subprocess.Popen(
            ["docker", "compose", "-f", str(compose_file), "exec", "-T", "mysql", "sh", "-lc", shell],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
        )
        if self.process.stdin is None or self.process.stdout is None:
            raise RuntimeError("failed to open mysql stdin")
        self.stdin = self.process.stdin
        self.stdout = self.process.stdout
        self.stdin.write("SET NAMES utf8mb4;\n")

    def _read_integer(self) -> int:
        line = self.stdout.readline()
        if not line:
            raise RuntimeError("mysql import ended before returning an affected-row count")
        try:
            return int(line.strip())
        except ValueError as error:
            raise RuntimeError(f"mysql returned a non-numeric affected-row count: {line.strip()!r}") from error

    def dictionary_count(self, language_id: int) -> int:
        self.stdin.write(
            "SELECT COUNT(*) FROM vocabularies "
            f"WHERE language_id={language_id} AND category LIKE 'WIKTIONARY%';\n",
        )
        self.stdin.flush()
        return self._read_integer()

    def write_batch(self, rows: list[VocabularyRow]) -> int:
        if not rows:
            return 0
        values = ",\n".join(row_sql(row) for row in rows)
        self.stdin.write(
            "INSERT IGNORE INTO vocabularies "
            "(language_id, level, word, reading, pronunciation, meaning, example, example_translation, category, created_at) "
            f"VALUES\n{values};\nSELECT ROW_COUNT();\n"
        )
        self.stdin.flush()
        return self._read_integer()

    def close(self) -> None:
        self.stdin.close()
        return_code = self.process.wait()
        if return_code != 0:
            stderr = (self.process.stderr.read() if self.process.stderr else "").strip()
            raise RuntimeError(f"mysql import failed with exit {return_code}: {stderr[-2000:]}")


def process_language(
    spec: LanguageSpec,
    cache_dir: Path,
    sink: MysqlSink | None,
) -> tuple[int, int, int, int]:
    source = download_and_verify(spec, cache_dir)
    existing_dictionary_rows = sink.dictionary_count(spec.language_id) if sink else 0
    target_remaining = max(spec.quota - existing_dictionary_rows, 0)
    if sink and target_remaining == 0:
        print(
            f"{spec.code}: accepted=0 rejected=0 inserted=0 "
            f"dictionaryRows={existing_dictionary_rows}/{spec.quota} (already satisfied)",
            flush=True,
        )
        return 0, 0, 0, 0

    rows, stats = iter_rows(source, spec, stop_after=None if sink else spec.quota)
    batch: list[VocabularyRow] = []
    inserted = 0
    for row in rows:
        batch.append(row)
        if sink:
            flush_at = min(BATCH_SIZE, target_remaining - inserted)
        else:
            flush_at = BATCH_SIZE
        if len(batch) >= flush_at:
            if sink:
                inserted += sink.write_batch(batch)
            batch.clear()
        if sink and inserted >= target_remaining:
            break
    if batch and sink:
        inserted += sink.write_batch(batch)
    accepted, rejected = stats[1], stats[2]
    if sink and existing_dictionary_rows + inserted < spec.quota:
        raise RuntimeError(
            f"{spec.code}: database has {existing_dictionary_rows + inserted} dictionary rows, "
            f"quota is {spec.quota}; source exhausted before target was reached"
        )
    if not sink and accepted < spec.quota:
        raise RuntimeError(f"{spec.code}: source yielded {accepted} valid entries, quota is {spec.quota}")
    print(
        f"{spec.code}: accepted={accepted} rejected={rejected} inserted={inserted} "
        f"dictionaryRows={existing_dictionary_rows + inserted}/{spec.quota}",
        flush=True,
    )
    return stats[0], accepted, rejected, inserted


def run_self_test() -> None:
    source, specs = load_manifest(DEFAULT_MANIFEST)
    assert source["license"]
    assert sum(spec.quota for spec in specs) == 1_000_000
    assert sql_literal("a'b\\c\n") == "'a\\'b\\\\c\\n'"
    row = normalize_entry({"": "hola", "d": ["hello"], "p": ["intj"], "i": "[ola]"}, specs[0], 0)
    assert row and row.meaning == "hello" and row.pronunciation == "[ola]"
    oversized_definition = "x" * (MAX_MEANING_LENGTH + 1)
    assert normalize_entry({"": "oversized", "d": [oversized_definition]}, specs[0], 0) is None
    assert compact_text("x" * MAX_READING_LENGTH, MAX_READING_LENGTH) == "x" * MAX_READING_LENGTH
    print(f"self-test: PASS ({len(specs)} languages, 1,000,000 quota rows)")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE)
    parser.add_argument("--compose-file", type=Path, default=ROOT / "docker-compose.yml")
    parser.add_argument("--database", help="optional database name; defaults to MYSQL_DATABASE in the container")
    parser.add_argument("--only", nargs="*", help="limit a dry-run/import to selected language codes")
    parser.add_argument("--dry-run", action="store_true", help="download/verify/normalize without database writes")
    parser.add_argument("--import", dest="do_import", action="store_true", help="bulk insert through the mysql compose service")
    parser.add_argument("--self-test", action="store_true", help="run local parser and SQL-escaping checks")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.self_test:
        run_self_test()
        return 0
    if args.dry_run == args.do_import:
        raise SystemExit("choose exactly one of --dry-run or --import")
    source, specs = load_manifest(args.manifest)
    selected = set(args.only or [])
    if selected:
        unknown = selected - {spec.code for spec in specs}
        if unknown:
            raise SystemExit(f"unknown language codes: {', '.join(sorted(unknown))}")
        specs = [spec for spec in specs if spec.code in selected]
    sink = MysqlSink(args.compose_file, "mysql", args.database) if args.do_import else None
    totals = [0, 0, 0, 0]
    try:
        for spec in specs:
            scanned, accepted, rejected, inserted = process_language(spec, args.cache_dir, sink)
            totals[0] += scanned
            totals[1] += accepted
            totals[2] += rejected
            totals[3] += inserted
    finally:
        if sink:
            sink.close()
    print(
        json.dumps(
            {
                "source": source["name"],
                "languages": len(specs),
                "scanned": totals[0],
                "accepted": totals[1],
                "rejected": totals[2],
                "inserted": totals[3],
                "databaseWrite": bool(args.do_import),
            },
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
