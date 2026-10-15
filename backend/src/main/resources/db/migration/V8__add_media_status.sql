-- TASK-52: the admin can mark a sense "needs_ai" (the picture is bad, make an AI image later: TASK-53).
-- NULL = nothing special. Bundled senses keep it on dictionary_sense, backend-word senses on word_lookup_media.
ALTER TABLE dictionary_sense ADD COLUMN media_status VARCHAR(16);
ALTER TABLE word_lookup_media ADD COLUMN media_status VARCHAR(16);

CREATE INDEX idx_dictionary_sense_media_status ON dictionary_sense (media_status);
CREATE INDEX idx_word_lookup_media_status ON word_lookup_media (media_status);
