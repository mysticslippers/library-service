CREATE TYPE library_status AS ENUM (
    'ACTIVE',
    'INACTIVE',
    'SUSPENDED',
    'ARCHIVED'
    );

CREATE TYPE branch_status AS ENUM (
    'ACTIVE',
    'TEMPORARILY_UNAVAILABLE',
    'DISABLED',
    'ARCHIVED'
    );

CREATE TYPE library_rule_status AS ENUM (
    'ACTIVE',
    'INACTIVE',
    'ARCHIVED'
    );

CREATE TYPE user_status AS ENUM (
    'PENDING_ACTIVATION',
    'ACTIVE',
    'BLOCKED',
    'INACTIVE',
    'ARCHIVED'
    );

CREATE TYPE role_code AS ENUM (
    'READER',
    'LIBRARIAN',
    'ADMIN'
    );

CREATE TYPE user_block_status AS ENUM (
    'ACTIVE',
    'EXPIRED',
    'CANCELLED'
    );

CREATE TYPE user_warning_status AS ENUM (
    'ACTIVE',
    'EXPIRED',
    'CANCELLED'
    );

CREATE TYPE material_type AS ENUM (
    'BOOK',
    'MAGAZINE',
    'TEXTBOOK',
    'JOURNAL',
    'DISSERTATION',
    'OTHER'
    );

CREATE TYPE material_status AS ENUM (
    'ACTIVE',
    'HIDDEN',
    'ARCHIVED',
    'REMOVED'
    );

CREATE TYPE copy_status AS ENUM (
    'AVAILABLE',
    'RESERVED',
    'LOANED',
    'DAMAGED',
    'LOST',
    'UNDER_REPAIR',
    'REMOVED'
    );

CREATE TYPE reservation_status AS ENUM (
    'ACTIVE',
    'READY_FOR_PICKUP',
    'CANCELLED_BY_USER',
    'CANCELLED_BY_LIBRARIAN',
    'EXPIRED',
    'USED'
    );

CREATE TYPE loan_status AS ENUM (
    'ACTIVE',
    'RETURNED',
    'OVERDUE',
    'LOST',
    'CANCELLED'
    );

CREATE TYPE violation_type AS ENUM (
    'OVERDUE',
    'DAMAGE',
    'LOSS',
    'OTHER'
    );

CREATE TYPE fine_tariff_status AS ENUM (
    'ACTIVE',
    'INACTIVE',
    'ARCHIVED'
    );

CREATE TYPE fine_status AS ENUM (
    'ACTIVE',
    'PAID',
    'CANCELLED'
    );

CREATE TYPE payment_status AS ENUM (
    'CREATED',
    'PENDING',
    'SUCCESS',
    'DECLINED',
    'CANCELLED',
    'FAILED',
    'TIMEOUT'
    );

CREATE TYPE notification_type AS ENUM (
    'ACCOUNT_ACTIVATION',
    'PASSWORD_RECOVERY',
    'ACCOUNT_STATUS_CHANGED',

    'RESERVATION_CREATED',
    'RESERVATION_READY',
    'RESERVATION_CANCELLED',
    'RESERVATION_EXPIRED',

    'LOAN_CREATED',
    'LOAN_DUE_SOON',
    'LOAN_OVERDUE',
    'LOAN_RETURNED',

    'FINE_CREATED',
    'FINE_PAID',
    'FINE_CANCELLED',

    'SYSTEM_MESSAGE'
    );

CREATE TYPE notification_channel AS ENUM (
    'EMAIL',
    'SMS'
    );

CREATE TYPE notification_status AS ENUM (
    'PLANNED',
    'PENDING',
    'SENT',
    'DELIVERED',
    'FAILED',
    'CANCELLED'
    );

CREATE TYPE audit_entity_type AS ENUM (
    'USER',
    'ROLE',
    'LIBRARY',
    'BRANCH',
    'LIBRARY_RULE',
    'GENRE',
    'AUTHOR',
    'MATERIAL',
    'MATERIAL_COPY',
    'RESERVATION',
    'LOAN',
    'FINE_TARIFF',
    'FINE',
    'PAYMENT_TRANSACTION',
    'NOTIFICATION',
    'USER_BLOCK',
    'USER_WARNING'
    );

CREATE TYPE audit_action AS ENUM (
    'CREATE',
    'UPDATE',
    'DELETE',
    'ARCHIVE',
    'RESTORE',

    'LOGIN',
    'LOGOUT',
    'ROLE_CHANGED',
    'STATUS_CHANGED',

    'BLOCK',
    'UNBLOCK',
    'WARNING_CREATED',

    'RESERVATION_CREATED',
    'RESERVATION_CANCELLED',

    'LOAN_CREATED',
    'LOAN_RETURNED',

    'FINE_CREATED',
    'FINE_CANCELLED',

    'PAYMENT_STATUS_CHANGED'
    );