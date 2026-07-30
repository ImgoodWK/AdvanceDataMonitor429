package com.imgood.textech.command;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.Config;

/** {@code /textech card status|bind}; adapter for the standalone Card Battle service. */
public class CommandCardBattle extends TeXTechCommandBase {

    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    @Override
    public String getCommandName() {
        return "card";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/textech card <status|bind|help>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + getCommandUsage(sender)));
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "status — 检查独立卡牌服务桥接状态"));
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "bind — 生成卡牌网页绑定码（个人设置中填入）"));
            return;
        }

        String sub = args[0].toLowerCase();
        if ("status".equals(sub)) {
            showStatus(sender);
            return;
        }
        if ("bind".equals(sub)) {
            bindPlayer(sender);
            return;
        }
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + getCommandUsage(sender)));
    }

    private static void showStatus(ICommandSender sender) {
        try {
            BridgeConfig bridge = requireBridgeConfig();
            JsonObject response = requestBridge(bridge, "GET", "/api/bridge/v1/status", null);
            String status = stringValue(response, "status", "unknown");
            String schema = stringValue(response, "schemaVersion", "?");
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "[CardBattle] 外部桥可用: status=" + status + " schemaVersion=" + schema));
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "URL: " + bridge.baseUrl));
            if (response.has("rewardDelivery") && response.get("rewardDelivery")
                .isJsonObject()) {
                JsonObject delivery = response.getAsJsonObject("rewardDelivery");
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GRAY + "奖励投递模式: "
                            + stringValue(delivery, "mode", "unknown")
                            + "；ADM 自动兑换尚未启用"));
            }
        } catch (Throwable t) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "[CardBattle] 外部桥不可用: " + errorMessage(t)));
        }
    }

    private static void bindPlayer(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "仅游戏内玩家可绑定"));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        UUID uuid = player.getUniqueID();
        try {
            BridgeConfig bridge = requireBridgeConfig();
            JsonObject body = new JsonObject();
            body.addProperty("mcUuid", uuid.toString());
            body.addProperty("mcName", player.getCommandSenderName());
            JsonObject response = requestBridge(bridge, "POST", "/api/bridge/v1/bind-codes", body);
            if (!response.has("code")) {
                throw new IllegalStateException("响应缺少 code");
            }
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "[CardBattle] 绑定码: "
                        + EnumChatFormatting.YELLOW
                        + response.get("code")
                            .getAsString()));
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "10 分钟内在卡牌网页「个人设置」填入。一账号仅绑一角色。"));
        } catch (Throwable t) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "[CardBattle] 生成绑定码失败: " + errorMessage(t)));
        }
    }

    private static BridgeConfig requireBridgeConfig() {
        String base = Config.cardBattleExternalApiBaseUrl != null ? Config.cardBattleExternalApiBaseUrl.trim() : "";
        String token = Config.cardBattleBridgeToken != null ? Config.cardBattleBridgeToken.trim() : "";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.length() == 0 || token.length() == 0) {
            throw new IllegalStateException("未配置 [cardBattle] externalApiBaseUrl 与 bridgeToken");
        }
        return new BridgeConfig(base, token);
    }

    private static JsonObject requestBridge(BridgeConfig bridge, String method, String path, JsonObject body)
        throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(bridge.baseUrl + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-CardBattle-Bridge-Token", bridge.token);
            if (body != null) {
                byte[] bytes = body.toString()
                    .getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                OutputStream output = connection.getOutputStream();
                try {
                    output.write(bytes);
                } finally {
                    output.close();
                }
            }

            int code = connection.getResponseCode();
            InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String responseText = readResponse(input);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code + (responseText.length() > 0 ? " " + responseText : ""));
            }
            if (responseText.length() == 0) {
                throw new IllegalStateException("服务返回空响应");
            }
            return new JsonParser().parse(responseText)
                .getAsJsonObject();
        } finally {
            connection.disconnect();
        }
    }

    private static String readResponse(InputStream input) throws Exception {
        if (input == null) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (output.size() + count > MAX_RESPONSE_BYTES) {
                    throw new IllegalStateException("服务响应过大");
                }
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
            output.close();
        }
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        if (!object.has(key) || object.get(key)
            .isJsonNull()) return fallback;
        return object.get(key)
            .getAsString();
    }

    private static String errorMessage(Throwable error) {
        String message = error.getMessage();
        return message != null && message.length() > 0 ? message
            : error.getClass()
                .getSimpleName();
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterTabCompletion(args, new String[] { "status", "bind", "help" });
        }
        return null;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    private static final class BridgeConfig {

        private final String baseUrl;
        private final String token;

        private BridgeConfig(String baseUrl, String token) {
            this.baseUrl = baseUrl;
            this.token = token;
        }
    }
}
