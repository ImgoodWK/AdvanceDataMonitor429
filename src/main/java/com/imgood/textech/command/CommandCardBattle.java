package com.imgood.textech.command;

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
import com.imgood.textech.cardbattle.auth.CardBattleAccounts;
import com.imgood.textech.handler.CardBattleProcessHandler;

/**
 * {@code /textech card status|start|stop|restart|bind}
 */
public class CommandCardBattle extends TeXTechCommandBase {

    @Override
    public String getCommandName() {
        return "card";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/textech card <status|start|stop|restart|bind|help>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + getCommandUsage(sender)));
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GRAY + "bind — 生成卡牌网页绑定码（个人设置中填入）"));
            return;
        }
        String sub = args[0].toLowerCase();
        if ("status".equals(sub)) {
            boolean on = CardBattleProcessHandler.isRunning();
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "[CardBattle] running="
                        + on
                        + " enabled="
                        + Config.cardBattleEnabled
                        + " port="
                        + Config.cardBattlePort));
            String url = CardBattleProcessHandler.getLastUrl();
            if (url != null && url.length() > 0) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "URL: " + url));
            }
            String err = CardBattleProcessHandler.getLastError();
            if (err != null && err.length() > 0) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "lastError: " + err));
            }
            return;
        }
        if ("bind".equals(sub)) {
            if (!(sender instanceof EntityPlayerMP)) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "仅游戏内玩家可绑定"));
                return;
            }
            EntityPlayerMP player = (EntityPlayerMP) sender;
            UUID uuid = player.getUniqueID();
            String name = player.getCommandSenderName();
            try {
                String code = issueBindCode(uuid.toString(), name);
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GREEN + "[CardBattle] 绑定码: "
                            + EnumChatFormatting.YELLOW
                            + code));
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GRAY + "10 分钟内在卡牌网页「个人设置」填入。一账号仅绑一角色。"));
            } catch (Throwable t) {
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED + "[CardBattle] 生成绑定码失败: "
                            + (t.getMessage() != null ? t.getMessage() : t.getClass()
                                .getSimpleName())));
            }
            return;
        }
        if ("start".equals(sub) || "stop".equals(sub) || "restart".equals(sub)) {
            if (!requireOp(sender)) {
                return;
            }
            if ("start".equals(sub)) {
                CardBattleProcessHandler.startIfEnabled();
            } else if ("stop".equals(sub)) {
                CardBattleProcessHandler.stopServer();
            } else {
                CardBattleProcessHandler.restartServer();
            }
            processCommand(sender, new String[] { "status" });
            return;
        }
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + getCommandUsage(sender)));
    }

    private static String issueBindCode(String mcUuid, String mcName) throws Exception {
        String base = Config.cardBattleExternalApiBaseUrl != null ? Config.cardBattleExternalApiBaseUrl.trim() : "";
        String bridge = Config.cardBattleBridgeToken != null ? Config.cardBattleBridgeToken.trim() : "";
        if (base.length() > 0 && bridge.length() > 0) {
            return issueBindCodeHttp(base, bridge, mcUuid, mcName);
        }
        CardBattleAccounts.BindCode entry = CardBattleAccounts.issueBindCode(mcUuid, mcName);
        return entry.code;
    }

    private static String issueBindCodeHttp(String baseUrl, String bridgeToken, String mcUuid, String mcName)
        throws Exception {
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        url = url + "/api/bridge/bind-codes";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(8000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("X-CardBattle-Bridge-Token", bridgeToken);
        JsonObject body = new JsonObject();
        body.addProperty("mcUuid", mcUuid);
        body.addProperty("mcName", mcName);
        byte[] bytes = body.toString()
            .getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        OutputStream out = conn.getOutputStream();
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
        int code = conn.getResponseCode();
        java.io.InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String text = "";
        if (stream != null) {
            byte[] buf = new byte[4096];
            int n = stream.read(buf);
            if (n > 0) text = new String(buf, 0, n, StandardCharsets.UTF_8);
            stream.close();
        }
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + " " + text);
        }
        JsonObject json = new JsonParser().parse(text)
            .getAsJsonObject();
        if (!json.has("code")) throw new IllegalStateException("响应缺少 code");
        return json.get("code")
            .getAsString();
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterTabCompletion(args, new String[] { "status", "start", "stop", "restart", "bind", "help" });
        }
        return null;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
