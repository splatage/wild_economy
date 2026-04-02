# Title settings catalogue notes

This patch cleans up `title-settings.yml` and expands it into a broad shipped catalogue without introducing a second title engine.

## Locked rules

- Relic titles are **cosmetic selectors only**.
- Relic titles are **entitlement-only** and do not duplicate relic progression checks.
- The default visibility mode is **HIDE until eligible/entitled**.
- Best-of-all-time and authority titles are **award-ready** and hidden until their entitlement is granted.
- `%wildeco_title%` supersedes the old ConditionalTextPlaceholders `splatage-member-rank` ladder.

## Title categories included

- Relic Titles
- Best of All Time
- Achievement Titles
- Authority Titles
- Time on Server

## Audit notes

- No parallel title logic was introduced.
- The existing `TitleEligibilityEvaluator` and shared requirement gate service remain the only eligibility path.
- `TitleSource` was widened only to improve category labelling and keep the catalogue readable.
