# Title Selection Surface Patch Notes

This patch completes the missing player-facing slice of the landed title backend.

## Included
- `/titles` command with `/title` alias
- `/titles clear` to clear the current manual selection and return to automatic best-available fallback
- paged `TitleMenu` GUI
- `TitleMenuListener`
- shipped `title-settings.yml` expanded to the full relic hall catalogue plus the existing bread commerce examples
- deterministic ordering improvement in `TitleSettingsConfig.orderedTitles()`

## Deliberate scope
This patch builds on the landed Phase 1 backend and does **not** replace it.

It does **not** implement:
- supporter title families
- commerce crown evaluation jobs
- richer persisted display-mode selection
- settings routing from another GUI surface

## Runtime behavior
- if the player selects an eligible title, that key becomes the manual active title
- if the selected title is cleared or later becomes ineligible, the runtime falls back to the highest-priority eligible title
- `%wildeco_title%` continues to read from the resolved-title cache path
