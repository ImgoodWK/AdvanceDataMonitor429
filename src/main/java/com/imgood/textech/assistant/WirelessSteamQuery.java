package com.imgood.textech.assistant;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.AdvanceDataMonitor;

public final class WirelessSteamQuery {

    private static final String[] CLASS_NAMES = { "com.science.gtnl.utils.world.steam.SteamWirelessNetworkManager",
        "com.science.gtnl.utils.world.steam.GlobalSteamWorldSavedData" };

    private WirelessSteamQuery() {}

    public static String query(EntityPlayerMP player, boolean chinese) {
        Value steam = findSteamValue(player);
        if (steam == null) {
            return chinese ? msg(
                "wireless.steam.noApi",
                "Wireless steam query did not find a compatible ScienceNotLeisure API. Check the debug log for exposed method names.")
                : "Wireless steam query did not find a compatible ScienceNotLeisure API. Check the debug log for exposed method names.";
        }
        StringBuilder builder = new StringBuilder(
            chinese ? msg("wireless.steam.statusTitle", "Wireless steam status:") : "Wireless steam status:");
        builder.append(chinese ? msg("wireless.steam.storedSteam", "\nStored steam: ") : "\nStored steam: ")
            .append(formatSteam(steam.value))
            .append(" L");
        builder.append(chinese ? msg("wireless.steam.sourceApi", "\nSource API: ") : "\nSource API: ")
            .append(steam.source);
        return builder.toString();
    }

    private static String formatSteam(BigInteger value) {
        if (value == null || value.signum() <= 0) {
            return "0";
        }
        // Format large numbers with commas for readability
        String raw = value.toString();
        if (raw.length() <= 3) {
            return raw;
        }
        StringBuilder formatted = new StringBuilder();
        int len = raw.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                formatted.append(',');
            }
            formatted.append(raw.charAt(i));
        }
        return formatted.toString();
    }

    private static String msg(String key, String fallback) {
        return AssistantLexicon.message(key, fallback);
    }

    private static Value findSteamValue(EntityPlayerMP player) {
        for (String className : CLASS_NAMES) {
            try {
                Class<?> clazz = Class.forName(className);
                AdvanceDataMonitor.LOG.info("[ADM Assistant] Wireless steam: loaded class {}", className);

                // Strategy 1: Try calling getUserSteam(UUID) —preferred because it handles
                // UUID→teamLeader transformation internally
                Value value = tryGetUserSteamMethod(clazz, player);
                if (value != null) {
                    return value;
                }

                // Strategy 2: Try getUserSteamInt(UUID)
                value = tryGetUserSteamIntMethod(clazz, player);
                if (value != null) {
                    return value;
                }

                // Strategy 3: Try static method auto-discovery —any static method with
                // "steam" in name that takes UUID/no-args and returns BigInteger/Number
                value = tryAutoDiscoverMethod(clazz, player);
                if (value != null) {
                    return value;
                }

                // Strategy 4: Try direct field access on GLOBAL_STEAM map
                // Must handle UUID transformation via SpaceProjectManager
                value = tryDirectField(clazz, player);
                if (value != null) {
                    return value;
                }

                // Log all steam-related API for debugging
                logCandidateApi(clazz);

            } catch (ClassNotFoundException e) {
                AdvanceDataMonitor.LOG.info("[ADM Assistant] Wireless steam: class not found: {}", className);
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.debug("[ADM Assistant] Wireless steam API probe failed for " + className, t);
            }
        }
        return null;
    }

    /**
     * Try calling getUserSteam(UUID) —the canonical API method.
     */
    private static Value tryGetUserSteamMethod(Class<?> clazz, EntityPlayerMP player) {
        try {
            Method method = clazz.getMethod("getUserSteam", UUID.class);
            if (!Modifier.isStatic(method.getModifiers())) {
                return null;
            }
            Object result = method.invoke(null, player.getUniqueID());
            if (result instanceof BigInteger) {
                Value value = new Value((BigInteger) result);
                value.source = clazz.getName() + ".getUserSteam(UUID)";
                AdvanceDataMonitor.LOG.info("[ADM Assistant] Wireless steam: getUserSteam returned {}", result);
                return value;
            }
        } catch (NoSuchMethodException ignored) {} catch (Throwable t) {
            AdvanceDataMonitor.LOG.debug("[ADM Assistant] getUserSteam failed", t);
        }
        return null;
    }

    /**
     * Try calling getUserSteamInt(UUID) —returns int (capped at Integer.MAX_VALUE).
     */
    private static Value tryGetUserSteamIntMethod(Class<?> clazz, EntityPlayerMP player) {
        try {
            Method method = clazz.getMethod("getUserSteamInt", UUID.class);
            if (!Modifier.isStatic(method.getModifiers())) {
                return null;
            }
            Object result = method.invoke(null, player.getUniqueID());
            if (result instanceof Number) {
                BigInteger bigValue = BigInteger.valueOf(((Number) result).longValue());
                Value value = new Value(bigValue);
                value.source = clazz.getName() + ".getUserSteamInt(UUID)";
                AdvanceDataMonitor.LOG.info("[ADM Assistant] Wireless steam: getUserSteamInt returned {}", result);
                return value;
            }
        } catch (NoSuchMethodException ignored) {} catch (Throwable t) {
            AdvanceDataMonitor.LOG.debug("[ADM Assistant] getUserSteamInt failed", t);
        }
        return null;
    }

    /**
     * Auto-discover: iterate all static methods and try ones that:
     * - Have "steam" in name (case-insensitive)
     * - Return BigInteger or Number
     * - Take UUID, EntityPlayer, or no arguments
     */
    private static Value tryAutoDiscoverMethod(Class<?> clazz, EntityPlayerMP player) {
        for (Method method : clazz.getMethods()) {
            String methodName = method.getName()
                .toLowerCase();
            if (!methodName.contains("steam") || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            // Skip setter/adder/clear methods
            if (methodName.contains("set") || methodName.contains("add")
                || methodName.contains("clear")
                || methodName.contains("remove")) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (!BigInteger.class.isAssignableFrom(returnType) && !Number.class.isAssignableFrom(returnType)
                && returnType != int.class
                && returnType != long.class) {
                continue;
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            Object[] args;
            if (paramTypes.length == 0) {
                args = new Object[0];
            } else if (paramTypes.length == 1) {
                args = buildArgs(paramTypes[0], player);
                if (args == null) continue;
            } else {
                continue;
            }
            try {
                Object result = method.invoke(null, args);
                if (result == null) continue;
                BigInteger bigValue = toBigInteger(result);
                if (bigValue == null) continue;
                Value value = new Value(bigValue);
                value.source = clazz.getName() + "." + method.getName() + "()";
                AdvanceDataMonitor.LOG.info(
                    "[ADM Assistant] Wireless steam: auto-discovered method {} returned {}",
                    method.getName(),
                    result);
                return value;
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.debug(
                    "[ADM Assistant] Wireless steam auto-discover method failed: " + clazz.getName()
                        + "."
                        + method.getName(),
                    t);
            }
        }
        return null;
    }

    /**
     * Try direct field access on the GLOBAL_STEAM map.
     * Must resolve UUID through SpaceProjectManager.getLeader() if available.
     */
    private static Value tryDirectField(Class<?> clazz, EntityPlayerMP player) {
        // Try multiple field name variants (different SNL versions use different casing)
        String[] fieldNames = { "GLOBAL_STEAM", "GlobalSteam", "globalSteam", "global_steam", "STEAM_MAP", "steamMap",
            "steam" };
        for (String fieldName : fieldNames) {
            try {
                Field field = findField(clazz, fieldName);
                if (field == null) continue;
                if (!Modifier.isStatic(field.getModifiers())) continue;
                if (!Map.class.isAssignableFrom(field.getType())) {
                    AdvanceDataMonitor.LOG.info(
                        "[ADM Assistant] Wireless steam field {} is not a Map, type={}",
                        fieldName,
                        field.getType()
                            .getName());
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<UUID, ?> rawMap = (Map<UUID, ?>) field.get(null);
                if (rawMap == null || rawMap.isEmpty()) {
                    AdvanceDataMonitor.LOG.info("[ADM Assistant] Wireless steam field {} is null/empty", fieldName);
                    continue;
                }

                // Try raw player UUID first, then try team leader UUID
                UUID lookupUuid = player.getUniqueID();
                Object rawValue = rawMap.get(lookupUuid);

                if (rawValue == null) {
                    // Try team leader UUID via SpaceProjectManager
                    UUID teamUuid = tryGetTeamLeader(player);
                    if (teamUuid != null && !teamUuid.equals(lookupUuid)) {
                        rawValue = rawMap.get(teamUuid);
                        if (rawValue != null) {
                            lookupUuid = teamUuid;
                        }
                    }
                }

                if (rawValue != null) {
                    BigInteger bigValue = toBigInteger(rawValue);
                    if (bigValue != null) {
                        Value value = new Value(bigValue);
                        value.source = clazz.getName() + "." + fieldName;
                        AdvanceDataMonitor.LOG
                            .info("[ADM Assistant] Wireless steam: direct field {} returned {}", fieldName, bigValue);
                        return value;
                    }
                } else {
                    // Value is null in map —player has never used steam
                    // Return zero so we at least report something instead of "API not found"
                    Value value = new Value(BigInteger.ZERO);
                    value.source = clazz.getName() + "." + fieldName + " (player not in map; steam=0)";
                    return value;
                }
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.debug("[ADM Assistant] Wireless steam field access failed for " + fieldName, t);
            }
        }
        return null;
    }

    /**
     * Find a field by name, trying getField() first then getDeclaredField().
     */
    private static Field findField(Class<?> clazz, String name) {
        try {
            return clazz.getField(name);
        } catch (NoSuchFieldException e) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                return null;
            }
        }
    }

    /**
     * Try to get team leader UUID via SpaceProjectManager.
     * Uses reflection since SpaceProjectManager may not be a compile-time dependency.
     */
    private static UUID tryGetTeamLeader(EntityPlayerMP player) {
        try {
            Class<?> spmClass = Class.forName("gregtech.common.misc.spaceprojects.SpaceProjectManager");
            Method getLeader = spmClass.getMethod("getLeader", UUID.class);
            Object result = getLeader.invoke(null, player.getUniqueID());
            if (result instanceof UUID) {
                return (UUID) result;
            }
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.debug("[ADM Assistant] SpaceProjectManager.getLeader not available", t);
        }
        return null;
    }

    private static Object[] buildArgs(Class<?> type, EntityPlayerMP player) {
        if (UUID.class.equals(type)) {
            return new Object[] { player.getUniqueID() };
        }
        if (String.class.equals(type)) {
            return new Object[] { player.getCommandSenderName() };
        }
        if (type.isAssignableFrom(player.getClass())) {
            return new Object[] { player };
        }
        return null;
    }

    private static BigInteger toBigInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigInteger) {
            return (BigInteger) value;
        }
        if (value instanceof Number) {
            return BigInteger.valueOf(((Number) value).longValue());
        }
        try {
            return new BigInteger(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void logCandidateApi(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
            String lower = method.getName()
                .toLowerCase();
            if (lower.contains("steam") && Modifier.isStatic(method.getModifiers())) {
                AdvanceDataMonitor.LOG.info(
                    "[ADM Assistant] Wireless steam API candidate: {}.{} returns {}",
                    clazz.getName(),
                    method.getName(),
                    method.getReturnType()
                        .getSimpleName());
            }
        }
        for (Field field : clazz.getFields()) {
            String lower = field.getName()
                .toLowerCase();
            if (lower.contains("steam") || lower.contains("global")) {
                AdvanceDataMonitor.LOG.info(
                    "[ADM Assistant] Wireless steam API candidate field: {}.{} type={}",
                    clazz.getName(),
                    field.getName(),
                    field.getType()
                        .getSimpleName());
            }
        }
    }

    private static final class Value {

        final BigInteger value;
        String source = "";

        private Value(BigInteger value) {
            this.value = value;
        }
    }
}
