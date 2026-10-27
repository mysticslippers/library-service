CREATE TABLE auth_rate_limits (
    bucket_key CHAR(64) PRIMARY KEY,
    attempts INTEGER NOT NULL CHECK (attempts > 0),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_auth_rate_limits_expiration ON auth_rate_limits(expires_at);

ALTER TABLE notifications
    ADD COLUMN auth_token_id BIGINT REFERENCES auth_tokens(id) ON DELETE SET NULL,
    ADD COLUMN secret_expires_at TIMESTAMP,
    ADD COLUMN secret_purged_at TIMESTAMP;

UPDATE notifications n
SET auth_token_id = t.id, secret_expires_at = t.expires_at
FROM auth_tokens t
WHERE n.user_id = t.user_id AND position(t.token IN n.body) > 0
    AND ((n.type = 'ACCOUNT_ACTIVATION' AND t.type = 'ACCOUNT_ACTIVATION')
        OR (n.type = 'PASSWORD_RECOVERY' AND t.type = 'PASSWORD_RESET'));

UPDATE notifications SET secret_expires_at = created_at + INTERVAL '24 hours'
WHERE type IN ('ACCOUNT_ACTIVATION', 'PASSWORD_RECOVERY') AND secret_expires_at IS NULL;

CREATE INDEX idx_notifications_auth_cleanup ON notifications(user_id, secret_expires_at, created_at)
    WHERE type IN ('ACCOUNT_ACTIVATION', 'PASSWORD_RECOVERY') AND secret_purged_at IS NULL;
CREATE INDEX idx_auth_tokens_used_at ON auth_tokens(used_at) WHERE used_at IS NOT NULL;
