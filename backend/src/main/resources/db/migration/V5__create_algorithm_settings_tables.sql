-- Settings of trading algorithms. Identical settings are stored once and reused by every run that
-- uses them. NULL in an optional column means "not set": every check for it is skipped.
CREATE TABLE IF NOT EXISTS algorithm_settings (
    id                     BIGSERIAL PRIMARY KEY,
    algorithm_id           SMALLINT  NOT NULL,
    time_period            INT       NOT NULL,
    timeframe              TEXT      NOT NULL,
    live_on_tick           BOOLEAN   NOT NULL,
    -- NULL when live_on_tick is true
    update_time_seconds    INT,
    order_size_percent     NUMERIC   NOT NULL,
    risk_per_trade_percent NUMERIC,
    stop_loss_percent      NUMERIC
);

CREATE INDEX IF NOT EXISTS algorithm_settings_lookup_idx
    ON algorithm_settings (algorithm_id, time_period, timeframe);

-- One table per algorithm type holds its own optional settings. Its key is also the key of the
-- shared row, so the shared table never changes when an algorithm is added.
CREATE TABLE IF NOT EXISTS sma_settings (
    settings_id BIGINT PRIMARY KEY REFERENCES algorithm_settings (id)
);
