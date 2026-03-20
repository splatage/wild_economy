# wild_economy Feature Scope: Shulker Protection in `/sellall` and `/sellcontainer`

## Status

Current feature scope for the sell-path safety and usability slice.

This document defines the intended scope for two related features:

1. **Protect shulkers from accidental sale during `/sellall`**
2. **Provide an intentional container-selling command: `/sellcontainer`**

These features are designed to work with the current `wild_economy` architecture, where:

- stock is memory-first and persisted asynchronously
- catalog and browse structures are in memory
- sell flows aggregate by item key before pricing and stock mutation
- top-level `/sellhand`, `/sellall`, and `/sellcontainer` are part of the command surface

---

## 1. Problem statement

### 1.1 Accidental shulker loss risk in `/sellall`

Players are accustomed to using broad sell commands such as `/sellall` for fast inventory cleanup.
That convenience becomes dangerous when players carry valuable shulker boxes in their inventory.

Even if the intention is to sell ordinary bulk items, players may accidentally include a shulker box in the sell pass and lose either:

- the shulker item itself
- the contents of the shulker indirectly or by misunderstanding
- confidence in the command because it feels unsafe to use around portable storage

For `wild_economy`, `/sellall` should feel safe and predictable.

### 1.2 Missing intentional container-sell workflow

Players also need a fast way to liquidate the contents of a container when that is the actual intention.

Examples:

- a chest or barrel they are looking at after farm collection
- a shulker box they are holding after bringing items back from a project or trip

The plugin now has a dedicated container-selling path, but the scope still needs to stay clear and constrained.

---

## 2. High-level feature goals

### Primary goals

- Make `/sellall` safe to use around shulker boxes.
- Provide an explicit command for selling container contents on purpose.
- Preserve player trust by making destructive behavior opt-in, not accidental.
- Reuse existing catalog, validation, pricing, stock, economy, and transaction-log systems where possible.
- Keep command behavior simple, fast, and easy to understand.

### Secondary goals

- Keep implementation aligned with current `wild_economy` architecture.
- Avoid unnecessary main-thread database work.
- Avoid feature creep such as recursive nested-container liquidation in v1.
- Keep the command surface intuitive for players familiar with `/sellhand` and `/sellall`.

---

## 3. Scope summary

This feature set contains two related behaviors:

### A. `/sellall` shulker protection

`/sellall` must **not automatically sell shulker boxes**.

Required behavior:

- shulker box item stacks are skipped during `/sellall`
- skipped shulkers are reported in the skipped summary
- this protection applies whether the shulker is empty or contains items

Rationale:

- the player expectation is safety
- intentional container liquidation should happen through `/sellcontainer`, not through a broad inventory sweep

### B. `/sellcontainer`

`/sellcontainer` is the intentional command for selling the **contents** of a container.

Supported v1 targets:

- the block container the player is currently looking at
- a shulker box held in the player’s main hand

Result:

- sell eligible contents from the chosen container
- aggregate by item key before pricing and stock mutation
- leave the container itself intact
- do not recurse into nested containers

---

## 4. Detailed `/sellall` shulker-protection scope

### 4.1 Required behavior

When `/sellall` scans the player inventory:

- any shulker box item stack must be excluded from sale planning
- the stack must remain untouched in the player inventory
- the command must continue processing other sellable items normally
- skipped shulker stacks should be included in the skipped output

### 4.2 Recommended user-facing messaging

Examples:

- `Skipped Purple Shulker Box x1 (protected container item)`
- `Skipped 2 protected shulker container(s)`

The wording should communicate that the skip is intentional safety behavior, not an error.

### 4.3 Why protection should apply to all shulkers, not only filled ones

Recommended default policy:

- `/sellall` skips all shulker box items, regardless of contents

Reasoning:

- players use `/sellall` as a broad cleanup command
- accidental sale of even an empty shulker can still feel wrong
- a simple rule is easier to remember and trust
- intentional shulker-related liquidation belongs under `/sellcontainer`

### 4.4 Explicit non-goals for this protection slice

This slice should **not**:

- inspect and liquidate nested container contents as part of `/sellall`
- recurse into container contents during `/sellall`
- weaken held shulker safety protections

---

## 5. Detailed `/sellcontainer` scope

### 5.1 Command purpose

`/sellcontainer` is the intentional command for selling the contents of a container.

It should feel like:

- the safe, explicit counterpart to `/sellall`
- the bulk-liquidation tool for organized storage
- the command players use when they deliberately want to empty a container into cash

### 5.2 Command shape

### Primary command

- `/sellcontainer`

### Optional future aliases

Not required for v1.

For v1, keep the surface simple and ship only `/sellcontainer`.

---

## 6. Target resolution rules for `/sellcontainer`

### 6.1 Supported targets in v1

#### Mode A: looked-at block container

If the player is looking at a supported storage block within interaction range, `/sellcontainer` should target that container.

Supported block targets:

- chest
- barrel
- placed shulker box

#### Mode B: held shulker box item

If the player is not targeting a supported block container, the command may fall back to a shulker box item in the player’s main hand.

This supports portable bulk liquidation while keeping the action explicit.

### 6.2 Protection rule for held shulkers

Held shulker boxes being sold through `/sellcontainer` are treated as belonging to the player holding them.
They do **not** require world-container ownership/protection checks.

Protection/access checks apply only to **placed world containers**.

---

## 7. Pricing and stock behavior

Container selling uses the same Exchange economics model as other sell flows.

For each sell action:

- contents are normalized and validated
- sellable items are aggregated by item key
- one stock snapshot is read per sold item key
- one batch quote is produced per item key
- one stock add is applied per item key after the payout succeeds

Soft-cap behavior remains the same:

- stock caps are pricing anchors, not hard sell blockers
- higher stock reduces sell value
- at or above cap, sell value floors at the configured minimum band
- container selling does not introduce hard stock-cap rejection logic

---

## 8. Nested container rules

To keep v1 behavior safe and understandable:

- do not recurse into nested containers
- skip nested container items rather than opening them recursively
- leave the outer container itself intact

This keeps the feature deliberate and avoids unexpected deep liquidation behavior.

---

## 9. Implementation priorities

The important implementation priorities for this feature area are:

1. preserve shulker safety and player trust
2. keep sell planning grouped by item key rather than per slot
3. keep pricing and in-memory stock mutation on the gameplay path
4. keep DB persistence and transaction logging asynchronous
5. avoid feature creep into recursive container automation

That is the intended v1 scope for container-related selling.
