package com.nzothr.emcstoragebridge.core;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FluidBucketMathTest {
    @Test
    void onlyWholeBucketsCanBePurchasedFromEmc() {
        assertEquals(0, FluidBucketMath.availableAmount(BigInteger.valueOf(999), 0, 1000, Long.MAX_VALUE));
        assertEquals(1000, FluidBucketMath.availableAmount(BigInteger.valueOf(1000), 0, 1000, Long.MAX_VALUE));
        assertEquals(2000, FluidBucketMath.availableAmount(BigInteger.valueOf(2500), 0, 1000, Long.MAX_VALUE));
    }

    @Test
    void partialRequestsReusePrepaidCache() {
        assertEquals(1, FluidBucketMath.bucketsToPurchase(1));
        assertEquals(999, FluidBucketMath.remainingCache(0, 1, 1));
        assertEquals(999, FluidBucketMath.availableAmount(BigInteger.ZERO, 999, 1000, Long.MAX_VALUE));
        assertEquals(0, FluidBucketMath.bucketsToPurchase(0));
        assertEquals(0, FluidBucketMath.remainingCache(999, 999, 0));
    }

    @Test
    void largerRequestsRoundUpToBucketsWithoutOverflow() {
        assertEquals(2, FluidBucketMath.bucketsToPurchase(1001));
        assertEquals(999, FluidBucketMath.remainingCache(0, 1001, 2));
        long buckets = FluidBucketMath.bucketsToPurchase(Long.MAX_VALUE);
        assertEquals(193, FluidBucketMath.remainingCache(0, Long.MAX_VALUE, buckets));
    }

    @Test
    void emcCostIsChargedPerPurchasedBucket() {
        assertEquals(BigInteger.valueOf(2000), FluidBucketMath.cost(1000, 2));
        assertEquals(BigInteger.ZERO, FluidBucketMath.cost(1000, 0));
    }
}
