# wild_economy

A curated, Exchange-first Minecraft economy plugin for The Wild.

`wild_economy` is designed around a fast, predictable player shop experience:

* **GUI-driven buying** for a clean browse-and-purchase flow
* **Command-driven selling** for fast liquidation of bulk goods
* **Player-stocked Exchange** as the default mode for useful, standardized materials
* **Unlimited buy-only** reserved for selected nuisance or world-damaging materials
* **Disabled items** for progression-sensitive or gameplay-shortcutting content
* **Stock-sensitive pricing** with soft-cap anchoring and graduated sell-value taper
* **Asynchronous persistence** with in-memory runtime stock for responsive gameplay

## v1 direction

The v1 design is intentionally narrow and opinionated:

* Exchange-first economy, not a broad admin shop clone
* Standardized bulk items are the main focus
* Buying should feel simple and fast
* Selling should be command-first and low friction
* Live stock is authoritative in memory and persisted asynchronously
* Catalog and browse structures are precomputed in memory
* Soft stock caps act as pricing anchors, not hard sell blockers

## Command surface

### Player commands

* `/shop`

  * Opens the shop GUI
* `/shop sellhand`

  * Sells the item in the player’s main hand
* `/shop sellall`

  * Sells all eligible sellable items in the player inventory
* `/shop sellcontainer`

  * Sells the contents of a supported container
* `/sellhand`

  * Top-level authoritative shortcut for selling the item in hand
* `/sellall`

  * Top-level authoritative shortcut for selling all eligible inventory items
* `/sellcontainer`

  * Top-level authoritative command for selling supported container contents

### Admin commands

* `/shopadmin <reload|generatecatalog>`

## Current sell behavior

### `/sellhand`

* Sells the held item if it is canonical, cataloged, and sell-enabled
* Uses the current stock-sensitive pricing model
* Does not hard-block purely because stock is already high

### `/sellall`

* Sells eligible inventory items in bulk
* Uses the same graduated value model as other Exchange sell paths
* Treats stock cap as a soft pricing anchor, not a hard ceiling
* **Protects shulker boxes from accidental sale** during the broad inventory sweep

### `/sellcontainer`

Intended for deliberate bulk liquidation of stored contents.

Supported v1 targets:

* looked-at chest
* looked-at barrel
* looked-at placed shulker box
* held shulker box item in the player’s main hand

Behavior:

* sells eligible contents only
* leaves the container itself intact
* does not recurse into nested containers
* skips unsupported nested container items rather than opening them recursively

## Shulker safety rules

To protect player trust and prevent accidental storage loss:

* filled shulker boxes are **not** treated as canonical sellable Exchange items
* `/sellall` skips shulker box items rather than selling them automatically
* intentional liquidation of shulker contents should happen through `/sellcontainer`

This means:

* carrying a filled shulker will not accidentally sell it as a normal item
* broad cleanup with `/sellall` remains safe around portable storage
* selling container contents is an explicit opt-in action

## Architecture status

The current foundation includes:

* Hikari-backed database access
* in-memory authoritative stock cache
* asynchronous stock persistence
* asynchronous transaction logging
* precomputed catalog and browse indexes
* batched stock flushing with periodic flush behavior
* stock metrics support in the runtime persistence layer

## Documentation

Key design documents live under `docs/`:

* `docs/database-design.md`
* `docs/technical-spec_v1.md`
* `docs/wild_economy-v1_scope.md`
* `docs/sell-container-feature-scope.md`

## Status

Active v1 implementation with:

* Exchange buy flow
* top-level sell commands
* shulker sale protection
* intentional container-content selling
* memory-first stock runtime and async persistence foundation
