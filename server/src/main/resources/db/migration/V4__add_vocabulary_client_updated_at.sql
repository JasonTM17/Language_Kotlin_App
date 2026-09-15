-- Preserve the client-side logical state version used to order offline
-- vocabulary state-sync retries. Existing rows remain compatible and receive
-- their first version from the next client mutation.
ALTER TABLE user_vocabulary_progress
    ADD COLUMN client_updated_at TIMESTAMP NULL;
