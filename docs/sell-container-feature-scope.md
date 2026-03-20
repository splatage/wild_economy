# wild_economy Feature Scope: Shulker Protection in `/sellall` and New `/sellcontainer` Command

## Status

Proposed feature scope for the next sell-path safety and usability slice.

This document defines the intended scope for two related features:

1. **Protect shulkers from accidental sale during `/sellall`**
2. **Add an intentional container-selling command: `/sellcontainer`**

These features are designed to work with the current `wild_economy` architecture, where:

* stock is memory-first and persisted asynchronously
* catalog and browse structures are in memory
* `sellHand()` and `sellAll()` already exist as the current player sell flows
* top-level `/sellhand` and `/sellall` are now part of the command surface

---

## 1. Problem statement

### 1.1 Accidental shulker loss risk in `/sellall`

Players are accustomed to using broad sell commands such as `/sellall` for fast inventory cleanup.

That convenience becomes dangerous when players carry valuable shulker boxes in their inventory. Even if the intention is to sell ordinary bulk items, players may accidentally include a shulker box in the sell pass and lose either:

* the shulker item itself
* the contents of the shulker indirectly or by misunderstanding
* confidence in the command because it feels unsafe to use around portable storage

For `wild_economy`, `/sellall` should feel safe and predictable.

### 1.2 Missing intentional container-sell workflow

Players also need a fast way to liquidate the contents of a container when that is the actual intention.

Examples:

* a chest or barrel they are looking at after farm collection
* a shulker box they are holding after bringing items back from a project or trip

Currently the plugin has no dedicated container-selling path. That means there is no intentional, explicit workflow for "sell the contents of this container, but do not treat it like an ordinary `/sellall` sweep."

---

## 2. High-level feature goals

### Primary goals

* Make `/sellall` safe to use around shulker boxes.
* Add an explicit command for selling container contents on purpose.
* Preserve player trust by making destructive behavior opt-in, not accidental.
* Reuse existing catalog, validation, pricing, stock, economy, and transaction-log systems where possible.
* Keep command behavior simple, fast, and easy to understand.

### Secondary goals

* Keep implementation aligned with current `wild_economy` architecture.
* Avoid unnecessary main-thread database work.
* Avoid feature creep such as recursive nested-container liquidation in v1.
* Keep the command surface intuitive for players familiar with `/sellhand` and `/sellall`.

---

## 3. Scope summary

This feature set introduces two related changes:

### A. `/sellall` shulker protection

`/sellall` must **not automatically sell shulker boxes**.

Recommended v1 behavior:

* Shulker box items are skipped during `/sellall`.
* Skipped shulkers are reported in the skipped summary.
* This protection applies whether the shulker is empty or contains items.

Rationale:

* The player expectation is safety.
* Distinguishing empty vs non-empty still leaves room for accidental sale of valuable portable storage.
* Intentional container liquidation should happen through `/sellcontainer`, not through a broad inventory sweep.

### B. `/sellcontainer`

Add a new intentional command for selling the **contents** of a container.

Recommended v1 supported container targets:

* the block container the player is currently looking at
* a shulker box held in the player’s main hand

Recommended v1 result:

* sell eligible contents from the chosen container
* leave the container itself intact
* do not recurse into nested containers

---

## 4. Detailed `/sellall` shulker-protection scope

## 4.1 Required behavior

When `/sellall` scans the player inventory:

* any shulker box item stack must be excluded from sale planning
* the stack must remain untouched in the player inventory
* the command must continue processing other sellable items normally
* skipped shulker stacks should be included in the skipped output

## 4.2 Recommended user-facing messaging

Examples:

* `Skipped Purple Shulker Box x1 (protected container item)`
* `Skipped 2 protected shulker container(s)`

The wording should communicate that the skip is intentional safety behavior, not an error.

## 4.3 Why protection should apply to all shulkers, not only filled ones

Recommended default policy:

* `/sellall` skips all shulker box items, regardless of contents

Reasoning:

* players use `/sellall` as a broad cleanup command
* accidental sale of even an empty shulker can still feel wrong
* a simple rule is easier to remember and trust
* intentional shulker-related liquidation belongs under `/sellcontainer`

## 4.4 Explicit non-goals for this protection slice

This slice should **not**:

* block manual sale of a shulker box via other intentional paths unless separately specified later
* add recursion into shulker contents during `/sellall`
* inspect and liquidate nested container contents as part of `/sellall`

---

## 5. Detailed `/sellcontainer` scope

## 5.1 Command purpose

`/sellcontainer` is the intentional command for selling the contents of a container.

It should feel like:

* the safe, explicit counterpart to `/sellall`
* the bulk-liquidation tool for organized storage
* the command players use when they deliberately want to empty a container into cash

## 5.2 Recommended command shape

### Primary command

* `/sellcontainer`

### Optional future aliases

Not required for v1, but possible later:

* `/sellcontents`
* `/sellbox`

For v1, keep the surface simple and ship only `/sellcontainer`.

---

## 6. Target resolution rules for `/sellcontainer`

## 6.1 Supported targets in v1

### Mode A: looked-at block container

If the player is looking at a supported storage block within interaction range, `/sellcontainer` should target that container.

Examples of supported block targets in v1:

* chest
* trapped chest
* barrel
* shulker box block

Optional in v1 depending on implementation comfort:

* dispenser
* dropper
* hopper

Recommended default scope for first implementation:

* chest-family
* barrel
* placed shulker box blocks

This keeps the feature focused on obvious storage containers.

### Mode B: held shulker box item

If the player is not targeting a supported block container, but is holding a shulker box item in main hand, `/sellcontainer` should target the contents of that held shulker item.

This gives players a portable-storage liquidation workflow without having to place the shulker first.

## 6.2 Resolution order

Recommended v1 resolution order:

1. looked-at supported block container
2. held shulker box item in main hand
3. fail with a clear message

This order prioritizes the more explicit spatial target first.

## 6.3 Failure message

Example:

* `No supported container found. Look at a chest/barrel/shulker or hold a shulker box.`

---

## 7. `/sellcontainer` behavior rules

## 7.1 What is sold

The command should sell only **eligible contained items** using the same underlying catalog/validation/pricing systems used by the rest of the Exchange.

That means:

* use the existing item normalization/validation rules
* respect item policy state and sell eligibility
* respect current stock-sensitive pricing behavior
* record sales through the existing transaction-log path

## 7.2 What is not sold

The command should **not sell the container itself**.

Examples:

* selling a chest’s contents should leave the chest block in the world
* selling a held shulker’s contents should leave the shulker item in hand (now partially emptied or emptied)

## 7.3 No recursive container liquidation in v1

V1 should **not recursively open nested containers**.

Examples:

* a shulker inside a chest is treated as a skipped item, not opened
* a shulker inside a held shulker is not supported
* container items inside the targeted container remain unsold unless a future feature explicitly expands this

This is a very important scope boundary for safety and implementation simplicity.

## 7.4 Partial success is allowed

`/sellcontainer` should support partial success.

Meaning:

* sell what is valid and eligible
* leave unsupported or non-sellable items untouched
* report skipped items clearly

This mirrors the practical usefulness of `/sellall` while remaining targeted.

---

## 8. Safety and trust rules

## 8.1 Fail closed on ambiguous or unsupported targets

If the command cannot confidently determine a valid supported container target, it should do nothing except show a clear error message.

## 8.2 Respect access boundaries

The feature must not become a bypass for container protection.

Recommended rule:

* only operate on containers the player can legitimately access through normal interaction semantics
* if protection/plugin interaction is uncertain, fail closed rather than forcing access

The exact protection-hook strategy can be decided during implementation, but the design principle is clear: `/sellcontainer` must not bypass ownership or region rules.

## 8.3 Preserve container item safety

For held shulkers, the command must mutate only the container’s contents, not destroy or replace the shulker item itself except as required to update the stored item data.

## 8.4 Inventory mutation only after planning

The command should first build a sale plan, then apply mutations after payout succeeds.

This matches the safer pattern already used elsewhere in the plugin and reduces the chance of item loss on payout failure.

---

## 9. User experience requirements

## 9.1 Success output

The command should behave similarly to `/sellall` in spirit:

* summary line with total earned
* itemized sold lines (possibly truncated for chat friendliness)
* skipped items section where useful

Example:

* `Sold contents of container for a total of 428.50`
* ` - 64x Iron Ingot for 128.00`
* ` - 32x Gunpowder for 64.00`
* `Skipped:`
* ` - Purple Shulker Box x1 (nested container not supported)`

## 9.2 Empty container output

If the target container has no sellable contents:

* show a clear no-op message
* do not mutate the container

Example:

* `No sellable items found in the targeted container.`

## 9.3 Protected shulker messaging in `/sellall`

The player should be able to understand why a shulker was not sold.

Example:

* `Skipped Red Shulker Box x1 (protected container item; use /sellcontainer intentionally)`

This message helps teach the new workflow.

---

## 10. Permissions and command registration

## 10.1 New permission

Recommended new permission node:

* `wild_economy.shop.sellcontainer`

### Default recommendation

* `default: true`

Rationale:

* this is a normal player convenience command, not an admin tool

## 10.2 Command registration

Add top-level command:

* `/sellcontainer`

This should be registered in `plugin.yml` and wired in the same authoritative style as `/sellhand` and `/sellall`.

## 10.3 Optional `/shop` integration

Recommended v1:

* support top-level `/sellcontainer`
* optionally support `/shop sellcontainer` as a secondary routed form for consistency

But the top-level command should be treated as authoritative.

---

## 11. Domain and result-model direction

## 11.1 New service capability

A dedicated service path should be added rather than overloading `sellHand()` or `sellAll()` with ambiguous behavior.

Recommended new service method concept:

* `sellContainer(UUID playerId, ContainerSellTarget target)`

Or equivalent shape appropriate to the codebase.

## 11.2 New result type

Recommended new domain result:

* `SellContainerResult`

Suggested fields:

* success flag
* sold lines
* total earned
* skipped descriptions
* human-readable message
* target description (optional)

This should parallel the existing `SellAllResult` pattern rather than inventing a radically different result model.

## 11.3 Target abstraction

Recommended abstraction:

* `ContainerSellTarget`

Suggested target kinds:

* LOOKED_AT_BLOCK_CONTAINER
* HELD_SHULKER_ITEM

This keeps command parsing separate from sell execution.

---

## 12. Container support boundaries for v1

## 12.1 In scope

Recommended v1 support:

* looked-at chest
* looked-at trapped chest
* looked-at barrel
* looked-at placed shulker box block
* held shulker box item in main hand

## 12.2 Out of scope for v1

To keep the first implementation safe and predictable, the following are out of scope unless explicitly approved later:

* recursive nested-container selling
* selling contents of ender chests
* selling contents of other players’ inaccessible storage via bypass hooks
* minecart containers
* bundles
* auto-selling the empty container item after contents are sold
* preview/confirmation GUI
* mass area/container chaining

---

## 13. Recommended implementation rules

## 13.1 Shared sell logic

The feature should reuse the same core logic as other sell paths for:

* validation
* pricing
* stock mutation
* payout
* transaction logging

Avoid creating a completely separate pricing or persistence path.

## 13.2 Planning before mutation

Implementation should follow this pattern:

1. resolve target container
2. inspect contents
3. validate and quote sellable items
4. build a planned-sale list
5. if no sellable items, stop with no-op message
6. remove only planned sold items from the container
7. attempt payout
8. on payout failure, restore removed items
9. on success, add stock and log sales
10. return result summary

## 13.3 Held-shulker update semantics

For held shulker items, implementation must safely rewrite the item’s stored inventory contents after removing sold items.

It must not accidentally:

* clear the shulker entirely when only part of the contents were sold
* destroy the shulker item itself
* desynchronize the player’s held item state

## 13.4 Skipping nested container items

If a targeted container contains other container items in v1, those should be skipped with an explicit reason.

Example:

* `Blue Shulker Box x1 (nested container not supported)`

---

## 14. Interaction with current pricing and stock model

## 14.1 Soft-cap model remains unchanged

This feature does **not** introduce a hard cap.

The existing stock-cap behavior remains a **soft pricing anchor**, not a strict sell blocker.

That means:

* `/sellcontainer` should use the same graduated pricing/taper logic as other sell paths
* the feature should not introduce hard rejection solely because stock is at or beyond the configured cap anchor

## 14.2 Shulker protection is about accidental sale, not stock restriction

Skipping shulkers in `/sellall` is a safety decision, not an economic decision.

It should be explained and implemented as a trust/protection feature.

---

## 15. Testing scope

## 15.1 `/sellall` shulker protection tests

Must verify:

* `/sellall` skips empty shulker boxes
* `/sellall` skips filled shulker boxes
* `/sellall` still sells normal eligible items around them
* skipped output mentions protected shulkers

## 15.2 `/sellcontainer` looked-at container tests

Must verify:

* chest contents sell correctly
* barrel contents sell correctly
* placed shulker-box contents sell correctly
* non-sellable items remain in place
* nested shulker items are skipped, not opened
* no-op behavior on empty or unsellable containers

## 15.3 `/sellcontainer` held-shulker tests

Must verify:

* held shulker contents sell correctly
* sold items are removed from shulker contents only
* unsold items remain in the shulker
* the shulker item itself remains in hand
* payout failure restores sold items back into the shulker

## 15.4 Safety tests

Must verify:

* command fails clearly when no valid container target exists
* command does not bypass inaccessible/protected container rules
* command does not recurse into nested containers

---

## 16. Rollout recommendation

Recommended rollout order:

### Phase A

* Add shulker protection to `/sellall`
* Teach players through skipped messaging

### Phase B

* Add `/sellcontainer` for looked-at block containers and held shulkers

This sequence improves safety immediately, then adds the intentional workflow.

---

## 17. Final scope decision summary

### Locked intent for this feature

* `/sellall` should be safe around shulkers.
* Shulker boxes should be protected from automatic sale during `/sellall`.
* Intentional liquidation of container contents should happen through `/sellcontainer`.
* `/sellcontainer` should support:

  * looked-at supported storage blocks
  * held shulker box items
* The command should sell contents only, not the container itself.
* V1 should not recurse into nested containers.
* V1 should reuse existing Exchange sell infrastructure wherever possible.
* The current soft stock-cap / graduated pricing model remains unchanged.

This is the recommended v1 scope for safe container-selling behavior in `wild_economy`.
