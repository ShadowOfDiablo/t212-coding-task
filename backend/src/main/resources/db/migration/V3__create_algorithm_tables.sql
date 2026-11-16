-- PostgreSQL has no CREATE TYPE IF NOT EXISTS, so each enum type is created only when missing.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'algorithm_type') THEN
        CREATE TYPE algorithm_type AS ENUM ('SIMPLE_MOVING_AVERAGE');
    END IF;
END $$;

-- One row per variant, never changed. Settings JSON has sorted keys and plain numbers, so equal
-- settings give equal text.
CREATE TABLE IF NOT EXISTS algorithm (
    id                BIGSERIAL      PRIMARY KEY,
    algorithm_type    algorithm_type NOT NULL,
    timeframe_seconds INTEGER        NOT NULL CHECK (timeframe_seconds > 0),
    settings          TEXT           NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL,
    UNIQUE (algorithm_type, timeframe_seconds, settings)
);
