package test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.imgood.advancedatamonitor.assistant.AssistantAiIntentJsonParser;
import com.imgood.advancedatamonitor.assistant.AssistantIntent;
import com.imgood.advancedatamonitor.assistant.AssistantIntentPlan;
import com.imgood.advancedatamonitor.assistant.AssistantIntentService;
import com.imgood.advancedatamonitor.assistant.AssistantIntentTask;
import com.imgood.advancedatamonitor.assistant.AssistantIntentType;
import com.imgood.advancedatamonitor.assistant.AssistantLexicon;
import com.imgood.advancedatamonitor.assistant.AssistantOrderLine;

public final class AssistantIntentParserSuite {

    private AssistantIntentParserSuite() {}

    public static void main(String[] args) throws Exception {
        installLexicon();
        AssistantIntentService parser = new AssistantIntentService();
        List<Case> cases = cases();
        int passed = 0;
        List<String> failures = new ArrayList<String>();

        System.out.println("ADM assistant parser suite");
        System.out.println("Case count: " + cases.size());
        System.out.println();

        for (int i = 0; i < cases.size(); i++) {
            Case c = cases.get(i);
            AssistantIntent actual = parser.parse(c.input);
            List<String> problems = c.check(actual);
            boolean ok = problems.isEmpty();
            if (ok) {
                passed++;
            } else {
                failures.add(
                    "#" + (i + 1)
                        + " "
                        + c.name
                        + " input='"
                        + c.input
                        + "' problems="
                        + problems
                        + " actual="
                        + describe(actual));
            }
            System.out.println((ok ? "PASS" : "FAIL") + " #" + (i + 1) + " " + c.name);
            System.out.println("  input : " + c.input);
            System.out.println("  expect: " + c.describeExpected());
            System.out.println("  actual: " + describe(actual));
        }

        AiSuiteResult aiResult = runAiJsonParserCases();
        passed += aiResult.passed;
        failures.addAll(aiResult.failures);

        System.out.println();
        System.out.println(
            "Summary: passed=" + passed + ", failed=" + failures.size() + ", total=" + (cases.size() + aiResult.total));
        if (!failures.isEmpty()) {
            System.out.println();
            System.out.println("Failures:");
            for (String failure : failures) {
                System.out.println("  " + failure);
            }
            System.exit(1);
        }
    }

    private static String describe(AssistantIntent intent) {
        StringBuilder builder = new StringBuilder();
        builder.append("type=")
            .append(intent.type);
        builder.append(", target='")
            .append(intent.target)
            .append("'");
        builder.append(", amount=")
            .append(intent.amount);
        builder.append(", option=")
            .append(intent.optionNumber);
        builder.append(", scope=")
            .append(intent.storageScope);
        if (!intent.orderLines.isEmpty()) {
            builder.append(", lines=[");
            for (int i = 0; i < intent.orderLines.size(); i++) {
                if (i > 0) {
                    builder.append("; ");
                }
                AssistantOrderLine line = intent.orderLines.get(i);
                builder.append(line.lineIndex)
                    .append(":'")
                    .append(line.target)
                    .append("' x")
                    .append(line.amount);
            }
            builder.append("]");
        }
        return builder.toString();
    }

    private static List<Case> cases() {
        List<Case> list = new ArrayList<Case>();
        list.add(
            new Case("test defaults to storage overview", "test", AssistantIntentType.QUERY_STORAGE).target("")
                .amount(1)
                .scope(AssistantIntent.STORAGE_SCOPE_ALL));
        list.add(
            new Case("storage overview", "查询库存", AssistantIntentType.QUERY_STORAGE).target("")
                .amount(1)
                .scope(AssistantIntent.STORAGE_SCOPE_ALL));
        list.add(
            new Case("item storage query", "库存里有多少铁锭", AssistantIntentType.QUERY_STORAGE).target("铁锭")
                .amount(1)
                .scope(AssistantIntent.STORAGE_SCOPE_ALL));
        list.add(
            new Case("item list scope", "查询物品清单", AssistantIntentType.QUERY_STORAGE).target("")
                .amount(1)
                .scope(AssistantIntent.STORAGE_SCOPE_ITEMS));
        list.add(
            new Case("fluid list scope", "查询流体清单", AssistantIntentType.QUERY_STORAGE).target("")
                .amount(1)
                .scope(AssistantIntent.STORAGE_SCOPE_FLUIDS));
        list.add(
            new Case("power query", "电量", AssistantIntentType.QUERY_POWER).target("")
                .amount(0)
                .option(-1));

        list.add(
            new Case("recipe query", "查询木棍配方", AssistantIntentType.QUERY_RECIPE).target("木棍")
                .amount(1)
                .option(-1));
        list.add(
            new Case("recipe natural phrasing", "木棍怎么合成", AssistantIntentType.QUERY_RECIPE).target("木棍")
                .amount(1)
                .option(-1));
        list.add(
            new Case("test recipe", "test 配方 木棍", AssistantIntentType.QUERY_RECIPE).target("木棍")
                .amount(1)
                .option(-1));

        list.add(
            new Case("order explicit amount", "下单64个木棍", AssistantIntentType.ORDER_ITEM).target("木棍")
                .amount(64)
                .option(-1));
        list.add(
            new Case("order stack amount", "下单一组木棍", AssistantIntentType.ORDER_ITEM).target("木棍")
                .amount(64)
                .option(-1));
        list.add(
            new Case("order half stack amount", "做半组木棍", AssistantIntentType.ORDER_ITEM).target("木棍")
                .amount(32)
                .option(-1));
        list.add(
            new Case("weak order with amount", "做10个楼梯", AssistantIntentType.ORDER_ITEM).target("楼梯")
                .amount(10)
                .option(-1));
        list.add(
            new Case("order default amount", "下单木棍", AssistantIntentType.ORDER_ITEM).target("木棍")
                .amount(1)
                .option(-1));
        list.add(
            new Case("make one item", "做一个木棍", AssistantIntentType.ORDER_ITEM).target("木棍")
                .amount(1)
                .option(-1));
        list.add(
            new Case("two as alternate word", "下单两组木棍", AssistantIntentType.ORDER_ITEM).target("木棍")
                .amount(128)
                .option(-1));

        list.add(
            new Case("confirm bare number", "1", AssistantIntentType.CONFIRM_OPTION).target("")
                .amount(0)
                .option(1));
        list.add(
            new Case("confirm prefixed number", "确认1", AssistantIntentType.CONFIRM_OPTION).target("")
                .amount(0)
                .option(1));
        list.add(
            new Case("confirm with amount override spaced", "确认1 10个", AssistantIntentType.CONFIRM_OPTION).target("")
                .amount(10)
                .option(1));
        list.add(
            new Case("confirm with compact amount override", "第1个10个", AssistantIntentType.CONFIRM_OPTION).target("")
                .amount(10)
                .option(1));
        list.add(
            new Case("confirm by candidate name and amount", "楼梯10个", AssistantIntentType.CONFIRM_OPTION).target("楼梯")
                .amount(10)
                .option(-1));
        list.add(
            new Case("confirm Chinese ordinal", "第二个", AssistantIntentType.CONFIRM_OPTION).target("")
                .amount(0)
                .option(2));
        list.add(
            new Case("confirm Chinese eleven", "第十一个", AssistantIntentType.CONFIRM_OPTION).target("")
                .amount(0)
                .option(11));

        list.add(
            new Case("cancel", "取消", AssistantIntentType.CANCEL).target("")
                .amount(0)
                .option(-1));

        list.add(
            new Case("batch order explicit amounts", "下单64个木棍和10个楼梯", AssistantIntentType.ORDER_BATCH).line("木棍", 64)
                .line("楼梯", 10));
        list.add(
            new Case("batch order repeated verb", "下单木棍，再下单楼梯", AssistantIntentType.ORDER_BATCH).line("木棍", 1)
                .line("楼梯", 1));

        list.add(
            new Case("withdraw explicit amount", "取出64个铁锭", AssistantIntentType.WITHDRAW_ITEM).target("铁锭")
                .amount(64)
                .option(-1));
        list.add(
            new Case("withdraw give me", "给我32木棍", AssistantIntentType.WITHDRAW_ITEM).target("木棍")
                .amount(32)
                .option(-1));
        list.add(
            new Case("withdraw storage context weak word", "从库存来64铁锭", AssistantIntentType.WITHDRAW_ITEM).target("铁锭")
                .amount(64)
                .option(-1));
        list.add(
            new Case("batch withdraw", "取出64个铁锭和32个木棍", AssistantIntentType.WITHDRAW_BATCH).line("铁锭", 64)
                .line("木棍", 32));
        list.add(
            new Case("ambiguous weak word", "来64铁锭", AssistantIntentType.CLARIFY).target("")
                .amount(0)
                .option(-1));
        list.add(
            new Case("order craft context weak word", "来64铁锭合成", AssistantIntentType.ORDER_ITEM).target("铁锭")
                .amount(64)
                .option(-1));

        list.add(
            new Case("plan create", "计划 做一组木棍", AssistantIntentType.PLAN_CREATE).target("做一组木棍")
                .amount(0)
                .option(-1));
        list.add(
            new Case("plan list", "查看计划", AssistantIntentType.PLAN_LIST).target("")
                .amount(0)
                .option(-1));
        list.add(
            new Case("plan complete", "完成第1个计划", AssistantIntentType.PLAN_COMPLETE).amount(0)
                .option(1));

        list.add(new Case("chat fallback greeting", "你好，介绍一下你自己", AssistantIntentType.CHAT));
        list.add(new Case("chat fallback open question", "今天适合玩什么模组", AssistantIntentType.CHAT));
        return list;
    }

    private static AiSuiteResult runAiJsonParserCases() {
        AssistantAiIntentJsonParser parser = new AssistantAiIntentJsonParser();
        List<AiCase> cases = new ArrayList<AiCase>();
        cases.add(
            new AiCase(
                "ai single recipe",
                "```json\n{\"tasks\":[{\"type\":\"QUERY_RECIPE\",\"target\":\"木棍\",\"amount\":1,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.95}]}\n```")
                    .task(AssistantIntentType.QUERY_RECIPE, "木棍", 1, -1, AssistantIntent.STORAGE_SCOPE_ALL));
        cases.add(
            new AiCase(
                "ai multi recipe",
                "{\"tasks\":[{\"type\":\"QUERY_RECIPE\",\"target\":\"木棍\",\"amount\":1,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9},{\"type\":\"QUERY_RECIPE\",\"target\":\"楼梯\",\"amount\":1,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9}]}")
                    .task(AssistantIntentType.QUERY_RECIPE, "木棍", 1, -1, AssistantIntent.STORAGE_SCOPE_ALL)
                    .task(AssistantIntentType.QUERY_RECIPE, "楼梯", 1, -1, AssistantIntent.STORAGE_SCOPE_ALL));
        cases.add(
            new AiCase(
                "ai batch order",
                "{\"tasks\":[{\"type\":\"ORDER_ITEM\",\"target\":\"木棍\",\"amount\":64,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9},{\"type\":\"ORDER_ITEM\",\"target\":\"楼梯\",\"amount\":10,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.88}]}")
                    .task(AssistantIntentType.ORDER_ITEM, "木棍", 64, -1, AssistantIntent.STORAGE_SCOPE_ALL)
                    .task(AssistantIntentType.ORDER_ITEM, "楼梯", 10, -1, AssistantIntent.STORAGE_SCOPE_ALL));
        cases.add(
            new AiCase(
                "ai confirm option",
                "{\"tasks\":[{\"type\":\"CONFIRM_OPTION\",\"target\":\"\",\"amount\":0,\"optionNumber\":2,\"storageScope\":\"all\",\"confidence\":0.93}]}")
                    .task(AssistantIntentType.CONFIRM_OPTION, "", 0, 2, AssistantIntent.STORAGE_SCOPE_ALL));
        cases.add(
            new AiCase(
                "ai chat",
                "{\"tasks\":[{\"type\":\"CHAT\",\"target\":\"\",\"amount\":0,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.7}]}")
                    .task(AssistantIntentType.CHAT, "", 0, -1, AssistantIntent.STORAGE_SCOPE_ALL));
        cases.add(
            new AiCase(
                "ai empty recipe target valid",
                "{\"tasks\":[{\"type\":\"QUERY_RECIPE\",\"target\":\"\",\"amount\":1,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9}]}")
                    .task(AssistantIntentType.QUERY_RECIPE, "", 1, -1, AssistantIntent.STORAGE_SCOPE_ALL));
        cases.add(
            new AiCase(
                "ai withdraw item",
                "{\"tasks\":[{\"type\":\"WITHDRAW_ITEM\",\"target\":\"铁锭\",\"amount\":64,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9}]}")
                    .task(AssistantIntentType.WITHDRAW_ITEM, "铁锭", 64, -1, AssistantIntent.STORAGE_SCOPE_ALL));
        cases.add(
            new AiCase(
                "ai clarify",
                "{\"tasks\":[{\"type\":\"CLARIFY\",\"target\":\"\",\"amount\":0,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9}]}")
                    .task(AssistantIntentType.CLARIFY, "", 0, -1, AssistantIntent.STORAGE_SCOPE_ALL));
        cases.add(
            new AiCase(
                "ai invalid empty order target",
                "{\"tasks\":[{\"type\":\"ORDER_ITEM\",\"target\":\"\",\"amount\":1,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9}]}"));
        cases.add(
            new AiCase(
                "ai invalid empty withdraw target",
                "{\"tasks\":[{\"type\":\"WITHDRAW_ITEM\",\"target\":\"\",\"amount\":1,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9}]}"));
        cases.add(
            new AiCase(
                "ai invalid low confidence",
                "{\"tasks\":[{\"type\":\"QUERY_STORAGE\",\"target\":\"??\",\"amount\":1,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.49}]}"));
        cases.add(
            new AiCase(
                "ai invalid storage scope",
                "{\"tasks\":[{\"type\":\"QUERY_STORAGE\",\"target\":\"??\",\"amount\":1,\"optionNumber\":-1,\"storageScope\":\"blocks\",\"confidence\":0.9}]}"));
        cases.add(
            new AiCase(
                "ai max eight tasks",
                "{\"tasks\":[{\"type\":\"QUERY_POWER\",\"target\":\"0\",\"amount\":0,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9},{\"type\":\"QUERY_POWER\",\"target\":\"1\",\"amount\":0,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9},{\"type\":\"QUERY_POWER\",\"target\":\"2\",\"amount\":0,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9},{\"type\":\"QUERY_POWER\",\"target\":\"3\",\"amount\":0,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9},{\"type\":\"QUERY_POWER\",\"target\":\"4\",\"amount\":0,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9},{\"type\":\"QUERY_POWER\",\"target\":\"5\",\"amount\":0,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9},{\"type\":\"QUERY_POWER\",\"target\":\"6\",\"amount\":0,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9},{\"type\":\"QUERY_POWER\",\"target\":\"7\",\"amount\":0,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9},{\"type\":\"QUERY_POWER\",\"target\":\"8\",\"amount\":0,\"optionNumber\":-1,\"storageScope\":\"all\",\"confidence\":0.9}]}")
                    .task(AssistantIntentType.QUERY_POWER, "0", 0, -1, AssistantIntent.STORAGE_SCOPE_ALL)
                    .task(AssistantIntentType.QUERY_POWER, "1", 0, -1, AssistantIntent.STORAGE_SCOPE_ALL)
                    .task(AssistantIntentType.QUERY_POWER, "2", 0, -1, AssistantIntent.STORAGE_SCOPE_ALL)
                    .task(AssistantIntentType.QUERY_POWER, "3", 0, -1, AssistantIntent.STORAGE_SCOPE_ALL)
                    .task(AssistantIntentType.QUERY_POWER, "4", 0, -1, AssistantIntent.STORAGE_SCOPE_ALL)
                    .task(AssistantIntentType.QUERY_POWER, "5", 0, -1, AssistantIntent.STORAGE_SCOPE_ALL)
                    .task(AssistantIntentType.QUERY_POWER, "6", 0, -1, AssistantIntent.STORAGE_SCOPE_ALL)
                    .task(AssistantIntentType.QUERY_POWER, "7", 0, -1, AssistantIntent.STORAGE_SCOPE_ALL));

        int passed = 0;
        List<String> failures = new ArrayList<String>();
        System.out.println();
        System.out.println("ADM assistant AI JSON parser suite");
        System.out.println("Case count: " + cases.size());
        System.out.println();
        for (int i = 0; i < cases.size(); i++) {
            AiCase c = cases.get(i);
            List<String> problems = new ArrayList<String>();
            AssistantIntentPlan plan = null;
            try {
                plan = parser.parse(c.input);
                problems.addAll(c.check(plan));
            } catch (Exception e) {
                problems.add(
                    "unexpected exception " + e.getClass()
                        .getSimpleName() + ": " + e.getMessage());
            }
            boolean ok = problems.isEmpty();
            if (ok) {
                passed++;
            } else {
                failures.add("AI #" + (i + 1) + " " + c.name + " problems=" + problems + " actual=" + describe(plan));
            }
            System.out.println((ok ? "PASS" : "FAIL") + " AI #" + (i + 1) + " " + c.name);
            System.out.println("  expect: " + c.describeExpected());
            System.out.println("  actual: " + describe(plan));
        }
        return new AiSuiteResult(passed, cases.size(), failures);
    }

    private static String describe(AssistantIntentPlan plan) {
        if (plan == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("tasks=")
            .append(plan.size())
            .append("[");
        for (int i = 0; i < plan.getTasks()
            .size(); i++) {
            if (i > 0) {
                builder.append("; ");
            }
            AssistantIntentTask task = plan.getTasks()
                .get(i);
            builder.append(task.type)
                .append(":'")
                .append(task.target)
                .append("' x")
                .append(task.amount)
                .append(" option=")
                .append(task.optionNumber)
                .append(" scope=")
                .append(task.storageScope);
        }
        builder.append("]");
        return builder.toString();
    }

    private static void installLexicon() throws Exception {
        AssistantLexicon.LexiconData d = new AssistantLexicon.LexiconData();
        d.commonWords = list("请", "帮我", "帮", "我");
        d.modalParticles = list("吗", "呢", "吧", "么", "嘛", "啊", "呀");
        d.edgePunctuationRegex = "^[\\s,.;:!?，。；：！？、]+|[\\s,.;:!?，。；：！？、]+$";
        d.chineseNumbers = list("一", "二", "三", "四", "五", "六", "七", "八", "九", "十");
        d.alternateTwoWords = list("两");
        d.optionPrefixes = list("第");
        d.optionSuffixes = list("个", "号");
        d.amountUnits = list("组", "个", "件", "根", "桶", "mb");
        d.groupAmountWords = list("一组");
        d.halfGroupAmountWords = list("半组");
        d.bucketAmountUnits = list("桶");
        d.cancelWords = list("取消", "算了", "停止");
        d.planCompleteWords = list("完成计划", "标记完成", "完成第");
        d.planListWords = list("列出计划", "查看计划", "有哪些计划", "计划列表");
        d.planCreateWords = list("计划", "提醒我", "预约", "待办");
        d.planStripWords = list("提醒我", "提醒", "计划", "待办", "预约");
        d.powerQueryWords = list("电量", "无线电网", "eu", "能源", "电网");
        d.recipeQueryWords = list(
            "配方",
            "样板",
            "样板配方",
            "怎么做",
            "怎么合成",
            "能不能合成",
            "pattern",
            "patterns",
            "recipe",
            "recipes");
        d.storageQueryWords = list(
            "库存",
            "存储",
            "容量",
            "占用",
            "百分比",
            "还有多少",
            "有多少",
            "多少",
            "数量",
            "流体",
            "液体",
            "气体",
            "物品",
            "清单",
            "有什么",
            "几种",
            "item",
            "fluid",
            "gas",
            "快满",
            "快空");
        d.confirmWords = list("确认", "选择", "第", "号");
        d.submitWords = list("确认", "提交");
        d.batchSeparators = list("和", "再", ",", "，", "、");
        d.orderStrongWords = list("下单", "订购", "制作");
        d.orderWeakWords = list("做", "合成", "来", "要");
        d.orderStripWords = list("下单", "做", "合成", "制作", "订购", "再", "来", "要", "根", "个", "件");
        d.confirmStripWords = list("确认", "选择", "第", "号", "个", "件", "根", "组");
        d.candidateAmountBlockWords = list("库存", "存储", "多少", "配方", "样板", "下单", "合成");
        d.testStorageWords = list("存储", "总览", "all", "storage");
        d.testRecipeWords = list("配方", "样板", "样板配方", "合成", "recipe", "recipes", "pattern", "patterns", "craft");
        d.fluidScopeWords = list("流体", "液体", "气体", "fluid", "fluids", "gas", "gases");
        d.itemScopeWords = list("物品", "物资", "item", "items");
        d.queryStripWords = list(
            "查询",
            "查一下",
            "查看下",
            "查看",
            "看一下",
            "看下",
            "还有多少",
            "有没有",
            "有无",
            "是否",
            "能否",
            "能不能",
            "可否",
            "可以不可以",
            "可以");
        d.recipeStripWords = list(
            "ae",
            "AE",
            "能不能",
            "可以",
            "能",
            "查看下",
            "查看",
            "查询",
            "看一下",
            "看下",
            "一下",
            "下",
            "看看",
            "网络",
            "里",
            "中",
            "的",
            "用什么",
            "什么",
            "哪些",
            "所有",
            "需要",
            "使用",
            "材料",
            "原料",
            "怎么做",
            "怎么合成",
            "合成",
            "制作",
            "配方",
            "样板",
            "样板配方",
            "吗",
            "recipe",
            "recipes",
            "pattern",
            "patterns",
            "craft",
            "with",
            "materials");
        d.storageStripWords = list(
            "ae",
            "AE",
            "哥特女皇",
            "女皇",
            "看一下",
            "看看",
            "查一下",
            "查看",
            "网络中",
            "网络里",
            "网络",
            "里",
            "中",
            "内",
            "的",
            "有什么",
            "有",
            "什么",
            "哪些",
            "清单",
            "列表",
            "有几种",
            "几种",
            "多少种",
            "是否有",
            "有没有",
            "有无",
            "是否",
            "能否",
            "能不能",
            "可否",
            "可以不可以",
            "可以",
            "存在",
            "情况",
            "空间",
            "库存",
            "存储",
            "容量",
            "占用",
            "百分比",
            "数量",
            "多少",
            "有多少",
            "帮",
            "总览",
            "总量",
            "统计",
            "快满",
            "快空");
        d.timeMinuteUnits = list("分钟", "分");
        d.timeHourUnits = list("小时", "钟头");
        d.timeAfterWords = list("后");
        d.timeDayWords = list("明天", "今天", "今晚", "晚上", "下午", "早上");
        d.timePointWords = list("点");
        d.timeMinuteWords = list("分");
        d.timeTomorrowWords = list("明天");
        d.timeAfternoonWords = list("晚", "下午");
        d.timeStripWords = list("提醒我", "提醒", "计划", "待办", "预约", "在");
        d.messages = new java.util.LinkedHashMap<String, String>();

        Field data = AssistantLexicon.class.getDeclaredField("data");
        data.setAccessible(true);
        data.set(null, d);
        Field loaded = AssistantLexicon.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.setBoolean(null, true);
    }

    private static List<String> list(String... values) {
        return new ArrayList<String>(Arrays.asList(values));
    }

    private static final class Case {

        private final String name;
        private final String input;
        private final AssistantIntentType type;
        private String target;
        private Long amount;
        private Integer option;
        private Integer scope;
        private final List<ExpectedLine> lines = new ArrayList<ExpectedLine>();

        private Case(String name, String input, AssistantIntentType type) {
            this.name = name;
            this.input = input;
            this.type = type;
        }

        private Case target(String value) {
            this.target = value;
            return this;
        }

        private Case amount(long value) {
            this.amount = Long.valueOf(value);
            return this;
        }

        private Case option(int value) {
            this.option = Integer.valueOf(value);
            return this;
        }

        private Case scope(int value) {
            this.scope = Integer.valueOf(value);
            return this;
        }

        private Case line(String target, long amount) {
            this.lines.add(new ExpectedLine(target, amount));
            return this;
        }

        private List<String> check(AssistantIntent actual) {
            List<String> problems = new ArrayList<String>();
            if (actual.type != this.type) {
                problems.add("type expected " + this.type + " got " + actual.type);
            }
            if (this.target != null && !this.target.equals(actual.target)) {
                problems.add("target expected '" + this.target + "' got '" + actual.target + "'");
            }
            if (this.amount != null && this.amount.longValue() != actual.amount) {
                problems.add("amount expected " + this.amount + " got " + actual.amount);
            }
            if (this.option != null && this.option.intValue() != actual.optionNumber) {
                problems.add("option expected " + this.option + " got " + actual.optionNumber);
            }
            if (this.scope != null && this.scope.intValue() != actual.storageScope) {
                problems.add("scope expected " + this.scope + " got " + actual.storageScope);
            }
            if (!this.lines.isEmpty()) {
                if (this.lines.size() != actual.orderLines.size()) {
                    problems.add("line count expected " + this.lines.size() + " got " + actual.orderLines.size());
                } else {
                    for (int i = 0; i < this.lines.size(); i++) {
                        ExpectedLine expected = this.lines.get(i);
                        AssistantOrderLine actualLine = actual.orderLines.get(i);
                        if (!expected.target.equals(actualLine.target)) {
                            problems.add(
                                "line " + (i + 1)
                                    + " target expected '"
                                    + expected.target
                                    + "' got '"
                                    + actualLine.target
                                    + "'");
                        }
                        if (expected.amount != actualLine.amount) {
                            problems.add(
                                "line " + (i + 1)
                                    + " amount expected "
                                    + expected.amount
                                    + " got "
                                    + actualLine.amount);
                        }
                    }
                }
            }
            return problems;
        }

        private String describeExpected() {
            StringBuilder builder = new StringBuilder();
            builder.append("type=")
                .append(this.type);
            if (this.target != null) {
                builder.append(", target='")
                    .append(this.target)
                    .append("'");
            }
            if (this.amount != null) {
                builder.append(", amount=")
                    .append(this.amount);
            }
            if (this.option != null) {
                builder.append(", option=")
                    .append(this.option);
            }
            if (this.scope != null) {
                builder.append(", scope=")
                    .append(this.scope);
            }
            if (!this.lines.isEmpty()) {
                builder.append(", lines=")
                    .append(this.lines);
            }
            return builder.toString();
        }
    }

    private static final class AiSuiteResult {

        private final int passed;
        private final int total;
        private final List<String> failures;

        private AiSuiteResult(int passed, int total, List<String> failures) {
            this.passed = passed;
            this.total = total;
            this.failures = failures;
        }
    }

    private static final class AiCase {

        private final String name;
        private final String input;
        private final List<ExpectedTask> tasks = new ArrayList<ExpectedTask>();

        private AiCase(String name, String input) {
            this.name = name;
            this.input = input;
        }

        private AiCase task(AssistantIntentType type, String target, long amount, int option, int scope) {
            this.tasks.add(new ExpectedTask(type, target, amount, option, scope));
            return this;
        }

        private List<String> check(AssistantIntentPlan actual) {
            List<String> problems = new ArrayList<String>();
            if (actual.size() != this.tasks.size()) {
                problems.add("task count expected " + this.tasks.size() + " got " + actual.size());
                return problems;
            }
            for (int i = 0; i < this.tasks.size(); i++) {
                ExpectedTask expected = this.tasks.get(i);
                AssistantIntentTask actualTask = actual.getTasks()
                    .get(i);
                if (expected.type != actualTask.type) {
                    problems.add("task " + (i + 1) + " type expected " + expected.type + " got " + actualTask.type);
                }
                if (!expected.target.equals(actualTask.target)) {
                    problems.add(
                        "task " + (i + 1)
                            + " target expected '"
                            + expected.target
                            + "' got '"
                            + actualTask.target
                            + "'");
                }
                if (expected.amount != actualTask.amount) {
                    problems
                        .add("task " + (i + 1) + " amount expected " + expected.amount + " got " + actualTask.amount);
                }
                if (expected.option != actualTask.optionNumber) {
                    problems.add(
                        "task " + (i + 1) + " option expected " + expected.option + " got " + actualTask.optionNumber);
                }
                if (expected.scope != actualTask.storageScope) {
                    problems.add(
                        "task " + (i + 1) + " scope expected " + expected.scope + " got " + actualTask.storageScope);
                }
            }
            return problems;
        }

        private String describeExpected() {
            return this.tasks.toString();
        }
    }

    private static final class ExpectedTask {

        private final AssistantIntentType type;
        private final String target;
        private final long amount;
        private final int option;
        private final int scope;

        private ExpectedTask(AssistantIntentType type, String target, long amount, int option, int scope) {
            this.type = type;
            this.target = target;
            this.amount = amount;
            this.option = option;
            this.scope = scope;
        }

        public String toString() {
            return this.type + ":'"
                + this.target
                + "' x"
                + this.amount
                + " option="
                + this.option
                + " scope="
                + this.scope;
        }
    }

    private static final class ExpectedLine {

        private final String target;
        private final long amount;

        private ExpectedLine(String target, long amount) {
            this.target = target;
            this.amount = amount;
        }

        public String toString() {
            return "'" + this.target + "' x" + this.amount;
        }
    }
}
