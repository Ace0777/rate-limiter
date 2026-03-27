CREATE TABLE clients (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_key    VARCHAR(64)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    plan_id    UUID         NOT NULL REFERENCES plans(id),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clients_api_key ON clients(api_key);