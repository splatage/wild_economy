package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.config.TitleSettingsConfig;
import com.splatage.wild_economy.store.eligibility.StoreEligibilityResult;
import com.splatage.wild_economy.title.eligibility.TitleEligibilityEvaluator;
import com.splatage.wild_economy.title.model.ResolvedTitle;
import com.splatage.wild_economy.title.model.TitleOption;
import com.splatage.wild_economy.title.service.ResolvedTitleService;
import com.splatage.wild_economy.title.service.TitleSelectionService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class TitleMenu {

    private static final int INVENTORY_SIZE = 54;
    private static final int PAGE_SIZE = 45;

    private final TitleSettingsConfig titleSettingsConfig;
    private final TitleEligibilityEvaluator titleEligibilityEvaluator;
    private final TitleSelectionService titleSelectionService;
    private final ResolvedTitleService resolvedTitleService;
    private final TitlePresentationFormatter presentationFormatter = new TitlePresentationFormatter();

    public TitleMenu(
            final TitleSettingsConfig titleSettingsConfig,
            final TitleEligibilityEvaluator titleEligibilityEvaluator,
            final TitleSelectionService titleSelectionService,
            final ResolvedTitleService resolvedTitleService
    ) {
        this.titleSettingsConfig = Objects.requireNonNull(titleSettingsConfig, "titleSettingsConfig");
        this.titleEligibilityEvaluator = Objects.requireNonNull(titleEligibilityEvaluator, "titleEligibilityEvaluator");
        this.titleSelectionService = Objects.requireNonNull(titleSelectionService, "titleSelectionService");
        this.resolvedTitleService = Objects.requireNonNull(resolvedTitleService, "resolvedTitleService");
    }

    public void open(final Player player, final int page) {
        final int safePage = Math.max(0, page);
        final TitleMenuHolder holder = TitleMenuHolder.page(safePage);
        final Inventory inventory = holder.createInventory(INVENTORY_SIZE, this.presentationFormatter.menuTitle(safePage));
        final String activeTitleKey = this.resolvedTitleService.getResolvedTitle(player)
                .map(ResolvedTitle::key)
                .orElse("");

        for (final EvaluatedTitle evaluatedTitle : this.titlesForPage(player, safePage)) {
            inventory.setItem(
                    evaluatedTitle.pageSlot(),
                    this.titleItem(evaluatedTitle.option(), evaluatedTitle.eligibility(), evaluatedTitle.option().key().equals(activeTitleKey))
            );
        }

        if (safePage > 0) {
            inventory.setItem(45, this.button(Material.ARROW, this.presentationFormatter.previousButtonName(), null));
        }
        inventory.setItem(48, this.button(Material.BARRIER, this.presentationFormatter.closeButtonName(), null));
        inventory.setItem(49, this.summaryItem(player));
        inventory.setItem(50, this.clearItem(player));
        if (safePage < this.maxPage(player)) {
            inventory.setItem(53, this.button(Material.ARROW, this.presentationFormatter.nextButtonName(), null));
        }

        player.openInventory(inventory);
    }

    public void handleClick(final InventoryClickEvent event, final int page) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= INVENTORY_SIZE) {
            return;
        }
        if (rawSlot < PAGE_SIZE) {
            for (final EvaluatedTitle evaluatedTitle : this.titlesForPage(player, page)) {
                if (evaluatedTitle.pageSlot() == rawSlot) {
                    this.handleTitleSelection(player, page, evaluatedTitle);
                    return;
                }
            }
            return;
        }

        switch (rawSlot) {
            case 45 -> {
                if (page > 0) {
                    this.open(player, page - 1);
                }
            }
            case 48 -> player.closeInventory();
            case 50 -> {
                this.titleSelectionService.clearSelectedTitleKey(player);
                this.resolvedTitleService.invalidate(player.getUniqueId());
                this.resolvedTitleService.warm(player);
                player.sendMessage("Your active title has been cleared. Automatic title selection is now in effect.");
                this.open(player, page);
            }
            case 53 -> {
                if (page < this.maxPage(player)) {
                    this.open(player, page + 1);
                }
            }
            default -> {
            }
        }
    }

    private void handleTitleSelection(final Player player, final int page, final EvaluatedTitle evaluatedTitle) {
        if (!evaluatedTitle.eligibility().acquirable()) {
            if (evaluatedTitle.eligibility().blockedMessage() != null && !evaluatedTitle.eligibility().blockedMessage().isBlank()) {
                player.sendMessage(evaluatedTitle.eligibility().blockedMessage());
            }
            if (evaluatedTitle.eligibility().inspirationalMessage() != null && !evaluatedTitle.eligibility().inspirationalMessage().isBlank()) {
                player.sendMessage(evaluatedTitle.eligibility().inspirationalMessage());
            }
            return;
        }
        this.titleSelectionService.setSelectedTitleKey(player, evaluatedTitle.option().key());
        this.resolvedTitleService.invalidate(player.getUniqueId());
        this.resolvedTitleService.warm(player);
        player.sendMessage("Active title set to " + evaluatedTitle.option().titleText() + ".");
        this.open(player, page);
    }

    private ItemStack titleItem(final TitleOption option, final StoreEligibilityResult eligibility, final boolean active) {
        final Material material = this.resolveMaterial(option.icon());
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(this.presentationFormatter.titleDisplayName(option, active));
            meta.setLore(this.presentationFormatter.titleLore(option, eligibility, active));
            this.presentationFormatter.applyMenuFlags(meta);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack summaryItem(final Player player) {
        final ItemStack stack = new ItemStack(Material.NAME_TAG);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            final Optional<ResolvedTitle> resolvedTitle = this.resolvedTitleService.getResolvedTitle(player);
            final Optional<TitleOption> selectedTitle = this.titleSelectionService.getSelectedTitleKey(player)
                    .map(this.titleSettingsConfig.titles()::get);
            meta.setDisplayName(this.presentationFormatter.summaryDisplayName());
            meta.setLore(this.presentationFormatter.summaryLore(resolvedTitle, selectedTitle));
            this.presentationFormatter.applyMenuFlags(meta);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack clearItem(final Player player) {
        final boolean automaticMode = this.titleSelectionService.getSelectedTitleKey(player).isEmpty();
        final Material material = automaticMode ? Material.GRAY_STAINED_GLASS_PANE : Material.BARRIER;
        return this.button(
                material,
                this.presentationFormatter.clearButtonName(automaticMode),
                this.presentationFormatter.clearButtonLore(automaticMode)
        );
    }

    private ItemStack button(final Material material, final String name, final List<String> lore) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            this.presentationFormatter.applyMenuFlags(meta);
            stack.setItemMeta(meta);
        }
        return stack;
    }


    private int maxPage(final Player player) {
        int maxSlot = -1;
        for (final EvaluatedTitle evaluatedTitle : this.visibleTitles(player)) {
            maxSlot = Math.max(maxSlot, evaluatedTitle.absoluteSlot());
        }
        return Math.max(0, maxSlot / PAGE_SIZE);
    }

    private List<EvaluatedTitle> titlesForPage(final Player player, final int page) {
        final List<EvaluatedTitle> pageTitles = new ArrayList<>();
        for (final EvaluatedTitle evaluatedTitle : this.visibleTitles(player)) {
            if ((evaluatedTitle.absoluteSlot() / PAGE_SIZE) == page) {
                pageTitles.add(new EvaluatedTitle(
                        evaluatedTitle.option(),
                        evaluatedTitle.eligibility(),
                        evaluatedTitle.absoluteSlot(),
                        evaluatedTitle.absoluteSlot() % PAGE_SIZE
                ));
            }
        }
        pageTitles.sort(Comparator.comparingInt(EvaluatedTitle::pageSlot));
        return pageTitles;
    }

    private List<EvaluatedTitle> visibleTitles(final Player player) {
        final List<EvaluatedTitle> visible = new ArrayList<>();
        int fallbackIndex = 0;
        final List<TitleOption> ordered = this.titleSettingsConfig.titles().values().stream()
                .sorted(
                        Comparator.comparing((TitleOption option) -> option.slot() == null ? Integer.MAX_VALUE : option.slot())
                                .thenComparing(TitleOption::key)
                )
                .toList();

        for (final TitleOption option : ordered) {
            final StoreEligibilityResult eligibility = this.titleEligibilityEvaluator.evaluate(player, option);
            if (!eligibility.visible()) {
                continue;
            }
            final int absoluteSlot = option.slot() == null ? (PAGE_SIZE + fallbackIndex++) : Math.max(0, option.slot());
            visible.add(new EvaluatedTitle(option, eligibility, absoluteSlot, absoluteSlot % PAGE_SIZE));
        }
        return visible;
    }

    private Material resolveMaterial(final String iconKey) {
        final Material material = Material.matchMaterial(iconKey);
        return material == null ? Material.BARRIER : material;
    }

    private record EvaluatedTitle(
            TitleOption option,
            StoreEligibilityResult eligibility,
            int absoluteSlot,
            int pageSlot
    ) {
    }
}
