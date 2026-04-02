Patched against wild_economy-main(25).zip.

Change:
- The exit button in TitleMenu now returns the player to the store root via ShopMenuRouter instead of simply closing the inventory.
- TitleMenu now has a ShopMenuRouter setter, wired from GuiBootstrap after the router is constructed.
- Fallback remains safe: if no router is set, the menu still closes normally.
