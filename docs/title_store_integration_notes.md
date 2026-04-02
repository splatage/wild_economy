Title/store integration patch against latest supplied repo (wild_economy-main(23).zip).

What changed
- Added a titles category to the shipped store example.
- Routed the titles category through ShopMenuRouter directly into TitleMenu.
- Passed TitleMenu into GuiBootstrap/ShopMenuRouter at the correct seam.
- Kept ShopMenuListener unchanged.
- Included title inventories in ShopMenuRouter.closeAllShopViews() so reload/shutdown closes them cleanly.
- Added StorePresentationFormatter copy/accent for the titles category.

Why
The latest supplied repo already contains the title chooser backend and GUI driven by title-settings.yml. What was missing was the store route that exposes that chooser to players.
