Title store integration patch for wild_economy-main(24)

Scope
- Expose the existing title chooser through the Store root GUI.

What changed
- Renamed the shipped empty `cosmetics` store category to `titles` so it hits the already-implemented `ShopMenuRouter` special route into `TitleMenu`.
- Updated `StorePresentationFormatter` with `titles` category accenting and description text.

Why this is the right seam
- The latest repo already contains `TitleMenu`, `TitleMenuListener`, `TitleCommand`, title services, placeholders, and a `ShopMenuRouter` special-case for the `titles` category.
- The missing piece was the shipped store category id.
- No fake store products were added; the store section opens the chooser defined by `title-settings.yml`.
