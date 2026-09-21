-- Persist the corpus chunks a tutor reply was grounded in (V8).
--
-- Citations were computed per turn and returned to the client, but never
-- stored, so reopening a conversation showed the same answer with no sources
-- and the learner could not tell whether the reply they were re-reading had
-- been grounded at all. Nullable: rows written before this migration, and
-- replies from modes that do not retrieve, legitimately have none.
ALTER TABLE ai_messages
    ADD COLUMN sources TEXT NULL;
