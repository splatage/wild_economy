package com.splatage.wild_economy.testing;

public interface HarnessClock {

    long epochSecond();

    static HarnessClock fixed(final long epochSecond) {
        return () -> epochSecond;
    }
}
