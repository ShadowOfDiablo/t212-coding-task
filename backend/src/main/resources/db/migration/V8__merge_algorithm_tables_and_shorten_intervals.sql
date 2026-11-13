-- One row per algorithm variant: its type (1 = simple moving average), timeframe code and every
-- other setting as JSON text. Rows are never changed; new settings are a new row. The JSON has
-- sorted keys, plain numbers without trailing zeros and no empty settings, so identical settings
-- always have identical text.
CREATE TABLE IF NOT EXISTS algorithm (
    id             BIGSERIAL   PRIMARY KEY,
    algorithm_type SMALLINT    NOT NULL,
    timeframe      TEXT        NOT NULL,
    settings       TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    UNIQUE (algorithm_type, timeframe, settings)
);

INSERT INTO algorithm (id, algorithm_type, timeframe, settings, created_at)
SELECT s.id,
       -- Databases created before V5 stored the id as a number still hold the algorithm's name.
       CASE s.algorithm_id::text
           WHEN '1' THEN 1
           WHEN 'example-strategy-SMA' THEN 1
       END,
       CASE s.timeframe
           WHEN 'ONE_SECOND' THEN '1s'
           WHEN 'ONE_MINUTE' THEN '1m'
           WHEN 'FIVE_MINUTES' THEN '5m'
           WHEN 'FIFTEEN_MINUTES' THEN '15m'
           WHEN 'ONE_HOUR' THEN '1h'
           WHEN 'FOUR_HOURS' THEN '4h'
           WHEN 'ONE_DAY' THEN '1d'
       END,
       '{' || concat_ws(',',
                        '"liveOnTick":' || s.live_on_tick::text,
                        '"orderSizePercent":' || trim_scale(s.order_size_percent)::text,
                        '"riskPerTradePercent":' || trim_scale(s.risk_per_trade_percent)::text,
                        '"stopLossPercent":' || trim_scale(s.stop_loss_percent)::text,
                        '"timePeriod":' || s.time_period::text,
                        '"updateTimeSeconds":' || s.update_time_seconds::text) || '}',
       COALESCE((SELECT MIN(r.recorded_at) FROM algorithm_run_settings r
                 WHERE r.settings_id = s.id), now())
FROM algorithm_settings s;

SELECT setval(pg_get_serial_sequence('algorithm', 'id'),
              COALESCE((SELECT MAX(id) FROM algorithm), 1),
              (SELECT MAX(id) FROM algorithm) IS NOT NULL);

-- A run uses one variant. A run that changed settings keeps the ones it started with.
ALTER TABLE algorithm_run ADD COLUMN IF NOT EXISTS algorithm_id BIGINT REFERENCES algorithm (id);
UPDATE algorithm_run r
SET algorithm_id = (SELECT s.settings_id FROM algorithm_run_settings s
                    WHERE s.run_id = r.id
                    ORDER BY s.recorded_at, s.event DESC
                    LIMIT 1);
DELETE FROM training_portfolio_point
WHERE run_id IN (SELECT id FROM algorithm_run WHERE algorithm_id IS NULL);
DELETE FROM algorithm_run WHERE algorithm_id IS NULL;
ALTER TABLE algorithm_run ALTER COLUMN algorithm_id SET NOT NULL;

-- The variant the live bot last traded each ticker with.
CREATE TABLE IF NOT EXISTS active_algorithm (
    broker       TEXT        NOT NULL,
    ticker       TEXT        NOT NULL,
    algorithm_id BIGINT      NOT NULL REFERENCES algorithm (id),
    activated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (broker, ticker)
);

INSERT INTO active_algorithm (broker, ticker, algorithm_id, activated_at)
SELECT DISTINCT ON (broker, ticker) broker, ticker, algorithm_id, started_at
FROM algorithm_run
WHERE mode = 'TRADING'
ORDER BY broker, ticker, started_at DESC;

DROP TABLE IF EXISTS algorithm_run_settings;
DROP TABLE IF EXISTS sma_settings;
DROP TABLE IF EXISTS algorithm_settings;

-- Candle intervals are stored as short codes, like the timeframe of an algorithm.
UPDATE candle
SET candle_interval = CASE candle_interval
                          WHEN 'ONE_SECOND' THEN '1s'
                          WHEN 'ONE_MINUTE' THEN '1m'
                          WHEN 'FIVE_MINUTES' THEN '5m'
                          WHEN 'FIFTEEN_MINUTES' THEN '15m'
                          WHEN 'ONE_HOUR' THEN '1h'
                          WHEN 'FOUR_HOURS' THEN '4h'
                          WHEN 'ONE_DAY' THEN '1d'
                          ELSE candle_interval
                      END;

-- The bot's positions, kept as FIFO lots. A position is found by the run it opened in and the time
-- of its first buy, so a position written again is updated, not duplicated.
CREATE TABLE IF NOT EXISTS position (
    id           BIGSERIAL   PRIMARY KEY,
    run_id       BIGINT      NOT NULL REFERENCES algorithm_run (id),
    broker       TEXT        NOT NULL,
    ticker       TEXT        NOT NULL,
    status       TEXT        NOT NULL,
    opened_at    TIMESTAMPTZ NOT NULL,
    closed_at    TIMESTAMPTZ,
    cost         NUMERIC     NOT NULL,
    realised_pnl NUMERIC     NOT NULL,
    mae          NUMERIC     NOT NULL,
    mfe          NUMERIC     NOT NULL,
    UNIQUE (run_id, opened_at)
);
CREATE INDEX IF NOT EXISTS position_open_idx ON position (broker, ticker, status);

-- Buys that still hold coins, each with the exchange order id of its buy. A lot that is sold
-- completely is deleted.
CREATE TABLE IF NOT EXISTS open_lot (
    id           BIGSERIAL   PRIMARY KEY,
    position_id  BIGINT      NOT NULL REFERENCES position (id),
    run_id       BIGINT      NOT NULL REFERENCES algorithm_run (id),
    buy_order_id BIGINT      NOT NULL,
    bought_at    TIMESTAMPTZ NOT NULL,
    quantity     NUMERIC     NOT NULL,
    entry_price  NUMERIC     NOT NULL
);
CREATE INDEX IF NOT EXISTS open_lot_position_idx ON open_lot (position_id);

-- One row per lot, or part of a lot, that a sell used: the trades of one sell share its
-- sell_order_id, and each points at the buy of its lot. Live order ids are Binance's orderId;
-- training ones come from the virtual account. Rows are only ever added.
CREATE TABLE IF NOT EXISTS trade (
    id            BIGSERIAL   PRIMARY KEY,
    position_id   BIGINT      NOT NULL REFERENCES position (id),
    run_id        BIGINT      NOT NULL REFERENCES algorithm_run (id),
    buy_order_id  BIGINT      NOT NULL,
    sell_order_id BIGINT      NOT NULL,
    bought_at     TIMESTAMPTZ NOT NULL,
    sold_at       TIMESTAMPTZ NOT NULL,
    quantity      NUMERIC     NOT NULL,
    entry_price   NUMERIC     NOT NULL,
    exit_price    NUMERIC     NOT NULL,
    fee           NUMERIC     NOT NULL,
    realised_pnl  NUMERIC     NOT NULL
);
CREATE INDEX IF NOT EXISTS trade_run_idx ON trade (run_id, sold_at);
