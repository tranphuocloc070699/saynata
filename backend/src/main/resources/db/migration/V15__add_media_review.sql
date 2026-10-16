-- TASK-80: the admin reviews the pictures and clips of senses and approves the good ones.
-- This is separate from media_status (TASK-52/53, "needs_ai") and from the word-text "needs review" flag.
-- An approval belongs to one media file: a sense counts as approved only while its current media_id equals
-- media_reviewed_media_id. A new picture (Pick another, upload, AI) therefore needs a new review without any reset.
-- Bundled senses keep it on dictionary_sense, backend-word senses on word_lookup_media.
ALTER TABLE dictionary_sense ADD COLUMN media_reviewed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE dictionary_sense ADD COLUMN media_reviewed_media_id BIGINT REFERENCES media (id) ON DELETE SET NULL;
ALTER TABLE word_lookup_media ADD COLUMN media_reviewed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE word_lookup_media ADD COLUMN media_reviewed_media_id BIGINT REFERENCES media (id) ON DELETE SET NULL;
