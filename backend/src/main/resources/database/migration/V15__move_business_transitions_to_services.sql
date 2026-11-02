DROP TRIGGER trg_reservation_created_set_copy_reserved ON reservations;
DROP TRIGGER trg_reservation_closed_release_copy ON reservations;
DROP TRIGGER trg_loan_created_set_copy_loaned ON loans;
DROP TRIGGER trg_loan_closed_update_copy_status ON loans;
DROP TRIGGER trg_payment_success_mark_fine_paid ON payment_transactions;

DROP FUNCTION reservation_created_set_copy_reserved();
DROP FUNCTION reservation_closed_release_copy();
DROP FUNCTION loan_created_set_copy_loaned();
DROP FUNCTION loan_closed_update_copy_status();
DROP FUNCTION payment_success_mark_fine_paid();

CREATE UNIQUE INDEX uq_loans_one_per_reservation ON loans (reservation_id) WHERE reservation_id IS NOT NULL;

CREATE OR REPLACE FUNCTION guard_material_copy_circulation() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.branch_id IS DISTINCT FROM OLD.branch_id OR NEW.material_id IS DISTINCT FROM OLD.material_id)
        AND (EXISTS (SELECT 1 FROM loans WHERE copy_id = NEW.id AND status IN ('ACTIVE', 'OVERDUE', 'LOST'))
            OR EXISTS (SELECT 1 FROM reservations WHERE copy_id = NEW.id AND status IN ('ACTIVE', 'READY_FOR_PICKUP'))) THEN
        RAISE EXCEPTION 'Cannot move a copy with active circulation operations' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION assert_copy_circulation(p_copy_id BIGINT) RETURNS VOID
    LANGUAGE plpgsql AS $$
DECLARE
    v_copy material_copies%ROWTYPE;
    v_loans INTEGER;
    v_lost INTEGER;
    v_reservations INTEGER;
BEGIN
    SELECT * INTO v_copy FROM material_copies WHERE id = p_copy_id FOR NO KEY UPDATE;
    IF NOT FOUND THEN
        RETURN;
    END IF;

    SELECT count(*), count(*) FILTER (WHERE status = 'LOST') INTO v_loans, v_lost
    FROM loans WHERE copy_id = p_copy_id AND status IN ('ACTIVE', 'OVERDUE', 'LOST');
    SELECT count(*) INTO v_reservations FROM reservations
    WHERE copy_id = p_copy_id AND status IN ('ACTIVE', 'READY_FOR_PICKUP');

    IF v_loans > 1 OR v_reservations > 1 OR (v_loans > 0 AND v_reservations > 0)
        OR (v_loans = 1 AND v_lost = 0 AND v_copy.status <> 'LOANED')
        OR (v_lost = 1 AND v_copy.status <> 'LOST')
        OR (v_reservations = 1 AND v_copy.status <> 'RESERVED')
        OR (v_loans = 0 AND v_reservations = 0 AND v_copy.status IN ('LOANED', 'RESERVED')) THEN
        RAISE EXCEPTION 'Copy status conflicts with its circulation operations' USING ERRCODE = '23514';
    END IF;

    IF EXISTS (SELECT 1 FROM reservations r WHERE r.copy_id = p_copy_id
               AND r.status IN ('ACTIVE', 'READY_FOR_PICKUP')
               AND (r.material_id <> v_copy.material_id OR r.branch_id <> v_copy.branch_id))
        OR EXISTS (SELECT 1 FROM loans l WHERE l.copy_id = p_copy_id
                   AND l.status IN ('ACTIVE', 'OVERDUE', 'LOST') AND l.branch_id <> v_copy.branch_id) THEN
        RAISE EXCEPTION 'Circulation operation does not match its copy' USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        SELECT 1 FROM loans l JOIN reservations r ON r.id = l.reservation_id
        WHERE l.copy_id = p_copy_id AND l.status IN ('ACTIVE', 'OVERDUE', 'LOST')
            AND (r.status <> 'USED' OR r.copy_id <> l.copy_id OR r.user_id <> l.user_id
                 OR r.branch_id <> l.branch_id OR r.material_id <> v_copy.material_id)
    ) THEN
        RAISE EXCEPTION 'Loan requires its matching used reservation' USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE FUNCTION check_copy_circulation_at_commit() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_TABLE_NAME = 'material_copies' THEN
        IF TG_OP <> 'DELETE' THEN
            PERFORM assert_copy_circulation(NEW.id);
        END IF;
    ELSE
        IF TG_OP <> 'DELETE' THEN
            PERFORM assert_copy_circulation(NEW.copy_id);
        END IF;
        IF TG_OP = 'DELETE' OR (TG_OP = 'UPDATE' AND OLD.copy_id IS DISTINCT FROM NEW.copy_id) THEN
            PERFORM assert_copy_circulation(OLD.copy_id);
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_copies_consistent_at_commit
    AFTER INSERT OR UPDATE OR DELETE ON material_copies
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_copy_circulation_at_commit();
CREATE CONSTRAINT TRIGGER trg_reservations_consistent_at_commit
    AFTER INSERT OR UPDATE OR DELETE ON reservations
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_copy_circulation_at_commit();
CREATE CONSTRAINT TRIGGER trg_loans_consistent_at_commit
    AFTER INSERT OR UPDATE OR DELETE ON loans
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_copy_circulation_at_commit();

CREATE FUNCTION assert_fine_payment_consistency(p_fine_id BIGINT) RETURNS VOID
    LANGUAGE plpgsql AS $$
DECLARE
    v_status fine_status;
BEGIN
    SELECT status INTO v_status FROM fines WHERE id = p_fine_id FOR NO KEY UPDATE;
    IF FOUND AND v_status <> 'PAID' AND EXISTS (
        SELECT 1 FROM payment_transactions WHERE fine_id = p_fine_id AND status = 'SUCCESS'
    ) THEN
        RAISE EXCEPTION 'Successful payment must have a paid fine' USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE FUNCTION check_fine_payment_at_commit() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_TABLE_NAME = 'fines' THEN
        IF TG_OP <> 'DELETE' THEN
            PERFORM assert_fine_payment_consistency(NEW.id);
        END IF;
    ELSE
        IF TG_OP <> 'DELETE' THEN
            PERFORM assert_fine_payment_consistency(NEW.fine_id);
        END IF;
        IF TG_OP = 'DELETE' OR (TG_OP = 'UPDATE' AND OLD.fine_id IS DISTINCT FROM NEW.fine_id) THEN
            PERFORM assert_fine_payment_consistency(OLD.fine_id);
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_fines_consistent_at_commit
    AFTER INSERT OR UPDATE OR DELETE ON fines
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_fine_payment_at_commit();
CREATE CONSTRAINT TRIGGER trg_payments_consistent_at_commit
    AFTER INSERT OR UPDATE OR DELETE ON payment_transactions
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_fine_payment_at_commit();

DO $$
DECLARE
    v_id BIGINT;
BEGIN
    FOR v_id IN SELECT id FROM material_copies ORDER BY id LOOP
        PERFORM assert_copy_circulation(v_id);
    END LOOP;
    FOR v_id IN SELECT DISTINCT fine_id FROM payment_transactions WHERE status = 'SUCCESS' ORDER BY fine_id LOOP
        PERFORM assert_fine_payment_consistency(v_id);
    END LOOP;
END;
$$;

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = timezone('UTC', clock_timestamp());
    RETURN NEW;
END;
$$;

DO $$
DECLARE
    v_column RECORD;
BEGIN
    FOR v_column IN
        SELECT table_name, column_name FROM information_schema.columns
        WHERE table_schema = current_schema() AND data_type = 'timestamp without time zone'
            AND table_name <> 'flyway_schema_history'
            AND column_default IN ('CURRENT_TIMESTAMP', 'now()')
        ORDER BY table_name, column_name
    LOOP
        EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN %I SET DEFAULT timezone(''UTC'', CURRENT_TIMESTAMP)',
                       current_schema(), v_column.table_name, v_column.column_name);
    END LOOP;
END;
$$;

CREATE OR REPLACE FUNCTION guard_fine_payment_state() RETURNS TRIGGER
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

