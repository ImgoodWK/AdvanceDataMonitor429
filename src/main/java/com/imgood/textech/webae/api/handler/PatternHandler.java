package com.imgood.textech.webae.api.handler;

import java.io.DataInputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.dto.PatternDto;
import com.imgood.textech.webae.dto.PatternDto.InterfaceDto;
import com.imgood.textech.webae.dto.PatternDto.PatternInjectRequest;
import com.imgood.textech.webae.dto.PatternDto.PatternInjectResult;
import com.imgood.textech.webae.pattern.BlankPatternHelper;
import com.imgood.textech.webae.pattern.InterfaceLocator;
import com.imgood.textech.webae.pattern.PatternBrowseService;
import com.imgood.textech.webae.pattern.PatternEncoder;
import com.imgood.textech.webae.pattern.PatternInjector;

import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for pattern encoding, injection, and interface enumeration.
 *
 * GET /api/interfaces?network=<id> — list all ME interfaces
 * POST /api/pattern/encode — encode a PatternDto to NBT JSON
 * POST /api/pattern/inject — inject an encoded pattern into an interface
 */
public class PatternHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final long SERVER_TIMEOUT_MS = 10_000L;

    /**
     * Handle pattern-related API requests. Accepts the full session for POST body reading.
     */
    public static NanoHTTPD.Response handle(String uri, NanoHTTPD.IHTTPSession session, String playerUuid) {
        Map<String, String> params = session.getParms();

        if ("/api/interfaces".equals(uri)) {
            return handleInterfaces(params, playerUuid);
        }
        if ("/api/pattern/encode".equals(uri)) {
            return handleEncode(session, playerUuid);
        }
        if ("/api/pattern/inject".equals(uri)) {
            return handleInject(session, playerUuid);
        }

        return jsonResponse(
            NanoHTTPD.Response.Status.NOT_FOUND,
            "{\"success\":false,\"message\":\"Unknown pattern endpoint\"}");
    }

    private static NanoHTTPD.Response handleInterfaces(Map<String, String> params, String playerUuid) {
        String networkStr = params.get("network");
        if (networkStr == null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing 'network' parameter\"}");
        }
        int networkId;
        try {
            networkId = Integer.parseInt(networkStr);
        } catch (NumberFormatException e) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid 'network' parameter\"}");
        }

        List<InterfaceDto> interfaces = InterfaceLocator.locateBlocking(playerUuid, networkId);
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"interfaces\":" + GSON.toJson(interfaces) + ",\"count\":" + interfaces.size() + "}");
    }

    private static NanoHTTPD.Response handleEncode(NanoHTTPD.IHTTPSession session, String playerUuid) {
        String body = readBody(session);
        if (body == null || body.isEmpty()) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Empty request body\"}");
        }

        try {
            JsonObject root = new JsonParser().parse(body)
                .getAsJsonObject();
            PatternDto pattern = GSON.fromJson(body, PatternDto.class);
            int networkId = -1;
            boolean consumeBlank = false;
            if (root.has("networkId") && !root.get("networkId")
                .isJsonNull()) {
                networkId = root.get("networkId")
                    .getAsInt();
            }
            if (root.has("consumeBlank") && !root.get("consumeBlank")
                .isJsonNull()) {
                consumeBlank = root.get("consumeBlank")
                    .getAsBoolean();
            } else if (networkId >= 0) {
                consumeBlank = true;
            }

            if (consumeBlank && networkId >= 0) {
                final int netId = networkId;
                final boolean[] consumed = new boolean[1];
                final CountDownLatch latch = new CountDownLatch(1);
                HandlerTick.enqueueServerTask(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            consumed[0] = BlankPatternHelper.consumeOne(playerUuid, netId);
                        } finally {
                            latch.countDown();
                        }
                    }
                });
                try {
                    if (!latch.await(SERVER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                        return jsonResponse(
                            NanoHTTPD.Response.Status.INTERNAL_ERROR,
                            "{\"success\":false,\"message\":\"Blank pattern check timed out\"}");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                    return jsonResponse(
                        NanoHTTPD.Response.Status.INTERNAL_ERROR,
                        "{\"success\":false,\"message\":\"Interrupted\"}");
                }
                if (!consumed[0]) {
                    return jsonResponse(
                        NanoHTTPD.Response.Status.BAD_REQUEST,
                        "{\"success\":false,\"code\":\"NO_BLANK_PATTERN\",\"message\":\"空白样板不足\"}");
                }
            }

            String encodedNbt = PatternEncoder.encode(pattern);
            pattern.encodedNbt = encodedNbt;
            return jsonResponse(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"data\":" + GSON.toJson(pattern) + "}");
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Pattern encode failed", e);
            return jsonResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"Encode failed: " + e.getMessage() + "\"}");
        }
    }

    private static NanoHTTPD.Response handleInject(NanoHTTPD.IHTTPSession session, String playerUuid) {
        String body = readBody(session);
        if (body == null || body.isEmpty()) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Empty request body\"}");
        }

        try {
            PatternInjectRequest request = GSON.fromJson(body, PatternInjectRequest.class);
            PatternInjectResult result = PatternInjector.injectBlocking(playerUuid, request);
            if (result != null && result.success) {
                PatternBrowseService.invalidateAll();
            }
            return jsonResponse(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"result\":" + GSON.toJson(result) + "}");
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Pattern inject failed", e);
            return jsonResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"Inject failed: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Read POST body from the session using the same approach as WebApiRouter.
     */
    private static String readBody(NanoHTTPD.IHTTPSession session) {
        try {
            int contentLength = 0;
            String cl = session.getHeaders()
                .get("content-length");
            if (cl != null) {
                contentLength = Integer.parseInt(cl.trim());
            }
            if (contentLength <= 0) return "";
            byte[] buffer = new byte[contentLength];
            DataInputStream dis = new DataInputStream(session.getInputStream());
            dis.readFully(buffer);
            return new String(buffer, "UTF-8");
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to read POST body: {}", e.getMessage());
            return null;
        }
    }

    private static EntityPlayerMP findPlayer(String playerUuid) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return null;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                EntityPlayerMP mp = (EntityPlayerMP) obj;
                if (mp.getUniqueID()
                    .toString()
                    .equals(playerUuid)) {
                    return mp;
                }
            }
        }
        return null;
    }

    private static NanoHTTPD.Response jsonResponse(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
