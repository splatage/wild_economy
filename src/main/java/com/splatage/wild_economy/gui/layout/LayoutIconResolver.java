package com.splatage.wild_economy.gui.layout;

import org.bukkit.Material;

public final class LayoutIconResolver {

    public Material resolveGroupIcon(final LayoutGroupDefinition group) {
        final Material configured = this.resolveConfigured(group.icon());
        if (configured != null) {
            return configured;
        }
        return switch (group.key()) {
            case "FARMING_FOOD" -> Material.BREAD;
            case "MINING_MINERALS" -> Material.IRON_PICKAXE;
            case "MOB_DROPS" -> Material.BONE;
            case "BUILDING_MATERIALS" -> Material.BRICKS;
            case "REDSTONE_UTILITIES" -> Material.REDSTONE;
            case "COMBAT_ADVENTURE" -> Material.DIAMOND_SWORD;
            case "MISC" -> Material.CHEST;
            default -> Material.CHEST;
        };
    }

    public Material resolveChildIcon(final LayoutChildDefinition child) {
        final Material configured = this.resolveConfigured(child.icon());
        if (configured != null) {
            return configured;
        }
        return switch (child.key()) {
            case "FARM" -> Material.WHEAT;
            case "FOOD" -> Material.BREAD;
            case "STONE" -> Material.STONE;
            case "WOOD" -> Material.OAK_LOG;
            case "OTHER_BUILDING" -> Material.BRICKS;
            case "BREWING_ENCHANTING" -> Material.BREWING_STAND;
            case "REDSTONE" -> Material.REDSTONE;
            case "TRANSPORT" -> Material.MINECART;
            case "WEAPONS" -> Material.DIAMOND_SWORD;
            case "TOOLS" -> Material.IRON_PICKAXE;
            case "ARMOR" -> Material.IRON_CHESTPLATE;
            case "DECORATION" -> Material.PAINTING;
            case "OTHER" -> Material.CHEST;
            default -> Material.CHEST;
        };
    }

    private Material resolveConfigured(final String icon) {
        if (icon == null || icon.isBlank()) {
            return null;
        }
        return Material.matchMaterial(icon.trim().toUpperCase());
    }
}
