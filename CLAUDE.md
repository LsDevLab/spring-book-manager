# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A **Developer Book Tracker REST API** built with Spring Boot 4.0.3 and Java 21. Users manage a technical reading list — add books, track reading status/progress, and get recommendations by topic. This is an **educational/learning project** for exploring Spring Boot concepts.

## Build & Run Commands

```bash
# Start infrastructure (Postgres + Redis)
docker compose up -d

# Build (skip tests)
./mvnw clean package -DskipTests

# Run the app (dev profile, port 8081)
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=BookRepositoryTest

# Run a single test method
./mvnw test -Dtest=BookRepositoryTest#testMethodName
```

Swagger UI: `http://localhost:8081/swagger-ui.html`

## Profiles

- **dev** (default): Postgres via docker-compose, Redis, `show-sql: true`, Hibernate `ddl-auto: update`
- **test**: H2 in-memory DB, Hibernate `ddl-auto: create-drop`

## Architecture

Standard layered Spring Boot architecture: Controller → Service → Repository.

- **Entities**: `Book`, `User`, `UserBook` (join entity with reading status/progress). `Topic` and `ReadingStatus` are enums.
- **DTOs**: Request/response DTOs in `dto/request/` and `dto/response/`. Entities are never exposed directly. `BookMapper` (MapStruct) handles Book ↔ DTO conversion.
- **Security**: JWT-based stateless auth. `JwtAuthenticationFilter` extracts tokens, `JwtTokenProvider` creates/validates them. `/api/auth/**` and Swagger paths are public; everything else requires authentication. Role-based access via `@PreAuthorize` and `@EnableMethodSecurity`.
- **Caching**: Two-tier — Caffeine (in-memory) configured in `CacheConfiguration`, Redis for distributed cache. Both configured via `application.yaml`.
- **Events**: `BookCompletedEvent` published when a user marks a book as COMPLETED, handled asynchronously by `BookCompletedEventListener`.
- **Scheduling**: `ScheduledTasks` runs periodic jobs (trending book refresh, active session logging).
- **Exceptions**: Custom exceptions (`BookNotFoundException`, `UserBookNotFoundException`, etc.) handled globally by `GlobalExceptionHandler` (`@ControllerAdvice`).

## Key Conventions

- Uses **Lombok** (`@Data`, `@RequiredArgsConstructor`, etc.) — fields won't have visible getters/setters in source.
- Uses **MapStruct** for Book mapping — generated implementation is in `target/generated-sources/`.
- Annotation processors: Lombok runs before MapStruct (configured in `pom.xml` compiler plugin).
- This is a learning project — when helping, **explain concepts and guide rather than just providing code**. Keep old approaches commented out for comparison when replacing with new patterns.