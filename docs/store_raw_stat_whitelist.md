# Store Raw Statistic Whitelist

This document catalogues the current raw player statistics exposed through the Bukkit/Paper API surface and recommends which ones are strong enough to expose for Store gating.

## Important API note

For player statistics, Paper is exposing the same `org.bukkit.Statistic` model used by Bukkit/Spigot.

In practice this means the Store does not need separate "Bukkit stat" and "Paper stat" requirement families. One raw-stat requirement family with validation is enough.

The underlying API supports three read shapes:

- plain statistic
- material-qualified statistic
- entity-qualified statistic

This is why the clean long-term Store model is:

- raw stat requirement
- optional material qualifier
- optional entity qualifier

with validation against the statistic's type.

## Practical grouping

The current enum can be grouped into the following practical families.

### Time and lifecycle

- `PLAY_ONE_MINUTE`
- `TOTAL_WORLD_TIME`
- `TIME_SINCE_DEATH`
- `TIME_SINCE_REST`
- `LEAVE_GAME`
- `DEATHS`
- `SLEEP_IN_BED`
- `JUMP`

### Movement and travel

- `WALK_ONE_CM`
- `SPRINT_ONE_CM`
- `CROUCH_ONE_CM`
- `SNEAK_TIME`
- `CLIMB_ONE_CM`
- `FALL_ONE_CM`
- `FLY_ONE_CM`
- `AVIATE_ONE_CM`
- `SWIM_ONE_CM`
- `WALK_ON_WATER_ONE_CM`
- `WALK_UNDER_WATER_ONE_CM`
- `BOAT_ONE_CM`
- `MINECART_ONE_CM`
- `HORSE_ONE_CM`
- `PIG_ONE_CM`
- `STRIDER_ONE_CM`
- `NAUTILUS_ONE_CM`
- `HAPPY_GHAST_ONE_CM`

### Combat and damage

- `DAMAGE_DEALT`
- `DAMAGE_TAKEN`
- `DAMAGE_ABSORBED`
- `DAMAGE_RESISTED`
- `DAMAGE_DEALT_ABSORBED`
- `DAMAGE_DEALT_RESISTED`
- `DAMAGE_BLOCKED_BY_SHIELD`
- `MOB_KILLS`
- `PLAYER_KILLS`
- `KILL_ENTITY`
- `ENTITY_KILLED_BY`
- `TARGET_HIT`

### Block and item progression

- `MINE_BLOCK`
- `BREAK_ITEM`
- `CRAFT_ITEM`
- `USE_ITEM`
- `PICKUP`
- `DROP`
- `DROP_COUNT`
- `ITEM_ENCHANTED`

### Villagers, raids, and broader progression signals

- `ANIMALS_BRED`
- `FISH_CAUGHT`
- `TALKED_TO_VILLAGER`
- `TRADED_WITH_VILLAGER`
- `RAID_TRIGGER`
- `RAID_WIN`

### Containers, workstations, and interaction counts

- `BEACON_INTERACTION`
- `BELL_RING`
- `BREWINGSTAND_INTERACTION`
- `CAKE_SLICES_EATEN`
- `CAULDRON_FILLED`
- `CAULDRON_USED`
- `CHEST_OPENED`
- `CLEAN_SHULKER_BOX`
- `DISPENSER_INSPECTED`
- `DROPPER_INSPECTED`
- `ENDERCHEST_OPENED`
- `FLOWER_POTTED`
- `FURNACE_INTERACTION`
- `CRAFTING_TABLE_INTERACTION`
- `HOPPER_INSPECTED`
- `INTERACT_WITH_ANVIL`
- `INTERACT_WITH_BLAST_FURNACE`
- `INTERACT_WITH_CAMPFIRE`
- `INTERACT_WITH_CARTOGRAPHY_TABLE`
- `INTERACT_WITH_GRINDSTONE`
- `INTERACT_WITH_LECTERN`
- `INTERACT_WITH_LOOM`
- `INTERACT_WITH_SMITHING_TABLE`
- `INTERACT_WITH_SMOKER`
- `INTERACT_WITH_STONECUTTER`
- `NOTEBLOCK_PLAYED`
- `NOTEBLOCK_TUNED`
- `OPEN_BARREL`
- `RECORD_PLAYED`
- `SHULKER_BOX_OPENED`
- `TRAPPED_CHEST_TRIGGERED`
- `ARMOR_CLEANED`
- `BANNER_CLEANED`

## Recommended whitelist for Store gating

These are the raw stats that are most useful, legible, and defensible as progression gates.

### Tier 1: strong default inclusions

These are the best default inclusions for general Store gating.

- `PLAY_ONE_MINUTE`
- `WALK_ONE_CM`
- `SPRINT_ONE_CM`
- `SWIM_ONE_CM`
- `AVIATE_ONE_CM`
- `MOB_KILLS`
- `MINE_BLOCK` with material qualification
- `CRAFT_ITEM` with material qualification
- `USE_ITEM` with material qualification
- `TRADED_WITH_VILLAGER`
- `RAID_WIN`
- `KILL_ENTITY` with entity qualification

Why these are strong:

- players can understand them
- they map well to themed progression
- they are less arbitrary than workstation click counters
- they create concrete goals without needing Store-owned telemetry

### Tier 2: good niche inclusions

These are useful when tied to a specific path or theme.

- `FISH_CAUGHT`
- `ANIMALS_BRED`
- `TALKED_TO_VILLAGER`
- `TIME_SINCE_DEATH`
- `TIME_SINCE_REST`
- `BOAT_ONE_CM`
- `HORSE_ONE_CM`
- `STRIDER_ONE_CM`
- `BREAK_ITEM` with material qualification
- `ITEM_ENCHANTED`
- `TARGET_HIT`

These are best used for specific tracks rather than exposed as broad defaults.

### Tier 3: available but weak progression signals

These exist in the API, but they are poor default shop gates.

- chest/barrel/shulker open counts
- hopper/dropper/dispenser inspected counts
- workstation interaction counts
- bell rings
- note block play/tune counts
- flower potted
- armor cleaned
- banner cleaned
- cauldron use/fill counts

These tend to be noisy, easy to cheese, or not very meaningful as gameplay progression.

## Recommended exclusions / cautions

### `PLAYER_KILLS`

Generally avoid for a PvE/community survival server unless you explicitly want PvP-linked unlocks.

### `TOTAL_WORLD_TIME`

This is usually less meaningful than actual player playtime.

### `PLAY_ONE_MINUTE`

Useful, but the name is misleading.

It actually tracks ticks played rather than literal minutes, so thresholds should be documented carefully.

### `DAMAGE_*` families

Available, but usually noisier and less legible than kills, travel, or material-specific progression.

### `ENTITY_KILLED_BY`

Potentially useful, but more naturally suited to challenge or survival difficulty tracks than broad progression.

## Important omission

There is no generic raw `PLACE_BLOCK` statistic exposed in the current enum.

That means a simple raw-stat-only Store system cannot express `blocks placed` directly. If the server still wants placement-based progression, that remains a valid reason to keep a narrow custom-counter path.

## Recommended long-term requirement surface

If the Store moves toward raw native stats as the main progression source, the clean requirement model would be:

- `RAW_STAT`
- optional `material`
- optional `entity`
- `min`

Examples:

### Plain stat

```yml
requirements:
  all-of:
    - type: RAW_STAT
      statistic: PLAY_ONE_MINUTE
      min: 72000
```

### Material-qualified stat

```yml
requirements:
  all-of:
    - type: RAW_STAT
      statistic: MINE_BLOCK
      material: DIAMOND_ORE
      min: 64
```

### Entity-qualified stat

```yml
requirements:
  all-of:
    - type: RAW_STAT
      statistic: KILL_ENTITY
      entity: ENDERMAN
      min: 25
```

## Recommended validation rules

At config load time:

- parse `Statistic` by enum name
- inspect its type
- require no qualifier for untyped stats
- require `material` for block/item substatistics
- require `entity` for entity substatistics
- reject mismatched combinations with a clear config error

This keeps the Store config simple while still exposing the full native stat surface safely.
