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
