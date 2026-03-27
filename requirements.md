# Developer Book Tracker API

A REST API where developers can manage their technical reading list — add books, track reading status, write notes, and get recommendations by topic.

---

## Entities

**User**
- `id`, `username`, `email`

**Book**
- `id`, `title`, `author`, `isbn`
- `topic` (enum: `BACKEND`, `FRONTEND`, `DEVOPS`, `ALGORITHMS`, `ARCHITECTURE`)
- `totalPages`

**UserBook** *(join entity between User and Book)*
- `id`, `status` (enum: `WANT_TO_READ`, `READING`, `COMPLETED`)
- `currentPage`, `notes`, `startedAt`, `completedAt`

---

## Endpoints

### Books
| Method | Path | Description | Topics |
|---|---|---|---|
| GET | `/api/books` | List all books, filterable by topic, paginated | `@RestController`, `@GetMapping`, `Pageable`, filtering |
| GET | `/api/books/{id}` | Get a single book | `@PathVariable`, `@Cacheable` |
| POST | `/api/books` | Add a new book | `@PostMapping`, `@Valid`, `@RequestBody` |
| PUT | `/api/books/{id}` | Update a book | `@PutMapping`, `@CachePut` |
| DELETE | `/api/books/{id}` | Delete a book | `@DeleteMapping`, `@CacheEvict` |

### User Reading List
| Method | Path | Description | Topics |
|---|---|---|---|
| GET | `/api/users/{userId}/books` | Get user's full reading list | `@OneToMany`, DTO mapping |
| POST | `/api/users/{userId}/books/{bookId}` | Add book to reading list | `@ManyToMany`, `@Transactional` |
| PATCH | `/api/users/{userId}/books/{bookId}` | Update progress, notes, status | `@PatchMapping`, Spring Events |
| DELETE | `/api/users/{userId}/books/{bookId}` | Remove book from reading list | `@Transactional` |

### Recommendations
| Method | Path | Description | Topics |
|---|---|---|---|
| GET | `/api/recommendations?topic=BACKEND` | Get trending books by topic | `@Cacheable`, `@RequestParam` |
| POST | `/api/admin/recommendations/refresh` | Manually evict cache and refresh | `@CacheEvict` |

### Stats
| Method | Path | Description | Topics |
|---|---|---|---|
| GET | `/api/users/{userId}/stats` | Books completed, pages read, favourite topic | `@Query`, aggregation |
| GET | `/api/admin/stats` | Global platform stats | `@Cacheable`, `@Scheduled` refresh |

---

## Features & Cross-Cutting Concerns

### Persistence
- Map all entities using JPA annotations (`@Entity`, `@Table`, `@OneToMany`, `@ManyToMany`)
- Use Spring Data JPA repositories (`JpaRepository`)
- Write custom queries for stats and topic filtering (`@Query`)
- *(Topics: `@Entity`, `@OneToMany`, `@ManyToMany`, `JpaRepository`, `@Query`)*

### Validation & Error Handling
- Validate all incoming request bodies (e.g. `isbn` format, non-blank fields, page range)
- Handle all errors globally with a consistent JSON error response
- *(Topics: `@Valid`, `@NotBlank`, `@Min/@Max`, `@ControllerAdvice`, `@ExceptionHandler`)*

### DTOs
- Never expose entities directly — use request/response DTO classes for all endpoints
- *(Topics: DTO pattern, `@RequestBody`, `@ResponseBody`, Lombok `@Data`)*

### Caching
- Cache `GET /api/books/{id}` and `GET /api/recommendations`
- Evict/update cache on book mutations
- Start with Caffeine (in-memory), then swap to Redis by changing config only — zero service code changes
- *(Topics: `@EnableCaching`, `@Cacheable`, `@CachePut`, `@CacheEvict`, Caffeine, Redis)*

### Scheduling
- Every night at 2am: fetch trending books from Open Library API (or mock it), persist results, evict recommendations cache
- Every 60 seconds: log a count of active reading sessions
- *(Topics: `@EnableScheduling`, `@Scheduled` with `cron` and `fixedRate`)*

### Async & Events
- When a user marks a book as `COMPLETED`, publish an internal application event
- Handle the event asynchronously: update user stats, log an achievement, send a mock congratulations email
- *(Topics: `ApplicationEventPublisher`, `@EventListener`, `@Async`, `@EnableAsync`)*

### Transactions
- Wrap all multi-step DB operations (e.g. adding a book to a reading list + updating stats) in a single transaction
- *(Topics: `@Transactional`, rollback behaviour)*

### Configuration & Profiles
- Use `dev` profile with PostgreSQL and `test` profile with H2 in-memory DB
- Externalise all sensitive config (DB credentials, ports) in `application.properties` / `application.yml`
- *(Topics: `@Profile`, Spring Profiles, `application.yml`, `server.port`)*

### Monitoring
- Expose Actuator endpoints for health, cache metrics, and scheduled task info
- *(Topics: Spring Boot Actuator, `/actuator/health`, `/actuator/metrics`)*

### Authentication & Authorization
- Secure all API endpoints using Spring Security with JWT-based stateless authentication
- Users register and log in via dedicated auth endpoints; successful login returns a JWT access token
- Protect endpoints by role: regular users can manage their own reading list; only `ADMIN` users can access `/api/admin/**` endpoints
- Passwords are hashed with BCrypt before persisting
- *(Topics: `Spring Security`, `SecurityFilterChain`, `JwtAuthenticationFilter`, `OncePerRequestFilter`, `BCryptPasswordEncoder`, `@PreAuthorize`, `@EnableMethodSecurity`, `UserDetailsService`)*

---

## Authentication Endpoints

| Method | Path | Description | Topics |
|---|---|---|---|
| POST | `/api/auth/register` | Register a new user (username, email, password) | `@PostMapping`, `BCryptPasswordEncoder`, `@Valid` |
| POST | `/api/auth/login` | Authenticate and receive a JWT token | `AuthenticationManager`, `JwtTokenProvider` |
| GET | `/api/auth/me` | Get the currently authenticated user's profile | `SecurityContextHolder`, `@AuthenticationPrincipal` |

---

## Infrastructure (Docker)

```bash
# PostgreSQL
docker run --name devlibrary-db \
  -e POSTGRES_DB=devlibrary \
  -e POSTGRES_USER=dev \
  -e POSTGRES_PASSWORD=secret \
  -p 5432:5432 \
  -d postgres:16

# Redis (for Stage 2 caching)
docker run --name devlibrary-redis \
  -p 6379:6379 \
  -d redis:7
```

---

## Suggested Build Order

1. Project setup via start.spring.io (`Spring Web`, `Spring Data JPA`, `PostgreSQL Driver`, `Validation`, `Lombok`)
2. DB connection + verify app starts
3. `Book` entity + basic CRUD endpoints
4. `User` + `UserBook` entity + reading list endpoints
5. Validation + global error handling
6. DTOs for all endpoints
7. Filtering + pagination on book list
8. Custom `@Query` for stats endpoint
9. Spring Profiles — H2 for tests, Postgres for dev
10. Caching with Caffeine → swap to Redis
11. Scheduling — nightly refresh + interval logger
12. Events + Async — book completion flow
13. Actuator — health + metrics
14. Authentication — Spring Security + JWT + role-based access

---

## Spring Boot Topics Coverage Summary

| Topic | Covered by |
|---|---|
| `@RestController`, `@GetMapping` etc. | All endpoints |
| `@Service`, `@Repository`, `@Component` | All layers |
| Spring Data JPA | All DB access |
| `@OneToMany`, `@ManyToMany` | User ↔ Book relationship |
| `@Query` | Stats + filtering |
| `@Valid`, `@ControllerAdvice` | Validation + error handling |
| DTOs + Lombok | Request/response mapping |
| `Pageable` | Book list pagination |
| `@Transactional` | Multi-step DB operations |
| `@Cacheable`, `@CacheEvict`, `@CachePut` | Book + recommendations cache |
| Caffeine + Redis | Cache implementations |
| `@Scheduled` (cron + fixedRate) | Nightly refresh + interval logger |
| `ApplicationEventPublisher` + `@EventListener` | Book completion event |
| `@Async` | Async event handling |
| Spring Profiles | Dev/test environments |
| `application.yml` | Externalised config |
| Spring Boot Actuator | Monitoring |
| Spring Security, `SecurityFilterChain` | Authentication & authorization |
| JWT (`JwtTokenProvider`, `JwtAuthenticationFilter`) | Stateless auth |
| `BCryptPasswordEncoder` | Password hashing |
| `@PreAuthorize`, `@EnableMethodSecurity` | Role-based access control |
| `UserDetailsService` | Custom user loading |
