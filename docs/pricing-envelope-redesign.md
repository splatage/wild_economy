# Pricing envelope redesign

Base revision: `d45012340b77da728538fd1e35f197faf18f9274`

## Purpose

This note records the pricing redesign direction agreed for the current runtime pricing audit.

The old design used ordered fill-ratio bands to taper sell value as stock increased.

The new design replaces that with one linear sell envelope per item:

- a full / maximum sell price
- a floor / minimum sell price
- a minimum stock anchor
- a maximum stock anchor

This is easier to explain, easier to hand-edit, and less error-prone than ordered overlapping bands.

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

## Live runtime schema for this slice

This first implementation slice keeps the live runtime schema inline in `exchange-items.yml`:

