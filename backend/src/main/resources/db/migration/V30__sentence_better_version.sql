-- TASK-110: prompt v11 also writes "better", the text with its real mistakes fixed, and "better_why", a one-line
-- reason. Both stay NULL when the text is already natural, and for rows saved before v10.
ALTER TABLE sentence_translation ADD COLUMN better TEXT;
ALTER TABLE sentence_translation ADD COLUMN better_why TEXT;

-- The version is part of the sentence cache key, so the v10 answers are not read any more. Every sentence the
-- prefill job already answered goes back to pending, so the next runs answer it again with v11 (about $0.0003 each).
UPDATE prefill_item
SET status = 'pending', attempts = 0, last_error = NULL, updated_at = CURRENT_TIMESTAMP
WHERE kind = 'sentence' AND status = 'done';
