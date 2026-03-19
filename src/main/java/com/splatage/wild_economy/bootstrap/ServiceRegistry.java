public void registerCommands() {
    final PluginCommand shop = this.plugin.getCommand("shop");
    if (shop != null) {
        shop.setExecutor(new ShopCommand(
            new ShopSellHandSubcommand(this.exchangeService),
            new ShopSellAllSubcommand(this.exchangeService)
        ));
    }

    final PluginCommand shopAdmin = this.plugin.getCommand("shopadmin");
    if (shopAdmin != null) {
        shopAdmin.setExecutor(new ShopAdminCommand(this.plugin));
    }
}
