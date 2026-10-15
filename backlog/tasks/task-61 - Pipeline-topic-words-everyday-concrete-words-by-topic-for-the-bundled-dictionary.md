---
id: TASK-61
title: >-
  Pipeline: topic words (everyday concrete words by topic) for the bundled
  dictionary
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-15 07:22'
updated_date: '2026-10-15 08:42'
labels:
  - pipeline
  - dictionary
dependencies: []
references:
  - 'https://github.com/globalwordnet/english-wordnet'
  - 'https://github.com/rspeer/wordfreq'
  - 'https://www.cambridgeenglish.org/Images/starters-word-list-picture-book.pdf'
  - pipeline/scratch/topic_candidates.py
documentation:
  - backlog/docs/features/doc-6 - Dictionary-pipeline.md
priority: medium
type: feature
ordinal: 59000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why

The bundled dictionary is NGSL (2,750 words), which is chosen by frequency, so everyday concrete words are missing: "apple", "giraffe", "scarf", "kettle", "tram" are not there. A learner who looks around a street, a kitchen or a zoo cannot find most of what they see. The developer asked (2026-10-15) for vocabulary for streets, vegetables, animals and tools. This task adds about 1,000-1,300 "topic words" (mostly concrete nouns, a few verbs) with a `topic` label, so they can also be browsed by topic.

## What the prototype found (2026-10-15)

Script: `pipeline/scratch/topic_candidates.py` (not in the CLI). Output: `pipeline/scratch/topic_candidates_first_3.5.tsv` and `..._any_3.5.tsv` (word, pos, zipf, topic, WordNet gloss).

- Sources: Open English WordNet 2025 lexicographer files + hypernyms give the topic (noun.animal, noun.food, noun.plant; noun.artifact split by anchors like clothing, conveyance, furniture, tool); wordfreq Zipf >= 3.5 keeps common words (animals, food, plants, home, clothes, transport go down to 3.1-3.2). Words already in pipeline.db (lemma or form) are removed.
- `--senses first`: 1,271 candidates. places 193, animals 189, food and drink 182 (14 verbs), tools and objects 145, plants 138, people and jobs 95, home 81, nature 66, the body 59 (7 verbs), clothes 58, transport 53, weather 12 (rain/snow/wind are already in NGSL).
- `--senses any`: 1,888 candidates, better recall (tiger, whale, donkey) but more noise.
- Cambridge Young Learners check (Starters/Movers/Flyers thematic list, read only, not copied): 121 of their concrete-topic words are outside NGSL; `first` covers 84, `any` covers 93. Missing: compounds and -y adjectives (toothbrush, armchair, motorbike, sunny, windy, striped, curly), some nouns (handbag, cupboard, railway, astronaut, moustache, pyjamas), and adverbs (upstairs, downstairs).
- Noise WordNet + wordfreq cannot remove: names (charlotte, perry, carter, cooper, maria; OEWN 2025 has almost no proper nouns, and wordfreq has no case), rare first senses (bass, bishop as food; trump, kit as objects; "heaven" as a place), technical words (receptor, valve, electron). A hand stop list in the script is only a stopgap.

## Plan (to refine when the task starts)

1. Candidate step `pipeline topics candidates`: the prototype logic, plus a Wiktionary name check (a capitalised Kaikki page with pos "name" means drop or review), and a small hand-made add list for the gaps above (compounds, weather and describing adjectives). Saves rows into pipeline.db with a `topic` and `source = 'topic'`.
2. Judge step (cheap, Haiku 5.5, batches of ~50 words, structured output): "is the everyday meaning of this word concrete and useful for an A2-B2 learner? which topic?" About 40 calls, under $0.50. Then the developer reviews the kept list (TSV or the admin) before any Sonnet money is spent.
3. Generate: the existing `generate` (Sonnet 5.5, same prompt, levelled examples and usage) on the topic rows. NGSL cost for comparison: Sonnet ~$0.008 per call; a full NGSL redo is ~$36 for 2,750 words; usage + examples passes were ~$17. Estimate for ~1,200 topic words: **$15-20** (fewer senses per concrete noun, some retries). Run by the developer, not by agents.
4. Validate: the defining-vocabulary check stays NGSL-based, so a definition of "giraffe" may not use "giraffe" or other topic words. Check that examples may use the headword (it is not in the NGSL vocabulary).
5. Data model: `topic` on the pipeline entry and in Postgres (new Flyway file, `dictionary_word.topic VARCHAR(32) NULL` + index; `word_rank` is NOT NULL, so topic words get a rank after NGSL, e.g. 10,000 + Zipf order). `import-to-postgres`/`export` carry it; `dictionary.sqlite` gets `words.topic`.
6. Media: the existing `pipeline media` pass (Pixabay, free key) on the new words; nearly all are photo words. ~1,200 words at Pixabay's 100 requests/minute is about 30-60 minutes.
7. Browse by topic (separate tasks, mockup first): app: a "Topics" list → word grid with photos; admin: a `topic` filter and column on the words list next to `source` and `cefr`.
8. Licenses: add `pipeline/licenses/` (or a NOTICE section) with OEWN (CC BY 4.0, attribution required) and wordfreq (code Apache 2.0, data CC BY-SA 4.0: we use only the numbers to filter, we do not ship its lists); add the attribution to the app's About/credits and to doc-6. Cambridge YL lists are © Cambridge: used only to check coverage, never copied into the repo.

## Open questions for the developer

- `first` (1,271, cleaner) or `any` (1,888, needs the judge step) as the starting list?
- Keep "people and jobs" and "nature" as topics, or only the ten asked for?
- Should topic words show a CEFR level like NGSL words (the model sets it per sense)?
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A pipeline command builds the topic word candidates from Open English WordNet + wordfreq, removes NGSL words and names, and saves each word with exactly one topic
- [ ] #2 The developer approved the final list (about 800-1,500 words) before any paid Sonnet generation runs
- [ ] #3 Topic words are generated with the existing generate pass (simple definition, levelled examples, usage) and pass the same validation
- [ ] #4 The entry, pipeline.db, Postgres (new Flyway migration) and the exported dictionary.sqlite carry a topic field; NGSL words have no topic
- [ ] #5 The media pass gives topic words photos like NGSL words
- [ ] #6 OEWN and wordfreq attribution and license files are in the repo and in doc-6; no Cambridge list is copied into the repo
- [ ] #7 doc-6 (Dictionary pipeline) is updated with the new commands and the topic field; pipeline tests cover candidate filtering and the topic field
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Scope of this slice (developer, 2026-10-15): start from the first-sense list, add a Haiku judge step. Generation, Postgres topic column, media and browsing come after the developer reviews the judged list.
1. pipeline/src/pipeline/topics.py: candidate builder from the prototype (OEWN 2025 + wordfreq, first sense decides, NGSL removed), plus a short hand list of everyday words the first-sense rule misses (tiger, whale, toothbrush, sunny...).
2. New table topic_words in data/pipeline.db (lemma, pos, zipf, topic, gloss, status candidate|keep|drop, judge reason). The NGSL words table is not touched.
3. Judge pass (judge.py style like usage.py): Haiku 5.5, 50 words per call, structured output: keep or drop + topic + short reason. Drops names, rare first senses, technical, rude. fetch is a parameter so tests use a fake.
4. CLI: pipeline topics candidates | judge [--dry-run --limit] | report | export-tsv.
5. wn + wordfreq as dependencies; licenses/NOTICE for OEWN (CC BY 4.0) and wordfreq.
6. Tests for filtering, store and judge clean-up. Update doc-6, pipeline README, CLAUDE.md overview line.
7. doc-7 decision row (approved by the developer today).
Agents do not run the paid judge pass: the developer runs it (about 26 calls, under $0.50).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-15: topics.py + CLI (topics candidates | judge | report | export-tsv), topic_words table in pipeline.db, licenses/NOTICE.md, doc-6 section 11, doc-7 row. Real run on a copy of pipeline.db: 1,378 candidates (1,271 first-sense + hand list). Judge not run yet: the developer runs it (about 28 Haiku calls, under $0.10). Remaining: move keep words to the words table, generate, topic column in Postgres + dictionary.sqlite, media, browse by topic.

2026-10-15: full judge run (28 Haiku calls, under $0.10): 980 keep, 398 drop, 0 failed. Reviewed every verdict; topics review applies REVIEW_DROP (47), REVIEW_MOVE (21), REVIEW_KEEP (11). Final: 944 keep, 434 drop. Results live in the worktree's data/pipeline.db copy (topic_words) and data/out/topic_words.tsv.
<!-- SECTION:NOTES:END -->
