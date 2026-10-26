# Library Service

![Java](https://img.shields.io/badge/Java-17+-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)
![Redis](https://img.shields.io/badge/Redis-cache-red)
![S3](https://img.shields.io/badge/S3-object%20storage-569A31)
![MinIO](https://img.shields.io/badge/MinIO-local%20S3-C72E49)
![Prometheus](https://img.shields.io/badge/Prometheus-metrics-E6522C)
![Grafana](https://img.shields.io/badge/Grafana-dashboards-F46800)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-event--streaming-black)
![License](https://img.shields.io/badge/license-MIT-lightgrey)
[![CodeFactor](https://www.codefactor.io/repository/github/mysticslippers/library-service/badge)](https://www.codefactor.io/repository/github/mysticslippers/library-service)

Library Service is a library management system designed to automate catalog search, material reservation, book loans, returns, fine calculation and payment processing.

The project was developed as a system analysis and software design project. It includes requirements specification, use case modeling, BPMN process diagrams, C4 architecture diagrams, class diagram and sequence diagram.

## Overview

The system is intended for three main user groups:

* **Readers** — search materials, reserve books, view active loans and pay fines.
* **Librarians** — manage the catalog, process reservations, register book loans and returns.
* **System administrators** — configure the system, monitor its operation and manage technical settings.

The main goal of the project is to automate common library workflows and provide a centralized service for managing materials, users, reservations, loans, returns, fines and notifications.

## Features

* User registration and authentication
* Catalog search with filtering and sorting
* Material reservation and waiting queue
* Book loan processing
* Book return processing
* Fine calculation for overdue materials
* Fine payment through an external payment service
* Email/SMS notifications
* Audit logging for critical operations
* Basic reporting and monitoring support

## Tech Stack

### Backend

* Java 17+
* Spring Boot
* Spring Web
* Spring Security
* Spring Data JPA / Hibernate
* PostgreSQL
* Redis
* AWS S3 SDK / MinIO
* Apache Kafka
* Maven

### Infrastructure

* Docker Compose
* REST API
* JSON
* Micrometer, Prometheus and Grafana
* External payment service integration
* External email/SMS notification service integration

### Planned Client Applications

* Web App: React, Redux, TypeScript
* Mobile App: Flutter
* Desktop App: Kotlin Compose Multiplatform

## Architecture

The system follows a client-server architecture with a centralized backend API.

The main backend service is implemented as a Java Spring REST API. It communicates with PostgreSQL for persistent storage, Redis for caching and rate limiting, Apache Kafka for asynchronous event streaming, and external services for payments and notifications.

<details>
<summary><b>Context Diagram</b></summary>

![Context Diagram](docs/diagrams/context.png)

</details>

<details>
<summary><b>Container Diagram</b></summary>

![Container Diagram](docs/diagrams/containers.png)

</details>

<details>
<summary><b>Component Diagram</b></summary>

![Component Diagram](docs/diagrams/components.png)

</details>

<details>
<summary><b>Class Diagram</b></summary>

![Class Diagram](docs/diagrams/class-diagram.png)

</details>

<details>
<summary><b>Sequence Diagram</b></summary>

![Sequence Diagram](docs/diagrams/sequence-fine-payment.png)

</details>

## Core Business Processes

The project includes BPMN diagrams for the main library workflows.

<details>
<summary><b>Book Loan Process</b></summary>

![Book Loan Process](docs/diagrams/bpmn/book-loan-process.png)

</details>

<details>
<summary><b>Book Return Process</b></summary>

![Book Return Process](docs/diagrams/bpmn/book-return-process.png)

</details>

## Project Structure

```text
library-service/
├── backend/              # Spring Boot backend application
├── docs/                 # Requirements, use cases, architecture and reports
├── observability/        # Prometheus and Grafana configuration
├── postman/              # Postman API collection
├── scripts/              # Database initialization scripts
├── docker-compose.yml    # Local infrastructure
├── README.md
└── LICENSE
```

## Authentication concurrency

Login locks the user's database row before checking account status or password and
holds the lock until commit. Failed password attempts are committed even though
login returns an invalid-credentials error. Password reset acquires the same row
lock before reading or changing the user, so a concurrent login cannot restore an
old password hash or overwrite the reset's cleared attempt count. Requests for
different accounts can proceed independently.

Activation, password reset and code reissue use the same user row lock. A code is
loaded and checked for use and expiration only after acquiring that lock; the
initial owner lookup reads only the user ID. The account change and code consumption
commit together, and reissue revokes earlier unused codes of the same type. All
these operations acquire the user lock before reading or updating token entities.

With Docker running, `AuthConcurrencyIntegrationTest` checks concurrent logins,
the fifth-failure lockout, both orderings of login versus password reset, account
blocking and JWT revocation using PostgreSQL, BCrypt and signed JWTs. It also checks
single-use activation/reset codes, parallel reissue, both orderings of code use
versus reissue, and rollback of consumption or reissue. Notification delivery is
stubbed; these tests do not send messages.

## Audit data policy

Audit snapshots and manual events use explicit field allowlists in
`V8__sanitize_audit_details.sql` and `AuditDetailsSanitizer`. Unknown fields and
unexpected nested payloads are discarded. User snapshots retain account IDs,
status and security timestamps, excluding password hashes and contact details.
Password updates retain an event with `password_changed: true`. Notification
bodies, subjects, transport errors and credential fields are excluded from audit
details. `AuditLog.toString()` includes event metadata only.

V8 sanitizes existing audit history without changing event IDs, actors, actions or
timestamps, and protects subsequent database writes. Java sanitizes manual events
before persistence and filters details again when building API responses. Adding
an audited field requires updating both policies; PostgreSQL integration tests
check their agreement for every audit entity type and exercise migration from V7.
HTTP tests also check legacy data filtering and the controller's ADMIN restriction.

## Reservation pickup

Issue a reserved copy with `POST /api/loans`, providing `reservationId` along with
`userId`, `copyId`, `branchId`, and `issuedByUserId`. The reservation must be
`READY_FOR_PICKUP`. A successful transaction creates an `ACTIVE` loan, changes
the reservation to `USED`, and changes the copy to `LOANED`. A failure rolls back
all three changes. There is no separate command to mark a reservation as used.

Creating a reservation or loan locks the reader's user row until the transaction
ends. Requests for the same reader check limits and duplicate reservations in
sequence, including requests made by different staff or in different branches.
Different readers using different copies can create reservations independently.

Commands that compete for a copy lock its database row before checking availability,
branch or active operations. Creation keeps the reader lock first; transitions of
existing loans/reservations read only the copy ID before locking copy, then the
operation row. This order also applies to cancellation, expiration, return, loss,
renewal and pickup readiness. Copy status/branch edits use the same copy lock.
Automatic selection uses `FOR UPDATE SKIP LOCKED`, choosing another available copy
or returning the existing unavailable-copy error when no unlocked copy is available.

V9 enforces matching copy/material/branch on reservations. Unreserved loans require
an available copy without an active reservation; reserved loans require the matching
reader, copy, branch and an unexpired `READY_FOR_PICKUP` reservation. These conditions
also apply to initial `ACTIVE`, `OVERDUE` and `LOST` loans, keeping their copy status
consistent. Expiration is compared in UTC, matching Hibernate's timestamp binding. Insert
triggers lock parents before foreign-key checks and unique-index insertion can
invert the copy lock order. Copy status/branch guards also protect direct updates.

SQL owns copy transitions on creation/closing and reservation consumption. Services
flush and refresh trigger-managed entities before returning responses. A return's
requested physical condition is applied after SQL closes the loan, in the same
transaction.

V10 validates status changes on existing loans and reservations, freezes their
reader/copy/branch associations, and prevents changes to closed records. A loan
can move from `ACTIVE` to `OVERDUE`, `LOST`, `RETURNED` or `CANCELLED`; from
`OVERDUE` to `LOST`, `RETURNED` or `CANCELLED`; and from `LOST` to `RETURNED`.
An active reservation can become ready, cancelled or expired; a ready reservation
can also become `USED`, provided its matching loan exists in the same transaction.
Renewal must increase both the due date and its counter once while the loan stays
active. Deadline and library-rule checks remain in the services under the row locks.
Circulation uses an injectable `Clock`, defaulting to the server's system zone,
so deadline equality and requests crossing a deadline can be tested deterministically.
V9 and V10 are new migrations; earlier migration files remain unchanged.

Renewal uses the current rules of the loan's branch. Omitting `renewalDays` adds
the rule's full `renewalPeriodDays`; an explicit value must be between 1 and that
period. Readers, librarians and administrators have the same bound. Requests
above it are rejected without changing the loan, copy or audit history. The
extension is added to the existing due date and consumes one allowed renewal.

Run backend checks from `backend/`:

```shell
mvn verify
```

With Docker running, `ReservationLoanIntegrationTest` starts an isolated
PostgreSQL 16 container and applies the production Flyway migrations, including
the circulation triggers. It checks pickup, return, duplicate requests, and
transaction rollback. These integration tests are skipped when Docker is unavailable;
the remaining tests still run.

`CirculationLimitsIntegrationTest` also uses an isolated PostgreSQL container to
check overlapping requests, reservation and loan limits, duplicate materials,
and capacity becoming available after rollback.

`CopyCirculationIntegrationTest` checks different readers competing for one copy,
automatic selection, independent copies, rollback, status/branch edits, pickup
against cancellation/expiration, return conditions and direct SQL validation.
It also covers SQL/service lock ordering and renewal against return.

`CirculationStateIntegrationTest` checks every source status for service commands,
every pair of database statuses, immutable associations, and overlapping commands
including renewal/loss/overdue, readiness/cancellation/expiration and repeated commands.
It uses the real reservation event publisher and transactional outbox: readiness
creates one committed event, while a rollback or serialization failure leaves no
event or partial status change. Event delivery to Kafka is outside this test.
It also verifies renewal period boundaries for all three roles, current rules
across branches and rule changes, and denial of another reader's renewal request.

## Fine payments

Repeating a payment status command with the same status and effective external
payment ID returns the stored result without changing rows, timestamps or audit
history. Omitting the external ID preserves its current value. An assigned ID
cannot be replaced; final payments cannot acquire a new ID or change status.
A repeated `SUCCESS` also requires the associated fine to be `PAID`.

Payment creation, status changes, manual fine payment and fine cancellation lock
the fine first. Status changes read only its ID before locking the fine, then
lock and refresh the payment. Different fines remain independent. Manual payment
and cancellation are rejected while a payment is `CREATED` or `PENDING`; finish
or cancel that payment first.

V11 makes the SQL trigger responsible for settling a fine on payment success,
including direct SQL inserts. Services flush and refresh trigger-managed values.
Database guards validate transitions, amounts and immutable associations, and
prevent changes to closed fines and final payments. Identical SQL updates skip
the write itself. Native insert defaults and payment update/settlement timestamps
use UTC, matching Hibernate's timestamp binding. Applied migrations remain unchanged.
Creation, update and closure timestamps remain ordered when the JVM and database
clocks differ.

A direct SQL payment update already holds its payment row: if another transaction
holds the fine lock, the guard rejects it with a constraint conflict so the command
can be retried instead of inverting the service's lock order. Inserts acquire the
fine lock before foreign-key checks and unique-index insertion.

`PaymentConsistencyIntegrationTest` uses PostgreSQL/Flyway and the actual services,
mappers and triggers. It covers status matrices, repeated confirmations, unchanged
timestamps/audit, external IDs, competing fine/payment commands, independent fines,
SQL/service lock ordering and transaction rollback. Payment provider/webhook
integration remains a separate planned feature.

## Email delivery

Pending email notifications are delivered through an SMTP server using Spring Mail.
Configure the connection with environment variables:

```dotenv
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=library@example.com
MAIL_PASSWORD=application-password
MAIL_FROM=library@example.com
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS_ENABLE=true
MAIL_SMTP_SSL_ENABLE=false
```

For a local SMTP catcher, the defaults are `localhost:1025` with authentication and
STARTTLS disabled. Pending notifications are processed through
`POST /api/notifications/process-pending`.

Each notification is claimed as `SENDING` in its own committed transaction before
contacting SMTP. Concurrent workers skip claimed rows. Delivery results are committed
individually, so later delivery or audit failures do not roll back earlier results.
The batch response includes an `unknown` count for attempts whose outcome could not
be confirmed. Only definite failures can be scheduled again through `/{id}/resend`.

An SMTP failure after sending starts may mean that the server already accepted the
message. Such attempts become `UNKNOWN` and are not automatically resent. On the
next processing run, interrupted `SENDING` attempts older than
`NOTIFICATION_DELIVERY_SENDING_TIMEOUT_MS` (default `300000`, five minutes) also
become `UNKNOWN`. Check the SMTP provider's logs before deciding how to resolve
these notifications; there is no automatic retry for an uncertain outcome.
Set this timeout above the maximum expected duration of a delivery attempt.

`PATCH /api/notifications/{id}/status` accepts only `CANCELLED` for unsent
notifications. Sending, retry counts and delivery results are managed internally.

With Docker running, `NotificationDeliveryIntegrationTest` checks concurrent workers,
per-message commits, database and audit failures, and interrupted attempts against an
isolated PostgreSQL container. It does not contact SMTP or SMS providers.

## Redis catalog cache

Redis is used as a non-critical cache-aside layer for catalog material search.
PostgreSQL remains the source of truth, and search falls back to the database when
Redis is unavailable.

Start the local Redis instance together with the database:

```shell
docker compose up -d library-db library-redis
```

Optional settings:

```dotenv
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_CONNECT_TIMEOUT=1s
REDIS_COMMAND_TIMEOUT=1s
CATALOG_SEARCH_CACHE_TTL=90s
CACHE_KEY_PREFIX=library-service-backend:local:
```

Only the first ten result pages with at most 100 elements are cached. Catalog,
branch, loan, and reservation mutations that change material search results clear
the cached pages after a successful transaction.

## Kafka domain events

Kafka carries asynchronous domain events while PostgreSQL remains the source of
truth. Start the local single-node KRaft broker:

```shell
docker compose up -d kafka
```

The first implemented flow publishes `reservation.ready-for-pickup` to
`library.circulation.events`. The reservation update and an `outbox_events` row
are committed in the same PostgreSQL transaction. A background publisher
delivers that row to Kafka, and the `library-notification` consumer group creates
an idempotent `RESERVATION_READY` notification. Failed consumer records are
retried and then moved to a dead-letter topic.

Useful local checks:

```shell
docker exec library-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list

docker exec library-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic library.circulation.events \
  --from-beginning
```

Optional settings:

```dotenv
KAFKA_PORT=9092
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_CIRCULATION_TOPIC=library.circulation.events
KAFKA_TOPIC_PARTITIONS=3
KAFKA_TOPIC_REPLICATION_FACTOR=1
KAFKA_NOTIFICATION_GROUP_ID=library-notification
KAFKA_NOTIFICATION_RETRY_ATTEMPTS=4
KAFKA_NOTIFICATION_RETRY_DELAY_MS=1000
KAFKA_OUTBOX_BATCH_SIZE=50
KAFKA_OUTBOX_POLL_INTERVAL_MS=1000
KAFKA_OUTBOX_PUBLISH_TIMEOUT=5s
KAFKA_OUTBOX_RETRY_DELAY=5s
```

The bundled broker uses plaintext and replication factor `1`; it is intended
only for local development. Production requires separate security,
replication, retention and controller/broker topology settings.

## S3-compatible book cover storage

Cover image bytes are stored in a private S3-compatible bucket. PostgreSQL keeps
only a versioned object key, while catalog responses expose a cache-safe URL such
as `/api/materials/42/cover?v=<version>`. The local S3 implementation is MinIO;
the backend itself uses AWS SDK for Java 2.x and can be pointed at AWS S3 or
another compatible provider without changing the catalog API.

Start MinIO:

```shell
docker compose up -d minio
```

Open the local MinIO console at `http://localhost:9001`. The local defaults are
`minioadmin` / `minioadmin`; override them in `.env`:

```dotenv
S3_ENDPOINT=http://localhost:9000
S3_REGION=us-east-1
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=choose-a-local-secret
S3_COVERS_BUCKET=library-covers
S3_PATH_STYLE_ACCESS=true
S3_CREATE_BUCKET=true
S3_API_CALL_TIMEOUT=10s
COVER_MAX_FILE_SIZE=5MB
COVER_MAX_REQUEST_SIZE=6MB
MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001
```

Cover endpoints:

* `PUT /api/materials/{id}/cover` — multipart field `file`, librarian/admin only
* `GET /api/materials/{id}/cover` — public for active catalog materials
* `DELETE /api/materials/{id}/cover` — librarian/admin only

Uploads are limited to JPEG, PNG and WebP. The backend validates the actual file
signature, creates the private bucket on demand in the local environment, and
removes replaced objects only after the PostgreSQL transaction commits.

The bundled single-node MinIO container is intended for local development. For
production, use a managed or replicated S3-compatible store, separate
least-privilege credentials, and set `S3_CREATE_BUCKET=false` after provisioning
the bucket outside the application.

## Prometheus and Grafana

The backend exports Micrometer metrics through
`GET /api/actuator/prometheus`. Prometheus scrapes this endpoint every five
seconds, and Grafana automatically provisions both the Prometheus datasource and
the `Library Service Overview` dashboard.

Start the monitoring stack after the backend is running on port `1234`:

```shell
docker compose up -d prometheus grafana
```

Open:

* Prometheus: `http://localhost:9090/targets`
* Grafana: `http://localhost:3000`
* Provisioned dashboard:
  `http://localhost:3000/d/library-service-overview/library-service-overview`

The local Grafana credentials default to `admin` / `admin`. Override them in
`.env` before the first Grafana startup:

```dotenv
PROMETHEUS_PORT=9090
PROMETHEUS_RETENTION=7d
GRAFANA_PORT=3000
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=choose-a-local-password
```

The Prometheus scrape target is `host.docker.internal:1234`. If the backend
`SERVER_PORT` changes, update the target in
`observability/prometheus/prometheus.yml`.

For production, keep `/api/actuator/prometheus` on an internal management
network or protect it at the gateway; it is public in the local security
configuration so the Dockerized Prometheus instance can scrape it.

The dashboard includes backend availability, HTTP throughput, 5xx rate and p95
latency, JVM heap and CPU, HikariCP connections, observed business-operation
metrics, and catalog-cache hit/miss behavior.

## SMS delivery

SMS delivery code is retained for future use but is disabled by default. The backend
exposes only email preferences and rejects explicit SMS notification requests while
the enabled channel list contains only `EMAIL`:

```dotenv
NOTIFICATION_DELIVERY_ENABLED_CHANNELS=EMAIL
```

To enable SMS in the future, configure a private SMS.RU API key and explicitly add
the channel:

```dotenv
NOTIFICATION_DELIVERY_ENABLED_CHANNELS=EMAIL,SMS
SMS_RU_API_ID=your-private-api-id
SMS_RU_TEST_MODE=true
```

Optional settings include `SMS_RU_SENDER`, `SMS_RU_CONNECT_TIMEOUT`, and
`SMS_RU_READ_TIMEOUT`. The sender name must be approved by SMS.RU before use.
