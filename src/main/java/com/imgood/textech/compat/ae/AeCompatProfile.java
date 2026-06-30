package com.imgood.textech.compat.ae;

/**
 * AE integration profile selected at runtime for GTNH version compatibility.
 */
public enum AeCompatProfile {

    /** Pre-2.9.0 beta-1: AE2FC / GlodBlock fluid cell handlers. */
    LEGACY,

    /** GTNH 2.9.0 beta-1+: AE2 native fluid storage and pattern APIs. */
    NATIVE_FLUID
}
