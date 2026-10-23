CREATE FUNCTION audit_entity_table(p_type text) RETURNS text
    LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE p_type
        WHEN 'USER' THEN 'users'
        WHEN 'ROLE' THEN 'roles'
        WHEN 'LIBRARY' THEN 'libraries'
        WHEN 'BRANCH' THEN 'branches'
        WHEN 'LIBRARY_RULE' THEN 'library_rules'
        WHEN 'GENRE' THEN 'genres'
        WHEN 'AUTHOR' THEN 'authors'
        WHEN 'MATERIAL' THEN 'materials'
        WHEN 'MATERIAL_COPY' THEN 'material_copies'
        WHEN 'RESERVATION' THEN 'reservations'
        WHEN 'LOAN' THEN 'loans'
        WHEN 'FINE_TARIFF' THEN 'fine_tariffs'
        WHEN 'FINE' THEN 'fines'
        WHEN 'PAYMENT_TRANSACTION' THEN 'payment_transactions'
        WHEN 'NOTIFICATION' THEN 'notifications'
        WHEN 'NOTIFICATION_TEMPLATE' THEN 'notification_templates'
        WHEN 'NOTIFICATION_PREFERENCE' THEN 'notification_preferences'
        WHEN 'USER_BLOCK' THEN 'user_blocks'
        WHEN 'USER_WARNING' THEN 'user_warnings'
    END;
$$;

CREATE FUNCTION audit_snapshot_fields(p_type text) RETURNS text[]
    LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE p_type
        WHEN 'USER' THEN ARRAY['id', 'status', 'registered_at', 'activated_at', 'last_login_at', 'failed_login_attempts', 'locked_until', 'home_branch_id', 'token_version']::text[]
        WHEN 'ROLE' THEN ARRAY['id', 'code', 'name', 'description']::text[]
        WHEN 'LIBRARY' THEN ARRAY['id', 'code', 'name', 'status', 'created_at']::text[]
        WHEN 'BRANCH' THEN ARRAY['id', 'library_id', 'name', 'status', 'created_at']::text[]
        WHEN 'LIBRARY_RULE' THEN ARRAY['id', 'branch_id', 'max_active_reservations', 'max_active_loans', 'reservation_ttl_days', 'default_loan_days', 'renewal_allowed', 'max_renewal_count', 'renewal_period_days', 'reservation_allowed', 'status', 'valid_from', 'valid_to']::text[]
        WHEN 'GENRE' THEN ARRAY['id', 'code', 'name', 'status', 'created_at']::text[]
        WHEN 'AUTHOR' THEN ARRAY['id', 'first_name', 'last_name', 'middle_name', 'status', 'created_at']::text[]
        WHEN 'MATERIAL' THEN ARRAY['id', 'material_type', 'title', 'description', 'isbn', 'publication_year', 'publisher', 'language', 'status', 'created_at', 'updated_at']::text[]
        WHEN 'MATERIAL_COPY' THEN ARRAY['id', 'material_id', 'branch_id', 'inventory_number', 'status', 'shelf_location', 'created_at', 'updated_at']::text[]
        WHEN 'RESERVATION' THEN ARRAY['id', 'user_id', 'material_id', 'copy_id', 'branch_id', 'status', 'created_at', 'expires_at', 'ready_at', 'cancelled_at', 'cancellation_reason']::text[]
        WHEN 'LOAN' THEN ARRAY['id', 'user_id', 'copy_id', 'reservation_id', 'branch_id', 'issued_by_user_id', 'loaned_at', 'due_at', 'returned_at', 'renewal_count', 'status']::text[]
        WHEN 'FINE_TARIFF' THEN ARRAY['id', 'violation_type', 'amount_per_day', 'fixed_amount', 'max_amount', 'status', 'valid_from', 'valid_to']::text[]
        WHEN 'FINE' THEN ARRAY['id', 'user_id', 'loan_id', 'copy_id', 'tariff_id', 'reason', 'amount', 'status', 'created_at', 'paid_at', 'cancelled_at', 'cancellation_reason']::text[]
        WHEN 'PAYMENT_TRANSACTION' THEN ARRAY['id', 'fine_id', 'amount', 'status', 'created_at', 'updated_at']::text[]
        WHEN 'NOTIFICATION' THEN ARRAY['id', 'user_id', 'reservation_id', 'loan_id', 'fine_id', 'type', 'channel', 'status', 'created_at', 'sent_at', 'read_at', 'attempt_count', 'sending_started_at', 'version']::text[]
        WHEN 'NOTIFICATION_TEMPLATE' THEN ARRAY['id', 'type', 'channel', 'status', 'created_at', 'updated_at']::text[]
        WHEN 'NOTIFICATION_PREFERENCE' THEN ARRAY['id', 'user_id', 'type', 'channel', 'enabled', 'preferred', 'created_at', 'updated_at']::text[]
        WHEN 'USER_BLOCK' THEN ARRAY['id', 'user_id', 'created_by_user_id', 'reason', 'blocked_at', 'expires_at', 'unblocked_by_user_id', 'unblock_reason', 'unblocked_at', 'status']::text[]
        WHEN 'USER_WARNING' THEN ARRAY['id', 'user_id', 'created_by_user_id', 'reason', 'comment', 'created_at', 'expires_at', 'status']::text[]
        ELSE ARRAY[]::text[]
    END;
$$;

CREATE FUNCTION audit_manual_fields(p_type text) RETURNS text[]
    LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE p_type
        WHEN 'USER' THEN ARRAY['status', 'reason', 'updated', 'assignedRole', 'revokedRole']::text[]
        WHEN 'USER_BLOCK' THEN ARRAY['userId', 'reason']::text[]
        WHEN 'USER_WARNING' THEN ARRAY['userId', 'reason']::text[]
        WHEN 'NOTIFICATION' THEN ARRAY['action', 'processed', 'createdAt']::text[]
        WHEN 'NOTIFICATION_TEMPLATE' THEN ARRAY['type', 'channel']::text[]
        WHEN 'NOTIFICATION_PREFERENCE' THEN ARRAY['type', 'channel', 'enabled', 'preferred']::text[]
        ELSE ARRAY[]::text[]
    END;
$$;

CREATE FUNCTION audit_pick_scalars(p_data jsonb, p_fields text[]) RETURNS jsonb
    LANGUAGE sql IMMUTABLE AS $$
    SELECT COALESCE(jsonb_object_agg(key, value), '{}'::jsonb)
    FROM jsonb_each(CASE WHEN jsonb_typeof(p_data) = 'object' THEN p_data ELSE '{}'::jsonb END)
    WHERE key = ANY(p_fields) AND jsonb_typeof(value) IN ('string', 'number', 'boolean', 'null');
$$;

CREATE FUNCTION audit_safe_snapshot(p_type text, p_data jsonb) RETURNS jsonb
    LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    v_safe jsonb := audit_pick_scalars(p_data, audit_snapshot_fields(p_type));
BEGIN
    IF p_type = 'BRANCH' AND jsonb_typeof(p_data -> 'address') = 'object' THEN
        v_safe := v_safe || jsonb_build_object('address',
            audit_pick_scalars(p_data -> 'address', ARRAY['city', 'street', 'building']));
    END IF;
    RETURN v_safe;
END;
$$;

CREATE FUNCTION audit_sanitize_details(p_type text, p_details jsonb) RETURNS jsonb
    LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    v_safe jsonb;
    v_key text;
    v_roles jsonb;
BEGIN
    IF audit_entity_table(p_type) IS NULL OR jsonb_typeof(p_details) IS DISTINCT FROM 'object' THEN
        RETURN '{}'::jsonb;
    END IF;
    v_safe := audit_pick_scalars(p_details, audit_manual_fields(p_type));
    IF p_details ->> 'operation' IN ('INSERT', 'UPDATE', 'DELETE') THEN
        v_safe := v_safe || jsonb_build_object('operation', p_details -> 'operation');
    END IF;
    IF p_details ->> 'table' = audit_entity_table(p_type) THEN
        v_safe := v_safe || jsonb_build_object('table', audit_entity_table(p_type));
    END IF;
    FOREACH v_key IN ARRAY ARRAY['old_data', 'new_data'] LOOP
        IF jsonb_typeof(p_details -> v_key) = 'object' THEN
            v_safe := v_safe || jsonb_build_object(v_key, audit_safe_snapshot(p_type, p_details -> v_key));
        END IF;
    END LOOP;
    IF p_type = 'USER' THEN
        IF jsonb_typeof(p_details -> 'password_changed') = 'boolean' THEN
            v_safe := v_safe || jsonb_build_object('password_changed', p_details -> 'password_changed');
        END IF;
        IF jsonb_typeof(p_details -> 'roles') = 'array' THEN
            SELECT COALESCE(jsonb_agg(value ORDER BY ordinal), '[]'::jsonb) INTO v_roles
            FROM jsonb_array_elements(p_details -> 'roles') WITH ORDINALITY AS items(value, ordinal)
            WHERE value IN ('"READER"'::jsonb, '"LIBRARIAN"'::jsonb, '"ADMIN"'::jsonb);
            v_safe := v_safe || jsonb_build_object('roles', v_roles);
        END IF;
    END IF;
    RETURN v_safe;
END;
$$;

CREATE OR REPLACE FUNCTION audit_changes() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
DECLARE
    v_actor_user_id BIGINT := NULLIF(current_setting('app.current_user_id', true), '')::BIGINT;
    v_entity_type audit_entity_type;
    v_entity_id BIGINT;
    v_action audit_action;
    v_details jsonb;
BEGIN
    v_entity_type := CASE TG_TABLE_NAME
        WHEN 'users' THEN 'USER'::audit_entity_type
        WHEN 'roles' THEN 'ROLE'::audit_entity_type
        WHEN 'libraries' THEN 'LIBRARY'::audit_entity_type
        WHEN 'branches' THEN 'BRANCH'::audit_entity_type
        WHEN 'library_rules' THEN 'LIBRARY_RULE'::audit_entity_type
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
        WHEN 'notification_templates' THEN 'NOTIFICATION_TEMPLATE'::audit_entity_type
        WHEN 'notification_preferences' THEN 'NOTIFICATION_PREFERENCE'::audit_entity_type
        WHEN 'user_blocks' THEN 'USER_BLOCK'::audit_entity_type
        WHEN 'user_warnings' THEN 'USER_WARNING'::audit_entity_type
    END;
    IF v_entity_type IS NULL THEN
        RAISE EXCEPTION 'Unsupported table for audit: %', TG_TABLE_NAME;
    END IF;
    IF TG_OP = 'UPDATE' AND to_jsonb(OLD) = to_jsonb(NEW) THEN
        RETURN NEW;
    END IF;

    v_details := jsonb_build_object('operation', TG_OP, 'table', TG_TABLE_NAME);
    IF TG_OP <> 'INSERT' THEN
        v_details := v_details || jsonb_build_object('old_data',
            audit_safe_snapshot(v_entity_type::text, to_jsonb(OLD)));
    END IF;
    IF TG_OP <> 'DELETE' THEN
        v_details := v_details || jsonb_build_object('new_data',
            audit_safe_snapshot(v_entity_type::text, to_jsonb(NEW)));
    END IF;
    IF TG_TABLE_NAME = 'users' AND TG_OP = 'UPDATE' THEN
        IF OLD.password_hash IS DISTINCT FROM NEW.password_hash THEN
            v_details := v_details || jsonb_build_object('password_changed', true);
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN
        v_entity_id := OLD.id;
        v_action := 'DELETE';
    ELSIF TG_OP = 'INSERT' THEN
        v_entity_id := NEW.id;
        v_action := 'CREATE';
    ELSE
        v_entity_id := NEW.id;
        v_action := 'UPDATE';
    END IF;
    INSERT INTO audit_logs(actor_user_id, entity_type, entity_id, action, details)
    VALUES (v_actor_user_id, v_entity_type, v_entity_id, v_action,
            audit_sanitize_details(v_entity_type::text, v_details));
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION sanitize_audit_log_details() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    NEW.details := audit_sanitize_details(NEW.entity_type::text, NEW.details);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_audit_logs_sanitize_details
    BEFORE INSERT OR UPDATE OF details, entity_type ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION sanitize_audit_log_details();

UPDATE audit_logs
SET details = audit_sanitize_details(entity_type::text, details)
WHERE details IS DISTINCT FROM audit_sanitize_details(entity_type::text, details);
