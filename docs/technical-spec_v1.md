# wild_economy — Repo-Ready Technical Spec (v1)

## Status

This document is the **current implementation-stage source of truth** for `wild_economy` v1.

It reflects the locked direction to date:

* **Exchange-first, likely Exchange-only**
* **Player-stocked** is the default item mode
* **Unlimited buy-only** is a narrow exception
* **Disabled** protects progression and server identity
* **Buying is GUI-driven**
* **Selling is command-driven**
* Prices are sourced from **Essentials `worth.yml`**, imported into an **internal catalog**
* Player-stocked items use:

  * stock cap
  * background turnover/drain
  * smooth sell-price taper as stock fills

---

## 1. Product and naming conventions

### Plugin identity

* **Plugin/project name:** `wild_economy`
* **Java package root:** `com.splatage.wild_economy`
* **Player-facing base command:** `/shop`

### Internal terminology

Use **Shop** for player-facing command and menu language.
Use **Exchange** internally for the business/domain model.

Examples:

* Player command: `/shop sellall`
* Internal service: `ExchangeSellService`
* Internal menu class: `ExchangeBrowseMenu`

This keeps the player UX intuitive while preserving clean domain language in code.

---

## 2. Scope boundaries

## Included in v1

* Curated Exchange item catalog
* Item policy system
* `PLAYER_STOCKED` items
* `UNLIMITED_BUY` items
* `DISABLED` items
* Stock caps for player-stocked items
* Background stock turnover/drain
* Stock-sensitive sell-price taper
* Stable, standardized buy pricing
* Immediate payout on sell
* GUI browsing and buying
* Command-driven selling
* SQLite support
* MySQL/MariaDB support
* Transaction logging sufficient for support/debugging

## Excluded from v1

* Marketplace as a core system
* Auctions
* Bids/offers/negotiation
* Dynamic buy-side market pricing
* Public works integration
* Fuzzy item matching
* Broad support for every item by default
* Progression-sensitive items in easy circulation
* Large admin dashboards / analytics suites

---

## 3. Core design rules

1. **Support gameplay; do not shortcut it.**
2. **Reward useful player production.**
3. **Keep item circulation intentional and policy-driven.**
4. **Keep buy-side predictable.**
5. **Let sell-side respond to saturation in a controlled way.**
6. **Keep GUI thin.**
7. **Keep item normalization strict.**
8. **No blocking database work in GUI click paths.**
9. **No raw SQL in service classes.**
10. **No Bukkit `Player` leakage into repositories.**

---

## 4. Item policy model

## `PLAYER_STOCKED`

Default and primary mode.

Used for whitelisted, standardized bulk goods, especially useful farm outputs and server-useful materials.

Behavior:

* Players can sell these items to the Exchange
* Players are paid immediately
* Sold items become Exchange stock
* Other players can buy from that stock

Purpose:

* Reward useful production
* Tie trade to real supply
* Circulate practical goods without conjuring stock

## `UNLIMITED_BUY`

Narrow exception mode.

Used for nuisance or world-damaging materials where the design goal is to reduce ugly harvesting and landscape damage.

Behavior:

* Players can buy these items
* They are not part of the normal sell-back loop
* Supply is server-provided by policy

Likely examples:

* sand
* red sand
* ice
* logs

## `DISABLED`

Used for progression-sensitive or shortcutting items.

Behavior:

* Not buyable
* Not sellable

Purpose:

* Preserve progression
* Keep rare/important items out of easy circulation

---

## 5. Stock model

All `PLAYER_STOCKED` items use a finite stock buffer.

Each catalog entry may define:

* `stock-cap`
* `turnover-amount-per-interval`
* stock-state thresholds
* sell-price bands

### Behavior

* Player sells fill the stock buffer
* Player purchases drain the stock buffer quickly
* Background turnover drains the stock buffer slowly over time

### Design intent

This acts like a **buffered / leaky-bucket stock model**:

* bursty production can be absorbed
* player demand drains stock naturally
* background turnover reopens room even if player buy demand is weak
* stock does not remain permanently saturated

### Important interpretation

Background turnover is **not merely cleanup**.
It is a deliberate form of **passive server-side consumption / turnover**.

---

## 6. Pricing model

## Source of truth

Use Essentials `worth.yml` as the base standardized value source.

At startup/reload:

* import values from `worth.yml`
* merge them with explicit item config
* build an internal Exchange catalog

Runtime logic must use the internal catalog, not poke through `worth.yml` repeatedly.

## Buy pricing

Buy prices must remain:

* standardized
* stable
* predictable

Buy-side pricing should **not** behave like a live market simulator.

## Sell pricing

Sell prices taper smoothly downward as stock fills.

Meaning:

* low stock = better sell value
* high stock = reduced sell value
* near full stock = low but still valid sell value if selling is still enabled

This should be implemented as **simple stock-fill bands** in v1.

### Recommended v1 bands

Example only:

* 0.00–0.25 fill → 100%
* 0.25–0.50 fill → 85%
* 0.50–0.75 fill → 65%
* 0.75–0.90 fill → 40%
* 0.90–1.00 fill → 20%

This is intentionally simpler and more explainable than a formula-heavy curve.

---

## 7. Player interaction model

## Buying

Buying is GUI-driven.

Player flow:

1. `/shop`
2. Browse categories
3. Browse items
4. Open item detail
5. Select quantity
6. Confirm buy

The GUI should show:

* item name
* buy price
* stock state
* available stock

## Selling

Selling is command-driven.

Primary commands:

* `/shop sellhand`
* `/shop sellall`

This is deliberate because selling is a high-frequency action and GUI selling would be clunkier than command use.

### Sell UX requirements

Sell feedback must clearly report:

* what sold
* how much was earned
* which items were skipped
* whether any items sold at reduced value due to saturation

Nothing should be silently lost or skipped.

---

## 8. Package layout

```text
com.splatage.wild_economy
├── WildEconomyPlugin.java
├── bootstrap
│   ├── PluginBootstrap.java
│   ├── ServiceRegistry.java
│   └── LifecycleCoordinator.java
├── command
│   ├── ShopCommand.java
│   ├── ShopSellHandSubcommand.java
│   ├── ShopSellAllSubcommand.java
│   ├── ShopOpenSubcommand.java
│   ├── ShopAdminCommand.java
│   ├── ShopAdminReloadSubcommand.java
│   ├── ShopAdminStockSubcommand.java
│   ├── ShopAdminTurnoverSubcommand.java
│   └── ShopAdminQuoteSubcommand.java
├── config
│   ├── GlobalConfig.java
│   ├── DatabaseConfig.java
│   ├── ExchangeItemsConfig.java
│   ├── WorthImportConfig.java
│   ├── MessagesConfig.java
│   ├── ConfigLoader.java
│   └── ConfigValidator.java
├── economy
│   ├── EconomyGateway.java
│   ├── VaultEconomyGateway.java
│   └── EconomyResult.java
├── exchange
│   ├── catalog
│   │   ├── ExchangeCatalog.java
│   │   ├── ExchangeCatalogEntry.java
│   │   ├── CatalogLoader.java
│   │   ├── WorthImporter.java
│   │   └── CatalogMergeService.java
│   ├── domain
│   │   ├── ItemKey.java
│   │   ├── ItemPolicyMode.java
│   │   ├── ItemCategory.java
│   │   ├── StockState.java
│   │   ├── SellPriceBand.java
│   │   ├── StockSnapshot.java
│   │   ├── BuyQuote.java
│   │   ├── SellQuote.java
│   │   ├── SellLineResult.java
│   │   ├── SellHandResult.java
│   │   ├── SellAllResult.java
│   │   ├── BuyResult.java
│   │   ├── RejectionReason.java
│   │   └── TransactionType.java
│   ├── item
│   │   ├── ItemNormalizer.java
│   │   ├── ItemValidationService.java
│   │   ├── ValidationResult.java
│   │   ├── CanonicalItemRules.java
│   │   └── ItemSerializationService.java
│   ├── pricing
│   │   ├── PricingService.java
│   │   ├── SellPriceCurve.java
│   │   └── PriceFormatter.java
│   ├── stock
│   │   ├── StockService.java
│   │   ├── StockTurnoverService.java
│   │   ├── StockStateResolver.java
│   │   └── StockServiceImpl.java
│   ├── service
│   │   ├── ExchangeService.java
│   │   ├── ExchangeServiceImpl.java
│   │   ├── ExchangeBrowseService.java
│   │   ├── ExchangeBuyService.java
│   │   ├── ExchangeSellService.java
│   │   └── TransactionLogService.java
│   └── repository
│       ├── ExchangeStockRepository.java
│       ├── ExchangeTransactionRepository.java
│       ├── SchemaVersionRepository.java
│       ├── sqlite
│       │   ├── SqliteExchangeStockRepository.java
│       │   ├── SqliteExchangeTransactionRepository.java
│       │   └── SqliteSchemaVersionRepository.java
│       └── mysql
│           ├── MysqlExchangeStockRepository.java
│           ├── MysqlExchangeTransactionRepository.java
│           └── MysqlSchemaVersionRepository.java
├── gui
│   ├── ShopMenuRouter.java
│   ├── MenuSession.java
│   ├── ExchangeRootMenu.java
│   ├── ExchangeCategoryMenu.java
│   ├── ExchangeBrowseMenu.java
│   ├── ExchangeItemDetailMenu.java
│   └── MenuText.java
├── persistence
│   ├── DatabaseProvider.java
│   ├── DatabaseDialect.java
│   ├── ConnectionFactory.java
│   ├── MigrationManager.java
│   ├── TransactionRunner.java
│   └── JdbcUtils.java
├── scheduler
│   ├── StockTurnoverTask.java
│   └── AsyncExecutor.java
└── util
    ├── MoneyFormatter.java
    ├── InventorySnapshot.java
    ├── Preconditions.java
    └── TimeProvider.java
```

---

## 9. Mandatory domain objects

## `ItemKey`

Canonical Exchange key.

Example values:

* `minecraft:wheat`
* `minecraft:cactus`
* `minecraft:oak_log`

This must be strict. No fuzzy matching in v1.

## `ItemPolicyMode`

Initial v1 enum:

* `PLAYER_STOCKED`
* `UNLIMITED_BUY`
* `DISABLED`

`SELL_ONLY` is intentionally left out of the core v1 path unless later reintroduced deliberately.

## `ExchangeCatalogEntry`

This is the central runtime config object.

Each entry must capture:

* item key
* display name
* category
* policy mode
* base worth
* buy price
* sell price
* stock cap
* turnover amount per interval
* sell-price bands
* buy enabled
* sell enabled

## `StockSnapshot`

Used by pricing and GUI view logic.

Should include:

* item key
* current stock
* stock cap
* fill ratio
* stock state

## `BuyQuote` / `SellQuote`

Structured pricing outputs.

Sell quote must include:

* base unit price
* effective unit price
* total payout
* fill ratio
* whether tapering was applied

## Result models

Use structured result objects rather than booleans.

At minimum:

* `SellHandResult`
* `SellAllResult`
* `BuyResult`
* `SellLineResult`

---

## 10. Service list and responsibilities

## `CatalogService`

Responsibilities:

* load internal catalog from config + worth import
* expose catalog entries by key and category
* provide stable runtime item definitions

## `ItemValidationService`

Responsibilities:

* validate whether a held/inventory item is Exchange-eligible
* normalize item stacks to canonical `ItemKey`
* return clean rejection reasons

## `PricingService`

Responsibilities:

* compute buy quotes
* compute sell quotes
* apply stock saturation sell taper
* keep buy-side pricing stable

## `StockService`

Responsibilities:

* load stock snapshots
* increment stock on sell
* decrement stock on purchase
* enforce stock cap rules
* resolve stock state for GUI

## `ExchangeSellService`

Responsibilities:

* implement `/shop sellhand`
* implement `/shop sellall`
* validate sellable items
* quote payouts
* remove sold items
* pay player
* add to stock
* log transactions

## `ExchangeBuyService`

Responsibilities:

* implement purchase flow
* verify catalog/policy
* verify stock
* verify eco balance
* withdraw eco
* give item(s)
* decrement stock
* log transactions

## `ExchangeService`

High-level facade for command and GUI use.

Responsibilities:

* browse category
* get item view
* sell hand
* sell all
* buy

## `StockTurnoverService`

Responsibilities:

* perform scheduled stock turnover pass
* reduce stock by configured amount
* clamp at zero
* optionally log turnover summary

## `TransactionLogService`

Responsibilities:

* record sell, buy, and turnover activity
* provide enough data for later support/debugging

## `EconomyGateway`

Responsibilities:

* abstract Vault economy operations
* deposit
* withdraw
* get balance

---

## 11. Repository interfaces

## `ExchangeStockRepository`

Must support:

* get stock by item key
* batch get stocks
* increment stock
* decrement stock
* set stock directly for admin/task usage

## `ExchangeTransactionRepository`

Must support:

* insert transaction record

This can stay lean in v1.

## `SchemaVersionRepository`

Must support:

* get current schema version
* set current schema version

---

## 12. Data model / schema draft

## `exchange_stock`

Purpose:

* persistent stock counts for player-stocked items

Fields:

* `item_key`
* `stock_count`
* `updated_at`

## `exchange_transactions`

Purpose:

* audit/debug logging for sell, buy, turnover

Fields:

* `transaction_id`
* `transaction_type`
* `player_uuid`
* `item_key`
* `amount`
* `unit_price`
* `total_value`
* `created_at`
* `meta_json` nullable

## `schema_version`

Purpose:

* migration tracking

Fields:

* `version`
* `applied_at`

This is intentionally minimal for v1.

---

## 13. Command specification

## Player commands

* `/shop` → open Exchange GUI
* `/shop sellhand`
* `/shop sellall`

Optional later:

* `/shop buy` alias to open GUI
* `/shop worth` if helpful

## Admin commands

* `/shopadmin reload`
* `/shopadmin stock get <itemKey>`
* `/shopadmin stock set <itemKey> <amount>`
* `/shopadmin turnover run`
* `/shopadmin quote <itemKey> <amount>`

Admin surface should remain lean.

---

## 14. GUI specification

## `ExchangeRootMenu`

Purpose:

* entry point
* category selection

Recommended buttons:

* Farming
* Mining
* Mob Drops
* Building
* Utility
* Close

## `ExchangeBrowseMenu`

Purpose:

* paged item view within one category

Each item tile should show:

* icon
* display name
* buy price
* stock state
* maybe quantity available

## `ExchangeItemDetailMenu`

Purpose:

* confirm buy flow

Should show:

* icon
* display name
* buy price
* stock quantity/state
* quantity controls
* confirm button
* back button

Do **not** include sell GUI in v1.

---

## 15. Buy flow rules

1. Player opens `/shop`
2. GUI requests category view from `ExchangeService`
3. Service returns item views containing:

   * item key
   * display name
   * buy price
   * stock count
   * stock state
4. Player selects an item
5. GUI opens item detail menu
6. Player confirms quantity
7. `ExchangeBuyService`:

   * validates policy
   * validates stock
   * validates player balance
   * withdraws eco
   * decrements stock
   * grants item
   * logs purchase
8. GUI refreshes and shows result

Requirements:

* no negative stock
* no duplicate grants
* no balance withdrawal without successful item grant path

---

## 16. Sell flow rules

## `/shop sellhand`

1. Read held item
2. Validate item against Exchange policy
3. Normalize to `ItemKey`
4. Load current stock snapshot
5. Compute stock-aware sell quote
6. Remove sold quantity
7. Pay player immediately
8. Add sold quantity to stock
9. Log transaction
10. Send clear message

## `/shop sellall`

1. Snapshot inventory on main thread
2. Validate each stack
3. Group valid stacks by `ItemKey`
4. Load stock snapshots
5. Compute quotes using current stock fullness
6. Remove only sold quantities
7. Deposit combined payout
8. Add sold amounts to stock
9. Log transactions
10. Send summary

Summary must include:

* sold items
* total eco earned
* skipped items
* tapered-value items where relevant

Never silently drop or skip items.

---

## 17. Turnover rules

Background turnover is implemented by a scheduled task.

### v1 recommendation

Use **fixed amount per interval**, not percentage, for simplicity.

Each turnover pass:

* iterate `PLAYER_STOCKED` catalog entries
* load current stock
* subtract configured turnover amount
* clamp at zero
* persist new stock
* optionally log turnover amount

This is easier to reason about and tune than percentage-based decay.

---

## 18. Config files

## `config.yml`

General runtime settings.

Must include:

* turnover interval
* GUI page size
* command aliases if needed

## `database.yml`

Must include:

* backend type: sqlite/mysql
* sqlite file path
* mysql host/port/database/credentials/ssl

## `worth-import.yml`

Must include:

* path to Essentials `worth.yml`
* import enable/disable
* precedence rules for config overrides

## `exchange-items.yml`

This is the key config file.

Each item entry must define:

* display name
* category
* policy
* buy enabled
* sell enabled
* stock cap
* turnover amount per interval
* buy price
* sell price
* sell-price bands

This file should remain explicit, even if worth import provides most numeric defaults.

---

## 19. First-pass item policy list

This is a **recommended starter catalog**, not yet a permanently locked final list.

## `PLAYER_STOCKED` — likely include

### Farming

* wheat
* carrots
* potatoes
* beetroot
* sugar cane
* cactus
* bamboo
* kelp
* melon slices
* pumpkin
* wool
* eggs

### Mining / bulk materials

* cobblestone
* stone
* deepslate
* granite
* diorite
* andesite
* coal
* copper ingots
* redstone dust

### Mob drops

* rotten flesh
* bones
* string
* spider eyes

## Review-needed for `PLAYER_STOCKED`

* iron ingots
* slimeballs
* gunpowder
* lapis lazuli
* arrows
* honey bottles
* honeycomb

These are plausible, but need explicit balancing judgment.

## `UNLIMITED_BUY` — likely include

* sand
* red sand
* ice
* oak_log
* birch_log
* spruce_log
* jungle_log
* acacia_log
* dark_oak_log
* mangrove_log
* cherry_log

## Review-needed for `UNLIMITED_BUY`

* packed ice
* gravel
* glass

## `DISABLED` — likely include

* diamonds
* emeralds
* diamond tools
* diamond armor
* enchanted books
* enchanted gear
* nether stars
* beacons
* totems of undying
* elytra
* shulker boxes
* ancient debris
* netherite ingots
* netherite gear
* rare trims
* custom/plugin items by default

---

## 20. Build order

## Phase 1 — foundation

* plugin bootstrap
* config loading
* DB abstraction
* migration manager
* repository interfaces
* Vault gateway

## Phase 2 — catalog and item identity

* worth import
* item config loading
* internal catalog build
* strict item normalization
* validation service

## Phase 3 — selling

* `/shop sellhand`
* `/shop sellall`
* stock add
* payout
* transaction logging
* stock-aware sell taper

## Phase 4 — buying GUI

* `/shop`
* category browsing
* item detail view
* buy flow
* stock decrement

## Phase 5 — turnover and admin

* turnover task
* admin commands
* reload support
* quote support
* message polish

---

## 21. Hard implementation constraints

* GUI classes must not contain pricing logic
* GUI classes must not contain SQL
* Service classes must not talk to raw JDBC directly
* Repository layer must not depend on Bukkit player objects
* Buy prices must remain stable and explicit
* Sell taper must come only from configured stock bands
* No fuzzy or “smart guess” item matching in v1
* No blocking DB work in GUI click paths
* Keep config and domain model boring and explicit

---

## 22. Minimal acceptance criteria

## Selling

* `/shop sellhand` works for eligible items
* `/shop sellall` sells only eligible items
* payouts are correct
* stock increments are correct
* saturation taper is applied correctly
* feedback messages are clear

## Buying

* `/shop` opens quickly
* player can browse categories and items
* item detail shows correct price and stock
* purchase deducts eco and decrements stock correctly
* no purchases succeed when out of stock or underfunded

## Persistence

* same core behavior on SQLite and MySQL/MariaDB
* stock survives restart
* transaction logging survives restart
* schema init/migration is automatic and safe

## Performance

* no blocking DB work in player click paths
* sell commands feel fast
* GUI open/browse feels responsive

---

## 23. Recommended next artifact

The best next artifact after this spec is:

**a starter repo file plan** with:

* exact source file paths
* exact class stubs to create first
* `plugin.yml` skeleton
* config file skeletons ready to commit
* migration SQL files

That would be the direct bridge from planning into implementation.
