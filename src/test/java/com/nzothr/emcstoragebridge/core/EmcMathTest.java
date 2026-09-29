package com.nzothr.emcstoragebridge.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class EmcMathTest {
    @Test
    void extractsOnlyWholeItemsAndHonorsRequest() {
        assertEquals(3, EmcMath.maxExtractable(BigInteger.valueOf(29), 8, 10));
        assertEquals(2, EmcMath.maxExtractable(BigInteger.valueOf(29), 8, 2));
    }

    @Test
    void acceptsRequestsUpToLongMaxWhenBalanceIsLarger() {
        BigInteger balance = BigInteger.ONE.shiftLeft(100);
        assertEquals(Long.MAX_VALUE, EmcMath.maxExtractable(balance, 1, Long.MAX_VALUE));
    }

    @Test
    void calculatesCostsWithoutLongOverflow() {
        assertEquals(BigInteger.valueOf(24_000_000_000L), EmcMath.cost(4_000_000_000L, 6));
        assertThrows(IllegalArgumentException.class, () -> EmcMath.cost(-1, 1));
    }

    @Test
    void returnsZeroForUnavailableBalancesAndInvalidRequests() {
        assertEquals(0, EmcMath.maxExtractable(BigInteger.ZERO, 10, 5));
        assertEquals(0, EmcMath.maxExtractable(BigInteger.TEN, 0, 5));
        assertEquals(0, EmcMath.maxExtractable(BigInteger.TEN, 2, 0));
    }
}
