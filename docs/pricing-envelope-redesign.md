# Pricing envelope redesign

Base revision: `b3a28263ada706483b21236a677f4d55edf6a89d`

## Purpose

This note records the pricing redesign direction agreed for the current runtime pricing audit.

The old design used ordered fill-ratio bands and disconnected stock-profile defaults.

The current direction now has three concrete runtime slices:

1. replace sell bands with a linear sell envelope
2. wire reusable named `eco-envelope` and `stock-profile` refs into the live runtime path
3. seed first-run stock from profile or item-level `initial-stock` values without ever resetting persisted live stock on restart

## Buy model

Buy behavior remains intentionally simple.

- each purchase action is capped at 64 items
- the player receives a quoted buy price for that one transaction
- that quoted price must be honored for that transaction
- in the item detail GUI, the displayed unit price is captured when the menu opens and reused for the Buy 1 / 8 / 64 click path
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

