package com.imgood.textech.assistant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.imgood.textech.compat.ae.AeCompat;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

public final class PatternDetailFormatter {

    private static final String LANG_PATH = "assets/advancedatamonitor/lang/";
    private static final Map<String, String> EN_US = loadLang("en_US");
    private static final Map<String, String> ZH_CN = loadLang("zh_CN");

    private PatternDetailFormatter() {}

    public static String format(ICraftingPatternDetails pattern, CraftingCandidate candidate) {
        return format(pattern, candidate, true);
    }

    public static String format(ICraftingPatternDetails pattern, CraftingCandidate candidate, boolean chinese) {
        StringBuilder builder = new StringBuilder();
        String name = candidate == null ? text(chinese, "adm.ai.assistant.pattern.default_name")
            : candidate.displayName;
        builder.append(text(chinese, "adm.ai.assistant.pattern.details_title"))
            .append(name);
        builder.append("\n")
            .append(text(chinese, "adm.ai.assistant.pattern.type_label"))
            .append(
                isCrafting(pattern) ? text(chinese, "adm.ai.assistant.pattern.type_crafting")
                    : text(chinese, "adm.ai.assistant.pattern.type_processing"));
        appendAeStacks(
            builder,
            "adm.ai.assistant.pattern.item_inputs",
            pattern == null ? null : pattern.getCondensedInputs(),
            chinese);
        appendAeStacks(
            builder,
            "adm.ai.assistant.pattern.item_outputs",
            pattern == null ? null : pattern.getCondensedOutputs(),
            chinese);
        boolean fluidInput = AeCompat.patternFluids()
            .hasFluidInputs(pattern);
        boolean fluidOutput = AeCompat.patternFluids()
            .hasFluidOutputs(pattern);
        if (fluidInput) {
            builder.append("\n")
                .append(text(chinese, "adm.ai.assistant.pattern.fluid_inputs"));
            AeCompat.patternFluids()
                .appendFluidInputs(builder, pattern);
        }
        if (fluidOutput) {
            builder.append("\n")
                .append(text(chinese, "adm.ai.assistant.pattern.fluid_outputs"));
            AeCompat.patternFluids()
                .appendFluidOutputs(builder, pattern);
        }
        if (!fluidInput && !fluidOutput && !isCrafting(pattern)) {
            builder.append("\n")
                .append(text(chinese, "adm.ai.assistant.pattern.fluid_process_note"));
        }
        return builder.toString();
    }

    private static boolean isCrafting(ICraftingPatternDetails pattern) {
        if (pattern == null) {
            return true;
        }
        try {
            Method method = pattern.getClass()
                .getMethod("isCraftable");
            Object value = method.invoke(pattern);
            return !(value instanceof Boolean) || ((Boolean) value).booleanValue();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static void appendAeStacks(StringBuilder builder, String titleKey, IAEItemStack[] stacks, boolean chinese) {
        builder.append("\n")
            .append(text(chinese, titleKey));
        boolean any = false;
        if (stacks != null) {
            for (IAEItemStack stack : stacks) {
                if (stack == null || stack.getStackSize() <= 0) {
                    continue;
                }
                ItemStack itemStack = stack.getItemStack();
                String display = itemStack == null ? stack.getLocalizedName() : itemStack.getDisplayName();
                builder.append("\n- ")
                    .append(
                        display == null || display.isEmpty() ? text(chinese, "adm.ai.assistant.pattern.unknown_item")
                            : display)
                    .append(" x")
                    .append(stack.getStackSize());
                any = true;
            }
        }
        if (!any) {
            builder.append("\n- ")
                .append(text(chinese, "adm.ai.assistant.pattern.none_exposed"));
        }
    }

    private static String text(boolean chinese, String key) {
        Map<String, String> primary = chinese ? ZH_CN : EN_US;
        String value = primary.get(key);
        if (value != null) {
            return value;
        }
        value = EN_US.get(key);
        return value == null ? key : value;
    }

    private static Map<String, String> loadLang(String locale) {
        InputStream stream = PatternDetailFormatter.class.getClassLoader()
            .getResourceAsStream(LANG_PATH + locale + ".lang");
        if (stream == null) {
            return Collections.emptyMap();
        }
        Map<String, String> values = new HashMap<String, String>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isEmpty() && line.charAt(0) == '\ufeff') {
                        line = line.substring(1);
                    }
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int separator = line.indexOf('=');
                    if (separator <= 0) {
                        continue;
                    }
                    values.put(line.substring(0, separator), line.substring(separator + 1));
                }
            } finally {
                reader.close();
            }
        } catch (IOException ignored) {
            return Collections.emptyMap();
        }
        return values;
    }
}
