package com.imgood.textech.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.imgood.textech.assistant.AssistantLexicon.LexiconData;

public class AssistantIntentService {

    private Pattern optionNumberPattern(LexiconData lexicon) {
        return Pattern.compile(
            "(?:" + regexAlternation(lexicon.optionPrefixes)
                + ")?\\s*(\\d+)\\s*(?:"
                + regexAlternation(lexicon.optionSuffixes)
                + ")?");
    }

    private Pattern amountPattern(LexiconData lexicon) {
        return Pattern
            .compile("(" + numberTokenPattern(lexicon) + ")\\s*(" + regexAlternation(lexicon.amountUnits) + ")?");
    }

    public AssistantIntent parse(String text) {
        String raw = text == null ? "" : text.trim();
        String normalized = raw.toLowerCase();
        LexiconData lexicon = AssistantLexicon.get();
        if (raw.isEmpty()) {
            return AssistantIntent.chat(raw);
        }

        AssistantIntent testIntent = parseTestIntent(raw, normalized, lexicon);
        if (testIntent != null) {
            return testIntent;
        }

        int option = parseOptionNumber(normalized, lexicon);
        if (containsAny(normalized, lexicon.cancelWords)) {
            return new AssistantIntent(AssistantIntentType.CANCEL, raw, "", 0, -1);
        }
        if (containsAny(normalized, lexicon.planCompleteWords)) {
            return new AssistantIntent(AssistantIntentType.PLAN_COMPLETE, raw, stripPlanWords(raw, lexicon), 0, option);
        }
        if (containsAny(normalized, lexicon.planDeleteWords)) {
            return new AssistantIntent(AssistantIntentType.PLAN_DELETE, raw, stripPlanWords(raw, lexicon), 0, option);
        }
        if (containsAny(normalized, lexicon.planModifyWords)) {
            return new AssistantIntent(AssistantIntentType.PLAN_MODIFY, raw, stripPlanWords(raw, lexicon), 0, option);
        }
        if (containsAny(normalized, lexicon.planListWords)) {
            return new AssistantIntent(AssistantIntentType.PLAN_LIST, raw, "", 0, -1);
        }
        if (containsAny(normalized, lexicon.planCreateWords)) {
            return new AssistantIntent(AssistantIntentType.PLAN_ADD, raw, stripPlanWords(raw, lexicon), 0, -1);
        }
        if (containsAnyText(normalized, "天气", "下雨", "打雷", "雷暴", "weather", "rain", "thunder", "storm")) {
            return new AssistantIntent(AssistantIntentType.QUERY_WEATHER, raw, "", 0, -1);
        }
        if (containsAnyText(normalized, "时间", "几点", "白天", "晚上", "天亮", "天黑", "time", "day", "night")) {
            return new AssistantIntent(AssistantIntentType.QUERY_TIME, raw, "", 0, -1);
        }
        if (containsAnyText(
            normalized,
            "坐标",
            "位置",
            "朝向",
            "区块",
            "coordinate",
            "position",
            "where am i",
            "facing",
            "chunk")) {
            return new AssistantIntent(AssistantIntentType.QUERY_POSITION, raw, "", 0, -1);
        }
        if (containsAnyText(normalized, "群系", "生态群�?, "biome")) {
            return new AssistantIntent(AssistantIntentType.QUERY_BIOME, raw, "", 0, -1);
        }
        if (containsAnyText(
            normalized,
            "传�?,
            "传送到",
            "tp",
            "tpa",
            "teleport",
            "warp",
            "传送点",
            "传送列�?,
            "列出传�?,
            "所有传�?,
            "全部传�?)) {
            String target = stripTeleportWords(raw, lexicon);
            if (target.isEmpty() || containsAnyText(normalized, "传送点", "传送列�?, "列出传�?, "所有传�?, "全部传�?)) {
                return new AssistantIntent(AssistantIntentType.TELEPORT_LIST, raw, "", 0, -1);
            }
            return new AssistantIntent(AssistantIntentType.TELEPORT, raw, target, 0, -1);
        }
        if (containsAnyText(normalized, "背包空间", "背包空位", "背包", "inventory space", "inventory slots")) {
            return new AssistantIntent(
                AssistantIntentType.QUERY_INVENTORY,
                raw,
                stripStorageQueryTarget(raw, AssistantIntent.STORAGE_SCOPE_ITEMS, lexicon),
                extractAmount(normalized, lexicon),
                -1);
        }
        if (containsAnyText(
            normalized,
            "网络状�?,
            "连接�?,
            "数据显示�?,
            "高级数据显示�?,
            "link状�?,
            "link 状�?,
            "network status",
            "connector",
            "monitor")) {
            return new AssistantIntent(AssistantIntentType.QUERY_NETWORK, raw, "", 0, -1);
        }
        if (containsAnyText(normalized, "合成任务", "正在合成", "挂起任务", "crafting jobs", "craft jobs", "jobs")) {
            return new AssistantIntent(AssistantIntentType.QUERY_JOBS, raw, "", 0, -1);
        }
        if (containsAnyText(
            normalized,
            "\u84b8\u6c7d",
            "\u84b8\u6c7d\u91cf",
            "\u65e0\u7ebf\u84b8\u6c7d",
            "steam",
            "\u84b8\u6c7d\u7f51\u7edc",
            "\u84b8\u6c7d\u7f51")) {
            return new AssistantIntent(AssistantIntentType.QUERY_STEAM, raw, stripQueryWords(raw, lexicon), 0, -1);
        }
        if (containsAny(normalized, lexicon.powerQueryWords)) {
            return new AssistantIntent(
                AssistantIntentType.QUERY_POWER,
                raw,
                stripPowerQueryTarget(raw, lexicon),
                0,
                -1);
        }
        if (looksLikeRecipeQuery(normalized, lexicon)) {
            return new AssistantIntent(
                AssistantIntentType.QUERY_RECIPE,
                raw,
                stripRecipeQueryTarget(raw, lexicon),
                extractAmount(normalized, lexicon),
                -1);
        }
        if (containsAnyText(
            normalized,
            "查询物品数量",
            "物品数量",
            "库存数量",
            "储存数量",
            "所有物�?,
            "全部物品",
            "所有流�?,
            "全部流体",
            "查询数量",
            "查询库存",
            "库存列表",
            "储存列表",
            "item count",
            "storage count",
            "all items",
            "all fluids",
            "list items",
            "list storage",
            "inventory count",
            "查询所�?,
            "列出所�?,
            "列出物品",
            "list all")) {
            String target = stripItemCountQueryTarget(raw, lexicon);
            return new AssistantIntent(
                AssistantIntentType.QUERY_ITEM_COUNT,
                raw,
                target,
                extractAmount(normalized, lexicon),
                -1);
        }
        if (containsAnyText(
            normalized,
            "\u67e5\u8be2\u5b57\u8282",
            "\u5b57\u8282\u5360\u7528",
            "\u5b57\u8282\u4f7f\u7528",
            "\u5b58\u50a8\u5b57\u8282",
            "\u5b57\u8282\u7edf\u8ba1",
            "\u5b57\u8282\u4fe1\u606f",
            "\u7f51\u7edc\u5b57\u8282",
            "query bytes",
            "byte usage",
            "byte info",
            "storage bytes",
            "ae2 bytes",
            "check bytes",
            "\u7f51\u7edc\u5360\u7528",
            "\u7f51\u7edc\u4f7f\u7528",
            "\u5360\u7528\u7387",
            "\u5269\u4f59\u5b57\u8282",
            "\u53ef\u7528\u5b57\u8282",
            "\u5b57\u8282\u5360\u6bd4",
            "\u5b58\u50a8\u5360\u7528",
            "\u5b58\u50a8\u5360\u6bd4",
            "byte count",
            "byte query")) {
            return new AssistantIntent(AssistantIntentType.QUERY_BYTES, raw, "", 0, -1);
        }
        if (containsAny(normalized, lexicon.storageQueryWords)) {
            int scope = storageScope(normalized, lexicon);
            return new AssistantIntent(
                AssistantIntentType.QUERY_STORAGE,
                raw,
                stripStorageQueryTarget(raw, scope, lexicon),
                extractAmount(normalized, lexicon),
                -1,
                scope);
        }
        AssistantIntent batchWithdrawIntent = parseBatchWithdraw(raw, normalized, lexicon);
        if (batchWithdrawIntent != null) {
            return batchWithdrawIntent;
        }
        AssistantIntent batchIntent = parseBatchOrder(raw, normalized, lexicon);
        if (batchIntent != null) {
            return batchIntent;
        }
        if (looksLikeWithdrawIntent(normalized, lexicon)) {
            return new AssistantIntent(
                AssistantIntentType.WITHDRAW_ITEM,
                raw,
                stripWithdrawWords(raw, lexicon),
                extractAmount(normalized, lexicon),
                -1);
        }
        if (looksLikeAmbiguousWeakIntent(normalized, lexicon)) {
            return new AssistantIntent(AssistantIntentType.CLARIFY, raw, "", 0, -1);
        }
        if (looksLikeOrderIntent(normalized, lexicon)) {
            return new AssistantIntent(
                AssistantIntentType.ORDER_ITEM,
                raw,
                stripOrderWords(raw, lexicon),
                extractAmount(normalized, lexicon),
                -1);
        }
        if (containsAny(normalized, lexicon.submitWords) && !containsAny(normalized, lexicon.planCreateWords)) {
            return new AssistantIntent(
                AssistantIntentType.CONFIRM_OPTION,
                raw,
                stripConfirmTarget(raw, lexicon),
                parseConfirmAmount(normalized, lexicon),
                option);
        }
        if (option > 0 && isConfirmOptionText(normalized, lexicon)) {
            return new AssistantIntent(
                AssistantIntentType.CONFIRM_OPTION,
                raw,
                stripConfirmTarget(raw, lexicon),
                parseConfirmAmount(normalized, lexicon),
                option);
        }
        if (looksLikeCandidateAmountOverride(normalized, lexicon)) {
            return new AssistantIntent(
                AssistantIntentType.CONFIRM_OPTION,
                raw,
                stripConfirmTarget(raw, lexicon),
                extractAmount(normalized, lexicon),
                -1);
        }
        return AssistantIntent.chat(raw);
    }

    private boolean looksLikeRecipeQuery(String normalized, LexiconData lexicon) {
        if (!containsAny(normalized, lexicon.recipeQueryWords)) {
            return false;
        }
        return !looksLikeOrderIntent(normalized, lexicon) || containsAny(normalized, lexicon.recipeStripWords);
    }

    private AssistantIntent parseBatchWithdraw(String raw, String normalized, LexiconData lexicon) {
        if (!looksLikeWithdrawIntent(normalized, lexicon) || !containsAny(normalized, lexicon.batchSeparators)) {
            return null;
        }
        String separators = regexAlternation(lexicon.batchSeparators);
        String body = stripCommon(raw);
        String[] parts = body.split(separators);
        List<AssistantOrderLine> lines = new ArrayList<AssistantOrderLine>();
        for (String part : parts) {
            String target = stripWithdrawWords(part, lexicon);
            if (target.isEmpty()) {
                continue;
            }
            lines.add(new AssistantOrderLine(lines.size() + 1, target, extractAmount(part.toLowerCase(), lexicon)));
        }
        return lines.size() > 1 ? AssistantIntent.withdrawBatch(raw, lines) : null;
    }

    private AssistantIntent parseBatchOrder(String raw, String normalized, LexiconData lexicon) {
        if (!looksLikeOrderIntent(normalized, lexicon) || !containsAny(normalized, lexicon.batchSeparators)) {
            return null;
        }
        String separators = regexAlternation(lexicon.batchSeparators);
        String body = stripCommon(raw);
        String[] parts = body.split(separators);
        List<AssistantOrderLine> lines = new ArrayList<AssistantOrderLine>();
        for (String part : parts) {
            String target = stripOrderWords(part, lexicon);
            if (target.isEmpty()) {
                continue;
            }
            lines.add(new AssistantOrderLine(lines.size() + 1, target, extractAmount(part.toLowerCase(), lexicon)));
        }
        return lines.size() > 1 ? AssistantIntent.orderBatch(raw, lines) : null;
    }

    private boolean looksLikeWithdrawIntent(String normalized, LexiconData lexicon) {
        if (containsAny(normalized, lexicon.withdrawStrongWords)) {
            return true;
        }
        if (containsAny(normalized, lexicon.orderWeakWords) && containsAny(normalized, lexicon.storageContextWords)
            && !containsAny(normalized, lexicon.craftContextWords)) {
            return amountPattern(lexicon).matcher(normalized)
                .find() || containsAny(normalized, lexicon.groupAmountWords)
                || containsAny(normalized, lexicon.halfGroupAmountWords);
        }
        return false;
    }

    private boolean looksLikeAmbiguousWeakIntent(String normalized, LexiconData lexicon) {
        if (!containsAny(normalized, lexicon.ambiguousWeakWords)) {
            return false;
        }
        if (containsAny(normalized, lexicon.orderStrongWords) || containsAny(normalized, lexicon.withdrawStrongWords)) {
            return false;
        }
        if (containsAny(normalized, lexicon.recipeQueryWords) || containsAny(normalized, lexicon.storageQueryWords)) {
            return false;
        }
        boolean hasCraftContext = containsAny(normalized, lexicon.craftContextWords);
        boolean hasStorageContext = containsAny(normalized, lexicon.storageContextWords);
        if (hasCraftContext && !hasStorageContext) {
            return false;
        }
        if (hasStorageContext && !hasCraftContext) {
            return false;
        }
        return amountPattern(lexicon).matcher(normalized)
            .find() || containsAny(normalized, lexicon.groupAmountWords)
            || containsAny(normalized, lexicon.halfGroupAmountWords)
            || !stripCommon(normalized).isEmpty();
    }

    private boolean looksLikeOrderIntent(String normalized, LexiconData lexicon) {
        if (containsAny(normalized, lexicon.orderStrongWords)) {
            return true;
        }
        if (containsAny(normalized, lexicon.orderWeakWords)) {
            if (containsAny(normalized, lexicon.craftContextWords)
                && !containsAny(normalized, lexicon.storageContextWords)) {
                return amountPattern(lexicon).matcher(normalized)
                    .find() || containsAny(normalized, lexicon.groupAmountWords)
                    || containsAny(normalized, lexicon.halfGroupAmountWords);
            }
            if (!containsAny(normalized, lexicon.storageContextWords)) {
                return amountPattern(lexicon).matcher(normalized)
                    .find() || containsAny(normalized, lexicon.groupAmountWords)
                    || containsAny(normalized, lexicon.halfGroupAmountWords);
            }
        }
        return false;
    }

    private AssistantIntent parseTestIntent(String raw, String normalized, LexiconData lexicon) {
        if (!normalized.startsWith("test")) {
            return null;
        }
        String body = stripPunctuation(
            raw.substring(4)
                .trim());
        String bodyNormalized = body.toLowerCase();
        if (body.isEmpty() || containsAny(bodyNormalized, lexicon.testStorageWords)) {
            return new AssistantIntent(
                AssistantIntentType.QUERY_STORAGE,
                raw,
                "",
                1,
                -1,
                AssistantIntent.STORAGE_SCOPE_ALL);
        }
        if (containsAny(bodyNormalized, lexicon.testRecipeWords)) {
            return new AssistantIntent(
                AssistantIntentType.QUERY_RECIPE,
                raw,
                stripRecipeQueryTarget(body, lexicon),
                1,
                -1);
        }
        if (containsAny(bodyNormalized, lexicon.fluidScopeWords)) {
            return new AssistantIntent(
                AssistantIntentType.QUERY_STORAGE,
                raw,
                stripStorageQueryTarget(body, AssistantIntent.STORAGE_SCOPE_FLUIDS, lexicon),
                1,
                -1,
                AssistantIntent.STORAGE_SCOPE_FLUIDS);
        }
        if (containsAny(bodyNormalized, lexicon.itemScopeWords)) {
            return new AssistantIntent(
                AssistantIntentType.QUERY_STORAGE,
                raw,
                stripStorageQueryTarget(body, AssistantIntent.STORAGE_SCOPE_ITEMS, lexicon),
                1,
                -1,
                AssistantIntent.STORAGE_SCOPE_ITEMS);
        }
        return new AssistantIntent(
            AssistantIntentType.QUERY_STORAGE,
            raw,
            stripStorageQueryTarget(body, AssistantIntent.STORAGE_SCOPE_ALL, lexicon),
            1,
            -1,
            AssistantIntent.STORAGE_SCOPE_ALL);
    }

    private int parseOptionNumber(String text, LexiconData lexicon) {
        Matcher matcher = optionNumberPattern(lexicon).matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        int chinese = parseChineseNumber(text, lexicon);
        if (chinese > 0) {
            return chinese;
        }
        for (int i = 0; i < lexicon.chineseNumbers.size(); i++) {
            String word = unescape(lexicon.chineseNumbers.get(i));
            if (containsSuffix(text, word, lexicon.optionSuffixes)
                || containsPrefixed(text, word, lexicon.optionPrefixes)
                || text.equals(word)) {
                return i + 1;
            }
        }
        return -1;
    }

    private boolean isConfirmOptionText(String text, LexiconData lexicon) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.matches("\\d+")) {
            return true;
        }
        String ordinal = regexAlternation(lexicon.optionPrefixes);
        String optionSuffix = "(?:" + regexAlternation(lexicon.optionSuffixes) + ")?";
        String amountUnit = "(?:" + regexAlternation(lexicon.amountUnits) + ")?";
        if (trimmed.matches("(?:" + ordinal + ")?\\s*\\d+\\s*" + optionSuffix + "\\s+\\d+\\s*" + amountUnit)) {
            return true;
        }
        return containsAny(trimmed, lexicon.confirmWords);
    }

    private long parseConfirmAmount(String text, LexiconData lexicon) {
        Matcher matcher = amountPattern(lexicon).matcher(text == null ? "" : text);
        if (matcher.find() && matcher.find()) {
            return parseAmountMatch(matcher, lexicon);
        }
        return 0L;
    }

    private long parseAmountMatch(Matcher matcher, LexiconData lexicon) {
        long value = parseNumberToken(matcher.group(1), lexicon);
        String unit = unescape(matcher.group(2) == null ? "" : matcher.group(2));
        if (containsGroupUnit(unit, lexicon)) {
            return value * 64L;
        }
        if (containsValue(lexicon.bucketAmountUnits, unit)) {
            return value * 1000L;
        }
        return value;
    }

    private boolean looksLikeCandidateAmountOverride(String text, LexiconData lexicon) {
        if (text == null) {
            return false;
        }
        Matcher matcher = amountPattern(lexicon).matcher(text);
        if (!matcher.find() || !hasExplicitAmountUnitOrDigits(matcher)) {
            return false;
        }
        if (containsAny(text, lexicon.candidateAmountBlockWords)) {
            return false;
        }
        return !stripConfirmTarget(text, lexicon).isEmpty();
    }

    private boolean hasExplicitAmountUnitOrDigits(Matcher matcher) {
        String token = matcher.group(1);
        String unit = matcher.group(2);
        return (unit != null && !unit.isEmpty()) || (token != null && token.matches("\\d+"));
    }

    private long extractAmount(String text, LexiconData lexicon) {
        Matcher matcher = amountPattern(lexicon).matcher(text);
        if (matcher.find()) {
            return parseAmountMatch(matcher, lexicon);
        }
        if (containsAny(text, lexicon.groupAmountWords)) return 64L;
        if (containsAny(text, lexicon.halfGroupAmountWords)) return 32L;
        return 1L;
    }

    private boolean containsAny(String text, List<String> values) {
        return AssistantTextNormalizer.containsAny(text, values);
    }

    private boolean containsAnyText(String text, String... values) {
        if (text == null || values == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && !value.isEmpty() && text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String stripOrderWords(String text, LexiconData lexicon) {
        String result = removeWords(stripCommon(text), lexicon.orderStripWords).trim();
        return stripPunctuation(stripAmountPrefix(result, lexicon));
    }

    private String stripWithdrawWords(String text, LexiconData lexicon) {
        String result = removeWords(stripCommon(text), lexicon.withdrawStripWords).trim();
        result = removeWords(result, lexicon.storageContextWords).trim();
        result = removeWords(result, lexicon.ambiguousWeakWords).trim();
        return stripPunctuation(stripAmountPrefix(result, lexicon));
    }

    private String stripConfirmTarget(String text, LexiconData lexicon) {
        String result = removeWords(stripCommon(text), lexicon.confirmStripWords).trim();
        result = amountPattern(lexicon).matcher(result)
            .replaceAll("")
            .trim();
        result = stripChineseOptionNumber(result, lexicon);
        return stripPunctuation(result);
    }

    private String stripPowerQueryTarget(String text, LexiconData lexicon) {
        String result = stripQueryWords(text, lexicon);
        result = removeWords(result, lexicon.powerQueryWords).trim();
        return stripPunctuation(stripAmountPrefix(stripModalParticles(result), lexicon));
    }

    private String stripRecipeQueryTarget(String text, LexiconData lexicon) {
        String result = removeWordsLongestFirst(stripCommon(text), lexicon.recipeStripWords).trim();
        result = stripQueryWords(result, lexicon);
        return stripPunctuation(stripAmountPrefix(stripModalParticles(result), lexicon));
    }

    private String stripQueryWords(String text, LexiconData lexicon) {
        String result = removeWords(stripCommon(text), lexicon.queryStripWords).trim();
        return stripAmountPrefix(stripModalParticles(result), lexicon);
    }

    private int storageScope(String normalized, LexiconData lexicon) {
        if (containsAny(normalized, lexicon.fluidScopeWords)) {
            return AssistantIntent.STORAGE_SCOPE_FLUIDS;
        }
        if (containsAny(normalized, lexicon.itemScopeWords)) {
            return AssistantIntent.STORAGE_SCOPE_ITEMS;
        }
        return AssistantIntent.STORAGE_SCOPE_ALL;
    }

    private String stripStorageQueryTarget(String text, int scope, LexiconData lexicon) {
        String result = stripQueryWords(text, lexicon);
        result = removeWords(result, lexicon.storageStripWords).trim();
        if (scope == AssistantIntent.STORAGE_SCOPE_FLUIDS) {
            result = removeWords(result, lexicon.fluidScopeWords).trim();
        } else if (scope == AssistantIntent.STORAGE_SCOPE_ITEMS) {
            result = removeWords(result, lexicon.itemScopeWords).trim();
        }
        return stripPunctuation(stripAmountPrefix(stripModalParticles(result), lexicon));
    }

    private String stripPlanWords(String text, LexiconData lexicon) {
        return removeWords(stripCommon(text), lexicon.planStripWords).trim();
    }

    private String stripTeleportWords(String text, LexiconData lexicon) {
        List<String> teleportStripWords = new ArrayList<String>();
        teleportStripWords.add("\u4f20\u9001\u5230"); // 传送到
        teleportStripWords.add("\u4f20\u9001"); // 传�?
        teleportStripWords.add("tp");
        teleportStripWords.add("tpa");
        teleportStripWords.add("teleport");
        teleportStripWords.add("warp");
        String result = stripCommon(text);
        result = removeWords(result, teleportStripWords).trim();
        return stripPunctuation(stripModalParticles(result));
    }

    private String stripCommon(String text) {
        return AssistantTextNormalizer.stripCommon(text);
    }

    private String stripModalParticles(String text) {
        return AssistantTextNormalizer.stripModalParticles(text);
    }

    private String stripItemCountQueryTarget(String text, LexiconData lexicon) {
        String result = stripCommon(text);
        result = removeWords(result, lexicon.queryStripWords).trim();
        java.util.List<String> itemCountWords = java.util.Arrays.asList(
            "\u67e5\u8be2\u7269\u54c1\u6570\u91cf",
            "\u7269\u54c1\u6570\u91cf",
            "\u5e93\u5b58\u6570\u91cf",
            "\u50a8\u5b58\u6570\u91cf",
            "\u6240\u6709\u7269\u54c1",
            "\u5168\u90e8\u7269\u54c1",
            "\u6240\u6709\u6d41\u4f53",
            "\u5168\u90e8\u6d41\u4f53",
            "\u67e5\u8be2\u6570\u91cf",
            "\u67e5\u8be2\u5e93\u5b58",
            "\u5e93\u5b58\u5217\u8868",
            "\u50a8\u5b58\u5217\u8868",
            "\u67e5\u8be2\u6240\u6709",
            "\u5217\u51fa\u6240\u6709",
            "\u5217\u51fa\u7269\u54c1",
            "item count",
            "storage count",
            "all items",
            "all fluids",
            "list items",
            "list storage",
            "inventory count",
            "list all");
        result = removeWords(result, itemCountWords).trim();
        return stripPunctuation(stripAmountPrefix(stripModalParticles(result), lexicon));
    }

    private String stripPunctuation(String text) {
        return AssistantTextNormalizer.stripPunctuation(text);
    }

    private String stripAmountPrefix(String text, LexiconData lexicon) {
        if (text == null) {
            return "";
        }
        String result = amountPattern(lexicon).matcher(text)
            .replaceFirst("")
            .trim();
        String amountWords = regexAlternation(join(lexicon.groupAmountWords, lexicon.halfGroupAmountWords));
        result = result.replaceFirst("^(?:" + amountWords + ")", "")
            .trim();
        return result;
    }

    private String removeWords(String text, List<String> words) {
        return AssistantTextNormalizer.removeWords(text, words);
    }

    private String removeWordsLongestFirst(String text, List<String> words) {
        String result = text == null ? "" : text;
        if (words == null || words.isEmpty()) {
            return result;
        }
        List<String> sorted = new ArrayList<String>(words);
        java.util.Collections.sort(sorted, new java.util.Comparator<String>() {

            @Override
            public int compare(String a, String b) {
                return unescape(b).length() - unescape(a).length();
            }
        });
        return removeWords(result, sorted);
    }

    private String unescape(String text) {
        return AssistantTextNormalizer.unescape(text);
    }

    private int parseChineseNumber(String text, LexiconData lexicon) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        String normalized = text;
        for (String prefix : lexicon.optionPrefixes) {
            normalized = normalized.replace(unescape(prefix), "");
        }
        for (String suffix : lexicon.optionSuffixes) {
            normalized = normalized.replace(unescape(suffix), "");
        }
        normalized = normalized.trim();
        String ten = chineseTen(lexicon);
        int index = normalized.indexOf(ten);
        if (index >= 0) {
            int left = index == 0 ? 1 : chineseDigit(normalized.substring(0, index), lexicon);
            int right = index + 1 >= normalized.length() ? 0 : chineseDigit(normalized.substring(index + 1), lexicon);
            int value = left * 10 + right;
            return value > 0 ? value : -1;
        }
        return chineseDigit(normalized, lexicon);
    }

    private long parseNumberToken(String token, LexiconData lexicon) {
        if (token == null || token.isEmpty()) {
            return 1L;
        }
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException ignored) {}
        int chinese = parseChineseNumber(token, lexicon);
        return chinese > 0 ? chinese : 1L;
    }

    private String numberTokenPattern(LexiconData lexicon) {
        String digits = chineseNumberCharacters(lexicon);
        if (digits.isEmpty()) {
            return "\\d+";
        }
        return "(?:\\d+|[" + digits + "]+)";
    }

    private String chineseNumberCharacters(LexiconData lexicon) {
        StringBuilder builder = new StringBuilder();
        appendRegexChars(builder, lexicon.chineseNumbers);
        appendRegexChars(builder, lexicon.alternateTwoWords);
        String ten = chineseTen(lexicon);
        if (!ten.isEmpty()) {
            appendRegexChar(builder, ten.charAt(0));
        }
        return builder.toString();
    }

    private void appendRegexChars(StringBuilder builder, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            String normalized = unescape(value);
            for (int i = 0; i < normalized.length(); i++) {
                appendRegexChar(builder, normalized.charAt(i));
            }
        }
    }

    private void appendRegexChar(StringBuilder builder, char value) {
        if (builder.indexOf(String.valueOf(value)) >= 0) {
            return;
        }
        if ("\\^-[]".indexOf(value) >= 0) {
            builder.append('\\');
        }
        builder.append(value);
    }

    private String stripChineseOptionNumber(String text, LexiconData lexicon) {
        String result = text == null ? "" : text.trim();
        int value = parseChineseNumber(result, lexicon);
        return value > 0 ? "" : result;
    }

    private String chineseTen(LexiconData lexicon) {
        if (lexicon.chineseNumbers != null && lexicon.chineseNumbers.size() >= 10) {
            return unescape(lexicon.chineseNumbers.get(9));
        }
        return "\u5341";
    }

    private int chineseDigit(String text, LexiconData lexicon) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < lexicon.chineseNumbers.size(); i++) {
            if (unescape(lexicon.chineseNumbers.get(i)).equals(text)) {
                return i + 1;
            }
        }
        if (containsValue(lexicon.alternateTwoWords, text)) {
            return 2;
        }
        return -1;
    }

    private boolean containsSuffix(String text, String word, List<String> suffixes) {
        for (String suffix : suffixes) {
            if (text.contains(word + unescape(suffix))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPrefixed(String text, String word, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (text.contains(unescape(prefix) + word)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsGroupUnit(String unit, LexiconData lexicon) {
        if (unit == null || unit.isEmpty()) {
            return false;
        }
        for (String word : lexicon.groupAmountWords) {
            String normalized = unescape(word);
            if (normalized.endsWith(unit) || unit.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsValue(List<String> values, String value) {
        if (values == null || value == null) {
            return false;
        }
        for (String candidate : values) {
            if (value.equals(unescape(candidate))) {
                return true;
            }
        }
        return false;
    }

    private List<String> join(List<String> a, List<String> b) {
        List<String> result = new ArrayList<String>();
        if (a != null) result.addAll(a);
        if (b != null) result.addAll(b);
        return result;
    }

    private String regexAlternation(List<String> values) {
        StringBuilder builder = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append("|");
                }
                builder.append(Pattern.quote(unescape(value)));
            }
        }
        return builder.length() == 0 ? "(?!)" : builder.toString();
    }
}
