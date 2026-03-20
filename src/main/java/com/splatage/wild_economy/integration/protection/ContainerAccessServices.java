package com.splatage.wild_economy.integration.protection;

import com.splatage.wild_economy.platform.PlatformSupport;

public final class ContainerAccessServices {

    private ContainerAccessServices() {
    }

    public static ContainerAccessService createDefault() {
        if (PlatformSupport.isFolia()) {
            return new ProtectionPluginAwareFoliaContainerAccessService();
        }
        return new EventDrivenContainerAccessService();
    }
}
