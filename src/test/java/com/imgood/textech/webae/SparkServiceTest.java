package com.imgood.textech.webae;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.imgood.textech.webae.api.WebApiRouter;
import com.imgood.textech.webae.api.handler.SparkHandler;
import com.imgood.textech.webae.spark.SparkProfile;
import com.imgood.textech.webae.spark.SparkService;

public class SparkServiceTest {

    @Test
    public void extractsOfficialSparkViewerUrl() throws Exception {
        Assert.assertEquals(
            "https://spark.lucko.me/AbCd_123",
            invoke("findResultUrl", "Profiler results: https://spark.lucko.me/AbCd_123"));
    }

    @Test
    public void extractsSelfHostedActivityLogUrl() throws Exception {
        Assert.assertEquals(
            "https://spark.example.invalid/view/AbCd_123",
            invoke("findUploadUrl", "https://spark.example.invalid/view/AbCd_123"));
    }

    @Test
    public void rejectsViewerBaseUrlWithoutAResult() throws Exception {
        Assert.assertEquals("", invoke("findUploadUrl", "https://spark.lucko.me/"));
    }

    @Test
    public void trimsSentencePunctuationFromSelfHostedViewerUrl() throws Exception {
        Assert.assertEquals(
            "https://spark.example.invalid/view/AbCd_123",
            invoke("findUploadUrl", "Result: https://spark.example.invalid/view/AbCd_123."));
    }

    @Test
    public void allThreadModeUsesSafeIntervalFloorAndExplicitThreadGrouping() throws Exception {
        int interval = ((Integer) invoke(
            "normalizeInterval",
            new Class<?>[] { String.class, int.class },
            "allThreads",
            Integer.valueOf(2))).intValue();
        Assert.assertEquals(10, interval);

        SparkProfile profile = new SparkProfile();
        profile.mode = "allThreads";
        profile.intervalMillis = interval;
        Assert.assertEquals(
            "spark profiler --interval 10 --thread * --not-combined --ignore-sleeping",
            invoke("buildStartCommand", new Class<?>[] { SparkProfile.class }, profile));
    }

    @Test
    public void lagSpikeModeUsesTickFilterAndTimeOrderedViewer() throws Exception {
        SparkProfile profile = new SparkProfile();
        profile.mode = "lagSpikes";
        profile.intervalMillis = 4;
        profile.onlyTicksOverMillis = 75;
        Assert.assertEquals(
            "spark profiler --interval 4 --only-ticks-over 75",
            invoke("buildStartCommand", new Class<?>[] { SparkProfile.class }, profile));
        Assert.assertEquals(
            "spark profiler --stop --order-by-time",
            invoke("buildStopCommand", new Class<?>[] { SparkProfile.class }, profile));
    }

    @Test
    public void stripsRootTokenForDirectSparkPluginInvocation() throws Exception {
        Assert.assertArrayEquals(
            new String[] { "profiler", "--interval", "4" },
            (String[]) invoke("sparkCommandArguments", new Class<?>[] { String.class }, "spark profiler --interval 4"));
    }

    @Test
    public void generatedSyntaxMatchesSpark164ArgumentsParser() throws Exception {
        Class<?> argumentsType = Class.forName("me.lucko.spark.common.command.Arguments");
        Constructor<?> constructor = argumentsType.getConstructor(List.class);
        constructor.newInstance(Arrays.asList("--interval", "4"));
        constructor.newInstance(Arrays.asList("--stop", "--order-by-time"));

        try {
            constructor.newInstance(Arrays.asList("start", "--interval", "4"));
            Assert.fail("Spark 1.6.4 must reject the legacy bare start token");
        } catch (InvocationTargetException expected) {
            Assert.assertTrue(
                expected.getCause()
                    .getClass()
                    .getName()
                    .endsWith("Arguments$ParseException"));
        }
    }

    @Test
    public void recoversProfilerUrlFromSparkActivityFileSchema() throws Exception {
        File file = File.createTempFile("spark-activity", ".json");
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            writer.write(
                "[{\"time\":2000,\"type\":\"Profiler\","
                    + "\"data\":{\"type\":\"url\",\"value\":\"https://spark.lucko.me/File123\"}}]");
            writer.close();
            writer = null;
            Assert.assertEquals(
                "https://spark.lucko.me/File123",
                invoke(
                    "findActivityResultUrlFromFile",
                    new Class<?>[] { File.class, long.class, long.class },
                    file,
                    Long.valueOf(1500L),
                    Long.valueOf(2500L)));
        } finally {
            if (writer != null) writer.close();
            file.delete();
        }
    }

    @Test
    public void statusSummaryKeepsCountsButOmitsFullOutputArrays() throws Exception {
        SparkProfile profile = new SparkProfile();
        profile.messages.add("one");
        profile.messages.add("two");
        profile.baselineMessages.add("baseline");
        profile.hotspots.add(new SparkProfile.Hotspot());
        profile.categories.add(new SparkProfile.CategoryImpact());
        profile.threads.add(new SparkProfile.ThreadImpact());

        Method method = SparkHandler.class.getDeclaredMethod("summary", SparkProfile.class);
        method.setAccessible(true);
        JsonObject summary = (JsonObject) method.invoke(null, profile);

        Assert.assertEquals(
            2,
            summary.get("messageCount")
                .getAsInt());
        Assert.assertEquals(
            1,
            summary.get("baselineMessageCount")
                .getAsInt());
        Assert.assertEquals(
            1,
            summary.get("hotspotCount")
                .getAsInt());
        Assert.assertEquals(
            1,
            summary.get("categoryCount")
                .getAsInt());
        Assert.assertEquals(
            1,
            summary.get("threadCount")
                .getAsInt());
        Assert.assertFalse(summary.has("messages"));
        Assert.assertFalse(summary.has("baselineMessages"));
        Assert.assertFalse(summary.has("completionMessages"));
        Assert.assertFalse(summary.has("hotspots"));
        Assert.assertFalse(summary.has("categories"));
        Assert.assertFalse(summary.has("threads"));
    }

    @Test
    public void classifiesLocalHotspotsIntoActionableCategories() throws Exception {
        Assert.assertEquals(
            "ae2",
            invoke(
                "categoryFor",
                new Class<?>[] { String.class, String.class, String.class },
                "appeng.me.GridNode",
                "updateState",
                "Server thread"));
        Assert.assertEquals(
            "tileEntities",
            invoke(
                "categoryFor",
                new Class<?>[] { String.class, String.class, String.class },
                "net.minecraft.tileentity.TileEntityFurnace",
                "updateEntity",
                "Server thread"));
        Assert.assertEquals(
            "gc",
            invoke(
                "categoryFor",
                new Class<?>[] { String.class, String.class, String.class },
                "unknown",
                "run",
                "GC Thread#0"));
    }

    @Test
    public void normalizesSparkHistoryIdsForHttpPerformanceMetrics() throws Exception {
        Method method = WebApiRouter.class.getDeclaredMethod("normalizeRoute", String.class);
        method.setAccessible(true);
        Assert.assertEquals("/api/spark/history/{id}", method.invoke(null, "/api/spark/history/1723456789-1"));
    }

    @Test
    public void extractsBoundedHotspotsFromStoppedLocalSamplerTree() throws Exception {
        FakeNode entity = new FakeNode("net.minecraft.entity.EntityLiving", "onUpdate", 60D);
        FakeNode ae = new FakeNode("appeng.me.GridNode", "updateState", 100D, entity);
        FakeRoot root = new FakeRoot(100D, ae);
        SparkProfile profile = new SparkProfile();
        profile.intervalMillis = 4;

        invoke(
            "captureLocalAnalysis",
            new Class<?>[] { SparkProfile.class, Object.class },
            profile,
            new FakeSampler(root));

        Assert.assertEquals("ready", profile.analysisStatus);
        Assert.assertEquals(25, profile.sampleCount);
        Assert.assertEquals(2, profile.analyzedNodeCount);
        Assert.assertEquals(2, profile.hotspots.size());
        Assert.assertEquals("net.minecraft.entity.EntityLiving", profile.hotspots.get(0).className);
        Assert.assertEquals(60D, profile.hotspots.get(0).percent, 0.001D);
        Assert.assertEquals("entities", profile.categories.get(0).id);
        Assert.assertEquals("ae2", profile.categories.get(1).id);
    }

    @Test
    public void recognizesSparkCommandLevelProfilerFailures() throws Exception {
        Assert.assertEquals(
            Boolean.TRUE,
            invoke(
                "isProfilerFailureMessage",
                new Class<?>[] { String.class },
                "An active profiler is already running."));
        Assert.assertEquals(
            Boolean.FALSE,
            invoke(
                "isProfilerFailureMessage",
                new Class<?>[] { String.class },
                "The active profiler has completed! Uploading results..."));
    }

    private static String invoke(String name, String value) throws Exception {
        return String.valueOf(invoke(name, new Class<?>[] { String.class }, value));
    }

    private static Object invoke(String name, Class<?>[] parameterTypes, Object... values) throws Exception {
        Method method = SparkService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, values);
    }

    public static final class FakeSampler {

        private final FakeAggregator dataAggregator;

        private FakeSampler(FakeRoot root) {
            this.dataAggregator = new FakeAggregator(root);
        }
    }

    public static final class FakeAggregator {

        private final Map<String, FakeRoot> data = new LinkedHashMap<String, FakeRoot>();

        private FakeAggregator(FakeRoot root) {
            data.put("Server thread", root);
        }

        public Map<String, FakeRoot> getData() {
            return data;
        }
    }

    public static final class FakeRoot {

        private final double totalTime;
        private final List<FakeNode> children = new ArrayList<FakeNode>();

        private FakeRoot(double totalTime, FakeNode... children) {
            this.totalTime = totalTime;
            this.children.addAll(Arrays.asList(children));
        }

        public double getTotalTime() {
            return totalTime;
        }

        public Collection<FakeNode> getChildren() {
            return children;
        }
    }

    public static final class FakeNode {

        private final String className;
        private final String methodName;
        private final double totalTime;
        private final List<FakeNode> children = new ArrayList<FakeNode>();

        private FakeNode(String className, String methodName, double totalTime, FakeNode... children) {
            this.className = className;
            this.methodName = methodName;
            this.totalTime = totalTime;
            this.children.addAll(Arrays.asList(children));
        }

        public double getTotalTime() {
            return totalTime;
        }

        public Collection<FakeNode> getChildren() {
            return children;
        }

        public String getClassName() {
            return className;
        }

        public String getMethodName() {
            return methodName;
        }

        public int getLineNumber() {
            return 42;
        }
    }
}
