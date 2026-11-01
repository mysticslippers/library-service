CREATE INDEX idx_reservations_pending_deadline
    ON reservations (expires_at, id)
    WHERE status IN ('ACTIVE', 'READY_FOR_PICKUP');

CREATE INDEX idx_loans_pending_deadline
    ON loans (due_at, id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_user_blocks_pending_deadline
    ON user_blocks (expires_at, id)
    WHERE status = 'ACTIVE' AND expires_at IS NOT NULL;

CREATE INDEX idx_user_warnings_pending_deadline
    ON user_warnings (expires_at, id)
    WHERE status = 'ACTIVE' AND expires_at IS NOT NULL;
