# SaveStack — Architecture & Design Decisions

## Table of Contents

1. [Overview](#1-overview)
2. [Core Principles](#2-core-principles)
3. [System Architecture](#3-system-architecture)
   - 3.1 [High-Level Diagram](#31-high-level-diagram)
   - 3.2 [System Components](#32-system-components)
4. [Communication Model](#4-communication-model)
   - 4.1 [Communication Matrix](#41-communication-matrix)
   - 4.2 [Decision: HTTP vs Kafka](#42-decision-http-vs-kafka)
   - 4.3 [Client-to-Plugin Direct Communication](#43-client-to-plugin-direct-communication)
5. [Plugin Discovery](#5-plugin-discovery)
   - 5.1 [Discovery Flow](#51-discovery-flow)
   - 5.2 [Plugin Startup (Belt + Suspenders)](#52-plugin-startup-belt--suspenders)
   - 5.3 [Deploying & Removing Plugins](#53-deploying--removing-plugins)
6. [Plugin Lifecycle](#6-plugin-lifecycle)
   - 6.1 [Plugin States](#61-plugin-states)
   - 6.2 [User Activation Flow](#62-user-activation-flow)
7. [Plugin Settings](#7-plugin-settings)
   - 7.1 [Where Settings Live](#71-where-settings-live)
   - 7.2 [Settings Sync via Kafka](#72-settings-sync-via-kafka)
8. [Data Storage](#8-data-storage)
   - 8.1 [PostgreSQL Schema Isolation](#81-postgresql-schema-isolation)
   - 8.2 [Redis Usage](#82-redis-usage)
9. [Kafka Topics](#9-kafka-topics)
10. [Resilience & Failure Handling](#10-resilience--failure-handling)
    - 10.1 [Plugin Failure Scenarios](#101-plugin-failure-scenarios)
    - 10.2 [Resilience Principles](#102-resilience-principles)
    - 10.3 [Failure Recovery as a Runbook](#103-failure-recovery-as-a-runbook)
11. [Authentication](#11-authentication)
    - 11.1 [JWT-Based Stateless Auth](#111-jwt-based-stateless-auth)
    - 11.2 [How Services Validate Tokens](#112-how-services-validate-tokens)
12. [Architecture Decision Records](#12-architecture-decision-records)
    - 12.1 [Why Kafka over RabbitMQ?](#121-why-kafka-over-rabbitmq)
    - 12.2 [Why Single PostgreSQL with Schemas?](#122-why-single-postgresql-with-schemas)
    - 12.3 [Why Shared JWT Signing Key?](#123-why-shared-jwt-signing-key)
    - 12.4 [Why Client-to-Plugin Direct Communication?](#124-why-client-to-plugin-direct-communication)
    - 12.5 [Why Redis TTL Instead of Cleanup Jobs?](#125-why-redis-ttl-instead-of-cleanup-jobs)
13. [Plugin Tech Stacks](#13-plugin-tech-stacks)
    - 13.1 [Stack Assignment](#131-stack-assignment)
    - 13.2 [Universal Plugin Contract](#132-universal-plugin-contract)
14. [Frontend Architecture](#14-frontend-architecture)
    - 14.1 [Frontend Assignment](#141-frontend-assignment)
    - 14.2 [Routing Topology](#142-routing-topology)
    - 14.3 [Next.js — Public Website](#143-nextjs--public-website)
    - 14.4 [Angular — User Dashboard](#144-angular--user-dashboard)
    - 14.5 [Nuxt.js — Admin Panel](#145-nuxtjs--admin-panel)
15. [Full Tech Stack Summary](#15-full-tech-stack-summary)
    - 15.1 [Backend Services](#151-backend-services)
    - 15.2 [Frontend Applications](#152-frontend-applications)
    - 15.3 [Infrastructure](#153-infrastructure)
16. [Core Service Scope](#16-core-service-scope)
    - 16.1 [What Core DOES](#161-what-core-does)
    - 16.2 [What Core DOES NOT](#162-what-core-does-not)

---

## 1. Overview

SaveStack is a **cloud-hosted, plugin-based platform**. One deployment serves many users. Each user chooses which plugins to activate. All business logic runs server-side.

> **Key insight:** The architecture is designed so Core remains thin and agnostic to plugin details. Each plugin is independently deployable and the system degrades gracefully if any service fails.

---

## 2. Core Principles

1. **Plugins are microservices** — each plugin is an independent, deployable service
2. **Core is thin** — it handles auth, plugin discovery, notifications, and recommendation aggregation. It knows nothing about fuel, groceries, or budgets
3. **Zero coupling between plugins** — plugins never call each other. They communicate via Kafka events
4. **Graceful degradation** — if a plugin goes down, only that plugin's features are affected. Everything else keeps working
5. **Auto-discovery** — deploy a plugin service, it registers itself. No manual configuration in the core
6. **Free data sources** — MVP runs entirely on free, open, or public data

---

## 3. System Architecture

### 3.1 High-Level Diagram

```
                   ┌─────────────────────────────────────┐
                   │   CLIENT LAYER                      │
                   ├─────────────────────────────────────┤
                   │ Flutter / Web / CarPlay / ESP32      │
                   └──────────────────┬──────────────────┘
                                      │
                   ┌──────────────────┴──────────────────┐
                   │  1. POST /auth/login                │
                   │  2. GET /plugins/store (catalog)    │
                   │  3. JWT + plugin discovery          │
                   └──────────────────┬──────────────────┘
                                      │
                        ┌─────────────▼─────────────┐
                        │       CORE SERVICE        │
                        │                           │
                        │   • Authentication        │
                        │   • JWT Issuance          │
                        │   • Plugin Registry       │
                        │   • Kafka Discovery Pings │
                        │   • Notification Engine   │
                        │   • Settings Sync         │
                        └─────────────┬─────────────┘
                                      │
                ┌─────────────────────┼─────────────────────┐
                │                     │                     │
                │ Step 3: Clients call plugins directly     │
                │ (JWT in Authorization header)             │
                │                     │                     │
    ┌───────────▼──────────┐  ┌──────▼────────┐  ┌────────▼────────┐
    │   FUEL SERVICE       │  │ GROCERY SRVC  │  │  BUDGET SERVICE  │
    │                      │  │               │  │                  │
    │ POST /register       │  │ POST /register│  │ POST /register   │
    │ GET /recommendations │  │ GET /lists    │  │ GET /goals       │
    │ POST /heartbeat      │  │ GET /trips    │  │ GET /savings     │
    └─────────────┬────────┘  └───────┬───────┘  └────────┬─────────┘
                  │                   │                   │
                  │ ░░░░░░░ KAFKA EVENT BUS ░░░░░░░        │
                  │ (discovery, heartbeats, events)       │
                  │                   │                   │
                  └─────────────────────┼─────────────────┘
                                        │
                    ┌───────────────────┴────────────────────┐
                    │      PERSISTENT STORAGE LAYER         │
                    ├───────────────────┬────────────────────┤
                    │                   │                   │
            ┌───────▼────────┐  ┌───────▼───────┐  ┌────────▼──────┐
            │   PostgreSQL   │  │     Redis     │  │   MQTT Broker │
            │                │  │               │  │                │
            │ core schema    │  │ Plugin status │  │ → ESP32 device │
            │ fuel schema    │  │ Price caches  │  │                │
            │ grocery schema │  │ Recommendations│  └────────────────┘
            │ budget schema  │  │ TTL-based     │
            └────────────────┘  │ expiration    │
                                └───────────────┘

    LEGEND:
    ──────  HTTP synchronous (request/response)
    ░░░░░   Kafka asynchronous (pub/sub)
    → → →   Push via MQTT (WebSocket upgrade for real-time)
```

### 3.2 System Components

| Component | Type | Purpose |
|-----------|------|---------|
| **Core** | Spring Boot service | Authentication, plugin registry, notification engine, settings sync |
| **Fuel Service** | Spring Boot service | Fuel price aggregation, route optimization, recommendations |
| **Grocery Service** | FastAPI service | Supermarket promotion scraping, shopping list generation |
| **Budget Service** | Express service | Savings goal tracking, budget snapshots from Actual Budget |
| **PostgreSQL** | Relational DB | Persistent data storage, one schema per service |
| **Redis** | Key-value cache | Plugin health status, price/recommendation caches with TTL |
| **Kafka** | Event streaming | Discovery pings, heartbeats, cross-plugin events |
| **MQTT Broker** | Message bus | Push updates to physical devices (ESP32 displays) |

---

## 4. Communication Model

### 4.1 Communication Matrix

A complete map of who talks to whom, how, and when:

| From | To | Mechanism | Payload | When | Requires Response? |
|---|---|---|---|---|---|
| Client | Core | HTTP/REST | `POST /auth/login` credentials | User logs in | Yes (JWT + catalog) |
| Client | Core | HTTP/REST | `GET /api/plugins/store` | User views plugin store | Yes (list + status) |
| Client | Core | HTTP/REST | `PUT /api/user/settings` | User changes global settings | Yes (ACK) |
| Client | Plugin | HTTP/REST | `GET /api/recommendations` + JWT | User requests plugin features | Yes (recommendations) |
| Core | All Plugins | Kafka | `{ timestamp, correlationId }` | Startup, manual rediscover | No (broadcast) |
| Plugin | Core | HTTP/REST | `POST /api/plugins/register` + metadata | On discovery ping or startup | Yes (200 OK) |
| Plugin | Core | Kafka | `{ pluginId, timestamp }` | Every 30 seconds | No (fire-and-forget) |
| Plugin | Core | Kafka | `{ userId, action, settings }` | Settings changed in Core | No (notification) |
| Plugin | All | Kafka | `{ event, timestamp, userId }` | Trip completed, prices dropped, goals hit | No (broadcast) |
| Plugin | Plugin | Kafka | Indirect via topic subscription | Goal-aware recommendations | No (event-driven) |
| Core | Plugin | Kafka | `{ userId, pluginId, settings }` | User configures plugin | No (notification) |

### 4.2 Decision: HTTP vs Kafka

The golden rule:

```
┌────────────────────────────────────┐
│ NEED AN IMMEDIATE RESPONSE?        │
│ ├─ YES  → Use HTTP (synchronous)   │
│ └─ NO   → Use Kafka (asynchronous) │
└────────────────────────────────────┘
```

**HTTP is used for:**
- Login and token issuance (must complete before client proceeds)
- Plugin registration (Core needs to store immediately)
- Fetching data (client waiting for results)
- Configuration changes (user wants immediate feedback)

**Kafka is used for:**
- Discovery broadcasts (no need to know if every plugin heard it)
- Heartbeats (Core updates status asynchronously)
- Cross-plugin events (eventual consistency is acceptable)
- Notifications (can be queued if Core is temporarily slow)

> **Why this matters:** HTTP forces request/response semantics; Kafka enables fire-and-forget pub/sub. Mixing them thoughtfully prevents Core from becoming a bottleneck and allows plugins to operate independently.

### 4.3 Client-to-Plugin Direct Communication

The client gets a JWT from Core on login, then talks to plugins directly. Plugins validate the JWT independently (shared signing key). No core in the middle for business operations.

```
CLIENT WORKFLOW:

Step 1: Authenticate via Core
  POST http://core:8080/auth/login
  {
    "email": "user@example.com",
    "password": "secure_password"
  }
  ← Response:
    {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "expiresIn": 3600,
      "plugins": [
        {
          "id": "fuel",
          "name": "Fuel Intelligence",
          "baseUrl": "http://fuel-service:8082",
          "status": "UP"
        },
        {
          "id": "grocery",
          "name": "Smart Shopper",
          "baseUrl": "http://grocery-service:8083",
          "status": "UP"
        }
      ]
    }

Step 2: Use JWT to call plugins directly
  GET http://fuel-service:8082/api/recommendations
  Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

Step 3: Plugin validates JWT locally
  - Extracts public key from shared secret
  - Verifies signature matches
  - Checks expiration
  - Serves response (no Core involved)

Step 4: If JWT expires
  - Client detects 401 response
  - Client re-authenticates via Core
  - Gets new JWT
  - Proceeds normally
```

**Benefits:**
- Core is never in the request path for business data
- Plugins don't need to call Core to validate tokens
- Horizontal scaling doesn't require Core to be a bottleneck
- Network latency for plugin operations is minimal (direct client-to-service)

**Responsibilities:**
- **Client:** Caches plugin catalog and URLs locally; retries with new JWT if 401
- **Plugin:** Validates JWT signature independently
- **Core:** Issues JWT with shared signing key; doesn't need to validate every subsequent request

---

## 5. Plugin Discovery

### 5.1 Discovery Flow

Plugins are auto-discovered. No manual catalog, no configuration in Core. Deploy a service and it appears.

```
┌─────────────────────────────────────────────────────────────────┐
│ DISCOVERY PING (Kafka broadcast)                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Core publishes:                                                │
│  Topic: "core.discovery.ping"                                   │
│  Payload: { timestamp: "2026-04-09T15:30:00Z", version: "1" }  │
│                                                                 │
│  Triggered by:                                                  │
│    ├─ Core startup                                              │
│    ├─ Admin button: "Rediscover plugins"                        │
│    └─ Periodic heartbeat check (e.g., every 5 min)             │
│                                                                 │
│  Plugins listening on this topic hear it immediately.           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ Each plugin reacts
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ PLUGIN REGISTRATION (HTTP synchronous)                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Each plugin calls:                                             │
│  POST http://core:8080/api/plugins/register                     │
│  {                                                              │
│    "id": "fuel",                                                │
│    "name": "Fuel Intelligence",                                 │
│    "description": "Find cheapest fuel on your route",           │
│    "version": "0.1.0",                                          │
│    "category": "transport",                                     │
│    "icon": "fuel-pump",                                         │
│    "baseUrl": "http://fuel-service:8082",                       │
│    "settingsSchema": [                                          │
│      {                                                          │
│        "key": "carConsumption",                                 │
│        "type": "number",                                        │
│        "label": "L/100km",                                      │
│        "required": true                                         │
│      },                                                         │
│      {                                                          │
│        "key": "fuelType",                                       │
│        "type": "enum",                                          │
│        "options": ["benzina", "diesel", "gpl"],                 │
│        "default": "benzina"                                     │
│      }                                                          │
│    ]                                                            │
│  }                                                              │
│                                                                 │
│  Core's action:                                                 │
│    ├─ Upserts plugin metadata into PostgreSQL (plugins table)   │
│    ├─ Associates with version                                  │
│    └─ Returns 200 OK                                            │
│                                                                 │
│  Benefits of this approach:                                     │
│    ├─ Self-describing: plugin carries ALL its metadata         │
│    ├─ No config files in Core to update                        │
│    ├─ Settings schema is plugin-supplied, UI is generic        │
│    └─ Plugins can evolve their schema independently            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ Periodic health check
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ HEARTBEAT (Kafka → Redis)                                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Every 30 seconds, each plugin publishes:                       │
│  Topic: "plugins.heartbeat"                                     │
│  Payload: { pluginId: "fuel", timestamp: "2026-04-09T15..." }  │
│                                                                 │
│  Core's Kafka consumer listens and updates Redis:              │
│  SET plugin:fuel:status "UP" EX 90                              │
│                                                                 │
│  The 90-second TTL is critical:                                 │
│    ├─ If plugin sends heartbeat at t=0, key expires at t=90   │
│    ├─ If plugin is healthy, next heartbeat at t=30 renews     │
│    ├─ If plugin crashes at t=45, no heartbeat at t=60+        │
│    ├─ Key silently expires at t=90                             │
│    ├─ No cleanup job needed — Redis handles it                │
│    └─ UI shows "temporarily unavailable" after 90s of silence  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 Plugin Startup (Belt + Suspenders)

A plugin does **both** on startup:

1. **Subscribes** to `core.discovery.ping` topic (to listen for future pings)
2. **Tries to register immediately** via HTTP (fast path — no waiting for next ping)

```
Plugin startup sequence:

Time 0s:   Plugin container starts
           │
           ├─ Initialize Kafka consumer
           │   Subscribe to: "core.discovery.ping"
           │   (no blocking; will receive pings in background)
           │
           └─ Immediately attempt HTTP registration
              POST http://core:8080/api/plugins/register
              
              Scenario A: Core is already up
              └─ 200 OK → registered instantly ✓
              
              Scenario B: Core is starting too
              └─ Connection refused → plugin waits, retries
                 (exponential backoff, or polling)
              
Time 30s:  Core finishes startup
           Core publishes discovery ping to Kafka
           Plugin receives ping, confirms registration
           
           Either way, at t=60s:
           Plugin is registered and sending heartbeats
```

**Why both mechanisms?**

- **HTTP registration:** Fast path when both services are up. No waiting.
- **Kafka listener:** Handles startup race conditions. Plugin will eventually register even if Core started first.
- **Result:** Startup order doesn't matter. The system is self-healing.

### 5.3 Deploying & Removing Plugins

#### Deploy a new plugin

```
1. Add service to docker-compose.yml
   services:
     new-plugin:
       image: savestack/new-plugin:latest
       environment:
         CORE_URL: http://core:8080
         KAFKA_BOOTSTRAP_SERVERS: kafka:9092
         JWT_SECRET_KEY: ${JWT_SECRET_KEY}
       ports:
         - "8084:8080"

2. docker-compose up -d new-plugin

3. New plugin starts:
   ├─ Subscribes to "core.discovery.ping"
   ├─ Tries HTTP registration
   └─ Starts publishing heartbeat every 30s

4. Next discovery ping (manual or periodic):
   ├─ Core publishes to Kafka
   ├─ Plugin confirms registration
   └─ Plugin appears in store immediately

Result: Users see it in Plugin Store. No code change in Core. No database migration.
```

#### Remove a plugin

```
1. docker-compose down new-plugin

2. Monitoring (automatic):
   ├─ t=0s:   Plugin stops, heartbeats stop
   ├─ t=90s:  Redis key plugin:new-plugin:status expires
   ├─ t=91s:  Next plugin store query sees status = DOWN
   └─ Users see "Temporarily unavailable"

3. Optional: Cleanup job (async)
   ├─ Runs weekly
   ├─ Finds plugins with no heartbeat for 7+ days
   ├─ Deletes from PostgreSQL plugins table
   └─ (Users who activated it see warning: "This plugin no longer exists")
```

---

## 6. Plugin Lifecycle

### 6.1 Plugin States

From the user's perspective, a plugin can be in one of four states:

```
┌──────────────┐
│  AVAILABLE   │  ← Service is UP
│              │     User hasn't activated it
│              │     Appears in Plugin Store with "Activate" button
└──────────────┘

         ↓ User clicks "Activate"

┌──────────────┐
│    SETUP     │  ← User activated but hasn't filled required settings
│              │     "Missing: Fuel type, Car consumption"
│              │     Can't use features until configured
└──────────────┘

         ↓ User fills in settings

┌──────────────┐
│    ACTIVE    │  ← Service is UP + User activated + Settings complete
│              │     Features available in dashboard
│              │     Data syncing with plugin service
└──────────────┘

         ↑
         │ Service goes down

┌──────────────┐
│ UNAVAILABLE  │  ← Service is DOWN or never deployed
│              │     Redis key expired
│              │     User sees "Temporarily unavailable"
│              │     Deactivated for user (user can reactivate when it's back)
└──────────────┘
```

### 6.2 User Activation Flow

#### Step 1: Browse Plugin Store

```
User opens app → Flutter calls Core:
GET /api/plugins/store?userId=A

Core combines data from multiple sources:

  PostgreSQL (plugins table):
    id: "fuel"
    name: "Fuel Intelligence"
    baseUrl: "http://fuel-service:8082"
    version: "0.1.0"
    settingsSchema: [...]

  +

  Redis (plugin health):
    plugin:fuel:status = "UP" (with 90s TTL)

  +

  PostgreSQL (user_plugins table):
    user_id: A
    plugin_id: "fuel"
    is_active: false  (hasn't activated yet)
    settings: {}

  =

  Response to client:
  {
    "plugins": [
      {
        "id": "fuel",
        "name": "Fuel Intelligence",
        "description": "...",
        "status": "UP",              ← from Redis
        "isActivated": false,        ← from user_plugins
        "settingsSchema": [...],     ← from plugins table
        "category": "transport",
        "icon": "fuel-pump"
      },
      ...
    ]
  }

Client caches this list locally for offline access.
```

#### Step 2: User Activates Plugin

```
User taps "Activate" on Budget plugin:

Flutter → Core:
POST /api/user/plugins/budget/activate
Authorization: Bearer {JWT}

Core's action:
  ├─ Validates JWT
  ├─ Inserts into user_plugins table:
  │   (user_id=A, plugin_id="budget", is_active=true, settings={})
  ├─ Publishes Kafka event:
  │   Topic: "core.plugin.settings_changed"
  │   Payload:
  │   {
  │     "userId": "A",
  │     "pluginId": "budget",
  │     "action": "activated",
  │     "settings": {}
  │   }
  └─ Returns 200 OK to client

Budget service (listening on Kafka):
  ├─ Receives "activated" event
  ├─ Initializes data for User A:
  │   ├─ Creates empty goals table row
  │   ├─ Pulls first snapshot from Actual Budget API
  │   └─ Caches settings locally in Redis
  └─ Ready to serve /api/goals requests
```

#### Step 3: User Configures Plugin

```
User navigates to Budget settings:

Flutter → Core:
PUT /api/user/A/plugins/budget/settings
Authorization: Bearer {JWT}
{
  "goals": [
    {
      "name": "house",
      "target": 30000,
      "deadline": "2029-01"
    }
  ]
}

Core's action:
  ├─ Validates JWT
  ├─ Updates user_plugins table:
  │   settings = { goals: [...] }
  ├─ Publishes Kafka event:
  │   Topic: "core.plugin.settings_changed"
  │   Payload:
  │   {
  │     "userId": "A",
  │     "pluginId": "budget",
  │     "action": "settings_updated",
  │     "settings": { goals: [...] }
  │   }
  └─ Returns 200 OK

Budget service (listening on Kafka):
  ├─ Receives event with new settings
  ├─ Updates local cache in Redis
  ├─ Validates settings against schema
  └─ Next /api/goals request uses new goals
```

#### Step 4: Plugin is Now Active

```
From this point on:

Flutter → Budget Service (directly):
GET /api/goals
Authorization: Bearer {JWT}

Budget service:
  ├─ Validates JWT locally
  ├─ Reads goals from local cache (or DB)
  ├─ Computes progress against Actual Budget
  └─ Returns { goals, progress, projections }

Core is not involved. Pure plugin-to-user communication.
```

---

## 7. Plugin Settings

### 7.1 Where Settings Live

A clear breakdown of what lives where, and why:

| Data | Storage | Why |
|---|---|---|
| **Plugin metadata** (name, icon, version, API endpoint) | Core PostgreSQL (`plugins` table) | Persistent, rarely changes, needed for Plugin Store display |
| **Plugin live health** (UP/DOWN) | Core Redis with 90s TTL | Ephemeral, auto-expires on silence, fast lookup |
| **"User A activated Fuel plugin"** (activation state) | Core PostgreSQL (`user_plugins` table) | Core is source of truth for user-plugin relationships; persists across sessions |
| **User's plugin settings** (car consumption, fuel type, etc.) | **Core PostgreSQL (source of truth)** + **Plugin's own cache (local copy)** | Core stores it durably; Kafka syncs it; plugin uses local copy at runtime for instant responses |
| **Plugin-specific business data** (shopping lists, price history, goal snapshots) | **Plugin's own database only** | Core doesn't know what a goal looks like; each plugin owns its domain |

### 7.2 Settings Sync via Kafka

The complete settings lifecycle:

```
┌──────────────────────────────────────────────────────────────┐
│ SCENARIO: User changes plugin settings                       │
└──────────────────────────────────────────────────────────────┘

1. User fills form in Flutter dashboard
   └─ "Car consumption: 7.5 L/100km, Fuel type: diesel"

2. Flutter sends to Core (synchronous)
   PUT http://core:8080/api/user/A/plugins/fuel/settings
   Authorization: Bearer {JWT}
   {
     "carConsumption": 7.5,
     "fuelType": "diesel"
   }

3. Core processes (transactional)
   ├─ BEGIN TRANSACTION
   ├─ UPDATE user_plugins
   │     SET settings = { carConsumption: 7.5, fuelType: "diesel" }
   │   WHERE user_id = A AND plugin_id = "fuel"
   ├─ PUBLISH to Kafka topic "core.plugin.settings_changed":
   │   {
   │     "userId": "A",
   │     "pluginId": "fuel",
   │     "settings": {
   │       "carConsumption": 7.5,
   │       "fuelType": "diesel"
   │     },
   │     "timestamp": "2026-04-09T15:30:00Z"
   │   }
   ├─ COMMIT
   └─ Return 200 OK to Flutter

4. Flutter shows "Settings saved ✓"

5. Fuel service receives Kafka event asynchronously
   ├─ Validates settings against settingsSchema
   ├─ Updates local cache: SET user:A:fuel:settings JSON
   ├─ Logs: "User A settings changed: carConsumption=7.5"
   └─ Next API call uses new settings

┌──────────────────────────────────────────────────────────────┐
│ NORMAL OPERATION (after settings are synced)                │
└──────────────────────────────────────────────────────────────┘

User makes API call to Fuel service:
GET http://fuel-service:8082/api/recommendations
Authorization: Bearer {JWT}

Fuel service processes:
├─ Validates JWT
├─ Reads cached settings from local memory: carConsumption=7.5
├─ Queries recommendations for diesel vehicles
├─ Returns response
└─ NEVER calls Core during this request

Benefits:
  ├─ Sub-100ms response (local cache, no network call to Core)
  ├─ Fuel service doesn't depend on Core availability
  └─ Scale each service independently
```

---

## 8. Data Storage

### 8.1 PostgreSQL Schema Isolation

One PostgreSQL instance, separate schemas per service. Each service owns its schema exclusively. This is **schema-per-service**, not separate databases.

```
savestack (PostgreSQL database)
│
├─ savestack_core schema
│  │
│  ├─ users
│  │  ├── id (PK)
│  │  ├── email
│  │  ├── password_hash
│  │  ├── created_at
│  │  └── is_admin
│  │
│  ├─ plugins
│  │  ├── id (PK)
│  │  ├── name
│  │  ├── description
│  │  ├── version
│  │  ├── category
│  │  ├── icon_url
│  │  ├── base_url
│  │  ├── settings_schema (JSONB)
│  │  ├── registered_at
│  │  └── last_heartbeat
│  │
│  └─ user_plugins
│     ├── id (PK)
│     ├── user_id (FK → users)
│     ├── plugin_id (FK → plugins)
│     ├── is_active
│     ├── settings (JSONB)
│     ├── activated_at
│     └── deactivated_at
│
├─ savestack_fuel schema
│  │
│  ├─ stations
│  │  ├── id (PK)
│  │  ├── name
│  │  ├── location (PostGIS geometry)
│  │  ├── brand
│  │  ├── open_hours (JSONB)
│  │  └── last_updated
│  │
│  ├─ prices
│  │  ├── id (PK)
│  │  ├── station_id (FK)
│  │  ├── fuel_type (enum: benzina, diesel, gpl)
│  │  ├── price
│  │  ├── date
│  │  └── source
│  │
│  ├─ price_history
│  │  ├── id (PK)
│  │  ├── station_id (FK)
│  │  ├── fuel_type
│  │  ├── price
│  │  ├── recorded_at
│  │  └── index on (station_id, recorded_at) for time-series queries
│  │
│  └─ user_settings (local cache)
│     ├── user_id (PK)
│     ├── car_consumption
│     ├── fuel_type
│     ├── preferred_brands
│     └── synced_at
│
├─ savestack_grocery schema
│  │
│  ├─ promotions
│  │  ├── id (PK)
│  │  ├── chain_id (FK to external)
│  │  ├── product_name
│  │  ├── original_price
│  │  ├── discount_price
│  │  ├── valid_from
│  │  ├── valid_to
│  │  ├── scrape_source
│  │  └── scraped_at
│  │
│  ├─ user_products (inventory)
│  │  ├── id (PK)
│  │  ├── user_id
│  │  ├── product_name
│  │  ├── average_life_days
│  │  ├── last_purchase_date
│  │  └── last_purchase_price
│  │
│  ├─ shopping_trips
│  │  ├── id (PK)
│  │  ├── user_id
│  │  ├── status (enum: planned, in_progress, completed)
│  │  ├── started_at
│  │  ├── completed_at
│  │  ├── total_planned_budget
│  │  └── total_spent
│  │
│  ├─ trip_items
│  │  ├── id (PK)
│  │  ├── trip_id (FK)
│  │  ├── product_name
│  │  ├── quantity
│  │  ├── planned_price
│  │  ├── actual_price
│  │  └── item_status (enum: planned, purchased, removed)
│  │
│  └─ user_settings (local cache)
│     ├── user_id (PK)
│     ├── shopping_frequency
│     ├── budget_per_trip
│     └── synced_at
│
└─ savestack_budget schema
   │
   ├─ goals
   │  ├── id (PK)
   │  ├── user_id
   │  ├── name
   │  ├── target_amount
   │  ├── deadline
   │  ├── created_at
   │  └── archived_at
   │
   ├─ snapshots (monthly imports from Actual Budget)
   │  ├── id (PK)
   │  ├── user_id
   │  ├── month
   │  ├── total_saved
   │  ├── savings_from_fuel
   │  ├── savings_from_grocery
   │  ├── other_savings
   │  ├── imported_at
   │  └── actual_budget_account_id
   │
   ├─ savings_log (time-series for charts)
   │  ├── id (PK)
   │  ├── user_id
   │  ├── amount
   │  ├── source (enum: fuel, grocery, manual)
   │  ├── recorded_at
   │  └── index on (user_id, recorded_at) for range queries
   │
   └─ user_settings (local cache)
      ├── user_id (PK)
      ├── actual_budget_api_key (encrypted)
      ├── account_mapping (JSONB)
      └── synced_at

KEY PRINCIPLE:
  • Core can read savestack_core.users (shared reference)
  • Services can read Core's user table (to validate user exists)
  • Services NEVER read each other's schemas
  • Each service is the sole writer to its own schema
  • Cross-schema communication happens via Kafka only
```

### 8.2 Redis Usage

```
┌──────────────────────────────────────────────────────────────┐
│ KEY PATTERNS AND THEIR PURPOSE                               │
├──────────────────────────────────────────────────────────────┤

plugin:{id}:status
  ├─ Pattern:  "plugin:fuel:status", "plugin:grocery:status"
  ├─ Value:    "UP" or "DOWN"
  ├─ TTL:      90 seconds (auto-expire on silence)
  ├─ Set by:   Core (Kafka consumer listening to plugins.heartbeat)
  ├─ Read by:  Core (when building plugin store response)
  └─ Purpose:  Live health indicator without polling services

fuel:prices:current
  ├─ Pattern:  Similar for each plugin
  ├─ Value:    JSON blob (prices for all stations today)
  ├─ TTL:      24 hours (refreshed by scheduled job)
  ├─ Set by:   Fuel service (daily CSV ingestion completes)
  ├─ Read by:  Fuel service (per-request, avoids DB query)
  └─ Purpose:  Reduce load on price history queries

grocery:promos:{chain}
  ├─ Pattern:  "grocery:promos:coop", "grocery:promos:carrefour"
  ├─ Value:    JSON array of promotions
  ├─ TTL:      7 days (refreshed on scrape)
  ├─ Set by:   Grocery service (after DoveConviene scrape)
  ├─ Read by:  Grocery service (match user products to promos)
  └─ Purpose:  Fast lookups without DB hit; automatic staleness window

user:{id}:recommendations
  ├─ Pattern:  "user:A:recommendations"
  ├─ Value:    Aggregated recommendations JSON
  ├─ TTL:      1 hour (stale recommendations acceptable)
  ├─ Set by:   Core (aggregation job runs every hour)
  ├─ Read by:  Flutter app (GET /api/dashboard/recommendations)
  └─ Purpose:  Batch compute recommendations when it's cheap

user:{id}:{pluginid}:settings
  ├─ Pattern:  "user:A:fuel:settings"
  ├─ Value:    Plugin settings JSON (carConsumption, fuelType, etc.)
  ├─ TTL:      No TTL (stays until explicitly updated)
  ├─ Set by:   Plugin service (on Kafka event from Core)
  ├─ Read by:  Plugin service (per-request, instant response)
  └─ Purpose:  Local cache; avoid DB read per API call

GENERAL PRINCIPLES:
  ├─ Redis is never source of truth (PostgreSQL is)
  ├─ Every Redis entry has a TTL or explicit update trigger
  ├─ No cleanup jobs; expiration is automatic
  ├─ Plugins can lose Redis data and still function (repopulate on next event)
  └─ Redis failure → Slight latency increase, no data loss
```

---

## 9. Kafka Topics

```
┌────────────────────────────────────────────────────────────────────────┐
│ TOPIC CATALOG                                                          │
├────────────────────────────────────────────────────────────────────────┤

core.discovery.ping
├─ Producer:   Core service
├─ Consumers:  All plugins (subscribed)
├─ Payload:    { timestamp: ISO8601, version: "1" }
├─ Frequency:  On startup + periodic rediscovery (admin trigger)
├─ Retention:  1 day (short; messages are fire-and-forget)
└─ Purpose:    Broadcast: "Plugins, please register yourselves"
   Example:    Core startup → publishes ping → all plugins hear
               Plugin registers → confirms availability in Core
               User admin panel: click "Rediscover" → ping published

core.plugin.settings_changed
├─ Producer:   Core service
├─ Consumers:  Specific plugin (subscribed to own topic)
├─ Payload:    {
│                userId: "A",
│                pluginId: "fuel",
│                action: "activated" | "deactivated" | "settings_updated",
│                settings: { ... },
│                timestamp: ISO8601
│              }
├─ Frequency:  When user activates/deactivates plugin or changes settings
├─ Retention:  7 days (plugins may be offline temporarily)
└─ Purpose:    Notify plugin of user configuration changes
   Example:    User activates Budget plugin → Core publishes event
               → Budget service initializes data for that user

plugins.heartbeat
├─ Producer:   All plugins (each sends independently)
├─ Consumers:  Core service (Kafka consumer group)
├─ Payload:    { pluginId: "fuel", timestamp: ISO8601 }
├─ Frequency:  Every 30 seconds per plugin
├─ Retention:  1 day
└─ Purpose:    Liveness signal; Core updates Redis on receipt
   Example:    Fuel service sends heartbeat every 30s
               → Core consumer reads → updates Redis key
               → On silence for 90s, Redis key expires → plugin marked DOWN

grocery.trip.completed
├─ Producer:   Grocery service
├─ Consumers:  Budget service, Core (for notifications)
├─ Payload:    {
│                userId: "A",
│                tripId: "trip123",
│                itemCount: 12,
│                plannedBudget: 50.00,
│                actualSpend: 42.50,
│                savings: 7.50,
│                timestamp: ISO8601
│              }
├─ Frequency:  When user marks shopping trip complete
├─ Retention:  30 days (for audit and recomputation)
└─ Purpose:    Cross-plugin: Budget service tracks savings
   Example:    User finishes shopping trip (in-app)
               → Grocery publishes "trip.completed"
               → Budget service consumes → logs savings amount
               → Core consumes → triggers notification "You saved €7.50!"

grocery.list.generated
├─ Producer:   Grocery service
├─ Consumers:  Core (for notification)
├─ Payload:    {
│                userId: "A",
│                listId: "list456",
│                items: [ { name: "milk", qty: 2, ... } ],
│                estimatedBudget: 40.00,
│                timestamp: ISO8601
│              }
├─ Frequency:  When shopping list is generated (on-demand or scheduled)
├─ Retention:  7 days
└─ Purpose:    Notify user via Core's notification engine
   Example:    Grocery generates list from user inventory
               → Publishes event → Core sends push notification
               → "Your shopping list is ready (€40 est.)"

fuel.alert.price_drop
├─ Producer:   Fuel service (price monitoring job)
├─ Consumers:  Core (for notification)
├─ Payload:    {
│                userId: "A",
│                stationId: "station789",
│                stationName: "Esso Milano",
│                fuelType: "diesel",
│                currentPrice: 1.52,
│                previousPrice: 1.67,
│                savingsPerLiter: 0.15,
│                timestamp: ISO8601
│              }
├─ Frequency:  When price drops > threshold for user's preferences
├─ Retention:  3 days
└─ Purpose:    Notify user via Core's notification engine
   Example:    Diesel drops to €1.52 at user's local station
               → Fuel publishes alert → Core sends notification
               → "Diesel down to €1.52 at Esso (save €15/tank!)"

budget.goal.milestone
├─ Producer:   Budget service (during snapshot import)
├─ Consumers:  Core (for notification)
├─ Payload:    {
│                userId: "A",
│                goalId: "goal123",
│                goalName: "House Fund",
│                percentComplete: 25.0,
│                amountSaved: 7500,
│                targetAmount: 30000,
│                timestamp: ISO8601
│              }
├─ Frequency:  When imported snapshot shows progress milestone (25%, 50%, etc.)
├─ Retention:  30 days (milestones are memorable)
└─ Purpose:    Celebrate progress; engage user via Core's notification engine
   Example:    Budget snapshot shows user hit 25% of house goal
               → Budget publishes milestone → Core sends notification
               → "🎉 You're 25% toward your house fund goal!"

scraper.flyer.updated
├─ Producer:   Scraper service (external to core four plugins)
├─ Consumers:  Grocery service (to refresh cache)
├─ Payload:    {
│                chainId: "coop",
│                itemsScraped: 342,
│                promotions: [ ... ],
│                scrapedAt: ISO8601,
│                version: "20260409"
│              }
├─ Frequency:  Weekly (or on-demand)
├─ Retention:  30 days
└─ Purpose:    Update promotional data from DoveConviene
   Example:    Scraper job runs Sunday morning
               → Publishes event with new promotions
               → Grocery service updates cache and DB
               → Users see new promos on Monday

┌────────────────────────────────────────────────────────────────────────┐
│ TOPIC DESIGN PRINCIPLES                                                │
├────────────────────────────────────────────────────────────────────────┤

Naming convention:
  ├─ {service}.{entity}.{action}
  ├─ Example: "grocery.trip.completed", "fuel.alert.price_drop"
  └─ Rationale: Clear ownership, easy to grep in monitoring

Payload format:
  ├─ Always include: userId, timestamp, source service
  ├─ Include context needed for consumer to act
  ├─ Avoid: Pointers to data; include the data itself
  └─ Rationale: Consumers are independent; no synchronous lookups

Retention:
  ├─ Short-term (1 day): Discovery, heartbeats (ephemeral)
  ├─ Medium-term (7 days): Settings changes (plugins may be offline)
  ├─ Long-term (30 days): Business events (audit trail)
  └─ Rationale: Kafka is not source of truth; use for eventual consistency

Consumer groups:
  ├─ Each consumer (service) has its own group
  ├─ Allows parallel processing per partition
  ├─ Allows offset management (replay on failure)
  └─ Rationale: Scale and resilience
```

---

## 10. Resilience & Failure Handling

### 10.1 Plugin Failure Scenarios

A complete matrix of what can go wrong and what happens:

| Scenario | What Breaks | User Impact | Recovery | Automatic? |
|---|---|---|---|---|
| **Plugin service crashes** | That plugin's features only | Features unavailable; other plugins work normally; app stays responsive | Service restarts; heartbeat resumes; Redis key refreshed | Yes (orchestrator, e.g., Kubernetes) |
| **Plugin responds slowly (>3s)** | That plugin's API call | Client times out; shows "Temporarily unavailable"; dashboard still responsive | Plugin recovers; client retries | Yes (client retry logic) |
| **Kafka broker goes down** | Discovery, heartbeats, cross-plugin events | No new plugins discovered; existing plugins still work; Core doesn't see heartbeats | Kafka recovers; plugins resume publishing | Yes (Kafka cluster failover) |
| **Core authentication service down** | User login and new JWT issuance | Existing users with valid JWT still work (app cached JWT); can't login new users | Core restarts; JPA recovers | Yes (orchestrator) |
| **Core goes down entirely** | JWT validation, plugin store updates, settings sync | App works with cached plugin list; can't login; can't change settings; existing plugins serve cached data | Core restarts; plugins re-register | Yes (orchestrator) |
| **PostgreSQL goes down** | All persistent data | Full platform outage | Database recovers; plugins may lose offline-sync data | Yes (DB cluster failover) |
| **Redis goes down** | Plugin health caching, price caches, recommendations cache | Plugin health temporarily unreliable; slowdown on price lookups; recommendations stale | Redis restarts; plugins repopulate cache | Yes (orchestrator) |
| **Settings sync not delivered** | User changes plugin config | Plugin doesn't receive new settings via Kafka; uses stale cached values | Kafka consumer retries; settings eventually applied | Yes (Kafka retry + offset management) |
| **Plugin receives heartbeat but crashes before publishing** | Liveness signal | Redis key eventually expires; marked DOWN after 90s silence | Plugin restarts; sends new heartbeat | Yes (orchestrator + TTL) |

### 10.2 Resilience Principles

The architecture is designed with these principles:

```
┌──────────────────────────────────────────────────────────────┐
│ PRINCIPLE 1: Statelessness Where Possible                    │
├──────────────────────────────────────────────────────────────┤
  JWT is self-contained and signed.
  Plugins validate JWT locally.
  No plugin depends on Core being available for auth.
  
  ✓ Benefit: Plugins operate independently.
  ✓ Benefit: Horizontal scaling doesn't require shared state.
  ✗ Trade-off: Can't revoke JWT immediately (handled via short TTL).

┌──────────────────────────────────────────────────────────────┐
│ PRINCIPLE 2: Client-Side Caching                             │
├──────────────────────────────────────────────────────────────┤
  Flutter app caches plugin catalog locally.
  App caches JWT until expiration.
  Recommendations cached on device.
  
  ✓ Benefit: App works when Core is briefly unavailable.
  ✓ Benefit: Reduced network calls.
  ✗ Trade-off: Data can be stale; user must refresh explicitly.

┌──────────────────────────────────────────────────────────────┐
│ PRINCIPLE 3: Automatic TTL-Based Expiration                  │
├──────────────────────────────────────────────────────────────┤
  Redis keys have TTL (plugin health: 90s, prices: 24h).
  No background cleanup jobs.
  Silence = auto-expiration.
  
  ✓ Benefit: No stale data; Redis handles cleanup.
  ✓ Benefit: No polling or health check loops.
  ✗ Trade-off: Data is eventually consistent, not immediate.

┌──────────────────────────────────────────────────────────────┐
│ PRINCIPLE 4: Event Sourcing (Kafka)                          │
├──────────────────────────────────────────────────────────────┤
  Settings changes published to Kafka.
  Plugins consume events asynchronously.
  No synchronous dependency on Core.
  
  ✓ Benefit: Plugins process events at their own pace.
  ✓ Benefit: If plugin is down, messages wait in Kafka.
  ✗ Trade-off: Eventual consistency; plugin sees changes after delay.

┌──────────────────────────────────────────────────────────────┐
│ PRINCIPLE 5: Fail Open, Not Closed                           │
├──────────────────────────────────────────────────────────────┤
  One plugin down doesn't affect others.
  Core down: existing users still work (with cached data).
  Kafka down: HTTP-based communication still works.
  
  ✓ Benefit: Partial failures are not cascading failures.
  ✓ Benefit: System degrades gracefully.
  ✗ Trade-off: Features may become unavailable incrementally.
```

### 10.3 Failure Recovery as a Runbook

#### Scenario: Fuel Service Crashes at 3 PM

```
TIME 3:00 PM
  Fuel service container crashes.
  Last heartbeat was at 2:59:50 PM.

TIME 3:00-3:01 PM (IMMEDIATE USER IMPACT)
  ├─ Existing Flutter users with active Fuel plugin
  │  └─ Next API call to Fuel service → connection refused
  │     ├─ Client catches error
  │     ├─ Shows: "Fuel recommendations temporarily unavailable"
  │     └─ Other plugins (Grocery, Budget) still fully functional
  │
  └─ Admin monitoring (Grafana)
     └─ Fuel service metrics: CPU 0%, requests 0, errors spiking

TIME 3:01:30 PM (INTERNAL)
  Core's Kafka consumer regularly checks Redis.
  Redis still has: plugin:fuel:status = "UP" (expires at 3:02:50)
  Core's plugin store query still returns: status = "UP"
  (because Redis key hasn't expired yet)

TIME 3:02:50 PM (DETECTION)
  Redis TTL expires: plugin:fuel:status removed.
  Next Core plugin store query sees: status = null (treated as DOWN).
  Admin dashboard updates: Fuel service shows 🔴 DOWN.

TIME 3:03:00 PM (USER SEES IT)
  Users refresh Plugin Store in app.
  Flutter → Core: GET /api/plugins/store
  Core responds: fuel.status = "DOWN"
  App updates UI: "Fuel Intelligence — Temporarily unavailable"
  (graceful degradation; other plugins still work)

TIME 3:03-3:15 PM (OPERATIONS RESPONSE)
  Oncall engineer receives alert.
  (Kubernetes auto-restart may have already restarted the pod.)

CASE A: Auto-restart succeeded
  TIME 3:04 PM: Fuel service restarts, initializes, tries HTTP registration
  Fuel service: POST http://core:8080/api/plugins/register
    ├─ Core receives and updates plugins table.
    ├─ Core also receives heartbeat at 3:04:30.
    ├─ Core updates Redis: SET plugin:fuel:status "UP" EX 90
    └─ Response: 200 OK
  
  TIME 3:05 PM: Next user refresh
    └─ Fuel status = "UP" → users can call Fuel service again
    └─ Cached recommendations now available

CASE B: Auto-restart fails; manual intervention needed
  TIME 3:15 PM: Engineer receives escalation.
  Engineer: kubectl get pods → sees fuel-service-xyz is CrashLoopBackOff
  Engineer: kubectl logs fuel-service-xyz
    └─ Discovers: database connection string is wrong (environment variable typo)
  
  Engineer: Updates ConfigMap with correct database connection.
  Kubernetes: Redeploys pod.
  Fuel service: Starts successfully.
  
  TIME 3:16 PM: Fuel sends registration + heartbeat.
  Redis updated: plugin:fuel:status = "UP".
  Users see Fuel is back.

RECOVERY METRICS:
  └─ Detection latency: ~90 seconds (Redis TTL)
  └─ User-visible downtime: ~2-3 minutes (until auto-restart or manual fix)
  └─ Data loss: None (fuel price history and user data intact in Fuel DB)
  └─ Impact: Fuel plugin features unavailable; all other features work
```

#### Scenario: Core Service Becomes Unresponsive at 6 PM

```
TIME 6:00 PM
  Core service enters a hang (database connection pool exhausted).
  HTTP requests to Core timeout after 30s.
  Kafka producer on Core is blocked.

TIME 6:00-6:02 PM (USER IMPACT)
  ├─ Users trying to login
  │  └─ POST /auth/login → hangs → timeout → "Connection timeout"
  │     └─ Users cannot get new JWT or plugin catalog
  │
  ├─ Users already logged in with valid JWT
  │  └─ Calls to Fuel/Grocery/Budget plugins work normally
  │     (plugins validate JWT independently; no Core call)
  │  └─ Settings dashboard unavailable (calls Core)
  │
  └─ Plugins trying to publish heartbeat
     └─ Kafka producer times out; retries queued

TIME 6:02 PM (CACHED DATA RESCUE)
  Flutter users with cached data:
  └─ App displays cached plugin list (from previous session)
  └─ Cached recommendations from last hour
  └─ Users can still use active plugins
  └─ Can't activate new plugins or change settings
  └─ App shows: "Sync unavailable — using cached data"

TIME 6:05 PM (MONITORING)
  Prometheus alert: "Core service response time > 30s"
  Grafana dashboard shows:
  ├─ Core CPU high (connection pool contention)
  ├─ Database query latency spiking
  └─ Kubernetes liveness probe failing

TIME 6:05-6:10 PM (OPERATIONS RESPONSE)
  Kubernetes:
  └─ Liveness probe fails for 30s × 3 retries = 90s total
  └─ Automatically restarts Core pod

  Core restarts:
  ├─ Initializes fresh connection pool.
  ├─ Publishes discovery ping to Kafka.
  ├─ Plugins receive ping, re-register (idempotent).
  └─ Heartbeat consumer comes online.

TIME 6:11 PM (RECOVERY)
  New users can login again.
  Existing users unaffected (still using cached JWT).
  Plugin store refreshes to show accurate status.
  Settings syncs resume.

RECOVERY METRICS:
  └─ Detection latency: ~90 seconds (Kubernetes liveness probes)
  └─ User-visible downtime: ~11 minutes for new logins; cached users unaffected
  └─ Data loss: None (authentication attempts queued; no data lost)
  └─ Impact: Can't login or change settings; active features work normally
```

#### Scenario: PostgreSQL Replica Falls Out of Sync

```
CONTEXT: SaveStack uses PostgreSQL with read replicas.
Core reads from read replica. Writes go to primary.
Fuel, Grocery, Budget read their own schema from replica.

TIME 8:30 AM
  Network latency spike between primary and replica.
  Replica replication lag increases to 5 minutes.

TIME 8:30-8:35 AM (CASCADING IMPACT)
  ├─ Plugin store query reads stale plugin status from replica.
  │  └─ Newly registered plugins don't appear for 5 min.
  │
  ├─ Settings changes are written to primary but read from replica.
  │  └─ User sees: "Settings saved" (from primary write ✓)
  │  └─ Plugin receives Kafka event (async, OK ✓)
  │  └─ But if plugin queries settings from replica, sees stale value (5 min lag)
  │
  └─ User activation writes succeed, but plugin doesn't see activation.

TIME 8:35 AM (DETECTION)
  Monitoring alert: "Replication lag > 60s"
  DBA receives page.

TIME 8:35-8:40 AM (RECOVERY)
  Option A: Wait for network recovery
  └─ Replication catches up automatically.
  
  Option B: Force re-sync (manual)
  └─ DBA promotes replica, re-syncs with primary.
  └─ Brief window of potential data loss risk.

TIME 8:40 AM (CORRECTIVE ACTION)
  Replication lag returns to <1s.
  Next plugin store query sees up-to-date status.
  Settings changes flow through quickly.
  System normalizes.

RECOVERY METRICS:
  └─ Detection latency: ~60-120 seconds (monitoring alert)
  └─ User-visible impact: Stale data for 5 minutes; eventual consistency OK
  └─ Data loss: None (primary is always correct source of truth)
  └─ Mitigation: Use read-write splitting; critical writes always hit primary
```

---

## 11. Authentication

### 11.1 JWT-Based Stateless Auth

SaveStack uses **stateless JWT authentication**. No sessions, no token validation calls to Core after issuance.

```
┌──────────────────────────────────────────────────────────────┐
│ JWT TOKEN STRUCTURE                                          │
├──────────────────────────────────────────────────────────────┤

Header:
{
  "alg": "HS256",
  "typ": "JWT"
}

Payload (claims):
{
  "sub": "user:A",           ← subject (user ID)
  "email": "user@example.com",
  "roles": ["user"],
  "iat": 1712700600,         ← issued at (unix timestamp)
  "exp": 1712704200,         ← expiration (1 hour from issue)
  "iss": "savestack-core",   ← issuer
  "aud": ["fuel", "grocery", "budget"]  ← intended audience (all plugins)
}

Signature:
  HS256(
    base64UrlEncode(header) + "." + base64UrlEncode(payload),
    "shared-secret-key-stored-in-env-var"
  )

Complete token:
  eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.
  eyJzdWIiOiJ1c2VyOkEiLCJlbWFpbCI6InVzZXJAZXhhbXBsZS5jb20iLCJyb2xlcyI6WyJ1c2VyIl0sImlhdCI6MTcxMjcwMDYwMCwiZXhwIjoxNzEyNzA0MjAwLCJpc3MiOiJzYXZlc3RhY2stY29yZSIsImF1ZCI6WyJmdWVsIiwiZ3JvY2VyeSIsImJ1ZGdldCJdfQ.
  abcdef123456...
```

### 11.2 How Services Validate Tokens

Each plugin can validate JWT independently. No round-trip to Core needed.

```
┌──────────────────────────────────────────────────────────────┐
│ PLUGIN TOKEN VALIDATION FLOW                                 │
├──────────────────────────────────────────────────────────────┤

1. Client sends request to plugin:
   GET http://fuel-service:8082/api/recommendations
   Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

2. Fuel service middleware intercepts:
   ├─ Extracts token from Authorization header.
   ├─ Decodes JWT (no signature verification yet).
   ├─ Reads claims: sub=user:A, exp=1712704200, aud=[..., fuel, ...]

3. Fuel service validates claims:
   ├─ Check: current_time < exp ✓ (token not expired)
   ├─ Check: "fuel" in aud ✓ (this plugin is in the audience)
   ├─ Check: sub matches expected format ✓

4. Fuel service validates signature:
   ├─ Read shared secret from environment: JWT_SECRET_KEY
   ├─ Recompute HS256 signature:
   │   expected = HS256(header + payload, JWT_SECRET_KEY)
   ├─ Compare with token's signature.
   │   If match: ✓ Token is authentic and untampered.
   │   If no match: ✗ Token is forged or corrupt.

5a. Valid token:
    └─ Request proceeds; user_id = sub.
       Fuel service reads recommendations for user:A.

5b. Invalid token:
    └─ Return 401 Unauthorized.
       Client catches 401 → refreshes JWT from Core → retries.

PERFORMANCE:
  └─ No network call to Core (signature validation is local crypto).
  └─ Validation time: ~1-2ms per request.
  └─ No shared session state required.
```

> **Critical:** The JWT signing key must be shared across all services via secure means:
> - Environment variables (for development)
> - Secret manager (AWS Secrets Manager, Vault, etc. in production)
> - Never commit to code repository; never log it

---

## 12. Architecture Decision Records

This section documents key architectural decisions and their rationale. These decisions shape how the system behaves.

### 12.1 Why Kafka over RabbitMQ?

```
DECISION:  Use Apache Kafka (not RabbitMQ) for the event bus.

TRADE-OFFS EVALUATED:

RabbitMQ:
  ✓ Lighter weight; easier to get started
  ✓ Lower latency for single messages
  ✓ More mature AMQP ecosystem
  ✗ Message durability depends on queue persistence settings
  ✗ Less suitable for event sourcing / event replay
  ✗ Not ideal for Kafka's key feature: topic retention (long-lived topics)

Kafka:
  ✓ Built for high-throughput pub/sub with persistent topics
  ✓ Topics retain messages for days (built-in retention)
  ✓ Consumer groups can replay from specific offset
  ✓ Partitioning enables horizontal scaling of consumers
  ✓ Industry standard for event streaming (battle-tested)
  ✗ Heavier operational burden (broker cluster, ZooKeeper/Kraft)
  ✗ Slightly higher latency (batching for throughput)

DECISION RATIONALE:

1. **Eventual Consistency Model**
   SaveStack uses eventual consistency (Kafka best practice):
   - User changes plugin settings (sync write to PostgreSQL).
   - Core publishes event to Kafka (async).
   - Plugin consumes event and updates cache.
   - Small delay between setting change and plugin seeing it is acceptable.
   
   Kafka's retained topics fit this perfectly.

2. **Offline Resilience**
   If a plugin is offline when Core publishes a settings change:
   - RabbitMQ: Message is lost if no durable queue. Must re-send manually.
   - Kafka: Message stays in topic for 7 days. Plugin reads it on startup.
   
   This is critical for plugins that might restart or be temporarily offline.

3. **Auditability**
   Long topic retention enables:
   - Replay events for testing/debugging.
   - Analyze event history for analytics.
   - Recover a plugin's state from event log.
   
   Valuable for platform observability.

4. **Scale Characteristics**
   SaveStack will eventually have 10K+ active users with plugins.
   Kafka scales horizontally: add brokers and partitions.
   RabbitMQ gets harder to reason about at this scale.

DEPLOYMENT IMPLICATIONS:
  ├─ Kafka cluster (3+ brokers) required (operational overhead).
  ├─ But: Managed Kafka services (Confluent, AWS MSK) reduce overhead.
  └─ Justifies the cost for long-term platform needs.

ALTERNATIVES CONSIDERED (rejected):
  ├─ Redis Pub/Sub: Too ephemeral; no retention.
  ├─ AWS SQS: Proprietary; vendor lock-in.
  ├─ NATS: Simpler but lacks Kafka's retention and replay semantics.
```

### 12.2 Why Single PostgreSQL with Schemas?

```
DECISION:  One PostgreSQL instance with separate schemas per service
           (NOT separate databases or databases per service).

TRADE-OFFS EVALUATED:

Option A: One database (savestack), one schema for all
  ✗ Services can directly access each other's tables.
  ✗ No clear ownership; risk of cross-service queries.
  ✗ Schema evolution by one team breaks another.
  ✗ No isolation for resource constraints.

Option B: One database (savestack), one schema per service ← CHOSEN
  ✓ Clear ownership: each service owns its schema.
  ✓ Prevents accidental cross-schema queries (enforced by name).
  ✓ Each service can evolve its schema independently.
  ✓ Shared tables (e.g., users) explicitly in core schema.
  ✓ Single database simplifies backup/recovery.
  ✓ Single PostgreSQL instance = simpler ops (vs. multi-instance).
  ✗ Schema not a hard boundary (requires discipline).

Option C: Separate database per service (distributed databases)
  ✓ Hard boundary: database access control enforced by PostgreSQL.
  ✓ True service independence; can migrate to separate instances later.
  ✗ Distributed transactions become very hard.
  ✗ Backup/recovery complexity (N databases to manage).
  ✗ Inter-service queries require app logic, not SQL.
  ✗ Operational overhead: N connection pools, N replication setups.

Option D: Separate PostgreSQL server per service
  ✓ Maximum independence.
  ✓ Allows different versions per service.
  ✗ Massive operational burden (N clusters, N backup jobs, N monitoring).
  ✗ Network latency between services for any shared queries.
  ✗ Overkill for MVP; premature optimization.

DECISION RATIONALE:

1. **Schema-per-Service is Best Middle Ground**
   Schemas provide logical isolation without operational complexity.
   Clear ownership via naming (savestack_core.*, savestack_fuel.*, etc.).
   Can migrate to separate databases later if needed.

2. **Shared Reference Data**
   The `users` table is in core schema.
   Other services can SELECT from savestack_core.users to verify user exists.
   But they CANNOT INSERT/UPDATE users (enforced by design, not database ACL).
   Communication: services don't query each other; they use Kafka events.

3. **Backup & Disaster Recovery**
   One database = one backup job.
   One replication setup.
   Point-in-time recovery for entire platform from single backup.
   Simpler to reason about.

4. **Operational Simplicity**
   PostgreSQL scales well to 100GB+.
   One monitoring instance.
   One connection pool configuration.
   Single point of upgrade/maintenance.

5. **Future Migration Path**
   If Fuel plugin becomes huge (massive table growth):
   ```
   CURRENT:  All in one PostgreSQL, different schemas.
   FUTURE:   Move savestack_fuel to separate PostgreSQL cluster.
   MIGRATION:
     1. Set up separate Fuel PostgreSQL.
     2. Create foreign-data-wrapper to core schema for user reference.
     3. Or: eliminate reference; call Core API for user lookups.
   ```
   The schema separation makes this migration much cleaner.

DATA CONSISTENCY ACROSS SERVICES:
  Challenge: User A activates Fuel plugin. Fuel needs to initialize data.
  Solution:
    1. Core writes: user_plugins.user_id=A, plugin_id="fuel", is_active=true
    2. Core publishes: Kafka event "plugin.activated"
    3. Fuel consumes event, queries savestack_core.users to get user A's email.
    4. Fuel initializes its local fuel.user_settings table.
    Result: Eventual consistency; Fuel service is independent.
```

### 12.3 Why Shared JWT Signing Key?

```
DECISION:  All services share one JWT signing key.
           Plugins validate tokens locally using this shared key.

TRADE-OFFS EVALUATED:

Option A: Shared key (all services sign/validate with same key) ← CHOSEN
  ✓ Plugins validate JWT locally; no call to Core.
  ✓ Stateless; no session store needed.
  ✓ Plugins can operate offline.
  ✗ Key compromise affects all services.
  ✗ Can't revoke a single token immediately (workaround: short TTL).

Option B: Core as token validator (plugins call Core to validate)
  ✓ Revocation is instant (Core can blacklist tokens).
  ✓ Centralized control; easy to add custom logic.
  ✗ Core becomes critical path for every plugin request.
  ✗ Core must be available for plugins to serve requests.
  ✗ Network latency added to every request (1-2ms minimum).
  ✗ Doesn't scale well (Core bottleneck).

Option C: Symmetric key per plugin (plugin signs its own sub-tokens)
  ✓ Service independence; can use custom claims.
  ✗ Extreme complexity; each plugin manages its own token format.
  ✗ Client integration nightmare (different auth per plugin).

DECISION RATIONALE:

1. **Client Simplicity**
   Client gets ONE JWT from Core.
   Client uses SAME JWT for all plugins.
   Plugins validate independently.
   This is the simplest contract for the client.

2. **Plugin Independence**
   Fuel plugin can serve requests even if Core is down (as long as JWT is valid).
   Plugin doesn't depend on Core's availability.
   Aligns with microservice principle: loose coupling.

3. **Performance**
   Signature validation is local crypto (~1-2ms).
   No network round-trip to Core.
   Every plugin request doesn't incur validation latency.

4. **Revocation Workaround**
   Can't revoke a single token immediately.
   Mitigated by: short JWT TTL (1 hour).
   If user is compromised, token expires in 1 hour anyway.
   For admin/security teams: rotate signing key (all tokens invalidated immediately).

5. **Key Security**
   Shared key is stored in:
   - Environment variables (dev).
   - Secret manager (production: Kubernetes Secrets, AWS Secrets Manager, etc.).
   - Never in code repository.
   - All services pull it at startup (no sharing in logs or configs).

OPERATIONAL PROCESS:
  If key compromise suspected:
    1. Generate new key.
    2. Update secret manager.
    3. Restart all services (Core and plugins).
    4. All existing tokens invalid (signed with old key).
    5. Users re-login, get new tokens with new key.
    6. Recovery time: ~5 minutes (rollout time).
```

### 12.4 Why Client-to-Plugin Direct Communication?

```
DECISION:  Client calls plugins directly (not via Core gateway).
           Core only issues JWT and plugin catalog; then gets out of the way.

TRADE-OFFS EVALUATED:

Option A: Direct client-to-plugin (with JWT) ← CHOSEN
  ✓ Core is not in the request path for business data.
  ✓ Plugins don't need to go through Core; parallel, independent requests.
  ✓ Horizontal scaling: add plugin instances without Core knowing.
  ✓ Latency: Direct path (no extra hop).
  ✓ Each plugin can scale independently.
  ✗ Client must handle plugin discovery and failover.
  ✗ Each plugin exposes its own API (no unified gateway).

Option B: Core as API Gateway (all plugin calls routed through Core)
  ✓ Unified API endpoint.
  ✓ Core can apply rate limiting, logging, versioning.
  ✓ Easier to change plugin URLs (client doesn't know them).
  ✗ Core becomes critical path for all requests.
  ✗ Bottleneck: Core must handle 100% of traffic.
  ✗ If Core goes down, no plugin features work.
  ✗ Network latency doubled (client → Core → plugin).
  ✗ Cascading failures: one slow plugin slows Core, affecting all other plugins.

DECISION RATIONALE:

1. **Architecture Principle: Thin Core**
   SaveStack core is intentionally thin.
   It doesn't route business requests; it only handles metadata.
   This prevents Core from becoming a bottleneck.

2. **Resilience**
   If Core is slow or down:
   - With Option A: Plugins still work; client uses cached plugin list.
   - With Option B: Entire system is down.
   
   Graceful degradation is critical for a platform serving real users.

3. **Scaling**
   Each plugin scales independently.
   Fuel plugin gets hammered? Spin up more instances. Core doesn't care.
   With Option B: scaling Fuel would require scaling Core, too (wasteful).

4. **Client Caching**
   Plugin catalog is cached locally in Flutter.
   Client knows plugin URLs and health status.
   Client can detect plugin is down and show appropriate message.
   With Option B: client wouldn't need to know (but lose resilience).

5. **Plugin Tech Stack Freedom**
   Each plugin can use any language/framework.
   No need to match Core's REST conventions.
   With Option B: Core would impose a gateway API contract.

IMPLEMENTATION DETAILS:

Plugin catalog response (from Core):
{
  "plugins": [
    {
      "id": "fuel",
      "baseUrl": "http://fuel-service:8082",  ← client stores this
      "status": "UP",
      ...
    }
  ]
}

Client routing logic:
  For each request:
    1. Check cached baseUrl for plugin.
    2. Check cached status (UP/DOWN).
    3. If DOWN: show "temporarily unavailable"; don't call.
    4. If UP: make request directly to baseUrl.
    5. If connection error: refresh plugin catalog; retry.
    6. If 401 (JWT expired): refresh JWT from Core; retry.

TRADE-OFFS IN CLIENT:
  ├─ Pro: Faster, more resilient.
  ├─ Con: Requires smarter error handling in client.
  ├─ Con: Must cache plugin catalog and handle cache invalidation.
  └─ Acceptable for educated frontend team.
```

### 12.5 Why Redis TTL Instead of Cleanup Jobs?

```
DECISION:  Use Redis TTL (time-to-live) for automatic key expiration.
           Do NOT run background cleanup jobs to remove stale keys.

TRADE-OFFS EVALUATED:

Option A: Redis TTL (auto-expiration) ← CHOSEN
  ✓ No background cleanup job needed.
  ✓ Stale data removed automatically.
  ✓ No race conditions between cleanup and updates.
  ✓ Simple; fewer moving parts.
  ✓ Scales well (Redis handles cleanup internally).
  ✗ Expiration is not immediate (slight staleness window).
  ✗ Requires setting TTL on every write (discipline).

Option B: Background cleanup job (e.g., every minute)
  ✓ Immediate removal of stale data.
  ✓ Fine-grained control over cleanup logic.
  ✗ New failure point (if cleanup job breaks, stale data accumulates).
  ✗ Adds operational complexity (another service to monitor).
  ✗ Race conditions: cleanup might remove a key that was just updated.
  ✗ Wasted resources: scanning for stale keys (expensive query).

DECISION RATIONALE:

1. **Operational Simplicity**
   No cleanup job = less to maintain.
   Redis is responsible for TTL; it's designed for this.
   We trust Redis's implementation over hand-rolled cleanup.

2. **Correctness**
   Example: Plugin heartbeat arrives at t=0 (TTL=90).
   - Option A: Key expires at t=90. If next heartbeat at t=30, renewed to t=120. Correct.
   - Option B: Cleanup job runs at t=60, sees key should expire at t=90, leaves it.
             At t=95, cleanup runs again, now removes it.
             But what if another heartbeat arrived at t=89?
             Cleanup must check current time; becomes complex.

3. **Scalability**
   For thousands of plugins and millions of Redis keys:
   - Option A: Redis's internal expiration is O(1) per key (amortized).
   - Option B: Cleanup job must scan all keys, O(n), expensive for large Redis.

4. **Failure Modes**
   If cleanup job crashes:
   - Option A: TTL still works; no stale data accumulation.
   - Option B: Stale data accumulates; Redis grows unbounded.

IMPLEMENTATION EXAMPLE:

When Core receives a plugin heartbeat:
  Kafka consumer receives: { pluginId: "fuel", timestamp: "..." }
  Core's Redis client:
    SET plugin:fuel:status "UP" EX 90
    
  This sets the key and tells Redis to auto-expire in 90 seconds.
  Core doesn't track it; Redis handles cleanup.

When querying plugin status:
  GET plugin:fuel:status
  
  If key exists: status is UP.
  If key doesn't exist: either the key hasn't been set, or TTL expired.
  Treat non-existent keys as status = DOWN.

NO BACKGROUND JOB NEEDED.
```

---

## 13. Plugin Tech Stacks

Each plugin is a microservice with its own stack. The contract is REST + Kafka — any language works.

### 13.1 Stack Assignment

| Plugin | Technology | Core Reasoning |
|---|---|---|
| **Core** | Spring Boot (Java) | Auth, plugin registry, notifications — Spring Security, Spring Kafka, Spring Data JPA cover all needs natively. Team expertise. |
| **Fuel** | Spring Boot (Java) | CSV ingestion (scheduled), PostGIS for geo-spatial, time-series price history. Spring Boot excels at this: `@Scheduled`, Spring Data JPA, PostGIS extensions. Most complex plugin. |
| **Grocery** | FastAPI (Python) | Web scraping (BeautifulSoup), fuzzy string matching for product names (easier in Python). Future ML (receipt OCR) already in Python ecosystem. Natural DX for data-heavy operations. |
| **Budget** | Express (Node.js) | Actual Budget provides native `@actual-app/api` npm package. From Node, it's a direct import. Goal tracking is simple arithmetic. Lightest plugin, simplest logic, rightmost on spectrum. |

### 13.2 Universal Plugin Contract

Every plugin, regardless of language, implements:

```
┌──────────────────────────────────────────────────────────────┐
│ REST API (HTTP Endpoints)                                    │
├──────────────────────────────────────────────────────────────┤

GET /api/plugin/descriptor
  Returns plugin metadata.
  Response:
  {
    "id": "fuel",
    "name": "Fuel Intelligence",
    "version": "0.1.0",
    "settingsSchema": [
      {
        "key": "carConsumption",
        "type": "number",
        "label": "L/100km"
      }
    ]
  }
  Purpose: Used during plugin discovery handshake.

GET /api/plugin/health
  Returns health status.
  Response:
  {
    "status": "UP",
    "checks": {
      "database": "UP",
      "kafka": "UP"
    }
  }
  Purpose: Health checks; not used by Core (uses heartbeat instead).

GET /api/{feature}
  Plugin-specific endpoints.
  Examples:
  - Fuel: GET /api/recommendations, GET /api/prices
  - Grocery: GET /api/shopping-list, POST /api/trip
  - Budget: GET /api/goals, GET /api/projections
  Auth: Client sends JWT in Authorization header.
  Plugin validates JWT using shared signing key.

┌──────────────────────────────────────────────────────────────┐
│ Kafka Consumer (Async Events)                                │
├──────────────────────────────────────────────────────────────┤

Topic: core.discovery.ping
  Subscribes on startup.
  On receipt: Plugin calls Core's /api/plugins/register (HTTP).
  Purpose: Register itself after startup or on explicit discovery.

Topic: core.plugin.settings_changed
  Subscribes on startup.
  On receipt: {userId, pluginId, settings} → update local cache.
  Purpose: Sync user configuration changes from Core.

┌──────────────────────────────────────────────────────────────┐
│ Kafka Producer (Events)                                      │
├──────────────────────────────────────────────────────────────┤

Topic: plugins.heartbeat
  Every 30 seconds: { pluginId, timestamp }
  Purpose: Liveness signal; Core updates Redis.

Topic: {plugin}.{entity}.{action}
  Examples:
    - grocery.trip.completed
    - fuel.alert.price_drop
    - budget.goal.milestone
  Consumers: Core (for notifications), other plugins (for coordination).
  Purpose: Broadcast business events.

┌──────────────────────────────────────────────────────────────┐
│ Authentication (JWT Validation)                              │
├──────────────────────────────────────────────────────────────┤

Shared JWT signing key:
  Environment variable: JWT_SECRET_KEY
  All plugins pull this at startup (from secret manager or env).

On every request:
  1. Extract JWT from Authorization: Bearer <token>
  2. Decode JWT (no verification yet).
  3. Validate claims: expiration, audience.
  4. Verify signature using shared key.
  5. If valid: serve request with user_id from token.
  6. If invalid: return 401 Unauthorized.

No call to Core for validation.
```

---

## 14. Frontend Architecture

Three web frameworks, each in its natural sweet spot. Plus Flutter for mobile and native integrations.

### 14.1 Frontend Assignment

| Frontend | Framework | Purpose | Scope | Auth |
|---|---|---|---|---|
| **Public website** | Next.js | Acquisition funnel; SEO for free content | Landing, fuel explorer, blog | None (public) |
| **User dashboard** | Angular | Full user experience; all plugin features | Rich UI, complex interactions | JWT |
| **Admin panel** | Nuxt.js | Platform monitoring, plugin health | Health checks, configuration | JWT (admin role) |
| **Mobile app** | Flutter | Primary interface; notifications; camera | All features, responsive | JWT |
| **Car integration** | CarPlay / Android Auto | Fuel while driving | Simplified fuel UI | JWT |
| **Physical display** | ESP32 + MQTT | Desk/fridge widget | Goal progress, daily tips | Push (no auth) |

### 14.2 Routing Topology

```
┌─────────────────────────────────────────────────────────────────┐
│ DNS ROUTING TO FRONTENDS                                        │
├─────────────────────────────────────────────────────────────────┤

Browser/App makes request to:

  savestack.com                 ← Public website (Next.js)
    │
    ├─ Reverse proxy (Nginx/Traefik) routes by hostname
    │
    └─ Serves: Landing page, fuel explorer, blog, signup
       (SSR/SSG, SEO-optimized, unauthenticated)

  app.savestack.com             ← User dashboard (Angular)
    │
    ├─ Reverse proxy routes by hostname
    │
    └─ Serves: Plugin store, recommendations, all user features
       (SPA, requires JWT, cached locally)

  admin.savestack.com           ← Admin panel (Nuxt.js)
    │
    ├─ Reverse proxy routes by hostname
    │
    └─ Serves: System health, plugin management, configuration
       (SSR, requires admin JWT, operator-only)

┌─────────────────────────────────────────────────────────────────┐
│ BACKEND API ROUTING FROM FRONTENDS                              │
├─────────────────────────────────────────────────────────────────┘

Reverse Proxy (Nginx/Traefik) also routes API requests:

  http://api.savestack.com/core/*        ← Routes to Core (port 8080)
  http://api.savestack.com/fuel/*        ← Routes to Fuel (port 8082)
  http://api.savestack.com/grocery/*     ← Routes to Grocery (port 8083)
  http://api.savestack.com/budget/*      ← Routes to Budget (port 8084)

OR: More direct routing (plugins advertise their own URLs):

  Core responds with plugin catalog:
  {
    "plugins": [
      {
        "id": "fuel",
        "baseUrl": "http://fuel-service.savestack.com",  ← direct URL
        ...
      }
    ]
  }

  Frontend stores baseUrl and calls directly.

Both approaches work; second is simpler for plugins (no reverse proxy).

┌─────────────────────────────────────────────────────────────────┐
│ FRONTEND-TO-BACKEND API MATRIX                                  │
├─────────────────────────────────────────────────────────────────┐

Next.js (Public website)
  └─ Core:    /auth/login, /auth/callback (redirects to dashboard)
  └─ Fuel:    /api/prices (public, unauthenticated)
              (Next.js backend makes request; client never touches Fuel)

Angular (User dashboard)
  └─ Core:    /auth/login, /api/plugins/store, /api/user/settings
  └─ Fuel:    /api/recommendations, /api/prices, /api/map
  └─ Grocery: /api/shopping-list, /api/trips, /api/products
  └─ Budget:  /api/goals, /api/projections, /api/savings

Nuxt.js (Admin panel)
  └─ Core:    /admin/system-health, /admin/plugins, /admin/config
              (admin-protected endpoints)

Flutter (Mobile app)
  └─ Core:    /auth/login, /api/plugins/store, /api/user/settings
  └─ Fuel:    /api/recommendations, /api/prices, /api/map
  └─ Grocery: /api/shopping-list, /api/trips, /api/products
  └─ Budget:  /api/goals, /api/projections, /api/savings

CarPlay / Android Auto (Native car integration)
  └─ Fuel:    /api/fuel-suggestion (simplified endpoint)

ESP32 (Physical widget)
  └─ Core:    MQTT subscriptions (one-way push)
              /mqtt/goal-progress, /mqtt/daily-tip
```

### 14.3 Next.js — Public Website

Purpose: SEO and acquisition funnel.

**Pages:**

```
/                          ← Landing page (SSG)
  Static HTML: hero, features, testimonials, CTA: "Sign up free"

/fuel                      ← Fuel explorer landing (SSG)
  Static HTML: intro, featured cities

/fuel/[city]               ← City fuel prices (SSR)
  Dynamic: GET /api/fuel/search?city=milano
  Returns current prices for all stations in that city.
  Rendered on-demand → HTML to browser (SEO: "Benzina a Milano")

/fuel/station/[id]         ← Individual station (SSR)
  Dynamic: Specific station's price history chart, reviews.
  Rendered on-demand → HTML to browser.

/about                     ← About page (SSG)
  Static HTML: mission, team.

/plugins                   ← Plugin showcase (SSG)
  Static HTML: "SaveStack is extensible; new plugins coming."

/blog                      ← Blog/MDX (SSG + dynamic)
  Articles on fuel savings, budgeting tips, grocery hacks.
  Pre-built SSG where possible; dynamic routes for user-contributed content.
```

**API Routes (Next.js backend):**

```
/api/fuel/search?city=X&type=benzina&radius=5
  ├─ Next.js backend receives request.
  ├─ Checks Redis: is fuel:prices:current cached?
  ├─ Cache hit: return cached prices.
  ├─ Cache miss:
  │   └─ GET http://fuel-service:8082/api/prices?city=milano
  │   └─ Cache result in Redis (15 min TTL).
  │   └─ Return to Next.js → template renders HTML.

/api/fuel/cities
  ├─ Static list of available cities (cached in Fuel service).
  └─ Returns immediately.

/api/fuel/trends?city=X
  ├─ Price history data for charts.
  └─ GET http://fuel-service:8082/api/price-trends?city=X
```

**Data Flow:**

```
Browser requests: https://savestack.com/fuel/milano

1. Browser sends HTTP request to next server.
2. Next.js SSR renderer activates.
3. Renders page layout, fetches data via internal /api/fuel/search.
4. Next.js backend queries Fuel service (or cache).
5. HTML is returned to browser (fully rendered).
6. Browser displays page → Google crawls rendered HTML.
7. React hydrates; client-side interactivity becomes available.
8. User can interact: filter, sort, view chart, sign up.
```

**Why Next.js for this?**

- **SEO:** Pre-rendered HTML is indexable by Google.
- **Performance:** Static pages (SSG) load instantly.
- **Caching:** Built-in ISR (Incremental Static Regeneration) for semi-dynamic content.
- **User funnel:** Attracts free users → drives signup → redirects to dashboard.

### 14.4 Angular — User Dashboard

Purpose: Full user experience; all plugin interactions; complex UI.

**Pages:**

```
/dashboard
  Overview: today's recommendations from all active plugins.
  Quick stats: money saved today, goals progress.

/dashboard/plugins
  Plugin Store: browse available plugins, status (UP/DOWN).
  Activate/deactivate plugins.
  Configure each plugin's settings (dynamic form from settingsSchema).

/dashboard/fuel
  Map: nearby fuel stations, prices, grades.
  Route setup: enter start/end points, get route-aware recommendations.
  Price trends: 30-day chart for selected stations.
  Savings history: how much saved with SaveStack.

/dashboard/grocery
  Product inventory: items you buy regularly, average life/cost.
  Shopping lists: generated from inventory + promos.
  Shopping trip (in-store mode): check items off, see real-time savings.
  Trip history: past trips, savings per trip.

/dashboard/grocery/trip
  Active shopping trip (fullscreen):
    ├─ Running total budget spent.
    ├─ Items to buy (checkable list).
    ├─ Real-time item price lookups (barcode scan or search).
    ├─ Suggested cheaper alternatives based on promos.
    ├─ "Finish trip" button → sends completion event.

/dashboard/budget
  Goals: create, edit, track savings goals.
  Projections: "At this rate, you'll save €X by deadline."
  Savings pulse: aggregate savings from all sources (fuel + grocery).
  Charts: savings over time (stacked bar chart).

/dashboard/settings
  Global preferences: notifications, language, theme.
  Connected accounts: Actual Budget sync.
  Privacy & data: what data SaveStack stores.
```

**Characteristics:**

- Complex interactive UI: drag-and-drop shopping list, interactive fuel map, dynamic charts.
- Real-time updates during shopping trips (WebSocket or polling).
- Plugin settings forms are dynamic (rendered from each plugin's `settingsSchema`).
- Offline support: caches plugin catalog and recommendations locally.
- Talks to Core (auth, plugins) and each active plugin service (business data).

**Data Flow:**

```
User logs in:
  1. POST /auth/login (to Core) → get JWT + plugin catalog.
  2. Angular stores JWT in memory (not localStorage, for security).
  3. Angular caches plugin catalog locally.

User browses fuel recommendations:
  1. GET /api/recommendations (to Fuel service) + JWT.
  2. Fuel service validates JWT, returns recommendations.
  3. Angular displays on map.

User activates Budget plugin:
  1. POST /api/user/plugins/budget/activate (to Core) + JWT.
  2. Core stores activation, publishes Kafka event.
  3. Budget service consumes event, initializes data.
  4. Angular refreshes plugin store view.
```

### 14.5 Nuxt.js — Admin Panel

Purpose: Platform operator's monitoring and management tool.

**Pages:**

```
/admin
  System overview: service health, user count, event throughput.
  Alerts: critical issues, slow services.

/admin/plugins
  Plugin health dashboard:
    ├─ Each plugin: status (UP/DOWN), last heartbeat.
    ├─ Version running, deployment time.
    ├─ Traffic: requests per second, errors.

/admin/plugins/discover
  "Rediscover Plugins" button (triggers Kafka discovery ping).
  Manual trigger for plugin discovery (normally automatic).

/admin/users
  User list with filters/search.
  Per-user: plugins activated, usage statistics.
  Ability to disable user account (emergency).

/admin/events
  Kafka event log viewer (recent events).
  Filter by topic, timestamp, plugin.
  For debugging: see what events are flowing.

/admin/scrapers
  Scraper status (for Grocery plugin):
    ├─ Last run time, items scraped, errors.
    ├─ Next run scheduled time.
    └─ Ability to manually trigger scrape.

/admin/config
  System configuration: JWT TTL, heartbeat interval, caching TTLs.
  Feature flags: enable/disable plugins, maintenance mode.
```

**Characteristics:**

- Lighter than Angular (less complex UI).
- SSR for fresh data on each load (admin wants latest status).
- Vue's simplicity fits the smaller scope.
- Talks to Core only (admin-protected endpoints).
- Requires admin role in JWT.

---

## 15. Full Tech Stack Summary

### 15.1 Backend Services

| Service | Technology | Primary Responsibility |
|---|---|---|
| **Core** | Spring Boot 4.0 (Java 21) | Authentication (JWT), plugin registry, notifications, settings sync, recommendation aggregation |
| **Fuel** | Spring Boot 4.0 (Java 21) | MIMIT CSV ingestion (scheduled), PostGIS geo-spatial queries, price history time-series, route optimization, fuel recommendations |
| **Grocery** | FastAPI (Python 3.11) | DoveConviene HTML scraping (weekly), product name fuzzy matching, shopping list generation, trip optimization, inventory tracking |
| **Budget** | Express (Node.js 18) | Actual Budget API integration (native `@actual-app/api`), savings goal tracking, monthly snapshot imports, goal milestone detection |

### 15.2 Frontend Applications

| Application | Technology | User Base | Primary Responsibility |
|---|---|---|---|
| **Public website** | Next.js 14 (React) | Unauthenticated users | Landing page, SEO-optimized fuel price explorer, blog, signup funnel |
| **User dashboard** | Angular 18 | Authenticated users | Plugin management, recommendations, interactive fuel map, shopping lists, budget tracking |
| **Admin panel** | Nuxt.js 3 (Vue) | Platform operators | System health monitoring, plugin management, user administration, event debugging |
| **Mobile app** | Flutter 3.19 | Mobile users | Primary interface, push notifications, in-store shopping mode, camera integration |
| **Car integration** | CarPlay / Android Auto (native) | Drivers | Simplified fuel suggestions, turn-by-turn integration |
| **Physical display** | ESP32 + Arduino (C++) | Home users | MQTT-subscribed widget showing daily tips, goal progress bar |

### 15.3 Infrastructure

| Component | Technology | Responsibility |
|---|---|---|
| **Event streaming** | Apache Kafka (3+ brokers) | Discovery pings, heartbeats, cross-plugin events, settings sync, notifications |
| **Primary database** | PostgreSQL 16 (+ PostGIS extension) | All persistent data; one schema per service for logical isolation |
| **Cache + session store** | Redis 7 (cluster mode) | Plugin health (TTL-based), price caches, recommendation caches, user settings |
| **MQTT broker** | Mosquitto 2.0 | One-way push to ESP32 physical devices (goal progress, daily tips) |
| **Reverse proxy** | Nginx or Traefik | Route subdomains to frontend apps; load balance backend services |
| **Containerization** | Docker (all services) | Consistent deployment; local dev via docker-compose |
| **Orchestration** | Kubernetes (production) | Pod scheduling, service discovery, auto-restart, rollouts |
| **Observability** | Prometheus + Grafana | Service metrics, health checks, alert thresholds; Loki for logs |
| **Secrets management** | Kubernetes Secrets (dev/staging); Vault or AWS Secrets Manager (production) | JWT signing key, database credentials, API keys |

---

## 16. Core Service Scope

### 16.1 What Core DOES

- **Authenticate users** — `/auth/login`, validates credentials, issues JWT
- **Issue self-contained JWT tokens** — signed with shared key; plugins validate independently
- **Discover and register plugins** — publish discovery ping, receive registrations via HTTP
- **Track plugin health** — consume heartbeats from Kafka, update Redis status
- **Store user-plugin relationships** — who activated which plugins (PostgreSQL)
- **Store user-plugin settings** — source of truth; syncs to plugins via Kafka
- **Aggregate recommendations** — batch job that calls all active plugins, combines results
- **Send notifications** — consumes Kafka events from plugins; triggers push notifications, emails
- **Serve plugin catalog** — returns list of available plugins with live status for UI

### 16.2 What Core DOES NOT

- Know what a fuel price is
- Know what a shopping list looks like
- Know what a budget goal is
- Route plugin API calls (client talks directly to plugins)
- Depend on any specific plugin being available
- Act as reverse proxy for plugin requests
- Store business data from plugins (only settings and metadata)
- Validate every plugin request (plugins validate JWT locally)
- Call other services synchronously (uses Kafka for eventual consistency)

> **The principle:** Core is thin, agnostic, and non-blocking. It's the platform foundation, not a mediator.

---

## Conclusion

SaveStack's architecture achieves the goals of a **scalable, plugin-based platform** through:

1. **Microservices separation** — Core handles platform concerns; plugins are independent services.
2. **Asynchronous communication** — Kafka enables loose coupling and resilience.
3. **Stateless authentication** — JWT allows plugins to validate requests without Core.
4. **Schema-per-service isolation** — Clear ownership while maintaining single database for operations simplicity.
5. **Graceful degradation** — One plugin down doesn't affect others; Core down doesn't break existing sessions.
6. **Automation & self-healing** — Plugin discovery is automatic; health tracking uses TTL; Kubernetes handles restarts.

This design scales to support thousands of users, hundreds of API calls per second, and the addition of new plugins without core system changes. It trades simplicity for resilience, distributing complexity across the codebase in exchange for a robust, loosely-coupled platform.
