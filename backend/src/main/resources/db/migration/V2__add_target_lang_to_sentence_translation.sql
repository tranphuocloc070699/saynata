-- Rows saved before this migration were all Vietnamese.
ALTER TABLE sentence_translation ADD COLUMN target_lang VARCHAR(8) NOT NULL DEFAULT 'vi';
