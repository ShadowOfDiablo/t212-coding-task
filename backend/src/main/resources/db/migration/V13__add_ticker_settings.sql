-- active_algorithm becomes each ticker's assigned settings: who chose them, the ticker's own budget
-- in USDT, and the run of its last successful auto-tune. NULL on rows written before.
ALTER TABLE active_algorithm ADD COLUMN IF NOT EXISTS settings_source settings_source;
ALTER TABLE active_algorithm ADD COLUMN IF NOT EXISTS budget NUMERIC;
ALTER TABLE active_algorithm ADD COLUMN IF NOT EXISTS tuned_run_id BIGINT REFERENCES algorithm_run (id);
