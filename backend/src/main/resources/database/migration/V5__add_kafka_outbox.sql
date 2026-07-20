CREATE TABLE outbox_events (
    id                UUID PRIMARY KEY,
    topic             VARCHAR(255) NOT NULL,
    event_key         VARCHAR(255) NOT NULL,
    event_type        VARCHAR(255) NOT NULL,
    event_version     INTEGER NOT NULL,
    aggregate_type    VARCHAR(100) NOT NULL,
    aggregate_id      VARCHAR(255) NOT NULL,
    event_json        TEXT NOT NULL,
    occurred_at       TIMESTAMPTZ NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at      TIMESTAMPTZ,
    attempt_count     INTEGER NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ,
    last_error        TEXT,

    CONSTRAINT chk_outbox_events_version
        CHECK (event_version > 0),

    CONSTRAINT chk_outbox_events_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE INDEX idx_outbox_events_pending
    ON outbox_events (next_attempt_at, occurred_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_outbox_events_aggregate
    ON outbox_events (aggregate_type, aggregate_id, occurred_at);

CREATE TABLE processed_events (
    id                BIGSERIAL PRIMARY KEY,
    event_id          UUID NOT NULL,
    consumer_name     VARCHAR(255) NOT NULL,
    processed_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_processed_events_consumer_event
        UNIQUE (consumer_name, event_id)
);

CREATE INDEX idx_processed_events_processed_at
    ON processed_events (processed_at);
