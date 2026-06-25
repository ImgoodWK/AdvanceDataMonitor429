package com.imgood.advancedatamonitor.compat.ae;

/** How {@link AeCompatProfile} was chosen during {@link AeCompat#init()}. */
public enum AeCompatDetectionSource {

    CONFIG_OVERRIDE,
    GTNH_VERSION_FILE,
    AE2_MOD_VERSION,
    CAPABILITY,
    DEFAULT_LEGACY
}
