package com.splatage.wild_economy.platform;

public final class PlatformSupport {

    private static final boolean FOLIA = detectFolia();

    private PlatformSupport() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (final ClassNotFoundException ignored) {
            return false;
        }
    }
}
