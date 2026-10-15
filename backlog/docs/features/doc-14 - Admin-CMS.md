---
id: doc-14
title: Admin CMS
type: specification
created_date: '2026-10-14 11:29'
updated_date: '2026-10-15 06:26'
---
> Status: **built against the API contract of TASK-49.1** (the backend part is built in parallel; names may need small changes in `types.ts`). TASK-49.2. Last traced from code: 2026-10-15 (infinite scroll in the list: TASK-54; Media row: TASK-52; review reasons: TASK-55).
> Spec: F5 Admin CMS (TASK-49). Decisions: doc-7 rows 10-14 (Admin CMS, shadcn/ui + Refine, layout A, users with roles).

## What it does

A web page for the developer (and later other admins) to check what Claude wrote. After signing in, the left sidebar has Words, Sentences and Media.
Each page is a split view (layout A): a list with a search box and filter chips on the left (it loads 50 more entries when you scroll near the end, TASK-54), and the selected entry as a plain table of fields on the right.
Three icon-only buttons act on the entry: **Mark reviewed** (check), **Edit** (pencil, a form plus a JSON tab) and **Regenerate** (arrow, asks for confirmation in the page, then deletes the entry so Claude makes it again on the next request).
In a word's detail, each sense has a **Media row**: the current photo or clip with its Pixabay credit, and three icon-only buttons to fix it (TASK-52): **Pick another** (images), **No image** (image-off) and **Mark for AI** (sparkles). The Media page in the sidebar is still a placeholder.

## Big picture

```mermaid
flowchart LR
    B[Browser: admin.loffy.me] -->|static files| C[Caddy]
    B -->|"fetch + cookie, /api/admin/**"| API[Spring Boot backend]
    API --> PG[(Postgres: word_lookup, sentence_translation)]
```

The admin app is only static files (`admin/dist`). The login cookie is HttpOnly, so the page never reads it; it only sends it back (`credentials: 'include'`, axios `withCredentials`).

## Step by step

```mermaid
sequenceDiagram
    participant U as Admin
    participant P as Page (Refine)
    participant A as /api/admin
    U->>P: open /words
    P->>A: GET /auth/me (cookie)
    A-->>P: 401, so go to /login
    U->>P: email + password
    P->>A: POST /auth/login
    A-->>P: Set-Cookie (HttpOnly)
    P->>A: GET /words?_start&_end&q&needs_review
    A-->>P: array + X-Total-Count
    U->>P: pick a word, press Edit, Save
    P->>A: PUT /words/{id} (entry JSON)
```

| Step | File:line | What happens |
|---|---|---|
| Routes, login guard | [App.tsx:25](../../../admin/src/App.tsx#L25) | Pages inside `Authenticated`; without a login, go to `/login` |
| Login, check, logout | [authProvider.ts:21](../../../admin/src/providers/authProvider.ts#L21) | `fetch` to `/api/admin/auth/*`; signed in only if `/me` has the ADMIN role; a 401 anywhere logs out ([onError](../../../admin/src/providers/authProvider.ts#L53)) |
| Data provider | [dataProvider.ts:7](../../../admin/src/providers/dataProvider.ts#L7) | Refine simple-rest on `${VITE_API_URL}/api/admin`, axios with cookies |
| List, search, chips | [EntryBrowser.tsx:72](../../../admin/src/components/EntryBrowser.tsx#L72) | `useInfiniteList`, 50 per page. `q` and `needs_review` go to the backend; Bundled, From backend and A1-A2 / B1-B2 filter the loaded rows. A new search or chip changes the query key, so the list starts again at page 1 |
| Infinite scroll | [EntryBrowser.tsx:104](../../../admin/src/components/EntryBrowser.tsx#L104) | A sentinel row at the end of the list is watched with `IntersectionObserver` (200px early). When it shows, `fetchNextPage` runs; there is a next page while the loaded rows are fewer than `X-Total-Count`. A spinner row shows while loading; the footer says "120 of 2,750" (no arrows). The selected id lives outside the pages, so it stays selected; after an edit the loaded pages are fetched again in place, without going back to the top |
| Mark reviewed, regenerate, save | [EntryBrowser.tsx:137](../../../admin/src/components/EntryBrowser.tsx#L113) | `POST /{id}/reviewed`, `DELETE /{id}`, `PUT /{id}`; then the loaded pages are fetched again |
| Regenerate confirm | [EntryActions.tsx:38](../../../admin/src/components/EntryActions.tsx#L38) | Inline panel; nothing is deleted until the check is pressed |
| Edit: Form and JSON tabs | [EntryEditor.tsx:32](../../../admin/src/components/EntryEditor.tsx#L32) | One draft; switching to Form parses the JSON and stops with a message if it is not valid |
| Media row, Pick another panel | [MediaRow.tsx:21](../../../admin/src/components/MediaRow.tsx#L21) | Pick another opens a panel **inline, under the row** (not a dialog) and searches at once with the sense's own words: [search:53](../../../admin/src/components/MediaRow.tsx#L53) calls `GET /media/search?sense=b-12:0`, and the answer fills the search input. 3x2 grid of up to 6 results; a clip has a small "clip" mark; the chosen tile gets an accent outline; the check button ([apply:68](../../../admin/src/components/MediaRow.tsx#L68)) sends the result with `PUT /words/{id}/senses/{n}/media`. [noImage:78](../../../admin/src/components/MediaRow.tsx#L78) is `DELETE` and [markForAi:84](../../../admin/src/components/MediaRow.tsx#L84) is `POST .../mark-for-ai`. After a change the word list is read again. A marked sense shows "Waiting for an AI image". Errors show the backend message under the row. Every write sends `Content-Type: application/json` (the backend needs it). |
| Review reasons | [ReviewReasons:27](../../../admin/src/pages/WordsPage.tsx#L27) | `reviewReasons` from the API (TASK-55). Under the **Status** row ([WordsPage.tsx:49](../../../admin/src/pages/WordsPage.tsx#L49)) a short list next to the "Needs review" badge; the same list at the top of the edit form ([WordForm:95](../../../admin/src/pages/WordsPage.tsx#L95); `EntryBrowser` passes the selected item as the third argument of `renderForm`, [EntryBrowser.tsx:233](../../../admin/src/components/EntryBrowser.tsx#L233)). When the word needs review but the list is empty it says "No problems found now; press ✓ to mark it reviewed". The flag is never cleared by the page. A reviewed word shows nothing. Sentences have no check yet. |
| Word fields | [WordsPage.tsx:43](../../../admin/src/pages/WordsPage.tsx#L43) | Table: IPA, sources, each sense (type + CEFR, explanation, 3 examples, usage, Vietnamese last, media) |
| Sentence fields | [SentencesPage.tsx:13](../../../admin/src/pages/SentencesPage.tsx#L13) | Sentence, simple explanation, words, translation last |
| API types | [types.ts](../../../admin/src/api/types.ts) | All shapes in one file; change them here when the backend differs |

## Errors

| Cause | Where | What the admin sees |
|---|---|---|
| Wrong email or password (401) | login | "Wrong email or password." under the form |
| Too many tries (429) | login | "Too many tries. Wait a minute and try again." |
| Session ended (401 on any call) | `onError` | Back to the login page |
| Invalid JSON in the JSON tab | editor | Red message; the tab does not change |
| Backend rejects the entry (400) | save | The backend message under the editor |
| List cannot load | list | "Could not load the list." |
| Pixabay search or download fails, too many searches, no Pixabay key (502, 429, 503) | Media row | The backend message in red under the row; the panel stays open |

## Run it

```bash
cd admin
cp .env.example .env.local      # VITE_API_URL=http://localhost:8081
npm ci
npm run dev                     # http://localhost:5173
```

The backend must allow CORS with credentials for `http://localhost:5173` (TASK-49.1). Production build and Caddy: see [Deploy to a VPS](../guides/doc-11%20-%20Deploy-to-a-VPS.md).

## Tests

`cd admin && npm test` (Vitest + Testing Library, no network).

| Test file | What it covers |
|---|---|
| `src/test/dataProvider.test.ts` | List URL (`_start`, `_end`, `_sort`, `_order`, `q`, `needs_review`), `X-Total-Count`, PUT, DELETE, cookies on |
| `src/test/login.test.tsx` | Login request (method, body, `credentials: include`), 401 message, ADMIN check, login form |
| `src/test/mediaRow.test.tsx` | Three icon-only buttons, panel opens inline and fills the search input, up to 6 tiles with the clip mark, search again with typed words, apply sends the chosen result (PUT, JSON header), No image (DELETE), Mark for AI (POST), the marked note, the backend error message |
| `src/test/infiniteScroll.test.tsx` | 50 words first, next page when the end is on screen (fake `IntersectionObserver`), count line "50 of 120" with no arrows, stops at the total, filter change goes back to page 1, selection kept when more load |
| `src/test/wordsPage.test.tsx` | List and badges, Needs review chip sends `needs_review=true`, source chip, field table, icon buttons, regenerate confirm, edit form and JSON tab, review reasons (detail and edit form), "No problems found now" hint |

## Change checklist

- Backend field renamed: change `admin/src/api/types.ts`, then the pages and tests the compiler points at.
- New chip: add it to `chips` in `WordsPage.tsx` or `SentencesPage.tsx` (server chips need backend support).
- New admin section: route in `App.tsx`, link in `Layout.tsx`.

## Known gaps

- Bundled / From backend and CEFR chips filter only the loaded rows until the backend supports `source` and `cefr` filters.
- IPA is shown but not edited: the contract says PUT sends the entry only.
- Media page is a placeholder with no list yet; it gets infinite scroll for free when it is built on EntryBrowser (TASK-54). The senses marked for AI are only available from the API (`GET /api/admin/media?status=needs_ai`); a list page for them can come with TASK-53.
- Pick another was not tried in a real browser with a real Pixabay key yet; the tests use mocked data.
- No Sentences "bundled" or CEFR chips: sentences have neither.
- Screens were not checked in a real browser against the real backend yet; the tests use mocked data.
