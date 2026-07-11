CREATE TABLE libraries (
                           id          BIGSERIAL PRIMARY KEY,
                           code        VARCHAR(50) NOT NULL UNIQUE,
                           name        VARCHAR(255) NOT NULL,
                           status      library_status NOT NULL DEFAULT 'ACTIVE',
                           created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE branches (
                          id          BIGSERIAL PRIMARY KEY,
                          library_id  BIGINT NOT NULL,
                          name        VARCHAR(255) NOT NULL,
                          address     JSONB NOT NULL,
                          status      branch_status NOT NULL DEFAULT 'ACTIVE',
                          created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT fk_branches_library
                              FOREIGN KEY (library_id)
                                  REFERENCES libraries (id)
                                  ON DELETE RESTRICT,

                          CONSTRAINT uniqe_branches_library_name
                              UNIQUE (library_id, name),

                          CONSTRAINT chk_branches_address_is_object
                              CHECK (jsonb_typeof(address) = 'object'),

                          CONSTRAINT chk_branches_address_required_fields
                              CHECK (
                                  address ? 'city'
                                      AND address ? 'street'
                                      AND address ? 'building'
                                  )
);

CREATE INDEX idx_branches_address_city
    ON branches USING btree ((address ->> 'city'));

CREATE INDEX idx_branches_address_street
    ON branches USING btree ((address ->> 'street'));

CREATE INDEX idx_branches_address_building
    ON branches USING btree ((address ->> 'building'));

CREATE TABLE library_rules (
                               id                          BIGSERIAL PRIMARY KEY,
                               branch_id                   BIGINT NOT NULL,
                               max_active_reservations     INT NOT NULL DEFAULT 5,
                               max_active_loans            INT NOT NULL DEFAULT 10,
                               reservation_ttl_days        INT NOT NULL DEFAULT 3,
                               default_loan_days           INT NOT NULL DEFAULT 14,
                               renewal_allowed             BOOLEAN NOT NULL DEFAULT TRUE,
                               max_renewal_count           INT NOT NULL DEFAULT 2,
                               renewal_period_days         INT NOT NULL DEFAULT 7,
                               reservation_allowed         BOOLEAN NOT NULL DEFAULT TRUE,
                               status                      library_rule_status NOT NULL DEFAULT 'ACTIVE',
                               valid_from                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               valid_to                    TIMESTAMP,

                               CONSTRAINT fk_library_rules_branch
                                   FOREIGN KEY (branch_id)
                                       REFERENCES branches (id)
                                       ON DELETE RESTRICT,

                               CONSTRAINT chk_library_rules_max_active_reservations
                                   CHECK (max_active_reservations >= 0),

                               CONSTRAINT chk_library_rules_max_active_loans
                                   CHECK (max_active_loans >= 0),

                               CONSTRAINT chk_library_rules_reservation_ttl_days
                                   CHECK (reservation_ttl_days > 0),

                               CONSTRAINT chk_library_rules_default_loan_days
                                   CHECK (default_loan_days > 0),

                               CONSTRAINT chk_library_rules_max_renewal_count
                                   CHECK (max_renewal_count >= 0),

                               CONSTRAINT chk_library_rules_renewal_period_days
                                   CHECK (renewal_period_days > 0),

                               CONSTRAINT chk_library_rules_valid_period
                                   CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE UNIQUE INDEX uq_library_rules_one_active_per_branch
    ON library_rules (branch_id)
    WHERE status = 'ACTIVE';

CREATE TABLE users (
                       id                    BIGSERIAL PRIMARY KEY,
                       email                 VARCHAR(255) NOT NULL UNIQUE,
                       phone                 VARCHAR(12) NOT NULL UNIQUE,
                       password_hash         VARCHAR(255) NOT NULL,
                       first_name            VARCHAR(100) NOT NULL,
                       last_name             VARCHAR(100) NOT NULL,
                       middle_name           VARCHAR(100),
                       status                user_status NOT NULL DEFAULT 'PENDING_ACTIVATION',
                       registered_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       activated_at          TIMESTAMP,
                       last_login_at         TIMESTAMP,
                       failed_login_attempts SMALLINT NOT NULL DEFAULT 0,
                       locked_until          TIMESTAMP,
                       home_branch_id        BIGINT,

                       CONSTRAINT fk_users_home_branch
                           FOREIGN KEY (home_branch_id)
                               REFERENCES branches (id)
                               ON DELETE SET NULL,

                       CONSTRAINT chk_users_failed_login_attempts
                           CHECK (failed_login_attempts >= 0),

                       CONSTRAINT chk_users_email_format
                           CHECK (email ~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$'),

                       CONSTRAINT chk_users_phone_ru_format
                           CHECK (phone IS NULL OR phone ~ '^(\+7|8)[0-9]{10}$')
);

CREATE INDEX idx_users_status
    ON users USING btree (status);

CREATE INDEX idx_users_home_branch
    ON users USING btree (home_branch_id);

CREATE INDEX idx_users_registered_at
    ON users USING btree (registered_at);

CREATE TABLE auth_tokens (
                             id         BIGSERIAL PRIMARY KEY,
                             user_id    BIGINT NOT NULL,
                             token      VARCHAR(512) NOT NULL UNIQUE,
                             type       auth_token_type NOT NULL,
                             expires_at TIMESTAMP NOT NULL,
                             used_at    TIMESTAMP,
                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT fk_auth_tokens_user
                                 FOREIGN KEY (user_id)
                                     REFERENCES users (id)
                                     ON DELETE CASCADE,

                             CONSTRAINT chk_auth_tokens_expiration
                                 CHECK (expires_at > created_at)
);

CREATE INDEX idx_auth_tokens_user_type_used
    ON auth_tokens USING btree (user_id, type, used_at);

CREATE INDEX idx_auth_tokens_expires_at
    ON auth_tokens USING btree (expires_at);

CREATE TABLE roles (
                        id          BIGSERIAL PRIMARY KEY,
                        code        role_code NOT NULL UNIQUE,
                        name        VARCHAR(100) NOT NULL,
                       description TEXT
);

INSERT INTO roles (code, name, description) VALUES
                                                (
                                                    'READER',
                                                    'Читатель',
                                                    'Пользователь библиотеки, который ищет материалы, бронирует их, получает материалы во временное пользование, возвращает их, продлевает срок выдачи и оплачивает штрафы.'
                                                ),
                                                (
                                                    'LIBRARIAN',
                                                    'Библиотекарь',
                                                    'Сотрудник библиотеки, который работает с каталогом, оформляет выдачу и возврат материалов, обрабатывает бронирования и следит за состоянием экземпляров.'
                                                ),
                                                (
                                                    'ADMIN',
                                                    'Администратор',
                                                    'Пользователь, который управляет пользователями, ролями, параметрами библиотек, филиалами, правилами обслуживания и отчётностью.'
                                                );

CREATE TABLE user_roles (
                            user_id BIGINT NOT NULL,
                            role_id BIGINT NOT NULL,
                            assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (user_id, role_id),

                            CONSTRAINT fk_user_roles_user
                                FOREIGN KEY (user_id)
                                    REFERENCES users (id)
                                    ON DELETE CASCADE,

                            CONSTRAINT fk_user_roles_role
                                FOREIGN KEY (role_id)
                                    REFERENCES roles (id)
                                    ON DELETE RESTRICT
);

CREATE TABLE user_blocks (
                             id                  BIGSERIAL PRIMARY KEY,
                             user_id             BIGINT NOT NULL,
                             created_by_user_id  BIGINT NOT NULL,
                              reason              TEXT NOT NULL,
                              blocked_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              expires_at          TIMESTAMP,
                              unblocked_by_user_id BIGINT,
                              unblock_reason      TEXT,
                              unblocked_at        TIMESTAMP,
                              status              user_block_status NOT NULL DEFAULT 'ACTIVE',

                             CONSTRAINT fk_user_blocks_user
                                 FOREIGN KEY (user_id)
                                     REFERENCES users (id)
                                     ON DELETE CASCADE,

                              CONSTRAINT fk_user_blocks_created_by
                                  FOREIGN KEY (created_by_user_id)
                                      REFERENCES users (id)
                                      ON DELETE RESTRICT,

                              CONSTRAINT fk_user_blocks_unblocked_by
                                  FOREIGN KEY (unblocked_by_user_id)
                                      REFERENCES users (id)
                                      ON DELETE SET NULL,

                              CONSTRAINT chk_user_blocks_dates
                                  CHECK (expires_at IS NULL OR expires_at > blocked_at),

                             CONSTRAINT chk_user_blocks_unblocked_at
                                 CHECK (unblocked_at IS NULL OR unblocked_at >= blocked_at)
);

CREATE UNIQUE INDEX uq_user_blocks_one_active_per_user
    ON user_blocks (user_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_user_blocks_user
    ON user_blocks USING btree (user_id);

CREATE INDEX idx_user_blocks_created_by
    ON user_blocks USING btree (created_by_user_id);

CREATE INDEX idx_user_blocks_status
    ON user_blocks USING btree (status);

CREATE TABLE user_warnings (
                               id                  BIGSERIAL PRIMARY KEY,
                               user_id             BIGINT NOT NULL,
                               created_by_user_id  BIGINT NOT NULL,
                               reason              TEXT NOT NULL,
                               comment             TEXT,
                               created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               expires_at          TIMESTAMP,
                               status              user_warning_status NOT NULL DEFAULT 'ACTIVE',

                               CONSTRAINT fk_user_warnings_user
                                   FOREIGN KEY (user_id)
                                       REFERENCES users (id)
                                       ON DELETE CASCADE,

                               CONSTRAINT fk_user_warnings_created_by
                                   FOREIGN KEY (created_by_user_id)
                                       REFERENCES users (id)
                                       ON DELETE RESTRICT,

                               CONSTRAINT chk_user_warnings_dates
                                   CHECK (expires_at IS NULL OR expires_at > created_at)
);

CREATE INDEX idx_user_warnings_user
    ON user_warnings USING btree (user_id);

CREATE INDEX idx_user_warnings_created_by
    ON user_warnings USING btree (created_by_user_id);

CREATE INDEX idx_user_warnings_status
    ON user_warnings USING btree (status);

CREATE INDEX idx_user_warnings_expires_at
    ON user_warnings USING btree (expires_at);

CREATE TABLE genres (
                        id          BIGSERIAL PRIMARY KEY,
                        code        VARCHAR(50) NOT NULL UNIQUE,
                        name        VARCHAR(100) NOT NULL UNIQUE,
                        status      genre_status NOT NULL DEFAULT 'ACTIVE',
                        created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_genres_status
    ON genres USING btree (status);

CREATE TABLE authors (
                         id          BIGSERIAL PRIMARY KEY,
                         first_name  VARCHAR(100) NOT NULL,
                         last_name   VARCHAR(100) NOT NULL,
                         middle_name VARCHAR(100),
                         status      author_status NOT NULL DEFAULT 'ACTIVE',
                         created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_authors_full_name
    ON authors USING btree (last_name, first_name, middle_name);

CREATE INDEX idx_authors_status
    ON authors USING btree (status);

CREATE TABLE materials (
                           id                  BIGSERIAL PRIMARY KEY,
                           isbn                VARCHAR(13) UNIQUE,
                           title               VARCHAR(255) NOT NULL,
                           description         TEXT,
                           publisher           VARCHAR(255),
                           publication_year    INT,
                           material_type       material_type NOT NULL DEFAULT 'BOOK',
                           language            VARCHAR(50) NOT NULL DEFAULT 'ru',
                           status              material_status NOT NULL DEFAULT 'ACTIVE',
                           created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at          TIMESTAMP,

                           CONSTRAINT chk_materials_publication_year
                               CHECK (publication_year IS NULL OR publication_year BETWEEN 1000 AND EXTRACT(YEAR FROM CURRENT_DATE)::INT + 1),

                           CONSTRAINT chk_materials_isbn_format
                               CHECK (isbn IS NULL OR isbn ~ '^[0-9]{10}([0-9]{3})?$')
);

CREATE INDEX idx_materials_title
    ON materials USING btree (title);

CREATE INDEX idx_materials_publisher
    ON materials USING btree (publisher);

CREATE INDEX idx_materials_publication_year
    ON materials USING btree (publication_year);

CREATE INDEX idx_materials_type_status
    ON materials USING btree (material_type, status);

CREATE INDEX idx_materials_search_vector
    ON materials
        USING gin (to_tsvector('russian', coalesce(title, '') || ' ' || coalesce(description, '')));

CREATE TABLE material_authors (
                                  material_id  BIGINT NOT NULL,
                                  author_id    BIGINT NOT NULL,
                                  author_order INT NOT NULL DEFAULT 1,
                                  PRIMARY KEY (material_id, author_id),

                                  CONSTRAINT fk_material_authors_material
                                      FOREIGN KEY (material_id)
                                          REFERENCES materials (id)
                                          ON DELETE CASCADE,

                                  CONSTRAINT fk_material_authors_author
                                      FOREIGN KEY (author_id)
                                          REFERENCES authors (id)
                                          ON DELETE RESTRICT,

                                  CONSTRAINT chk_material_authors_order
                                      CHECK (author_order > 0)
);

CREATE INDEX idx_material_authors_author
    ON material_authors USING btree (author_id);

CREATE UNIQUE INDEX uq_material_authors_material_order
    ON material_authors (material_id, author_order);

CREATE TABLE material_genres (
                                 material_id BIGINT NOT NULL,
                                 genre_id    BIGINT NOT NULL,
                                 PRIMARY KEY (material_id, genre_id),

                                 CONSTRAINT fk_material_genres_material
                                     FOREIGN KEY (material_id)
                                         REFERENCES materials (id)
                                         ON DELETE CASCADE,

                                 CONSTRAINT fk_material_genres_genre
                                     FOREIGN KEY (genre_id)
                                         REFERENCES genres (id)
                                         ON DELETE RESTRICT
);

CREATE INDEX idx_material_genres_genre
    ON material_genres USING btree (genre_id);

CREATE TABLE material_copies (
                                 id               BIGSERIAL PRIMARY KEY,
                                 material_id      BIGINT NOT NULL,
                                 branch_id        BIGINT NOT NULL,
                                 inventory_number VARCHAR(100) NOT NULL UNIQUE,
                                 status           copy_status NOT NULL DEFAULT 'AVAILABLE',
                                 shelf_location   VARCHAR(100),
                                 created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at       TIMESTAMP,

                                 CONSTRAINT fk_material_copies_material
                                     FOREIGN KEY (material_id)
                                         REFERENCES materials (id)
                                         ON DELETE RESTRICT,

                                 CONSTRAINT fk_material_copies_branch
                                     FOREIGN KEY (branch_id)
                                         REFERENCES branches (id)
                                         ON DELETE RESTRICT
);

CREATE INDEX idx_material_copies_material
    ON material_copies USING btree (material_id);

CREATE INDEX idx_material_copies_branch
    ON material_copies USING btree (branch_id);

CREATE INDEX idx_material_copies_status
    ON material_copies USING btree (status);

CREATE INDEX idx_material_copies_material_branch_status
    ON material_copies USING btree (material_id, branch_id, status);

CREATE TABLE reservations (
                              id                   BIGSERIAL PRIMARY KEY,
                              user_id              BIGINT NOT NULL,
                              material_id          BIGINT NOT NULL,
                              copy_id              BIGINT NOT NULL,
                              branch_id            BIGINT NOT NULL,
                              status               reservation_status NOT NULL DEFAULT 'ACTIVE',
                              created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              expires_at           TIMESTAMP NOT NULL,
                              ready_at             TIMESTAMP,
                              cancelled_at         TIMESTAMP,
                              cancellation_reason  TEXT,

                              CONSTRAINT fk_reservations_user
                                  FOREIGN KEY (user_id)
                                      REFERENCES users (id)
                                      ON DELETE RESTRICT,

                              CONSTRAINT fk_reservations_material
                                  FOREIGN KEY (material_id)
                                      REFERENCES materials (id)
                                      ON DELETE RESTRICT,

                              CONSTRAINT fk_reservations_copy
                                  FOREIGN KEY (copy_id)
                                      REFERENCES material_copies (id)
                                      ON DELETE RESTRICT,

                              CONSTRAINT fk_reservations_branch
                                  FOREIGN KEY (branch_id)
                                      REFERENCES branches (id)
                                      ON DELETE RESTRICT,

                              CONSTRAINT chk_reservations_expires_at
                                  CHECK (expires_at > created_at),

                              CONSTRAINT chk_reservations_ready_at
                                  CHECK (ready_at IS NULL OR ready_at >= created_at),

                              CONSTRAINT chk_reservations_cancelled_at
                                  CHECK (cancelled_at IS NULL OR cancelled_at >= created_at),

                              CONSTRAINT chk_reservations_cancel_reason
                                  CHECK ((status NOT IN ('CANCELLED_BY_USER', 'CANCELLED_BY_LIBRARIAN') AND cancellation_reason IS NULL)
                                      OR
                                         (status IN ('CANCELLED_BY_USER', 'CANCELLED_BY_LIBRARIAN') AND cancellation_reason IS NOT NULL)
                                      )
);

CREATE INDEX idx_reservations_user_status
    ON reservations USING btree (user_id, status);

CREATE INDEX idx_reservations_branch_status
    ON reservations USING btree (branch_id, status);

CREATE INDEX idx_reservations_material_status
    ON reservations USING btree (material_id, status);

CREATE INDEX idx_reservations_expires_at
    ON reservations USING btree (expires_at);

CREATE TABLE loans (
                       id                  BIGSERIAL PRIMARY KEY,
                       user_id             BIGINT NOT NULL,
                       copy_id             BIGINT NOT NULL,
                       reservation_id      BIGINT,
                       branch_id           BIGINT NOT NULL,
                       issued_by_user_id   BIGINT NOT NULL,
                       loaned_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       due_at              TIMESTAMP NOT NULL,
                       returned_at         TIMESTAMP,
                       renewal_count       INT NOT NULL DEFAULT 0,
                       status              loan_status NOT NULL DEFAULT 'ACTIVE',

                       CONSTRAINT fk_loans_user
                           FOREIGN KEY (user_id)
                               REFERENCES users (id)
                               ON DELETE RESTRICT,

                       CONSTRAINT fk_loans_copy
                           FOREIGN KEY (copy_id)
                               REFERENCES material_copies (id)
                               ON DELETE RESTRICT,

                       CONSTRAINT fk_loans_reservation
                           FOREIGN KEY (reservation_id)
                               REFERENCES reservations (id)
                               ON DELETE SET NULL,

                       CONSTRAINT fk_loans_branch
                           FOREIGN KEY (branch_id)
                               REFERENCES branches (id)
                               ON DELETE RESTRICT,

                       CONSTRAINT fk_loans_issued_by
                           FOREIGN KEY (issued_by_user_id)
                               REFERENCES users (id)
                               ON DELETE RESTRICT,

                       CONSTRAINT chk_loans_due_at
                           CHECK (due_at > loaned_at),

                       CONSTRAINT chk_loans_returned_at
                           CHECK (returned_at IS NULL OR returned_at >= loaned_at),

                       CONSTRAINT chk_loans_renewal_count
                           CHECK (renewal_count >= 0),

                       CONSTRAINT chk_loans_returned_status
                           CHECK ((status = 'RETURNED' AND returned_at IS NOT NULL)
                               OR (status <> 'RETURNED')
                               )
);

CREATE UNIQUE INDEX uq_loans_one_active_per_copy
    ON loans (copy_id)
    WHERE status IN ('ACTIVE', 'OVERDUE', 'LOST');

CREATE INDEX idx_loans_user_status
    ON loans USING btree (user_id, status);

CREATE INDEX idx_loans_copy_status
    ON loans USING btree (copy_id, status);

CREATE INDEX idx_loans_branch_status
    ON loans USING btree (branch_id, status);

CREATE INDEX idx_loans_due_at
    ON loans USING btree (due_at);

CREATE INDEX idx_loans_issued_by
    ON loans USING btree (issued_by_user_id);

CREATE TABLE fine_tariffs (
                              id              BIGSERIAL PRIMARY KEY,
                              violation_type  violation_type NOT NULL,
                              amount_per_day  NUMERIC(10, 2),
                              fixed_amount    NUMERIC(10, 2),
                              max_amount      NUMERIC(10, 2),
                              status          fine_tariff_status NOT NULL DEFAULT 'ACTIVE',
                              valid_from      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              valid_to        TIMESTAMP,

                              CONSTRAINT chk_fine_tariffs_amount_per_day
                                  CHECK (amount_per_day IS NULL OR amount_per_day > 0),

                              CONSTRAINT chk_fine_tariffs_fixed_amount
                                  CHECK (fixed_amount IS NULL OR fixed_amount > 0),

                              CONSTRAINT chk_fine_tariffs_max_amount
                                  CHECK (max_amount IS NULL OR max_amount > 0),

                              CONSTRAINT chk_fine_tariffs_amount_exists
                                  CHECK (amount_per_day IS NOT NULL OR fixed_amount IS NOT NULL),

                              CONSTRAINT chk_fine_tariffs_valid_period
                                  CHECK (valid_to IS NULL OR valid_to > valid_from),

                              CONSTRAINT chk_fine_tariffs_max_amount_logic
                                  CHECK (
                                      max_amount IS NULL
                                          OR (
                                          (fixed_amount IS NULL OR max_amount >= fixed_amount)
                                              AND (amount_per_day IS NULL OR max_amount >= amount_per_day)
                                          )
                                      )
);

CREATE UNIQUE INDEX uq_fine_tariffs_one_active_per_violation
    ON fine_tariffs (violation_type)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_fine_tariffs_violation_status
    ON fine_tariffs USING btree (violation_type, status);

CREATE INDEX idx_fine_tariffs_valid_period
    ON fine_tariffs USING btree (valid_from, valid_to);

CREATE TABLE fines (
                       id              BIGSERIAL PRIMARY KEY,
                       user_id         BIGINT NOT NULL,
                       loan_id         BIGINT,
                       copy_id         BIGINT,
                       tariff_id       BIGINT,
                       reason          violation_type NOT NULL,
                       amount          NUMERIC(10, 2) NOT NULL,
                       status          fine_status NOT NULL DEFAULT 'ACTIVE',
                       created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       paid_at         TIMESTAMP,
                       cancelled_at    TIMESTAMP,
                       cancellation_reason TEXT,

                       CONSTRAINT fk_fines_user
                           FOREIGN KEY (user_id)
                               REFERENCES users (id)
                               ON DELETE RESTRICT,

                       CONSTRAINT fk_fines_loan
                           FOREIGN KEY (loan_id)
                               REFERENCES loans (id)
                               ON DELETE SET NULL,

                       CONSTRAINT fk_fines_copy
                           FOREIGN KEY (copy_id)
                               REFERENCES material_copies (id)
                               ON DELETE SET NULL,

                       CONSTRAINT fk_fines_tariff
                           FOREIGN KEY (tariff_id)
                               REFERENCES fine_tariffs (id)
                               ON DELETE SET NULL,

                       CONSTRAINT chk_fines_amount
                           CHECK (amount > 0),

                       CONSTRAINT chk_fines_paid_at
                           CHECK (paid_at IS NULL OR paid_at >= created_at),

                       CONSTRAINT chk_fines_cancelled_at
                           CHECK (cancelled_at IS NULL OR cancelled_at >= created_at),

                       CONSTRAINT chk_fines_paid_status
                           CHECK (
                               (status = 'PAID' AND paid_at IS NOT NULL AND cancelled_at IS NULL)
                                   OR status <> 'PAID'
                               ),

                       CONSTRAINT chk_fines_cancelled_status
                           CHECK (
                               (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND paid_at IS NULL)
                                   OR status <> 'CANCELLED'
                               ),

                       CONSTRAINT chk_fines_cancellation_reason
                           CHECK (
                               (status = 'CANCELLED' AND cancellation_reason IS NOT NULL AND btrim(cancellation_reason) <> '')
                                   OR status <> 'CANCELLED'
                               )
);

CREATE INDEX idx_fines_user_status
    ON fines USING btree (user_id, status);

CREATE INDEX idx_fines_loan
    ON fines USING btree (loan_id);

CREATE INDEX idx_fines_copy
    ON fines USING btree (copy_id);

CREATE INDEX idx_fines_tariff
    ON fines USING btree (tariff_id);

CREATE INDEX idx_fines_reason_status
    ON fines USING btree (reason, status);

CREATE INDEX idx_fines_created_at
    ON fines USING btree (created_at);

CREATE TABLE payment_transactions (
                                      id                   BIGSERIAL PRIMARY KEY,
                                      fine_id              BIGINT NOT NULL,
                                      external_payment_id  VARCHAR(255) UNIQUE,
                                      amount               NUMERIC(10, 2) NOT NULL,
                                      status               payment_status NOT NULL DEFAULT 'CREATED',
                                      created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_at           TIMESTAMP,

                                      CONSTRAINT fk_payment_transactions_fine
                                          FOREIGN KEY (fine_id)
                                              REFERENCES fines (id)
                                              ON DELETE RESTRICT,

                                      CONSTRAINT chk_payment_transactions_amount
                                          CHECK (amount > 0),

                                      CONSTRAINT chk_payment_transactions_updated_at
                                          CHECK (updated_at IS NULL OR updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_payment_transactions_one_success_per_fine
    ON payment_transactions (fine_id)
    WHERE status = 'SUCCESS';

CREATE UNIQUE INDEX uq_payment_transactions_one_in_progress_per_fine
    ON payment_transactions (fine_id)
    WHERE status IN ('CREATED', 'PENDING');

CREATE INDEX idx_payment_transactions_fine_status
    ON payment_transactions USING btree (fine_id, status);

CREATE INDEX idx_payment_transactions_status
    ON payment_transactions USING btree (status);

CREATE INDEX idx_payment_transactions_created_at
    ON payment_transactions USING btree (created_at);

CREATE TABLE notification_templates (
                                        id                    BIGSERIAL PRIMARY KEY,
                                        type                  notification_type NOT NULL,
                                        channel               notification_channel NOT NULL,
                                        subject_template      VARCHAR(255),
                                        body_template         TEXT NOT NULL,
                                        required_parameters   JSONB NOT NULL DEFAULT '[]'::jsonb,
                                        status                notification_template_status NOT NULL DEFAULT 'ACTIVE',
                                        created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                        CONSTRAINT chk_notification_templates_required_parameters
                                            CHECK (jsonb_typeof(required_parameters) = 'array')
);

CREATE UNIQUE INDEX uq_notification_templates_one_active_per_type_channel
    ON notification_templates (type, channel)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_notification_templates_status
    ON notification_templates USING btree (status);

CREATE INDEX idx_notification_templates_type_channel
    ON notification_templates USING btree (type, channel);

CREATE TABLE notification_preferences (
                                          id          BIGSERIAL PRIMARY KEY,
                                          user_id     BIGINT NOT NULL,
                                          type        notification_type NOT NULL,
                                          channel     notification_channel NOT NULL,
                                          enabled     BOOLEAN NOT NULL DEFAULT TRUE,
                                          preferred   BOOLEAN NOT NULL DEFAULT FALSE,
                                          created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                          CONSTRAINT fk_notification_preferences_user
                                              FOREIGN KEY (user_id)
                                                  REFERENCES users (id)
                                                  ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_notification_preferences_user_type_channel
    ON notification_preferences (user_id, type, channel);

CREATE UNIQUE INDEX uq_notification_preferences_one_preferred_channel
    ON notification_preferences (user_id, type)
    WHERE preferred = TRUE;

CREATE INDEX idx_notification_preferences_user
    ON notification_preferences USING btree (user_id);

CREATE INDEX idx_notification_preferences_type_channel
    ON notification_preferences USING btree (type, channel);

CREATE TABLE notifications (
                               id              BIGSERIAL PRIMARY KEY,
                               user_id         BIGINT NOT NULL,
                               reservation_id  BIGINT,
                               loan_id         BIGINT,
                               fine_id         BIGINT,
                               type            notification_type NOT NULL,
                               channel         notification_channel NOT NULL,
                               subject         VARCHAR(255),
                               body            TEXT,
                               status          notification_status NOT NULL DEFAULT 'PENDING',
                               created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               sent_at         TIMESTAMP,
                               read_at         TIMESTAMP,
                               external_message_id VARCHAR(255),
                               error_message   TEXT,
                               attempt_count   INTEGER NOT NULL DEFAULT 0,

                               CONSTRAINT fk_notifications_user
                                   FOREIGN KEY (user_id)
                                       REFERENCES users (id)
                                       ON DELETE RESTRICT,

                               CONSTRAINT fk_notifications_reservation
                                   FOREIGN KEY (reservation_id)
                                       REFERENCES reservations (id)
                                       ON DELETE SET NULL,

                               CONSTRAINT fk_notifications_loan
                                   FOREIGN KEY (loan_id)
                                       REFERENCES loans (id)
                                       ON DELETE SET NULL,

                               CONSTRAINT fk_notifications_fine
                                   FOREIGN KEY (fine_id)
                                       REFERENCES fines (id)
                                       ON DELETE SET NULL,

                               CONSTRAINT chk_notifications_sent_at
                                   CHECK (sent_at IS NULL OR sent_at >= created_at),

                               CONSTRAINT chk_notifications_sent_status
                                   CHECK (
                                       (status IN ('SENT', 'DELIVERED') AND sent_at IS NOT NULL)
                                           OR status NOT IN ('SENT', 'DELIVERED')
                                       ),

                               CONSTRAINT chk_notifications_read_at
                                   CHECK (read_at IS NULL OR read_at >= created_at),

                               CONSTRAINT chk_notifications_attempt_count
                                   CHECK (attempt_count >= 0)
);

CREATE INDEX idx_notifications_user_status
    ON notifications USING btree (user_id, status);

CREATE INDEX idx_notifications_status_created_at
    ON notifications USING btree (status, created_at);

CREATE INDEX idx_notifications_type
    ON notifications USING btree (type);

CREATE INDEX idx_notifications_reservation
    ON notifications USING btree (reservation_id);

CREATE INDEX idx_notifications_loan
    ON notifications USING btree (loan_id);

CREATE INDEX idx_notifications_fine
    ON notifications USING btree (fine_id);

CREATE TABLE audit_logs (
                            id              BIGSERIAL PRIMARY KEY,
                            actor_user_id   BIGINT,
                            entity_type     audit_entity_type NOT NULL,
                            entity_id       BIGINT,
                            action          audit_action NOT NULL,
                            details         JSONB,
                            created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                            CONSTRAINT fk_audit_logs_actor_user
                                FOREIGN KEY (actor_user_id)
                                    REFERENCES users (id)
                                    ON DELETE SET NULL,

                            CONSTRAINT chk_audit_logs_details_is_object
                                CHECK (details IS NULL OR jsonb_typeof(details) = 'object')
);

CREATE INDEX idx_audit_logs_actor_created_at
    ON audit_logs USING btree (actor_user_id, created_at);

CREATE INDEX idx_audit_logs_entity
    ON audit_logs USING btree (entity_type, entity_id);

CREATE INDEX idx_audit_logs_action
    ON audit_logs USING btree (action);

CREATE INDEX idx_audit_logs_created_at
    ON audit_logs USING btree (created_at);

CREATE INDEX idx_audit_logs_details
    ON audit_logs USING gin (details);
