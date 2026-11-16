-- The run that placed the order; NULL for an order placed by hand, such as a test buy or sell.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS run_id BIGINT REFERENCES algorithm_run (id);
