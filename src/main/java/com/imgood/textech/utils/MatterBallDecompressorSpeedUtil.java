package com.imgood.textech.utils;

import com.imgood.textech.Config;

import appeng.api.config.Upgrades;
import appeng.parts.automation.UpgradeInventory;

/**
 * Matter-ball decompressor throughput from AE speed / hyper / superluminal cards.
 * <ul>
 * <li>Items per type per second: {@code baseItems * 2^speedCards}</li>
 * <li>Parallel types per second: {@code 2^(n^n)} from hyper or superluminal cards (strongest tier wins)</li>
 * </ul>
 */
public final class MatterBallDecompressorSpeedUtil {

    /** Hard cap to avoid runaway batch sizes from tetration. */
    private static final int MAX_PARALLEL_TYPES = 256;

    private MatterBallDecompressorSpeedUtil() {}

    public static int getItemsPerTypePerSecond(UpgradeInventory upgrades) {
        int speedCards = upgrades == null ? 0 : upgrades.getInstalledUpgrades(Upgrades.SPEED);
        if (speedCards < 0) {
            speedCards = 0;
        }
        double base = Config.matterBallDecompressorItemsPerSecond;
        return (int) Math.min(Integer.MAX_VALUE, Math.round(base * Math.pow(2.0D, speedCards)));
    }

    public static int getParallelTypesPerSecond(UpgradeInventory upgrades) {
        if (upgrades == null) {
            return 1;
        }
        int hyper = upgrades.getInstalledUpgrades(Upgrades.SUPERSPEED);
        int luminal = upgrades.getInstalledUpgrades(Upgrades.SUPERLUMINALSPEED);
        int parallel = 1;
        if (hyper > 0) {
            parallel = Math.max(parallel, tetrationParallel(hyper));
        }
        if (luminal > 0) {
            parallel = Math.max(parallel, tetrationParallel(luminal));
        }
        return Math.max(1, Math.min(MAX_PARALLEL_TYPES, parallel));
    }

    /** {@code 2^(n^n)} — user-specified parallel type count for hyper / superluminal cards. */
    private static int tetrationParallel(int cardCount) {
        if (cardCount <= 0) {
            return 1;
        }
        double exponent = Math.pow(cardCount, cardCount);
        double value = Math.pow(2.0D, exponent);
        if (value > MAX_PARALLEL_TYPES) {
            return MAX_PARALLEL_TYPES;
        }
        return (int) Math.round(value);
    }
}
