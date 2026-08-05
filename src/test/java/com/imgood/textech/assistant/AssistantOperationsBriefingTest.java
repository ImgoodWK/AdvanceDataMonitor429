package com.imgood.textech.assistant;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/** Regression contracts for the read-only operations briefing and its query intent. */
public class AssistantOperationsBriefingTest {

    @Test
    public void healthyBriefingHasBilingualTitlesHealthyOverallAndNoUrgentAction() {
        List<AssistantOperationsBriefing.Section> sections = healthySections();

        String english = AssistantOperationsBriefing.format("en_US", sections);
        Assert.assertTrue(english.contains("Operations Briefing (read-only)"));
        Assert.assertTrue(english.contains("Overall: HEALTHY"));
        Assert.assertTrue(english.contains("No urgent action is indicated by the collected data."));
        Assert.assertFalse(english.contains("AE2 byte capacity is nearly full"));

        String chinese = AssistantOperationsBriefing.format("zh_CN", sections);
        Assert.assertTrue(chinese.contains("基地运维简报（只读）"));
        Assert.assertTrue(chinese.contains("总体状态：正常"));
        Assert.assertTrue(chinese.contains("已收集的数据中没有明确的紧急动作。"));
        Assert.assertFalse(chinese.contains("AE2 字节接近满载"));
    }

    @Test
    public void nearlyFullBytesRequireAttentionAndStorageRecommendation() {
        AssistantOperationsBriefing.Section bytes = AssistantOperationsBriefing.section(
            "bytes",
            "AE2 bytes",
            "Usage: 92.1% (nearly full)");

        Assert.assertEquals(AssistantOperationsBriefing.Status.ATTENTION, bytes.status);
        String report = AssistantOperationsBriefing.format("en_US", Arrays.asList(bytes));
        Assert.assertTrue(report.contains("[ATTENTION] AE2 bytes"));
        Assert.assertTrue(report.contains("add storage"));
        Assert.assertTrue(report.contains("clear unneeded contents"));
    }

    @Test
    public void unavailableByteSectionDoesNotSuppressOtherSections() {
        AssistantOperationsBriefing.Section bytes = AssistantOperationsBriefing.unavailable(
            "bytes",
            "AE2 bytes",
            "Byte query failed: no nearby Advance Network Link.");
        AssistantOperationsBriefing.Section jobs = AssistantOperationsBriefing.section(
            "jobs",
            "Crafting jobs",
            "No server-side AE2 crafting calculation is pending.");
        AssistantOperationsBriefing.Section planner = AssistantOperationsBriefing.section(
            "planner",
            "Planner",
            "No entries in the planner.");

        Assert.assertEquals(AssistantOperationsBriefing.Status.UNAVAILABLE, bytes.status);
        String report = AssistantOperationsBriefing.format("en_US", Arrays.asList(bytes, jobs, planner));
        Assert.assertTrue(report.contains("Overall: UNAVAILABLE"));
        Assert.assertTrue(report.contains("[UNAVAILABLE] AE2 bytes"));
        Assert.assertTrue(report.contains("Byte query failed: no nearby Advance Network Link."));
        Assert.assertTrue(report.contains("Crafting jobs"));
        Assert.assertTrue(report.contains("Planner"));
    }

    @Test
    public void networkWithoutAnyAdvanceLinkIsUnavailable() {
        AssistantOperationsBriefing.Section network = AssistantOperationsBriefing.section(
            "network",
            "ADM network and connectors",
            "ADM network/connector status:\nAdvance Network Link: 0 (Found 0 connector(s) nearby)");

        Assert.assertEquals(AssistantOperationsBriefing.Status.UNAVAILABLE, network.status);
    }

    @Test
    public void networkHealthUsesExplicitProviderStatusWithoutReevaluatingPresentationText() {
        AssistantOperationsBriefing.Section healthy = AssistantOperationsBriefing.section(
            "networkHealth",
            "Network health diagnostics",
            "This localized text contains failed, unknown, and warning words.",
            AssistantOperationsBriefing.fromNetworkHealthStatus("healthy"));
        AssistantOperationsBriefing.Section degraded = AssistantOperationsBriefing.section(
            "networkHealth",
            "Network health diagnostics",
            "No status marker is required in this presentation text.",
            AssistantOperationsBriefing.fromNetworkHealthStatus("degraded"));
        AssistantOperationsBriefing.Section failed = AssistantOperationsBriefing.section(
            "networkHealth",
            "Network health diagnostics",
            "Localized issue labels only.",
            AssistantOperationsBriefing.fromNetworkHealthStatus("failed"));
        AssistantOperationsBriefing.Section unknown = AssistantOperationsBriefing.section(
            "networkHealth",
            "Network health diagnostics",
            "This text even says healthy.",
            AssistantOperationsBriefing.fromNetworkHealthStatus("unknown"));

        Assert.assertEquals(AssistantOperationsBriefing.Status.HEALTHY, healthy.status);
        Assert.assertEquals(AssistantOperationsBriefing.Status.ATTENTION, degraded.status);
        Assert.assertEquals(AssistantOperationsBriefing.Status.ATTENTION, failed.status);
        Assert.assertEquals(AssistantOperationsBriefing.Status.UNAVAILABLE, unknown.status);
    }

    @Test
    public void networkHealthWithNoSnapshotIsUnavailable() {
        AssistantOperationsBriefing.Section missing = AssistantOperationsBriefing.section(
            "networkHealth",
            "Network health diagnostics",
            "No network health snapshot is available yet; wait for the server sampler.");

        Assert.assertEquals(AssistantOperationsBriefing.Status.UNAVAILABLE, missing.status);
    }

    @Test
    public void futureNetworkHealthStatusIsUnavailableByDefault() {
        Assert.assertEquals(
            AssistantOperationsBriefing.Status.UNAVAILABLE,
            AssistantOperationsBriefing.fromNetworkHealthStatus("future-status"));
        Assert.assertEquals(
            AssistantOperationsBriefing.Status.UNAVAILABLE,
            AssistantOperationsBriefing.fromNetworkHealthStatus(null));
    }

    @Test
    public void jobsPendingMarkerIsAttentionButExplicitNoPendingSentenceIsHealthy() {
        AssistantOperationsBriefing.Section none = AssistantOperationsBriefing.section(
            "jobs",
            "Crafting jobs",
            "No server-side AE2 crafting calculation is pending.");
        AssistantOperationsBriefing.Section pending = AssistantOperationsBriefing.section(
            "jobs",
            "Crafting jobs",
            "Pending AE2 crafting calculations: 2");

        Assert.assertEquals(AssistantOperationsBriefing.Status.HEALTHY, none.status);
        Assert.assertEquals(AssistantOperationsBriefing.Status.ATTENTION, pending.status);
    }

    @Test
    public void plannerEmptyTodoAndMissingPlannerHaveDistinctStatuses() {
        AssistantOperationsBriefing.Section empty = AssistantOperationsBriefing.section(
            "planner",
            "Advanced Planner",
            "No entries in the planner.");
        AssistantOperationsBriefing.Section todo = AssistantOperationsBriefing.section(
            "planner",
            "Advanced Planner",
            "[Todo] Review storage priorities");
        AssistantOperationsBriefing.Section missing = AssistantOperationsBriefing.section(
            "planner",
            "Advanced Planner",
            "You don't have an Advanced Planner.");

        Assert.assertEquals(AssistantOperationsBriefing.Status.HEALTHY, empty.status);
        Assert.assertEquals(AssistantOperationsBriefing.Status.ATTENTION, todo.status);
        Assert.assertEquals(AssistantOperationsBriefing.Status.UNAVAILABLE, missing.status);
        Assert.assertTrue(
            AssistantOperationsBriefing.format("en_US", Arrays.asList(missing))
                .contains("Advanced Planner required by unavailable sections"));
    }

    @Test
    public void longInputIsBoundedAndSecretsAreRedacted() {
        StringBuilder longSummary = new StringBuilder(
            "apiKey=plain-api-value token: plain-token sk-1234567890 \"apiKey\":\"quoted secret value\" client_secret='quoted secret' password=\"space secret\" ");
        while (longSummary.length() < 3_000) {
            longSummary.append("filler ");
        }

        List<AssistantOperationsBriefing.Section> sections = new ArrayList<AssistantOperationsBriefing.Section>();
        for (int i = 0; i < 8; i++) {
            sections.add(AssistantOperationsBriefing.section("section" + i, "Section " + i, longSummary.toString()));
        }

        String report = AssistantOperationsBriefing.format("en_US", sections);
        Assert.assertTrue(report.length() <= AssistantOperationsBriefing.MAX_TOTAL_CHARS);
        Assert.assertFalse(report.contains("plain-api-value"));
        Assert.assertFalse(report.contains("plain-token"));
        Assert.assertFalse(report.contains("sk-1234567890"));
        Assert.assertFalse(report.contains("quoted secret value"));
        Assert.assertFalse(report.contains("quoted secret"));
        Assert.assertFalse(report.contains("space secret"));
        Assert.assertTrue(report.contains("<redacted>"));
    }

    @Test
    public void chineseOutputFitsTheUtf8PacketField() {
        StringBuilder summary = new StringBuilder();
        while (summary.length() < 3_000) {
            summary.append("运行状态正常；");
        }
        List<AssistantOperationsBriefing.Section> sections = new ArrayList<AssistantOperationsBriefing.Section>();
        for (int i = 0; i < 8; i++) {
            sections.add(AssistantOperationsBriefing.section("section" + i, "分段 " + i, summary.toString()));
        }

        String report = AssistantOperationsBriefing.format("zh_CN", sections);
        Assert.assertTrue(
            report.getBytes(Charset.forName("UTF-8")).length <= AssistantOperationsBriefing.MAX_TOTAL_UTF8_BYTES);
    }

    @Test
    public void nullAndEmptyLocalesFollowChineseDefaultConvention() {
        List<AssistantOperationsBriefing.Section> sections = healthySections();

        Assert.assertTrue(
            AssistantOperationsBriefing.format(null, sections).contains("基地运维简报（只读）"));
        Assert.assertTrue(
            AssistantOperationsBriefing.format("", sections).contains("基地运维简报（只读）"));
    }

    @Test
    public void jsonParserAcceptsQueryBriefingTaskType() {
        AssistantIntentPlan plan = new AssistantAiIntentJsonParser().parse(
            "{\"tasks\":[{\"type\":\"QUERY_BRIEFING\",\"confidence\":0.9}]}" );

        Assert.assertFalse(plan.isEmpty());
        Assert.assertEquals(1, plan.size());
        Assert.assertEquals(AssistantIntentType.QUERY_BRIEFING, plan.getTasks().get(0).type);
    }

    @Test
    public void queryBriefingIsAppendedAfterEveryLegacyIntent() {
        Assert.assertEquals(
            AssistantIntentType.HUD_CLOSE.ordinal() + 1,
            AssistantIntentType.QUERY_BRIEFING.ordinal());
        AssistantIntentType[] values = AssistantIntentType.values();
        Assert.assertEquals(AssistantIntentType.QUERY_BRIEFING, values[values.length - 1]);
    }

    @Test
    public void localServiceRecognizesReadOnlyBriefingQueries() {
        AssistantIntentService service = new AssistantIntentService();
        String[] queries = { "operations briefing", "network health", "基地简报", "运维简报" };
        for (String query : queries) {
            Assert.assertEquals(
                "Expected read-only briefing intent for: " + query,
                AssistantIntentType.QUERY_BRIEFING,
                service.parse(query).type);
        }
    }

    @Test
    public void briefingOutputDoesNotPromiseMutatingActions() {
        String report = AssistantOperationsBriefing.format("en_US", healthySections());

        Assert.assertFalse(report.contains("Order submitted"));
        Assert.assertFalse(report.contains("Withdrawal completed"));
        Assert.assertFalse(report.contains("Teleport completed"));
        Assert.assertFalse(report.contains("Plan updated"));
    }

    private static List<AssistantOperationsBriefing.Section> healthySections() {
        return Arrays.asList(
            AssistantOperationsBriefing.section("bytes", "AE2 bytes", "Usage: 25.0% (available)"),
            AssistantOperationsBriefing.section(
                "jobs",
                "Crafting jobs",
                "No server-side AE2 crafting calculation is pending."),
            AssistantOperationsBriefing.section("planner", "Planner", "No entries in the planner."));
    }
}
