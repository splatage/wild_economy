# Pricing envelope redesign

Base revision: `891e2ddf184b446a63e03b63afdd8d1185b001f6`

## Purpose

This note records the pricing redesign direction agreed for the current runtime pricing audit.

The old design used ordered fill-ratio bands and disconnected stock-profile defaults.

The current direction now has four concrete runtime slices:

1. replace sell bands with a linear sell envelope
2. wire reusable named `eco-envelope` and `stock-profile` refs into the live runtime path
3. seed first-run stock from profile or item-level `initial-stock` values without ever resetting persisted live stock on restart
4. fail fast on contradictory or dangerous pricing config instead of silently tolerating it

## Buy model

Buy behavior remains intentionally simple.

- each purchase action is capped at 64 items
- the player receives a quoted buy price for that one transaction
- that quoted price must be honored for that transaction
- a later purchase may be priced fresh

This keeps purchase behavior predictable and prevents in-transaction repricing surprises.

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

### `exchange-items.yml`

Named refs stay the normal runtime path:

