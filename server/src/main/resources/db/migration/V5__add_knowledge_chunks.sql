-- =============================================================================
-- RAG knowledge chunks (V5)
-- Written to run on BOTH MySQL 8 (production) and H2 in MySQL mode (tests),
-- following the V1 dialect rules:
--   * no inline INDEX/KEY clauses (separate CREATE INDEX statements)
--   * no ENGINE/CHARSET/COLLATE/ENUM/UNSIGNED/ON UPDATE clauses
--   * BOOLEAN + TIMESTAMP + DATETIME only
-- The embedding column is little-endian float32 BLOB, written/read by
-- VectorMath. `content` is deliberately NOT indexed: MySQL requires a prefix
-- length for TEXT indexes and H2 behaves differently, while retrieval filters
-- on language_id/level/embedding_model only.
-- =============================================================================

CREATE TABLE knowledge_chunks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    language_id BIGINT NOT NULL,
    level VARCHAR(20),
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    chunk_index INT NOT NULL DEFAULT 0,
    title VARCHAR(300) NOT NULL,
    content TEXT NOT NULL,
    embedding_model VARCHAR(80) NOT NULL,
    embedding BLOB NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_chunk_language FOREIGN KEY (language_id) REFERENCES languages (id),
    CONSTRAINT uq_chunk UNIQUE (source_type, source_id, chunk_index)
);

CREATE INDEX idx_chunk_lang_level ON knowledge_chunks (language_id, level);
CREATE INDEX idx_chunk_model ON knowledge_chunks (embedding_model);
CREATE INDEX idx_chunk_hash ON knowledge_chunks (content_hash);
