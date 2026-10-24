CREATE FUNCTION lock_circulation_copy_before_insert() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    PERFORM 1 FROM users WHERE id = NEW.user_id FOR KEY SHARE;
    IF TG_TABLE_NAME = 'loans' THEN
        PERFORM 1 FROM users WHERE id = NEW.issued_by_user_id FOR KEY SHARE;
    END IF;
    PERFORM 1 FROM material_copies WHERE id = NEW.copy_id FOR NO KEY UPDATE;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_reservations_lock_copy_before_insert
    BEFORE INSERT ON reservations FOR EACH ROW EXECUTE FUNCTION lock_circulation_copy_before_insert();
CREATE TRIGGER trg_loans_lock_copy_before_insert
    BEFORE INSERT ON loans FOR EACH ROW EXECUTE FUNCTION lock_circulation_copy_before_insert();

CREATE OR REPLACE FUNCTION reservation_created_set_copy_reserved() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
DECLARE
    v_copy material_copies%ROWTYPE;
BEGIN
    IF NEW.status IN ('ACTIVE', 'READY_FOR_PICKUP') THEN
        SELECT * INTO v_copy FROM material_copies WHERE id = NEW.copy_id FOR NO KEY UPDATE;
        IF NOT FOUND OR v_copy.status <> 'AVAILABLE'
            OR v_copy.material_id <> NEW.material_id OR v_copy.branch_id <> NEW.branch_id THEN
            RAISE EXCEPTION 'Copy is unavailable or does not match reservation material/branch' USING ERRCODE = '23514';
        END IF;
        IF EXISTS (SELECT 1 FROM reservations WHERE copy_id = NEW.copy_id AND id <> NEW.id
                   AND status IN ('ACTIVE', 'READY_FOR_PICKUP'))
            OR EXISTS (SELECT 1 FROM loans WHERE copy_id = NEW.copy_id AND status IN ('ACTIVE', 'OVERDUE', 'LOST')) THEN
            RAISE EXCEPTION 'Copy has an existing circulation operation' USING ERRCODE = '23514';
        END IF;
        UPDATE material_copies SET status = 'RESERVED' WHERE id = NEW.copy_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION loan_created_set_copy_loaned() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
DECLARE
    v_copy material_copies%ROWTYPE;
    v_reservation reservations%ROWTYPE;
BEGIN
    IF NEW.status IN ('ACTIVE', 'OVERDUE', 'LOST') THEN
        SELECT * INTO v_copy FROM material_copies WHERE id = NEW.copy_id FOR NO KEY UPDATE;
        IF NOT FOUND OR v_copy.branch_id <> NEW.branch_id THEN
            RAISE EXCEPTION 'Copy does not belong to the loan branch' USING ERRCODE = '23514';
        END IF;
        IF EXISTS (SELECT 1 FROM loans WHERE copy_id = NEW.copy_id AND id <> NEW.id
                   AND status IN ('ACTIVE', 'OVERDUE', 'LOST')) THEN
            RAISE EXCEPTION 'Copy already has an unresolved loan' USING ERRCODE = '23514';
        END IF;

        IF NEW.reservation_id IS NULL THEN
            IF v_copy.status <> 'AVAILABLE' OR EXISTS (
                SELECT 1 FROM reservations WHERE copy_id = NEW.copy_id AND status IN ('ACTIVE', 'READY_FOR_PICKUP')) THEN
                RAISE EXCEPTION 'Unreserved loan requires an available, unreserved copy' USING ERRCODE = '23514';
            END IF;
        ELSE
            SELECT * INTO v_reservation FROM reservations WHERE id = NEW.reservation_id FOR NO KEY UPDATE;
            IF NOT FOUND OR v_copy.status <> 'RESERVED'
                OR v_reservation.status <> 'READY_FOR_PICKUP'
                OR v_reservation.expires_at <= timezone('UTC', clock_timestamp())
                OR v_reservation.copy_id <> NEW.copy_id
                OR v_reservation.material_id <> v_copy.material_id
                OR v_reservation.user_id <> NEW.user_id
                OR v_reservation.branch_id <> NEW.branch_id THEN
                RAISE EXCEPTION 'Loan requires a matching, unexpired ready reservation' USING ERRCODE = '23514';
            END IF;
            IF EXISTS (SELECT 1 FROM reservations WHERE copy_id = NEW.copy_id AND id <> NEW.reservation_id
                       AND status IN ('ACTIVE', 'READY_FOR_PICKUP')) THEN
                RAISE EXCEPTION 'Copy has another active reservation' USING ERRCODE = '23514';
            END IF;
            UPDATE reservations SET status = 'USED' WHERE id = NEW.reservation_id;
        END IF;
        UPDATE material_copies
        SET status = CASE WHEN NEW.status = 'LOST' THEN 'LOST'::copy_status ELSE 'LOANED'::copy_status END
        WHERE id = NEW.copy_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION reservation_closed_release_copy() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status <> NEW.status AND NEW.status IN ('CANCELLED_BY_USER', 'CANCELLED_BY_LIBRARIAN', 'EXPIRED') THEN
        PERFORM 1 FROM material_copies WHERE id = NEW.copy_id FOR NO KEY UPDATE;
        IF NOT EXISTS (SELECT 1 FROM loans WHERE copy_id = NEW.copy_id AND status IN ('ACTIVE', 'OVERDUE', 'LOST'))
            AND NOT EXISTS (SELECT 1 FROM reservations WHERE copy_id = NEW.copy_id AND status IN ('ACTIVE', 'READY_FOR_PICKUP')) THEN
            UPDATE material_copies SET status = 'AVAILABLE' WHERE id = NEW.copy_id AND status = 'RESERVED';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION loan_closed_update_copy_status() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;
    IF NEW.status IN ('RETURNED', 'CANCELLED') THEN
        PERFORM 1 FROM material_copies WHERE id = NEW.copy_id FOR NO KEY UPDATE;
        IF NOT EXISTS (SELECT 1 FROM loans WHERE copy_id = NEW.copy_id AND status IN ('ACTIVE', 'OVERDUE', 'LOST'))
            AND NOT EXISTS (SELECT 1 FROM reservations WHERE copy_id = NEW.copy_id AND status IN ('ACTIVE', 'READY_FOR_PICKUP')) THEN
            UPDATE material_copies SET status = 'AVAILABLE' WHERE id = NEW.copy_id AND status IN ('LOANED', 'LOST');
        END IF;
    ELSIF NEW.status = 'LOST' THEN
        UPDATE material_copies SET status = 'LOST' WHERE id = NEW.copy_id AND status = 'LOANED';
    END IF;
    RETURN NEW;
END;
$$;


CREATE FUNCTION guard_material_copy_circulation() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
DECLARE
    v_loaned boolean;
    v_lost boolean;
    v_reserved boolean;
BEGIN
    SELECT EXISTS (SELECT 1 FROM loans WHERE copy_id = NEW.id AND status IN ('ACTIVE', 'OVERDUE')) INTO v_loaned;
    SELECT EXISTS (SELECT 1 FROM loans WHERE copy_id = NEW.id AND status = 'LOST') INTO v_lost;
    SELECT EXISTS (SELECT 1 FROM reservations WHERE copy_id = NEW.id AND status IN ('ACTIVE', 'READY_FOR_PICKUP')) INTO v_reserved;
    IF (NEW.branch_id IS DISTINCT FROM OLD.branch_id OR NEW.material_id IS DISTINCT FROM OLD.material_id)
        AND (v_loaned OR v_lost OR v_reserved) THEN
        RAISE EXCEPTION 'Cannot move a copy with active circulation operations' USING ERRCODE = '23514';
    END IF;
    IF (v_loaned AND (NEW.status <> 'LOANED' OR v_reserved OR v_lost))
        OR (v_lost AND (NEW.status <> 'LOST' OR v_reserved))
        OR (v_reserved AND NOT v_loaned AND NOT v_lost AND NEW.status <> 'RESERVED')
        OR (NOT v_loaned AND NOT v_lost AND NOT v_reserved AND NEW.status IN ('RESERVED', 'LOANED')) THEN
        RAISE EXCEPTION 'Copy status conflicts with its circulation operations' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_material_copies_guard_circulation
    BEFORE UPDATE OF status, branch_id, material_id ON material_copies
    FOR EACH ROW EXECUTE FUNCTION guard_material_copy_circulation();
