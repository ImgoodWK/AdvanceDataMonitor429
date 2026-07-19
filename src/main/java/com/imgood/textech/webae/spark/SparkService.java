package com.imgood.textech.webae.spark;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;

/**
 * Optional Spark bridge for WebAE. It talks to Spark through its runtime Forge
 * plugin and a reflective ICommandSender bridge. This keeps the TeXTech class
 * loader safe when the optional mod is absent while avoiding command-manager
 * ambiguity on integrated and dedicated servers.
 */
public final class SparkService {

    private static final Pattern RESULT_URL = Pattern.compile(
        "https?://(?:www\\.)?spark\\.lucko\\.me(?:/|#)[A-Za-z0-9_-]+(?:\\?[^\\s\\]\\)\\\"']*)?",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern HTTP_URL = Pattern.compile(
        "https?://[^\\s\\]\\)\\\"']+",
        Pattern.CASE_INSENSITIVE);
    private static final int MAX_MESSAGES = 120;
    private static final int MAX_HOTSPOTS = 50;
    private static final int MAX_THREADS = 16;
    private static final int MAX_ANALYSIS_DEPTH = 320;
    private static final int ANALYSIS_VERSION = 1;
    private static final int MIN_INTERVAL_MILLIS = 2;
    private static final int MAX_INTERVAL_MILLIS = 100;
    private static final int MIN_TICK_THRESHOLD_MILLIS = 25;
    private static final int MAX_TICK_THRESHOLD_MILLIS = 1000;
    private static final long RESULT_GRACE_SECONDS = 90L;
    private static final long RESULT_LOOKUP_INTERVAL_SECONDS = 2L;
    private static final String MODE_SERVER = "server";
    private static final String MODE_LAG_SPIKES = "lagSpikes";
    private static final String MODE_ALL_THREADS = "allThreads";
    private static final String OUTPUT_PROFILE = "profile";
    private static final String OUTPUT_BASELINE = "baseline";
    private static final String OUTPUT_COMPLETION = "completion";
    private static final AtomicLong IDS = new AtomicLong();
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(
        new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "WebAE-Spark-Watchdog");
                thread.setDaemon(true);
                return thread;
            }
        });

    private static SparkProfile active;

    private SparkService() {}

    public static boolean isAvailable() {
        try {
            return Loader.isModLoaded("spark");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isEnabled() {
        return Config.webSparkEnabled && isAvailable();
    }

    public static synchronized SparkProfile activeProfile() {
        if (active != null && active.isActive()) return active;
        List<SparkProfile> profiles = SparkProfileStore.instance().all();
        for (SparkProfile profile : profiles) {
            if (profile.isActive()) {
                active = profile;
                return profile;
            }
        }
        return null;
    }

    public static SparkProfile start(int requestedDuration, String initiatedBy) throws Exception {
        return start(requestedDuration, initiatedBy, MODE_SERVER, 0, 0);
    }

    public static SparkProfile start(
        int requestedDuration,
        String initiatedBy,
        String requestedMode,
        int requestedIntervalMillis,
        int requestedTickThresholdMillis) throws Exception {
        int duration = Math.max(5, Math.min(requestedDuration, Config.webSparkMaxDurationSeconds));
        String mode = normalizeMode(requestedMode);
        int intervalMillis = normalizeInterval(mode, requestedIntervalMillis);
        int tickThresholdMillis = MODE_LAG_SPIKES.equals(mode)
            ? clamp(requestedTickThresholdMillis <= 0 ? 50 : requestedTickThresholdMillis,
                MIN_TICK_THRESHOLD_MILLIS, MAX_TICK_THRESHOLD_MILLIS)
            : 0;
        SparkProfile profile = new SparkProfile();
        synchronized (SparkService.class) {
            if (!isEnabled()) {
                throw new IllegalStateException("Spark is not installed or the WebAE Spark feature is disabled");
            }
            if (activeProfile() != null) {
                throw new IllegalStateException("A Spark profiler is already running");
            }
            if (isSparkProfilerActive()) {
                throw new IllegalStateException("Spark profiler is already active outside WebAE");
            }
            profile.id = String.valueOf(System.currentTimeMillis()) + "-" + IDS.incrementAndGet();
            profile.status = "running";
            profile.initiatedBy = initiatedBy == null || initiatedBy.isEmpty() ? "WebAE" : initiatedBy;
            profile.startedAt = System.currentTimeMillis();
            profile.durationSeconds = duration;
            profile.mode = mode;
            profile.intervalMillis = intervalMillis;
            profile.onlyTicksOverMillis = tickThresholdMillis;
            profile.includeAllThreads = MODE_ALL_THREADS.equals(mode);
            profile.analysisStatus = "pending";
            profile.analysisVersion = ANALYSIS_VERSION;
            active = profile;
            SparkProfileStore.instance().upsert(profile);
        }
        try {
            executeAuxiliary("spark tps", profile, OUTPUT_BASELINE);
            // Spark 1.7.10 broadcasts timeout completion to online senders, so
            // a synthetic ICommandSender may never see the Viewer URL. Stop
            // explicitly and recover the authoritative URL from ActivityLog.
            executeOnMainThread(buildStartCommand(profile), profile, OUTPUT_PROFILE);
        } catch (Exception e) {
            fail(profile, e);
            throw e;
        }
        WATCHDOG.schedule(new Runnable() {
            @Override
            public void run() {
                stopAtRequestedDuration(profile);
            }
        }, duration, TimeUnit.SECONDS);
        return profile;
    }

    public static SparkProfile stop() throws Exception {
        SparkProfile profile;
        synchronized (SparkService.class) {
            profile = activeProfile();
            if (profile == null) return null;
            if (!"running".equals(profile.status)) return profile;
            markStopping(profile);
        }
        try {
            Object sampler = activeSparkSampler();
            executeOnMainThread(buildStopCommand(profile), profile, OUTPUT_PROFILE);
            captureLocalAnalysis(profile, sampler);
            executeAuxiliary("spark tps", profile, OUTPUT_COMPLETION);
        } catch (Exception e) {
            fail(profile, e);
            throw e;
        }
        beginResultLookup(profile, "stopped");
        return profile;
    }

    private static void stopAtRequestedDuration(SparkProfile profile) {
        synchronized (SparkService.class) {
            if (active != profile || !"running".equals(profile.status)) return;
            markStopping(profile);
        }
        try {
            Object sampler = activeSparkSampler();
            executeOnMainThread(buildStopCommand(profile), profile, OUTPUT_PROFILE);
            captureLocalAnalysis(profile, sampler);
            executeAuxiliary("spark tps", profile, OUTPUT_COMPLETION);
            beginResultLookup(profile, "finished");
        } catch (Exception e) {
            fail(profile, e);
        }
    }

    private static void markStopping(SparkProfile profile) {
        profile.status = "stopping";
        if (profile.samplingStoppedAt == 0L) profile.samplingStoppedAt = System.currentTimeMillis();
        SparkProfileStore.instance().upsert(profile);
    }

    private static void beginResultLookup(SparkProfile profile, String statusWithoutResult) {
        scheduleViewerResultLookup(profile);
        scheduleFinalization(profile, statusWithoutResult);
    }

    /**
     * Spark's completion output is asynchronous. In 1.6.4 the authoritative
     * uploaded URL is also written to its activity log, so use it as a
     * fallback when the synthetic command sender does not receive the chat
     * component on a particular Forge/SRG runtime.
     */
    private static void scheduleViewerResultLookup(final SparkProfile profile) {
        WATCHDOG.schedule(new Runnable() {
            @Override
            public void run() {
                String resultUrl = findActivityResultUrl(profile);
                if (!resultUrl.isEmpty()) {
                    synchronized (SparkService.class) {
                        completeWithResult(profile, resultUrl);
                    }
                    return;
                }
                synchronized (SparkService.class) {
                    if (!profile.isActive()) return;
                }
                scheduleViewerResultLookup(profile);
            }
        }, RESULT_LOOKUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Re-check Spark's bounded activity log on demand. This is intentionally
     * not performed by idle page polling, so old-history recovery has no
     * recurring game-side cost.
     */
    public static SparkProfile recoverResult(String id) {
        SparkProfile profile = SparkProfileStore.instance().find(id);
        if (profile == null || (profile.resultUrl != null && !profile.resultUrl.isEmpty())) return profile;
        String resultUrl = findActivityResultUrl(profile);
        if (resultUrl.isEmpty()) return profile;
        synchronized (SparkService.class) {
            profile.resultUrl = resultUrl;
            profile.status = "completed";
            if (profile.samplingStoppedAt == 0L) {
                long expectedStop = profile.startedAt + Math.max(0, profile.durationSeconds) * 1000L;
                profile.samplingStoppedAt = profile.completedAt > 0L
                    ? Math.min(expectedStop, profile.completedAt)
                    : expectedStop;
            }
            if (profile.completedAt == 0L) profile.completedAt = System.currentTimeMillis();
            SparkProfileStore.instance().upsert(profile);
        }
        return profile;
    }

    private static void scheduleFinalization(final SparkProfile profile, final String statusWithoutResult) {
        WATCHDOG.schedule(new Runnable() {
            @Override
            public void run() {
                synchronized (SparkService.class) {
                    if (!profile.isActive()) return;
                    profile.status = profile.resultUrl == null || profile.resultUrl.isEmpty()
                        ? statusWithoutResult
                        : "completed";
                    if (profile.samplingStoppedAt == 0L) profile.samplingStoppedAt = System.currentTimeMillis();
                    if (profile.completedAt == 0L) profile.completedAt = System.currentTimeMillis();
                    if (active == profile) active = null;
                    SparkProfileStore.instance().upsert(profile);
                }
            }
        }, RESULT_GRACE_SECONDS, TimeUnit.SECONDS);
    }

    private static void fail(SparkProfile profile, Exception error) {
        synchronized (SparkService.class) {
            profile.status = "failed";
            if (profile.samplingStoppedAt == 0L) profile.samplingStoppedAt = System.currentTimeMillis();
            profile.completedAt = System.currentTimeMillis();
            profile.error = safeMessage(error);
            if (profile.analysisStatus == null || "pending".equals(profile.analysisStatus)) {
                profile.analysisStatus = "unavailable";
            }
            if (active == profile) active = null;
            SparkProfileStore.instance().upsert(profile);
        }
    }

    private static void executeAuxiliary(String command, SparkProfile profile, String outputGroup) {
        try {
            executeOnMainThread(command, profile, outputGroup);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Optional Spark context command failed: {}", command, e);
        }
    }

    private static void executeOnMainThread(
        final String command,
        final SparkProfile profile,
        final String outputGroup) throws Exception {
        final Object server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");
        Callable<Object> task = new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                Object sender = createCommandSender(server, profile, outputGroup);
                Object sparkPlugin = sparkServerPlugin();
                Method direct = findSparkProcessCommand(sparkPlugin, sender);
                if (direct != null) {
                    direct.invoke(sparkPlugin, sender, sparkCommandArguments(command));
                    AdvanceDataMonitor.LOG.info("[WebAE] Dispatched Spark command through server plugin: {}", command);
                    return Integer.valueOf(1);
                }

                // Compatibility fallback for an unknown Spark patch build.
                AdvanceDataMonitor.LOG.warn(
                    "[WebAE] Spark server plugin command bridge unavailable; falling back to command manager");
                Object manager = invokeNoArgs(server, "getCommandManager", "func_71187_D");
                Method execute = null;
                Method[] methods = manager.getClass().getMethods();
                for (Method method : methods) {
                    if (("executeCommand".equals(method.getName()) || "func_71556_a".equals(method.getName()))
                        && method.getParameterTypes().length == 2) {
                        execute = method;
                        break;
                    }
                }
                if (execute == null) throw new NoSuchMethodException("Command manager executeCommand");
                Object result = execute.invoke(manager, sender, command);
                if (result instanceof Number && ((Number) result).intValue() <= 0) {
                    throw new IllegalStateException("Spark command was not accepted: " + command);
                }
                return result;
            }
        };

        Method scheduler = findSingleArgMethod(server.getClass(), "callFromMainThread", "func_152344_a");
        if (scheduler != null) {
            Object futureObject = scheduler.invoke(server, task);
            if (futureObject instanceof Future<?>) {
                ((Future<?>) futureObject).get(15L, TimeUnit.SECONDS);
            }
        } else {
            task.call();
        }
    }

    private static Method findSparkProcessCommand(Object sparkPlugin, Object sender) {
        if (sparkPlugin == null || sender == null) return null;
        for (Method method : sparkPlugin.getClass().getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getReturnType() == Void.TYPE && parameters.length == 2
                && parameters[0].isInstance(sender) && parameters[1] == String[].class) {
                return method;
            }
        }
        return null;
    }

    /** Spark processCommand receives everything after the root `spark` token. */
    private static String[] sparkCommandArguments(String command) {
        if (command == null) return new String[0];
        String[] tokens = command.trim().split("\\s+");
        if (tokens.length <= 1) return new String[0];
        List<String> arguments = new ArrayList<String>();
        for (int i = 1; i < tokens.length; i++) arguments.add(tokens[i]);
        return arguments.toArray(new String[arguments.size()]);
    }

    private static Object createCommandSender(
        final Object server,
        final SparkProfile profile,
        final String outputGroup) throws Exception {
        final Class<?> senderType = Class.forName("net.minecraft.command.ICommandSender");
        return Proxy.newProxyInstance(
            senderType.getClassLoader(),
            new Class<?>[] { senderType },
            new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    String name = method.getName();
                    if (isChatMessageMethod(name)) {
                        if (args != null && args.length > 0) {
                            capture(profile, args[0], outputGroup);
                        }
                        return null;
                    }
                    if ("canCommandSenderUseCommand".equals(name) || "func_70003_b".equals(name)) return Boolean.TRUE;
                    if ("getCommandSenderName".equals(name) || "func_70005_c_".equals(name)) return "WebAE";
                    if ("getEntityWorld".equals(name) || "func_130014_f_".equals(name)) {
                        try {
                            Method world = server.getClass().getMethod("worldServerForDimension", int.class);
                            return world.invoke(server, 0);
                        } catch (Exception ignored) {
                            return null;
                        }
                    }
                    if ("getPlayerCoordinates".equals(name) || "func_180425_c".equals(name)) {
                        try {
                            Class<?> coords = Class.forName("net.minecraft.util.ChunkCoordinates");
                            return coords.getConstructor(int.class, int.class, int.class).newInstance(0, 64, 0);
                        } catch (Exception ignored) {
                            return null;
                        }
                    }
                    return defaultValue(method.getReturnType());
                }
            });
    }

    private static boolean isChatMessageMethod(String name) {
        return "addChatMessage".equals(name) || "func_145747_a".equals(name)
            || name.toLowerCase().contains("message");
    }

    private static void capture(SparkProfile profile, Object component, String outputGroup) {
        String message = chatText(component);
        if (message == null) return;
        String clean = message.replaceAll("\\u00a7.", "");
        String resultUrl = findResultUrl(clean);
        if (resultUrl.isEmpty()) resultUrl = findClickEventUrl(component);
        if (clean.isEmpty() && resultUrl.isEmpty()) return;
        synchronized (SparkService.class) {
            if (!clean.isEmpty()) {
                if (profile.messages == null) profile.messages = new java.util.ArrayList<String>();
                profile.messages.add(clean);
                while (profile.messages.size() > MAX_MESSAGES) profile.messages.remove(0);
                if (OUTPUT_BASELINE.equals(outputGroup)) {
                    if (profile.baselineMessages == null) profile.baselineMessages = new java.util.ArrayList<String>();
                    profile.baselineMessages.add(clean);
                } else if (OUTPUT_COMPLETION.equals(outputGroup)) {
                    if (profile.completionMessages == null) profile.completionMessages = new java.util.ArrayList<String>();
                    profile.completionMessages.add(clean);
                }
            }
            if (OUTPUT_PROFILE.equals(outputGroup) && isProfilerFailureMessage(clean)) {
                profile.status = "failed";
                profile.error = clean;
                if (profile.samplingStoppedAt == 0L) profile.samplingStoppedAt = System.currentTimeMillis();
                profile.completedAt = System.currentTimeMillis();
                if (active == profile) active = null;
            }
            if (!resultUrl.isEmpty()) {
                completeWithResult(profile, resultUrl);
            }
            SparkProfileStore.instance().upsert(profile);
        }
    }

    private static boolean isProfilerFailureMessage(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("active profiler is already running")
            || lower.contains("no active profiler")
            || lower.contains("profiler operation failed unexpectedly");
    }

    private static String chatText(Object component) {
        if (component == null) return "";
        String[] methods = new String[] {
            "getUnformattedTextForChat", "func_150260_c", "getUnformattedText", "func_150261_e", "getFormattedText",
            "func_150254_d"
        };
        for (String methodName : methods) {
            try {
                Object value = component.getClass().getMethod(methodName).invoke(component);
                if (value != null) return String.valueOf(value);
            } catch (Exception ignored) {}
        }
        return String.valueOf(component);
    }

    private static String findResultUrl(String value) {
        if (value == null) return "";
        Matcher matcher = RESULT_URL.matcher(value);
        return matcher.find() ? matcher.group() : "";
    }

    private static String findUploadUrl(String value) {
        if (value == null) return "";
        Matcher matcher = HTTP_URL.matcher(value);
        while (matcher.find()) {
            String candidate = trimUrlPunctuation(matcher.group());
            if (hasResultSuffix(candidate)) return candidate;
        }
        return "";
    }

    private static String trimUrlPunctuation(String value) {
        if (value == null) return "";
        int end = value.length();
        while (end > 0) {
            char character = value.charAt(end - 1);
            if (character != '.' && character != ',' && character != ';' && character != ':' && character != '!') {
                break;
            }
            end--;
        }
        return value.substring(0, end);
    }

    private static boolean hasResultSuffix(String url) {
        int hostStart = url.indexOf("://");
        if (hostStart < 0) return false;
        hostStart += 3;
        int separator = -1;
        for (int i = hostStart; i < url.length(); i++) {
            char character = url.charAt(i);
            if (character == '/' || character == '?' || character == '#') {
                separator = i;
                break;
            }
        }
        return separator >= 0 && separator < url.length() - 1;
    }

    private static String findClickEventUrl(Object component) {
        if (component == null) return "";
        Object style = invokeOptionalNoArgs(component, "getChatStyle", "func_150256_b");
        Object clickEvent = style == null ? null : invokeOptionalNoArgs(style, "getChatClickEvent", "func_150235_h");
        Object value = clickEvent == null ? null : invokeOptionalNoArgs(clickEvent, "getValue", "func_150669_a");
        String result = findResultUrl(value == null ? "" : String.valueOf(value));
        if (result.isEmpty()) result = findUploadUrl(value == null ? "" : String.valueOf(value));
        if (!result.isEmpty()) return result;
        Object siblings = invokeOptionalNoArgs(component, "getSiblings", "func_150253_a");
        if (siblings instanceof Iterable<?>) {
            for (Object sibling : (Iterable<?>) siblings) {
                result = findClickEventUrl(sibling);
                if (!result.isEmpty()) return result;
            }
        }
        return "";
    }

    private static String findActivityResultUrl(SparkProfile profile) {
        long[] window = activityWindow(profile);
        try {
            Object platform = sparkPlatform();
            Object activityLog = invokeOptionalNoArgs(platform, "getActivityLog");
            Object entries = invokeOptionalNoArgs(activityLog, "getLog");
            if (entries instanceof List<?>) {
                String result = findActivityResultUrl((List<?>) entries, window[0], window[1]);
                if (!result.isEmpty()) return result;
            }
        } catch (Throwable error) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Unable to read Spark ActivityLog in memory", error);
        }
        return findActivityResultUrlFromFile(sparkActivityFile(), window[0], window[1]);
    }

    private static String findActivityResultUrl(List<?> activities, long lowerBound, long upperBound) {
        // ActivityLog stores newest first. Walk backwards so the first
        // matching upload in this run's completion window wins.
        for (int i = activities.size() - 1; i >= 0; i--) {
            Object activity = activities.get(i);
            Object time = invokeOptionalNoArgs(activity, "getTime");
            if (!(time instanceof Number)) continue;
            long activityTime = ((Number) time).longValue();
            if (activityTime < lowerBound || activityTime > upperBound) continue;
            Object type = invokeOptionalNoArgs(activity, "getType");
            Object dataType = invokeOptionalNoArgs(activity, "getDataType");
            if (!"Profiler".equals(String.valueOf(type)) || !"url".equalsIgnoreCase(String.valueOf(dataType))) {
                continue;
            }
            Object dataValue = invokeOptionalNoArgs(activity, "getDataValue");
            String resultUrl = findUploadUrl(dataValue == null ? "" : String.valueOf(dataValue));
            if (!resultUrl.isEmpty()) return resultUrl;
        }
        return "";
    }

    private static String findActivityResultUrlFromFile(File file, long lowerBound, long upperBound) {
        if (file == null || !file.isFile()) return "";
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonArray()) return "";
            JsonArray activities = root.getAsJsonArray();
            for (int i = activities.size() - 1; i >= 0; i--) {
                JsonObject activity = activities.get(i).getAsJsonObject();
                long activityTime = activity.has("time") ? activity.get("time").getAsLong() : 0L;
                if (activityTime < lowerBound || activityTime > upperBound) continue;
                if (!activity.has("type") || !"Profiler".equals(activity.get("type").getAsString())) continue;
                JsonObject data = activity.has("data") && activity.get("data").isJsonObject()
                    ? activity.getAsJsonObject("data")
                    : null;
                if (data == null || !data.has("type") || !"url".equalsIgnoreCase(data.get("type").getAsString())) {
                    continue;
                }
                String resultUrl = findUploadUrl(data.has("value") ? data.get("value").getAsString() : "");
                if (!resultUrl.isEmpty()) return resultUrl;
            }
        } catch (Throwable error) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Unable to read Spark activity file {}", file, error);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {}
            }
        }
        return "";
    }

    private static long[] activityWindow(SparkProfile profile) {
        long lowerBound = profile.samplingStoppedAt > 0L
            ? profile.samplingStoppedAt - 5000L
            : profile.startedAt;
        long upperBound = profile.completedAt > 0L
            ? profile.completedAt + TimeUnit.MINUTES.toMillis(5L)
            : System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5L);
        // Prevent a missing old run from adopting a later run's URL.
        for (SparkProfile candidate : SparkProfileStore.instance().all()) {
            if (candidate == profile || candidate.startedAt <= profile.startedAt) continue;
            upperBound = Math.min(upperBound, candidate.startedAt - 1L);
        }
        return new long[] { lowerBound, upperBound };
    }

    private static File sparkActivityFile() {
        Object configDirectory = readField(sparkMod(), "configDirectory");
        if (configDirectory instanceof Path) {
            return ((Path) configDirectory).resolve("activity.json").toFile();
        }
        return new File(new File("config"), "activity.json");
    }

    /**
     * Build a bounded, viewer-independent summary from Spark's stopped local
     * sampler tree. The optional integration stays reflective so loading
     * TeXTech without Spark never resolves Spark classes.
     */
    private static void captureLocalAnalysis(SparkProfile profile, Object sampler) {
        if (profile == null) return;
        if (sampler == null) {
            finishAnalysis(profile, "unavailable", null);
            return;
        }
        try {
            Object aggregator = readField(sampler, "dataAggregator");
            Object rawData = invokeNoArgs(aggregator, "getData");
            if (!(rawData instanceof Map<?, ?>)) {
                finishAnalysis(profile, "unavailable", null);
                return;
            }

            AnalysisAccumulator accumulator = new AnalysisAccumulator();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawData).entrySet()) {
                Object root = entry.getValue();
                if (root == null) continue;
                String threadName = entry.getKey() == null ? "unknown" : String.valueOf(entry.getKey());
                Object rawThreadTime = invokeNoArgs(root, "getTotalTime");
                double threadTime = rawThreadTime instanceof Number ? ((Number) rawThreadTime).doubleValue() : 0D;
                if (threadTime <= 0D) continue;
                accumulator.totalTimeMillis += threadTime;
                SparkProfile.ThreadImpact thread = new SparkProfile.ThreadImpact();
                thread.name = threadName;
                thread.timeMillis = threadTime;
                accumulator.threads.add(thread);
                Object rawChildren = invokeNoArgs(root, "getChildren");
                Collection<?> rootChildren = rawChildren instanceof Collection<?>
                    ? (Collection<?>) rawChildren
                    : Collections.emptyList();
                if (rootChildren.isEmpty()) continue;
                AnalysisNodeAccess access = new AnalysisNodeAccess(rootChildren.iterator().next().getClass());
                for (Object child : rootChildren) {
                    accumulateNode(child, threadName, 0, access, accumulator);
                }
            }
            accumulator.finish(profile.intervalMillis);
            finishAnalysis(profile, accumulator.hotspots.isEmpty() ? "empty" : "ready", accumulator);
        } catch (Throwable error) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Unable to build local Spark analysis for run {}", profile.id, error);
            finishAnalysis(profile, "unavailable", null);
        }
    }

    private static void accumulateNode(
        Object node,
        String threadName,
        int depth,
        AnalysisNodeAccess access,
        AnalysisAccumulator accumulator) throws Exception {
        if (node == null || depth >= MAX_ANALYSIS_DEPTH) return;
        accumulator.nodeCount++;
        double totalTime = access.totalTime(node);
        Collection<?> children = access.children(node);
        double childTime = 0D;
        for (Object child : children) {
            childTime += Math.max(0D, access.totalTime(child));
            accumulateNode(child, threadName, depth + 1, access, accumulator);
        }
        double selfTime = Math.max(0D, totalTime - childTime);
        String className = access.className(node);
        String methodName = access.methodName(node);
        if (className.isEmpty() && methodName.isEmpty()) return;

        String key = className + '\n' + methodName;
        MethodImpact method = accumulator.methods.get(key);
        if (method == null) {
            method = new MethodImpact();
            method.className = className;
            method.methodName = methodName;
            method.lineNumber = access.lineNumber(node);
            method.category = categoryFor(className, methodName, threadName);
            accumulator.methods.put(key, method);
        }
        method.totalTimeMillis += totalTime;
        method.selfTimeMillis += selfTime;
        if (threadName.equals(method.dominantThread)) {
            method.dominantThreadTime += selfTime;
        } else if (selfTime > method.dominantThreadTime) {
            method.dominantThread = threadName;
            method.dominantThreadTime = selfTime;
        }

        if (selfTime > 0D) {
            CategoryImpact category = accumulator.categories.get(method.category);
            if (category == null) {
                category = new CategoryImpact();
                category.id = method.category;
                accumulator.categories.put(category.id, category);
            }
            category.timeMillis += selfTime;
            if (selfTime > category.topSelfTime) {
                category.topSelfTime = selfTime;
                category.topClassName = className;
                category.topMethodName = methodName;
            }
        }
    }

    private static void finishAnalysis(
        SparkProfile profile,
        String status,
        AnalysisAccumulator accumulator) {
        synchronized (SparkService.class) {
            profile.analysisVersion = ANALYSIS_VERSION;
            profile.analysisStatus = status;
            profile.hotspots = accumulator == null
                ? new ArrayList<SparkProfile.Hotspot>()
                : accumulator.hotspots;
            profile.categories = accumulator == null
                ? new ArrayList<SparkProfile.CategoryImpact>()
                : accumulator.categoryResults;
            profile.threads = accumulator == null
                ? new ArrayList<SparkProfile.ThreadImpact>()
                : accumulator.threads;
            profile.sampledTimeMillis = accumulator == null ? 0D : accumulator.totalTimeMillis;
            profile.sampleCount = accumulator == null ? 0 : accumulator.sampleCount;
            profile.analyzedNodeCount = accumulator == null ? 0 : accumulator.nodeCount;
            SparkProfileStore.instance().upsert(profile);
        }
    }

    private static String categoryFor(String className, String methodName, String threadName) {
        String classLower = className == null ? "" : className.toLowerCase(java.util.Locale.ROOT);
        String methodLower = methodName == null ? "" : methodName.toLowerCase(java.util.Locale.ROOT);
        String threadLower = threadName == null ? "" : threadName.toLowerCase(java.util.Locale.ROOT);
        String combined = classLower + "." + methodLower;
        if (threadLower.contains("gc thread") || threadLower.contains("garbage collector")
            || combined.contains("garbagecollect") || combined.contains("gc.collector")) return "gc";
        if (classLower.startsWith("appeng.")) return "ae2";
        if (classLower.startsWith("gregtech.") || classLower.startsWith("gtplusplus.")
            || classLower.startsWith("goodgenerator.") || classLower.startsWith("bartworks.")) return "gregtech";
        if (classLower.startsWith("com.imgood.textech.") || classLower.startsWith("tectech.")) return "textech";
        if (combined.contains("world.gen") || combined.contains("chunkprovidergenerate")
            || combined.contains("populatechunk") || combined.contains("worldgenerator")) return "worldgen";
        if (combined.contains("tileentity")) return "tileEntities";
        if (methodLower.contains("updateentities") || "func_72939_s".equals(methodLower)
            || classLower.startsWith("net.minecraft.entity.")) return "entities";
        if (combined.contains("chunk") || combined.contains("regionfile")
            || combined.contains("anvil")) return "chunks";
        if (classLower.startsWith("net.minecraft.network.") || classLower.startsWith("io.netty.")) return "network";
        if (combined.contains("saveall") || classLower.startsWith("java.io.")
            || classLower.startsWith("java.nio.") || combined.contains("compressedstreamtools")) return "io";
        if (classLower.startsWith("net.minecraftforge.") || classLower.startsWith("cpw.mods.fml.")) return "forge";
        if (classLower.startsWith("net.minecraft.")) return "minecraft";
        if (classLower.startsWith("java.") || classLower.startsWith("javax.")
            || classLower.startsWith("sun.") || classLower.startsWith("com.sun.")) return "jvm";
        if (classLower.startsWith("com.") || classLower.startsWith("org.")
            || classLower.startsWith("mods.")) return "otherMods";
        return "other";
    }

    private static final class AnalysisNodeAccess {

        private final Method totalTime;
        private final Method children;
        private final Method className;
        private final Method methodName;
        private final Method lineNumber;

        private AnalysisNodeAccess(Class<?> nodeType) throws Exception {
            this.totalTime = nodeType.getMethod("getTotalTime");
            this.children = nodeType.getMethod("getChildren");
            this.className = nodeType.getMethod("getClassName");
            this.methodName = nodeType.getMethod("getMethodName");
            this.lineNumber = nodeType.getMethod("getLineNumber");
        }

        private double totalTime(Object node) throws Exception {
            Object value = totalTime.invoke(node);
            return value instanceof Number ? ((Number) value).doubleValue() : 0D;
        }

        private Collection<?> children(Object node) throws Exception {
            Object value = children.invoke(node);
            return value instanceof Collection<?> ? (Collection<?>) value : Collections.emptyList();
        }

        private String className(Object node) throws Exception {
            Object value = className.invoke(node);
            return value == null ? "" : String.valueOf(value);
        }

        private String methodName(Object node) throws Exception {
            Object value = methodName.invoke(node);
            return value == null ? "" : String.valueOf(value);
        }

        private int lineNumber(Object node) throws Exception {
            Object value = lineNumber.invoke(node);
            return value instanceof Number ? ((Number) value).intValue() : -1;
        }
    }

    private static final class MethodImpact {

        private String className;
        private String methodName;
        private int lineNumber;
        private String category;
        private String dominantThread;
        private double dominantThreadTime;
        private double selfTimeMillis;
        private double totalTimeMillis;
    }

    private static final class CategoryImpact {

        private String id;
        private double timeMillis;
        private double topSelfTime;
        private String topClassName;
        private String topMethodName;
    }

    private static final class AnalysisAccumulator {

        private final Map<String, MethodImpact> methods = new HashMap<String, MethodImpact>();
        private final Map<String, CategoryImpact> categories = new HashMap<String, CategoryImpact>();
        private final List<SparkProfile.Hotspot> hotspots = new ArrayList<SparkProfile.Hotspot>();
        private final List<SparkProfile.CategoryImpact> categoryResults =
            new ArrayList<SparkProfile.CategoryImpact>();
        private final List<SparkProfile.ThreadImpact> threads = new ArrayList<SparkProfile.ThreadImpact>();
        private double totalTimeMillis;
        private int sampleCount;
        private int nodeCount;

        private void finish(int intervalMillis) {
            final double denominator = Math.max(0.001D, totalTimeMillis);
            List<MethodImpact> methodResults = new ArrayList<MethodImpact>(methods.values());
            Collections.sort(methodResults, new Comparator<MethodImpact>() {
                @Override
                public int compare(MethodImpact left, MethodImpact right) {
                    return Double.compare(right.selfTimeMillis, left.selfTimeMillis);
                }
            });
            int hotspotLimit = Math.min(MAX_HOTSPOTS, methodResults.size());
            for (int i = 0; i < hotspotLimit; i++) {
                MethodImpact source = methodResults.get(i);
                if (source.selfTimeMillis <= 0D) continue;
                SparkProfile.Hotspot hotspot = new SparkProfile.Hotspot();
                hotspot.className = source.className;
                hotspot.methodName = source.methodName;
                hotspot.lineNumber = source.lineNumber;
                hotspot.category = source.category;
                hotspot.dominantThread = source.dominantThread;
                hotspot.selfTimeMillis = source.selfTimeMillis;
                hotspot.totalTimeMillis = source.totalTimeMillis;
                hotspot.percent = source.selfTimeMillis * 100D / denominator;
                hotspots.add(hotspot);
            }

            List<CategoryImpact> categoryValues = new ArrayList<CategoryImpact>(categories.values());
            Collections.sort(categoryValues, new Comparator<CategoryImpact>() {
                @Override
                public int compare(CategoryImpact left, CategoryImpact right) {
                    return Double.compare(right.timeMillis, left.timeMillis);
                }
            });
            for (CategoryImpact source : categoryValues) {
                SparkProfile.CategoryImpact result = new SparkProfile.CategoryImpact();
                result.id = source.id;
                result.timeMillis = source.timeMillis;
                result.percent = source.timeMillis * 100D / denominator;
                result.topClassName = source.topClassName;
                result.topMethodName = source.topMethodName;
                categoryResults.add(result);
            }

            Collections.sort(threads, new Comparator<SparkProfile.ThreadImpact>() {
                @Override
                public int compare(SparkProfile.ThreadImpact left, SparkProfile.ThreadImpact right) {
                    return Double.compare(right.timeMillis, left.timeMillis);
                }
            });
            while (threads.size() > MAX_THREADS) threads.remove(threads.size() - 1);
            for (SparkProfile.ThreadImpact thread : threads) {
                thread.percent = thread.timeMillis * 100D / denominator;
            }
            sampleCount = (int) Math.min(
                Integer.MAX_VALUE,
                Math.round(totalTimeMillis / Math.max(1, intervalMillis)));
        }
    }

    /** Avoid taking over a profiler that an operator started in-game. */
    private static boolean isSparkProfilerActive() {
        return activeSparkSampler() != null;
    }

    private static Object activeSparkSampler() {
        try {
            Object modules = readField(sparkPlatform(), "commandModules");
            if (!(modules instanceof Iterable<?>)) return null;
            for (Object module : (Iterable<?>) modules) {
                if (module == null || !module.getClass().getName().endsWith(".SamplerModule")) continue;
                return readField(module, "activeSampler");
            }
        } catch (Throwable ignored) {
            // Optional internals differ across Spark builds; command-level
            // busy handling remains the final guard when introspection fails.
        }
        return null;
    }

    private static Object sparkPlatform() {
        return readField(sparkServerPlugin(), "platform");
    }

    private static Object sparkServerPlugin() {
        return readField(sparkMod(), "activeServerPlugin");
    }

    private static Object sparkMod() {
        Object container = Loader.instance().getIndexedModList().get("spark");
        return invokeOptionalNoArgs(container, "getMod");
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) return null;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static void completeWithResult(SparkProfile profile, String resultUrl) {
        profile.resultUrl = resultUrl;
        profile.status = "completed";
        if (profile.samplingStoppedAt == 0L) profile.samplingStoppedAt = System.currentTimeMillis();
        profile.completedAt = System.currentTimeMillis();
        if (active == profile) active = null;
        SparkProfileStore.instance().upsert(profile);
        AdvanceDataMonitor.LOG.info("[WebAE] Spark Viewer URL captured for run {}", profile.id);
    }

    private static String normalizeMode(String requestedMode) {
        if (MODE_LAG_SPIKES.equals(requestedMode)) return MODE_LAG_SPIKES;
        if (MODE_ALL_THREADS.equals(requestedMode)) return MODE_ALL_THREADS;
        return MODE_SERVER;
    }

    private static int normalizeInterval(String mode, int requestedIntervalMillis) {
        int defaultInterval = MODE_ALL_THREADS.equals(mode) ? 10 : 4;
        int interval = requestedIntervalMillis <= 0 ? defaultInterval : requestedIntervalMillis;
        interval = clamp(interval, MIN_INTERVAL_MILLIS, MAX_INTERVAL_MILLIS);
        // Sampling every 2-4 ms across every JVM thread is disproportionately
        // expensive. Keep the advanced all-thread mode at a safe floor.
        return MODE_ALL_THREADS.equals(mode) ? Math.max(10, interval) : interval;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String buildStartCommand(SparkProfile profile) {
        // Spark 1.6.4 has no `start` subcommand. Its Arguments parser requires
        // every token after `profiler` to begin with `--`; no control flag means start.
        StringBuilder command = new StringBuilder("spark profiler --interval ")
            .append(profile.intervalMillis);
        if (MODE_LAG_SPIKES.equals(profile.mode)) {
            command.append(" --only-ticks-over ").append(profile.onlyTicksOverMillis);
        } else if (MODE_ALL_THREADS.equals(profile.mode)) {
            command.append(" --thread * --not-combined --ignore-sleeping");
        }
        return command.toString();
    }

    private static String buildStopCommand(SparkProfile profile) {
        return MODE_LAG_SPIKES.equals(profile.mode)
            ? "spark profiler --stop --order-by-time"
            : "spark profiler --stop";
    }

    private static Object invokeNoArgs(Object target, String... names) throws Exception {
        Exception failure = null;
        for (String name : names) {
            try {
                return target.getClass().getMethod(name).invoke(target);
            } catch (Exception e) {
                failure = e;
            }
        }
        throw failure == null ? new NoSuchMethodException() : failure;
    }

    private static Object invokeOptionalNoArgs(Object target, String... names) {
        try {
            return invokeNoArgs(target, names);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Method findSingleArgMethod(Class<?> type, String... names) {
        for (Method method : type.getMethods()) {
            for (String name : names) {
                if (name.equals(method.getName()) && method.getParameterTypes().length == 1) return method;
            }
        }
        return null;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return Boolean.FALSE;
        if (type == byte.class) return Byte.valueOf((byte) 0);
        if (type == short.class) return Short.valueOf((short) 0);
        if (type == int.class) return Integer.valueOf(0);
        if (type == long.class) return Long.valueOf(0L);
        if (type == float.class) return Float.valueOf(0F);
        if (type == double.class) return Double.valueOf(0D);
        if (type == char.class) return Character.valueOf('\0');
        return null;
    }

    private static String safeMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
