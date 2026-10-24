-- TASK-105: account management in the admin.
-- A blocked account keeps cached and bundled answers but gets no new Claude call (ClaudeGate, 403 account_blocked).
ALTER TABLE users ADD COLUMN blocked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN blocked_reason VARCHAR(300);
-- Saved on sign-in and on token refresh, at most once an hour. Empty until the account signs in again.
ALTER TABLE users ADD COLUMN last_seen_at TIMESTAMP WITH TIME ZONE;

-- Who asked for the call. Empty for old rows (before TASK-105), the night job and calls without a signed-in user.
-- No foreign key on purpose: deleting an account keeps its usage rows for the cost history, without the person.
ALTER TABLE claude_usage ADD COLUMN user_id BIGINT;
CREATE INDEX idx_claude_usage_user_created ON claude_usage (user_id, created_at);
