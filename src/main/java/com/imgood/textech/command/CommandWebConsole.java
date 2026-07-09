package com.imgood.textech.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.WebAeLocalDataDir;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.auth.WebAuthToken;
import com.imgood.textech.webae.auth.WebLoginCodeStore;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.icon.IconExportScope;
import com.imgood.textech.webae.icon.IconMissingQueue;
import com.imgood.textech.webae.icon.IconRenderMode;
import com.imgood.textech.webae.icon.IconSnapshotItemCollector;
import com.imgood.textech.webae.icon.IconStore;
import com.imgood.textech.webae.network.PacketWebConsoleTokenNotify;
import com.imgood.textech.webae.snapshot.AeSnapshotCollector;
import com.imgood.textech.webae.worldmap.WorldMapCaptureCoordinator;
import com.imgood.textech.webae.worldmap.WorldMapSnapshotStatusDto;
import com.imgood.textech.webae.worldmap.WorldMapSnapshotStore;

public class CommandWebConsole extends TeXTechCommandBase {

    private static final String[] SUBCOMMANDS = { "issue", "login", "guest", "copy", "revoke", "list", "reload",
        "recipes", "icons", "refresh", "server", "worldmap", "wm", "help" };
    private static final String[] RECIPES_ACTIONS = { "upload", "export", "status", "clear" };
    private static final String[] RECIPES_UPLOAD_SCOPES = { "snapshot", "deep" };
    private static final String[] ICONS_ACTIONS = { "upload", "render", "verify", "import", "import-nesql", "modes",
        "status", "clear" };
    private static final String[] WORLDMAP_ACTIONS = { "upload", "accept", "status", "help" };
    private static final String[] WM_ACTIONS = { "y", "n", "up", "st", "help" };
    private static final String[] SERVER_ACTIONS = { "status", "restart" };
    private static final int HELP_LINES = 13;

    @Override
    public String getCommandName() {
        return "admweb";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return translate("adm.command.admweb.usage");
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("adm-web", "webconsole");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!Config.webConsoleEnabled) {
            sendFormatted(sender, EnumChatFormatting.RED, "adm.webconsole.disabled");
            return;
        }

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendUsage(sender);
            return;
        }

        String sub = args[0].toLowerCase();
        if ("issue".equals(sub)) {
            handleIssue(sender);
        } else if ("login".equals(sub)) {
            handleLogin(sender);
        } else if ("guest".equals(sub)) {
            handleGuest(sender, args);
        } else if ("copy".equals(sub)) {
            handleCopy(sender);
        } else if ("revoke".equals(sub)) {
            handleRevoke(sender, args);
        } else if ("list".equals(sub)) {
            handleList(sender);
        } else if ("reload".equals(sub)) {
            handleReload(sender);
        } else if ("recipes".equals(sub)) {
            handleRecipes(sender, args);
        } else if ("icons".equals(sub)) {
            handleIcons(sender, args);
        } else if ("refresh".equals(sub)) {
            handleRefresh(sender, args);
        } else if ("server".equals(sub)) {
            handleServer(sender, args);
        } else if ("worldmap".equals(sub)) {
            handleWorldMap(sender, args);
        } else if ("wm".equals(sub)) {
            handleWorldMapShort(sender, args);
        } else {
            sendUsage(sender);
        }
    }

    private void handleIssue(ICommandSender sender) {
        if (!requirePlayer(sender)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        String uuid = player.getUniqueID()
            .toString();
        if (WebAeOwnerContext.countMonitors(uuid) <= 0) {
            sendFormatted(sender, EnumChatFormatting.RED, "adm.webconsole.token.no_monitor");
            return;
        }
        String ownerName = player.getCommandSenderName();
        WebAuthToken token = WebAuthToken.generateOwnerToken(uuid, ownerName);
        WebAeOwnerContext.invalidateConnectors(uuid);
        sendTokenIssue(player, token.token);
    }

    private void handleLogin(ICommandSender sender) {
        if (!requirePlayer(sender)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        String uuid = player.getUniqueID()
            .toString();
        String ownerName = player.getCommandSenderName();
        if (WebAeOwnerContext.countMonitors(uuid) <= 0) {
            sendFormatted(sender, EnumChatFormatting.RED, "adm.webconsole.token.no_monitor");
            return;
        }
        String code = WebLoginCodeStore.generateCode(uuid, ownerName);
        if (code == null) {
            sendFormatted(sender, EnumChatFormatting.RED, "adm.webconsole.login.failed");
            return;
        }
        AdvanceDataMonitor.ADMCHANEL.sendTo(
            new PacketWebConsoleTokenNotify(
                PacketWebConsoleTokenNotify.KIND_LOGIN,
                code,
                Config.webConsolePort,
                Config.webConsoleBindAddress),
            player);
    }

    private void handleCopy(ICommandSender sender) {
        if (!requirePlayer(sender)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        WebAuthToken token = WebAuthToken.findByActorUuid(
            player.getUniqueID()
                .toString());
        if (token == null) {
            sendFormatted(
                sender,
                EnumChatFormatting.RED,
                "adm.webconsole.token.notfound",
                player.getCommandSenderName());
            return;
        }
        AdvanceDataMonitor.ADMCHANEL
            .sendTo(new PacketWebConsoleTokenNotify(PacketWebConsoleTokenNotify.KIND_CLIP, token.token, 0, ""), player);
    }

    private static void sendTokenIssue(EntityPlayerMP player, String tokenValue) {
        AdvanceDataMonitor.ADMCHANEL.sendTo(
            new PacketWebConsoleTokenNotify(
                PacketWebConsoleTokenNotify.KIND_ISSUE,
                tokenValue,
                Config.webConsolePort,
                Config.webConsoleBindAddress),
            player);
    }

    private void handleGuest(ICommandSender sender, String[] args) {
        if (!requirePlayer(sender)) {
            return;
        }
        if (args.length < 2) {
            sendFormatted(sender, EnumChatFormatting.YELLOW, "adm.webconsole.guest.usage");
            return;
        }
        EntityPlayerMP owner = (EntityPlayerMP) sender;
        String ownerUuid = owner.getUniqueID()
            .toString();
        if (WebAeOwnerContext.countMonitors(ownerUuid) <= 0) {
            sendFormatted(sender, EnumChatFormatting.RED, "adm.webconsole.token.no_monitor");
            return;
        }
        EntityPlayerMP guest = MinecraftServer.getServer()
            .getConfigurationManager()
            .func_152612_a(args[1]);
        if (guest == null) {
            sendFormatted(sender, EnumChatFormatting.RED, "adm.webconsole.guest.not_online", args[1]);
            return;
        }
        String ownerName = owner.getCommandSenderName();
        WebAuthToken token = WebAuthToken.generateGuestToken(
            ownerUuid,
            ownerName,
            guest.getUniqueID()
                .toString(),
            guest.getCommandSenderName());
        WebAeOwnerContext.invalidateConnectors(ownerUuid);

        sendTokenIssue(guest, token.token);

        sendFormatted(
            sender,
            EnumChatFormatting.GREEN,
            "adm.webconsole.guest.issued_to_owner",
            guest.getCommandSenderName());
        sendLocalized(
            sender,
            EnumChatFormatting.GRAY,
            "adm.command.admweb.guest.token_preview",
            token.token.substring(0, 8));
    }

    private void handleRevoke(ICommandSender sender, String[] args) {
        if (sender instanceof EntityPlayerMP && args.length >= 2 && !canUseOpCommands(sender)) {
            EntityPlayerMP owner = (EntityPlayerMP) sender;
            String revoked = WebAuthToken.revokeGuestTokenByActorName(
                owner.getUniqueID()
                    .toString(),
                args[1]);
            if (revoked != null) {
                sendFormatted(sender, EnumChatFormatting.GREEN, "adm.webconsole.guest.revoked", args[1]);
            } else {
                sendFormatted(sender, EnumChatFormatting.RED, "adm.webconsole.token.notfound", args[1]);
            }
            return;
        }

        String target;
        if (args.length >= 2) {
            target = args[1];
            if (!canUseOpCommands(sender)) {
                sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.revoke.other_op");
                return;
            }
        } else if (sender instanceof EntityPlayerMP) {
            target = ((EntityPlayerMP) sender).getUniqueID()
                .toString();
        } else {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.revoke.usage");
            return;
        }

        String revokedToken = WebAuthToken.revokeOwnerToken(target);
        if (revokedToken == null) {
            EntityPlayerMP targetPlayer = MinecraftServer.getServer()
                .getConfigurationManager()
                .func_152612_a(target);
            if (targetPlayer != null) {
                revokedToken = WebAuthToken.revokeOwnerToken(
                    targetPlayer.getUniqueID()
                        .toString());
            }
        }
        if (revokedToken != null) {
            sendFormatted(sender, EnumChatFormatting.GREEN, "adm.webconsole.token.revoked", target);
        } else {
            sendFormatted(sender, EnumChatFormatting.RED, "adm.webconsole.token.notfound", target);
        }
    }

    private void handleList(ICommandSender sender) {
        if (!requireOp(sender)) {
            return;
        }
        List<WebAuthToken> tokens = WebAuthToken.listAll();
        if (tokens.isEmpty()) {
            sendLocalized(sender, EnumChatFormatting.YELLOW, "adm.command.admweb.list.empty");
            return;
        }
        sendHelpHeader(sender, "adm.command.admweb.list.title");
        for (WebAuthToken t : tokens) {
            String typeLabel = WebAuthSession.TYPE_GUEST.equals(t.type) ? translate("adm.command.admweb.list.type.guest")
                : translate("adm.command.admweb.list.type.owner");
            String actor = t.actorName != null && !t.actorName.isEmpty() ? t.actorName : t.actorUuid;
            sendLocalized(
                sender,
                EnumChatFormatting.WHITE,
                "adm.command.admweb.list.entry",
                typeLabel,
                t.ownerUuid,
                actor,
                t.token.substring(0, 8),
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(t.issuedAt)));
        }
    }

    private void handleReload(ICommandSender sender) {
        if (!requireOp(sender)) {
            return;
        }
        boolean ok = Config.reloadConfiguration();
        if (ok) {
            sendFormatted(sender, EnumChatFormatting.GREEN, "adm.webconsole.config.reloaded");
            sendLocalized(sender, EnumChatFormatting.GRAY, "adm.command.admweb.reload.note");
        } else {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.reload.failed");
        }
    }

    private void handleServer(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelpHeader(sender, "adm.command.admweb.server.title");
            sendHelpLines(sender, "adm.command.admweb.server.help", 2);
            return;
        }
        String action = args[1].toLowerCase();
        if ("status".equals(action)) {
            boolean enabled = Config.webConsoleEnabled;
            boolean running = com.imgood.textech.handler.WebAeServerHandler.isRunning();
            sendHelpHeader(sender, "adm.command.admweb.server.status.title");
            sendLocalized(sender, EnumChatFormatting.WHITE, "adm.command.admweb.server.status.enabled", enabled);
            sendLocalized(sender, EnumChatFormatting.WHITE, "adm.command.admweb.server.status.running", running);
            if (enabled) {
                sendLocalized(
                    sender,
                    EnumChatFormatting.WHITE,
                    "adm.command.admweb.server.status.bind",
                    Config.webConsoleBindAddress,
                    Config.webConsolePort);
                sendLocalized(sender, EnumChatFormatting.GRAY, "adm.command.admweb.server.status.logs");
            }
            return;
        }
        if ("restart".equals(action)) {
            if (!requireOp(sender)) {
                return;
            }
            if (!Config.webConsoleEnabled) {
                sendFormatted(sender, EnumChatFormatting.RED, "adm.webconsole.disabled");
                return;
            }
            boolean ok = com.imgood.textech.handler.WebAeServerHandler.restartServer();
            if (ok) {
                sendFormatted(
                    sender,
                    EnumChatFormatting.GREEN,
                    "adm.webconsole.server.restarted",
                    Config.webConsoleBindAddress,
                    Config.webConsolePort);
            } else {
                sendFormatted(sender, EnumChatFormatting.RED, "adm.webconsole.server.restart_failed");
            }
            return;
        }
        sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.server.unknown");
    }

    private void handleRefresh(ICommandSender sender, String[] args) {
        if (!requireOp(sender)) {
            return;
        }
        String uuid;
        if (sender instanceof EntityPlayerMP) {
            uuid = ((EntityPlayerMP) sender).getUniqueID()
                .toString();
        } else if (args.length >= 3) {
            uuid = args[2];
        } else {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.refresh.usage");
            return;
        }

        WebAeOwnerContext.invalidateConnectors(uuid);

        if (args.length >= 2) {
            int networkId;
            try {
                networkId = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sendLocalized(sender, EnumChatFormatting.RED, "adm.command.common.invalid_network_id", args[1]);
                return;
            }
            refreshOneNetwork(uuid, networkId);
            sendFormatted(sender, EnumChatFormatting.GREEN, "adm.webconsole.refresh.started_single", networkId);
            return;
        }

        int refreshed = refreshAllNetworks(uuid);
        if (refreshed <= 0) {
            sendFormatted(sender, EnumChatFormatting.YELLOW, "adm.webconsole.refresh.no_active");
        } else {
            sendFormatted(sender, EnumChatFormatting.GREEN, "adm.webconsole.refresh.started_all", refreshed);
        }
    }

    private void refreshOneNetwork(String uuid, int networkId) {
        com.imgood.textech.webae.cache.SnapshotCache.instance()
            .invalidateAll(uuid, networkId);
        com.imgood.textech.webae.cache.SnapshotScheduler.markActive(uuid, networkId);
        com.imgood.textech.webae.cache.SnapshotScheduler.forceCollectStorage(uuid, networkId);
        com.imgood.textech.webae.cache.SnapshotScheduler.forceCollectGt(uuid, networkId);
        com.imgood.textech.webae.power.PowerSampler.getInstance()
            .markActive(uuid, networkId);
    }

    private int refreshAllNetworks(String uuid) {
        java.util.List<AeSnapshotCollector.NetworkInfo> networks = AeSnapshotCollector
            .findNetworksBlocking(uuid, 10_000L, true);
        if (networks == null || networks.isEmpty()) return 0;
        for (int i = 0; i < networks.size(); i++) {
            refreshOneNetwork(uuid, i);
        }
        return networks.size();
    }

    private void handleRecipes(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelpHeader(sender, "adm.command.admweb.recipes.title");
            sendHelpLines(sender, "adm.command.admweb.recipes.help", 4);
            return;
        }
        String action = args[1].toLowerCase();
        if ("upload".equals(action) || "export".equals(action)) {
            if (!requireOp(sender) || !requirePlayer(sender)) {
                return;
            }
            EntityPlayerMP player = (EntityPlayerMP) sender;
            if (!Config.webRecipeUploadEnabled) {
                sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.recipes.upload_disabled");
                return;
            }
            String scope = "full";
            String snapshotJson = "";
            if (args.length >= 3) {
                if ("snapshot".equalsIgnoreCase(args[2])) {
                    scope = "snapshot";
                    java.util.List<String> itemIds = IconSnapshotItemCollector.collectItemIds();
                    snapshotJson = new com.google.gson.Gson().toJson(itemIds);
                    sendLocalized(
                        sender,
                        EnumChatFormatting.AQUA,
                        "adm.command.admweb.recipes.snapshot_info",
                        itemIds.size());
                } else if ("deep".equalsIgnoreCase(args[2])) {
                    scope = "deep";
                    sendLocalized(sender, EnumChatFormatting.YELLOW, "adm.command.admweb.recipes.deep_info");
                }
            }
            if ("export".equals(action)) {
                sendLocalized(sender, EnumChatFormatting.AQUA, "adm.command.admweb.recipes.exporting");
            }
            com.imgood.textech.webae.network.PacketWebUploadTrigger trigger = new com.imgood.textech.webae.network.PacketWebUploadTrigger(
                com.imgood.textech.webae.network.PacketWebUploadTrigger.TYPE_RECIPES,
                snapshotJson,
                scope);
            AdvanceDataMonitor.ADMCHANEL.sendTo(trigger, player);
            sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admweb.recipes.triggered", scope);
        } else if ("status".equals(action)) {
            com.imgood.textech.webae.recipe.RecipeCacheStore.CacheStatus status = com.imgood.textech.webae.recipe.RecipeCacheStore
                .instance()
                .getStatus();
            sendHelpHeader(sender, "adm.command.admweb.recipes.status.title");
            sendLocalized(sender, EnumChatFormatting.WHITE, "adm.command.admweb.recipes.status.total", status.recipeCount);
            sendLocalized(sender, EnumChatFormatting.WHITE, "adm.command.admweb.recipes.status.handlers", status.handlerCount);
            sendLocalized(sender, EnumChatFormatting.WHITE, "adm.command.admweb.recipes.status.updated", formatTime(status.lastUpdateTime));
            if (status.lastDiskSave > 0) {
                sendLocalized(sender, EnumChatFormatting.WHITE, "adm.command.admweb.recipes.status.disk_size", formatBytes(status.diskCacheSize));
                sendLocalized(sender, EnumChatFormatting.WHITE, "adm.command.admweb.recipes.status.disk_save", formatTime(status.lastDiskSave));
            } else {
                sendLocalized(sender, EnumChatFormatting.GRAY, "adm.command.admweb.recipes.status.disk_none");
            }
        } else if ("clear".equals(action)) {
            if (!requireOp(sender)) {
                return;
            }
            com.imgood.textech.webae.recipe.RecipeCacheStore.instance()
                .clear();
            sendFormatted(sender, EnumChatFormatting.GREEN, "adm.webconsole.recipes.cleared");
        } else {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.recipes.unknown");
        }
    }

    private void handleIcons(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelpHeader(sender, "adm.command.admweb.icons.title");
            sendHelpLines(sender, "adm.command.admweb.icons.help", 7);
            return;
        }
        String action = args[1].toLowerCase();
        if ("modes".equals(action)) {
            sendHelpHeader(sender, "adm.command.admweb.icons.modes.title");
            for (IconRenderMode mode : IconRenderMode.allModes()) {
                EnumChatFormatting statusColor = mode.isImplemented() ? EnumChatFormatting.GREEN
                    : (mode.isDeprecated() ? EnumChatFormatting.GRAY : EnumChatFormatting.GRAY);
                String statusKey = mode.isImplemented() ? "adm.command.admweb.icons.mode.ready"
                    : (mode.isDeprecated() ? "adm.command.admweb.icons.mode.deprecated"
                        : "adm.command.admweb.icons.mode.planned");
                sendPlain(
                    sender,
                    EnumChatFormatting.WHITE,
                    "  "
                        + mode.getId()
                        + " "
                        + statusColor
                        + translate(statusKey)
                        + EnumChatFormatting.RESET
                        + " — "
                        + I18nFormat(mode.getLabelKey()));
            }
            return;
        }
        if ("upload".equals(action)) {
            handleIconsUpload(sender, args);
        } else if ("render".equals(action)) {
            handleIconsRender(sender, args);
        } else if ("verify".equals(action)) {
            handleIconsVerify(sender, args);
        } else if ("import".equals(action)) {
            handleIconsImport(sender, args);
        } else if ("import-nesql".equals(action)) {
            handleIconsImportNesql(sender, args);
        } else if ("status".equals(action)) {
            java.util.List<IconStore.PackInfo> packs = IconStore.instance()
                .listPacks();
            sendHelpHeader(sender, "adm.command.admweb.icons.status.title");
            if (packs.isEmpty()) {
                sendLocalized(sender, EnumChatFormatting.GRAY, "adm.command.admweb.icons.status.empty");
            } else {
                for (IconStore.PackInfo p : packs) {
                    StringBuilder line = new StringBuilder();
                    line.append(translate("adm.command.admweb.icons.status.pack", p.packName, p.iconCount));
                    if (p.modeCounts != null && !p.modeCounts.isEmpty()) {
                        line.append(" [");
                        boolean first = true;
                        for (java.util.Map.Entry<String, Integer> mc : p.modeCounts.entrySet()) {
                            if (!first) line.append(", ");
                            line.append(mc.getKey())
                                .append('=')
                                .append(mc.getValue());
                            first = false;
                        }
                        line.append(']');
                    }
                    sendPlain(sender, EnumChatFormatting.WHITE, line.toString());
                }
            }
            sendLocalized(sender, EnumChatFormatting.WHITE, "adm.command.admweb.icons.status.cache", Config.webIconCacheEnabled);
            sendLocalized(sender, EnumChatFormatting.WHITE, "adm.command.admweb.icons.status.upload", Config.webIconUploadEnabled);
            sendLocalized(sender, EnumChatFormatting.WHITE, "adm.command.admweb.icons.status.pack_enabled", Config.webIconPackEnabled);
            sendLocalized(
                sender,
                EnumChatFormatting.WHITE,
                "adm.command.admweb.icons.status.queue",
                IconMissingQueue.instance()
                    .pendingCount());
        } else if ("clear".equals(action)) {
            if (!requireOp(sender)) {
                return;
            }
            int removed = IconStore.instance()
                .clearAll();
            IconMissingQueue.instance()
                .clear();
            sendFormatted(sender, EnumChatFormatting.GREEN, "adm.webconsole.icons.cleared", removed);
        } else {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.icons.unknown");
        }
    }

    private void handleIconsUpload(ICommandSender sender, String[] args) {
        if (!requireOp(sender) || !requirePlayer(sender)) {
            return;
        }
        if (!Config.webIconCacheEnabled || !Config.webIconUploadEnabled) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.icons.disabled");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        int argIdx = 2;
        IconExportScope scope = IconExportScope.ALL;
        if (args.length >= 3 && "snapshot".equalsIgnoreCase(args[2])) {
            scope = IconExportScope.SNAPSHOT;
            argIdx = 3;
        }
        String packName = args.length > argIdx ? args[argIdx] : "default";
        String renderMode = args.length > argIdx + 1 ? args[argIdx + 1] : IconRenderMode.NEI.getId();
        if (!IconStore.isValidPackName(packName) || !IconRenderMode.isValidModeId(renderMode)) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.icons.invalid");
            return;
        }
        java.util.List<String> itemIds = scope == IconExportScope.SNAPSHOT ? IconSnapshotItemCollector.collectItemIds()
            : new java.util.ArrayList<String>();
        if (scope == IconExportScope.SNAPSHOT && itemIds.isEmpty()) {
            sendLocalized(sender, EnumChatFormatting.YELLOW, "adm.command.admweb.icons.no_snapshot");
            return;
        }
        IconMissingQueue.instance()
            .setProviderUuid(
                player.getUniqueID()
                    .toString());
        AdvanceDataMonitor.ADMCHANEL.sendTo(
            new com.imgood.textech.webae.network.PacketWebUploadTrigger(
                com.imgood.textech.webae.network.PacketWebUploadTrigger.TYPE_ICONS,
                packName,
                renderMode,
                scope,
                itemIds),
            player);
        sendLocalized(
            sender,
            EnumChatFormatting.GREEN,
            "adm.command.admweb.icons.upload_started",
            packName,
            renderMode,
            scope.getId());
    }

    private void handleIconsRender(ICommandSender sender, String[] args) {
        if (!requireOp(sender) || !requirePlayer(sender) || args.length < 3) {
            if (args.length < 3) {
                sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.icons.render_usage");
            }
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        String itemId = args[2];
        String packName = args.length >= 4 ? args[3] : "default";
        String renderMode = args.length >= 5 ? args[4] : IconRenderMode.NEI.getId();
        java.util.List<String> ids = new java.util.ArrayList<String>();
        ids.add(itemId);
        IconMissingQueue.instance()
            .setProviderUuid(
                player.getUniqueID()
                    .toString());
        AdvanceDataMonitor.ADMCHANEL.sendTo(
            new com.imgood.textech.webae.network.PacketWebUploadTrigger(
                com.imgood.textech.webae.network.PacketWebUploadTrigger.TYPE_ICONS,
                packName,
                renderMode,
                IconExportScope.LIST,
                ids),
            player);
        sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admweb.icons.rendering", itemId);
    }

    private void handleIconsVerify(ICommandSender sender, String[] args) {
        if (!requirePlayer(sender) || args.length < 3) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.icons.verify_usage");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        AdvanceDataMonitor.ADMCHANEL.sendTo(
            new com.imgood.textech.webae.network.PacketWebUploadTrigger(
                com.imgood.textech.webae.network.PacketWebUploadTrigger.TYPE_ICON_VERIFY,
                args.length >= 4 ? args[3] : "default",
                args[2]),
            player);
        sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admweb.icons.verify_open", args[2]);
    }

    private void handleIconsImport(ICommandSender sender, String[] args) {
        if (!requireOp(sender) || args.length < 3) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.icons.import_usage");
            return;
        }
        String packName = args.length >= 4 ? args[3] : "default";
        java.io.File src = new java.io.File(args[2]);
        if (!IconStore.isValidPackName(packName) || !src.isDirectory()) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.icons.import_invalid");
            return;
        }
        java.io.File destDir = new java.io.File(
            IconStore.instance()
                .getBaseDir(),
            packName + java.io.File.separator + IconRenderMode.NEI.getId());
        destDir.mkdirs();
        int copied = 0;
        java.io.File[] files = src.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f == null || !f.getName()
                    .endsWith(".png")) continue;
                String base = f.getName()
                    .substring(
                        0,
                        f.getName()
                            .length() - 4);
                java.io.File out = new java.io.File(destDir, IconStore.sanitizeItemId(base.replace(':', '_')) + ".png");
                try {
                    java.nio.file.Files
                        .copy(f.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                } catch (Exception ignored) {}
            }
        }
        IconStore.instance()
            .refreshPack(packName);
        IconStore.instance()
            .recordDefaultPack(packName);
        IconStore.instance()
            .recordModeUpload(packName, IconRenderMode.NEI.getId(), copied);
        sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admweb.icons.import_done", copied, packName);
    }

    private void handleIconsImportNesql(ICommandSender sender, String[] args) {
        if (!requireOp(sender)) {
            return;
        }
        String packName = args.length >= 3 ? args[2] : "default";
        String subPath = args.length >= 4 ? args[3] : "";
        if (!IconStore.isValidPackName(packName)) {
            sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.icons.import_nesql_invalid");
            return;
        }
        String repoPath = WebAeLocalDataDir.resolveNesqlRepositoryPath();
        int copied = com.imgood.textech.webae.icon.NesqlIconImporter.importFromRepository(packName, subPath);
        sendLocalized(
            sender,
            EnumChatFormatting.GREEN,
            "adm.command.admweb.icons.import_nesql_done",
            copied,
            packName,
            repoPath);
    }

    private static String formatTime(long ms) {
        if (ms <= 0) return "-";
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(ms));
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void handleWorldMap(ICommandSender sender, String[] args) {
        if (!requirePlayer(sender)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        if (args.length < 2 || "help".equalsIgnoreCase(args[1])) {
            sendHelpHeader(sender, "adm.command.admweb.worldmap.title");
            sendHelpLines(sender, "adm.command.admweb.worldmap.help", 3);
            return;
        }
        String action = args[1].toLowerCase();
        int networkId = 0;
        if ("upload".equals(action)) {
            if (args.length >= 3) {
                try {
                    networkId = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sendLocalized(sender, EnumChatFormatting.RED, "adm.command.common.invalid_network_id", args[2]);
                    return;
                }
            }
            String ownerUuid = player.getUniqueID()
                .toString();
            String result = WorldMapCaptureCoordinator.instance()
                .requestSnapshot(
                    ownerUuid,
                    networkId,
                    ownerUuid,
                    player.getDisplayName(),
                    true,
                    true);
            if (result == null) {
                sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.worldmap.start_failed");
            } else {
                sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admweb.worldmap.start_ok", result);
            }
            return;
        }
        if ("accept".equals(action)) {
            if (args.length < 3) {
                sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.worldmap.accept_usage");
                return;
            }
            boolean ok = WorldMapCaptureCoordinator.instance()
                .accept(args[2], player);
            if (!ok) {
                sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.worldmap.accept_failed");
            } else {
                sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admweb.wm.accept_ok");
            }
            return;
        }
        if ("status".equals(action)) {
            if (args.length >= 3) {
                try {
                    networkId = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sendLocalized(sender, EnumChatFormatting.RED, "adm.command.common.invalid_network_id", args[2]);
                    return;
                }
            }
            String ownerUuid = player.getUniqueID()
                .toString();
            WorldMapSnapshotStatusDto status = WorldMapCaptureCoordinator.instance()
                .buildStatus(ownerUuid, networkId);
            int ver = WorldMapSnapshotStore.currentVersion(ownerUuid, networkId);
            String progress = "";
            if (status != null && status.completedChunks > 0) {
                progress = translate("adm.command.admweb.worldmap.progress", status.completedChunks, status.totalChunks);
            }
            sendLocalized(
                sender,
                EnumChatFormatting.AQUA,
                "adm.command.admweb.worldmap.status",
                ver,
                status != null ? status.captureState : "?",
                progress);
            return;
        }
        sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.worldmap.unknown");
    }

    private void handleWorldMapShort(ICommandSender sender, String[] args) {
        if (!requirePlayer(sender)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        if (args.length < 2 || "help".equalsIgnoreCase(args[1])) {
            sendHelpHeader(sender, "adm.command.admweb.wm.title");
            sendHelpLines(sender, "adm.command.admweb.wm.help", 4);
            return;
        }
        String action = args[1].toLowerCase();
        if ("y".equals(action) || "yes".equals(action)) {
            String requestId = args.length >= 3 ? args[2] : WorldMapCaptureCoordinator.instance()
                .latestPendingForPlayer(player);
            if (requestId == null || requestId.isEmpty()) {
                sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.wm.no_pending");
                return;
            }
            boolean ok = WorldMapCaptureCoordinator.instance()
                .accept(requestId, player);
            if (!ok) {
                sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.worldmap.accept_failed");
            } else {
                sendLocalized(sender, EnumChatFormatting.GREEN, "adm.command.admweb.wm.accept_ok");
            }
            return;
        }
        if ("n".equals(action) || "no".equals(action)) {
            String requestId = args.length >= 3 ? args[2] : WorldMapCaptureCoordinator.instance()
                .latestPendingForPlayer(player);
            if (requestId == null || requestId.isEmpty()) {
                sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.wm.no_pending");
                return;
            }
            if (!WorldMapCaptureCoordinator.instance()
                .reject(requestId, player)) {
                sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.worldmap.accept_failed");
            }
            return;
        }
        if ("up".equals(action)) {
            String[] forwarded = new String[] { "worldmap", "upload" };
            if (args.length >= 3) {
                forwarded = new String[] { "worldmap", "upload", args[2] };
            }
            handleWorldMap(sender, forwarded);
            return;
        }
        if ("st".equals(action)) {
            String[] forwarded = new String[] { "worldmap", "status" };
            if (args.length >= 3) {
                forwarded = new String[] { "worldmap", "status", args[2] };
            }
            handleWorldMap(sender, forwarded);
            return;
        }
        sendLocalized(sender, EnumChatFormatting.RED, "adm.command.admweb.wm.unknown");
    }

    private void sendUsage(ICommandSender sender) {
        sendHelpHeader(sender, "adm.command.admweb.title");
        sendUsageSummary(sender, "adm.command.admweb.usage");
        sendHelpLines(sender, "adm.command.admweb.help", HELP_LINES);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterTabCompletion(args, SUBCOMMANDS);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("server".equals(sub)) {
                return filterTabCompletion(args, SERVER_ACTIONS);
            }
            if ("recipes".equals(sub)) {
                return filterTabCompletion(args, RECIPES_ACTIONS);
            }
            if ("icons".equals(sub)) {
                return filterTabCompletion(args, ICONS_ACTIONS);
            }
            if ("worldmap".equals(sub)) {
                return filterTabCompletion(args, WORLDMAP_ACTIONS);
            }
            if ("wm".equals(sub)) {
                return filterTabCompletion(args, WM_ACTIONS);
            }
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if ("wm".equals(sub) && ("y".equalsIgnoreCase(args[1]) || "n".equalsIgnoreCase(args[1]))) {
                if (!(sender instanceof EntityPlayerMP)) {
                    return null;
                }
                return filterTabCompletion(
                    args,
                    WorldMapCaptureCoordinator.instance()
                        .listPendingForPlayer((EntityPlayerMP) sender));
            }
            if ("recipes".equals(sub) && ("upload".equalsIgnoreCase(args[1]) || "export".equalsIgnoreCase(args[1]))) {
                return filterTabCompletion(args, RECIPES_UPLOAD_SCOPES);
            }
            if ("icons".equals(sub) && "upload".equalsIgnoreCase(args[1])) {
                List<String> opts = new ArrayList<>();
                opts.add("snapshot");
                opts.add("all");
                for (IconRenderMode mode : IconRenderMode.allModes()) {
                    opts.add(mode.getId());
                }
                return filterTabCompletion(args, opts);
            }
        }
        if (args.length == 4 && "icons".equalsIgnoreCase(args[0]) && "upload".equalsIgnoreCase(args[1])) {
            List<String> filtered = new ArrayList<>();
            filtered.add("all");
            for (IconRenderMode mode : IconRenderMode.allModes()) {
                if (mode.getId()
                    .startsWith(args[3].toLowerCase())) {
                    filtered.add(mode.getId());
                }
            }
            return filtered.isEmpty() ? null : filtered;
        }
        return null;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    private void sendFormatted(ICommandSender sender, EnumChatFormatting color, String key, Object... args) {
        ChatComponentTranslation component = new ChatComponentTranslation(key, args);
        component.getChatStyle()
            .setColor(color);
        sender.addChatMessage(component);
    }

    private static String I18nFormat(String key, Object... args) {
        return net.minecraft.util.StatCollector.translateToLocalFormatted(key, args);
    }

    private static String translate(String key, Object... args) {
        return net.minecraft.util.StatCollector.translateToLocalFormatted(key, args);
    }
}
