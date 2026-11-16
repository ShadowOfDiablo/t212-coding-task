DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'settings_source') THEN
        CREATE TYPE settings_source AS ENUM ('MANUAL', 'AUTOMATIC');
    END IF;
END $$;

-- Who chose the run's algorithm variant: the user or the tuner.
ALTER TABLE algorithm_run
    ADD COLUMN IF NOT EXISTS settings_source settings_source NOT NULL DEFAULT 'MANUAL';
