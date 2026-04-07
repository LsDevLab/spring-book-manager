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

- [x] **10.1 Add Spring Security + JWT dependencies** — Add spring-boot-starter-security, jjwt library, and configure a basic SecurityFilterChain that permits auth endpoints and secures everything else *(depends on 3.1)*
    - `Spring Security` `SecurityFilterChain` `HttpSecurity` `authorizeHttpRequests`
- [x] **10.2 Password hashing & User entity update** — Add `password` and `role` (enum: `USER`, `ADMIN`) fields to the User entity, hash passwords with BCrypt on registration *(depends on 10.1)*
    - `BCryptPasswordEncoder` `@Entity` `enum Role`
- [x] **10.3 Register endpoint** — POST /api/auth/register — create a new user with hashed password and default role USER *(depends on 10.2)*
    - `@PostMapping` `@Valid` `BCryptPasswordEncoder` `DTO pattern`
- [x] **10.4 Login endpoint + JWT generation** — POST /api/auth/login — authenticate credentials and return a signed JWT token *(depends on 10.3)*
    - `AuthenticationManager` `UsernamePasswordAuthenticationToken` `JwtTokenProvider` `HMAC signing`
- [x] **10.5 JWT authentication filter** — Implement a OncePerRequestFilter that reads the Authorization header, validates the JWT, and sets the SecurityContext *(depends on 10.4)*
    - `OncePerRequestFilter` `JwtAuthenticationFilter` `SecurityContextHolder` `UserDetailsService`
- [x] **10.6 Get current user endpoint** — GET /api/auth/me — return profile of the authenticated user *(depends on 10.5)*
    - `@AuthenticationPrincipal` `SecurityContextHolder`
- [x] **10.7 Role-based access control** — Restrict /api/admin/** endpoints to ADMIN role, ensure users can only access their own reading list *(depends on 10.6)*
    - `@PreAuthorize` `@EnableMethodSecurity` `hasRole('ADMIN')` `ownership check`

---

## Phase 11 — API Documentation
> Auto-generated interactive docs and collection export

- [x] **11.1 Add SpringDoc OpenAPI** — Add springdoc-openapi dependency, expose Swagger UI and OpenAPI JSON spec *(depends on 10.7)*
    - `springdoc-openapi` `Swagger UI` `/v3/api-docs` `/swagger-ui.html`
- [x] **11.2 Annotate controllers with Swagger annotations** — Add @Tag, @Operation, @ApiResponse, @Parameter to all endpoints *(depends on 11.1)*
    - `@Tag` `@Operation` `@ApiResponse` `@Parameter` `@SecurityRequirement`
- [x] **11.3 Annotate DTOs with @Schema** — Add descriptions and examples to all request/response DTO fields *(depends on 11.2)*
    - `@Schema` `example values` `description`
- [x] **11.4 Configure JWT auth in Swagger** — Add @OpenAPIDefinition and @SecurityScheme so the Authorize button works in Swagger UI *(depends on 11.2)*
    - `@OpenAPIDefinition` `@SecurityScheme` `SecuritySchemeType.HTTP` `bearerFormat`
- [x] **11.5 Postman collection setup** — Import OpenAPI spec into Postman, configure environment variables (baseUrl, token, userId) with auto-token script *(depends on 11.1)*
    - `Postman import` `environment variables` `post-response scripts`

---

## Phase 12 — JPA Specifications
> Dynamic, composable queries — filter by any combination of fields without writing a new method for each one

- [x] **12.1 Extend BookRepository with JpaSpecificationExecutor** — One-line change that unlocks Specification-based queries *(depends on 4.2)*
    - `JpaSpecificationExecutor<Book>` `Specification<T>`
- [x] **12.2 Create BookSpecifications utility class** — Static methods returning reusable Specification lambdas: titleContains, authorContains, hasTopic, minPages, maxPages *(depends on 12.1)*
    - `Specification<Book>` `CriteriaBuilder` `Root<Book>` `Predicate` `lambda composition`
- [x] **12.3 Create BookSearchDTO** — DTO with all optional filter fields, bound from query params. All nullable — only non-null fields become active filters *(depends on 12.2)*
    - `@RequestParam binding` `nullable fields` `partial filter pattern`
- [x] **12.4 Build dynamic queries in BookService** — Chain non-null Specifications with .and(), pass to findAll(Specification, Pageable) *(depends on 12.2, 12.3)*
    - `Specification.where()` `.and()` `.or()` `dynamic composition`
- [x] **12.5 Update search endpoint** — Refactor GET /api/books/search to accept BookSearchDTO. Clients can now filter by any combination: ?title=clean&topic=BACKEND&minPages=100 *(depends on 12.4)*
    - `composable filters` `multi-criteria search` `Swagger docs update`

---

## Phase 13 — Advanced Redis
> Go beyond simple caching — rate limiting, pub/sub, distributed locking

- [x] **13.1 Rate limiting with Redis** — Store request counts per user with TTL, return 429 Too Many Requests when exceeded *(depends on 6.3, 10.5)*
    - `RedisTemplate` `increment` `expire` `TTL` `HandlerInterceptor` `429 status`
- [x] **13.2 Live reading activity with Redis data structures** — Track who's reading what in real-time using Hash, Set, and Sorted Set — no DB queries for reads *(depends on 13.1, 3.3)*
    - `opsForHash()` `opsForSet()` `opsForZSet()` `ZINCRBY` `ZREVRANGE` `HSET` `HGETALL` `SADD` `SCARD`
    - **13.2a** Update progress (existing PATCH) also writes session to Redis Hash + updates Sorted Set by topic
    - **13.2b** GET /api/activity/topic/{topic} — who's reading books in this topic right now? (Sorted Set + Hash)
    - **13.2c** GET /api/activity/book/{bookId} — who's reading this book? (Set + Hash)
    - **13.2d** GET /api/activity/book/{bookId}/count — live reader count (Set SCARD)
    - **13.2e** Completing a book removes the user from all active reading structures
- [x] **13.3 Unique reader stats with HyperLogLog** — Track approximate unique reader count per topic and per book using PFADD/PFCOUNT — constant ~12KB memory regardless of millions of users *(depends on 13.2)*
    - `opsForHyperLogLog()` `PFADD` `PFCOUNT` `PFMERGE` `probabilistic data structure`
    - **13.3a** PFADD when a user starts reading a book — add userId to `unique_readers:<topic>` and `unique_readers:book:<bookId>`
    - **13.3b** GET /api/activity/topic/{topic}/unique-readers — approximate unique reader count for a topic
    - **13.3c** GET /api/activity/book/{bookId}/unique-readers — approximate unique reader count for a book
    - **13.3d** Compare with Set-based exact count — same answer, fraction of the memory

## Phase 14 — Keycloak Integration
> Replace hand-rolled JWT auth with a production-grade identity provider

- [ ] **14.1 Set up Keycloak with Docker** — Run Keycloak container, create a realm, define client, and configure roles (USER, ADMIN) via the admin console *(depends on 10.7)*
    - `Keycloak` `Docker Compose` `realm` `client` `roles` `OIDC`
- [ ] **14.2 Configure Spring as OAuth2 Resource Server** — Replace custom JwtAuthenticationFilter with spring-boot-starter-oauth2-resource-server, validate Keycloak-issued tokens *(depends on 14.1)*
    - `spring-boot-starter-oauth2-resource-server` `JwtDecoder` `issuer-uri` `jwk-set-uri`
- [ ] **14.3 Map Keycloak roles to Spring Security authorities** — Extract realm/client roles from the JWT claims and convert to GrantedAuthority so @PreAuthorize still works *(depends on 14.2)*
    - `JwtAuthenticationConverter` `granted authorities mapper` `realm_access` `resource_access`
- [ ] **14.4 Remove custom auth code** — Delete AuthController, JwtTokenProvider, JwtAuthenticationFilter, AuthUserDetails. Registration and login are now handled by Keycloak *(depends on 14.3)*
    - `code cleanup` `separation of concerns` `externalized auth`

---

## Phase 15 — GraphQL
> Expose a GraphQL endpoint alongside REST — clients query exactly the fields they need

- [ ] **15.1 Add Spring GraphQL starter** — Add spring-boot-starter-graphql, create the .graphqls schema file with Book, User, UserBook types *(depends on 3.2)*
    - `spring-boot-starter-graphql` `SDL schema` `type` `Query` `Mutation`
- [ ] **15.2 Book queries and mutations** — Implement @QueryMapping for books (list, byId, search) and @MutationMapping for create/update/delete *(depends on 15.1)*
    - `@QueryMapping` `@MutationMapping` `@Argument` `DataFetcher`
- [ ] **15.3 Reading list queries with nested types** — Query a user's reading list with nested book details, let the client choose which fields to fetch *(depends on 15.2)*
    - `nested types` `@SchemaMapping` `N+1 problem` `@BatchMapping`
- [ ] **15.4 Secure GraphQL with Spring Security** — Apply authentication and role-based authorization to GraphQL operations *(depends on 15.3, 10.7)*
    - `@PreAuthorize on GraphQL` `SecurityContext in GraphQL` `query-level vs field-level auth`
- [ ] **15.5 GraphiQL playground** — Enable the built-in GraphiQL UI for interactive query testing *(depends on 15.1)*
    - `spring.graphql.graphiql.enabled` `GraphiQL` `introspection`