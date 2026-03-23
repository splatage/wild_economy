# Pricing envelope redesign

Base revision: `84d841730f5dfa359904f5cb39f5f7581637d863`

## Purpose

This note records the pricing redesign direction agreed for the current runtime pricing work.

The old design used ordered fill-ratio bands to taper sell value as stock increased.

The new design uses one linear sell envelope per item through reusable named `eco-envelope` and `stock-profile` references:

- a base worth
- a buy multiplier
- a sell multiplier
- a minimum stock anchor
- a maximum stock anchor
- a floor price factor

This is easier to explain, easier to hand-edit, and less error-prone than ordered overlapping bands.

## Buy model

Buy behavior remains intentionally simple.

- each purchase action is capped at 64 items
- the player receives a quoted buy price for that one transaction
- that quoted price is honored for that transaction
- later purchases may be priced fresh

For GUI detail-menu buys, there is now one extra hardening rule:

- detail-menu buy quotes are short-lived
- the shown quoted price is honored for a click while that detail-menu quote remains fresh
- stale detail-menu quotes refresh before purchase instead of pinning an old quote indefinitely
- the current detail-menu quote lifetime is 30 seconds

This preserves the user's visible quote expectation without letting long-open menus hold stale buy prices forever.

## Sell model

Sell behavior remains bulk-friendly.

Players may sell a large grouped quantity such as a full inventory or supported container contents.

The shop computes one aggregated payout per item key using the stock interval traversed by that batch.

That payout is piecewise:

1. full-price plateau before the taper begins
2. linear taper through the configured stock range
3. floor-price plateau after the taper completes

The linear section uses trapezoid / averaged integration, which matches the current code style while removing the config complexity of bands.

## Runtime schema

`exchange-items.yml` provides per-item refs or overrides:

