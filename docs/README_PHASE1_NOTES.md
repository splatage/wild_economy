# Phase 1 Catalog Generator Notes

This phase intentionally keeps the generator simple.

Included:
- scan Bukkit `Material` item universe
- normalize item keys to lowercase snake_case matching Bukkit material names
- import prices from Essentials `worth.yml`
- classify top-level category
- suggest default policy
- write generated catalog YAML

Not yet included:
- recipe graph
- derivation depth
- root/basic item configuration
- override merge
- command wiring

## Current default policy logic

1. hard-disabled items -> `DISABLED`
2. explicit always-available allowlist -> `ALWAYS_AVAILABLE`
3. remaining items with worth -> `EXCHANGE`
4. everything else -> `DISABLED`

This matches the current v1 testing goal:

> If a valid standard item is present in `worth.yml`, default it into the Exchange unless a hard exclusion says otherwise.

## Intended next phase

Phase 2 adds:
- recipe graph extraction from Bukkit recipes
- minimum derivation depth from configured root/basic items
- depth-based inclusion/exclusion to reduce clutter
- overrides merged last
