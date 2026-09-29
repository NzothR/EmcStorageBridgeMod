package com.nzothr.emcstoragebridge.core;

import java.math.BigInteger;

public final class EmcMath {
    private EmcMath() {
    }

    public static long maxExtractable(BigInteger emc, long emcPerItem, long requestedAmount) {
        if (emc == null || emc.signum() <= 0 || emcPerItem <= 0 || requestedAmount <= 0) {
            return 0;
        }

        BigInteger available = emc.divide(BigInteger.valueOf(emcPerItem));
        return available.min(BigInteger.valueOf(requestedAmount)).longValueExact();
    }

    public static BigInteger cost(long amount, long emcPerItem) {
        if (amount < 0 || emcPerItem < 0) {
            throw new IllegalArgumentException("Amount and EMC value must be non-negative");
        }
        return BigInteger.valueOf(amount).multiply(BigInteger.valueOf(emcPerItem));
    }

    public static BigInteger gain(long amount, long emcPerItem) {
        return cost(amount, emcPerItem);
    }
}
