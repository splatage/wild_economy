This slice humanizes requirement progress units in store/title gating output.

Included changes:
- Converts distance statistics from centimetres to blocks for progress lines:
  - WALK_ONE_CM
  - SPRINT_ONE_CM
  - SWIM_ONE_CM
  - AVIATE_ONE_CM
- Converts tick-based timing statistics to readable durations:
  - PLAY_ONE_MINUTE
  - TIME_SINCE_DEATH
  - TIME_SINCE_REST
- Humanizes requirement labels:
  - "Enderman killed"
  - "Firework Rocket used"
  - "Blocks placed in survival"
  - etc.
- Adds grouping separators for large counts.
- Updates and extends eligibility tests for the new display format.

Scope note:
- This changes presentation only. Internal gating thresholds remain unchanged in raw underlying units.
- Existing config values such as WALK_ONE_CM minima continue to be defined in Bukkit statistics units; they are only displayed to players in clearer units.
