-- TASK-79: the admin can upload their own picture or clip for a sense (for example an image made in ChatGPT).
-- source says where a media row came from: 'pixabay' (the pipeline, the backend job, Pick another) or 'upload'.
-- An uploaded row has no Pixabay author or page: photographer, page_url and pixabay_id were already nullable (V6).
ALTER TABLE media ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'pixabay' CHECK (source IN ('pixabay', 'upload'));
