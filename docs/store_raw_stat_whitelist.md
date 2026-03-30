# Store Raw Statistic Whitelist

This document catalogues the raw player statistics exposed through the Bukkit/Paper API surface and records the curated subset recommended for Store gating.

## Important API note

For player statistics, Paper is exposing the same `org.bukkit.Statistic` model used by Bukkit/Spigot.

In practice this means the Store does not need separate "Bukkit stat" and "Paper stat" requirement families. One validated raw-stat family is enough.

The underlying API supports three read shapes:

- plain statistic
- material-qualified statistic
- entity-qualified statistic

## Curated whitelist

### Plain statistics (`STATISTIC`)

- `PLAY_ONE_MINUTE`
- `WALK_ONE_CM`
- `SPRINT_ONE_CM`
- `SWIM_ONE_CM`
- `AVIATE_ONE_CM`
- `JUMP`
- `MOB_KILLS`
- `PLAYER_KILLS`
- `ANIMALS_BRED`
- `FISH_CAUGHT`
- `TALKED_TO_VILLAGER`
- `TRADED_WITH_VILLAGER`
- `RAID_TRIGGER`
- `RAID_WIN`
- `DEATHS`
- `TIME_SINCE_DEATH`
- `TIME_SINCE_REST`
- `TARGET_HIT`

### Material-qualified statistics (`STATISTIC_MATERIAL`)

- `MINE_BLOCK`
- `CRAFT_ITEM`
- `USE_ITEM`
- `BREAK_ITEM`
- `PICKUP`
- `DROP`

### Entity-qualified statistics (`STATISTIC_ENTITY`)

- `KILL_ENTITY`
- `ENTITY_KILLED_BY`

## Notable caveats

- `PLAY_ONE_MINUTE` is a misleading enum name. It actually tracks played ticks.
- There is no generic raw `PLACE_BLOCK` statistic in the current API surface.
- The Store loader now validates raw-stat requirements against this whitelist and against the qualifier type required by the underlying Bukkit statistic.

## Recommended config shape

```yml
requirements:
  all-of:
    - type: STATISTIC
      statistic: PLAY_ONE_MINUTE
      min: 72000
    - type: STATISTIC_MATERIAL
      statistic: MINE_BLOCK
      material: DEEPSLATE_DIAMOND_ORE
      min: 64
    - type: STATISTIC_ENTITY
      statistic: KILL_ENTITY
      entity: ENDERMAN
      min: 25
```
