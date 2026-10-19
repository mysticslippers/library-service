ALTER TYPE notification_status ADD VALUE 'SENDING';
ALTER TYPE notification_status ADD VALUE 'UNKNOWN';

ALTER TABLE notifications
    ADD COLUMN sending_started_at TIMESTAMP,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
