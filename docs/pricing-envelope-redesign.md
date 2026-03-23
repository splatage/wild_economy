# Pricing envelope redesign

Base revision: `5584f78e8e2f22b6df20a07444853e3b46660a1e`

## Purpose

This note records the pricing redesign direction for the current runtime pricing path.

The old design used ordered fill-ratio bands to taper sell value as stock increased.

The new design uses named reusable eco envelopes:

- a buy price multiplier
- a sell price multiplier
- a minimum stock anchor
- a maximum stock anchor
- a floor price factor

This is easier to explain, easier to hand-edit, and less error-prone than ordered overlapping bands.

## Buy model

Buy behavior remains intentionally simple.

- each purchase action is capped at 64 items
- the player receives a quoted buy price for that one transaction
- that quoted price is honored for that transaction
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

The linear section uses trapezoid / averaged integration.

## Runtime config shape

This is now expressed through reusable references in `exchange-items.yml`:

