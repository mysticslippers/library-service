CREATE FUNCTION guard_loan_transition() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id, NEW.user_id, NEW.copy_id, NEW.reservation_id, NEW.branch_id,
           NEW.issued_by_user_id, NEW.loaned_at)
        IS DISTINCT FROM ROW(OLD.id, OLD.user_id, OLD.copy_id, OLD.reservation_id, OLD.branch_id,
                             OLD.issued_by_user_id, OLD.loaned_at) THEN
        RAISE EXCEPTION 'Loan identity and origin cannot be changed' USING ERRCODE = '23514';
    END IF;

    IF OLD.status IN ('RETURNED', 'CANCELLED') AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'Closed loan cannot be changed' USING ERRCODE = '23514';
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
        (OLD.status = 'ACTIVE' AND NEW.status IN ('OVERDUE', 'LOST', 'RETURNED', 'CANCELLED'))
        OR (OLD.status = 'OVERDUE' AND NEW.status IN ('LOST', 'RETURNED', 'CANCELLED'))
        OR (OLD.status = 'LOST' AND NEW.status = 'RETURNED')
    ) THEN
        RAISE EXCEPTION 'Invalid loan status transition: % -> %', OLD.status, NEW.status USING ERRCODE = '23514';
    END IF;

    IF NEW.due_at IS DISTINCT FROM OLD.due_at OR NEW.renewal_count IS DISTINCT FROM OLD.renewal_count THEN
        IF OLD.status <> 'ACTIVE' OR NEW.status <> 'ACTIVE'
            OR NEW.due_at <= OLD.due_at OR NEW.renewal_count <> OLD.renewal_count + 1 THEN
            RAISE EXCEPTION 'Renewal must extend an active loan and increment its counter once' USING ERRCODE = '23514';
        END IF;
    END IF;

    IF NEW.returned_at IS DISTINCT FROM OLD.returned_at AND NEW.status <> 'RETURNED' THEN
        RAISE EXCEPTION 'Return timestamp requires a returned loan' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_loans_guard_transition
    BEFORE UPDATE ON loans FOR EACH ROW EXECUTE FUNCTION guard_loan_transition();

CREATE FUNCTION guard_reservation_transition() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id, NEW.user_id, NEW.material_id, NEW.copy_id, NEW.branch_id)
        IS DISTINCT FROM ROW(OLD.id, OLD.user_id, OLD.material_id, OLD.copy_id, OLD.branch_id) THEN
        RAISE EXCEPTION 'Reservation identity cannot be changed' USING ERRCODE = '23514';
    END IF;

    IF OLD.status IN ('USED', 'EXPIRED', 'CANCELLED_BY_USER', 'CANCELLED_BY_LIBRARIAN')
        AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'Closed reservation cannot be changed' USING ERRCODE = '23514';
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status THEN
        IF NOT (
            (OLD.status = 'ACTIVE' AND NEW.status IN ('READY_FOR_PICKUP', 'EXPIRED', 'CANCELLED_BY_USER', 'CANCELLED_BY_LIBRARIAN'))
            OR (OLD.status = 'READY_FOR_PICKUP' AND NEW.status IN ('USED', 'EXPIRED', 'CANCELLED_BY_USER', 'CANCELLED_BY_LIBRARIAN'))
        ) THEN
            RAISE EXCEPTION 'Invalid reservation status transition: % -> %', OLD.status, NEW.status USING ERRCODE = '23514';
        END IF;

        IF NEW.status = 'READY_FOR_PICKUP' AND NEW.ready_at IS NULL THEN
            RAISE EXCEPTION 'Ready reservation requires its readiness timestamp' USING ERRCODE = '23514';
        END IF;

        IF NEW.status IN ('CANCELLED_BY_USER', 'CANCELLED_BY_LIBRARIAN') AND NEW.cancelled_at IS NULL THEN
            RAISE EXCEPTION 'Cancelled reservation requires its cancellation timestamp' USING ERRCODE = '23514';
        END IF;

        IF NEW.status = 'USED' AND NOT EXISTS (
            SELECT 1 FROM loans l
            WHERE l.reservation_id = NEW.id AND l.user_id = NEW.user_id
                AND l.copy_id = NEW.copy_id AND l.branch_id = NEW.branch_id
                AND l.status IN ('ACTIVE', 'OVERDUE', 'LOST')
        ) THEN
            RAISE EXCEPTION 'Reservation can be used only with its matching loan' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_reservations_guard_transition
    BEFORE UPDATE ON reservations FOR EACH ROW EXECUTE FUNCTION guard_reservation_transition();
