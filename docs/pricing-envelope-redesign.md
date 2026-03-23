# Pricing envelope redesign

Base revision: `b9910a2b970eb60430dfc674f4881dbf271e40ed`

## Purpose

This note records the pricing redesign direction agreed for the current runtime pricing audit.

The old design used ordered fill-ratio bands and disconnected stock-profile defaults.

The new direction keeps the linear sell model from the first slice, then wires reusable named pricing and stock defaults into the live runtime path.

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

## Runtime schema in this slice

This slice moves the live runtime path to named reusable refs.

### exchange-items.yml

