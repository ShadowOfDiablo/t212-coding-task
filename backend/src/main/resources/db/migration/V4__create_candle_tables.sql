-- Historical candles, downloaded once from the exchange and replayed by training runs.

CREATE TABLE IF NOT EXISTS ticker (
    id     BIGSERIAL PRIMARY KEY,
    broker TEXT      NOT NULL,
    symbol TEXT      NOT NULL,
    UNIQUE (broker, symbol)
);

-- The primary key is the index a period is read through. Its first and last row per ticker and
-- interval mark the stored range that downloads extend.
CREATE TABLE IF NOT EXISTS candle (
    ticker_id       BIGINT      NOT NULL REFERENCES ticker (id),
    candle_interval TEXT        NOT NULL,
    open_time       TIMESTAMPTZ NOT NULL,
    close_time      TIMESTAMPTZ NOT NULL,
    open            NUMERIC     NOT NULL,
    high            NUMERIC     NOT NULL,
    low             NUMERIC     NOT NULL,
    close           NUMERIC     NOT NULL,
    volume          NUMERIC     NOT NULL,
    PRIMARY KEY (ticker_id, candle_interval, open_time)
);
