This patch is based only on wild_economy-main(23).zip.

It exposes the existing TitleMenu through the store by adding a `titles` category and routing that category in ShopMenuRouter.

Key compile-safety correction:
- ShopMenuListener remains unchanged.
- TitleMenu is injected into ShopMenuRouter via GuiBootstrap and ServiceRegistry.
- GuiBootstrap does not pass TitleMenu into ShopMenuListener.
