CREATE TABLE plans (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(50)  NOT NULL UNIQUE,  -- 'free', 'pro', 'enterprise'
    requests_per_minute INT          NOT NULL,
    requests_per_hour   INT          NOT NULL,
    requests_per_day    INT          NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);