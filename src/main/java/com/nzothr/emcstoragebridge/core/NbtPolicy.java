package com.nzothr.emcstoragebridge.core;

public enum NbtPolicy {
    REJECT,
    ALLOW;

    public static NbtPolicy fromConfig(String value) {
        return "ALLOW".equalsIgnoreCase(value) ? ALLOW : REJECT;
    }
}
