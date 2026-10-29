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

The main backend service is implemented as a Java Spring REST API. It communicates with PostgreSQL for persistent storage and shared authentication quotas, Redis for catalog caching, Apache Kafka for asynchronous event streaming, and external services for payments and notifications.

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
`GET /api/actuator/prometheus` in the explicitly selected `local` profile.
Prometheus authenticates as the dedicated `prometheus` account using the shared
file `secrets/security.monitoring.password` and scrapes this endpoint every five
seconds, and Grafana automatically provisions both the Prometheus datasource and
the `Library Service Overview` dashboard.

Create the ignored monitoring secret once, select the `local` backend profile,
and start the monitoring stack after the backend is running on port `1234`:

```shell
pwsh -File scripts/create-monitoring-secret.ps1
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

Application JWTs cannot access metrics. The monitoring credentials only read
Prometheus metrics and do not authenticate business API requests. Health responses
hide components and diagnostic details, including on component subpaths.
Production uses a separate private management listener; see Deployment profiles below.

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

## Public authentication protection

Flyway migration `V12__protect_public_auth.sql` adds shared PostgreSQL quotas and
retention metadata for authentication notifications. Restart the backend to apply
it before using the updated authentication code.

Every public authentication POST command has an IP quota, including malformed
JSON and invalid DTOs. Valid commands also consume a quota for the normalized
email, or the submitted code for activation/reset. Registration additionally
limits equivalent `8…` / `+7…` phone numbers. Defaults are fixed windows:

| Command | Per IP | Per email/code | Window |
| --- | ---: | ---: | --- |
| `login` | 60 | 20 | 15 minutes |
| `register` | 10 | 3 | 1 hour |
| `password-recovery` | 20 | 3 | 1 hour |
| `resend-activation` | 20 | 3 | 1 hour |
| `activate` | 30 | 10 | 15 minutes |
| `password-reset` | 30 | 10 | 15 minutes |

Registration, recovery and activation resend also share a budget of **3 attempts
per email per hour**, including unknown accounts. Exceeding a quota returns `429`
with `Retry-After`; unavailable quota storage returns a generic `503` and does
not run the command. Counters commit independently of the business transaction,
so failed login/code attempts still count. All backend replicas must use the same
database and `AUTH_RATE_LIMIT_KEY` (at least 32 UTF-8 bytes; defaults to `JWT_SECRET`).
Quota keys are HMAC digests rather than raw contacts, IP addresses or codes.
Quota commits use a separate pool of at most two database connections per replica,
with a two-second connection/query timeout, so business transactions holding the
main pool cannot deadlock while waiting to record their authentication attempts.

The `AUTH_<COMMAND>_IP_LIMIT`, `AUTH_<COMMAND>_ACCOUNT_LIMIT` and
`AUTH_<COMMAND>_WINDOW` variables configure login/register/recovery/resend.
Activation/reset use `AUTH_ACTIVATE_TOKEN_LIMIT` / `AUTH_RESET_TOKEN_LIMIT`
instead of an account limit. `AUTH_MAIL_ACCOUNT_LIMIT` and `AUTH_MAIL_WINDOW`
configure the shared email budget. Redis cache eviction cannot reset these limits.

Registration returns the same `201` message for a new account and duplicate
email/phone. Recovery and activation resend return the same `200` message for
eligible, ineligible and missing accounts. Login uses one credentials error for
missing, inactive and locked accounts and still performs BCrypt work for missing
accounts. New registration/reset passwords are limited to **72 UTF-8 bytes**.
Responses are marked `Cache-Control: no-store`, buffered until at least
`AUTH_MINIMUM_RESPONSE_TIME` (default `400ms`, after transaction completion), and
request bodies are limited by `AUTH_MAX_REQUEST_BYTES` (default `16384`). This
reduces timing differences; it does not guarantee constant response times under load.

Forwarded headers are not trusted by default (`server.forward-headers-strategy:
none`). If using Nginx, set `AUTH_TRUSTED_PROXIES` to the explicit proxy IP/CIDR
list and have that proxy **overwrite** `X-Real-IP` with `$remote_addr`. Only that
header from an allowed direct peer is used; `X-Forwarded-For` is ignored. Keep the
backend behind the proxy and share the HMAC key across replicas. Use consistent
JVM time zones across replicas, preferably UTC; Hibernate stores timestamps in UTC.

The retention job runs every `AUTH_CLEANUP_INTERVAL_MS` (default `60000`) using
bounded batches and row locks safe for multiple replicas. Defaults:

- Delete auth codes **24 hours after use or expiry** (`AUTH_TOKEN_RETENTION`).
- Scrub secret notification bodies, subjects and delivery errors after code
  use/expiry or a maximum message age of **24 hours**
  (`AUTH_SECRET_BODY_RETENTION`). Notification history and IDs remain.
- Preserve fresh in-flight deliveries. Expired stale deliveries become `UNKNOWN`
  and are not automatically retried. Pending expired/used messages are not claimed.
- Authentication messages cannot be manually requeued through the general
  notification resend endpoint; request a fresh code through authentication instead.

`AUTH_CLEANUP_BATCH_SIZE` defaults to `100`; `AUTH_CLEANUP_ENABLED` defaults to
`true`. Cleanup is eventual, with delay depending on the interval and backlog.
Active codes remain plaintext in `auth_tokens` and message bodies for delivery;
this change bounds their retention and does not add encryption or hashing at rest.

## Deployment profiles

The default configuration exposes only a minimal health endpoint. Monitoring is
disabled, health details/components are hidden, Swagger is disabled, and no local
`.env` file is imported. Choose the deployment profile explicitly.

For local development, run from the project root:

```powershell
./scripts/create-monitoring-secret.ps1
$env:SPRING_PROFILES_ACTIVE = 'local'
mvn -f backend/pom.xml spring-boot:run
```

In IntelliJ, set the active Spring profile to `local`. That profile imports `.env`
from the working directory or its parent and imports the monitoring secret from
`secrets/` or `../secrets/`. The script creates a random secret without printing it
and preserves an existing file. The `secrets/` directory is ignored by Git.
The tracked `application-local.yml` contains configuration, not private values;
keep credentials in the ignored `.env` and secret files.

After updating an existing local stack, recreate the containers whose published
ports or secret mounts changed:

```shell
docker compose up -d --force-recreate library-db pgadmin prometheus
```

This recreates containers while retaining their named volumes. Database clients
will need to reconnect. Local PostgreSQL and pgAdmin now bind only to `127.0.0.1`,
as do the other published infrastructure ports.

### Production configuration

`docker-compose.prod.yml` is a standalone Compose file. Run it with `-f`; do not
merge it with the local Compose file. It uses the separate `library-service-prod`
project and separate named volumes.

- Backend application: `127.0.0.1:8080` on the Docker host, for a host reverse proxy.
- Actuator: `backend:8081/actuator` inside the monitoring network; no published port.
- Prometheus: `http://backend:8081/actuator/prometheus`, with file-based Basic credentials.
- PostgreSQL, Redis, Kafka, MinIO and Prometheus: no host port publication.
- Grafana: `127.0.0.1:3000`, accessible through an administrative SSH tunnel.
- pgAdmin is absent. Kafka has only private internal/controller listeners.

The infrastructure and monitoring networks are internal Docker networks. The
backend also joins an application network for outbound SMTP and other required
connections. Application JWTs, including ADMIN, do not grant access to Actuator.
The monitoring account cannot access `env`, `configprops`, `heapdump`, `info`,
`metrics`, discovery links or application endpoints.

Copy `production.env.example` to the ignored `.env.production` and fill in the
required credentials. `JWT_SECRET` must be a Base64 signing key with at least
64 random bytes; `AUTH_RATE_LIMIT_KEY` must contain at least 32 bytes and be shared
across backend replicas. Database, storage, mail and Grafana passwords have no
production fallback values.

On Windows, create the monitoring secret with the PowerShell script. On a Linux
server, create it before starting Compose:

```shell
install -d -m 700 secrets
umask 077
(
  set -C
  openssl rand -base64 32 | tr -d '\n' > secrets/security.monitoring.password
)
chmod 644 secrets/security.monitoring.password
```

Run the Linux generation commands only for a new secret; preserve an existing
file. The private directory protects the host file, while the file's read
permissions allow the different backend/Prometheus container users to read their
explicitly granted mounts. Compose mounts the secret read-only. After rotation,
restart backend and Prometheus together.

Build and validate the deployment from the project root with Docker running:

```shell
mvn -f backend/pom.xml clean verify
docker compose --env-file .env.production -f docker-compose.prod.yml config --quiet
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
```

The Dockerfile packages the verified JAR and runs Java as an unprivileged user.
Only the JAR is included in its build context; `.env` and monitoring secrets are
not copied into the image. The production profile reads the monitoring secret
from `/run/secrets/security.monitoring.password` and refuses enabled monitoring
with a missing or unsupported password. Its HTTP port, management port and scrape
path are independent of the local `SERVER_PORT` setting.

Host Nginx can proxy application traffic to `127.0.0.1:8080`; keep management and
infrastructure ports outside public proxy routes. When adding Nginx, configure
`AUTH_TRUSTED_PROXIES` with its actual direct peer IP/CIDR as seen by the backend
and have Nginx overwrite `X-Real-IP`, as described under Public authentication
protection. The default trusts no proxy headers.
TLS, a public domain, backup
verification and deployment on an actual server are separate deployment steps.

## Authorization regression tests and CI

`AccessSecurityIntegrationTest` runs HTTP requests through the real security
filters, signed JWTs, controllers, services and PostgreSQL 16 with all Flyway
migrations. S3 access and external notification delivery are replaced at their
transport boundaries; no real messages are sent.

`AccessEndpointCatalog` specifies the expected policy for all 117 business HTTP
operations for anonymous, READER, LIBRARIAN and ADMIN requests. The suite checks
that every controller and route has a case. Separate scenarios create committed
records for two readers and check ownership, unfiltered search, pagination,
foreign filters and commands, staff hierarchy and unchanged persisted state
after denied requests.

Account hierarchy violations return `403 ACCESS_DENIED`. Cancelling or expiring
warnings and manually expiring blocks check the authenticated actor against the
target's current roles. JWT checks cover signature, expiry, version, password
reset, disabled/locked accounts and role revocation with an already issued token.
The JWT filter also handles an account disappearing or a token expiring between
validation and principal loading as an unauthenticated request.

Activation and password recovery notifications remain excluded from search and
their subject, body and delivery error are redacted in detail/read responses.
The generic notification API cannot manually create, cancel or resend these
messages, including for staff accounts; authentication commands manage their
lifecycle. Ordinary notifications retain their existing manual operations.

Run the focused security tests with Docker available:

```shell
mvn -f backend/pom.xml "-Dtest=AccessSecurityIntegrationTest,JwtAuthenticationFilterTest,NotificationServiceDeliveryStatusTest" test
```

`.github/workflows/backend.yml` runs on pushes and pull requests. It requires
Docker, executes `mvn clean verify` including migration/OpenAPI tests and JaCoCo,
checks that every integration suite executed without skips, and builds the
backend image. Test and coverage reports are retained for seven days. To enforce
this job on merges, select `verify` from the Backend workflow as a required status
check in the repository's branch protection settings.

The same report check can be run locally after the full build:

```shell
mvn -f backend/pom.xml clean verify
python scripts/verify-test-reports.py
```
