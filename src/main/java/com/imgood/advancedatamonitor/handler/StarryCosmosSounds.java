package com.imgood.advancedatamonitor.handler;

import java.util.Random;

import net.minecraft.world.World;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.handler.StarryCosmosSwordConstants;

/**
 * Custom sound effects for Empyrean Holy Judgment (CC0 Kenney assets — see assets/.../sound/CREDITS.txt).
 */
public final class StarryCosmosSounds {

    private static final String PREFIX = AdvanceDataMonitor.MODID + ":";

    public static final String MELEE_HIT = PREFIX + "starry_melee_hit";
    public static final String SLASH = PREFIX + "starry_slash";
    public static final String THROW = PREFIX + "starry_throw";
    public static final String SLAM = PREFIX + "starry_slam";
    public static final String STAB_FALL = PREFIX + "starry_stab_fall";
    public static final String STAB_IMPACT = PREFIX + "starry_stab_impact";
    public static final String RAIN_START = PREFIX + "starry_rain_start";
    public static final String RAIN_TICK = PREFIX + "starry_rain_tick";
    public static final String JUDGMENT = PREFIX + "starry_judgment";

    private StarryCosmosSounds() {}

    public static void playMeleeHit(World world, double x, double y, double z) {
        playAt(world, x, y, z, MELEE_HIT, 1.0F, 0.95F + world.rand.nextFloat() * 0.1F);
    }

    /** Left-click crescent line slash. */
    public static void playSlash(World world, double x, double y, double z) {
        playAt(world, x, y, z, SLASH, 1.1F, 0.9F + world.rand.nextFloat() * 0.15F);
    }

    /** Right-click sword throw. */
    public static void playThrow(World world, double x, double y, double z) {
        playAt(world, x, y, z, THROW, 1.0F, 1.0F);
    }

    /** Shift+right-click area judgment cast. */
    public static void playJudgmentCast(World world, double x, double y, double z) {
        playAt(world, x, y, z, JUDGMENT, 1.3F, 0.85F);
    }

    /** Giant slam sword embeds into the ground (throw impact centerpiece). */
    public static void playSlamImpact(World world, double x, double y, double z) {
        playAt(world, x, y, z, SLAM, 1.5F, 0.8F);
        playAt(world, x, y, z, STAB_IMPACT, 0.9F, 0.65F);
    }

    /** Vertical stab begins descending onto a target. */
    public static void playStabFall(World world, double x, double y, double z, float renderScale) {
        float ratio = renderScale / StarryCosmosSwordConstants.SCALE_SLAM;
        float volume = 0.7F + ratio * 0.6F;
        float pitch = 0.75F + ratio * 0.35F;
        playAt(world, x, y, z, STAB_FALL, volume, pitch);
    }

    /** Vertical stab hits the target / ground. */
    public static void playStabImpact(World world, double x, double y, double z, float renderScale) {
        float ratio = renderScale / StarryCosmosSwordConstants.SCALE_SLAM;
        float volume = 0.8F + ratio * 0.7F;
        float pitch = 0.7F + ratio * 0.45F;
        playAt(world, x, y, z, STAB_IMPACT, volume, pitch);
    }

    /** Sword rain field begins after throw lands. */
    public static void playRainStart(World world, double x, double y, double z) {
        playAt(world, x, y, z, RAIN_START, 1.2F, 0.9F);
    }

    /** Single rain sword sticks into the ground. */
    public static void playRainSwordImpact(World world, double x, double y, double z, Random rand) {
        float pitch = 1.05F + rand.nextFloat() * 0.25F;
        playAt(world, x, y, z, RAIN_TICK, 0.35F, pitch);
    }

    private static void playAt(World world, double x, double y, double z, String sound, float volume, float pitch) {
        if (world == null || world.isRemote) {
            return;
        }
        world.playSoundEffect(x, y, z, sound, volume, pitch);
    }
}
