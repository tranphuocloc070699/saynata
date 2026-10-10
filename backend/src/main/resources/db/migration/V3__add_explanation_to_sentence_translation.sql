-- From prompt v3 on, a sentence row holds the whole explanation, not only the translation.
-- Older rows get empty values; they are never read again because the key includes the prompt version.
ALTER TABLE sentence_translation ADD COLUMN meaning TEXT NOT NULL DEFAULT '';
ALTER TABLE sentence_translation ADD COLUMN words_json TEXT NOT NULL DEFAULT '[]';
