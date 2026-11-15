CREATE TYPE settings_source AS ENUM ('MANUAL', 'AUTOMATIC');

-- Who chose the run's algorithm variant: the user or the tuner.
ALTER TABLE algorithm_run
    ADD COLUMN settings_source settings_source NOT NULL DEFAULT 'MANUAL';
