package com.imgood.textech.assistant;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.AdvanceDataMonitor;

public final class WirelessPowerQuery {

    private static final String[] CLASS_NAMES = { "gregtech.common.misc.WirelessNetworkManager",
        "gregtech.common.misc.GlobalEnergyWorldSavedData",
        "gregtech.api.metatileentity.implementations.WirelessNetworkManager",
        "tectech.mechanics.dataTransport.WirelessNetworkManager" };

    private static final String[] ENERGY_METHODS = { "getUserEU", "getEU", "getStoredEU", "getWirelessEU",
        "getUserEnergy", "getEnergy" };

    private static final String[] CAPACITY_METHODS = { "getUserMaxEU", "getMaxEU", "getCapacity", "getWirelessMaxEU" };

    private WirelessPowerQuery() {}

    public static String query(EntityPlayerMP player, boolean chinese) {
        Value energy = findValue(player, ENERGY_METHODS);
        Value capacity = findValue(player, CAPACITY_METHODS);
        if (energy == null) {
            return chinese ? msg(
                "wireless.noApi",
                "Wireless power query did not find a compatible GTNH/TecTech API. Check the debug log for exposed method names.")
                : "Wireless power query did not find a compatible GTNH/TecTech API. Check the debug log for exposed method names.";
        }
        StringBuilder builder = new StringBuilder(
            chinese ? msg("wireless.statusTitle", "Wireless power status:") : "Wireless power status:");
        builder.append(chinese ? msg("wireless.storedEu", "\nStored EU: ") : "\nStored EU: ")
            .append(energy.value)
            .append(" EU");
        if (capacity != null) {
            builder.append(chinese ? msg("wireless.maxCapacity", "\nMax capacity: ") : "\nMax capacity: ")
                .append(capacity.value)
                .append(" EU");
        } else {
            builder.append(
                chinese ? msg("wireless.maxCapacityUnavailable", "\nMax capacity: not exposed by the detected API.")
                    : "\nMax capacity: not exposed by the detected API.");
        }
        builder.append(
            chinese ? msg("wireless.ioUnavailable", "\nInput/output rate: not exposed by the detected API.")
                : "\nInput/output rate: not exposed by the detected API.");
        builder.append(chinese ? msg("wireless.sourceApi", "\nSource API: ") : "\nSource API: ")
            .append(energy.source);
        return builder.toString();
    }

    private static String msg(String key, String fallback) {
        return AssistantLexicon.message(key, fallback);
    }

    private static Value findValue(EntityPlayerMP player, String[] methodNames) {
        for (String className : CLASS_NAMES) {
            try {
                Class<?> clazz = Class.forName(className);
                for (String methodName : methodNames) {
                    Value value = invoke(clazz, methodName, player);
                    if (value != null) {
                        value.source = clazz.getName() + "." + methodName;
                        return value;
                    }
                }
                logCandidateMethods(clazz);
            } catch (ClassNotFoundException ignored) {} catch (Throwable t) {
                AdvanceDataMonitor.LOG.debug("[ADM Assistant] Wireless power API probe failed for " + className, t);
            }
        }
        return null;
    }

    private static Value invoke(Class<?> clazz, String methodName, EntityPlayerMP player) {
        for (Method method : clazz.getMethods()) {
            if (!method.getName()
                .equals(methodName) || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            Object[] args = argsFor(method.getParameterTypes(), player);
            if (args == null) {
                continue;
            }
            try {
                Object result = method.invoke(null, args);
                BigInteger value = toBigInteger(result);
                if (value != null) {
                    return new Value(value);
                }
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.debug(
                    "[ADM Assistant] Wireless power method failed: " + clazz.getName() + "." + method.getName(),
                    t);
            }
        }
        return null;
    }

    private static Object[] argsFor(Class<?>[] types, EntityPlayerMP player) {
        if (types.length == 0) {
            return new Object[0];
        }
        if (types.length != 1 || player == null) {
            return null;
        }
        Class<?> type = types[0];
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
            return new BigInteger(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void logCandidateMethods(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
            String lower = method.getName()
                .toLowerCase();
            if (lower.contains("eu") || lower.contains("energy") || lower.contains("wireless")) {
                AdvanceDataMonitor.LOG
                    .info("[ADM Assistant] Wireless API candidate: {}.{}", clazz.getName(), method.getName());
            }
        }
    }

    private static final class Value {

        private final BigInteger value;
        private String source = "";

        private Value(BigInteger value) {
            this.value = value;
        }
    }
}
