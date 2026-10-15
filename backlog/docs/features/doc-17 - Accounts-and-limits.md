---
id: doc-17
title: Accounts and limits
type: specification
created_date: '2026-10-15 10:00'
updated_date: '2026-10-15 10:01'
---
# Accounts and limits

> Status: **backend built** (TASK-66 accounts, TASK-68 sign-in for new Claude calls, TASK-69 rate limits, TASK-67 daily budget). The iOS sign-in screen is not built: it waits for a mockup. Last traced from code: 2026-10-15.
> Why: doc-7 rows of 2026-10-15. Related: [Admin API](doc-13%20-%20Admin-API.md) (the admin has its own cookie login), [Sentence translation](doc-4%20-%20Sentence-translation.md), [Word lookup](doc-5%20-%20Word-lookup.md), the SES setup in the [deploy guide](../guides/doc-11%20-%20Deploy-to-a-VPS.md) (section 10).

## What it does

A learner signs in with an **email code**: type an email, get a 6-digit code by email, type it, done. There are no passwords.
A signed-in learner may ask for **new** explanations from Claude. A **guest** (no token) gets only what costs nothing new:
the bundled words (on the device) and anything already in the shared cache. Claude spending also has a hard
**daily cap in dollars**, and every path has a **rate limit**.

| Caller | Cache hit or bundled word | New sentence, or word not bundled and not cached |
|---|---|---|
| Guest | Works | 401 `sign_in_required` |
| Signed in | Works | Calls Claude (if the budget and the user limit allow) |
| Anyone, budget used up | Works | 503 `daily_limit_reached` |

The current iOS app sends no token yet. So until the sign-in screen exists, **a new sentence or an uncached word from the app gives 401**.
Bundled words and cached answers work as before.

## Big picture

```mermaid
flowchart LR
    APP[iOS app] -->|"optional Authorization: Bearer"| SEC[Spring Security chain]
    SEC --> RL[PublicReadRateLimitFilter: 120 per minute per IP]
    RL --> BF[BearerTokenFilter: user id into CallerContext]
    BF --> TC[TranslationController / WordController]
    TC --> SVC[TranslationService / WordService]
    SVC -->|hit| CACHE[(Postgres cache + bundled words)]
    SVC -->|miss| GATE[ClaudeGate]
    GATE -->|"signed in? budget left? under user limit?"| CL[ClaudeService]
    CL -->|"tokens + cost"| USE[(claude_usage)]
    GATE -.->|"401 / 503 / 429"| ERR[ApiExceptionHandler]
    APP --> AUTH[AuthController /api/auth/*]
    AUTH --> ES[EmailSender: SES or log]
    AUTH --> DB[(email_login_code, refresh_token, users)]
```

## Sign-in flow

```mermaid
sequenceDiagram
    participant A as App
    participant C as AuthController
    participant S as AuthService
    participant M as EmailSender
    A->>C: POST /api/auth/email-code {email}
    C->>S: requestCode(email, ip)
    S->>S: normalise email, rate limit (IP, email)
    S->>S: 6-digit code, save HMAC hash, replace any old code
    S->>M: send code (a failure is only logged)
    C-->>A: 204 (always)
    A->>C: POST /api/auth/verify {email, code}
    C->>S: verify
    S->>S: not expired? count the guess (max 5), hash equal? delete the code
    S->>S: find or create the USER
    C-->>A: {accessToken, refreshToken, expiresIn, user}
    A->>C: POST /api/auth/refresh {refreshToken}
    C-->>A: new pair, the old refresh token is revoked
```

| Step | File:line | What happens |
|---|---|---|
| Tables | [V10__create_accounts.sql](../../../backend/src/main/resources/db/migration/V10__create_accounts.sql) | `email_login_code` (one row per email: keyed hash, attempts, expiry), `refresh_token` (SHA-256 of the token, `revoked_at`, cascade delete with the user). `users.password_hash` may now be empty: app users have no password. |
| Ask for a code | [AuthService.requestCode:82](../../../backend/src/main/java/com/englishtranslation/backend/auth/AuthService.java#L82) | Validates and lower-cases the email ([Emails.normalize:17](../../../backend/src/main/java/com/englishtranslation/backend/auth/Emails.java#L17)), applies the two rate limits, saves the code (a second code **replaces** the first: one per email), sends it. The same for known and unknown emails, so the controller can always answer 204. |
| Code hash | [AuthService.hashCode:200](../../../backend/src/main/java/com/englishtranslation/backend/auth/AuthService.java#L200) | HMAC-SHA256 with a key from the server secret, over `email:code`. A plain hash of 6 digits could be reversed in a second; the secret makes a stolen database useless. The code itself is never saved or logged by the SES sender. |
| Verify | [AuthService.verify:98](../../../backend/src/main/java/com/englishtranslation/backend/auth/AuthService.java#L98) | Expired: row deleted. The guess is counted by one atomic SQL update (max 5), so parallel requests cannot get extra guesses. A right code is deleted in the same step that accepts it, so it works once. Every failure is the same 401 `invalid_code`. First sign-in creates the user (role USER); an existing user (even an admin) just signs in. |
| Tokens | [AuthService.issueTokens:181](../../../backend/src/main/java/com/englishtranslation/backend/auth/AuthService.java#L181) | Access token: JWT HS256, 1 hour, only the user id, claim `typ=access`, own key ([AccessTokenService](../../../backend/src/main/java/com/englishtranslation/backend/auth/AccessTokenService.java)). Refresh token: 32 random bytes, 60 days, only its SHA-256 is saved. |
| Keys | [AuthKeys:29](../../../backend/src/main/java/com/englishtranslation/backend/auth/AuthKeys.java#L29) | One base secret gives separate keys for the access token and the code hash: `USER_JWT_SECRET`, else `ADMIN_JWT_SECRET`, else random at start-up (a warning is logged). So a missing variable never stops a deploy. |
| Refresh | [AuthService.refresh:126](../../../backend/src/main/java/com/englishtranslation/backend/auth/AuthService.java#L126) | Rotation: the used token is revoked and a new pair is issued. If a **revoked** token is sent again, all tokens of that user are revoked (someone may have copied it) and the app must sign in again. |
| Sign out | [AuthService.signOut:143](../../../backend/src/main/java/com/englishtranslation/backend/auth/AuthService.java#L143) | Revokes that refresh token. Always 204. |
| Delete account | [AuthService.deleteAccount:153](../../../backend/src/main/java/com/englishtranslation/backend/auth/AuthService.java#L153) | `DELETE /api/me` removes the user, its roles, its refresh tokens and a pending code. The access token stops working at once (the filter checks that the user exists). An account with the ADMIN role is refused (403 `admin_account`). |
| Bearer filter | [BearerTokenFilter:40](../../../backend/src/main/java/com/englishtranslation/backend/auth/BearerTokenFilter.java#L40) | Optional token. Bad, expired or unknown-user token = a guest, no error. A good one authenticates as role USER and sets `CallerContext`. It never touches `/api/admin`, so an access token cannot open the admin API and the admin cookie cannot open `/api/me`. |
| Rules | [SecurityConfig:43](../../../backend/src/main/java/com/englishtranslation/backend/admin/SecurityConfig.java#L43) | `/api/me` needs USER; `/api/auth/*`, `/api/translations*`, `/api/words/*` stay open. |
| Email | [AuthConfig:20](../../../backend/src/main/java/com/englishtranslation/backend/auth/AuthConfig.java#L20), [SesEmailSender](../../../backend/src/main/java/com/englishtranslation/backend/auth/SesEmailSender.java), [LoggingEmailSender](../../../backend/src/main/java/com/englishtranslation/backend/auth/LoggingEmailSender.java) | `SES_FROM_EMAIL` set: SES v2, region `AWS_REGION`, keys from the standard AWS variables, client built on the first send. Not set: the code is written to the log and a warning is logged at start-up. A send failure is logged and the answer is still 204. |

Endpoints (all JSON, no cookie):

| Method and path | Body | Answer |
|---|---|---|
| `POST /api/auth/email-code` | `{email}` | 204 always. 400 `invalid_email` for a bad format. 429 `rate_limited`. |
| `POST /api/auth/verify` | `{email, code}` | `{accessToken, refreshToken, expiresIn (seconds), user: {id, email}}`. 401 `invalid_code`. |
| `POST /api/auth/refresh` | `{refreshToken}` | Same shape as verify. 401 `invalid_refresh_token`. |
| `POST /api/auth/sign-out` | `{refreshToken}` | 204 always. |
| `GET /api/me` | Bearer | `{id, email}`. 401 without a token. |
| `DELETE /api/me` | Bearer | 204. |

## The gate for new Claude calls

[ClaudeGate.beforeNewCall:33](../../../backend/src/main/java/com/englishtranslation/backend/limits/ClaudeGate.java#L33) runs right before every Claude call, and only after the cache and bundled lookups missed:
[TranslationService:69](../../../backend/src/main/java/com/englishtranslation/backend/translation/TranslationService.java#L69) (sentences, also personal-looking text and the stream) and [WordService:86](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L86) (words).
The order is: (1) guest: 401 `sign_in_required`; (2) budget used up: 503 `daily_limit_reached`; (3) user over 60 per hour or 300 per day: 429 `rate_limited`.
The budget comes before the user limit so a refused call does not use up a user's allowance.
The word retry (a second call for hard words) only re-checks the budget ([checkBudget:49](../../../backend/src/main/java/com/englishtranslation/backend/limits/ClaudeGate.java#L49)).

Who is asking: [CallerContext](../../../backend/src/main/java/com/englishtranslation/backend/limits/CallerContext.java) holds the user id in a thread-local. `BearerTokenFilter` sets it for the request. The sentence stream runs on its own thread, so [TranslationController:65](../../../backend/src/main/java/com/englishtranslation/backend/translation/TranslationController.java#L65) copies the user id there. For the stream, the error is an `error` event: `{"status": 401, "message": "...", "code": "sign_in_required"}` (the `code` field is only there for these coded errors).

Admin tools: the admin API makes no Claude call today (deleting a row is "regenerate": the next **public** lookup asks Claude, and that lookup goes through the gate). A future admin tool that calls Claude must call `ClaudeGate.checkBudget()` and record with `Feature.ADMIN`.

## Rate limits

[RateLimiter](../../../backend/src/main/java/com/englishtranslation/backend/limits/RateLimiter.java#L16) is a small token bucket in memory (no Redis; a restart clears it; one server). "5 per hour" is a bucket of 5 that refills evenly. When several limits apply, a refused request uses up none of them. Numbers live in `app.limits.*` ([LimitProperties](../../../backend/src/main/java/com/englishtranslation/backend/limits/LimitProperties.java)); each can be set from env.

| What | Default | Env |
|---|---|---|
| `/api/auth/email-code` per IP | 5 per hour | `LIMIT_EMAIL_CODE_IP_MAX`, `LIMIT_EMAIL_CODE_IP_PERIOD` |
| `/api/auth/email-code` per email | 3 per 15 minutes | `LIMIT_EMAIL_CODE_EMAIL_MAX`, `LIMIT_EMAIL_CODE_EMAIL_PERIOD` |
| New Claude calls per signed-in user | 60 per hour, 300 per day | `LIMIT_CLAUDE_USER_HOUR_MAX`, `LIMIT_CLAUDE_USER_DAY_MAX` |
| `/api/translations*`, `/api/words/*` per IP (cache hits too) | 120 per minute | `LIMIT_PUBLIC_READ_IP_MAX`, `LIMIT_PUBLIC_READ_IP_PERIOD` |

Over a limit: **429**, header `Retry-After` (seconds), body code `rate_limited`. The public read limit is a servlet filter ([PublicReadRateLimitFilter:34](../../../backend/src/main/java/com/englishtranslation/backend/limits/PublicReadRateLimitFilter.java#L34)), so even the event stream gets a real 429.
The client IP is `request.getRemoteAddr()`, like the admin login limit ([ClientIp](../../../backend/src/main/java/com/englishtranslation/backend/limits/ClientIp.java)). Tomcat's remote-ip valve (`server.tomcat.remoteip.*`) swaps in `X-Forwarded-For` only when the request comes from a loopback or private proxy (Caddy). The backend port is on loopback only (PR #51), so a client cannot fake the header.

## Daily Claude budget

```mermaid
flowchart LR
    CALL[Claude call finished] --> REC["ClaudeService.recordUsage: input + output tokens from the response"]
    REC --> COST["ClaudeUsageService.cost: tokens x app.claude.pricing.model / 1,000,000"]
    COST --> ROW[(claude_usage row: model, feature, tokens, cost)]
    GATE[ClaudeGate] -->|"sum of today's UTC rows >= app.claude.daily-budget-usd?"| ROW
```

| Step | File:line | What happens |
|---|---|---|
| Table | [V11__create_claude_usage.sql](../../../backend/src/main/resources/db/migration/V11__create_claude_usage.sql) | One row per call: time, model, feature (`SENTENCE`, `WORD`, `ADMIN`), input and output tokens, cost in USD (8 decimals). The cost is saved, so a later price change does not rewrite history. |
| Record | [ClaudeService.recordUsage:232](../../../backend/src/main/java/com/englishtranslation/backend/claude/ClaudeService.java#L232) | Called for the normal sentence call, the streamed call (the stream accumulator sums the start and end events) and the word call, **before** the answer is read, so a refused or "not English" answer is counted too (it cost money). Never throws. |
| Cost | [ClaudeUsageService.cost:74](../../../backend/src/main/java/com/englishtranslation/backend/claude/ClaudeUsageService.java#L74) | `(input x inputPerMtok + output x outputPerMtok) / 1,000,000`. A model with no price in config is counted at the **highest** configured price (never free) and a warning is logged. |
| Prices | `app.claude.pricing.<model>.input-per-mtok` / `output-per-mtok` in [application.properties](../../../backend/src/main/resources/application.properties) | `claude-haiku-5-5`: $0.10 in, $0.50 out per million tokens (prompts up to 100K tokens; checked 2026-10-15). **Check them when the model or Anthropic's prices change.** |
| Cap | [ClaudeUsageService.budgetReached:105](../../../backend/src/main/java/com/englishtranslation/backend/claude/ClaudeUsageService.java#L105) | Sum of today's rows (the day starts at 00:00 UTC) `>=` `app.claude.daily-budget-usd` (`CLAUDE_DAILY_BUDGET_USD`, default 1.00). Then the answer is 503 `daily_limit_reached`, "Saynata has reached today's limit for new explanations. Cached words still work. Try again tomorrow." |
| Report | [AdminUsageController](../../../backend/src/main/java/com/englishtranslation/backend/admin/AdminUsageController.java), [ClaudeUsageService.report:110](../../../backend/src/main/java/com/englishtranslation/backend/claude/ClaudeUsageService.java#L110) | `GET /api/admin/usage?days=7` (admin cookie; 1 to 90 days): `{dailyBudgetUsd, todayUsd, days: [{date, total, byFeature}]}`, newest first, empty days included. No admin screen yet. |

## Errors

| Cause | HTTP | Code | Message |
|---|---|---|---|
| Guest needs a new Claude call | 401 | `sign_in_required` | "Sign in to get a new explanation." |
| Today's budget used up | 503 | `daily_limit_reached` | "Saynata has reached today's limit for new explanations. Cached words still work. Try again tomorrow." |
| Any limit above | 429 + `Retry-After` | `rate_limited` | "Too many requests. Try again in a little while." |
| Bad email format | 400 | `invalid_email` | "Enter a valid email address." |
| Wrong, expired, used or locked code | 401 | `invalid_code` | "That code is wrong or has expired. Ask for a new code." |
| Unknown, expired or used refresh token | 401 | `invalid_refresh_token` | "Your session has ended. Sign in again." |
| `/api/me` without a good token | 401 | none (empty body) | |
| Delete an admin account through `/api/me` | 403 | `admin_account` | "An admin account cannot be deleted here. Ask the developer." |

The body is Spring problem details (`type`, `title`, `status`, `detail`) plus `code`. The app should switch on `code`, not on the text.

## Tests

| Test file | What it covers |
|---|---|
| `AuthFlowTest` | 204 for known and unknown email, bad email 400, the code is 6 digits and only a keyed hash is saved, expiry (10 minutes), 5 wrong tries then locked, a code works once, a second code replaces the first, a failing email sender still gives 204, user created on first sign-in, refresh rotation, reuse of an old refresh token revokes the rest, refresh stored as a hash, expired refresh token, sign-out, `/api/me`, access token vs admin cookie, account deletion (user, tokens, access token), admin account not deletable, app user cannot log in to the admin, email-code rate limits (per email and per IP). |
| `ClaudeGateTest` | Guest cache hit works with no Claude call, guest miss is 401 (sentence, word, stream error event), signed-in miss calls Claude (also in the stream thread), per-user limit gives 429 + `Retry-After`, cache hits do not use the limit, budget used up gives 503 with **no Claude call** (HTTP and stream) while cached answers still work, per-IP public read limit. |
| `ClaudeUsageServiceTest` | Cost maths, unknown model price, the UTC day boundary, the budget cap, the report. |
| `ClaudeServiceStreamingTest` | Tokens from a stream are recorded for the sentence feature, also for a refused answer. |
| `RateLimiterTest` | Bucket maths with a clock moved by hand, no partial use of several limits. |
| `EmailSenderConfigTest` | Starts with no AWS settings (logging fake) and with SES settings (no AWS call at start-up); email text; email checks. |
| `AdminUsageTest` | `/api/admin/usage` needs the admin login, spend per feature, `days` default and cap. |

The old tests set `app.auth.require-sign-in=false` in `src/test/resources/config/application.properties`, so they still call the services as a guest. `ClaudeGateTest` sets it to `true`. Run: `cd backend && ./mvnw -B test`.

## Change checklist

- New place that calls Claude: call `ClaudeGate.beforeNewCall()` (or `checkBudget()` for admin tools) before it and record the usage with the right `Feature`.
- New model: add its price to `app.claude.pricing.*`.
- New env key: also `backend/.env.example`, `backend/docker-compose.prod.yml` (it lists env keys by hand) and doc-11.
- New coded error: extend `ApiException`; the stream already sends `code` for any `ApiException`.

## Known gaps

- The iOS app has no sign-in screen yet, so a new sentence or uncached word from the app gives 401 until it is built (waits for a mockup).
- Sign-in emails go out only after `SES_FROM_EMAIL` and the AWS variables are set (doc-11, section 10). Before that, codes are only in the backend log.
- Rate limits and the code attempts live partly in memory: a restart clears the rate limits (the code attempts are in Postgres).
- The budget check and the usage write are not one atomic step, so a burst of parallel calls can pass the check together and go a little over the cap.
- If a stream breaks half way, the tokens already spent are not recorded (the usage is read from the finished message).
- The budget counts input and output tokens only. We use no prompt caching, so there is nothing else to count. Prompts over 100K tokens would cost more per token; ours are far smaller.
- `GET /api/me` does not return a display name or roles.
- Expired refresh tokens are deleted when someone signs in (a week after they expire), not by a timer.
- Migration numbers: V10 and V11. If another branch also added a V10 before this merged, renumber one of them.
