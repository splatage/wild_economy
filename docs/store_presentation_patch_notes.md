Store presentation patch scope

Included files:
- StorePresentationFormatter.java
- StoreRootMenu.java
- StoreCategoryMenu.java
- StoreProductDetailMenu.java

Completed scope:
- Human-friendly purchase wording instead of raw enum names
- Human-friendly status wording instead of "Ownership: Unlocked"
- Relic hall path-aware accent colours
- Non-italic styled names and lore via explicit legacy formatting
- Hidden default item attributes / additional tooltip where API exposes the flag
- Better root category presentation and browse prompts
- Better detail purchase button naming and state handling

Notes:
- This patch changes the renderer. It does not require rewriting the shipped store-products.yml content.
- Gradle compile/test could not be run in the container because the wrapper attempted to download gradle-8.14.3 from services.gradle.org and network access was unavailable.
