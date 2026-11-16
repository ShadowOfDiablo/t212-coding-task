-- Times the exchange account went back to the demo starting balances, found from the balances.
CREATE TABLE IF NOT EXISTS account_reset (
    id        BIGSERIAL   PRIMARY KEY,
    broker_id SMALLINT    NOT NULL REFERENCES brokers (broker_id),
    reset_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS account_reset_broker_idx ON account_reset (broker_id, reset_at);
