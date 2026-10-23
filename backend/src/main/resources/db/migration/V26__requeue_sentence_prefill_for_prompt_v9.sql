-- Prompt v9 (TASK-89.3) writes a short summary: a gist line, up to 3 bullets, bold key words.
-- The version is part of the sentence cache key, so the v8 answers are not read any more. Every sentence the
-- prefill job already answered goes back to pending, so the next runs answer it again with v9 (about $0.0003 each).
UPDATE prefill_item
SET status = 'pending', attempts = 0, last_error = NULL, updated_at = CURRENT_TIMESTAMP
WHERE kind = 'sentence' AND status = 'done';
