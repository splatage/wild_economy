package com.splatage.wild_economy.gui;

import com.splatage.wild_economy.config.TitleSettingsConfig;
import com.splatage.wild_economy.store.eligibility.StoreEligibilityResult;
import com.splatage.wild_economy.title.eligibility.TitleEligibilityEvaluator;
import com.splatage.wild_economy.title.model.ResolvedTitle;
import com.splatage.wild_economy.title.model.TitleOption;
import com.splatage.wild_economy.title.model.TitleSection;
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
    private static final int[] HUB_SECTION_SLOTS = {11, 19, 21, 23, 25};

    private final TitleSettingsConfig titleSettingsConfig;
    private final TitleEligibilityEvaluator titleEligibilityEvaluator;
    private final TitleSelectionService titleSelectionService;
    private final ResolvedTitleService resolvedTitleService;
    private final TitlePresentationFormatter presentationFormatter = new TitlePresentationFormatter();
    private ShopMenuRouter shopMenuRouter;

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

    public void setShopMenuRouter(final ShopMenuRouter shopMenuRouter) {
        this.shopMenuRouter = shopMenuRouter;
    }

    public void open(final Player player, final int ignoredPage) {
        this.openHub(player);
    }

    public void handleClick(final InventoryClickEvent event, final TitleMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= INVENTORY_SIZE) {
            return;
        }

        switch (holder.view()) {
            case HUB -> this.handleHubClick(player, rawSlot);
            case SECTION -> this.handleSectionClick(player, holder.section(), holder.page(), rawSlot);
            case FAMILY -> this.handleFamilyClick(player, holder.section(), holder.family(), holder.page(), rawSlot);
        }
    }

    private void openHub(final Player player) {
        final TitleMenuHolder holder = TitleMenuHolder.hub();
        final Inventory inventory = holder.createInventory(INVENTORY_SIZE, this.presentationFormatter.menuTitle(holder.view(), null, null, 0));

        final TitleSection[] sections = this.sectionsInDisplayOrder();
        for (int i = 0; i < sections.length && i < HUB_SECTION_SLOTS.length; i++) {
            final TitleSection section = sections[i];
            inventory.setItem(HUB_SECTION_SLOTS[i], this.sectionButton(player, section));
        }

        inventory.setItem(48, this.button(Material.BARRIER, this.presentationFormatter.closeButtonName(), null));
        inventory.setItem(49, this.summaryItem(player));
        inventory.setItem(50, this.clearItem(player));
        player.openInventory(inventory);
    }

    private void openSection(final Player player, final TitleSection section, final int page) {
        final int safePage = Math.max(0, page);
        final TitleMenuHolder holder = TitleMenuHolder.section(section, safePage);
        final Inventory inventory = holder.createInventory(INVENTORY_SIZE, this.presentationFormatter.menuTitle(holder.view(), section, null, safePage));

        final List<String> families = this.visibleFamilies(player, section);
        if (families.size() > 1) {
            final List<String> familiesForPage = this.slice(families, safePage);
            for (int i = 0; i < familiesForPage.size(); i++) {
                inventory.setItem(i, this.familyButton(player, section, familiesForPage.get(i)));
            }
            if (safePage > 0) {
                inventory.setItem(45, this.button(Material.ARROW, this.presentationFormatter.previousButtonName(), null));
            }
            inventory.setItem(46, this.button(Material.SPECTRAL_ARROW, this.presentationFormatter.backButtonName(), null));
            inventory.setItem(48, this.button(Material.BARRIER, this.presentationFormatter.closeButtonName(), null));
            inventory.setItem(49, this.summaryItem(player));
            inventory.setItem(50, this.clearItem(player));
            if (safePage < this.maxPage(families.size())) {
                inventory.setItem(53, this.button(Material.ARROW, this.presentationFormatter.nextButtonName(), null));
            }
        } else {
            final String family = families.isEmpty() ? null : families.get(0);
            this.populateTitlePage(inventory, player, this.visibleTitles(player, section, family), safePage);
            if (safePage > 0) {
                inventory.setItem(45, this.button(Material.ARROW, this.presentationFormatter.previousButtonName(), null));
            }
            inventory.setItem(46, this.button(Material.SPECTRAL_ARROW, this.presentationFormatter.backButtonName(), null));
            inventory.setItem(48, this.button(Material.BARRIER, this.presentationFormatter.closeButtonName(), null));
            inventory.setItem(49, this.summaryItem(player));
            inventory.setItem(50, this.clearItem(player));
            if (safePage < this.maxPage(this.visibleTitles(player, section, family).size())) {
                inventory.setItem(53, this.button(Material.ARROW, this.presentationFormatter.nextButtonName(), null));
            }
        }

        player.openInventory(inventory);
    }

    private void openFamily(final Player player, final TitleSection section, final String family, final int page) {
        final int safePage = Math.max(0, page);
        final TitleMenuHolder holder = TitleMenuHolder.family(section, family, safePage);
        final Inventory inventory = holder.createInventory(INVENTORY_SIZE, this.presentationFormatter.menuTitle(holder.view(), section, family, safePage));
        final List<EvaluatedTitle> titles = this.visibleTitles(player, section, family);
        this.populateTitlePage(inventory, player, titles, safePage);

        if (safePage > 0) {
            inventory.setItem(45, this.button(Material.ARROW, this.presentationFormatter.previousButtonName(), null));
        }
        inventory.setItem(46, this.button(Material.SPECTRAL_ARROW, this.presentationFormatter.backButtonName(), null));
        inventory.setItem(48, this.button(Material.BARRIER, this.presentationFormatter.closeButtonName(), null));
        inventory.setItem(49, this.summaryItem(player));
        inventory.setItem(50, this.clearItem(player));
        if (safePage < this.maxPage(titles.size())) {
            inventory.setItem(53, this.button(Material.ARROW, this.presentationFormatter.nextButtonName(), null));
        }

        player.openInventory(inventory);
    }

    private void populateTitlePage(final Inventory inventory, final Player player, final List<EvaluatedTitle> titles, final int page) {
        final String activeTitleKey = this.activeTitleKey(player);
        final List<EvaluatedTitle> pageTitles = this.slice(titles, page);
        for (int index = 0; index < pageTitles.size(); index++) {
            final EvaluatedTitle evaluatedTitle = pageTitles.get(index);
            inventory.setItem(index, this.titleItem(
                    evaluatedTitle.option(),
                    evaluatedTitle.eligibility(),
                    evaluatedTitle.option().key().equals(activeTitleKey)
            ));
        }
    }

    private String activeTitleKey(final Player player) {
        return this.resolvedTitleService.getResolvedTitle(player)
                .map(ResolvedTitle::key)
                .orElse("");
    }

    private void handleHubClick(final Player player, final int rawSlot) {
        for (int i = 0; i < HUB_SECTION_SLOTS.length; i++) {
            if (HUB_SECTION_SLOTS[i] != rawSlot) {
                continue;
            }
            final TitleSection[] sections = this.sectionsInDisplayOrder();
            if (i < sections.length) {
                this.openSection(player, sections[i], 0);
            }
            return;
        }
        switch (rawSlot) {
            case 48 -> this.exitToStore(player);
            case 50 -> this.clearSelection(player, () -> this.openHub(player));
            default -> {
            }
        }
    }

    private void handleSectionClick(final Player player, final TitleSection section, final int page, final int rawSlot) {
        final List<String> families = this.visibleFamilies(player, section);
        if (families.size() > 1) {
            final List<String> pageFamilies = this.slice(families, page);
            if (rawSlot < pageFamilies.size()) {
                this.openFamily(player, section, pageFamilies.get(rawSlot), 0);
                return;
            }
        } else {
            final String family = families.isEmpty() ? null : families.get(0);
            final List<EvaluatedTitle> titles = this.visibleTitles(player, section, family);
            final List<EvaluatedTitle> pageTitles = this.slice(titles, page);
            if (rawSlot < pageTitles.size()) {
                this.handleTitleSelection(player, pageTitles.get(rawSlot), () -> this.openSection(player, section, page));
                return;
            }
        }

        switch (rawSlot) {
            case 45 -> {
                if (page > 0) {
                    this.openSection(player, section, page - 1);
                }
            }
            case 46 -> this.openHub(player);
            case 48 -> this.exitToStore(player);
            case 50 -> this.clearSelection(player, () -> this.openSection(player, section, page));
            case 53 -> {
                final int total = families.size() > 1 ? families.size() : this.visibleTitles(player, section, families.isEmpty() ? null : families.get(0)).size();
                if (page < this.maxPage(total)) {
                    this.openSection(player, section, page + 1);
                }
            }
            default -> {
            }
        }
    }

    private void handleFamilyClick(final Player player, final TitleSection section, final String family, final int page, final int rawSlot) {
        final List<EvaluatedTitle> titles = this.visibleTitles(player, section, family);
        final List<EvaluatedTitle> pageTitles = this.slice(titles, page);
        if (rawSlot < pageTitles.size()) {
            this.handleTitleSelection(player, pageTitles.get(rawSlot), () -> this.openFamily(player, section, family, page));
            return;
        }
        switch (rawSlot) {
            case 45 -> {
                if (page > 0) {
                    this.openFamily(player, section, family, page - 1);
                }
            }
            case 46 -> this.openSection(player, section, 0);
            case 48 -> this.exitToStore(player);
            case 50 -> this.clearSelection(player, () -> this.openFamily(player, section, family, page));
            case 53 -> {
                if (page < this.maxPage(titles.size())) {
                    this.openFamily(player, section, family, page + 1);
                }
            }
            default -> {
            }
        }
    }

    private void handleTitleSelection(final Player player, final EvaluatedTitle evaluatedTitle, final Runnable reopen) {
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
        reopen.run();
    }

    private void clearSelection(final Player player, final Runnable reopen) {
        this.titleSelectionService.clearSelectedTitleKey(player);
        this.resolvedTitleService.invalidate(player.getUniqueId());
        this.resolvedTitleService.warm(player);
        player.sendMessage("Your active title has been cleared. Automatic title selection is now in effect.");
        reopen.run();
    }

    private void exitToStore(final Player player) {
        if (this.shopMenuRouter != null) {
            this.shopMenuRouter.openStoreRoot(player);
            return;
        }
        player.closeInventory();
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

    private ItemStack sectionButton(final Player player, final TitleSection section) {
        final ItemStack stack = new ItemStack(this.presentationFormatter.sectionIcon(section));
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            final List<EvaluatedTitle> visibleTitles = this.visibleTitles(player, section, null);
            final int available = (int) visibleTitles.stream().filter(title -> title.eligibility().acquirable()).count();
            meta.setDisplayName(this.presentationFormatter.hubButtonName(section));
            meta.setLore(this.presentationFormatter.hubButtonLore(section, available, visibleTitles.size()));
            this.presentationFormatter.applyMenuFlags(meta);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack familyButton(final Player player, final TitleSection section, final String family) {
        final TitleOption representative = this.titleSettingsConfig.representativeForFamily(section, family);
        final ItemStack stack = new ItemStack(this.resolveMaterial(representative == null ? "BOOK" : representative.icon()));
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            final List<EvaluatedTitle> titles = this.visibleTitles(player, section, family);
            final int available = (int) titles.stream().filter(title -> title.eligibility().acquirable()).count();
            meta.setDisplayName(this.presentationFormatter.familyButtonName(section, family));
            meta.setLore(this.presentationFormatter.familyButtonLore(section, family, available, titles.size(), representative));
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

    private TitleSection[] sectionsInDisplayOrder() {
        final List<TitleSection> sections = new ArrayList<>();
        for (final TitleSection section : TitleSection.values()) {
            if (!this.titleSettingsConfig.titlesInSection(section).isEmpty()) {
                sections.add(section);
            }
        }
        return sections.toArray(TitleSection[]::new);
    }

    private List<String> visibleFamilies(final Player player, final TitleSection section) {
        final List<String> visible = new ArrayList<>();
        for (final String family : this.titleSettingsConfig.familiesInSection(section)) {
            if (!this.visibleTitles(player, section, family).isEmpty()) {
                visible.add(family);
            }
        }
        return visible;
    }

    private List<EvaluatedTitle> visibleTitles(final Player player, final TitleSection section, final String family) {
        final List<TitleOption> source = family == null || family.isBlank()
                ? this.titleSettingsConfig.titlesInSection(section)
                : this.titleSettingsConfig.titlesInSectionFamily(section, family);
        final List<EvaluatedTitle> visible = new ArrayList<>();
        for (final TitleOption option : source) {
            final StoreEligibilityResult eligibility = this.titleEligibilityEvaluator.evaluate(player, option);
            if (!eligibility.visible()) {
                continue;
            }
            visible.add(new EvaluatedTitle(option, eligibility));
        }
        return visible;
    }

    private <T> List<T> slice(final List<T> source, final int page) {
        final int start = Math.max(0, page) * PAGE_SIZE;
        if (start >= source.size()) {
            return List.of();
        }
        final int end = Math.min(source.size(), start + PAGE_SIZE);
        return source.subList(start, end);
    }

    private int maxPage(final int totalEntries) {
        return Math.max(0, (Math.max(0, totalEntries) - 1) / PAGE_SIZE);
    }

    private Material resolveMaterial(final String iconKey) {
        final Material material = Material.matchMaterial(iconKey);
        return material == null ? Material.BARRIER : material;
    }

    private record EvaluatedTitle(TitleOption option, StoreEligibilityResult eligibility) {
    }
}
