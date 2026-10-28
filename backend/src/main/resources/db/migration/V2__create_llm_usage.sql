-- One row per LLM call that really happened (and was paid). The gate reads this table:
--   today's money spent  -> sum(cost_usd) since midnight UTC
--   one IP's calls       -> count(*) for that ip in the last hour
CREATE TABLE llm_usage (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ip         VARCHAR(45)   NOT NULL, -- 45 = longest IPv6 text
    headword   VARCHAR(100)  NOT NULL,
    cost_usd   NUMERIC(10,6) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX llm_usage_created_at_idx ON llm_usage (created_at);
CREATE INDEX llm_usage_ip_created_at_idx ON llm_usage (ip, created_at);
