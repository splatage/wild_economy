# wild_economy Folia GriefPrevention adapter patchset

Source-of-truth snapshot: `91eff2f4d2286ad749caa46981404616df19691a`

This patchset continues the Folia migration by replacing the conservative blanket deny for **GriefPrevention** with a real reflection-based access adapter that does **not** rely on a synthetic `PlayerInteractEvent`.

## Why this step

At this commit, the plugin already has:

* `folia-supported: true`
* player-owned and location-owned execution routing
* a dedicated `FoliaContainerSellCoordinator`
* a conservative Folia access strategy that blocks placed-container selling whenever a known protection plugin is installed

That conservative strategy is safe, but it leaves GriefPrevention servers unnecessarily blocked on Folia.

This patch adds:

* `GriefPreventionContainerAccessService`
* updated selection logic in `ContainerAccessServices`
* narrowed fallback blocking for the remaining protection plugins

The adapter is reflection-based so it does not add a compile dependency on GriefPrevention.

---

## File: `src/main/java/com/splatage/wild_economy/integration/protection/GriefPreventionContainerAccessService.java`

```java
package com.splatage.wild_economy.integration.protection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class GriefPreventionContainerAccessService implements ContainerAccessService {

    private static final String GP_PLUGIN_NAME = "GriefPrevention";
    private static final String UNKNOWN_ACCESS_MESSAGE =
        "Could not verify access to that GriefPrevention claim, so the sale was cancelled.";

    private final Plugin griefPreventionPlugin;

    public GriefPreventionContainerAccessService(final Plugin griefPreventionPlugin) {
        this.griefPreventionPlugin = Objects.requireNonNull(griefPreventionPlugin, "griefPreventionPlugin");
    }

    @Override
    public ContainerAccessResult canAccessPlacedContainer(final Player player, final Block targetBlock) {
        if (player == null || targetBlock == null) {
            return ContainerAccessResult.deny(UNKNOWN_ACCESS_MESSAGE);
        }

        try {
            final Object griefPrevention = this.resolvePluginSingleton();
            final Object dataStore = this.readField(griefPrevention.getClass(), griefPrevention, "dataStore");
            if (dataStore == null) {
                return ContainerAccessResult.deny(UNKNOWN_ACCESS_MESSAGE);
            }

            final Object claim = this.findClaimAt(dataStore, targetBlock.getLocation());
            if (claim == null) {
                return ContainerAccessResult.allow();
            }

            final String denialMessage = this.callAllowContainers(claim, player);
            if (denialMessage == null || denialMessage.isBlank()) {
                return ContainerAccessResult.allow();
            }

            return ContainerAccessResult.deny(denialMessage);
        } catch (final Throwable ignored) {
            return ContainerAccessResult.deny(UNKNOWN_ACCESS_MESSAGE);
        }
    }

    private Object resolvePluginSingleton() throws Exception {
        final Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");

        try {
            final Field instanceField = gpClass.getField("instance");
            final Object singleton = instanceField.get(null);
            if (singleton != null) {
                return singleton;
            }
        } catch (final NoSuchFieldException ignored) {
            // Fall through to plugin instance fallback.
        }

        if (gpClass.isInstance(this.griefPreventionPlugin)) {
            return this.griefPreventionPlugin;
        }

        throw new IllegalStateException("Unable to resolve GriefPrevention singleton");
    }

    private Object findClaimAt(final Object dataStore, final Location location) throws Exception {
        for (final Method method : dataStore.getClass().getMethods()) {
            if (!method.getName().equals("getClaimAt")) {
                continue;
            }

            final Class<?>[] parameterTypes = method.getParameterTypes();

            if (parameterTypes.length == 4
                && parameterTypes[0] == Location.class
                && parameterTypes[1] == boolean.class
                && parameterTypes[2] == boolean.class) {
                return method.invoke(dataStore, location, true, false, null);
            }

            if (parameterTypes.length == 3
                && parameterTypes[0] == Location.class
                && parameterTypes[1] == boolean.class) {
                return method.invoke(dataStore, location, true, null);
            }
        }

        throw new NoSuchMethodException("No compatible getClaimAt method found on " + dataStore.getClass().getName());
    }

    private String callAllowContainers(final Object claim, final Player player) throws Exception {
        final Method allowContainers = claim.getClass().getMethod("allowContainers", Player.class);
        final Object result = allowContainers.invoke(claim, player);
        return result == null ? null : String.valueOf(result);
    }

    private Object readField(final Class<?> ownerClass, final Object instance, final String fieldName) throws Exception {
        final Field field = ownerClass.getField(fieldName);
        return field.get(instance);
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/integration/protection/ProtectionPluginAwareFoliaContainerAccessService.java`

```java
package com.splatage.wild_economy.integration.protection;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class ProtectionPluginAwareFoliaContainerAccessService implements ContainerAccessService {

    private static final List<String> UNSUPPORTED_PROTECTION_PLUGINS = List.of(
        "PlotSquared",
        "Towny",
        "Lands",
        "HuskClaims",
        "Residence",
        "Factions",
        "FactionsUUID",
        "SaberFactions",
        "MassiveCore",
        "KingdomsX"
    );

    private final String detectedProtectionPlugin;

    public ProtectionPluginAwareFoliaContainerAccessService() {
        this.detectedProtectionPlugin = this.detectProtectionPlugin();
    }

    @Override
    public ContainerAccessResult canAccessPlacedContainer(final Player player, final Block targetBlock) {
        if (this.detectedProtectionPlugin == null) {
            return ContainerAccessResult.allow();
        }

        return ContainerAccessResult.deny(
            "Placed container selling is temporarily disabled on Folia while protection plugin '"
                + this.detectedProtectionPlugin
                + "' is installed. Held shulker selling still works."
        );
    }

    private String detectProtectionPlugin() {
        for (final String pluginName : UNSUPPORTED_PROTECTION_PLUGINS) {
            if (Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
                return pluginName;
            }
        }
        return null;
    }
}
```

---

## File: `src/main/java/com/splatage/wild_economy/integration/protection/ContainerAccessServices.java`

```java
package com.splatage.wild_economy.integration.protection;

import com.splatage.wild_economy.platform.PlatformSupport;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class ContainerAccessServices {

    private static final String GRIEF_PREVENTION_PLUGIN = "GriefPrevention";

    private ContainerAccessServices() {
    }

    public static ContainerAccessService createDefault() {
        if (!PlatformSupport.isFolia()) {
            return new EventDrivenContainerAccessService();
        }

        final Plugin griefPrevention = Bukkit.getPluginManager().getPlugin(GRIEF_PREVENTION_PLUGIN);
        if (griefPrevention != null && griefPrevention.isEnabled()) {
            return new GriefPreventionContainerAccessService(griefPrevention);
        }

        return new ProtectionPluginAwareFoliaContainerAccessService();
    }
}
```

---

## Notes

* This patch intentionally uses reflection so the plugin can keep GriefPrevention optional and avoid a compile-only dependency.
* The GriefPrevention adapter targets the long-standing Bukkit-side `dataStore.getClaimAt(...)` path and the `Claim.allowContainers(Player)` check.
* The remaining unsupported protection plugins still use the current conservative Folia fallback deny path.
* The next step after this patch is to add explicit region-safe adapters for PlotSquared and Towny, because those are the next most common claim systems in the current support list.
