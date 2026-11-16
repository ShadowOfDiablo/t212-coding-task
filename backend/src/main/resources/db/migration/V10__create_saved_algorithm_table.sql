-- Settings variants the user saved to pick again, under a name they chose. Removing an entry keeps
-- the variant, so the runs that used it keep their settings. An entry keeps its number for good,
-- so deleting one never renumbers the others.
CREATE TABLE IF NOT EXISTS saved_algorithm (
    id           BIGSERIAL   PRIMARY KEY,
    number       INTEGER     NOT NULL UNIQUE,
    algorithm_id BIGINT      NOT NULL UNIQUE REFERENCES algorithm (id),
    name         TEXT        NOT NULL,
    saved_at     TIMESTAMPTZ NOT NULL
);
