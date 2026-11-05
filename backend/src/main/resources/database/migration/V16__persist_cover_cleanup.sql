CREATE TABLE cover_cleanup (
    object_key VARCHAR(512) PRIMARY KEY,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0)
);

CREATE INDEX ix_cover_cleanup_due ON cover_cleanup (next_attempt_at, object_key);
