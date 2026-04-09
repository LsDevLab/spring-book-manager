# The Full Stack — Developer Black Box

## Exploration Summary

This document captures the research and design exploration for **The Full Stack**, a platform that passively records a developer's work life and uses AI to extract meaning, patterns, and connections — without any manual input.

---

## 1. Core Concept

A "black box" for developers. Like an airplane's flight recorder, it passively captures everything — what you coded, what you read, what broke, what you shipped — and lets you rewind, search, and discover patterns.

**The key principle: zero manual input.** The platform ingests data automatically from tools you already use. The user's only role is to review, correct, and explore — never to type things in.

### What makes it different from existing tools?

| Existing Tool | What it tracks | What it misses |
|---|---|---|
| WakaTime / ActivityWatch | Time per language/project | No *meaning* — why you spent that time, what you learned |
| GitHub Insights | Commit graphs, PR stats | No correlation with other activities (reading, debugging, meetings) |
| Obsidian / Notion | Whatever you manually write | Requires constant manual input — dies after week 3 |
| roadmap.sh | Static learning paths | No tracking of actual progress, no personalization |

**The Full Stack fills the gap**: it connects *what you coded* with *what you read*, *what broke*, *what meetings fragmented your day*, and *what you actually learned* — all inferred automatically.

---

## 2. Event Sources

### Tier 1: Easy (webhooks & APIs — configure once, stream forever)

| Source | Events Captured | How |
|---|---|---|
| **GitHub / GitLab** | Commits, PRs (opened/merged/reviewed), issues, code review comments, languages per repo | Webhook registration (~30 event types from GitHub) |
| **CI/CD** (GitHub Actions, Jenkins) | Build started/passed/failed, deploy events, test results, pipeline duration | Webhooks (GitHub Actions sends `workflow_run` events) |
| **Calendar** (Google / Outlook) | Meetings, focus blocks, 1:1s, standups | OAuth2 + periodic sync via Calendar API or push notifications |

### Tier 2: Medium (lightweight agent/plugin)

| Source | Events Captured | How |
|---|---|---|
| **IDE plugin** (WakaTime-compatible) | Active file, language, project, time spent, activity level | Reuse existing open-source WakaTime plugins for VS Code/IntelliJ — just point them at your backend URL. The WakaTime heartbeat protocol (`POST /api/v1/heartbeats`) is open and well-documented. **No custom plugin needed.** |
| **Browser extension** | Dev-relevant URLs visited (docs, Stack Overflow, tutorials), time on page | Small Chrome/Firefox extension (~200 lines JS) with configurable domain allowlist |

### Tier 3: Harder but high-value (more integration work)

| Source | Events Captured | How |
|---|---|---|
| **Jira / Linear / Notion** | Tickets assigned, status changes, sprint progress | Webhooks (Jira, Linear) or polling (Notion API) |
| **Slack / Discord** | Channels active in, message metadata (not content — privacy-first) | Bot with minimal permissions |
| **RSS / Atom feeds** | Blog posts, release notes, newsletters consumed | User adds feed URLs once, backend polls periodically |
| **Docker / K8s** | Container events, deployments, scaling, crashes | Kubernetes API watch streams or Prometheus alertmanager webhooks |

### Tier 4: Optional (IoT layer)

| Source | Events Captured | How |
|---|---|---|
| **ESP32 + MQTT** | Physical deep-work button, desk presence (PIR sensor), ambient noise/temp | ESP32 publishes to MQTT topic -> broker -> Kafka consumer -> event store |

### Input classification

- **Zero input forever**: GitHub, CI/CD, IDE, calendar, Jira — once connected, they stream indefinitely
- **One-time setup**: RSS feeds, browser extension allowlist, ESP32 pairing
- **Never required**: typing descriptions, tagging, journaling — that's the AI's job

---

## 3. AI Episode-Clustering Engine

The inference engine has **3 layers**, from simple to smart. Layer 1 alone produces useful results. Each layer builds on the previous.

### Layer 1: Temporal Sessionization (no AI needed)

**Rule:** If two consecutive events from the same user are < N minutes apart, they belong to the same session. A gap longer than N starts a new one.

- Research suggests **~60 minutes** is the optimal threshold for developer activity (vs. 30 min for web browsing)
- Can be made adaptive per user — if someone codes in long bursts, the threshold stretches

**Tech:** Kafka Streams or Spring Boot consumer with stateful window aggregation. Redis holds the "current open session" per user. On gap detection, the session is closed and flushed to PostgreSQL.

**Example output:**
```
Session 1: 09:01-09:45 (44 min) — 7 events
Session 2: 13:00-13:05 (5 min) — 2 events
```

### Layer 2: Heuristic Enrichment (rules + lightweight NLP, no LLM)

Tags each session with *what it was about* using rules and basic text analysis on available signals:

**Available signals per event:**
- Repo/project name (IDE heartbeats, GitHub webhooks)
- File paths (IDE heartbeats -> infer module/package)
- Languages (IDE heartbeats, GitHub file extensions)
- PR titles + commit messages (free-text gold from GitHub payloads)
- URLs visited (browser extension — domain + path)
- Jira/Linear ticket titles (webhook payload)
- CI/CD outcomes (pass/fail, pipeline name)

**Tagging rules (no ML needed):**
```
IF CI/CD failure + same-repo IDE activity       -> "debugging", "build-fix"
IF Stack Overflow/docs URLs + IDE activity       -> "learning", "research"
IF PR review + no IDE activity in same repo      -> "code-review"
IF only IDE heartbeats, same repo, many files    -> "feature-work" or "refactoring"
IF Jira ticket transition + IDE                  -> "ticket-work" + link ticket ID
```

**Topic extraction:** TF-IDF or keyword extraction (e.g. `rake-nltk`) on commit messages and PR titles within the session. No LLM needed.

**Tech:** Spring Boot rule engine (even just if/else or a small DSL). Python FastAPI sidecar for keyword extraction. Runs as a post-processing step on session close.

**Example output:**
```
Session 1: 09:01-09:45
  Project: payment-service
  Languages: Java
  Activity: debugging + research
  Topics: asyncio, semaphore, concurrency
  Sources: 2 articles read, 1 PR reviewed
```

### Layer 3: LLM Summarization & Connection (the magic layer)

Takes the tagged session and asks an LLM to:
1. **Generate a human-readable summary** (one sentence)
2. **Infer the developer's intent** (what were they trying to accomplish?)
3. **Identify knowledge gained** (what did they learn or practice?)
4. **Find connections to past sessions** (continuation? new direction? emerging interest?)

**Example LLM output for a session:**
```
Summary: "Fixed a race condition in the payment queue by introducing
          a semaphore, after researching async concurrency patterns."

Intent: Resolving payment timeouts under high load (PLAT-289)

Knowledge gained: async concurrency patterns, semaphore usage in Java,
                  race condition debugging

Connections: Continuation of payment-service performance work
             (sessions from Apr 3, 5, 7). Related to PLAT-289.
```

**Cost/practicality:**

| Approach | Pros | Cons |
|---|---|---|
| Claude/OpenAI API | Best quality, easiest to start | ~$0.01-0.05 per session. At 5-10 sessions/day = $1-15/month/user |
| Self-hosted LLM (Llama, Mistral) | Free after infra, fully private | Needs GPU or beefy CPU. Lower quality |
| **Hybrid (recommended)** | Layer 2 handles 80% of sessions, LLM only for complex/ambiguous ones | Best cost/quality tradeoff |

---

## 4. What the User Sees

Opening the app on a Monday morning (having typed nothing all week):

> **Last week: 23 episodes across 4 projects**
>
> **payment-service** (12.3 hrs) — Race condition debugging arc. Started with timeout investigation, evolved into async refactor. 4 articles read, 2 PRs merged. *Connected to PLAT-289.*
>
> **user-service** (3.1 hrs) — New endpoint for user preferences. Routine feature work.
>
> **infra** (2.4 hrs) — Kubernetes config for staging. Triggered by CI failures.
>
> **learning** (1.8 hrs) — Read 6 articles about observability and distributed tracing. *No code yet — emerging interest?*

---

## 5. Architecture

```
Raw Events                    Layer 1              Layer 2              Layer 3
-----------                  ---------            ---------            ---------
IDE heartbeat --+
GitHub push ----+
Browser URL ----+----> Kafka ----> Temporal   ----> Heuristic   ----> LLM
CI/CD event ----+                  Sessionizer      Tagger/Enricher   Summarizer
Jira webhook ---+                  (Spring Boot     (Spring Boot +    (FastAPI +
MQTT event -----+                   + Redis)         FastAPI)          Claude API)
                                      |                  |                 |
                                 "Session 1:        "Session 1:       "Fixed race
                                  09:01-09:45,       payment-service,  condition in
                                  7 events"          debugging,        payment queue
                                                     concurrency"      using semaphore
                                                                       pattern..."
                                                                           |
                                                                      PostgreSQL
                                                                     (episode store)
                                                                           |
                                                                      GraphQL API
                                                                           |
                                                          +----------------+----------------+
                                                          |                |                |
                                                       Next.js          Angular          Flutter
                                                      (timeline)        (graph)         (mobile)
```

## 6. Full Tech Stack Mapping

| Component | Technology | Role |
|---|---|---|
| Event ingestion | **Apache Kafka** | Central event bus — all sources publish here |
| Core API + webhook receivers | **Spring Boot** | Sessionizer, rule engine, REST/GraphQL endpoints |
| ML/NLP services | **FastAPI** | Keyword extraction, LLM orchestration, topic clustering |
| Primary database | **PostgreSQL** | Event store, episode store, knowledge graph, relationships |
| Cache | **Redis** | Active sessions, recent episodes, real-time activity feed |
| API layer | **GraphQL** | Flexible frontend queries for timeline, search, analytics |
| Timeline/Explorer UI | **Next.js** | Episode timeline, weekly digests, search |
| Analytics/Graph UI | **Angular** | Knowledge graph visualization, dependency map, deep analytics |
| Plugin Manager UI | **Nuxt.js** | Integration configuration, source management |
| Mobile | **Flutter** | "What did I do today" glanceable view, quick review |
| IoT | **ESP32 + MQTT** | Physical deep-work triggers, desk presence, environment data |
| Deployment | **Docker + Kubernetes** | Containerized microservices |
| Observability | **Prometheus + Grafana** | Platform metrics, event flow monitoring, system health |
| Testing | **JUnit, Pytest, Jest, Cypress** | Backend, ML services, frontend, E2E |

---

## 7. Market Gaps Identified (from research)

1. **No tool bridges "what I coded" with "what I learned"** — WakaTime tracks time, Obsidian tracks notes, nothing connects them
2. **Tool sprawl is real** — devs use 7.4 tools on average, lose 6-15 hrs/week to context switching. No single pane of glass exists
3. **All knowledge trackers require manual input** — and get abandoned. Zero-input is the differentiator
4. **AI episode clustering doesn't exist yet** — temporal sessionization is well-studied in academia, but nobody applies it to developer activity with LLM enrichment
5. **"Second brain" setups are DIY** — Obsidian + Claude Code is powerful but requires heavy manual configuration. A purpose-built platform could automate this entirely

---

## 8. Open Questions

- **Privacy model**: How much data is stored? Where? Self-hosted only, or cloud option with encryption?
- **Multi-user / team features**: Should this support teams (shared episodes, project-level views) or stay personal?
- **Onboarding flow**: What's the minimum viable set of integrations to show value on day one? (Probably: GitHub + IDE plugin)
- **Monetization** (if ever): Self-hosted free, cloud hosted paid? Plugin marketplace?
- **Naming**: "The Full Stack" works as a project name, but the product could have a sharper name (e.g., "DevBox", "BlackStack", "StackTrace")
