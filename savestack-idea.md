# SaveStack — Your Automatic Money Copilot

**A platform that helps young adults spend less without thinking about it.**

---

## Table of Contents

1. [The Vision](#the-vision)
2. [The Problem](#the-problem)
3. [The Solution](#the-solution)
4. [The Three MVP Plugins](#the-three-mvp-plugins)
   - [Fuel Plugin — "Should I fill up today?"](#1-fuel-plugin--should-i-fill-up-today)
   - [Grocery Plugin — "Your optimized shopping plan"](#2-grocery-plugin--your-optimized-shopping-plan)
   - [Budget Plugin — "Are you on track?"](#3-budget-plugin--are-you-on-track)
5. [Plugin Architecture](#plugin-architecture)
6. [User Surfaces](#user-surfaces)
7. [Tech Stack](#tech-stack)
8. [Data Sources](#data-sources-summary)
9. [What Makes This Different](#what-makes-this-different)
10. [MVP Build Order](#mvp-build-order)
11. [Open Questions](#open-questions)

---

## The Vision

SaveStack watches prices, learns your habits, and tells you when and where to act — across fuel, groceries, and personal budget goals. The core principle is elegantly simple:

> **The system does the thinking, you just live your life.**

No spreadsheets. No manual tracking. No obsessing over every euro. You connect your data sources once, and SaveStack gives you personalized, actionable advice that compounds small daily savings into big results over time.

---

## The Problem

Imagine you're a young adult with a normal job, saving for a house, wanting holidays, planning a family. Your brain runs a constant loop:

- "Where is fuel cheapest today?"
- "Should I fill up now or wait?"
- "Is this grocery item cheaper at another store?"
- "Am I saving enough this month?"
- "Can I afford that trip?"

**The tools exist. They just don't work together.**

| Problem | Current tooling |
|---------|-----------------|
| **Fuel pricing** | Apps like Prezzi Benzina show you 21,000 stations but don't know your route or your habits |
| **Budget tracking** | Tools like YNAB and Revolut tell you what you already spent — they don't help you spend less |
| **Grocery deals** | Apps like DoveConviene show promotions but don't know what YOU actually buy |
| **Unified view** | No single tool connects all three into a clear "are you on track?" picture |

Each tool solves one problem. None of them work together. And none of them take action on your behalf — they just show you options and hope you'll optimize yourself.

---

## The Solution

A **plugin-based platform** where:

- The **core** handles users, settings, notifications, and a universal recommendation engine
- Each **plugin** independently ingests data, computes recommendations, and communicates via events
- All data sources are **free** — no paid APIs required for the MVP
- The user interacts through **mobile (Flutter)**, **web dashboard (Next.js/Angular)**, and eventually **CarPlay/Android Auto** and an **ESP32 physical display**

The architecture is deliberately modular. This isn't just a technical choice — it's a design principle. Every plugin can be developed, tested, and deployed independently. New money-saving domains can be added without touching the core system.

```
┌─────────────────────────────────────────────────────────┐
│                    CORE PLATFORM                        │
│                                                         │
│  • User & Auth          • Notification Engine           │
│  • Settings             • Event Bus (Kafka)             │
│  • Plugin Registry      • Recommendation Aggregator     │
│  • GraphQL Gateway      • Plugin Lifecycle Manager      │
└──────────────────────────┬──────────────────────────────┘
                           │
                   [Plugin SPI Contract]
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
   ┌────▼────┐      ┌──────▼──────┐    ┌────▼─────┐
   │  FUEL   │      │   GROCERY   │    │  BUDGET  │
   │ Plugin  │      │   Plugin    │    │  Plugin  │
   │         │      │             │    │          │
   │ MIMIT   │      │ DoveConviene│    │ Actual   │
   │ CSV     │      │ JSON-LD +   │    │ Budget   │
   │ (free)  │      │ purchase    │    │ API      │
   │         │      │ history     │    │ (free)   │
   └─────────┘      │ (free)      │    │          │
                    └─────────────┘    └──────────┘
```

---

## The Three MVP Plugins

### 1. Fuel Plugin — "Should I fill up today?"

#### Data source: MIMIT (Italian Ministry mandate)

MIMIT provides comprehensive Italian fuel data as a free, open dataset:
- Every fuel station in Italy: location, brand, GPS coordinates
- Every price category: Benzina, Gasolio, GPL, Metano, self-service/attended
- Fresh timestamps on daily updates (8:00 AM)
- Direct CSV download under IODL 2.0 license (commercial use OK)
- 21,000+ stations covering the entire country

> **Why this works:** This data is mandated by Italian law (Art. 51 L. 99/2009), refreshed daily, and completely free. No API keys, no rate limits, no licensing fees. Perfect for an MVP.

#### What it does

The fuel plugin isn't just a prettier map of nearby stations. It **understands your economics**.

You set up once:
- Your car's consumption (L/100km)
- Your tank size
- Your usual commute route

The plugin then:
1. **Filters intelligently** — narrows 21,000 stations to ~30 along your route corridor
2. **Calculates net savings** — price difference *minus detour cost*. A €0.05/L discount means nothing if it's 20km out of your way
3. **Learns patterns** — research shows fuel prices follow weekly cycles. Monday/Tuesday tend to be cheapest; Friday/Saturday most expensive
4. **Gives one clear action**, not a list:

> **Example recommendations:**
> - *"Fill up at IP Via Roma today — €1.749/L — save €4.20 vs your usual"*
> - *"Wait — your usual station tends to drop prices on Tuesdays and you have fuel for 2 more days"*
> - *"Prices are average today. Fill up wherever convenient"*

**User effort:** Zero after initial setup.

---

### 2. Grocery Plugin — "Your optimized shopping plan"

#### Data sources

The grocery plugin draws from multiple free, complementary sources:

| Source | What we get | Legal basis |
|--------|------------|-------------|
| **DoveConviene (ShopFully)** | Product names + prices in schema.org/Product format, embedded in public flyer HTML | SEO-public structured data; ~100-200 HTTP requests/week covers all major chains |
| **Open Food Facts** | Product identity by barcode | Open Database License; no auth, no rate limit |
| **User's purchase history** | Tap-to-buy during shopping + receipt scanning | User's own data |

Together, these sources create a complete picture: what you buy, how often, and when it's on sale.

#### The shopping cycle — a real user story

> **Why this matters:** The grocery plugin doesn't just show you flyers. It learns your shopping patterns, proposes what you need, finds the best prices across nearby stores, and then guides you through the actual shopping trip. By the end, you've saved money and the system knows more about your habits for next time.

##### Step 1: System proposes a shopping list

Each product in your inventory has an **average life** (set by you, refined over time). The system computes what's due or overdue:

```
Proposed Shopping List (Apr 12)

  [x] Olio EVO 1L          due in 2 days
  [x] Pasta De Cecco 500g  overdue by 1 day
  [x] Mozzarella           due today
  [x] Pomodori Pelati      due in 3 days
  [ ] Lavazza Oro          not due for 12 days

  [+ Add item]
```

The user glances at this and thinks: "Looks right." It took seconds.

##### Step 2: User reviews and adjusts

- Uncheck items not needed this time
- Add items the system didn't predict (dinner guests, special meal planned)
- Choose **max number of shops to visit**: 1, 2, or 3 (controls shopping friction)

##### Step 3: System optimizes the plan

The plugin fetches current promotional prices from DoveConviene for each item at nearby stores. It then solves a constrained optimization problem:

```
Minimize:   total cost
Subject to: max N shops
            store accessibility
Secondary:  prefer fewer stops
```

For small datasets (10–30 items, 5–10 stores), brute-force works perfectly: thousands of comparisons run in milliseconds.

Result:

```
Optimized Plan (max 2 shops)

  STOP 1: Eurospin (Via Roma)
    Olio EVO 1L ........... €3.99 (promo -27%)
    Pomodori Pelati ....... €0.59
    Subtotal: €4.58

  STOP 2: Conad (Via Larga)
    Pasta De Cecco 500g ... €1.09
    Mozzarella ............ €2.29
    Subtotal: €3.38

  TOTAL: €7.96
  vs all-Conad: €9.40 — SAVE €1.44

  [Start Shopping]
```

This isn't just cheaper—it's *personalized*. The system found that *you* can save €1.44 by going to two stores rather than one, and that's worth your time.

##### Step 4: In-store shopping mode

You're shopping. For each item, tap to confirm you picked it up. If an item isn't found or the price differs from the plan:
- Skip it
- Substitute a different product
- Note the actual price (feeds back into future recommendations)

##### Step 5: Feedback loop and learning

After shopping:
- The system records purchase dates per product
- Recalculates average product life using a weighted running average:
  ```
  new_avg_life = (old_avg_life × purchase_count + actual_interval) 
                 / (purchase_count + 1)
  ```
  Weight caps at ~10 to stay responsive to habit changes
- Trip data feeds into the budget plugin (actual spend + savings tracked)
- Over time, the predicted shopping list becomes eerily accurate

**User effort:** Review the proposed list before shopping (~1 min). Tap items during shopping (~1 sec per item). Everything else is automatic.

---

### 3. Budget Plugin — "Are you on track?"

#### Data source: Actual Budget (open source, self-hosted)

Actual Budget is a privacy-first budget app with a complete REST API:
- Open source, self-hosted (you own your data)
- Full API with Node.js and Python clients
- Reads transactions, accounts, categories, budgets
- Bank sync via GoCardless/SimpleFIN for EU banks

> **Why self-hosted?** Because you might be saving for something private. The architecture respects that.

#### What it does

The budget plugin syncs with your Actual Budget instance and answers one question:

**"Are you on track?"**

Concretely:

1. **Goal projections** — tracks your savings targets (house down payment, holiday fund, emergency fund) and projects forward:
   - *"At your current pace, you'll reach your house goal in 2 years and 10 months"*
   - *"You're 2 months ahead on your holiday fund — you can afford that weekend trip"*

2. **Aggregates savings across all plugins:**
   - *"This month you saved €18 on fuel and €23 on groceries. Year to date: €312"*
   - Shows the actual impact of following SaveStack recommendations

3. **Monthly "savings pulse" digest** — not nagging, just a clear snapshot:
   - Your savings rate
   - Month-over-month comparison
   - Goal progress toward each target

**User effort:** Zero. It syncs automatically with your Actual Budget instance.

---

## Plugin Architecture

### The Contract

Every plugin implements the same interface:

| Method | Purpose |
|--------|---------|
| `descriptor()` | name, version, category (fuel, grocery, budget, etc.) |
| `dataSources()` | what external data it consumes, freshness, license |
| `getRecommendations(user)` | returns universal `Recommendation` objects |
| `userSettings()` | plugin-specific configuration (e.g., car details, store preferences) |
| `subscribedEvents()` | which Kafka topics it listens to |
| `onEvent(event)` | react to platform or cross-plugin events |

This contract is **deliberately minimal**. A new plugin can be added without modifying core code.

### Communication

Plugins don't call each other directly. They communicate through **Kafka events**:

```
fuel.recommendation.generated 
  ↓
  [Core notification engine]
  ↓
  Push to user's phone, email, CarPlay

grocery.trip.completed
  ↓
  [Budget plugin listens]
  ↓
  Records spending + calculates savings

budget.goal.milestone
  ↓
  [Core notification engine]
  ↓
  Celebration notification
```

### Data isolation

Each plugin gets its own **PostgreSQL schema**. The core can read aggregated queries, but plugin data stays isolated. Shared **Redis** cache reduces external API calls.

---

## User Surfaces

SaveStack reaches the user through multiple surfaces, each optimized for a specific context:

| Surface | Tech | Primary use | Trigger |
|---------|------|-------------|---------|
| **Mobile app** | Flutter | Push notifications, shopping mode, quick glance at recommendations, receipt/flyer scanning | Daily habit |
| **Web dashboard** | Next.js (public) + Angular (private) | Full analytics, goal projections, price trends, settings, historical data | Weekly deep-dive |
| **CarPlay / Android Auto** | Native API | "Cheapest fuel on your route" card while driving | Real-time while commuting |
| **ESP32 e-ink display** | ESP32 + MQTT | Physical desk/fridge widget: today's tip, goal progress bar | Ambient awareness |

The MVP launches with **mobile app + web dashboard**. CarPlay and ESP32 are Phase 2.

---

## Tech Stack

| Component | Technology | Role | Why |
|-----------|------------|------|-----|
| **Core API** | Spring Boot | Plugin registry, user/auth, recommendation engine, webhook receivers, scheduling | Battle-tested, great plugin model support |
| **ML / scraping** | FastAPI | DoveConviene scraper, price prediction (V2), product matching | Fast iteration, Python ecosystem for ML |
| **Event bus** | Apache Kafka | Price feed ingestion, cross-plugin events, notification pipeline | Scales to thousands of users; enables async |
| **Primary database** | PostgreSQL | All persistent data, one schema per plugin | Proven, schema isolation support |
| **Cache** | Redis | Current prices, active session state, route cache | Sub-millisecond lookups |
| **API layer** | GraphQL | Flexible frontend queries across all plugins | Clients fetch exactly what they need |
| **Public web** | Next.js | SEO-friendly pages, fuel price explorer | Static generation, fast Core Web Vitals |
| **Private dashboard** | Angular | Personal analytics, goals, detailed settings | Rich UI for power users |
| **Mobile** | Flutter | Primary interface, push notifications, shopping mode, camera for receipts | Single codebase for iOS + Android |
| **Car integration** | CarPlay / Android Auto | Fuel suggestion while driving | Native integrations, safety-first |
| **IoT** | ESP32 + MQTT | Physical savings display / goal progress widget | Low power, simple MQTT protocol |
| **Deployment** | Docker + Kubernetes | Containerized microservices | Scales from laptop to cloud |
| **Observability** | Prometheus + Grafana | Scraper health, data freshness, system metrics | Know what's actually happening in prod |
| **Testing** | JUnit, Pytest, Jest, Cypress | Backend, ML, frontend, E2E | Each layer tested in its native framework |

---

## Data Sources Summary

All three MVP plugins rely exclusively on **free** data sources. This is non-negotiable for the MVP.

| Source | Data provided | Cost | Legal basis | Update frequency |
|--------|----------------|------|-------------|------------------|
| **MIMIT** | All Italian fuel stations + prices (Benzina, Gasolio, GPL, Metano) | Free | IODL 2.0 open license, mandated by Art. 51 L. 99/2009 | Daily (8:00 AM UTC) |
| **DoveConviene** | Supermarket promotions: product names + prices in JSON-LD schema.org/Product | Free | Structured data embedded in public HTML for SEO purposes | Weekly per chain (~100-200 HTTP requests/week) |
| **Open Food Facts** | Product identity by barcode (EAN/GTIN) + nutrition | Free | Open Database License (ODbL), no authentication, no rate limits | Continuous (crowd-sourced) |
| **Actual Budget** | User's transactions, budgets, categories, accounts | Free | Open source software, self-hosted, user's own data | On-demand sync via REST API |

> **Legal note on DoveConviene:** The data is promotional (public flyers), embedded in HTML for SEO, and we're extracting structured data that's already public. Learning project risk is very low.

---

## What Makes This Different

The market has tools. SaveStack has **judgment**.

| What existing tools do | What SaveStack does |
|------------------------|-------------------|
| "Here are 21,000 gas stations" | "Fill up at THIS one, on YOUR route, TODAY — save €4.20" |
| "Here's a flyer with 80 products" | "3 items from YOUR shopping list are on promo at Eurospin this week" |
| "You spent €380 on groceries" | "You saved €23 on groceries this month by following the optimized plans" |
| Requires browsing, comparing, deciding | Pushes ONE recommendation when YOU should act |
| Separate apps for fuel, groceries, budget | One platform, three plugins, unified savings view |
| Data goes to a mysterious server | Data stays with you (self-hosted option available) |

**The fundamental difference:** SaveStack doesn't just inform. It decides.

---

## MVP Build Order

Each phase delivers a working product. Users can benefit at every step.

1. **Phase 1: Core platform**
   - User registration and JWT auth
   - Plugin registry and lifecycle management
   - Basic recommendation aggregator
   - GraphQL API gateway
   - Kafka event bus setup

2. **Phase 2: Fuel plugin + notifications**
   - MIMIT data ingestion (daily refresh)
   - Route-aware fuel price analysis
   - Cheapest-on-route recommendation logic
   - Push notifications (APNS, FCM)

3. **Phase 3: Flutter mobile app**
   - Show fuel recommendations
   - Fuel price map view (optional)
   - Notification handling
   - Navigation to recommended station

4. **Phase 4: Grocery plugin**
   - DoveConviene scraper (Python/FastAPI)
   - Product inventory with purchase history
   - Shopping list generation (due date prediction)
   - Store optimization algorithm (constraint solver)
   - In-store tap-to-buy mode

5. **Phase 5: Budget plugin**
   - Actual Budget API sync
   - Goal tracking and projections
   - Savings aggregation from fuel + grocery
   - Monthly savings pulse digest

6. **Phase 6: Web dashboard**
   - Public marketing site (fuel price trends, calculator)
   - Private dashboard (analytics, goal projections, settings)
   - Historical data views, trend charts

7. **Phase 7+: CarPlay integration**
   - Native CarPlay integration (requires Apple Developer Program, $99/year)
   - Fuel suggestions as driving context cards

8. **Phase 8+: ESP32 e-ink display**
   - Physical widget showing today's top recommendation + goal progress
   - MQTT connection to core platform

---

## Open Questions

These are the decisions that will shape the product beyond the MVP:

### Name

"SaveStack" is our working title. Other candidates:
- "SpendStack" — emphasize the behavioral shift
- "StackSave" — easier to remember?
- "PennyStack" — cute but might undersell the impact

### Multi-country expansion

MIMIT is Italy-only. Other EU countries have equivalent fuel data APIs (sometimes also government-mandated). How generalizable should the plugin architecture be? Should the MVP launch Italy-only and expand later, or design for multi-country from the start?

### Privacy-first cloud vs. self-hosted

The current design assumes self-hosted Actual Budget. Should SaveStack offer a cloud-hosted option? If so, what privacy guarantees?
- Encryption at rest
- Encrypted in transit
- Data retention policies
- GDPR compliance (EU-only service initially?)

### Community features

Should users be able to share price observations? E.g.:
- "I filled up at IP Via Roma on Apr 9 at €1.749"
- Community contributes data to a "ground truth" price feed between MIMIT updates
- Grocery plugin: crowd-sourced price observations fill gaps in DoveConviene data

Risk: Privacy, spam, trust. Reward: Real-time price accuracy.

### Monetization

The fuel and grocery data are free. The intelligence layer is the value. Options:
- **Open source core + self-hosted** — zero friction, rely on donations or consulting
- **Freemium cloud service** — free tier (basic recommendations), paid tier (advanced analytics, CarPlay, ESP32 support)
- **Premium data services** — sell anonymized trends to retailers (later, if community buys in)

The MVP should assume **free, open source** to focus on building the right product, not the right business model.

---

**Next step:** Pick a phase and start building. The architecture supports iterating each plugin in isolation. Feedback and improvements compound over time.
