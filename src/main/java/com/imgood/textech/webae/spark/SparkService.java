package com.imgood.textech.webae.spark;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;

/**
 * Optional Spark bridge for WebAE. It deliberately talks to Spark through the
 * registered Forge command and a reflective ICommandSender bridge. This keeps
 * the TeXTech class loader safe when the optional Spark mod is absent and also
 * tolerates Spark patch releases that change internal API classes.
 */
public final class SparkService {

    private static final Pattern RESULT_URL = Pattern.compile("https?://spark\\.lucko\\.me/[A-Za-z0-9_-]+");
    private static final int MAX_MESSAGES = 120;
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

    public static synchronized SparkProfile start(int requestedDuration, String initiatedBy) throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException("Spark is not installed or the WebAE Spark feature is disabled");
        }
        if (activeProfile() != null) {
            throw new IllegalStateException("A Spark profiler is already running");
        }
        int duration = Math.max(5, Math.min(requestedDuration, Config.webSparkMaxDurationSeconds));
        SparkProfile profile = new SparkProfile();
        profile.id = String.valueOf(System.currentTimeMillis()) + "-" + IDS.incrementAndGet();
        profile.status = "running";
        profile.initiatedBy = initiatedBy == null || initiatedBy.isEmpty() ? "WebAE" : initiatedBy;
        profile.startedAt = System.currentTimeMillis();
        profile.durationSeconds = duration;
        active = profile;
        SparkProfileStore.instance().upsert(profile);
        try {
            executeOnMainThread("spark profiler start --timeout " + duration, profile);
        } catch (Exception e) {
            profile.status = "failed";
            profile.completedAt = System.currentTimeMillis();
            profile.error = safeMessage(e);
            SparkProfileStore.instance().upsert(profile);
            active = null;
            throw e;
        }
        WATCHDOG.schedule(new Runnable() {
            @Override
            public void run() {
                synchronized (SparkService.class) {
                    if (profile.isActive()) {
                        profile.status = profile.resultUrl == null || profile.resultUrl.isEmpty() ? "finished" : "completed";
                        profile.completedAt = System.currentTimeMillis();
                        SparkProfileStore.instance().upsert(profile);
                        active = null;
                    }
                }
            }
        }, duration + 90L, TimeUnit.SECONDS);
        return profile;
    }

    public static synchronized SparkProfile stop() throws Exception {
        SparkProfile profile = activeProfile();
        if (profile == null) return null;
        profile.status = "stopping";
        SparkProfileStore.instance().upsert(profile);
        try {
            executeOnMainThread("spark profiler stop", profile);
        } catch (Exception e) {
            profile.status = "failed";
            profile.completedAt = System.currentTimeMillis();
            profile.error = safeMessage(e);
            SparkProfileStore.instance().upsert(profile);
            active = null;
            throw e;
        }
        WATCHDOG.schedule(new Runnable() {
            @Override
            public void run() {
                synchronized (SparkService.class) {
                    if (profile.isActive()) {
                        profile.status = profile.resultUrl == null || profile.resultUrl.isEmpty() ? "stopped" : "completed";
                        profile.completedAt = System.currentTimeMillis();
                        SparkProfileStore.instance().upsert(profile);
                        active = null;
                    }
                }
            }
        }, 15L, TimeUnit.SECONDS);
        return profile;
    }

    private static void executeOnMainThread(final String command, final SparkProfile profile) throws Exception {
        final Object server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) throw new IllegalStateException("Minecraft server is not available");
        Callable<Object> task = new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                Object manager = invokeNoArgs(server, "getCommandManager");
                Object sender = createCommandSender(server, profile);
                Method execute = null;
                Method[] methods = manager.getClass().getMethods();
                for (Method method : methods) {
                    if ("executeCommand".equals(method.getName()) && method.getParameterTypes().length == 2) {
                        execute = method;
                        break;
                    }
                }
                if (execute == null) throw new NoSuchMethodException("Command manager executeCommand");
                return execute.invoke(manager, sender, command);
            }
        };

        Method scheduler = findSingleArgMethod(server.getClass(), "callFromMainThread");
        if (scheduler != null) {
            Object futureObject = scheduler.invoke(server, task);
            if (futureObject instanceof Future<?>) {
                ((Future<?>) futureObject).get(15L, TimeUnit.SECONDS);
            }
        } else {
            task.call();
        }
    }

    private static Object createCommandSender(final Object server, final SparkProfile profile) throws Exception {
        final Class<?> senderType = Class.forName("net.minecraft.command.ICommandSender");
        return Proxy.newProxyInstance(
            senderType.getClassLoader(),
            new Class<?>[] { senderType },
            new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    String name = method.getName();
                    if ("addChatMessage".equals(name) || name.toLowerCase().contains("message")) {
                        if (args != null && args.length > 0) {
                            capture(profile, chatText(args[0]));
                        }
                        return null;
                    }
                    if ("canCommandSenderUseCommand".equals(name)) return Boolean.TRUE;
                    if ("getCommandSenderName".equals(name)) return "WebAE";
                    if ("getEntityWorld".equals(name)) {
                        try {
                            Method world = server.getClass().getMethod("worldServerForDimension", int.class);
                            return world.invoke(server, 0);
                        } catch (Exception ignored) {
                            return null;
                        }
                    }
                    if ("getPlayerCoordinates".equals(name)) {
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

    private static void capture(SparkProfile profile, String message) {
        if (message == null || message.isEmpty()) return;
        String clean = message.replaceAll("\\u00a7.", "");
        synchronized (SparkService.class) {
            if (profile.messages == null) profile.messages = new java.util.ArrayList<String>();
            profile.messages.add(clean);
            while (profile.messages.size() > MAX_MESSAGES) profile.messages.remove(0);
            Matcher matcher = RESULT_URL.matcher(clean);
            if (matcher.find()) {
                profile.resultUrl = matcher.group();
                profile.status = "completed";
                profile.completedAt = System.currentTimeMillis();
                active = null;
            }
            SparkProfileStore.instance().upsert(profile);
        }
    }

    private static String chatText(Object component) {
        if (component == null) return "";
        String[] methods = new String[] { "getUnformattedTextForChat", "getUnformattedText", "getFormattedText" };
        for (String methodName : methods) {
            try {
                Object value = component.getClass().getMethod(methodName).invoke(component);
                if (value != null) return String.valueOf(value);
            } catch (Exception ignored) {}
        }
        return String.valueOf(component);
    }

    private static Object invokeNoArgs(Object target, String name) throws Exception {
        return target.getClass().getMethod(name).invoke(target);
    }

    private static Method findSingleArgMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (name.equals(method.getName()) && method.getParameterTypes().length == 1) return method;
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
