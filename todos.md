# DevLibrary — Developer Book Tracker API

A REST API where developers can manage their technical reading list — add books, track reading status, write notes, and get recommendations by topic.

---

## Phase 1 — Foundation
> Get the app running with a real database

- [x] **1.1 Project setup** — Create project on start.spring.io, add dependencies, verify app starts on custom port
    - `Spring Boot autoconfiguration` `application.yml` `server.port` `Lombok`
- [x] **1.2 Connect to PostgreSQL** — Configure datasource, verify Hibernate can reach the DB *(depends on 1.1)*
    - `application.yml` `spring.datasource` `spring.jpa.hibernate.ddl-auto`
- [x] **1.3 Book entity + repository** — Define the Book entity and a JpaRepository for it *(depends on 1.2)*
    - `@Entity` `@Table` `@Id` `@GeneratedValue` `JpaRepository`

---

## Phase 2 — Basic CRUD
> First working REST endpoints — the core of any Spring Boot app

- [x] **2.1 Book CRUD endpoints (no validation)** — GET /api/books, GET /api/books/{id}, POST, PUT, DELETE — plain, no error handling yet *(depends on 1.3)*
    - `@RestController` `@GetMapping` `@PostMapping` `@PutMapping` `@DeleteMapping` `@PathVariable` `@RequestBody` `@Service`
- [x] **2.2 Add DTOs** — Introduce request/response DTO classes, stop exposing entities directly *(depends on 2.1)*
    - `DTO pattern` `Lombok @Data` `@RequestBody` `@ResponseBody`
- [x] **2.3 Add validation to Book endpoints** — Validate incoming request bodies (non-blank fields, isbn format, page range) *(depends on 2.2)*
    - `@Valid` `@NotBlank` `@Min` `@Max` `@Pattern`
- [x] **2.4 Global error handling** — Return consistent JSON error responses for validation errors, not-found, and unexpected exceptions *(depends on 2.3)*
    - `@ControllerAdvice` `@ExceptionHandler` `ResponseEntity` `custom exceptions`

---

## Phase 3 — Relationships & Reading List
> Introduce User, the UserBook join entity, and multi-entity operations

- [x] **3.1 User entity + CRUD** — Create User entity, repository, service, and basic endpoints *(depends on 2.4)*
    - `@Entity` `JpaRepository` `@RestController`
- [x] **3.2 UserBook join entity + reading list endpoints** — Model the User ↔ Book relationship, add POST and DELETE for the reading list *(depends on 3.1)*
    - `@ManyToOne` `@OneToMany` `@JoinColumn` `@ManyToMany` `@Transactional`
- [x] **3.3 Update progress endpoint** — PATCH /api/users/{userId}/books/{bookId} — update currentPage, notes, status *(depends on 3.2)*
    - `@PatchMapping` `@Transactional` `partial update pattern`
- [x] **3.4 User stats endpoint (basic)** — GET /api/users/{userId}/stats using derived queries *(depends on 3.3)*
    - `Spring Data derived queries` `JpaRepository query methods`
- [x] **3.5 Enrich stats with custom @Query** — Replace derived queries with JPQL for aggregations *(depends on 3.4)*
    - `@Query` `JPQL` `aggregation` `projections`

---

## Phase 4 — Query Power
> Filtering, sorting and pagination on the book list

- [x] **4.1 Pagination on GET /api/books** — Add Pageable support — page, size, sort query params out of the box *(depends on 2.4)*
    - `Pageable` `PageRequest` `Page<T>` `Spring Data pagination`
- [x] **4.2 Filtering by topic** — Add ?topic=BACKEND filtering to GET /api/books *(depends on 4.1)*
    - `@RequestParam` `derived query methods`

---

## Phase 5 — Profiles & Environments
> Make the app behave differently per environment

- [x] **5.1 Add test profile with H2** — Create application-test.yml using H2 in-memory DB so tests don't need Postgres running *(depends on 3.5)*
    - `Spring Profiles` `@Profile` `application-{profile}.yml` `H2`
- [x] **5.2 Add dev profile for Postgres** — Move Postgres config into application-dev.yml, keep base config minimal *(depends on 5.1)*
    - `Spring Profiles` `profile activation` `application.yml`

---

## Phase 6 — Caching
> Add caching in two stages — first in-memory, then Redis

- [x] **6.1 Cache book lookups with Caffeine** — @Cacheable on GET /api/books/{id}, @CacheEvict on DELETE, @CachePut on PUT *(depends on 5.2)*
    - `@EnableCaching` `@Cacheable` `@CacheEvict` `@CachePut` `Caffeine`
- [x] **6.2 Cache recommendations endpoint** — GET /api/recommendations?topic=X with @Cacheable keyed by topic *(depends on 6.1)*
    - `@Cacheable key` `cache key expressions` `@RequestParam`
- [x] **6.3 Swap Caffeine for Redis** — Add Redis Docker container and change spring.cache.type=redis, no service code changes needed *(depends on 6.2)*
    - `Redis` `spring.cache.type` `Spring cache abstraction` `Docker`
- [x] **6.4 Manual cache refresh endpoint** — POST /api/admin/recommendations/refresh — evict all recommendations cache entries *(depends on 6.3)*
    - `@CacheEvict allEntries` `admin endpoints`

---

## Phase 7 — Scheduling
> Background jobs running on a timer

- [x] **7.1 Interval logger** — Log a count of active reading sessions every 60 seconds *(depends on 5.2)*
    - `@EnableScheduling` `@Scheduled fixedRate`
- [x] **7.2 Nightly recommendation refresh job** — Every night at 2am: fetch trending books, persist, evict cache *(depends on 7.1, 6.4)*
    - `@Scheduled cron` `cron expressions` `RestTemplate / WebClient` `@CacheEvict`

---

## Phase 8 — Events & Async
> Decouple side effects from the main flow using internal events

- [x] **8.1 Publish BookCompletedEvent** — When status changes to COMPLETED in the PATCH endpoint, publish an internal event *(depends on 3.3)*
    - `ApplicationEventPublisher` `custom event class` `record`
- [x] **8.2 Handle event asynchronously** — Listen to BookCompletedEvent — update stats, log achievement, send mock email *(depends on 8.1)*
    - `@EventListener` `@Async` `@EnableAsync` `async thread pool`

---

## Phase 9 — Monitoring
> Observe what's happening inside the running app

- [x] **9.1 Add Spring Boot Actuator** — Expose /actuator/health and /actuator/info *(depends on 5.2)*
    - `Spring Boot Actuator` `/actuator/health` `management.endpoints`
- [x] **9.2 Expose cache and scheduler metrics** — Enable /actuator/metrics and /actuator/scheduledtasks *(depends on 9.1, 7.2, 6.3)*
    - `/actuator/metrics` `/actuator/scheduledtasks` `Micrometer`

---

## Phase 10 — Authentication & Authorization
> Secure the API with Spring Security and JWT-based stateless authentication

- [ ] **10.1 Add Spring Security + JWT dependencies** — Add spring-boot-starter-security, jjwt library, and configure a basic SecurityFilterChain that permits auth endpoints and secures everything else *(depends on 3.1)*
    - `Spring Security` `SecurityFilterChain` `HttpSecurity` `authorizeHttpRequests`
- [ ] **10.2 Password hashing & User entity update** — Add `password` and `role` (enum: `USER`, `ADMIN`) fields to the User entity, hash passwords with BCrypt on registration *(depends on 10.1)*
    - `BCryptPasswordEncoder` `@Entity` `enum Role`
- [ ] **10.3 Register endpoint** — POST /api/auth/register — create a new user with hashed password and default role USER *(depends on 10.2)*
    - `@PostMapping` `@Valid` `BCryptPasswordEncoder` `DTO pattern`
- [ ] **10.4 Login endpoint + JWT generation** — POST /api/auth/login — authenticate credentials and return a signed JWT token *(depends on 10.3)*
    - `AuthenticationManager` `UsernamePasswordAuthenticationToken` `JwtTokenProvider` `HMAC signing`
- [ ] **10.5 JWT authentication filter** — Implement a OncePerRequestFilter that reads the Authorization header, validates the JWT, and sets the SecurityContext *(depends on 10.4)*
    - `OncePerRequestFilter` `JwtAuthenticationFilter` `SecurityContextHolder` `UserDetailsService`
- [ ] **10.6 Get current user endpoint** — GET /api/auth/me — return profile of the authenticated user *(depends on 10.5)*
    - `@AuthenticationPrincipal` `SecurityContextHolder`
- [ ] **10.7 Role-based access control** — Restrict /api/admin/** endpoints to ADMIN role, ensure users can only access their own reading list *(depends on 10.6)*
    - `@PreAuthorize` `@EnableMethodSecurity` `hasRole('ADMIN')` `ownership check`