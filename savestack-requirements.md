# SaveStack — Technical Requirements & Design Decisions

This document specifies all functional requirements, architectural decisions, constraints, and design rationale for SaveStack, a plugin-based platform for optimizing recurring expenses.

---

## Table of Contents

1. [Product Vision & Strategy](#1-product-vision--strategy)
   - 1.1 [What SaveStack Is](#11-what-savestack-is)
   - 1.2 [Target User Profile](#12-target-user-profile)
   - 1.3 [Core Design Philosophy](#13-core-design-philosophy)

2. [Core Constraints & Design Principles](#2-core-constraints--design-principles)
   - 2.1 [Data Freedom: No Cost, No Lock-in](#21-data-freedom-no-cost-no-lock-in)
   - 2.2 [Minimal Manual Input](#22-minimal-manual-input)
   - 2.3 [Plugin-Based Architecture](#23-plugin-based-architecture)
   - 2.4 [Cloud-Hosted, Multi-Tenant](#24-cloud-hosted-multi-tenant)

3. [MVP Plugins](#3-mvp-plugins)
   - 3.1 [Fuel Plugin](#31-fuel-plugin)
   - 3.2 [Grocery Plugin](#32-grocery-plugin)
   - 3.3 [Budget Plugin](#33-budget-plugin)

4. [Platform Core](#4-platform-core)
   - 4.1 [Authentication & Authorization](#41-authentication--authorization)
   - 4.2 [Plugin Lifecycle Management](#42-plugin-lifecycle-management)
   - 4.3 [Notifications & Recommendation Aggregation](#43-notifications--recommendation-aggregation)

5. [Communication Model](#5-communication-model)
   - 5.1 [Protocol Rules](#51-protocol-rules)
   - 5.2 [Kafka Event Streams](#52-kafka-event-streams)

6. [Data Storage Architecture](#6-data-storage-architecture)
   - 6.1 [PostgreSQL Schema](#61-postgresql-schema)
   - 6.2 [Redis Cache Strategy](#62-redis-cache-strategy)

7. [Frontend Architecture](#7-frontend-architecture)
   - 7.1 [Next.js Public Website](#71-nextjs-public-website)
   - 7.2 [Angular User Dashboard](#72-angular-user-dashboard)
   - 7.3 [Nuxt.js Admin Panel](#73-nuxtjs-admin-panel)
   - 7.4 [Mobile & Other Surfaces](#74-mobile--other-surfaces)

8. [Technology Stack](#8-technology-stack)

9. [Resilience & Failure Modes](#9-resilience--failure-modes)

10. [MVP Build Roadmap](#10-mvp-build-roadmap)

11. [Open Design Decisions](#11-open-design-decisions)

---

## 1. Product Vision & Strategy

### 1.1 What SaveStack Is

SaveStack is a **cloud-hosted, plugin-based intelligence platform** that helps users spend less on recurring expenses (fuel, groceries) and track financial progress toward life goals (house down payment, holidays, family). The system actively watches prices, learns personal habits, and delivers personalized, actionable recommendations.

**Key capabilities:**
- **Automatic price monitoring** across fuel stations and grocery chains
- **Habit learning** via purchase history and recurring patterns
- **Optimized trip planning** that minimizes total spend across multiple stores
- **Savings attribution** linking platform recommendations to actual financial wins
- **Goal tracking** with timeline projections and milestone alerts

### 1.2 Target User Profile

- **Demographics:** Young adults (25–40) with stable employment, earning €30k–€80k/year
- **Motivation:** Saving for major life goals without sacrificing convenience or time
- **Pain point:** Spending €150–300/month on fuel + groceries without clear visibility or optimization

### 1.3 Core Design Philosophy

> **The system does the thinking, the user just lives their life.**

SaveStack operates on one fundamental principle:
- **Minimize manual input** — users should never feel like data entry clerks
- **Maximize automation** — predictions, proposals, and optimizations run without asking
- **Empower decisions** — when users act, they do so with confidence because the platform has done the analysis
- **Transparency** — users always understand *why* the platform recommends something

---

## 2. Core Constraints & Design Principles

### 2.1 Data Freedom: No Cost, No Lock-in

> **All MVP data sources must be cost-free. No paid APIs. No proprietary data.**

This is non-negotiable for product-market fit in a bootstrapped, self-learning phase.

| Data Source | What We Get | Cost | Legal Basis |
|---|---|---|---|
| **MIMIT (Ministero Sviluppo Economico)** | 21,000+ Italian fuel stations + daily prices | Free | Italian Open Data License 2.0 (IODL), mandated by Art. 51 of Law 99/2009 |
| **DoveConviene.it** | ~80–200 grocery promotions per flyer page across major chains | Free | Structured `schema.org/Product` JSON-LD embedded in public HTML pages (published for SEO consumption) |
| **Open Food Facts** | Product identity, nutrition data, barcode lookup | Free | Open Database License (ODbL) |
| **Actual Budget API** | User's transaction history, account balances, budgets | Free | Open-source software; user self-hosts their own instance |

---

### 2.2 Minimal Manual Input

Users should accept only these input patterns:

| Action | Effort | Frequency |
|---|---|---|
| **One-time setup** | 5–10 min | Once |
| **Car details** (Fuel) | 2 min: consumption (L/100km), tank size, commute points | Once |
| **Preferred stores** (Grocery) | 3 min: list 3–5 nearby chains | Once |
| **Budget profile** (Budget) | 2 min: link Actual Budget instance + define savings goals | Once |
| **Review & adjust** | 1 min: approve/edit shopping list before heading to store | Per trip |
| **In-store tapping** | 1 sec/item: tap as you add to cart (e.g., 20 items = 20 sec) | Per trip |
| **Toggle plugins** | 10 sec: enable/disable features | As needed |

**Everything else is automatic:**
- Data ingestion, price scraping, comparison
- Recommendation generation, ranking, aggregation
- Habit analysis, pattern detection, trend forecasting
- Lifecycle tracking, savings accumulation, goal projection

---

### 2.3 Plugin-Based Architecture

> **New features are new plugins. Changes to core require consensus.**

**Architecture principles:**

- **Each plugin = independent microservice** running in its own process, with its own database schema, deployed independently
- **Language-agnostic** — plugins can be written in Java, Python, Node.js, Go, Rust, etc. (proven by MVP using three stacks)
- **Auto-discovery at runtime** — plugins register themselves; Core doesn't need to know about them in advance
- **Failure isolation** — one plugin crashing does not affect others or the Core
- **Asynchronous-first communication** — plugins publish domain events to Kafka; Core and other plugins listen

**Plugin contract:**
- Must expose HTTP `/api/health` endpoint (polled on startup)
- Must expose HTTP `/api/recommendations?user_id={id}` endpoint for aggregation
- Must publish heartbeat to Kafka topic `plugins.heartbeat` every 30 seconds
- Must listen to `core.plugin.settings_changed` for configuration updates
- Must listen to `core.discovery.ping` for self-registration opportunity

---

### 2.4 Cloud-Hosted, Multi-Tenant

SaveStack operates as a **single deployment serving many users:**

- **Core runs once** (horizontally scalable behind a load balancer)
- **Each plugin runs once** (or replicated for high availability)
- **One PostgreSQL instance** with separate schemas per service (multi-tenant database)
- **One Redis instance** for distributed caching and plugin health state
- **One Kafka cluster** for event distribution across services

**User isolation:**
- Users see only their own data
- Authentication via JWT (contains user_id); every endpoint validates it
- Row-level security enforced in PostgreSQL (queries filter by `user_id`)
- Plugin settings stored per-user in Core (users can toggle plugins independently)

---

## 3. MVP Plugins

### 3.1 Fuel Plugin

**Stack:** Spring Boot (Java) | **Author:** Core team

#### 3.1.1 Data Source: MIMIT Open Data

The Italian Ministry of Economic Development publishes fuel station data and daily prices.

**Data files (downloaded daily at 08:00 UTC):**

| File | Contents | Format | Notes |
|---|---|---|---|
| `anagrafica_impianti_attivi.csv` | Station metadata | Pipe-separated (since Feb 2026) | `id_impianto`, `gestore`, `bandiera` (brand), `indirizzo`, `comune`, `provincia`, `lat`, `lng` |
| `prezzo_alle_8.csv` | Daily prices at 8 AM | Pipe-separated | `id_impianto`, `combustibile` (fuel type), `prezzo` (EUR), `is_self` (self-service flag), `communicated_at` (ISO 8601 timestamp) |

**Scale:** ~21,000 active stations across Italy, ~15–30 different fuel types, updated daily.

#### 3.1.2 User Configuration (One-Time Setup)

User provides once and can edit anytime:

```
User Fuel Settings
├── car_consumption: number (L/100km)
├── tank_size: number (liters)
├── commute_route: array<{lat, lng}> (5–10 points from home to workplace)
└── preferred_fuels: array<string> (e.g., ["diesel95", "benzina95"])
```

#### 3.1.3 Core Algorithms

**A) Cheapest-on-Route Algorithm**

When the user requests a fuel recommendation, the system:

1. **Fetch current prices** from the fuel database
2. **Filter by fuel type** (user's preferred types)
3. **Find stations within corridor** along the commute route:
   - Corridor = ±3 km perpendicular to the line connecting home→workplace
   - Use PostGIS `ST_DWithin()` to find stations within the corridor
   - Exclude stations > 50 km away
4. **Calculate net savings** for each candidate station:

```
net_savings = (reference_price - candidate_price) × estimated_liters - (detour_km × consumption × fuel_price)

where:
  reference_price = weighted average of last 7 days at user's "usual" station
  candidate_price = today's price at candidate station
  estimated_liters = tank_size - (fuel_remaining_estimate)
  detour_km = extra distance to candidate vs usual station
  consumption = user's L/100km
  fuel_price = average fuel price (€/L) on the candidate
```

5. **Rank candidates** by net savings (highest first)
6. **Contextual recommendation** based on top station:

| Scenario | Recommendation Message |
|---|---|
| Top station savings > €5 + < 10 min detour | "Fill up at **[Station Name]** today — save **€X.XX**. Only 5 km out of your way." |
| Top station savings < €2 but convenient | "Prices are average today. Fill up wherever convenient — no significant gain." |
| Pattern shows cheaper tomorrow | "Wait — prices tend to drop tomorrow at your usual station. You could save **€X.XX**." |

**B) Price Pattern Analysis**

Analyze 4 weeks of price history per station:

```
weekly_pattern = average_price_by_day_of_week

Example for Diesel at Milano Station A:
  Monday:    €1.649 (cheapest)
  Tuesday:   €1.651
  Wednesday: €1.655
  Thursday:  €1.668
  Friday:    €1.695 (expensive)
  Saturday:  €1.702 (most expensive)
  Sunday:    €1.680
```

**Insight:** Station prices follow a 7-day cycle. Users who fill up Monday–Tuesday save ~€3 per tank vs Friday–Sunday. Identify and surface this pattern to the user.

**C) Fuel Remaining Estimate**

Track:
- Last fill-up date & location
- Fuel type & amount (liters)
- User's reported recent km driven

Calculate estimate:
```
fuel_remaining ≈ amount_added - (km_driven / consumption_L_per_100km)
```

Display urgency if remaining < 15L.

#### 3.1.4 Functional Requirements

| Requirement | Definition of Done |
|---|---|
| **Daily MIMIT ingestion** | Scheduled job runs at 08:00 UTC. Fetches both CSV files. Parses pipe-separated format. Stores or updates stations and prices in PostgreSQL. Logs success/failure. Retries once on network error. |
| **Station metadata storage** | Stations indexed by `id_impianto`, geo-indexed with PostGIS. Supports queries like "all stations within 10 km of point (45.5, 9.2)" in <100 ms. |
| **Price history retention** | Price records partitioned by date (daily partition). Supports 1-year lookback. Trend queries (e.g., "avg price per day of week") execute in <500 ms. |
| **Route corridor filtering** | Given home + work GPS + 3 km corridor width, return eligible stations. Test with actual Italian city topologies (Milan, Rome, Naples). |
| **Net savings calculation** | Correctly compute savings accounting for detour distance, vehicle consumption, and price difference. Test against manual calculations. |
| **Weekly pattern analysis** | Identify day-of-week seasonality. Flag if a station's Monday avg is statistically different from Friday avg (t-test, p < 0.05). |
| **Recommendation generation** | Produce a single, clear recommendation per user per day. Fit into a mobile-friendly card (< 2 lines of text). Include reason (savings, pattern, or neutral). |
| **30-day price trend** | Return aggregated price trends for a city and/or a specific station. Suitable for line charts (x=date, y=price). |
| **Fuel remaining tracking** | Show alert if estimated fuel < 20% of tank capacity. Allow user to log a fill-up manually if estimate diverges. |

#### 3.1.5 HTTP API Endpoints

```
GET    /api/recommendations?user_id={id}
       Response: { type: "fuel_recommendation", message, station, savings_euros, reasoning, trend_chart_url }

GET    /api/stations/search?lat={lat}&lng={lng}&radius_km={radius}
       Response: [{ id, name, address, brand, lat, lng, fuel_types, current_prices: { diesel95, benzina95 } }]

GET    /api/prices/history?station_id={id}&days=30
       Response: [{ date, fuel_type, avg_price, min_price, max_price }]

POST   /api/user/settings
       Body: { car_consumption, tank_size, commute_route, preferred_fuels }

GET    /api/health
       Response: { status: "UP", ... }
```

#### 3.1.6 Non-Functional Requirements

- **Geo-spatial queries:** Use PostGIS for efficient polygon/corridor operations
- **Public API (no auth):** Next.js frontend must be able to fetch city-level price data without JWT (for SEO pages). Serve cached data only.
- **Cache:** Redis `fuel:prices:current` (24h TTL), `fuel:prices:{city}` (15 min TTL)
- **Scaling:** Should handle 10,000 concurrent users requesting recommendations (async, queue-backed)

---

### 3.2 Grocery Plugin

**Stack:** FastAPI (Python) | **Author:** Data team

#### 3.2.1 Data Sources

**Primary: DoveConviene.it**
- Website embeds `schema.org/Product` JSON-LD in flyer page HTML
- ~80–200 products per flyer page
- Each product has: `name`, `price` (EUR), `priceCurrency`, `seller` (store chain), `validFrom`, `validThrough` (dates)
- Major chains: Eurospin, Conad, Lidl, Esselunga, Carrefour, Coop, Auchan, Bennet, and ~20 others

**Coverage:** ~100–200 HTTP requests/week covers all major Italian chains' active flyers.

**Secondary: Open Food Facts**
- Barcode → product database lookup
- Helps match user's product inventory to DoveConviene offers
- Used when user scans a barcode or when product name is ambiguous

#### 3.2.2 DoveConviene Scraping Pipeline

**Data Flow Diagram:**

```
┌─────────────────────────────────────────────────────────────────┐
│ Weekly Scraping Job (Scheduled per chain)                       │
└─────────────────────────────────────────────────────────────────┘

Step 1: Fetch Retailer Index Page
├─ URL: https://www.doveconviene.it/volantini/[retailer-slug]
├─ Extract: JSON-LD or HTML data attributes
├─ Output: { flyerId, validFrom, validThrough, retailerName }
└─ Rate limit: 1 req/sec, retry on 429

        ↓

Step 2: Fetch Each Flyer Page (Iterate)
├─ URL: https://www.doveconviene.it/volantini/[flyerId]
├─ Extract: All <script type="application/ld+json"> blocks (Product schema)
├─ Parse: name, price, priceCurrency, seller, validFrom, validThrough
└─ Rate limit: 1 req/sec per retailer

        ↓

Step 3: Normalize & Store
├─ Remove duplicates (same product at same store on same day)
├─ Convert price to EUR (assume EUR for Italian chains)
├─ Assign validity period (validFrom → validThrough)
├─ Store in PostgreSQL table `promotions`
└─ Log: which flyers processed, counts, errors

        ↓

Step 4: Cache
├─ Redis key: grocery:promos:{chain} (e.g., grocery:promos:lidl)
├─ Value: [{ productName, price, validUntil, store }]
└─ TTL: 7 days (flyer cycle)
```

**Acceptance Criteria:**
- Successfully parse JSON-LD from 95%+ of flyer pages (no vision AI, pure JSON parsing)
- Handle rate limits (429 responses); retry with exponential backoff
- Store promotions with clear validity dates (mismatches cause poor recommendations)
- No false positives: don't store expired offers

#### 3.2.3 User Product Inventory

Users define a list of products they buy regularly. Each product is learned over time.

**Product Entity:**

```
user_products
├── id: UUID
├── user_id: UUID (foreign key)
├── product_name: string (e.g., "Pasta Barilla 500g")
├── category: enum (Pasta, Oil, Dairy, Fruits, Vegetables, Meat, etc.)
├── avg_life_days: float (initially 7, adjusted over time)
├── purchase_count: int (starts at 0)
├── last_purchased: timestamp (nullable)
├── next_due: timestamp (calculated: last_purchased + avg_life_days)
└── barcode: string (optional, for direct matching)
```

#### 3.2.4 Average Life Calculation (Habit Learning)

The system learns *when* users tend to buy each product using a **weighted running average:**

```
Initial state (first purchase):
  avg_life_days = 7 (default assumption)
  purchase_count = 1

When user marks item as purchased (after shopping):
  actual_interval = today - last_purchased
  
  new_avg_life = (old_avg_life × min(purchase_count, 10) + actual_interval) / (min(purchase_count, 10) + 1)
  purchase_count += 1

Capped weight (max 10):
  The weight of historical data decreases over time, so recent purchases matter more
  This makes the system responsive to changing habits (e.g., seasonal produce, life changes)
```

**Example:**
```
User buys milk regularly.

Purchase 1 (Week 1):  interval = 7d,  purchase_count = 1  → avg_life = 7d
Purchase 2 (Week 3):  interval = 14d, purchase_count = 2  → avg_life = (7×1 + 14) / 2 = 10.5d
Purchase 3 (Week 5):  interval = 15d, purchase_count = 3  → avg_life = (10.5×2 + 15) / 3 = 11.8d
Purchase 4 (Week 6):  interval = 8d,  purchase_count = 4  → avg_life = (11.8×3 + 8) / 4 = 10.85d
...
After 10+ purchases, weight caps at 10, so recent purchases still influence but don't whip the average around.
```

#### 3.2.5 Shopping List Generation

**Trigger:** User requests a new shopping list (or system auto-generates weekly reminder).

**Algorithm:**

```
lookahead_days = 7 (e.g., plan for next week)

For each product in user_products:
  next_due = last_purchased + avg_life_days
  
  if next_due <= today + lookahead_days:
    add to shopping_list
    calculate urgency:
      if next_due < today:
        urgency = "overdue by {today - next_due} days"
      elif next_due == today:
        urgency = "due today"
      else:
        urgency = "due in {next_due - today} days"

Sort by urgency (overdue first, then due today, then due soon)
Return list with: product_name, category, urgency, last_purchase_price (if known)
```

**User interaction:**
1. System proposes list
2. User reviews on mobile app or dashboard
3. User unchecks items not needed this week (e.g., "I have enough pasta")
4. User adds items not predicted (e.g., "party snacks for this weekend")
5. User sets max shops to visit: 1, 2, or 3
6. **Submit** → system optimizes trip

#### 3.2.6 Trip Optimization Algorithm

**Goal:** Find the cheapest way to buy all items, visiting at most N stores.

**Data available:**
- Current promotions in Redis (chain → {product, price})
- User's shopping list (30 items typical)
- Max shops to visit (1, 2, or 3)
- User's preferred stores (cached settings)

**Algorithm (Brute Force over Store Combinations):**

```
Step 1: Fetch promotions
  For each product in list:
    Find all stores offering it at a promotion
    Find fallback price if not on promotion (cached avg for that store)
    candidates[product] = [(store_1, price_1), (store_2, price_2), ...]

Step 2: Generate store combinations
  If user selected max_shops = 3:
    Generate all combinations of 3 stores (or fewer) from their preferred stores
    typical: 10 preferred stores → C(10,3) = 120 combinations
    
Step 3: For each combination, solve assignment problem
  For each product, assign to the cheapest store in the combination
  total_cost = sum of all product prices
  
Step 4: Rank combinations by:
  Primary: total_cost (lowest first)
  Secondary: number of stores (fewer is better for effort, if cost difference < 2%)
  
Step 5: Return top combination
  per-store breakdown: { store, items: [{product, price}], subtotal }
  total_cost
  savings_vs_single_store: (if they bought everything at one store)
```

**Complexity:** 120 combos × 30 items × 2 assignments/item ≈ 7,200 ops → <100 ms on modest hardware. ✓

**Acceptance Criteria:**
- Correctly identify all items available at each store
- Handle out-of-stock (skip, flag to user)
- Rank options by total cost first
- Run in < 1 second for typical lists

#### 3.2.7 In-Store Shopping Mode

User opens app while at the store. Items are grouped by assigned store, ordered by aisle (if available).

**Flow:**

```
┌──────────────────────────────────────┐
│ Shopping List (Max 3 Stores)         │
├──────────────────────────────────────┤
│                                      │
│ LIDL                                 │
│ ├─ Pasta Barilla 500g      €1.20 ✓ ◄─ user taps "got it"
│ ├─ Olive Oil 1L            €8.50 ?  ◄─ system waiting
│ └─ Mozzarella 250g         €2.80 ✗  ◄─ user marks unavailable
│                                      │
│ EUROSPIN                             │
│ ├─ Rice Arborio 1kg        €4.20 ✓  │
│ └─ Canned Tomatoes (2x)    €1.60 ✓  │
│                                      │
│ ESSELUNGA                            │
│ └─ Wine Red 750ml          €7.99 ✗  │
│                                      │
└──────────────────────────────────────┘

Tapping an item → Mark as purchased (✓)
Long-press → Price differs or substitute → Update price
"Not found" → Mark unavailable (✗)
```

**Data capture:**
- Which items were actually purchased (vs skipped)
- Actual prices paid (vs expected from promotion)
- Substitutions (e.g., "Barilla → De Cecco")

#### 3.2.8 Feedback Loop (After Shopping)

After user marks the trip as complete:

1. **Recalculate avg_life** for each purchased product (see 3.2.4)
2. **Record actual prices** (if different from expected promotion)
3. **Publish Kafka event:**

```json
{
  "topic": "grocery.trip.completed",
  "payload": {
    "user_id": "user-123",
    "trip_id": "trip-456",
    "completed_at": "2026-04-09T18:30:00Z",
    "items": [
      { "product_id": "p1", "expected_price": 1.20, "actual_price": 1.25, "purchased": true },
      { "product_id": "p2", "expected_price": 2.80, "actual_price": null, "purchased": false }
    ],
    "total_cost": 45.80,
    "savings_vs_single_store": 12.50
  }
}
```

**Consumers:**
- **Budget plugin** — listens for `grocery.trip.completed`, accumulates savings
- **Core** — listens for `grocery.trip.completed`, sends notification "Trip complete! Saved €12.50 vs single-store baseline"

#### 3.2.9 Product Matching Strategy

When a user adds a product to their inventory, the system must match it against DoveConviene offers.

**Matching hierarchy (fallback chain):**

1. **Exact match** — product name exactly matches previous successful match (cached)
2. **Fuzzy match** — Levenshtein distance or trigram similarity
   - Threshold: ~85% similarity (tuned via testing)
   - Example: "Pasta Barilla 500g" fuzzy-matches to "Barilla pasta 500g"
3. **Barcode lookup** — if user provides/scans barcode, look up in Open Food Facts
4. **User confirmation** — if multiple matches or low confidence, ask user to select

**Machine Learning (deferred for V2):**
- Embeddings-based matching (SBERT or similar)
- Cross-language support
- Handle packaging variations better

#### 3.2.10 HTTP API Endpoints

```
GET    /api/recommendations?user_id={id}
       Response: { type: "grocery_list", items: [...], best_trip: { stores, total_cost, savings } }

POST   /api/shopping-list/generate
       Body: { user_id, lookahead_days, max_shops }
       Response: { items: [...], optimized_trips: [{stores, total, savings}] }

POST   /api/shopping-list/{id}/complete
       Body: { items: [{product_id, actual_price, purchased}] }
       Response: { success, avg_life_updates: {...}, savings }

GET    /api/user/products
       Response: [{ id, name, category, avg_life_days, last_purchased, next_due }]

POST   /api/user/products
       Body: { name, category, barcode? }
       Response: { id, matched_to_offers: [...] }

GET    /api/health
       Response: { status: "UP", last_scrape: "2026-04-09T08:00:00Z" }
```

#### 3.2.11 Non-Functional Requirements

- **Scraping frequency:** Weekly per chain (flyer cycle is ~7 days; daily is overkill)
- **Product matching accuracy:** Achieve 90%+ correct matches on common Italian products
- **List generation latency:** <500 ms from request to response
- **Trip optimization latency:** <1 s for typical 30-item list with 3-store constraint
- **Data freshness:** Promotions should be no older than 7 days (flyer validity)

---

### 3.3 Budget Plugin

**Stack:** Express (Node.js) | **Author:** Finance team

#### 3.3.1 Data Source: Actual Budget API

**Actual Budget** is an open-source, self-hosted personal finance app. Users self-host their instance and provide SaveStack with access credentials.

**Integration method:**
- User provides: Actual Budget URL, API token
- Plugin uses `@actual-app/api` (Node.js native client)
- Connection: plugin → Actual Budget instance (user-controlled, not cloud)

**Data available:**
- Transactions (date, amount, category, description)
- Accounts (checking, savings, credit card, etc.)
- Categories (custom user-defined)
- Budgets (e.g., "Groceries: €300/month")

#### 3.3.2 Savings Goals

User defines savings goals with target amount and deadline.

**Goal Entity:**

```
goals
├── id: UUID
├── user_id: UUID
├── name: string (e.g., "House down payment")
├── description: string (optional)
├── target_amount: float (e.g., 30000)
├── deadline: date (e.g., 2029-01-15)
├── current_amount: float (synced from Actual Budget)
└── created_at: timestamp
```

#### 3.3.3 Savings Rate & Timeline Projection

**Calculation:**

```
Step 1: Fetch transactions from Actual Budget (last 3 months)

Step 2: Classify as "income" or "expense" by category
  income = sum of categorized income
  expenses = sum of categorized expenses
  
Step 3: Calculate monthly savings rate (average)
  monthly_savings_rate = (income - expenses) / 3
  
Step 4: Project goal completion
  For each goal:
    remaining = target_amount - current_amount
    months_to_goal = remaining / monthly_savings_rate
    projected_date = today + (months_to_goal months)
    
    Compare projected_date vs deadline:
      if projected_date < deadline:
        delta = deadline - projected_date
        message = "You're {delta} ahead on your {goal_name} fund"
      elif projected_date > deadline:
        delta = projected_date - deadline
        message = "At current pace, {delta} late. Optimizing fuel + groceries could close the gap."
      else:
        message = "On track to reach {goal_name} by {deadline}"
```

**Example:**
```
Goal: House down payment €30,000 by 2029-01-15
Current: €12,000 (40% complete)
Monthly savings: €800 (from 3-month average)
Remaining: €18,000

Projection:
  months_to_goal = 18,000 / 800 = 22.5 months
  projected_date = 2026-04-09 + 22.5 months = ~Feb 2028
  delta = 2029-01-15 - 2028-02-15 = ~11 months

Message: "You're 11 months ahead on your House fund. Keep it up!"
```

#### 3.3.4 Savings Attribution from SaveStack Plugins

The system tracks how much the user has saved thanks to SaveStack recommendations.

**Kafka event consumption:**

| Topic | Event | Action |
|---|---|---|
| `grocery.trip.completed` | Trip finished, savings calculated | Accumulate: `savings_log.amount = trip.savings_vs_single_store` |
| `fuel.recommendation.followed` | User filled up at recommended station | Estimate: `savings_log.amount = (user_price - avg_price) * liters` |

**Tracking:**

```
savings_log
├── id: UUID
├── user_id: UUID
├── date: date
├── source_plugin: enum (fuel, grocery, ...)
├── source_event_id: UUID (trip_id or recommendation_id)
├── amount: float (EUR)
└── description: string (e.g., "Saved on Lidl trip" or "Filled up at cheaper station")

Aggregations:
├── This month: SUM(amount) WHERE month = current_month
├── Year-to-date: SUM(amount) WHERE year = current_year
├── All-time: SUM(amount)
└── By plugin: GROUP BY source_plugin
```

#### 3.3.5 Monthly Savings Pulse

Once per month, the system generates a non-nagging summary digest.

**Content:**

```
🎯 Savings Pulse — April 2026

Fuel savings:     +€28.50  (3 recommended fill-ups)
Grocery savings:  +€34.20  (2 multi-store trips)
Total SaveStack:  +€62.70

Progress on "House down payment":
  Current: €12,062.70 (+€62.70 this month)
  Target:  €30,000 by Jan 2029
  Status:  On track, 11 months ahead

What's next:
  • Gas prices down Friday–Monday; fill up then
  • New promotions at Lidl starting tomorrow
  • You're due to buy milk and pasta
```

**Delivery:** Email, push notification, in-app digest (user chooses in settings).

#### 3.3.6 HTTP API Endpoints

```
GET    /api/recommendations?user_id={id}
       Response: { type: "budget_summary", goals: [...], monthly_savings, annual_projection }

GET    /api/goals?user_id={id}
       Response: [{ id, name, target, deadline, current, projected_date, status }]

POST   /api/goals
       Body: { user_id, name, target_amount, deadline }
       Response: { id, ... }

GET    /api/savings?user_id={id}&period=month_or_ytd
       Response: { total, by_plugin: {fuel, grocery}, transactions: [...] }

POST   /api/settings
       Body: { actual_budget_url, api_token, sync_frequency }
       Response: { success, tested: bool }

GET    /api/health
       Response: { status: "UP", last_sync: "2026-04-09T12:00:00Z" }
```

#### 3.3.7 Non-Functional Requirements

- **Sync frequency:** Every 6 hours (tunable)
- **Data freshness:** Actual Budget transactions should be <6h old in SaveStack
- **Projection accuracy:** Based on 3-month average (assume static rate for MVP; ML seasonality deferred)
- **Notification frequency:** One digest/month (non-intrusive)

---

## 4. Platform Core

**Stack:** Spring Boot (Java) | **Author:** Core team

The Platform Core is the hub connecting users, plugins, and notifications. It does not perform any domain logic (fuel, grocery, budget); it orchestrates plugins and manages the user experience.

### 4.1 Authentication & Authorization

#### 4.1.1 JWT-Based Stateless Auth

All services share a single JWT secret (environment variable `JWT_SECRET`).

**Token structure:**

```json
{
  "sub": "user-123",
  "email": "alice@example.com",
  "roles": ["user"],
  "iat": 1712707200,
  "exp": 1712793600,
  "iss": "savestack.core"
}
```

**Validation:**
- Each service (Core, Fuel, Grocery, Budget) validates JWT independently
- No service-to-service authentication calls
- Signature verified using shared secret
- Expiration checked; if expired, 401 Unauthorized

**Roles:**
- `user` — regular end-user
- `admin` — platform operator (can see all users, trigger discovery, view system health)

#### 4.1.2 Public vs Protected Routes

| Route | Auth Required | Purpose |
|---|---|---|
| `POST /api/auth/register` | No | User signup |
| `POST /api/auth/login` | No | User login, returns JWT |
| `GET /api/auth/refresh` | No (but optional JWT) | Refresh token |
| `GET /api/fuel/...` (all fuel endpoints) | **No** | Public fuel data for Next.js SEO pages |
| `GET /api/plugins` | **Yes** | List available plugins |
| `POST /api/plugins/{id}/activate` | **Yes** | User activates a plugin |
| `GET /api/recommendations` | **Yes** | Get aggregated recommendations |
| `POST /admin/...` | **Yes** + admin role | Admin operations |

---

### 4.2 Plugin Lifecycle Management

Plugins have a well-defined lifecycle: discovery → registration → heartbeat → settings → health.

#### 4.2.1 Plugin Discovery & Self-Registration

**Trigger 1: Core Startup**
- Core publishes Kafka event: `core.discovery.ping`
- All plugins (currently running) hear the ping
- Plugins respond by HTTP POST to Core with metadata

**Trigger 2: Admin Action ("Rediscover")**
- Admin clicks "Rediscover Plugins" in admin panel
- Core publishes `core.discovery.ping` again
- Plugins respond with updated metadata

**Trigger 3: Plugin Startup (Fast Path)**
- Plugin on startup: tries HTTP POST to Core immediately (doesn't wait for ping)
- Reduced latency for single-plugin restarts

**Registration Endpoint (Core):**

```
POST /api/plugins/register
Body:
{
  "id": "fuel-plugin",
  "name": "Fuel Optimizer",
  "description": "Find cheapest fuel stations on your route",
  "version": "1.2.3",
  "category": "spending",
  "base_url": "http://fuel-service:8080",
  "contact_email": "fuel@savestack.local",
  "settings_schema": {
    "type": "object",
    "properties": {
      "car_consumption": { "type": "number", "unit": "L/100km" },
      "tank_size": { "type": "number", "unit": "liters" }
    }
  }
}

Response:
{
  "id": "fuel-plugin",
  "registered_at": "2026-04-09T12:00:00Z",
  "status": "active"
}
```

**Storage:**
- Core stores plugin metadata in PostgreSQL table `plugins`
- Updated on each registration (idempotent)

#### 4.2.2 Plugin Heartbeat & Health Status

**Heartbeat (From Plugin):**

Every 30 seconds, each plugin publishes to Kafka topic `plugins.heartbeat`:

```json
{
  "plugin_id": "fuel-plugin",
  "status": "UP",
  "version": "1.2.3",
  "timestamp": "2026-04-09T12:00:00Z",
  "metrics": {
    "uptime_seconds": 86400,
    "requests_processed": 12000,
    "errors_last_minute": 0
  }
}
```

**Consumption (Core):**

Core Kafka consumer listens to `plugins.heartbeat`:
1. Parses message
2. Writes to Redis: `SET plugin:{id}:status "UP" EX 90`
3. Logs for observability

**Expiration:**
- Redis TTL = 90 seconds (3× heartbeat interval)
- If plugin stops sending heartbeats for >90s, key expires automatically
- Status queries return "DOWN"

**Health Check Endpoint (Query):**

```
GET /api/plugins/{id}/status

Response:
{
  "id": "fuel-plugin",
  "status": "UP",   // or "DOWN"
  "last_heartbeat": "2026-04-09T12:00:30Z",
  "version": "1.2.3"
}
```

#### 4.2.3 Plugin Settings Sync

Settings are the source of truth for how a plugin behaves (e.g., which fuel types a user prefers, max shops to visit).

**Source of truth:** PostgreSQL table `user_plugins.settings` (JSONB column)

**Flow:**

```
User changes settings (e.g., "max shops = 2") in Angular dashboard
          ↓
Core API endpoint: POST /api/users/{id}/plugins/{plugin_id}/settings
          ↓
Core validates + stores in PostgreSQL (user_plugins.settings)
          ↓
Core publishes Kafka event: core.plugin.settings_changed
          ↓
Plugin listens to Kafka, receives event, updates local cache (its own DB)
          ↓
During normal operation, plugin reads local cache (fast), never calls Core
```

**Kafka Event:**

```json
{
  "topic": "core.plugin.settings_changed",
  "payload": {
    "plugin_id": "grocery-plugin",
    "user_id": "user-123",
    "settings": {
      "preferred_stores": ["lidl", "conad"],
      "max_shops_default": 2,
      "notify_when_list_ready": true
    },
    "changed_at": "2026-04-09T12:00:00Z"
  }
}
```

---

### 4.3 Notifications & Recommendation Aggregation

#### 4.3.1 Event-Driven Notifications

Core listens to Kafka topics from plugins and sends notifications to users.

**Kafka Topics → Notifications:**

| Topic | Event | Notification | Channels |
|---|---|---|---|
| `grocery.list.generated` | Shopping list ready | "Your shopping list is ready (5 items, €45 saved)" | Push + Email |
| `fuel.alert.price_drop` | Price drops on user's route | "Prices dropped 3% today. Save €2.50 at [Station]" | Push + CarPlay card |
| `budget.goal.milestone` | Goal milestone reached | "🎉 You're 50% toward your House fund!" | Push + Email |

**Core logic:**

```
For each Kafka event:
  1. Extract plugin_id and user_id
  2. Check: Is plugin active for user? (query user_plugins.active)
  3. If no: skip (don't notify user about inactive plugins)
  4. If yes: send notification (push, email, MQTT — user's preference)
```

#### 4.3.2 Recommendation Aggregation

When user opens dashboard, Angular frontend calls:

```
GET /api/recommendations?user_id={id}
```

Core responds with:

```json
{
  "recommendations": [
    {
      "plugin_id": "fuel-plugin",
      "type": "fuel_recommendation",
      "priority": 1,
      "message": "Fill up at Eni, Milano — save €2.50",
      "action_url": "http://fuel-service:8080/api/recommendations?user_id=..."
    },
    {
      "plugin_id": "grocery-plugin",
      "type": "grocery_list",
      "priority": 2,
      "message": "Your shopping list is ready (5 items, €45 estimated)",
      "action_url": "http://grocery-service:8080/api/recommendations?user_id=..."
    },
    {
      "plugin_id": "budget-plugin",
      "type": "budget_summary",
      "priority": 3,
      "message": "You're on track for your House fund. 11 months ahead!",
      "action_url": "http://budget-service:8080/api/recommendations?user_id=..."
    }
  ]
}
```

**Aggregation algorithm:**

```
For each active plugin for this user:
  1. HTTP GET {plugin_base_url}/api/recommendations?user_id={user_id}
  2. Timeout: 3 seconds (slow plugins are skipped)
  3. If error: log, skip plugin (partial success is OK)
  4. Collect response: { type, message, priority, ... }
  
Sort by priority (lower = more urgent)
Return to client
```

**Fallback:** If all plugins timeout, return cached recommendations from last successful call.

---

## 5. Communication Model

### 5.1 Protocol Rules

> **Clear rule: Use the right tool for the job.**

| Pattern | Protocol | Reason | Example |
|---|---|---|---|
| **Need a response** | HTTP (request-response) | Synchronous, expects answer | Client → Core: "Get my recommendations" |
| **Just informing / Broadcasting** | Kafka (pub-sub) | Asynchronous, many listeners | Plugin → Core: "Trip completed, here's data" |
| **Plugins calling each other** | **Never HTTP** — use Kafka | Loose coupling, no direct dependencies | Grocery doesn't call Budget; publishes to Kafka |

**Rationale:**
- HTTP calls between plugins create tight coupling → harder to test, deploy, scale
- Kafka decouples plugins → any plugin can fail without affecting others
- Core acts as hub, not router

---

### 5.2 Kafka Event Streams

**Single Kafka cluster, eight topics:**

| Topic | Producer | Consumer(s) | Latency SLA |
|---|---|---|---|
| `core.discovery.ping` | Core | All plugins | — (admin-triggered, not time-critical) |
| `core.plugin.settings_changed` | Core | Affected plugin | <5 seconds (plugin caches, applies immediately) |
| `plugins.heartbeat` | All plugins | Core | ~30 seconds (batch heartbeat) |
| `grocery.trip.completed` | Grocery | Budget, Core | <10 seconds (for notifications + savings tracking) |
| `grocery.list.generated` | Grocery | Core | <5 seconds (for "list ready" notification) |
| `fuel.alert.price_drop` | Fuel | Core | <10 seconds (for price alert notification) |
| `budget.goal.milestone` | Budget | Core | <10 seconds (for milestone notification) |
| `core.audit.log` | Core | Observability stack | <1 minute (non-critical) |

**Message format (standard):**

```json
{
  "id": "msg-uuid-12345",
  "topic": "grocery.trip.completed",
  "timestamp": "2026-04-09T18:30:00Z",
  "source": "grocery-plugin",
  "user_id": "user-123",
  "payload": {
    "trip_id": "trip-456",
    "items_count": 30,
    "total_cost": 45.80,
    "savings_vs_single_store": 12.50
  }
}
```

---

## 6. Data Storage Architecture

### 6.1 PostgreSQL Schema

**Single PostgreSQL instance.** Multi-tenant via separate schemas per service. Row-level security enforced in queries.

```
savestack_core (Core service)
├── users
│   ├── id (UUID, PK)
│   ├── email (string, unique)
│   ├── password_hash (string, bcrypt)
│   ├── roles (array: ["user"] or ["admin"])
│   └── created_at (timestamp)
│
├── plugins
│   ├── id (string, PK) — e.g., "fuel-plugin"
│   ├── name (string)
│   ├── description (string)
│   ├── version (string)
│   ├── category (enum: spending, tracking, goals, other)
│   ├── base_url (string) — e.g., "http://fuel-service:8080"
│   ├── contact_email (string)
│   ├── settings_schema (JSONB)
│   └── first_seen (timestamp)
│
└── user_plugins
    ├── user_id (UUID, FK → users.id)
    ├── plugin_id (string, FK → plugins.id)
    ├── active (boolean) — user has enabled this plugin
    ├── settings (JSONB) — user's settings for this plugin
    ├── activated_at (timestamp)
    └── PK(user_id, plugin_id)

savestack_fuel (Fuel plugin)
├── stations
│   ├── id_impianto (integer, PK) — MIMIT station ID
│   ├── gestore (string) — operator
│   ├── bandiera (string) — brand
│   ├── indirizzo (string)
│   ├── comune (string) — city
│   ├── provincia (string) — province
│   ├── lat (numeric)
│   ├── lng (numeric)
│   ├── geom (PostGIS geometry) — for spatial queries
│   └── updated_at (timestamp)
│
├── prices (partitioned by date: prices_20260409, prices_20260408, ...)
│   ├── id_impianto (integer, FK)
│   ├── fuel_type (string) — e.g., "diesel95"
│   ├── price (numeric) — EUR per liter
│   ├── is_self (boolean) — self-service
│   ├── communicated_at (timestamp)
│   └── PK(id_impianto, fuel_type, communicated_at)
│
└── user_settings
    ├── user_id (UUID, PK)
    ├── car_consumption (numeric) — L/100km
    ├── tank_size (numeric) — liters
    ├── commute_route (JSONB) — array of {lat, lng}
    ├── preferred_fuels (array: ["diesel95", "benzina95"])
    └── updated_at (timestamp)

savestack_grocery (Grocery plugin)
├── promotions
│   ├── id (UUID, PK)
│   ├── store_chain (string) — e.g., "lidl"
│   ├── product_name (string)
│   ├── price (numeric) — EUR
│   ├── original_price (numeric, nullable)
│   ├── discount_pct (numeric, nullable)
│   ├── valid_from (date)
│   ├── valid_to (date)
│   ├── flyer_id (string, optional) — source flyer
│   └── inserted_at (timestamp)
│
├── user_products
│   ├── id (UUID, PK)
│   ├── user_id (UUID, FK)
│   ├── product_name (string)
│   ├── category (string)
│   ├── avg_life_days (numeric, default 7)
│   ├── purchase_count (integer)
│   ├── last_purchased (date, nullable)
│   ├── next_due (date, computed)
│   ├── barcode (string, nullable)
│   └── updated_at (timestamp)
│
├── shopping_trips
│   ├── id (UUID, PK)
│   ├── user_id (UUID, FK)
│   ├── planned_date (date)
│   ├── max_shops (integer) — 1, 2, or 3
│   ├── estimated_total (numeric)
│   ├── actual_total (numeric, nullable)
│   ├── savings (numeric, nullable) — vs single-store baseline
│   ├── status (enum: draft, planned, shopping, completed, abandoned)
│   └── created_at, updated_at (timestamps)
│
├── trip_items
│   ├── id (UUID, PK)
│   ├── trip_id (UUID, FK)
│   ├── product_id (UUID, FK → user_products.id)
│   ├── assigned_store (string) — which chain
│   ├── expected_price (numeric)
│   ├── actual_price (numeric, nullable)
│   ├── purchased (boolean)
│   ├── skipped (boolean)
│   └── updated_at (timestamp)
│
└── user_settings
    ├── user_id (UUID, PK)
    ├── preferred_stores (array: ["lidl", "conad"])
    ├── max_shops_default (integer, default 2)
    ├── notify_when_list_ready (boolean)
    └── updated_at (timestamp)

savestack_budget (Budget plugin)
├── goals
│   ├── id (UUID, PK)
│   ├── user_id (UUID, FK)
│   ├── name (string) — e.g., "House down payment"
│   ├── description (string, nullable)
│   ├── target_amount (numeric)
│   ├── deadline (date)
│   ├── current_amount (numeric) — synced from Actual Budget
│   ├── created_at (timestamp)
│   └── updated_at (timestamp)
│
├── snapshots (monthly financial snapshots)
│   ├── id (UUID, PK)
│   ├── user_id (UUID, FK)
│   ├── month (date) — first day of month
│   ├── income (numeric)
│   ├── expenses (numeric)
│   ├── savings_rate (numeric) — (income - expenses) / income
│   └── recorded_at (timestamp)
│
├── savings_log (detailed savings transactions)
│   ├── id (UUID, PK)
│   ├── user_id (UUID, FK)
│   ├── date (date)
│   ├── source_plugin (enum: fuel, grocery)
│   ├── source_event_id (UUID) — trip_id or recommendation_id
│   ├── amount (numeric) — EUR
│   ├── description (string)
│   └── recorded_at (timestamp)
│
└── user_settings
    ├── user_id (UUID, PK)
    ├── actual_budget_url (string)
    ├── actual_budget_token (string, encrypted)
    ├── sync_frequency (integer, default 6 hours)
    └── updated_at (timestamp)
```

---

### 6.2 Redis Cache Strategy

| Key Pattern | Value | TTL | Purpose |
|---|---|---|---|
| `plugin:{id}:status` | `{ status: "UP"\|"DOWN", last_heartbeat, version }` | 90s | Plugin liveness (auto-expires) |
| `fuel:prices:current` | Gzipped JSON of all station prices | 24h | Fuel plugin internal cache |
| `fuel:prices:{city}` | Formatted city-level prices for Next.js | 15 min | Public API response caching (SEO pages) |
| `grocery:promos:{chain}` | Array of current promotions per chain | 7 days | Grocery plugin cached promotions (flyer cycle) |
| `jwt:blacklist:{token_hash}` | Empty (presence = true) | 24h | Logged-out tokens (optional, for strict logout) |

---

## 7. Frontend Architecture

SaveStack needs **three distinct web frontends** to serve different audiences. Each has a specific purpose and target user.

### 7.1 Next.js Public Website

**Domain:** `savestack.com` | **Framework:** Next.js (React) | **Auth:** None

**Purpose:**
- SEO landing page and acquisition funnel
- Fuel price explorer (no login required) — a free tool that drives organic traffic
- Blog / educational content about fuel savings
- Signup/login redirect to Angular dashboard

**Key pages:**

| Route | Type | Purpose | SEO Value |
|---|---|---|---|
| `/` | SSG | Landing page, value prop, CTA | "SaveStack — Save on Fuel & Groceries" |
| `/fuel` | SSG | Fuel explorer landing | "Find Cheapest Fuel Near You" |
| `/fuel/[city]` | SSR | City-specific prices (Milano, Roma, etc.) | "Cheapest Fuel in Milano Today" |
| `/fuel/station/[id]` | SSR | Individual station detail + 30-day history | "Eni Milano Prezzo Benzina — Storico" |
| `/about` | SSG | About SaveStack | "About SaveStack" |
| `/plugins` | SSG | Plugin showcase | "SaveStack Plugins" |
| `/blog/[slug]` | MDX | Articles: "How to Save on Fuel", "Grocery Hacks" | "How to Save €1000/Year on Fuel" |

**API Routes (caching facade to Fuel plugin):**

```
GET  /api/fuel/search?q={query}  (fuzzy city search)
GET  /api/fuel/cities            (list of indexed cities)
GET  /api/fuel/[city]/prices     (current prices in city)
GET  /api/fuel/trends            (30-day trend data for charts)
```

**User journey:**
```
Google "prezzo benzina Milano" 
  ↓ (organic search)
Lands on /fuel/milano (SSR page with real data)
  ↓
Sees live fuel prices + chart
  ↓
Clicks "Get Personalized Advice" CTA
  ↓
Redirected to /signup → app.savestack.com (Angular dashboard)
```

---

### 7.2 Angular User Dashboard

**Domain:** `app.savestack.com` | **Framework:** Angular (SPA) | **Auth:** JWT

**Purpose:**
- Full interactive experience for logged-in users
- Multi-plugin integration and control
- Real-time recommendations from all active plugins
- User settings, notifications, history

**Key sections:**

| Route | Purpose | Components |
|---|---|---|
| `/dashboard` | **Home** — quick overview, all active recommendations | Fuel card, Grocery card, Budget card, recent savings |
| `/dashboard/plugins` | **Plugin Store** — browse, install, configure plugins | Plugin cards with toggle, settings forms |
| `/dashboard/fuel` | **Fuel Hub** — map, route, trends, recommendation history | Map (stations), route editor, price chart, recs history |
| `/dashboard/grocery` | **Grocery Hub** — products, lists, trips, in-store mode | Product library, list editor, trip optimizer, shopping cart UI |
| `/dashboard/budget` | **Budget Hub** — goals, projections, monthly pulse, charts | Goal cards, timeline projection, savings breakdown by plugin |
| `/dashboard/settings` | **User Settings** — profile, notifications, integrations | Profile, 2FA, notification preferences, Actual Budget link |

**Real-time elements:**
- Fuel recommendations update daily (scheduled)
- Grocery lists generated on-demand (user clicks "Generate List")
- Budget goals updated every 6 hours (Actual Budget sync)
- Push notifications for price drops, list ready, milestones

---

### 7.3 Nuxt.js Admin Panel

**Domain:** `admin.savestack.com` | **Framework:** Nuxt.js (SSR + SPA) | **Auth:** JWT + admin role

**Purpose:**
- Platform operator monitoring and control
- System health overview
- Plugin lifecycle management
- User analytics and diagnostics

**Key sections:**

| Route | Purpose |
|---|---|
| `/admin` | **System Overview** — user count, request throughput, error rate, Kafka lag, DB connections |
| `/admin/plugins` | **Plugin Health** — each plugin's status, heartbeat, version, logs, restart controls |
| `/admin/plugins/discover` | **Plugin Discovery** — trigger discovery ping, view registration history |
| `/admin/users` | **User Management** — list users, per-user plugin activation, diagnostics |
| `/admin/events` | **Event Log** — Kafka event viewer (can filter by topic, time range, payload) |
| `/admin/scrapers` | **Scraper Status** — MIMIT download status, DoveConviene scrape runs, last success/error |

---

### 7.4 Mobile & Other Surfaces

| Surface | Technology | Primary Contact | Purpose |
|---|---|---|---|
| **Mobile app** | Flutter | Core + plugins | Full user experience on-the-go: see recommendations, start shopping trips, manage plugins |
| **CarPlay / Android Auto** | Native (Car Framework) | Fuel plugin REST API | Single card: fuel recommendation + map to cheapest station |
| **Physical ESP32 display** | MQTT + HAL | Core (MQTT broker) | Wall-mounted widget showing: daily fuel tip + goal progress bar |

---

## 8. Technology Stack

### 8.1 Backend Services

| Service | Language | Framework | Rationale |
|---|---|---|---|
| **Core** | Java | Spring Boot 4.0.3 | Mature, robust, good for orchestration |
| **Fuel Plugin** | Java | Spring Boot | Same team, familiar patterns, geo queries (PostGIS) |
| **Grocery Plugin** | Python | FastAPI | Fast HTTP, easy web scraping, data transformation |
| **Budget Plugin** | Node.js | Express | Lightweight, easy to integrate with npm package (@actual-app/api) |

### 8.2 Frontend & Client

| Application | Technology | Rationale |
|---|---|---|
| **Public website** | Next.js (React) | SSR for SEO, API routes for caching, fast page loads |
| **User dashboard** | Angular | Mature SPA framework, powerful data binding, component-driven |
| **Admin panel** | Nuxt.js (Vue) | Lightweight SSR, less boilerplate than Angular |
| **Mobile app** | Flutter | Cross-platform (iOS + Android), fast development, native feel |

### 8.3 Infrastructure & Data

| Component | Technology | Rationale |
|---|---|---|
| **Event bus** | Apache Kafka | Proven, scalable, ordered topics, consumer groups |
| **Primary database** | PostgreSQL | ACID, multi-schema support, PostGIS for geo queries |
| **Cache layer** | Redis | Fast key-value, TTL expiration, Pub/Sub (optional) |
| **Message broker** | Mosquitto (MQTT) | Lightweight pub/sub for ESP32 and mobile push |
| **Reverse proxy** | Nginx or Traefik | Load balancing, SSL termination, routing |
| **Deployment** | Docker + Kubernetes | Container isolation, orchestration, horizontal scaling |
| **Observability** | Prometheus + Grafana | Metrics collection, dashboards, alerting |

---

## 9. Resilience & Failure Modes

This section defines system behavior under stress and failure conditions.

| Failure Scenario | Expected Behavior | Recovery |
|---|---|---|
| **One plugin goes down** | Only that plugin's features unavailable. Other plugins work normally. User sees "temporarily unavailable" for that plugin's recommendation. | Plugin restarts → sends heartbeat → auto-detected within 30s |
| **Plugin comes back online** | Auto-detected via heartbeat. Status changes from DOWN to UP. Plugin's features immediately available again. | — (transparent recovery) |
| **Core goes down briefly** | Mobile clients work with cached JWT + cached plugin catalog. Can't login, can't change settings, can't sync new data. But active plugins still respond to direct HTTP calls. | Core restarts → accepts new logins and changes |
| **Kafka goes down** | Discovery pings don't reach plugins. Cross-plugin events don't flow. But direct HTTP calls still work (Fuel, Grocery, Budget endpoints). Plugins stay live (cached settings). | Kafka restarts → buffered messages replayed → system re-syncs |
| **PostgreSQL goes down** | Full outage. No login possible. All services eventually timeout and return 503. | DB restarts or failover to replica (HA setup in production) |
| **Redis goes down** | Plugin health status not updated (appears DOWN even if running). Cache misses → direct DB queries (slower). No TTL expiration for blacklisted tokens. | Redis restarts → cache rebuilds, heartbeats resume. Old tokens still accepted (minor risk). |
| **Slow plugin response** | Client request to Core's aggregation endpoint times out at 3s. Slow plugin's recommendation skipped; others returned. | Timeout metrics logged. Ops investigates slow plugin. Plugin may be rate-limited or restarted. |
| **Partial Kafka failure (some brokers down)** | If quorum maintained (3+ brokers, 2 down): no impact. If quorum lost: writes fail, reads stall. | Wait for broker recovery or manual intervention. |

**Monitoring alerts:**
- Plugin status DOWN for >5 min
- Kafka consumer lag >10k messages
- PostgreSQL replication lag >1s
- API endpoint p95 latency >2s
- Error rate >1%

---

## 10. MVP Build Roadmap

The MVP is broken into 10 sequential phases, each producing independently useful functionality. Phases have dependencies; they build on each other.

### Phase Overview

```
┌─ Phase 1: Core Platform (Dependency: none)
│  └─ Phase 2: Fuel Plugin (Dependency: Core)
│     └─ Phase 3: Mobile App (Dependency: Core + Fuel)
│        └─ Phase 4: Public Website (Dependency: Fuel plugin)
│           └─ Phase 5: Grocery Plugin (Dependency: Core)
│              └─ Phase 6: Budget Plugin (Dependency: Core + Grocery for Kafka)
│                 └─ Phase 7: Angular Dashboard (Dependency: Core + all plugins)
│                    └─ Phase 8: Admin Panel (Dependency: Core)
│                       └─ Phase 9: CarPlay / Android Auto (Dependency: Fuel plugin)
│                          └─ Phase 10: ESP32 Display (Dependency: Core + Budget)
```

### Phase Breakdown

| Phase | What | Duration Estimate | Outcome | Launch? |
|---|---|---|---|---|
| **1** | **Core: Auth, Plugin Registry, Heartbeat, Discovery** | 2 weeks | Platform shell running. Users can register, plugins can register and heartbeat. No real features yet. | No (internal) |
| **2** | **Fuel Plugin: MIMIT Ingestion, Recommendation** | 2 weeks | First useful feature working. Users can get fuel recommendations. Scheduled job downloads daily prices. | Maybe (beta) |
| **3** | **Flutter App: Basic Layout, Fuel Screen, Notifications** | 2 weeks | Users can use app to see fuel recommendations and get push notifications. | Maybe (internal beta) |
| **4** | **Next.js Public Site: Fuel Explorer, SEO** | 1 week | Public fuel data accessible without login. Google starts indexing. Organic traffic funnel activated. | Yes (if Phase 2 solid) |
| **5** | **Grocery Plugin: Scraper, Product Inventory, Shopping List, Trip Optimizer** | 3 weeks | Second major feature. Users can manage product inventory and get optimized shopping trips. | Yes (new feature drop) |
| **6** | **Budget Plugin: Actual Budget Sync, Goals, Savings Tracking** | 2 weeks | Third feature. Connects to user's budget, tracks savings. Financial goals added. | Yes (new feature drop) |
| **7** | **Angular Dashboard: Full Analytics, Multi-Plugin UI** | 2 weeks | Complete web experience. Users can manage all plugins, see all recommendations, adjust settings. | Yes (major update) |
| **8** | **Nuxt.js Admin Panel: Plugin Health, User Diagnostics, Event Log** | 1.5 weeks | Operator tooling. Can see system health, monitor plugins, debug issues. | Ops only |
| **9** | **CarPlay / Android Auto: Fuel Card** | 1 week | In-car experience. Drivers get fuel suggestion without touching phone. | Yes (seasonal, depends on iOS release schedule) |
| **10** | **ESP32 Display: Daily Tip + Goal Progress** | 1 week | Physical widget. Can show on desk or nightstand. | Yes (gift to early users) |

**Total MVP timeline:** ~15 weeks (~4 months) from start to full feature launch.

**Key milestones:**
- **Week 4:** Phase 1 + 2 complete → internal alpha, fuel recommendations working
- **Week 6:** Phase 3 complete → mobile app usable
- **Week 7:** Phase 4 complete → public SEO funnel launches
- **Week 10:** Phase 5 complete → grocery features added
- **Week 12:** Phase 6 complete → budget integration, savings tracking
- **Week 14:** Phase 7 complete → full web dashboard
- **Week 15:** Phases 8–10 complete → complete product

---

## 11. Open Design Decisions

These are live design questions worth debating. Unresolved decisions are flagged here; implementation should wait for consensus.

| Decision | Options | Pros & Cons | Current Lean | Blocker? |
|---|---|---|---|---|
| **Project name** | SaveStack vs SpendStack vs PennyStack vs StackSave vs [other] | SaveStack (catchy, domain available). SpendStack (less aspirational). PennyStack (generic). | SaveStack | No (can rebrand later) |
| **GraphQL vs REST for Core** | GraphQL (flexible client queries, strong typing) vs REST (simpler, fewer dependencies) | GraphQL: clients query only what they need (bandwidth), but adds complexity (resolver chains). REST: straightforward, cacheable, but requires API versioning for new fields. | Leaning GraphQL for plugin aggregation (clients can query fuel + grocery + budget in one call) | No (can start with REST, add GraphQL later) |
| **Single PostgreSQL vs per-service DB** | Single instance, separate schemas (simpler) vs one DB per service (true data isolation, polyglot) | Single: simpler ops, easier joins across services, but schema conflicts if services evolve independently. Per-service: true autonomy, but distributed joins hard, need event-driven sync. | Single instance for MVP (simpler, less ops burden) | No (can split later if needed) |
| **DoveConviene scraping frequency** | Daily vs weekly per chain | Daily: fresher data, but unnecessary (flyers are 7 days). Weekly: sufficient, less load on DoveConviene. | Weekly per chain | No (can tune based on user feedback) |
| **Product matching V2** | Fuzzy string matching (MVP) vs ML embeddings (SBERT, all-MiniLM) | Fuzzy: simple, fast, works for Italian products. ML: better for synonyms ("milk" vs "latte"), but needs training data. | Fuzzy for MVP, ML later | No (MVP ships with fuzzy) |
| **Multi-country support** | Italy-only (MVP) vs EU from start | Italy-only: faster to market, MIMIT + DoveConviene proven. EU: other countries have similar open fuel data (Germany, Spain, France) but require separate sources. | Italy-only MVP. Other countries in V1.1 | No (clear scope for MVP) |
| **Apple Developer Program ($99/yr)** | When to pay? Before iOS TestFlight? Before App Store? | Need to pay before submitting to App Store. TestFlight can work via ad-hoc provisioning in early stages. | Pay after Fuel plugin + Flutter app are solid (Week 6) | No (budget decision, not technical) |
| **Receipt scanning** | As additional grocery data source? | Pros: captures actual prices, helps with product matching. Cons: LLM + OCR cost ($$$), privacy concerns (users uploading images). | Deferred. V1.1 feature if user demand is high. | No (MVP ships without) |
| **Notifications platform** | Firebase Cloud Messaging vs OneSignal vs Expo Notifications | FCM: free, reliable, no extra vendor. OneSignal: richer UI but paid at scale. Expo: simple for Flutter but vendor lock-in. | FCM for MVP (free, reliable) | No (can switch later) |
| **Goal projection seasonality** | Static 3-month average (MVP) vs ML seasonality model | Static: simple, less code. ML: accounts for summer splurges, holiday spending. | Static for MVP. ML in V1.1 if needed. | No (MVP uses static) |

---

## Appendix: Key Formulas & Algorithms

### Fuel Savings Calculation

```
net_savings = (reference_price - candidate_price) × estimated_liters - (detour_km × consumption × fuel_price)

where:
  reference_price = avg price last 7 days at usual station
  candidate_price = today's price at candidate
  estimated_liters = tank_size - fuel_remaining
  detour_km = distance to candidate - distance to usual
  consumption = L/100km (user setting)
  fuel_price = current fuel price at candidate (EUR/L)
```

### Grocery Product Average Life

```
new_avg_life = (old_avg_life × min(purchase_count, 10) + actual_interval) / (min(purchase_count, 10) + 1)

Effect:
  - First 10 purchases: running average, responsive to new pattern
  - After 10 purchases: weight caps at 10, becomes less volatile
  - Habit changes (e.g., milk consumption up) reflected in 10–15 purchases
```

### Grocery Trip Optimization (Brute Force)

```
Input:
  items = [item_1, ..., item_n] (e.g., 30 items)
  stores = [store_1, ..., store_m] (e.g., 10 preferred)
  max_shops = k (1, 2, or 3)
  prices[item][store] = EUR (or null if not available)

Algorithm:
  1. Generate all combinations of ≤k stores from m stores: C(m, 1) + C(m, 2) + ... + C(m, k)
  2. For each combination:
     - For each item, assign to cheapest store in combination
     - Calculate total_cost = sum of all item prices
  3. Sort by total_cost (ascending)
  4. Return top 3 combinations (alternatives for user)

Complexity:
  C(10, 1) + C(10, 2) + C(10, 3) = 10 + 45 + 120 = 175 combinations
  175 × 30 items × 2 ops = 10,500 ops → <100ms
```

### Budget Goal Projection

```
monthly_savings_rate = (sum(income_last_3_months) - sum(expenses_last_3_months)) / 3

For each goal:
  remaining = target_amount - current_amount
  months_to_goal = ceiling(remaining / monthly_savings_rate)
  projected_completion_date = today + (months_to_goal × 1 month)
  
  delta = projected_completion_date - deadline
  if delta < 0:
    message = "You're {|delta|} ahead"
  elif delta > 0:
    message = "You're {delta} late at current pace"
  else:
    message = "On track"
```

---

## Appendix: Kafka Topic Schemas

For reference, here are the message schemas for each Kafka topic.

### core.discovery.ping

```json
{
  "id": "msg-uuid",
  "timestamp": "2026-04-09T12:00:00Z",
  "source": "core"
}
```

**Purpose:** Trigger plugin registration. Plugins hear this and respond via HTTP.

---

### core.plugin.settings_changed

```json
{
  "id": "msg-uuid",
  "timestamp": "2026-04-09T12:00:00Z",
  "source": "core",
  "plugin_id": "grocery-plugin",
  "user_id": "user-123",
  "settings": {
    "preferred_stores": ["lidl", "conad"],
    "max_shops_default": 2
  }
}
```

**Purpose:** Notify plugin of user's new settings. Plugin updates local cache.

---

### plugins.heartbeat

```json
{
  "id": "msg-uuid",
  "timestamp": "2026-04-09T12:00:30Z",
  "source": "fuel-plugin",
  "plugin_id": "fuel-plugin",
  "status": "UP",
  "version": "1.2.3",
  "metrics": {
    "uptime_seconds": 86400,
    "requests_processed": 12000,
    "errors_last_minute": 0
  }
}
```

**Purpose:** Heartbeat signal. Core writes to Redis.

---

### grocery.trip.completed

```json
{
  "id": "msg-uuid",
  "timestamp": "2026-04-09T18:30:00Z",
  "source": "grocery-plugin",
  "user_id": "user-123",
  "trip_id": "trip-456",
  "items_purchased": 28,
  "items_skipped": 2,
  "total_cost": 45.80,
  "estimated_cost": 43.30,
  "savings_vs_single_store": 12.50,
  "stores_visited": ["lidl", "conad"],
  "product_updates": [
    {
      "product_id": "p1",
      "purchase_date": "2026-04-09",
      "actual_price": 1.25,
      "expected_price": 1.20
    }
  ]
}
```

**Purpose:** Trip completed. Budget plugin listens to accumulate savings. Core sends notification.

---

### grocery.list.generated

```json
{
  "id": "msg-uuid",
  "timestamp": "2026-04-09T12:00:00Z",
  "source": "grocery-plugin",
  "user_id": "user-123",
  "items_count": 5,
  "estimated_total": 43.30,
  "best_trip": {
    "stores": ["lidl", "conad"],
    "total": 30.80,
    "savings": 12.50
  }
}
```

**Purpose:** Shopping list ready. Core sends "Your list is ready" push notification.

---

### fuel.alert.price_drop

```json
{
  "id": "msg-uuid",
  "timestamp": "2026-04-09T09:30:00Z",
  "source": "fuel-plugin",
  "user_id": "user-123",
  "station_id": 12345,
  "station_name": "Eni Milano Centrale",
  "fuel_type": "diesel95",
  "current_price": 1.649,
  "previous_price": 1.702,
  "price_drop_pct": 3.1,
  "estimated_savings": 2.50,
  "recommendation_id": "rec-789"
}
```

**Purpose:** Price drop alert. Core sends "Prices down! Save €2.50" notification.

---

### budget.goal.milestone

```json
{
  "id": "msg-uuid",
  "timestamp": "2026-04-09T12:00:00Z",
  "source": "budget-plugin",
  "user_id": "user-123",
  "goal_id": "goal-001",
  "goal_name": "House down payment",
  "milestone_pct": 50,
  "current_amount": 15000,
  "target_amount": 30000,
  "reached_at": "2026-04-09T12:00:00Z"
}
```

**Purpose:** Goal milestone reached (25%, 50%, 75%, 100%). Core sends celebratory notification.

---

## Appendix: User Journey Maps

### Journey 1: New User (Fuel-First)

```
Day 1: Sign up
  └─ Register email, password
  └─ Land on Angular dashboard (empty, onboarding)
  └─ Prompt: "Link your car?"
     └─ Enter: consumption (L/100km), tank size, commute points
     └─ Car profile saved
  └─ Discover plugins: see Fuel plugin available
  └─ Activate Fuel plugin
  └─ First recommendation appears in 1 min (prices already loaded)
  
Day 2-3: Start using Fuel
  └─ Daily: Check recommendation in app
  └─ First fill-up: user logs it (or auto-detects from location?)
  └─ See estimated savings after 3-4 fill-ups
  
Day 7: Onboarding for Grocery
  └─ Prompt: "Want to save on groceries too?"
  └─ Link Actual Budget (optional, for Budget plugin)
  └─ Add 5-10 products to inventory
  └─ Generate first shopping list
  └─ Complete first trip, see trip optimizer in action
  
Day 14: Multi-plugin experience
  └─ Fuel recommendations + Grocery shopping + Budget goal progress on dashboard
  └─ Get first "Savings Pulse" email: "You saved €40 this week"
  └─ User engaged, habit forming
```

### Journey 2: Power User (All Plugins)

```
Week 1-2: Onboarding (same as Journey 1)

Week 3: Budget setup
  └─ Link Actual Budget instance
  └─ Define goals: "House €30k by Jan 2029", "Holiday €5k by Aug 2026"
  └─ Budget plugin syncs transactions
  └─ See projections: "House on track, Holiday 2 months ahead"
  
Week 4+: Optimization loop
  └─ Monday: Check Fuel recommendation → fill up if savings > €2
  └─ Wednesday: Generate Grocery list → execute optimized trip → log prices
  └─ Friday: Check Budget pulse → see cumulative savings: "Fuel €50, Grocery €35 this month"
  └─ See goal progress updated in real-time
  
Month 2: Habits solidified
  └─ Mobile app on home screen
  └─ Fuel recommendation checked daily (95% engagement)
  └─ Grocery trip optimized weekly (saves 15–20 min + €10–15)
  └─ Budget plugin motivation: visual goal progress
  └─ User refers friend: "It's like a financial copilot"
```

---

**End of Document.**
