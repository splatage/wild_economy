package com.splatage.wild_economy.bootstrap;

import com.splatage.wild_economy.WildEconomyPlugin;
import com.splatage.wild_economy.config.EconomyConfig;
import com.splatage.wild_economy.economy.service.EconomyService;
import com.splatage.wild_economy.exchange.activity.MarketActivityService;
import com.splatage.wild_economy.exchange.service.ExchangeService;
import com.splatage.wild_economy.exchange.supplier.SupplierStatsService;
import com.splatage.wild_economy.gui.ExchangeBrowseMenu;
import com.splatage.wild_economy.gui.ExchangeItemDetailMenu;
import com.splatage.wild_economy.gui.ExchangeRootMenu;
import com.splatage.wild_economy.gui.ExchangeSubcategoryMenu;
import com.splatage.wild_economy.gui.layout.LayoutIconResolver;
import com.splatage.wild_economy.gui.MarketActivityMenu;
import com.splatage.wild_economy.gui.PlayerInfoItemFactory;
import com.splatage.wild_economy.gui.ShopMenuListener;
import com.splatage.wild_economy.gui.ShopMenuRouter;
import com.splatage.wild_economy.gui.StoreCategoryMenu;
import com.splatage.wild_economy.gui.StoreProductDetailMenu;
import com.splatage.wild_economy.gui.StoreRootMenu;
import com.splatage.wild_economy.gui.TopSupplierMenu;
import com.splatage.wild_economy.gui.XpBottleMenu;
import com.splatage.wild_economy.gui.admin.AdminItemInspectorMenu;
import com.splatage.wild_economy.gui.admin.AdminMenuListener;
import com.splatage.wild_economy.gui.admin.AdminMenuRouter;
import com.splatage.wild_economy.gui.admin.AdminOverrideEditMenu;
import com.splatage.wild_economy.gui.admin.AdminReviewBucketMenu;
import com.splatage.wild_economy.gui.admin.AdminRootMenu;
import com.splatage.wild_economy.gui.admin.AdminRuleImpactMenu;
import com.splatage.wild_economy.gui.browse.ExchangeLayoutBrowseService;
import com.splatage.wild_economy.gui.layout.LayoutBlueprint;
import com.splatage.wild_economy.platform.PlatformExecutor;
import com.splatage.wild_economy.store.service.StoreService;
import com.splatage.wild_economy.xp.listener.XpBottleRedeemListener;
import com.splatage.wild_economy.xp.service.XpBottleService;

final class GuiBootstrap {

    private GuiBootstrap() {
    }

    static Components create(
            final WildEconomyPlugin plugin,
            final PlatformExecutor platformExecutor,
            final ExchangeLayoutBrowseService exchangeLayoutBrowseService,
            final ExchangeService exchangeService,
            final SupplierStatsService supplierStatsService,
            final MarketActivityService marketActivityService,
            final PlayerInfoItemFactory playerInfoItemFactory,
            final LayoutBlueprint layoutBlueprint,
            final StoreService storeService,
            final EconomyConfig economyConfig,
            final XpBottleService xpBottleService,
            final EconomyService economyService
    ) {
        final LayoutIconResolver layoutIconResolver = new LayoutIconResolver();
        final TopSupplierMenu topSupplierMenu = new TopSupplierMenu(supplierStatsService, playerInfoItemFactory);
        final MarketActivityMenu marketActivityMenu = new MarketActivityMenu(marketActivityService, playerInfoItemFactory);
        final ExchangeRootMenu rootMenu = new ExchangeRootMenu(
                exchangeLayoutBrowseService,
                playerInfoItemFactory,
                layoutBlueprint,
                layoutIconResolver,
                topSupplierMenu,
                marketActivityMenu
        );
        final ExchangeSubcategoryMenu subcategoryMenu = new ExchangeSubcategoryMenu(
                exchangeLayoutBrowseService,
                playerInfoItemFactory,
                layoutBlueprint,
                layoutIconResolver
        );
        final ExchangeBrowseMenu browseMenu = new ExchangeBrowseMenu(
                exchangeLayoutBrowseService,
                playerInfoItemFactory,
                layoutBlueprint
        );
        final ExchangeItemDetailMenu itemDetailMenu = new ExchangeItemDetailMenu(
                exchangeService,
                platformExecutor,
                playerInfoItemFactory
        );
        final StoreRootMenu storeRootMenu = new StoreRootMenu(storeService, playerInfoItemFactory);
        final StoreCategoryMenu storeCategoryMenu = new StoreCategoryMenu(
                storeService,
                economyConfig,
                playerInfoItemFactory
        );
        final StoreProductDetailMenu storeProductDetailMenu = new StoreProductDetailMenu(
                storeService,
                economyConfig,
                playerInfoItemFactory
        );
        final XpBottleMenu xpBottleMenu = new XpBottleMenu(storeService, playerInfoItemFactory);

        final ShopMenuRouter shopMenuRouter = new ShopMenuRouter(
                platformExecutor,
                rootMenu,
                subcategoryMenu,
                browseMenu,
                itemDetailMenu,
                storeRootMenu,
                storeCategoryMenu,
                storeProductDetailMenu,
                xpBottleMenu,
                topSupplierMenu,
                marketActivityMenu
        );

        rootMenu.setShopMenuRouter(shopMenuRouter);
        subcategoryMenu.setShopMenuRouter(shopMenuRouter);
        browseMenu.setShopMenuRouter(shopMenuRouter);
        itemDetailMenu.setShopMenuRouter(shopMenuRouter);
        storeRootMenu.setShopMenuRouter(shopMenuRouter);
        storeCategoryMenu.setShopMenuRouter(shopMenuRouter);
        storeProductDetailMenu.setShopMenuRouter(shopMenuRouter);
        xpBottleMenu.setShopMenuRouter(shopMenuRouter);
        topSupplierMenu.setShopMenuRouter(shopMenuRouter);
        marketActivityMenu.setShopMenuRouter(shopMenuRouter);

        final ShopMenuListener shopMenuListener = new ShopMenuListener(
                exchangeLayoutBrowseService,
                rootMenu,
                subcategoryMenu,
                browseMenu,
                itemDetailMenu,
                storeRootMenu,
                storeCategoryMenu,
                storeProductDetailMenu,
                xpBottleMenu,
                topSupplierMenu,
                marketActivityMenu
        );

        final AdminRootMenu adminRootMenu = new AdminRootMenu();
        final AdminReviewBucketMenu adminReviewBucketMenu = new AdminReviewBucketMenu();
        final AdminRuleImpactMenu adminRuleImpactMenu = new AdminRuleImpactMenu();
        final AdminItemInspectorMenu adminItemInspectorMenu = new AdminItemInspectorMenu();
        final AdminOverrideEditMenu adminOverrideEditMenu = new AdminOverrideEditMenu();
        final AdminMenuRouter adminMenuRouter = new AdminMenuRouter(
                plugin,
                platformExecutor,
                adminRootMenu,
                adminReviewBucketMenu,
                adminRuleImpactMenu,
                adminItemInspectorMenu,
                adminOverrideEditMenu
        );
        adminRootMenu.setAdminMenuRouter(adminMenuRouter);
        adminReviewBucketMenu.setAdminMenuRouter(adminMenuRouter);
        adminRuleImpactMenu.setAdminMenuRouter(adminMenuRouter);
        adminItemInspectorMenu.setAdminMenuRouter(adminMenuRouter);
        adminOverrideEditMenu.setAdminMenuRouter(adminMenuRouter);
        final AdminMenuListener adminMenuListener = new AdminMenuListener(
                adminRootMenu,
                adminReviewBucketMenu,
                adminRuleImpactMenu,
                adminItemInspectorMenu,
                adminOverrideEditMenu
        );

        final XpBottleRedeemListener xpBottleRedeemListener = new XpBottleRedeemListener(xpBottleService);

        return new Components(
                shopMenuRouter,
                shopMenuListener,
                adminMenuRouter,
                adminMenuListener,
                xpBottleRedeemListener
        );
    }

    record Components(
            ShopMenuRouter shopMenuRouter,
            ShopMenuListener shopMenuListener,
            AdminMenuRouter adminMenuRouter,
            AdminMenuListener adminMenuListener,
            XpBottleRedeemListener xpBottleRedeemListener
    ) {
    }
}
