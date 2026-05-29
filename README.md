# Library Service

![Java](https://img.shields.io/badge/Java-17+-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)
![Redis](https://img.shields.io/badge/Redis-cache-red)
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
* Apache Kafka
* Maven

### Infrastructure

* Docker Compose
* REST API
* JSON
* Logging and monitoring infrastructure
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
├── postman/              # Postman API collection
├── scripts/              # Database initialization scripts
├── docker-compose.yml    # Local infrastructure
├── README.md
└── LICENSE
```
