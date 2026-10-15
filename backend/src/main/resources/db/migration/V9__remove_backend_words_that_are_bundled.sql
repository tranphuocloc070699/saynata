-- TASK-58: the bundled entry wins. Before the 2,750 bundled words were imported into Postgres (V6),
-- Claude could describe a word like "run" and the answer was saved in word_lookup. Now the same word is in both
-- lists in the admin. GET /api/words/{word} answers a bundled word from dictionary_word and never writes a
-- word_lookup row for it (WordService), so these old rows are not needed any more.
--
-- The links to the media rows go first. The media rows and the files on disk stay (a known gap, doc-15).
DELETE FROM word_lookup_media
WHERE word_lookup_id IN (
    SELECT l.id FROM word_lookup l WHERE lower(l.word) IN (SELECT lower(w.lemma) FROM dictionary_word w));

DELETE FROM word_lookup
WHERE lower(word) IN (SELECT lower(w.lemma) FROM dictionary_word w);
