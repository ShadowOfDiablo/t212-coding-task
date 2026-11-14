CREATE TABLE brokers (
    broker_id SMALLINT PRIMARY KEY,
    name      TEXT     NOT NULL UNIQUE
);

INSERT INTO brokers (broker_id, name) VALUES (1, 'binance');

-- followed_at is NULL when only the candles of the ticker are stored.
CREATE TABLE ticker (
    id          BIGSERIAL   PRIMARY KEY,
    broker_id   SMALLINT    NOT NULL REFERENCES brokers (broker_id),
    symbol      TEXT        NOT NULL,
    followed_at TIMESTAMPTZ,
    UNIQUE (broker_id, symbol)
);
