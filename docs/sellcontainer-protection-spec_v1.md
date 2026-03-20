# wild_economy — `/shop sellcontainer` protected-container access check spec (v1)

## Status

This document defines the engineering scope and implementation direction for adding a **pre-sale container access/protection check** to `/shop sellcontainer`.

It is intended to be added to the repo as a repo-ready implementation spec and used as the source of truth for this feature.

---

## 1. Problem statement

`/shop sellcontainer` currently resolves a targeted supported block container or a held shulker item, reads the inventory, plans sales, removes sold contents, and pays the player.

For world containers, this means the command can currently operate on a target inventory **without first confirming that the player is actually allowed to open/use that container under external protection systems** such as claims/land plugins.

That creates a correctness and compatibility gap:

* the command should respect claim/container protections
* the plugin should not bypass another plugin's access rules simply because it can read a Bukkit inventory
* the player experience should match normal gameplay expectations: if the player cannot open/use the container, they should not be able to sell from it via `/shop sellcontainer`

---

## 2. Desired behavior

When a player runs `/shop sellcontainer` on a **world block container**:

1. resolve the targeted block
2. verify it is a supported container type
3. verify the player is allowed to access/use/open that container
4. only if access is allowed, proceed to read contents and perform the sale

If access is denied, the command must:

* abort before reading/planning/modifying contents
* return a clear player-facing message
* not pay the player
* not change stock
* not remove items

Held shulker-box selling is **not** part of the claims-protection problem in the same way, because that container item is already in the player's possession. This feature applies only to **placed world containers**.

---

## 3. Scope

## In scope

* add an access/protection gate before selling from targeted world containers
* respect protection/claims plugins where practical
* fail closed when access cannot be confidently established
* keep held shulker selling working as-is unless future security policy says otherwise
* keep the existing supported-container list unchanged for v1:

  * chest
  * barrel
  * shulker box block
* provide clean user-facing denial messaging
* keep the design extensible for plugin-specific adapters later

## Out of scope

* full region/claims abstraction for every protection plugin on day one
* adding new supported container types
* changing `/shop sellall` semantics
* changing held-shulker behavior beyond current protections for nested shulker contents
* adding a hard dependency on any specific claims plugin in v1
* simulating a visible GUI open as part of the user experience

---

## 4. Current implementation summary

The current sell-container flow is centered in `ExchangeSellServiceImpl`.

Current high-level behavior:

* get online player
* ray-target a block within `CONTAINER_TARGET_RANGE`
* resolve chest/barrel/shulker block inventory if present
* otherwise allow selling from a held shulker item
* plan sales directly from the target `Inventory`
* remove sold stacks
* pay the player
* add stock and log transactions

Important observation:

The current flow resolves the target inventory directly from `BlockState` and proceeds immediately to inventory sale planning. There is no access-control layer between "supported target found" and "sell from inventory target".

That gap is what this spec addresses.

---

## 5. Design principles

1. **Respect other plugins' protections.**
2. **Do not bypass claim/container rules through direct inventory access.**
3. **Fail before mutation.** Access checks happen before reading, planning, payout, or item removal.
4. **Fail closed.** If the plugin cannot confidently determine access is allowed, do not sell.
5. **Keep dependencies optional.** v1 should not require GriefPrevention, Towny, Lands, Factions, etc.
6. **Keep the sell path thin.** The access gate should be a narrow precondition, not a large policy engine.
7. **Support future adapters.** The design should allow more accurate plugin-specific integration later.

---

## 6. Recommended solution

Introduce a dedicated access/protection abstraction and place it directly in the world-container sale path.

### New internal concept

`ContainerAccessService`

Purpose:

* determine whether a player may use a targeted world container block for `/shop sellcontainer`
* return structured allow/deny results with a machine-readable reason and optional message

### Recommended method shape

```java
public interface ContainerAccessService {
    ContainerAccessResult canAccessSellContainer(Player player, Block targetBlock, BlockState state);
}
```

### Recommended result model

```java
public record ContainerAccessResult(
    boolean allowed,
    ContainerAccessDecision decision,
    String message
) {}
```

```java
public enum ContainerAccessDecision {
    ALLOWED,
    DENIED_BY_PROTECTION,
    UNSUPPORTED_TARGET,
    UNKNOWN_PLUGIN_STATE,
    INTERNAL_ERROR
}
```

### Primary integration point

Insert the access check in `sellContainer(...)` after target resolution, but before calling `sellFromInventoryTarget(...)`.

Suggested flow:

1. get target block
2. resolve supported block target
3. if block target exists:

   * run `containerAccessService.canAccessSellContainer(player, targetBlock, targetBlock.getState())`
   * if denied, return a failed `SellContainerResult`
   * if allowed, proceed to `sellFromInventoryTarget(...)`
4. if no supported block target, fall back to held shulker behavior

---

## 7. Why not rely only on "open container" as an implementation detail?

The user-level goal is correct: before selling from a block container, perform the equivalent of a normal access/use/open permission check.

However, the engineering implementation should not be defined only as "open the inventory UI first".

Reasons:

* opening a UI has side effects and is not necessary for the command UX
* different protection plugins may enforce at different interaction points
* the command needs an internal yes/no access decision, not a forced visible inventory interaction
* a direct inventory open may be a poor fit for automation or future non-UI flows

So the design target should be:

> replicate or integrate with the server's normal container-access permission model

not:

> visibly open the inventory as part of the command

That said, a generic access strategy may still use a Bukkit/Paper event-style permission probe or compatibility bridge internally if needed.

---

## 8. Architecture

## 8.1 New package area

Recommended package:

```text
com.splatage.wild_economy.integration.protection
```

Recommended initial classes:

```text
com.splatage.wild_economy
└── integration
    └── protection
        ├── ContainerAccessService.java
        ├── ContainerAccessServiceImpl.java
        ├── ContainerAccessResult.java
        ├── ContainerAccessDecision.java
        ├── ProtectionAdapter.java
        ├── ProtectionAdapterRegistry.java
        ├── ProtectionCheckContext.java
        └── adapters
            ├── NoopProtectionAdapter.java
            └── GenericContainerUseAdapter.java
```

Optional later:

```text
adapters
├── GriefPreventionProtectionAdapter.java
├── TownyProtectionAdapter.java
├── LandsProtectionAdapter.java
├── FactionsProtectionAdapter.java
└── PlotSquaredProtectionAdapter.java
```

## 8.2 Adapter model

Use a small adapter chain rather than baking plugin logic into `ExchangeSellServiceImpl`.

### `ProtectionAdapter`

```java
public interface ProtectionAdapter {
    boolean isAvailable();
    ProtectionCheckResult check(ProtectionCheckContext context);
    int priority();
    String name();
}
```

### `ProtectionCheckContext`

Should include:

* `Player player`
* `Block targetBlock`
* `BlockState blockState`

### `ProtectionCheckResult`

Possible outcomes:

* `ALLOW`
* `DENY`
* `ABSTAIN`
* `ERROR`

This lets v1 support:

* plugin-specific adapters later
* a generic fallback adapter now
* fail-closed behavior if no adapter can confidently allow access

---

## 9. v1 implementation strategy

## Phase 1 — minimal safe base

Deliver a generic protection layer with the following behavior:

* world block containers are checked through `ContainerAccessService`
* if the access service cannot positively allow the action, `/shop sellcontainer` fails closed
* held shulker selling remains unchanged

### Phase 1 acceptance rule

For world containers, **no direct inventory sale occurs unless access is explicitly allowed by the access service**.

## Phase 2 — compatibility upgrades

Add plugin-specific adapters for major server ecosystems as needed:

* GriefPrevention
* Towny
* Lands
* PlotSquared
* Factions-style plugins

These adapters should remain optional and only activate when the corresponding plugin is present.

---

## 10. v1 access policy

### Placed world containers

`/shop sellcontainer` must require explicit access approval.

### Held shulker items

Held shulker items are treated as belonging to the player holding them and do not require a claims or owner check in v1.

### Double chests

Treat as normal chest access. No separate UX or scope change is required.

### Protected but viewable edge cases

If a plugin allows visual interaction but restricts take/use semantics, the plugin-specific adapter should decide. Until then, the generic v1 implementation should prefer **deny over risky allow**.

## 11.plementation should prefer **deny over risky allow**.

---

## 11. Recommended message behavior

Player-facing messages should stay simple.

### Denied by protection

Recommended default:

* `You cannot use /shop sellcontainer on that protected container.`

Optional plugin-aware variants later:

* `You do not have permission to use that container.`
* `That container is protected by another plugin.`

### Unknown / could not verify

Recommended default:

* `Could not verify access to that container, so the sale was cancelled.`

Important:

* do not reveal excessive internal plugin/debug details to normal users
* do keep more specific internal diagnostics available for debug logging

---

## 12. Logging and diagnostics

Add debug-level logging only.

Recommended debug events:

* target block type and coordinates
* whether target resolved to supported container type
* access-check outcome
* adapter used
* failure reason category

Do not spam normal logs on successful sells.

---

## 13. Service wiring changes

## Constructor change

`ExchangeSellServiceImpl` should receive `ContainerAccessService` as a dependency.

### Before

```java
public ExchangeSellServiceImpl(
    ExchangeCatalog exchangeCatalog,
    ItemValidationService itemValidationService,
    StockService stockService,
    PricingService pricingService,
    EconomyGateway economyGateway,
    TransactionLogService transactionLogService
)
```

### After

```java
public ExchangeSellServiceImpl(
    ExchangeCatalog exchangeCatalog,
    ItemValidationService itemValidationService,
    StockService stockService,
    PricingService pricingService,
    EconomyGateway economyGateway,
    TransactionLogService transactionLogService,
    ContainerAccessService containerAccessService
)
```

## Sell path change

Recommended pseudocode:

```java
final Block targetBlock = player.getTargetBlockExact(CONTAINER_TARGET_RANGE);
final SupportedContainerTarget blockTarget = this.resolveSupportedBlockTarget(targetBlock);
if (blockTarget != null) {
    final ContainerAccessResult accessResult = this.containerAccessService
        .canAccessSellContainer(player, targetBlock, targetBlock.getState());

    if (!accessResult.allowed()) {
        return new SellContainerResult(
            false,
            List.of(),
            BigDecimal.ZERO,
            List.of(),
            blockTarget.description(),
            accessResult.message()
        );
    }

    return this.sellFromInventoryTarget(playerId, blockTarget.inventory(), blockTarget.description());
}
```

---

## 14. Generic fallback strategy

v1 should include a generic fallback strategy, but it must be conservative.

### Recommended generic fallback goals

* mirror normal container-use permission as closely as practical
* avoid opening the UI for the user
* avoid mutating the block or inventory
* avoid fake success when another plugin would have denied access

### Important rule

If the generic strategy cannot confidently determine access, it must return deny/unknown rather than allow.

This may make v1 slightly strict on some servers, but it is the safer correctness tradeoff.

---

## 15. Plugin-specific adapter strategy

Adapters should be optional integrations discovered at runtime.

### Adapter loading model

* detect installed plugins in bootstrap/service wiring
* register available adapters in priority order
* first non-abstain decision wins
* if all abstain, fall back to the generic adapter
* if generic cannot allow, deny

### Suggested priority

1. plugin-specific adapter for known protection plugin
2. generic container-use adapter
3. deny on unresolved state

### Important design rule

Do not add hard compile-time coupling to protection plugins in the base implementation unless explicitly chosen later.

Where compile-time hooks are eventually added, keep them in isolated adapter classes.

---

## 16. Failure and rollback rules

Protection checks must happen before any mutation.

Therefore, on protection denial:

* no inventory scan should proceed beyond what is required to determine supported target
* no payout attempted
* no stock changes
* no transaction log entry for sale

This feature should not require additional rollback complexity if inserted at the correct precondition point.

---

## 17. Test plan

## Unit-level tests

### `ContainerAccessService`

Test:

* allowed result is passed through
* denied result is passed through
* unknown/error results fail closed
* held shulker path is not blocked by world-container access policy

### `ExchangeSellServiceImpl`

Test:

* target block resolves to supported container + access denied -> failed `SellContainerResult`, no stock/economy mutation
* target block resolves to supported container + access allowed -> normal sell path continues
* no supported target + held shulker -> existing held shulker path still works
* no supported target + no held shulker -> unchanged failure message path

## Integration tests

Recommended manual matrix:

1. unclaimed chest -> allowed
2. claimed/protected chest owned by player -> allowed if plugin would allow open/use
3. claimed/protected chest not owned by player -> denied
4. barrel with protection -> denied when normal open/use would be denied
5. world shulker block in protected claim -> denied when normal open/use would be denied
6. held shulker item -> allowed under current v1 policy
7. nested shulker items in container -> still skipped as today

## Regression checks

* `/shop sellhand` unchanged
* `/shop sellall` unchanged
* sell summaries unchanged except for new protection denial path

---

## 18. Acceptance criteria

This feature is complete when all of the following are true:

1. `/shop sellcontainer` no longer sells from a world block container without a prior access/protection check.
2. Access denial stops the operation before payout or inventory mutation.
3. Held shulker selling still works.
4. The design supports future plugin-specific adapters without rewriting the sell service.
5. User-facing denial messaging is clear.
6. The implementation does not add blocking, invasive, or UI-opening side effects to the command path.

---

## 19. Risks and tradeoffs

### Risk: generic fallback may be over-conservative

Yes. That is acceptable for v1 because the feature goal is to avoid bypassing protection.

### Risk: plugin ecosystems differ

Yes. That is why the design uses optional adapters and a conservative fallback.

### Risk: temptation to directly inspect inventories anyway

This must be avoided. Direct inventory access is exactly what can bypass another plugin's intended protection model.

### Tradeoff chosen

Prefer **false negatives** over **false positives**.

In practice:

* better to occasionally deny `/shop sellcontainer` on a weird setup
* than to permit selling from a container the player should not be able to use

---

## 20. Recommended file additions

Recommended new files:

```text
src/main/java/com/splatage/wild_economy/integration/protection/ContainerAccessService.java
src/main/java/com/splatage/wild_economy/integration/protection/ContainerAccessServiceImpl.java
src/main/java/com/splatage/wild_economy/integration/protection/ContainerAccessResult.java
src/main/java/com/splatage/wild_economy/integration/protection/ContainerAccessDecision.java
src/main/java/com/splatage/wild_economy/integration/protection/ProtectionAdapter.java
src/main/java/com/splatage/wild_economy/integration/protection/ProtectionAdapterRegistry.java
src/main/java/com/splatage/wild_economy/integration/protection/ProtectionCheckContext.java
src/main/java/com/splatage/wild_economy/integration/protection/adapters/NoopProtectionAdapter.java
src/main/java/com/splatage/wild_economy/integration/protection/adapters/GenericContainerUseAdapter.java
```

Recommended doc path:

```text
docs/sellcontainer-protection-spec_v1.md
```

---

## 21. Recommended implementation order

1. add result model and service interface
2. wire `ContainerAccessService` into bootstrap/registry
3. inject it into `ExchangeSellServiceImpl`
4. insert pre-sale check in world-container path only
5. implement conservative generic fallback
6. add tests for denied/allowed/no-target/held-shulker flows
7. optionally add debug logging
8. later, add plugin-specific adapters only when needed

---

## 22. Final direction

The implementation should treat `/shop sellcontainer` as a **privileged action on a world container** and require an explicit access decision before proceeding.

The correct architectural move is not to make the command visibly open the inventory, but to add a **dedicated access/protection gate** that mirrors normal gameplay access rules as closely as possible, fails closed when uncertain, and is structured for future claims-plugin adapters.
