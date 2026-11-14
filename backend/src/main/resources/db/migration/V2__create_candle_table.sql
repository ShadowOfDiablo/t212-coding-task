-- The close time is open_time + interval - 1 ms. The first and last row per ticker and interval
-- mark the stored range that downloads extend.
CREATE TABLE candle (
    ticker_id        BIGINT      NOT NULL REFERENCES ticker (id),
    interval_seconds INTEGER     NOT NULL CHECK (interval_seconds > 0),
    open_time        TIMESTAMPTZ NOT NULL,
    open             NUMERIC     NOT NULL,
    high             NUMERIC     NOT NULL,
    low              NUMERIC     NOT NULL,
    close            NUMERIC     NOT NULL,
    volume           NUMERIC     NOT NULL,
    PRIMARY KEY (ticker_id, interval_seconds, open_time)
);
