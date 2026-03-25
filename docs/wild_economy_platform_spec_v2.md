# wild_economy Economy + Store Engineering Spec (v2)

## Product direction

`wild_economy` is a **curated survival economy platform** with one unified `/shop` GUI and three internal domains:

1. **Economy Core**
   - single money authority
   - persistent balances
   - atomic deposit / withdraw / transfer
   - ledger and balance ranking
   - Vault provider
   - PlaceholderAPI exposure
   - player/admin economy commands

2. **Exchange**
   - stock-backed survival goods
   - curated item catalog
   - player-stocked default flow
   - fast GUI buying
   - command-first selling
   - existing stock/pricing identity preserved

3. **Store**
   - non-stock purchases shown inside `/shop`
   - ranks
   - cosmetics
   - perks
   - special tools
   - XP bottle withdrawals
   - permanent one-time unlocks
   - repeatable grants

## Product positioning

The plugin should **not** become a generic all-in-one shop clone. It should be positioned as:

**A curated survival economy platform with one unified `/shop`, a player-stocked Exchange, a built-in Vault economy, and a safe in-game Store for non-stock perks and tools.**

---

## Scope

### In scope for v1

#### Economy Core
- DB-backed balances for durability
- cache-first runtime balance access
- append-only ledger
- `/balance`
- `/bal`
- `/pay`
- `/baltop`
- admin balance commands
- Vault economy provider
- PlaceholderAPI support
- global single-currency model

#### Exchange
- preserve current Exchange identity
- Exchange uses internal economy core instead of external Vault consumer path
- no redesign of pricing philosophy in this slice
- no broadening Exchange into non-stock perks

#### Store
- GUI-integrated Store root category
- ranks
- cosmetics
- perks
- special tools
- XP bottle withdrawals
- permanent one-time unlocks
- repeatable grants
- purchase audit log
- internal entitlement tracking

#### XP bottles
- withdraw player XP into custom XP bottle items
- redeem through event handling
- GUI surfaced inside Store

### Explicitly out of scope for v1
- multi-currency
- bank/shared accounts
- world-specific balances
- negative balances
- taxes
- interest
- debts
- scheduled payments
- cash notes
- timed subscriptions
- temporary VIP with expiry/revocation
- gifting
- bundles
- random crates
- arbitrary scripting
- complex rollback orchestration across third-party plugins

---

## User-facing UX

### Root `/shop`
`/shop` opens directly into the **top-level Exchange browse layer**, with **Store** presented as a peer root category alongside Exchange categories.

Example root layout:
- Building Blocks
- Farming
- Mob Drops
- Redstone
- Food
- Misc
- Store

### Store sections
Inside Store:
- Ranks
- Cosmetics
- Perks
- Tools
- XP Bottles

These are player-facing groupings only. They do not imply shared backend logic.

---

## Core architectural rule

**One economy core, many consumers.**

Consumers:
- Exchange buy flow
- Exchange sell flow
- `/pay`
- `/balance`
- `/baltop`
- Store purchases
- Vault adapter
- PAPI adapter

The GUI must never own business logic. It should only route clicks to domain services.

---

## Service boundaries

### Economy Core boundary

#### Responsibilities
- create/load accounts
- fetch balances
- deposit
- withdraw
- transfer
- set/take/give admin operations
- append ledger entries
- top balance queries
- amount formatting
- name caching for display
- Vault bridge entrypoint

#### Must not do
- know about item stock
- know about Exchange categories
- know about Store product icons/lore
- know about XP item rendering
- execute arbitrary product commands

### Exchange boundary

#### Responsibilities
- browse Exchange catalog
- quote buy price
- quote sell payout
- validate sellability and stock policy
- consume/add stock
- create Exchange audit records
- call economy core for money mutation

#### Must not do
- write balances directly
- implement `/pay`
- expose Vault API
- store permanent entitlements
- treat Store products as stock items

### Store boundary

#### Responsibilities
- load product catalog
- render Store categories
- validate product visibility/ownership
- validate one-time vs repeatable purchase rules
- call economy core for payment where applicable
- dispatch typed product actions
- write purchase audit records
- write entitlements for permanent unlocks

#### Must not do
- share schema with Exchange items
- mutate balances directly
- become a generic arbitrary command console
- depend on Exchange stock logic

### XP boundary

#### Responsibilities
- compute current player XP points
- withdraw XP points safely
- create custom XP bottle item
- redeem custom bottle through event flow

#### Must not do
- use balance currency
- be modeled as an Exchange item

---

## Proposed package layout

```text
com.splatage.wild_economy
  bootstrap/
  config/
  gui/
  economy/
    model/
    service/
    repository/
    vault/
    placeholder/
    command/
  exchange/
    model/
    service/
    repository/
    command/
    gui/
  store/
    model/
    service/
    repository/
    action/
    entitlement/
    gui/
  xp/
    model/
    service/
    listener/
    gui/
  persistence/
  util/
```

---

## Database design

### New tables

#### `economy_accounts`
Canonical current balance.

```sql
player_uuid      VARCHAR(36) PRIMARY KEY
balance_minor    BIGINT NOT NULL
updated_at       BIGINT NOT NULL
```

#### `economy_ledger`
Append-only money history.

```sql
entry_id             BIGINT PRIMARY KEY AUTO_INCREMENT
player_uuid          VARCHAR(36) NOT NULL
entry_type           VARCHAR(64) NOT NULL
amount_minor         BIGINT NOT NULL
balance_after_minor  BIGINT NOT NULL
counterparty_uuid    VARCHAR(36) NULL
reference_type       VARCHAR(64) NULL
reference_id         VARCHAR(128) NULL
created_at           BIGINT NOT NULL
```

#### `economy_name_cache`
For `/baltop` display.

```sql
player_uuid    VARCHAR(36) PRIMARY KEY
last_name      VARCHAR(64) NOT NULL
updated_at     BIGINT NOT NULL
```

#### `store_entitlements`
Permanent ownership.

```sql
player_uuid      VARCHAR(36) NOT NULL
entitlement_key  VARCHAR(128) NOT NULL
product_id       VARCHAR(128) NOT NULL
granted_at       BIGINT NOT NULL
PRIMARY KEY (player_uuid, entitlement_key)
```

#### `store_purchases`
Store audit trail.

```sql
purchase_id        BIGINT PRIMARY KEY AUTO_INCREMENT
player_uuid         VARCHAR(36) NOT NULL
product_id          VARCHAR(128) NOT NULL
product_type        VARCHAR(64) NOT NULL
price_minor         BIGINT NOT NULL
status              VARCHAR(32) NOT NULL
failure_reason      VARCHAR(255) NULL
created_at          BIGINT NOT NULL
completed_at        BIGINT NULL
```

#### `xp_bottle_tokens`
Only if DB-backed redemption hardening is required.

```sql
token_id            VARCHAR(36) PRIMARY KEY
owner_uuid          VARCHAR(36) NULL
xp_points           INT NOT NULL
created_at          BIGINT NOT NULL
redeemed_at         BIGINT NULL
status              VARCHAR(32) NOT NULL
```

### Existing tables
Keep Exchange tables separate. Do not merge economy ledger and Exchange transaction history into one generic mega-table.

---

## Migration method

The migration loader must be upgraded from a v1-only model to ordered multi-version discovery.

### Required migration refactor
- discover all `V<number>__name.sql` files under dialect path
- sort by version
- apply every version above current schema version
- keep the existing dialect split (`sqlite` / `mysql`)
- fail startup hard on partial migration failure

### Proposed migration sequence
- `V2__economy_core.sql`
- `V3__store_products.sql`
- `V4__xp_bottle_tokens.sql` if needed

---

## Config design

Keep config domains separate.

### `economy.yml`

```yaml
currency:
  singular: Coin
  plural: Coins
  symbol: "$"
  fractional-digits: 2
  use-symbol-in-formatting: true

accounts:
  auto-create-on-join: true
  auto-create-on-first-transaction: true

commands:
  pay:
    enabled: true
    min-amount: 0.01
    allow-self-pay: false

baltop:
  page-size: 10
  max-page-size: 20
  cache-seconds: 30

vault:
  enabled: true
  support-banks: false
  support-world-balances: false

placeholders:
  enabled: true

admin:
  log-balance-adjustments: true
```

### `store-products.yml`

```yaml
categories:
  ranks:
    display-name: "Ranks"
    icon: NETHER_STAR
    slot: 10
  cosmetics:
    display-name: "Cosmetics"
    icon: GLOW_ITEM_FRAME
    slot: 12
  perks:
    display-name: "Perks"
    icon: BEACON
    slot: 14
  tools:
    display-name: "Tools"
    icon: DIAMOND_PICKAXE
    slot: 16
  xp_bottles:
    display-name: "XP Bottles"
    icon: EXPERIENCE_BOTTLE
    slot: 22

products:
  vip_rank:
    category: ranks
    type: PERMANENT_UNLOCK
    display-name: "&6VIP"
    icon: GOLD_INGOT
    price: 25000.00
    entitlement-key: "rank.vip"
    confirm: true
    actions:
      - type: CONSOLE_COMMAND
        command: "lp user {player} parent add vip"
      - type: MESSAGE
        message: "&aYou unlocked VIP!"

  builder_wand:
    category: tools
    type: REPEATABLE_GRANT
    display-name: "&bBuilder Wand"
    icon: BLAZE_ROD
    price: 5000.00
    confirm: true
    actions:
      - type: CONSOLE_COMMAND
        command: "customtools givewand {player}"

xp-bottles:
  small:
    display-name: "&aSmall XP Bottle"
    icon: EXPERIENCE_BOTTLE
    xp-points: 100
    confirm: false
  medium:
    display-name: "&eMedium XP Bottle"
    icon: EXPERIENCE_BOTTLE
    xp-points: 500
    confirm: true
```

---

## Product model

Store must be typed.

```java
public enum StoreProductType {
    PERMANENT_UNLOCK,
    REPEATABLE_GRANT,
    XP_WITHDRAWAL
}
```

```java
public record StoreProduct(
    String productId,
    String categoryId,
    StoreProductType type,
    String displayName,
    Material icon,
    MoneyAmount price,
    @Nullable String entitlementKey,
    boolean requireConfirmation,
    List<ProductAction> actions
) {}
```

### Rules
- `PERMANENT_UNLOCK`
  - requires entitlement key
  - cannot be bought twice unless config explicitly allows repurchase
- `REPEATABLE_GRANT`
  - no entitlement required
  - can be purchased repeatedly
- `XP_WITHDRAWAL`
  - no money charge by default
  - costs player XP points instead

---

## Action model

Do not support free-form scripting in v1.

### Allowed action types
- `CONSOLE_COMMAND`
- `MESSAGE`
- `TITLE`
- `BROADCAST`
- `DIRECT_ITEM_GRANT` only if implemented safely by the plugin

### Placeholder whitelist
- `{player}`
- `{player_uuid}`
- `{product_id}`

### Execution policy
1. validate eligibility
2. reserve/withdraw money if applicable
3. execute actions
4. write entitlement if needed
5. mark purchase success
6. refund on detected failure
7. log everything

Honest constraint: third-party plugin actions can never be perfectly atomic. v1 guarantees best-effort refund on explicit action failure, not global transactional rollback across all external plugins.

---

## Economy money model

Use integer minor units internally.

```java
public record MoneyAmount(long minorUnits) {
    public static MoneyAmount ofMinor(long minor) { return new MoneyAmount(minor); }
}
```

Reason:
- no floating-point drift
- easy DB storage
- exact comparisons
- safe Vault adaptation with controlled conversion

---

## Commands

### Player economy commands
- `/balance`
- `/bal [player]`
- `/pay <player> <amount>`
- `/baltop [page]`

### Admin economy commands
Use `/eco` as a distinct root.
- `/eco balance <player>`
- `/eco give <player> <amount>`
- `/eco take <player> <amount>`
- `/eco set <player> <amount>`
- `/eco reset <player>`

### Shop commands
- `/shop`
- `/shop sellhand`
- `/shop sellall`
- `/shop sellcontainer`
- `/sellhand`
- `/sellall`
- `/sellcontainer`
- `/shopadmin`

Optional later:
- `/shop store`
- `/shop xpwithdraw`

---

## Permissions

```yaml
wild_economy.shop:
  default: true

wild_economy.shop.buy:
  default: true

wild_economy.shop.sell:
  default: true

wild_economy.shop.sellcontainer:
  default: true

wild_economy.balance:
  default: true

wild_economy.balance.others:
  default: op

wild_economy.pay:
  default: true

wild_economy.baltop:
  default: true

wild_economy.store:
  default: true

wild_economy.store.buy:
  default: true

wild_economy.store.category.ranks:
  default: true

wild_economy.store.category.cosmetics:
  default: true

wild_economy.store.category.perks:
  default: true

wild_economy.store.category.tools:
  default: true

wild_economy.store.category.xp:
  default: true

wild_economy.admin:
  default: op
  children:
    wild_economy.admin.view: true
    wild_economy.admin.reload: true
    wild_economy.admin.apply: true
    wild_economy.admin.override: true
    wild_economy.admin.eco: true

wild_economy.admin.eco:
  default: op
  children:
    wild_economy.admin.eco.balance: true
    wild_economy.admin.eco.give: true
    wild_economy.admin.eco.take: true
    wild_economy.admin.eco.set: true
    wild_economy.admin.eco.reset: true
```

---

## GUI service model

### Presentation interfaces

```java
public interface ShopPageProvider {
    ShopPage buildPage(Player player, ShopContext context);
}

public interface ShopClickHandler {
    ClickResult handle(Player player, ShopClickContext click);
}
```

### Root GUI contract
- root page shows Exchange top-level categories
- Store root icon is one peer tile
- item tiles contain routing metadata only
- click handlers dispatch to Exchange, Store, or XP services

### GUI metadata model

```java
public sealed interface ShopEntry
    permits ExchangeCategoryEntry, StoreCategoryEntry, ExchangeItemEntry, StoreProductEntry, XpBottleEntry {}
```

---

## Economy service API

```java
public interface EconomyService {
    MoneyAmount getBalance(UUID playerId);
    EconomyResult deposit(UUID playerId, MoneyAmount amount, EconomyReason reason);
    EconomyResult withdraw(UUID playerId, MoneyAmount amount, EconomyReason reason);
    TransferResult transfer(UUID from, UUID to, MoneyAmount amount, EconomyReason reason);
    EconomyResult setBalance(UUID playerId, MoneyAmount amount, EconomyReason reason);
    List<BalanceRankEntry> getTopBalances(int page, int size);
}
```

```java
public enum EconomyReason {
    ADMIN_GIVE,
    ADMIN_TAKE,
    ADMIN_SET,
    PLAYER_PAY_SEND,
    PLAYER_PAY_RECEIVE,
    EXCHANGE_BUY,
    EXCHANGE_SELL,
    STORE_PURCHASE,
    STORE_REFUND,
    MIGRATION_ADJUSTMENT
}
```

---

## Exchange integration contract

Exchange should depend only on `EconomyService`.

### Buy flow
1. quote price
2. atomically consume stock
3. withdraw money
4. deliver items
5. refund and restore stock on delivery failure path
6. write Exchange audit

### Sell flow
1. scan/normalize/group items
2. compute payout
3. mutate stock
4. deposit money
5. write Exchange audit

Do not let Exchange repositories write balance tables directly.

---

## Store service API

```java
public interface StoreService {
    List<StoreCategoryView> getVisibleCategories(Player player);
    List<StoreProductView> getVisibleProducts(Player player, String categoryId);
    StorePurchaseResult purchase(Player player, String productId);
    boolean ownsEntitlement(UUID playerId, String entitlementKey);
}
```

```java
public interface ProductActionExecutor {
    ActionExecutionResult execute(Player player, StoreProduct product);
}
```

```java
public interface EntitlementService {
    boolean hasEntitlement(UUID playerId, String key);
    void grantEntitlement(UUID playerId, String key, String productId);
}
```

---

## XP service API

```java
public interface XpBottleService {
    int getCurrentXpPoints(Player player);
    XpWithdrawResult withdrawToBottle(Player player, int xpPoints);
    XpRedeemResult redeemBottle(Player player, ItemStack item);
}
```

---

## Vault adapter

`wild_economy` should become the Vault provider, not just a Vault consumer.

### v1 adapter policy
- implement global player balances
- return global results from world-specific methods
- `hasBankSupport() == false`
- bank methods return failure/unsupported
- `fractionalDigits() == 2`
- format using `economy.yml`

---

## PlaceholderAPI adapter

### v1 placeholders
- `%wild_economy_balance%`
- `%wild_economy_balance_formatted%`
- `%wild_economy_baltop_position%`
- `%wild_economy_baltop_name_1%`
- `%wild_economy_baltop_balance_1%`

---

## Performance method

### General
- no DB reads on hot GUI browse clicks
- immutable in-memory Store catalog
- precomputed category maps
- short-lived cached baltop pages
- centralized async write paths for non-transactional logs where safe

### Economy persistence model
Balances are **DB-backed for durability but cache-first at runtime**.

- online balances and hot reads from GUI, Vault, PAPI, and commands should be served from memory where possible
- money mutations must go through one serialized economy service
- cache state should update immediately on successful mutation
- durable persistence should happen asynchronously with strict failure fences
- if persistence health degrades, new money mutations should fail closed rather than silently continue unsafely

### Shared DB infrastructure
Economy should reuse the **same underlying database configuration/provider/pool as Exchange** in v1.

- one shared database configuration
- one shared `DatabaseProvider` / connection pool
- separate repositories per domain
- separate logical writer queues/workers if needed
- separate tables
- not separate pools

### Backend-specific writer discipline

#### SQLite
- single writer thread
- bounded queue
- batched transaction flushes
- fail closed when queue is unhealthy/full

#### MySQL/MariaDB
- same shared pool in v1
- small async writer worker model if needed
- per-account or per-transfer serialization discipline
- no main-thread blocking DB writes

### Exchange
- preserve current in-memory authoritative stock
- async stock persistence
- precomputed browse structures
- aggregated sell paths

### Store
- load once at startup/reload
- no dynamic command parsing on every click
- route click directly to typed purchase service

---

## Security method

### Store hardening
- typed products only
- no arbitrary scripts
- no arbitrary placeholders
- config-defined only
- audit every purchase
- confirmation for permanent/high-value purchases
- refund on explicit action failure
- clear logs for partial failure diagnosis

### XP bottle hardening
- custom PDC marker
- optional DB token ID for one-time redemption hardening
- reject malformed/untrusted bottles
- never trust display name/lore alone

### Economy hardening
- validate all amounts > 0
- deny self-pay
- deny overflow conditions
- deny negative post-balance
- one transaction path for all balance changes
- admin operations fully logged

---

## Implementation phases

### Phase 1 — economy foundation
- migration loader refactor
- `economy_accounts`
- `economy_ledger`
- `economy_name_cache`
- `EconomyService`
- player/admin commands
- formatting

### Phase 2 — Vault + PAPI
- Vault provider
- PAPI internal expansion
- plugin.yml dependency adjustment

### Phase 3 — Exchange rewiring
- remove external-economy dependency from Exchange runtime path
- Exchange uses internal `EconomyService`

### Phase 4 — Store foundation
- `store-products.yml`
- Store catalog loader
- `store_entitlements`
- `store_purchases`
- typed action executor
- Store GUI pages

### Phase 5 — XP bottles
- XP bottle service
- PDC or token storage
- Store XP section
- redemption listener

### Phase 6 — polish
- confirmation flows
- better audit messages
- metrics/debug logging
- admin docs

---

## Stage summary

### Locked
- product direction
- domain split
- v1 in-scope/out-of-scope
- GUI structure
- service boundaries
- DB schema direction
- config separation
- commands
- permissions
- integration strategy for Vault/PAPI/XP
- cache-first DB-backed economy persistence model
- shared DB provider/pool with separate logical write discipline

### Not yet delivered
- repo-targeted implementation plan against the current commit
- exact file-by-file implementation
- migration SQL
- full Java class scaffolds
- plugin.yml rewrite
