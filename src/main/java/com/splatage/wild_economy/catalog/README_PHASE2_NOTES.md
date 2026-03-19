# Phase 2 Catalog Generator Notes

This phase adds rooted recipe derivation.

## Locked rules

- `root-values.yml` is the only anchor source.
- Only anchored items can seed derivation.
- Missing anchors block that derivation path.
- An item may still be included if another complete recipe path resolves from anchored roots.
- The chosen path is the minimum valid derivation depth.
- Depth is measured in craft steps from anchored roots.
- The current default max derivation depth in the facade is `1`.

## Expected behavior example

- `jungle_log` with root value -> included at depth `0`
- `jungle_planks` crafted from `jungle_log` -> included at depth `1`
- `jungle_stairs` crafted from `jungle_planks` -> blocked by default depth limit `1`

## Current simplifications

- value derivation is ingredient total divided by output count
- no crafting tax / bonus yet
- recipe choices only support `MaterialChoice`
- exact-meta recipes are skipped
- very large combinational choice explosions are skipped

## Intended later phases

- config-driven derivation depth instead of facade constant
- generated base catalog as runtime source
- `exchange-items.yml` overrides winning last
- more specific policy heuristics beyond root/derived inclusion
