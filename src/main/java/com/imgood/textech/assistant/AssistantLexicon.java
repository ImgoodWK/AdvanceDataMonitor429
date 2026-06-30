package com.imgood.textech.assistant;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;

public final class AssistantLexicon {

    private static final String FILE_NAME = "assistant-lexicon.json";
    private static final String RESOURCE_PATH = "/assets/textech/config/assistant-lexicon.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static LexiconData data = emptyDefaults();
    private static boolean loaded;

    private AssistantLexicon() {}

    public static synchronized LexiconData get() {
        if (!loaded) {
            reload();
        }
        return data;
    }

    public static synchronized String reload() {
        File file = AssistantDataFiles.dataFile(FILE_NAME);
        ensureFile(file);
        LexiconData fallback = resourceDefaults();
        LexiconData loadedData = null;
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
            loadedData = GSON.fromJson(reader, LexiconData.class);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Failed to load assistant lexicon; using bundled defaults", e);
        }
        data = merge(loadedData, fallback);
        loaded = true;
        return "Assistant lexicon reloaded from " + file.getPath() + ".";
    }

    public static File file() {
        return AssistantDataFiles.dataFile(FILE_NAME);
    }

    private static void ensureFile(File file) {
        if (file.exists()) {
            return;
        }
        try {
            InputStream stream = AssistantLexicon.class.getResourceAsStream(RESOURCE_PATH);
            if (stream != null) {
                try (InputStreamReader reader = new InputStreamReader(stream, "UTF-8");
                    java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                        new FileOutputStream(file),
                        "UTF-8")) {
                    char[] buffer = new char[4096];
                    int read;
                    while ((read = reader.read(buffer)) >= 0) {
                        writer.write(buffer, 0, read);
                    }
                }
                return;
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("Failed to copy default assistant lexicon resource", e);
        }
        try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            GSON.toJson(emptyDefaults(), writer);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Failed to write empty assistant lexicon", e);
        }
    }

    private static LexiconData resourceDefaults() {
        try {
            InputStream stream = AssistantLexicon.class.getResourceAsStream(RESOURCE_PATH);
            if (stream != null) {
                try (InputStreamReader reader = new InputStreamReader(stream, "UTF-8")) {
                    LexiconData parsed = GSON.fromJson(reader, LexiconData.class);
                    return merge(parsed, emptyDefaults());
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("Failed to load bundled assistant lexicon defaults", e);
        }
        return emptyDefaults();
    }

    private static LexiconData merge(LexiconData value, LexiconData fallback) {
        LexiconData result = value == null ? new LexiconData() : value;
        result.commonWords = list(result.commonWords, fallback.commonWords);
        result.modalParticles = list(result.modalParticles, fallback.modalParticles);
        result.edgePunctuationRegex = string(result.edgePunctuationRegex, fallback.edgePunctuationRegex);
        result.chineseNumbers = list(result.chineseNumbers, fallback.chineseNumbers);
        result.alternateTwoWords = list(result.alternateTwoWords, fallback.alternateTwoWords);
        result.optionPrefixes = list(result.optionPrefixes, fallback.optionPrefixes);
        result.optionSuffixes = list(result.optionSuffixes, fallback.optionSuffixes);
        result.amountUnits = list(result.amountUnits, fallback.amountUnits);
        result.groupAmountWords = list(result.groupAmountWords, fallback.groupAmountWords);
        result.halfGroupAmountWords = list(result.halfGroupAmountWords, fallback.halfGroupAmountWords);
        result.bucketAmountUnits = list(result.bucketAmountUnits, fallback.bucketAmountUnits);
        result.cancelWords = list(result.cancelWords, fallback.cancelWords);
        result.planCompleteWords = list(result.planCompleteWords, fallback.planCompleteWords);
        result.planListWords = list(result.planListWords, fallback.planListWords);
        result.planCreateWords = list(result.planCreateWords, fallback.planCreateWords);
        result.planDeleteWords = list(result.planDeleteWords, fallback.planDeleteWords);
        result.planModifyWords = list(result.planModifyWords, fallback.planModifyWords);
        result.planStripWords = list(result.planStripWords, fallback.planStripWords);
        result.powerQueryWords = list(result.powerQueryWords, fallback.powerQueryWords);
        result.steamQueryWords = list(result.steamQueryWords, fallback.steamQueryWords);
        result.recipeQueryWords = list(result.recipeQueryWords, fallback.recipeQueryWords);
        result.storageQueryWords = list(result.storageQueryWords, fallback.storageQueryWords);
        result.confirmWords = list(result.confirmWords, fallback.confirmWords);
        result.submitWords = list(result.submitWords, fallback.submitWords);
        result.batchSeparators = list(result.batchSeparators, fallback.batchSeparators);
        result.withdrawStrongWords = list(result.withdrawStrongWords, fallback.withdrawStrongWords);
        result.withdrawStripWords = list(result.withdrawStripWords, fallback.withdrawStripWords);
        result.craftContextWords = list(result.craftContextWords, fallback.craftContextWords);
        result.storageContextWords = list(result.storageContextWords, fallback.storageContextWords);
        result.ambiguousWeakWords = list(result.ambiguousWeakWords, fallback.ambiguousWeakWords);
        result.orderStrongWords = list(result.orderStrongWords, fallback.orderStrongWords);
        result.orderWeakWords = list(result.orderWeakWords, fallback.orderWeakWords);
        result.orderStripWords = list(result.orderStripWords, fallback.orderStripWords);
        result.confirmStripWords = list(result.confirmStripWords, fallback.confirmStripWords);
        result.candidateAmountBlockWords = list(result.candidateAmountBlockWords, fallback.candidateAmountBlockWords);
        result.testStorageWords = list(result.testStorageWords, fallback.testStorageWords);
        result.testRecipeWords = list(result.testRecipeWords, fallback.testRecipeWords);
        result.fluidScopeWords = list(result.fluidScopeWords, fallback.fluidScopeWords);
        result.itemScopeWords = list(result.itemScopeWords, fallback.itemScopeWords);
        result.queryStripWords = list(result.queryStripWords, fallback.queryStripWords);
        result.recipeStripWords = list(result.recipeStripWords, fallback.recipeStripWords);
        result.storageStripWords = list(result.storageStripWords, fallback.storageStripWords);
        result.timeMinuteUnits = list(result.timeMinuteUnits, fallback.timeMinuteUnits);
        result.timeHourUnits = list(result.timeHourUnits, fallback.timeHourUnits);
        result.timeAfterWords = list(result.timeAfterWords, fallback.timeAfterWords);
        result.timeDayWords = list(result.timeDayWords, fallback.timeDayWords);
        result.timePointWords = list(result.timePointWords, fallback.timePointWords);
        result.timeMinuteWords = list(result.timeMinuteWords, fallback.timeMinuteWords);
        result.timeTomorrowWords = list(result.timeTomorrowWords, fallback.timeTomorrowWords);
        result.timeAfternoonWords = list(result.timeAfternoonWords, fallback.timeAfternoonWords);
        result.timeStripWords = list(result.timeStripWords, fallback.timeStripWords);
        result.messages = map(result.messages, fallback.messages);
        validatePatterns(result, fallback);
        return result;
    }

    private static List<String> list(List<String> value, List<String> fallback) {
        if (value != null && !value.isEmpty()) {
            return new ArrayList<String>(value);
        }
        return fallback == null ? new ArrayList<String>() : new ArrayList<String>(fallback);
    }

    private static String string(String value, String fallback) {
        return value == null || value.trim()
            .isEmpty() ? fallback : value;
    }

    private static Map<String, String> map(Map<String, String> value, Map<String, String> fallback) {
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<String, String>();
        if (fallback != null) {
            result.putAll(fallback);
        }
        if (value != null) {
            result.putAll(value);
        }
        return result;
    }

    private static void validatePatterns(LexiconData result, LexiconData fallback) {
        try {
            Pattern.compile(result.edgePunctuationRegex);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn(
                "Invalid assistant edge punctuation regex; using bundled fallback: " + result.edgePunctuationRegex,
                e);
            result.edgePunctuationRegex = fallback.edgePunctuationRegex;
        }
    }

    public static String message(String key, String fallback) {
        String value = get().messages.get(key);
        return AssistantTextNormalizer.unescape(value == null ? fallback : value);
    }

    private static LexiconData emptyDefaults() {
        LexiconData d = new LexiconData();
        d.commonWords = new ArrayList<String>();
        d.modalParticles = new ArrayList<String>();
        d.edgePunctuationRegex = "^[\\s,.;:!?]+|[\\s,.;:!?]+$";
        d.chineseNumbers = new ArrayList<String>();
        d.alternateTwoWords = new ArrayList<String>();
        d.optionPrefixes = new ArrayList<String>();
        d.optionSuffixes = new ArrayList<String>();
        d.amountUnits = new ArrayList<String>();
        d.groupAmountWords = new ArrayList<String>();
        d.halfGroupAmountWords = new ArrayList<String>();
        d.bucketAmountUnits = new ArrayList<String>();
        d.cancelWords = new ArrayList<String>();
        d.planCompleteWords = new ArrayList<String>();
        d.planListWords = new ArrayList<String>();
        d.planCreateWords = new ArrayList<String>();
        d.planDeleteWords = new ArrayList<String>();
        d.planModifyWords = new ArrayList<String>();
        d.planStripWords = new ArrayList<String>();
        d.powerQueryWords = new ArrayList<String>();
        d.steamQueryWords = new ArrayList<String>();
        d.recipeQueryWords = new ArrayList<String>();
        d.storageQueryWords = new ArrayList<String>();
        d.confirmWords = new ArrayList<String>();
        d.submitWords = new ArrayList<String>();
        d.batchSeparators = new ArrayList<String>();
        d.withdrawStrongWords = new ArrayList<String>();
        d.withdrawStripWords = new ArrayList<String>();
        d.craftContextWords = new ArrayList<String>();
        d.storageContextWords = new ArrayList<String>();
        d.ambiguousWeakWords = new ArrayList<String>();
        d.orderStrongWords = new ArrayList<String>();
        d.orderWeakWords = new ArrayList<String>();
        d.orderStripWords = new ArrayList<String>();
        d.confirmStripWords = new ArrayList<String>();
        d.candidateAmountBlockWords = new ArrayList<String>();
        d.testStorageWords = new ArrayList<String>();
        d.testRecipeWords = new ArrayList<String>();
        d.fluidScopeWords = new ArrayList<String>();
        d.itemScopeWords = new ArrayList<String>();
        d.queryStripWords = new ArrayList<String>();
        d.recipeStripWords = new ArrayList<String>();
        d.storageStripWords = new ArrayList<String>();
        d.timeMinuteUnits = new ArrayList<String>();
        d.timeHourUnits = new ArrayList<String>();
        d.timeAfterWords = new ArrayList<String>();
        d.timeDayWords = new ArrayList<String>();
        d.timePointWords = new ArrayList<String>();
        d.timeMinuteWords = new ArrayList<String>();
        d.timeTomorrowWords = new ArrayList<String>();
        d.timeAfternoonWords = new ArrayList<String>();
        d.timeStripWords = new ArrayList<String>();
        d.messages = new java.util.LinkedHashMap<String, String>();
        return d;
    }

    public static Pattern edgePunctuationPattern() {
        return Pattern.compile(get().edgePunctuationRegex);
    }

    public static final class LexiconData {

        public List<String> commonWords;
        public List<String> modalParticles;
        public String edgePunctuationRegex;
        public List<String> chineseNumbers;
        public List<String> alternateTwoWords;
        public List<String> optionPrefixes;
        public List<String> optionSuffixes;
        public List<String> amountUnits;
        public List<String> groupAmountWords;
        public List<String> halfGroupAmountWords;
        public List<String> bucketAmountUnits;
        public List<String> cancelWords;
        public List<String> planCompleteWords;
        public List<String> planListWords;
        public List<String> planCreateWords;
        public List<String> planDeleteWords;
        public List<String> planModifyWords;
        public List<String> planStripWords;
        public List<String> powerQueryWords;
        public List<String> steamQueryWords;
        public List<String> recipeQueryWords;
        public List<String> storageQueryWords;
        public List<String> confirmWords;
        public List<String> submitWords;
        public List<String> batchSeparators;
        public List<String> withdrawStrongWords;
        public List<String> withdrawStripWords;
        public List<String> craftContextWords;
        public List<String> storageContextWords;
        public List<String> ambiguousWeakWords;
        public List<String> orderStrongWords;
        public List<String> orderWeakWords;
        public List<String> orderStripWords;
        public List<String> confirmStripWords;
        public List<String> candidateAmountBlockWords;
        public List<String> testStorageWords;
        public List<String> testRecipeWords;
        public List<String> fluidScopeWords;
        public List<String> itemScopeWords;
        public List<String> queryStripWords;
        public List<String> recipeStripWords;
        public List<String> storageStripWords;
        public List<String> timeMinuteUnits;
        public List<String> timeHourUnits;
        public List<String> timeAfterWords;
        public List<String> timeDayWords;
        public List<String> timePointWords;
        public List<String> timeMinuteWords;
        public List<String> timeTomorrowWords;
        public List<String> timeAfternoonWords;
        public List<String> timeStripWords;
        public Map<String, String> messages;
    }
}
