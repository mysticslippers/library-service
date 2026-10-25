ALTER TABLE fines ALTER COLUMN created_at SET DEFAULT timezone('UTC', CURRENT_TIMESTAMP);
ALTER TABLE payment_transactions ALTER COLUMN created_at SET DEFAULT timezone('UTC', CURRENT_TIMESTAMP);

DROP TRIGGER trigger_payment_transactions_set_updated_at ON payment_transactions;

CREATE FUNCTION guard_payment_transaction() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
DECLARE
    v_fine fines%ROWTYPE;
BEGIN
    NEW.external_payment_id := NULLIF(btrim(NEW.external_payment_id), '');
    IF TG_OP = 'UPDATE' THEN
        IF ROW(NEW.id, NEW.fine_id, NEW.amount, NEW.created_at)
            IS DISTINCT FROM ROW(OLD.id, OLD.fine_id, OLD.amount, OLD.created_at) THEN
            RAISE EXCEPTION 'Payment identity and amount cannot be changed' USING ERRCODE = '23514';
        END IF;
        -- Skip the write itself, including timestamp and audit updates, on an identical replay.
        IF NEW IS NOT DISTINCT FROM OLD THEN
            RETURN NULL;
        END IF;
        IF OLD.status IN ('SUCCESS', 'DECLINED', 'CANCELLED', 'FAILED', 'TIMEOUT') THEN
            RAISE EXCEPTION 'Final payment transaction cannot be changed' USING ERRCODE = '23514';
        END IF;
        IF OLD.external_payment_id IS NOT NULL AND NEW.external_payment_id IS DISTINCT FROM OLD.external_payment_id THEN
            RAISE EXCEPTION 'External payment id cannot be changed once assigned' USING ERRCODE = '23514';
        END IF;
        IF NEW.updated_at IS DISTINCT FROM OLD.updated_at THEN
            RAISE EXCEPTION 'Payment update timestamp is managed by the database' USING ERRCODE = '23514';
        END IF;
        IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
            (OLD.status = 'CREATED' AND NEW.status IN ('PENDING', 'SUCCESS', 'CANCELLED', 'FAILED'))
            OR (OLD.status = 'PENDING' AND NEW.status IN ('SUCCESS', 'DECLINED', 'CANCELLED', 'FAILED', 'TIMEOUT'))
        ) THEN
            RAISE EXCEPTION 'Invalid payment status transition: % -> %', OLD.status, NEW.status USING ERRCODE = '23514';
        END IF;
    END IF;

    -- On INSERT this runs before FK checks and unique-index insertion. The lock is
    -- compatible with FK KEY SHARE and is also the first lock acquired by services.
    IF TG_OP = 'UPDATE' THEN
        -- A direct SQL UPDATE already holds the payment row. Reject an inverted
        -- lock order instead of deadlocking a service that holds the fine first.
        BEGIN
            SELECT * INTO v_fine FROM fines WHERE id = NEW.fine_id FOR NO KEY UPDATE NOWAIT;
        EXCEPTION WHEN lock_not_available THEN
            RAISE EXCEPTION 'Fine is being changed; retry the payment command' USING ERRCODE = '23514';
        END;
    ELSE
        SELECT * INTO v_fine FROM fines WHERE id = NEW.fine_id FOR NO KEY UPDATE;
    END IF;
    IF NOT FOUND OR v_fine.status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Payment requires an active fine' USING ERRCODE = '23514';
    END IF;
    IF NEW.amount <> v_fine.amount THEN
        RAISE EXCEPTION 'Payment amount must equal the fine amount' USING ERRCODE = '23514';
    END IF;
    IF EXISTS (SELECT 1 FROM payment_transactions WHERE fine_id = NEW.fine_id
               AND status = 'SUCCESS' AND id <> NEW.id) THEN
        RAISE EXCEPTION 'Fine already has a successful payment' USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'UPDATE' OR NEW.status <> 'CREATED' THEN
        NEW.updated_at := timezone('UTC', clock_timestamp());
    ELSE
        NEW.updated_at := NULL;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_payment_transactions_guard
    BEFORE INSERT OR UPDATE ON payment_transactions FOR EACH ROW EXECUTE FUNCTION guard_payment_transaction();

CREATE FUNCTION guard_fine_payment_state() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF ROW(NEW.id, NEW.user_id, NEW.loan_id, NEW.copy_id, NEW.tariff_id, NEW.reason, NEW.amount, NEW.created_at)
            IS DISTINCT FROM ROW(OLD.id, OLD.user_id, OLD.loan_id, OLD.copy_id, OLD.tariff_id, OLD.reason, OLD.amount, OLD.created_at) THEN
            RAISE EXCEPTION 'Fine identity and amount cannot be changed' USING ERRCODE = '23514';
        END IF;
        IF NEW IS NOT DISTINCT FROM OLD THEN
            RETURN NULL;
        END IF;
        IF OLD.status IN ('PAID', 'CANCELLED') THEN
            RAISE EXCEPTION 'Closed fine cannot be changed' USING ERRCODE = '23514';
        END IF;
    END IF;

    IF NEW.status = 'ACTIVE' AND (NEW.paid_at IS NOT NULL OR NEW.cancelled_at IS NOT NULL OR NEW.cancellation_reason IS NOT NULL) THEN
        RAISE EXCEPTION 'Active fine cannot have closure metadata' USING ERRCODE = '23514';
    END IF;
    IF NEW.status IN ('PAID', 'CANCELLED') THEN
        IF EXISTS (SELECT 1 FROM payment_transactions WHERE fine_id = NEW.id AND status IN ('CREATED', 'PENDING')) THEN
            RAISE EXCEPTION 'Fine has a payment transaction in progress' USING ERRCODE = '23514';
        END IF;
        IF NEW.status = 'CANCELLED' AND EXISTS (
            SELECT 1 FROM payment_transactions WHERE fine_id = NEW.id AND status = 'SUCCESS') THEN
            RAISE EXCEPTION 'Fine with a successful payment cannot be cancelled' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF NEW.status = 'PAID' AND NEW.cancellation_reason IS NOT NULL THEN
        RAISE EXCEPTION 'Paid fine cannot have a cancellation reason' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_fines_guard_payment_state
    BEFORE INSERT OR UPDATE ON fines FOR EACH ROW EXECUTE FUNCTION guard_fine_payment_state();

CREATE OR REPLACE FUNCTION payment_success_mark_fine_paid() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status <> 'SUCCESS' THEN
        RETURN NEW;
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;

    UPDATE fines SET status = 'PAID', paid_at = timezone('UTC', clock_timestamp())
    WHERE id = NEW.fine_id AND status = 'ACTIVE';
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Successful payment requires an active fine' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER trg_payment_success_mark_fine_paid ON payment_transactions;
CREATE TRIGGER trg_payment_success_mark_fine_paid
    AFTER INSERT OR UPDATE ON payment_transactions FOR EACH ROW EXECUTE FUNCTION payment_success_mark_fine_paid();
