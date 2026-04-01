Title/rank scope — slice: player-facing title selection

This slice folds the existing title backend into a usable player-facing feature.

Included:
- /titles command (alias: /title)
- paged title selection GUI
- active-title summary and clear action
- title option rendering with status (Active / Available / Locked)
- selection persistence through the existing PDC-backed TitleSelectionService
- immediate cache refresh through ResolvedTitleService after selection changes
- expanded shipped title-settings.yml so all relic-hall prestige titles appear as selectable earned titles
- existing bread commerce examples kept as sample non-relic title families

Notes:
- This slice does not implement commerce leaderboard evaluation. It only surfaces title options whose entitlements already exist.
- This slice does not add rank-permission overlays. It focuses on earned title selection and display.
- The title menu is deliberately separate from store purchasing. It behaves like settings, not a shop.
