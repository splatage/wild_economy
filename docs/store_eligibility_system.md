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

- `ENTITLEMENT`
- `PERMISSION`
- `STATISTIC`
- `STATISTIC_MATERIAL`
- `STATISTIC_ENTITY`
- `ADVANCEMENT`
- `CUSTOM_COUNTER`

## Recommended direction

For shop progression, prefer the raw-stat requirement family plus permissions, entitlements, and advancements.

The current raw-stat model is intentionally narrow and validated against a curated whitelist so invalid or noisy statistics do not quietly leak into the config surface.
