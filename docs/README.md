This surgical overlay restores GuiBootstrap to the latest supplied source-of-truth shape from wild_economy-main(22).zip.

Why:
- The compile failure comes from a stale/incorrect working-tree patch that passes TitleMenu into ShopMenuListener.
- In the latest supplied code, ShopMenuListener does NOT accept TitleMenu and should not be part of title routing.
- TitleMenu already has its own holder/listener/command path in source.

What this overlay contains:
- src/main/java/com/splatage/wild_economy/bootstrap/GuiBootstrap.java

Expected outcome:
- Clears the constructor mismatch at GuiBootstrap.java:124.
- Returns the GUI bootstrap seam to the current latest code supplied by the user.
