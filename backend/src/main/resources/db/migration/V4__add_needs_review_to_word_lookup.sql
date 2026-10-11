-- TASK-16: true when Claude's entry still used hard words after one retry. Saved anyway, but marked for a person to look at.
ALTER TABLE word_lookup ADD COLUMN needs_review BOOLEAN NOT NULL DEFAULT FALSE;
