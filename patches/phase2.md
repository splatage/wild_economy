# wild_economy — Second Commit Plan (Sell Path First)

## Status

This document defines the **second commit** after the initial scaffold.

Its goal is to implement the **first real vertical slice** of `wild_economy`:

* load config
* build the internal Exchange catalog
* normalize and validate sellable items
* quote stock-aware sell value
* pay the player
* add sold stock
* log the transaction
* support:

  * `/shop sellhand`
  * `/shop sellall`

This commit should **not** attempt to implement GUI buying yet.

---

# 1. Why the sell path comes first

The sell path is the strongest first slice because it exercises the core system without bringing in GUI complexity.

It forces the plugin to prove:

* item identity rules
* item policy rules
* worth import and explicit item config
* pricing rules
* stock buffer updates
* economy payouts
* persistence
* transaction logging

If the sell path is clean and correct, the buy side becomes much easier later.

---

# 2. Second commit scope

## Included

* Config loading (real, not stubbed)
* Catalog loading from `exchange-items.yml`
* Worth import from `worth-import.yml` + Essentials `worth.yml`
* Internal `ExchangeCatalog` build
* Strict Bukkit item normalization
* Sell validation
* Stock snapshot and room calculation
* Sell-price taper from stock bands
* SQLite stock repository implementation
* SQLite transaction repository implementation
* Vault economy deposit
* `/shop sellhand`
* `/shop sellall`
* Clear player result messages

## Deferred to later

* Buy GUI
* MySQL implementation if SQLite comes first
* browse/item detail menus
* stock turnover task implementation
* admin commands beyond maybe a minimal reload if needed

---

# 3. Exact file list to flesh out now

## High priority — must implement

### Config / catalog

* `src/main/java/com/splatage/wild_economy/config/ConfigLoader.java`
* `src/main/java/com/splatage/wild_economy/config/ExchangeItemsConfig.java`
* `src/main/java/com/splatage/wild_economy/config/WorthImportConfig.java`
* `src/main/java/com/splatage/wild_economy/config/DatabaseConfig.java`
* `src/main/java/com/splatage/wild_economy/exchange/catalog/CatalogLoader.java`
* `src/main/java/com/splatage/wild_economy/exchange/catalog/WorthImporter.java`
* `src/main/java/com/splatage/wild_economy/exchange/catalog/CatalogMergeService.java`

### Item identity / validation

* `src/main/java/com/splatage/wild_economy/exchange/item/BukkitItemNormalizer.java`
* `src/main/java/com/splatage/wild_economy/exchange/item/ItemValidationServiceImpl.java`
* `src/main/java/com/splatage/wild_economy/exchange/item/CanonicalItemRules.java`

### Persistence

* `src/main/java/com/splatage/wild_economy/persistence/DatabaseProvider.java`
* `src/main/java/com/splatage/wild_economy/persistence/MigrationManager.java`
* `src/main/java/com/splatage/wild_economy/persistence/JdbcUtils.java`
* `src/main/java/com/splatage/wild_economy/exchange/repository/sqlite/SqliteExchangeStockRepository.java`
* `src/main/java/com/splatage/wild_economy/exchange/repository/sqlite/SqliteExchangeTransactionRepository.java`
* `src/main/java/com/splatage/wild_economy/exchange/repository/sqlite/SqliteSchemaVersionRepository.java`

### Economy

* `src/main/java/com/splatage/wild_economy/economy/VaultEconomyGateway.java`

### Stock / pricing / services

* `src/main/java/com/splatage/wild_economy/exchange/stock/StockServiceImpl.java`
* `src/main/java/com/splatage/wild_economy/exchange/stock/StockStateResolver.java`
* `src/main/java/com/splatage/wild_economy/exchange/pricing/PricingServiceImpl.java`
* `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellServiceImpl.java`
* `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeServiceImpl.java`
* `src/main/java/com/splatage/wild_economy/exchange/service/TransactionLogServiceImpl.java`

### Commands / bootstrap

* `src/main/java/com/splatage/wild_economy/command/ShopCommand.java`
* `src/main/java/com/splatage/wild_economy/command/ShopSellHandSubcommand.java`
* `src/main/java/com/splatage/wild_economy/command/ShopSellAllSubcommand.java`
* `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`

## Leave as placeholders in this commit

* all GUI classes
* buy service implementation
* turnover task logic
* MySQL repository implementations

---

# 4. Recommended implementation order

## Step 1 — make config loading real

Implement:

* `ConfigLoader`
* basic config model parsing for:

  * `config.yml`
  * `database.yml`
  * `worth-import.yml`
  * `exchange-items.yml`

Goal:

* startup can load validated settings and item definitions

### Output of this step

You should be able to produce in memory:

* `GlobalConfig`
* `DatabaseConfig`
* `WorthImportConfig`
* raw item config entries

---

## Step 2 — make the Exchange catalog real

Implement:

* `WorthImporter`
* `CatalogLoader`
* `CatalogMergeService`
* `ExchangeCatalog`

### Rules

* explicit item config remains authoritative where set
* `worth.yml` fills base numeric values when allowed
* internal catalog entries must be complete enough for sell path

### Output of this step

A usable `ExchangeCatalog` with entries like:

* item key
* display name
* category
* policy mode
* buy enabled
* sell enabled
* base worth
* buy price
* sell price
* stock cap
* turnover amount
* sell-price bands

---

## Step 3 — implement strict item normalization

Implement:

* `BukkitItemNormalizer`
* `CanonicalItemRules`
* `ItemValidationServiceImpl`

### v1 rules

Keep this strict.

A sellable Exchange item should generally be:

* non-null
* non-air
* a configured canonical material/item key
* not custom-named
* not lore-modified
* not enchanted
* not damaged if damage matters
* not carrying weird metadata outside explicit support

Do not add fuzzy matching.

### Output of this step

The plugin can inspect a held stack and answer:

* valid/invalid
* canonical item key
* rejection reason

---

## Step 4 — implement SQLite persistence first

Implement:

* `DatabaseProvider`
* `MigrationManager`
* SQLite repositories

### Recommendation

Do SQLite first for the first working slice.
Leave MySQL placeholders until the sell path is proven.

### Requirements

* auto-create database file
* run V1 schema migration if missing
* stock rows can be created lazily on first use
* transaction inserts work reliably

### Output of this step

Working SQLite-backed storage for:

* stock
* transaction log
* schema version

---

## Step 5 — implement economy payout

Implement:

* `VaultEconomyGateway`

### Requirements

* resolve Vault provider at startup
* fail plugin enable cleanly if Vault economy is missing
* support:

  * `deposit(UUID, BigDecimal)`
  * `withdraw(UUID, BigDecimal)`
  * `getBalance(UUID)`

### Output of this step

The plugin can actually pay players on sell.

---

## Step 6 — implement stock snapshot and pricing

Implement:

* `StockServiceImpl`
* `StockStateResolver`
* `PricingServiceImpl`

### StockService responsibilities for this commit

* load current stock
* compute cap/fill ratio from catalog entry
* increment stock on sell
* compute available room

### PricingService responsibilities for this commit

* quote sell value from:

  * configured sell base price
  * stock fill ratio
  * matching sell-price band
* return structured `SellQuote`

### Important v1 decision

Do **not** partially sell a stack to fill remaining room in this commit unless explicitly chosen.

Cleaner first version:

* if stack amount exceeds room, reject that stack with `STOCK_FULL`
* or skip it in `sellall`

That is stricter and simpler for the first working path.
You can revisit partial-stack selling later if desired.

---

## Step 7 — implement transaction logging

Implement:

* `TransactionLogServiceImpl`

### Requirements

Record:

* type = `SELL`
* player UUID
* item key
* amount
* unit price
* total value
* created time
* optional metadata json later

Keep this thin.

---

## Step 8 — implement the actual sell service

Implement:

* `ExchangeSellServiceImpl`
* enough of `ExchangeServiceImpl` to expose sell flows

### `/shop sellhand` behavior

1. resolve player
2. read held item
3. validate item
4. normalize to `ItemKey`
5. load stock snapshot
6. quote sell value
7. if invalid/rejected: return `SellHandResult` with rejection reason
8. remove held quantity from player
9. deposit eco
10. increment stock
11. log sale
12. return success result

### `/shop sellall` behavior

1. snapshot inventory contents
2. validate each stack independently
3. group valid stacks by slot or item key depending on implementation convenience
4. for each valid stack:

   * load stock snapshot
   * quote sell value
   * if sellable, mark for removal and stock addition
5. apply removals on main thread
6. deposit combined eco total
7. increment stock per sold line
8. log sale lines
9. return `SellAllResult`

### Recommended first-pass simplification

For the first working commit, process `sellall` **per stack** rather than trying to aggregate multiple stacks into one quote before stock changes.

Why:

* simpler correctness
* stock-aware taper updates naturally as stock fills during the command
* less hidden complexity

That means later stacks in the same `/shop sellall` may sell for a lower price if saturation rises during the operation.
That is acceptable and even conceptually aligned with the design.

---

## Step 9 — wire commands for real behavior

Implement:

* `ShopCommand`
* `ShopSellHandSubcommand`
* `ShopSellAllSubcommand`
* service lookup/wiring in `ServiceRegistry`

### Command routing behavior

* `/shop` with no args: for now send a placeholder like “Shop GUI coming soon” or optionally open empty stub GUI later
* `/shop sellhand`: execute real sell-hand flow
* `/shop sellall`: execute real sell-all flow

### Message expectations

Player-facing messages should use `messages.yml` where practical, but do not overbuild message infrastructure in this commit.
A minimal clean implementation is enough.

---

# 5. Exact per-file responsibilities

## `ConfigLoader.java`

Must do:

* read Bukkit YAML configs from plugin data folder/resources
* map values into config records
* expose item raw config map

## `ExchangeItemsConfig.java`

Should become the typed representation of parsed item config entries.
Likely include:

* map of item key -> raw item settings object

## `WorthImporter.java`

Must do:

* parse Essentials worth file
* map recognized values to `ItemKey`
* return a `Map<ItemKey, BigDecimal>`

## `CatalogLoader.java`

Must do:

* read item config entries
* combine them with worth values via `CatalogMergeService`
* produce final `ExchangeCatalog`

## `BukkitItemNormalizer.java`

Must do:

* convert `ItemStack` -> canonical `ItemKey`
* reject null/air/weird states cleanly

## `ItemValidationServiceImpl.java`

Must do:

* validate sell eligibility against:

  * canonical item rules
  * catalog entry existence
  * policy mode
  * sell enabled

## `SqliteExchangeStockRepository.java`

Must do:

* get stock
* increment stock
* decrement stock
* set stock
* lazy insert missing rows if needed

## `SqliteExchangeTransactionRepository.java`

Must do:

* insert transaction rows

## `VaultEconomyGateway.java`

Must do:

* talk to Vault provider
* deposit funds
* fetch balance

## `StockServiceImpl.java`

Must do:

* get stock snapshot from repository + catalog
* compute room/cap/fill/state
* add stock after sell

## `PricingServiceImpl.java`

Must do:

* find matching sell band by fill ratio
* compute effective unit sell price
* compute total price

## `ExchangeSellServiceImpl.java`

Must do:

* orchestrate sell path
* remain free of raw SQL
* remain free of direct Bukkit command parsing

## `ExchangeServiceImpl.java`

For this commit, it only needs to delegate sell methods.
Buy/browse methods can remain unimplemented placeholders until GUI work starts.

---

# 6. Data/logic decisions to lock for this commit

## Decision A — strict eligibility

Only items explicitly present in the internal catalog and marked sellable should sell.

## Decision B — no fuzzy metadata support

No custom names, lore, enchants, damage-based ambiguity, or plugin-specific items in the first sell slice.

## Decision C — no partial stack selling for cap edge cases

If a stack would exceed remaining room:

* reject or skip the stack for now

This keeps the first path simpler and safer.

## Decision D — `/shop sellall` may taper during the command

Process stacks in order and let stock rise between lines.
This is simpler and still conceptually consistent.

## Decision E — SQLite first, MySQL second

The second commit should prove correctness on SQLite first.
MySQL can be filled in immediately after or in the next follow-up commit.

---

# 7. Minimal acceptance criteria for the second commit

## Startup

* plugin enables
* configs load
* SQLite DB initializes
* schema migration runs
* catalog builds successfully
* Vault economy resolves successfully

## `/shop sellhand`

* valid configured item sells successfully
* player receives correct eco
* stock increments correctly
* transaction row is written
* invalid item is rejected clearly
* disabled or unsellable item is rejected clearly

## `/shop sellall`

* valid configured inventory items sell successfully
* skipped items remain in inventory
* payout total is correct
* stock increments correctly per sold line
* transaction rows are written
* high-stock taper is reflected in payouts

## Safety

* no silent item loss
* no sell path without payout
* no payout without item removal
* no stock increment if item was not removed

---

# 8. Recommended commit breakdown inside the second stage

To keep this manageable, I would actually split the “second commit” work into smaller local commits:

### Commit 2A

* config loader
* worth import
* catalog loader
* strict item normalization/validation

### Commit 2B

* SQLite persistence
* migration manager
* Vault economy gateway

### Commit 2C

* stock service
* pricing service
* transaction logging
* `/shop sellhand`

### Commit 2D

* `/shop sellall`
* polish messages
* startup wiring cleanup

That is a much safer implementation path than trying to land it all in one shot.

---

# 9. What not to do in this stage

* do not build buy GUI yet
* do not add Marketplace scaffolding
* do not make buy prices dynamic
* do not add fuzzy item matching
* do not add partial-stack cap-fill logic unless really needed now
* do not overbuild admin commands
* do not make the command layer own business rules

Keep this stage focused on the first working economic core.

---

# 10. Best next artifact after this plan

The best next artifact would be:

**copy-ready file contents for Commit 2A**

That means exact contents for:

* `ConfigLoader.java`
* `ExchangeItemsConfig.java`
* `WorthImporter.java`
* `CatalogLoader.java`
* `CatalogMergeService.java`
* `BukkitItemNormalizer.java`
* `ItemValidationServiceImpl.java`
* `CanonicalItemRules.java`

That is the cleanest place to start real implementation.
