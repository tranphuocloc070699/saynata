CREATE TABLE word (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    headword           VARCHAR(100) NOT NULL,
    part_of_speech     VARCHAR(30),
    ipa                VARCHAR(100),
    simple_explanation TEXT         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One row per word, "Run" and "run" count as the same word.
CREATE UNIQUE INDEX word_headword_lower_uq ON word (lower(headword));
