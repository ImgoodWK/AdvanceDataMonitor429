package com.imgood.textech.webae.console;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.server.MinecraftServer;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.console.AdminConsoleStore.CommandAuditEntry;

/** Executes one bounded WebAE console command at a time on the server thread. */
public final class AdminCommandService {

    public static final int MAX_COMMAND_LENGTH = 512;
    private static final long ACTOR_COOLDOWN_MS = 750L;
    private static final long RESPONSE_WAIT_MS = 8000L;
    private static final int MAX_SERVER_TASK_QUEUE = 128;
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);
    private static final Map<String, Long> LAST_SUBMIT_BY_ACTOR = new HashMap<String, Long>();

    private AdminCommandService() {}

    public static Submission submit(
        String rawCommand,
        boolean confirmed,
        String actorUuid,
        String actorName) throws InterruptedException {
        final String command = normalizeCommand(rawCommand);
        if (isHighRisk(command) && !confirmed) {
            return Submission.rejected("confirmation_required", "This command requires explicit confirmation.");
        }
        if (HandlerTick.getServerTaskQueueDepth() >= MAX_SERVER_TASK_QUEUE) {
            return Submission.rejected("server_busy", "The server task queue is busy. Try again shortly.");
        }
        if (!acquireActorCooldown(actorUuid)) {
            return Submission.rejected("rate_limited", "Commands are limited to one submission every 750 ms.");
        }
        if (!IN_FLIGHT.compareAndSet(false, true)) {
            return Submission.rejected("console_busy", "Another WebAE console command is still running.");
        }

        final CommandAuditEntry queued = AdminConsoleStore.instance().createQueued(command, actorUuid, actorName);
        final CountDownLatch latch = new CountDownLatch(1);
        HandlerTick.enqueueServerTask(new Runnable() {
            @Override
            public void run() {
                long startedAt = System.currentTimeMillis();
                List<String> output = new ArrayList<String>();
                String status = "completed";
                String error = "";
                int affected = 0;
                try {
                    affected = executeOnServerThread(command, actorName, output);
                } catch (Throwable t) {
                    status = "failed";
                    error = safeError(t);
                    output.add(error);
                    AdvanceDataMonitor.LOG.warn(
                        "[WebAE] Admin console command failed: actor={} command={}", actorName, command, t);
                } finally {
                    AdminConsoleStore.instance().complete(
                        queued.id,
                        status,
                        affected,
                        startedAt,
                        System.currentTimeMillis(),
                        output,
                        error);
                    IN_FLIGHT.set(false);
                    latch.countDown();
                }
            }
        });

        boolean completed = latch.await(RESPONSE_WAIT_MS, TimeUnit.MILLISECONDS);
        CommandAuditEntry entry = AdminConsoleStore.instance().historyEntry(queued.id);
        return Submission.accepted(entry, !completed);
    }

    public static String normalizeCommand(String raw) {
        if (raw == null) throw new IllegalArgumentException("Command is required.");
        String command = raw.trim();
        while (command.startsWith("/")) command = command.substring(1).trim();
        if (command.isEmpty()) throw new IllegalArgumentException("Command is required.");
        if (command.length() > MAX_COMMAND_LENGTH) {
            throw new IllegalArgumentException("Command exceeds " + MAX_COMMAND_LENGTH + " characters.");
        }
        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (ch == '\r' || ch == '\n' || ch == 0) {
                throw new IllegalArgumentException("Command must contain exactly one line.");
            }
        }
        return command;
    }

    public static boolean isHighRisk(String command) {
        if (command == null) return false;
        String normalized = command.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) normalized = normalized.substring(1).trim();
        String root = normalized;
        int space = root.indexOf(' ');
        if (space >= 0) root = root.substring(0, space);
        return "stop".equals(root) || "restart".equals(root) || "save-off".equals(root)
            || "op".equals(root) || "deop".equals(root) || "ban".equals(root)
            || "ban-ip".equals(root) || "pardon".equals(root) || "pardon-ip".equals(root)
            || "kick".equals(root) || "kill".equals(root) || "whitelist".equals(root);
    }

    private static synchronized boolean acquireActorCooldown(String actorUuid) {
        String key = actorUuid == null || actorUuid.isEmpty() ? "?" : actorUuid;
        long now = System.currentTimeMillis();
        Long previous = LAST_SUBMIT_BY_ACTOR.get(key);
        if (previous != null && now - previous.longValue() < ACTOR_COOLDOWN_MS) return false;
        LAST_SUBMIT_BY_ACTOR.put(key, Long.valueOf(now));
        if (LAST_SUBMIT_BY_ACTOR.size() > 128) {
            List<String> stale = new ArrayList<String>();
            for (Map.Entry<String, Long> entry : LAST_SUBMIT_BY_ACTOR.entrySet()) {
                if (now - entry.getValue().longValue() > 60000L) stale.add(entry.getKey());
            }
            for (String staleKey : stale) LAST_SUBMIT_BY_ACTOR.remove(staleKey);
        }
        return true;
    }

    private static int executeOnServerThread(String command, final String actorName, final List<String> output)
        throws Exception {
        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) throw new IllegalStateException("Minecraft server is not available.");
        final Class<?> senderType = Class.forName("net.minecraft.command.ICommandSender");
        Object sender = Proxy.newProxyInstance(
            senderType.getClassLoader(),
            new Class<?>[] { senderType },
            new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
                    String name = method.getName();
                    if ("addChatMessage".equals(name) || "func_145747_a".equals(name)) {
                        if (args != null && args.length > 0) addOutput(output, chatText(args[0]));
                        return null;
                    }
                    if ("canCommandSenderUseCommand".equals(name) || "func_70003_b".equals(name)) {
                        return Boolean.TRUE;
                    }
                    if ("getCommandSenderName".equals(name) || "func_70005_c_".equals(name)) {
                        String cleanActor = AdminConsoleStore.safeText(actorName, 32);
                        return cleanActor.isEmpty() ? "WebAE" : "WebAE/" + cleanActor;
                    }
                    if ("getEntityWorld".equals(name) || "func_130014_f_".equals(name)) {
                        return server.worldServerForDimension(0);
                    }
                    if ("getPlayerCoordinates".equals(name) || "func_180425_c".equals(name)) {
                        Class<?> coords = Class.forName("net.minecraft.util.ChunkCoordinates");
                        return coords.getConstructor(int.class, int.class, int.class).newInstance(0, 64, 0);
                    }
                    if (method.getReturnType().getName().equals("net.minecraft.util.IChatComponent")) {
                        Class<?> text = Class.forName("net.minecraft.util.ChatComponentText");
                        return text.getConstructor(String.class).newInstance("WebAE");
                    }
                    return defaultValue(method.getReturnType());
                }
            });

        Object manager = server.getCommandManager();
        Method execute = null;
        for (Method method : manager.getClass().getMethods()) {
            if (("executeCommand".equals(method.getName()) || "func_71556_a".equals(method.getName()))
                && method.getParameterTypes().length == 2) {
                execute = method;
                break;
            }
        }
        if (execute == null) throw new NoSuchMethodException("Command manager executeCommand");
        Object result = execute.invoke(manager, sender, command);
        return result instanceof Number ? ((Number) result).intValue() : 0;
    }

    private static void addOutput(List<String> output, String line) {
        if (line == null) return;
        String clean = line.replaceAll("\\u00a7.", "").replace('\r', ' ').replace('\n', ' ').trim();
        if (clean.isEmpty()) return;
        output.add(clean.length() <= AdminConsoleStore.MAX_OUTPUT_LINE_LENGTH
            ? clean : clean.substring(0, AdminConsoleStore.MAX_OUTPUT_LINE_LENGTH));
        while (output.size() > AdminConsoleStore.MAX_OUTPUT_LINES + 1) output.remove(0);
    }

    private static String chatText(Object component) {
        if (component == null) return "";
        String[] methods = new String[] {
            "getUnformattedTextForChat", "func_150260_c", "getUnformattedText", "func_150261_e",
            "getFormattedText", "func_150254_d"
        };
        for (String methodName : methods) try {
            Object value = component.getClass().getMethod(methodName).invoke(component);
            if (value != null) return String.valueOf(value);
        } catch (Exception ignored) {}
        return String.valueOf(component);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == Boolean.TYPE) return Boolean.FALSE;
        if (type == Character.TYPE) return Character.valueOf('\0');
        if (type == Byte.TYPE) return Byte.valueOf((byte) 0);
        if (type == Short.TYPE) return Short.valueOf((short) 0);
        if (type == Integer.TYPE) return Integer.valueOf(0);
        if (type == Long.TYPE) return Long.valueOf(0L);
        if (type == Float.TYPE) return Float.valueOf(0F);
        if (type == Double.TYPE) return Double.valueOf(0D);
        return null;
    }

    private static String safeError(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage();
        if (message == null || message.trim().isEmpty()) message = root.getClass().getSimpleName();
        return AdminConsoleStore.safeText(message, 500);
    }

    public static final class Submission {
        public boolean accepted;
        public boolean pending;
        public String code;
        public String message;
        public CommandAuditEntry entry;

        static Submission accepted(CommandAuditEntry entry, boolean pending) {
            Submission result = new Submission();
            result.accepted = true;
            result.pending = pending;
            result.entry = entry;
            return result;
        }

        static Submission rejected(String code, String message) {
            Submission result = new Submission();
            result.accepted = false;
            result.code = code;
            result.message = message;
            return result;
        }
    }
}
