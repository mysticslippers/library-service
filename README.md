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
