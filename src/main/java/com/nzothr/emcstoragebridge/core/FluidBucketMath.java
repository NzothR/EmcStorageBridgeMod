package com.nzothr.emcstoragebridge.core;

import java.math.BigInteger;

/** Overflow-safe accounting for whole-bucket EMC purchases and mB cache remainders. */
public final class FluidBucketMath {
    public static final long BUCKET_MILLIBUCKETS = 1000L;

    private FluidBucketMath() {}

    public static long availableAmount(BigInteger balance, long cached, long bucketPrice, long limit) {
        if (bucketPrice <= 0 || balance.signum() < 0) return Math.min(cached, limit);
        BigInteger buckets = balance.divide(BigInteger.valueOf(bucketPrice));
        BigInteger available = buckets.multiply(BigInteger.valueOf(BUCKET_MILLIBUCKETS))
                .add(BigInteger.valueOf(cached));
        return available.min(BigInteger.valueOf(limit)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    public static long bucketsToPurchase(long amountBeyondCache) {
        if (amountBeyondCache <= 0) return 0;
        return amountBeyondCache / BUCKET_MILLIBUCKETS
                + (amountBeyondCache % BUCKET_MILLIBUCKETS == 0 ? 0 : 1);
    }

    public static long remainingCache(long cached, long extracted, long bucketsPurchased) {
        if (extracted <= cached) return cached - extracted;
        long amountBeyondCache = extracted - cached;
        if (bucketsPurchased != bucketsToPurchase(amountBeyondCache)) {
            throw new IllegalArgumentException("Purchased bucket count does not cover extraction");
        }
        long remainder = amountBeyondCache % BUCKET_MILLIBUCKETS;
        return remainder == 0 ? 0 : BUCKET_MILLIBUCKETS - remainder;
    }

    public static BigInteger cost(long bucketPrice, long bucketsPurchased) {
        return BigInteger.valueOf(bucketPrice).multiply(BigInteger.valueOf(bucketsPurchased));
    }
}
