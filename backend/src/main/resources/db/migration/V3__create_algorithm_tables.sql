CREATE TYPE algorithm_type AS ENUM ('SIMPLE_MOVING_AVERAGE');

-- One row per variant, never changed. Settings JSON has sorted keys and plain numbers, so equal
-- settings give equal text.
CREATE TABLE algorithm (
    id                BIGSERIAL      PRIMARY KEY,
    algorithm_type    algorithm_type NOT NULL,
    timeframe_seconds INTEGER        NOT NULL CHECK (timeframe_seconds > 0),
    settings          TEXT           NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL,
    UNIQUE (algorithm_type, timeframe_seconds, settings)
);
