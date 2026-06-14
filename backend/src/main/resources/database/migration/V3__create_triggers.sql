CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_materials_set_updated_at
    BEFORE UPDATE ON materials
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trigger_material_copies_set_updated_at
    BEFORE UPDATE ON material_copies
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trigger_payment_transactions_set_updated_at
    BEFORE UPDATE ON payment_transactions
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE FUNCTION audit_changes()
    RETURNS TRIGGER AS $$
DECLARE
    v_actor_user_id BIGINT;
    v_entity_id BIGINT;
    v_action audit_action;
    v_entity_type audit_entity_type;
BEGIN
    v_actor_user_id := NULLIF(current_setting('app.current_user_id', true), '')::BIGINT;

    v_entity_type := CASE TG_TABLE_NAME
                         WHEN 'libraries' THEN 'LIBRARY'::audit_entity_type
                         WHEN 'branches' THEN 'BRANCH'::audit_entity_type
                         WHEN 'library_rules' THEN 'LIBRARY_RULE'::audit_entity_type
                         WHEN 'users' THEN 'USER'::audit_entity_type
                         WHEN 'user_blocks' THEN 'USER_BLOCK'::audit_entity_type
                         WHEN 'user_warnings' THEN 'USER_WARNING'::audit_entity_type
                         WHEN 'genres' THEN 'GENRE'::audit_entity_type
                         WHEN 'authors' THEN 'AUTHOR'::audit_entity_type
                         WHEN 'materials' THEN 'MATERIAL'::audit_entity_type
                         WHEN 'material_copies' THEN 'MATERIAL_COPY'::audit_entity_type
                         WHEN 'reservations' THEN 'RESERVATION'::audit_entity_type
                         WHEN 'loans' THEN 'LOAN'::audit_entity_type
                         WHEN 'fine_tariffs' THEN 'FINE_TARIFF'::audit_entity_type
                         WHEN 'fines' THEN 'FINE'::audit_entity_type
                         WHEN 'payment_transactions' THEN 'PAYMENT_TRANSACTION'::audit_entity_type
                         WHEN 'notifications' THEN 'NOTIFICATION'::audit_entity_type
        END;

    IF v_entity_type IS NULL THEN
        RAISE EXCEPTION 'Unsupported table for audit: %', TG_TABLE_NAME;
    END IF;

    IF TG_OP = 'INSERT' THEN
        v_action := 'CREATE';
        v_entity_id := NEW.id;

        INSERT INTO audit_logs (
            actor_user_id,
            entity_type,
            entity_id,
            action,
            details
        )
        VALUES (
                   v_actor_user_id,
                   v_entity_type,
                   v_entity_id,
                   v_action,
                   jsonb_build_object(
                           'operation', TG_OP,
                           'table', TG_TABLE_NAME,
                           'new_data', to_jsonb(NEW)
                   )
               );

        RETURN NEW;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF to_jsonb(OLD) = to_jsonb(NEW) THEN
            RETURN NEW;
        END IF;

        v_action := 'UPDATE';
        v_entity_id := NEW.id;

        INSERT INTO audit_logs (
            actor_user_id,
            entity_type,
            entity_id,
            action,
            details
        )
        VALUES (
                   v_actor_user_id,
                   v_entity_type,
                   v_entity_id,
                   v_action,
                   jsonb_build_object(
                           'operation', TG_OP,
                           'table', TG_TABLE_NAME,
                           'old_data', to_jsonb(OLD),
                           'new_data', to_jsonb(NEW)
                   )
               );

        RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE' THEN
        v_action := 'DELETE';
        v_entity_id := OLD.id;

        INSERT INTO audit_logs (
            actor_user_id,
            entity_type,
            entity_id,
            action,
            details
        )
        VALUES (
                   v_actor_user_id,
                   v_entity_type,
                   v_entity_id,
                   v_action,
                   jsonb_build_object(
                           'operation', TG_OP,
                           'table', TG_TABLE_NAME,
                           'old_data', to_jsonb(OLD)
                   )
               );

        RETURN OLD;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_libraries_audit_changes
    AFTER INSERT OR UPDATE OR DELETE ON libraries
    FOR EACH ROW
EXECUTE FUNCTION audit_changes();

CREATE TRIGGER trg_branches_audit_changes
    AFTER INSERT OR UPDATE OR DELETE ON branches
    FOR EACH ROW
EXECUTE FUNCTION audit_changes();

CREATE TRIGGER trg_library_rules_audit_changes
    AFTER INSERT OR UPDATE OR DELETE ON library_rules
    FOR EACH ROW
EXECUTE FUNCTION audit_changes();

CREATE TRIGGER trg_users_audit_changes
    AFTER INSERT OR UPDATE OR DELETE ON users
    FOR EACH ROW
EXECUTE FUNCTION audit_changes();

CREATE TRIGGER trg_user_blocks_audit_changes
    AFTER INSERT OR UPDATE OR DELETE ON user_blocks
    FOR EACH ROW
EXECUTE FUNCTION audit_changes();

CREATE TRIGGER trg_user_warnings_audit_changes
    AFTER INSERT OR UPDATE OR DELETE ON user_warnings
    FOR EACH ROW
EXECUTE FUNCTION audit_changes();

CREATE TRIGGER trg_genres_audit_changes
    AFTER INSERT OR UPDATE OR DELETE ON genres
    FOR EACH ROW
EXECUTE FUNCTION audit_changes();

CREATE TRIGGER trg_authors_audit_changes
    AFTER INSERT OR UPDATE OR DELETE ON authors
    FOR EACH ROW
EXECUTE FUNCTION audit_changes();

CREATE TRIGGER trg_materials_audit_changes
    AFTER INSERT OR UPDATE OR DELETE ON materials
    FOR EACH ROW
EXECUTE FUNCTION audit_changes();

CREATE TRIGGER trg_material_copies_audit_changes
    AFTER INSERT OR UPDATE OR DELETE ON material_copies
    FOR EACH ROW
EXECUTE FUNCTION audit_changes();

CREATE TRIGGER trg_reservations_audit_changes
    AFTER INSERT OR UPDATE OR DELETE ON reservations
    FOR EACH ROW
EXECUTE FUNCTION audit_changes();

CREATE TRIGGER trg_loans_audit_changes
    AFTER INSERT OR UPDATE OR DELETE ON loans
    FOR EACH ROW
EXECUTE FUNCTION audit_changes();

CREATE TRIGGER trg_payment_transactions_audit_changes
    AFTER INSERT OR UPDATE OR DELETE ON payment_transactions
    FOR EACH ROW
EXECUTE FUNCTION audit_changes();

CREATE TRIGGER trg_fines_audit_changes
    AFTER INSERT OR UPDATE OR DELETE ON fines
    FOR EACH ROW
EXECUTE FUNCTION audit_changes();

CREATE OR REPLACE FUNCTION reservation_created_set_copy_reserved()
    RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'ACTIVE' THEN
        UPDATE material_copies
        SET status = 'RESERVED'
        WHERE id = NEW.copy_id
          AND status = 'AVAILABLE';

        IF NOT FOUND THEN
            RAISE EXCEPTION
                'Cannot reserve copy %, because it is not AVAILABLE',
                NEW.copy_id;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_reservation_created_set_copy_reserved
    AFTER INSERT ON reservations
    FOR EACH ROW
EXECUTE FUNCTION reservation_created_set_copy_reserved();

CREATE OR REPLACE FUNCTION reservation_closed_release_copy()
    RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status <> NEW.status
        AND NEW.status IN ('CANCELLED_BY_USER', 'CANCELLED_BY_LIBRARIAN', 'EXPIRED') THEN

        IF EXISTS (
            SELECT 1
            FROM loans
            WHERE copy_id = NEW.copy_id
              AND status IN ('ACTIVE', 'OVERDUE')
        ) THEN
            RETURN NEW;
        END IF;

        UPDATE material_copies
        SET status = 'AVAILABLE'
        WHERE id = NEW.copy_id
          AND status = 'RESERVED';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_reservation_closed_release_copy
    AFTER UPDATE OF status ON reservations
    FOR EACH ROW
EXECUTE FUNCTION reservation_closed_release_copy();

CREATE OR REPLACE FUNCTION loan_created_set_copy_loaned()
    RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'ACTIVE' THEN

        UPDATE material_copies
        SET status = 'LOANED'
        WHERE id = NEW.copy_id
          AND status IN ('AVAILABLE', 'RESERVED');

        IF NOT FOUND THEN
            RAISE EXCEPTION
                'Cannot loan copy %, because it is not AVAILABLE or RESERVED',
                NEW.copy_id;
        END IF;

        IF NEW.reservation_id IS NOT NULL THEN
            UPDATE reservations
            SET status = 'USED'
            WHERE id = NEW.reservation_id
              AND copy_id = NEW.copy_id
              AND user_id = NEW.user_id
              AND status IN ('ACTIVE', 'READY_FOR_PICKUP');

            IF NOT FOUND THEN
                RAISE EXCEPTION
                    'Cannot mark reservation % as USED for copy % and user %',
                    NEW.reservation_id,
                    NEW.copy_id,
                    NEW.user_id;
            END IF;
        END IF;

    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_loan_created_set_copy_loaned
    AFTER INSERT ON loans
    FOR EACH ROW
EXECUTE FUNCTION loan_created_set_copy_loaned();

CREATE OR REPLACE FUNCTION loan_closed_update_copy_status()
    RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;

    IF NEW.status = 'RETURNED' THEN
        UPDATE material_copies
        SET status = 'AVAILABLE'
        WHERE id = NEW.copy_id
          AND status = 'LOANED';

        RETURN NEW;
    END IF;

    IF NEW.status = 'LOST' THEN
        UPDATE material_copies
        SET status = 'LOST'
        WHERE id = NEW.copy_id
          AND status = 'LOANED';

        RETURN NEW;
    END IF;

    IF NEW.status = 'CANCELLED' THEN
        UPDATE material_copies
        SET status = 'AVAILABLE'
        WHERE id = NEW.copy_id
          AND status = 'LOANED';

        RETURN NEW;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_loan_closed_update_copy_status
    AFTER UPDATE OF status ON loans
    FOR EACH ROW
EXECUTE FUNCTION loan_closed_update_copy_status();

CREATE OR REPLACE FUNCTION payment_success_mark_fine_paid()
    RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status <> NEW.status
        AND NEW.status = 'SUCCESS' THEN

        UPDATE fines
        SET status = 'PAID',
            paid_at = CURRENT_TIMESTAMP
        WHERE id = NEW.fine_id
          AND status = 'ACTIVE';

        IF NOT FOUND THEN
            RAISE EXCEPTION
                'Cannot mark fine % as PAID: fine is not ACTIVE or does not exist',
                NEW.fine_id;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payment_success_mark_fine_paid
    AFTER UPDATE OF status ON payment_transactions
    FOR EACH ROW
EXECUTE FUNCTION payment_success_mark_fine_paid();