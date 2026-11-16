CREATE TABLE IF NOT EXISTS brokers (
    broker_id SMALLINT PRIMARY KEY,
    name      TEXT     NOT NULL UNIQUE
);

INSERT INTO brokers (broker_id, name)
SELECT 1, 'binance'
WHERE NOT EXISTS (SELECT 1 FROM brokers WHERE broker_id = 1);

-- followed_at is NULL when only the candles of the ticker are stored.
CREATE TABLE IF NOT EXISTS ticker (
    id          BIGSERIAL   PRIMARY KEY,
    broker_id   SMALLINT    NOT NULL REFERENCES brokers (broker_id),
    symbol      TEXT        NOT NULL,
    followed_at TIMESTAMPTZ,
    UNIQUE (broker_id, symbol)
);
