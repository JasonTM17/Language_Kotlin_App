-- LinguaAI catalogue expansion (V7).
-- Language metadata is schema-owned; the million-row dictionary payload is an
-- external, checksum-verified import and must not live in a Flyway migration.

INSERT INTO languages (id, code, name, levels) VALUES
(8, 'it', 'Italian', 'A1,A2,B1,B2,C1'),
(9, 'pt', 'Portuguese', 'A1,A2,B1,B2,C1'),
(10, 'ru', 'Russian', 'A1,A2,B1,B2,C1'),
(11, 'ar', 'Arabic', 'A1,A2,B1,B2'),
(12, 'hi', 'Hindi', 'A1,A2,B1,B2'),
(13, 'id', 'Indonesian', 'A1,A2,B1,B2'),
(14, 'th', 'Thai', 'A1,A2,B1,B2'),
(15, 'tr', 'Turkish', 'A1,A2,B1,B2'),
(16, 'vi', 'Vietnamese', 'A1,A2,B1,B2,C1'),
(17, 'nl', 'Dutch', 'A1,A2,B1,B2,C1');

-- The importer is safe to rerun and uses this natural-key guard. Existing
-- curated rows were checked for duplicates before this migration was authored.
CREATE UNIQUE INDEX uq_vocab_language_word ON vocabularies (language_id, word);
CREATE INDEX idx_vocab_word ON vocabularies (word);
CREATE INDEX idx_vocab_reading ON vocabularies (reading);
