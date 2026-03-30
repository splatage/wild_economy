# Store Eligibility System

This document describes the current Store-owned gating system in `wild_economy`.

## Purpose

The Store now uses one shared eligibility system for:

- top-level category visibility
- category access
- product visibility
- product examination/detail viewing
- purchase enforcement

The important design rule is that the Store owns this logic. The GUI does not invent its own separate rules, and the purchase path does not bypass the same checks the player sees in menus.

## Current model

Both `StoreCategory` and `StoreProduct` can declare:

- `requirements`
- `visibility-when-unmet`
- `locked-message`

This means a section or item can be:

- completely hidden when requirements are not met
- visible but locked when requirements are not met

If visible-but-locked, the player should still be able to inspect the item and understand what they need to do next.

## Visibility policy

Supported values:

- `HIDE`
- `SHOW_LOCKED`

Behavior:

- `HIDE`: the category or product does not appear for unmet players
- `SHOW_LOCKED`: the category or product appears, but cannot be acquired until requirements are met

If `visibility-when-unmet` is omitted, the loader currently defaults it to `SHOW_LOCKED`.

## Locked messaging

`locked-message` is optional and intended for motivational or aspirational messaging.

Examples:

- `Support the server to unlock this section.`
- `Reach the Nether to unlock this path.`
- `Keep building to unlock this path.`

This message should complement the factual requirement/progress lines rather than replace them.

## Requirement model

The current loader supports a single requirement group:

```yml
requirements:
  all-of:
    - type: ...
      ...
```

`all-of` means every listed requirement must be satisfied.

There is currently no `any-of` / OR group support.

## Current requirement types

### `ENTITLEMENT`

Requires that the player already owns another permanent Store unlock.

Accepted keys:

- `key`
- `entitlement`

Example:

```yml
requirements:
  all-of:
    - type: ENTITLEMENT
      entitlement: "builder.1"
```

### `PERMISSION`

Requires that the player has a Bukkit/Paper permission node.

Accepted key:

- `node`

Example:

```yml
requirements:
  all-of:
    - type: PERMISSION
      node: "wild.store.vip"
```

### `STATISTIC`

Requires that a raw Bukkit/Paper statistic reaches a minimum value.

Accepted keys:

- `statistic`
- `min`

Example:

```yml
requirements:
  all-of:
    - type: STATISTIC
      statistic: "PLAY_ONE_MINUTE"
      min: 72000
```

### `STATISTIC_MATERIAL`

Requires that a material-qualified Bukkit/Paper statistic reaches a minimum value.

Accepted keys:

- `statistic`
- `material`
- `min`

Example:

```yml
requirements:
  all-of:
    - type: STATISTIC_MATERIAL
      statistic: "MINE_BLOCK"
      material: "DIAMOND_ORE"
      min: 64
```

### `ADVANCEMENT`

Requires that the player has completed a Minecraft advancement.

Accepted keys:

- `key`
- `advancement`

Example:

```yml
requirements:
  all-of:
    - type: ADVANCEMENT
      advancement: "minecraft:story/enter_the_nether"
```

### `CUSTOM_COUNTER`

Requires that a Store-owned custom counter reaches a minimum value.

Accepted keys:

- `key`
- `counter`
- `counter-key`
- `min`

Example:

```yml
requirements:
  all-of:
    - type: CUSTOM_COUNTER
      counter: "blocks_placed"
      min: 500
```

## Tier sequencing

Permanent unlock entitlement keys with a dotted numeric suffix are treated as sequential tiers.

Examples:

- `builder.1`
- `builder.2`
- `builder.3`

Current behavior:

- tier `N` requires ownership of tier `N-1`
- tier `1` has no prior-tier requirement
- zero-padded keys remain sequential if the naming is consistent

This sequencing is implicit from the entitlement key format. It is not declared as a separate config rule.

## Tier cooldown

The Store can also enforce a cooldown between purchases within one dotted numeric track.

Current config source:

- `tiered-track-purchase-cooldown-seconds` in `config.yml`

Behavior:

- only applies to tiered permanent unlock tracks
- does not affect unrelated product ids
- does not affect repeatable grants or XP withdrawals

## Current custom counters

The current shipped examples use block-placement counters, including:

- `blocks_placed`
- `blocks_placed.<material>`
- `blocks_placed.survival`
- `blocks_placed.survival.<material>`

These exist because not every progression idea is represented directly by native Bukkit/Paper statistics.

## Current practical examples

### Hidden category

```yml
categories:
  vip:
    display-name: "VIP"
    icon: TOTEM_OF_UNDYING
    slot: 10
    visibility-when-unmet: HIDE
    locked-message: "Support the server to unlock this section."
    requirements:
      all-of:
        - type: PERMISSION
          node: "wild.store.vip"
```

### Visible-but-locked advancement-gated product

```yml
products:
  nether_traveler:
    category: perks
    type: PERMANENT_UNLOCK
    display-name: "&5Nether Traveler"
    icon: CRYING_OBSIDIAN
    price: 12000.00
    entitlement-key: "perk.nether_traveler"
    confirm: true
    visibility-when-unmet: SHOW_LOCKED
    locked-message: "Reach the Nether to unlock this path."
    requirements:
      all-of:
        - type: ADVANCEMENT
          advancement: "minecraft:story/enter_the_nether"
```

### Visible-but-locked counter-gated product

```yml
products:
  builder_path_1:
    category: perks
    type: PERMANENT_UNLOCK
    display-name: "&bBuilder Path I"
    icon: BRICKS
    price: 8000.00
    entitlement-key: "builder.1"
    confirm: true
    visibility-when-unmet: SHOW_LOCKED
    locked-message: "Keep building to unlock this path."
    requirements:
      all-of:
        - type: CUSTOM_COUNTER
          counter: "blocks_placed"
          min: 500
```

## Current limits

The current implementation is intentionally narrow.

Notable current limits:

- requirement groups are `all-of` only
- no explicit `STATISTIC_ENTITY` requirement type yet
- dotted numeric tiering is implicit rather than explicitly configurable
- custom counters are still a small curated set rather than a full progression language

## Recommended next direction

For vanilla progression gates, the cleanest next step is to lean more heavily on raw Bukkit/Paper statistics and keep custom counters only for semantics that the API does not expose natively.

See `docs/store_raw_stat_whitelist.md`.
