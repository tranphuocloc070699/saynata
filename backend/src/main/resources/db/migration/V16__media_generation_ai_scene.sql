-- TASK-82: the Codex AI pictures (pipeline: uv run pipeline media-ai). One media_generation row per picture
-- attempt. The old OpenAI rows (TASK-53) keep these columns empty. The backend does not read them.
--
-- prompt_version  which version of the scene rules made the picture (for example ai-v1); a new version means
--                 "draw it again"
-- method          the way Claude chose to show the sense: object, need, emotion, action, quality, relation, role,
--                 story, repetition, or none (the sense cannot be drawn)
-- scene_json      Claude's scene: method, scene, focus, avoid
-- guess_json      the blind guess: open answer, choices, answer, confidence, why
-- passed          true when the blind guess found the word (only a passed picture is linked)
-- attempt         1, 2 or 3 for the same sense and prompt_version
--
-- The two JSON columns are JSON, not JSONB: the backend tests run on H2, which has no JSONB. Nothing queries
-- inside them (the pipeline report reads whole values).
ALTER TABLE media_generation ADD COLUMN prompt_version VARCHAR(16);
ALTER TABLE media_generation ADD COLUMN method VARCHAR(16);
ALTER TABLE media_generation ADD COLUMN scene_json JSON;
ALTER TABLE media_generation ADD COLUMN guess_json JSON;
ALTER TABLE media_generation ADD COLUMN passed BOOLEAN;
ALTER TABLE media_generation ADD COLUMN attempt INTEGER;

-- A sense whose method is "none" is recorded but nothing is drawn, so it has no file.
ALTER TABLE media_generation ALTER COLUMN file_name DROP NOT NULL;
