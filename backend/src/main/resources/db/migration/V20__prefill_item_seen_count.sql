-- TASK-83.8: how many translated sentences contained this word (sentence-words source). Each sighting raises the
-- priority a little, so words learners meet often are answered first by the nightly prefill job.
-- (V18 and V19 are Java migrations, see src/main/java/db/migration.)
ALTER TABLE prefill_item ADD COLUMN seen_count INT NOT NULL DEFAULT 1;
