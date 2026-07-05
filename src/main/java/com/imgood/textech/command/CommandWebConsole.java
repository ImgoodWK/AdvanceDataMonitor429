package com.imgood.textech.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.auth.WebAuthToken;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.icon.IconExportScope;
import com.imgood.textech.webae.icon.IconMissingQueue;
import com.imgood.textech.webae.icon.IconRenderMode;
import com.imgood.textech.webae.icon.IconSnapshotItemCollector;
import com.imgood.textech.webae.icon.IconStore;
import com.imgood.textech.webae.network.PacketWebIconExportScope;
import com.imgood.textech.webae.network.PacketWebConsoleTokenNotify;
import com.imgood.textech.webae.WebAeLocalDataDir;
import com.imgood.textech.webae.snapshot.AeSnapshotCollector;

public class CommandWebConsole extends CommandBase {

    private static final String[] SUBCOMMANDS = { "issue", "guest", "copy", "revoke", "list", "reload", "recipes", "icons",
        "refresh", "server", "help" };

    @Override
    public String getCommandName() {
        return "admweb";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/admweb <issue|revoke|list|reload|help>";
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
        } else {
            sendUsage(sender);
        }
    }

    private void handleIssue(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) {
            send(sender, EnumChatFormatting.RED + "This command can only be used by a player.");
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
        WebAuthToken token = WebAuthToken.generateOwnerToken(uuid, ownerName);
        WebAeOwnerContext.invalidateConnectors(uuid);
        sendTokenIssue(player, token.token);
    }

    private void handleCopy(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) {
            send(sender, EnumChatFormatting.RED + "This command can only be used by a player.");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        WebAuthToken token = WebAuthToken.findByActorUuid(
            player.getUniqueID()
                .toString());
        if (token == null) {
            sendFormatted(sender, EnumChatFormatting.RED, "adm.webconsole.token.notfound", player.getCommandSenderName());
            return;
        }
        AdvanceDataMonitor.ADMCHANEL.sendTo(
            new PacketWebConsoleTokenNotify(PacketWebConsoleTokenNotify.KIND_CLIP, token.token, 0, ""),
            player);
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
        if (!(sender instanceof EntityPlayerMP)) {
            send(sender, EnumChatFormatting.RED + "This command can only be used by a player.");
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
        send(
            sender,
            EnumChatFormatting.GRAY + "Guest token: " + EnumChatFormatting.WHITE + token.token.substring(0, 8) + "...");
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
                send(sender, EnumChatFormatting.RED + "You need operator permission to revoke other players' tokens.");
                return;
            }
        } else if (sender instanceof EntityPlayerMP) {
            target = ((EntityPlayerMP) sender).getUniqueID()
                .toString();
        } else {
            send(sender, EnumChatFormatting.RED + "Usage: /admweb revoke [player|guestName]");
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
        if (!canUseOpCommands(sender)) {
            send(sender, EnumChatFormatting.RED + "You need operator permission to list all tokens.");
            return;
        }
        List<WebAuthToken> tokens = WebAuthToken.listAll();
        if (tokens.isEmpty()) {
            send(sender, EnumChatFormatting.YELLOW + "No active tokens.");
            return;
        }
        send(sender, EnumChatFormatting.AQUA + "=== Active Web Console Tokens ===");
        for (WebAuthToken t : tokens) {
            String typeLabel = WebAuthSession.TYPE_GUEST.equals(t.type) ? "guest" : "owner";
            String actor = t.actorName != null && !t.actorName.isEmpty() ? t.actorName : t.actorUuid;
            send(
                sender,
                EnumChatFormatting.WHITE + "  ["
                    + typeLabel
                    + "] owner="
                    + t.ownerUuid
                    + " actor="
                    + actor
                    + " | Token: "
                    + t.token.substring(0, 8)
                    + "..."
                    + " | Issued: "
                    + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(t.issuedAt)));
        }
    }

    private void handleReload(ICommandSender sender) {
        if (!canUseOpCommands(sender)) {
            send(sender, EnumChatFormatting.RED + "You need operator permission to reload configuration.");
            return;
        }
        boolean ok = Config.reloadConfiguration();
        if (ok) {
            sendFormatted(sender, EnumChatFormatting.GREEN, "adm.webconsole.config.reloaded");
            send(
                sender,
                EnumChatFormatting.GRAY
                    + "Note: some settings (e.g. web console port/bind address) only take effect after a server restart.");
        } else {
            send(sender, EnumChatFormatting.RED + "Configuration reload failed — see server log for details.");
        }
    }

    private void handleServer(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, EnumChatFormatting.YELLOW + "/admweb server status  - Show WebAE HTTP server state");
            send(sender, EnumChatFormatting.YELLOW + "/admweb server restart - Restart HTTP server (OP only)");
            return;
        }
        String action = args[1].toLowerCase();
        if ("status".equals(action)) {
            boolean enabled = Config.webConsoleEnabled;
            boolean running = com.imgood.textech.handler.WebAeServerHandler.isRunning();
            send(sender, EnumChatFormatting.AQUA + "=== WebAE HTTP Server ===");
            send(sender, EnumChatFormatting.WHITE + "  Config enabled: " + enabled);
            send(sender, EnumChatFormatting.WHITE + "  Running: " + running);
            if (enabled) {
                send(
                    sender,
                    EnumChatFormatting.WHITE + "  Bind: " + Config.webConsoleBindAddress + ":" + Config.webConsolePort);
                send(sender, EnumChatFormatting.GRAY + "  Logs: logs/latest.log (search [WebAE])");
            }
            return;
        }
        if ("restart".equals(action)) {
            if (!canUseOpCommands(sender)) {
                send(sender, EnumChatFormatting.RED + "You need operator permission to restart the WebAE server.");
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
        send(sender, EnumChatFormatting.RED + "Unknown server subcommand. Use: status, restart");
    }

    private void handleRefresh(ICommandSender sender, String[] args) {
        if (!canUseOpCommands(sender)) {
            send(sender, EnumChatFormatting.RED + "You need operator permission to force a snapshot refresh.");
            return;
        }
        String uuid;
        if (sender instanceof EntityPlayerMP) {
            uuid = ((EntityPlayerMP) sender).getUniqueID()
                .toString();
        } else if (args.length >= 3) {
            uuid = args[2];
        } else {
            send(
                sender,
                EnumChatFormatting.RED + "Refresh must be triggered by a player, or specify: /admweb refresh [network] <ownerUuid>");
            return;
        }

        WebAeOwnerContext.invalidateConnectors(uuid);

        if (args.length >= 2) {
            int networkId;
            try {
                networkId = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                send(sender, EnumChatFormatting.RED + "Invalid network id: " + args[1]);
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
        java.util.List<AeSnapshotCollector.NetworkInfo> networks = AeSnapshotCollector.findNetworksBlocking(uuid, 10_000L, true);
        if (networks == null || networks.isEmpty()) return 0;
        for (int i = 0; i < networks.size(); i++) {
            refreshOneNetwork(uuid, i);
        }
        return networks.size();
    }

    private void handleRecipes(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            send(
                sender,
                EnumChatFormatting.YELLOW
                    + "/admweb recipes upload [snapshot|deep]   - Collect & upload recipes (snapshot = storage items only)");
            send(
                sender,
                EnumChatFormatting.YELLOW
                    + "/admweb recipes export   - Alias of upload (export recipes to the disk cache)");
            send(
                sender,
                EnumChatFormatting.YELLOW
                    + "/admweb recipes status   - Show server recipe cache status (incl. disk cache)");
            send(
                sender,
                EnumChatFormatting.YELLOW + "/admweb recipes clear    - Clear recipe cache (memory + disk, OP only)");
            return;
        }
        String action = args[1].toLowerCase();
        if ("upload".equals(action) || "export".equals(action)) {
            if (!canUseOpCommands(sender)) {
                send(sender, EnumChatFormatting.RED + "You need operator permission to trigger recipe upload.");
                return;
            }
            if (!(sender instanceof EntityPlayerMP)) {
                send(sender, EnumChatFormatting.RED + "This command can only be used by a player.");
                return;
            }
            EntityPlayerMP player = (EntityPlayerMP) sender;
            if (!Config.webRecipeUploadEnabled) {
                send(sender, EnumChatFormatting.RED + "Recipe upload is disabled in config.");
                return;
            }
            String scope = "full";
            String snapshotJson = "";
            if (args.length >= 3) {
                if ("snapshot".equalsIgnoreCase(args[2])) {
                    scope = "snapshot";
                    java.util.List<String> itemIds = com.imgood.textech.webae.icon.IconSnapshotItemCollector.collectItemIds();
                    snapshotJson = new com.google.gson.Gson().toJson(itemIds);
                    send(
                        sender,
                        EnumChatFormatting.AQUA + "Snapshot upload: " + itemIds.size() + " storage items → related recipes");
                } else if ("deep".equalsIgnoreCase(args[2])) {
                    scope = "deep";
                    send(sender, EnumChatFormatting.YELLOW + "Deep NEI item scan — may take several minutes.");
                }
            }
            if ("export".equals(action)) {
                send(sender, EnumChatFormatting.AQUA + "Exporting recipes to the server disk cache...");
            }
            com.imgood.textech.webae.network.PacketWebUploadTrigger trigger = new com.imgood.textech.webae.network.PacketWebUploadTrigger(
                com.imgood.textech.webae.network.PacketWebUploadTrigger.TYPE_RECIPES,
                snapshotJson,
                scope);
            com.imgood.textech.AdvanceDataMonitor.ADMCHANEL.sendTo(trigger, player);
            send(sender, EnumChatFormatting.GREEN + "Triggered NEI recipe upload (" + scope + ") on your client.");
        } else if ("status".equals(action)) {
            com.imgood.textech.webae.recipe.RecipeCacheStore.CacheStatus status = com.imgood.textech.webae.recipe.RecipeCacheStore
                .instance()
                .getStatus();
            send(sender, EnumChatFormatting.AQUA + "=== Recipe Cache Status ===");
            send(sender, EnumChatFormatting.WHITE + "  Total recipes: " + status.recipeCount);
            send(sender, EnumChatFormatting.WHITE + "  Handler types: " + status.handlerCount);
            send(sender, EnumChatFormatting.WHITE + "  Last update: " + formatTime(status.lastUpdateTime));
            if (status.lastDiskSave > 0) {
                send(sender, EnumChatFormatting.WHITE + "  Disk cache size: " + formatBytes(status.diskCacheSize));
                send(sender, EnumChatFormatting.WHITE + "  Last disk save: " + formatTime(status.lastDiskSave));
            } else {
                send(sender, EnumChatFormatting.GRAY + "  Disk cache: not yet saved");
            }
        } else if ("clear".equals(action)) {
            if (!canUseOpCommands(sender)) {
                send(sender, EnumChatFormatting.RED + "You need operator permission to clear the recipe cache.");
                return;
            }
            com.imgood.textech.webae.recipe.RecipeCacheStore.instance()
                .clear();
            sendFormatted(sender, EnumChatFormatting.GREEN, "adm.webconsole.recipes.cleared");
        } else {
            send(sender, EnumChatFormatting.RED + "Unknown recipes subcommand. Use: upload, export, status, clear");
        }
    }

    private void handleIcons(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, EnumChatFormatting.YELLOW + "/admweb icons upload [pack] [mode|all|snapshot]");
            send(sender, EnumChatFormatting.YELLOW + "/admweb icons upload snapshot [pack] [mode]");
            send(sender, EnumChatFormatting.YELLOW + "/admweb icons render <itemId> [pack] [mode]");
            send(sender, EnumChatFormatting.YELLOW + "/admweb icons verify <itemId> [pack]");
            send(sender, EnumChatFormatting.YELLOW + "/admweb icons import <folder> [pack]");
            send(sender, EnumChatFormatting.YELLOW + "/admweb icons import-nesql [pack] [subpath]");
            send(sender, EnumChatFormatting.YELLOW + "/admweb icons modes | status");
            return;
        }
        String action = args[1].toLowerCase();
        if ("modes".equals(action)) {
            send(sender, EnumChatFormatting.AQUA + "=== WebAE Icon Render Modes ===");
            for (IconRenderMode mode : IconRenderMode.allModes()) {
                String status = mode.isImplemented() ? EnumChatFormatting.GREEN + "ready"
                    : (mode.isDeprecated() ? EnumChatFormatting.GRAY + "deprecated" : EnumChatFormatting.GRAY + "planned");
                send(
                    sender,
                    EnumChatFormatting.WHITE + "  " + mode.getId() + " " + status + EnumChatFormatting.RESET + " — "
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
            send(sender, EnumChatFormatting.AQUA + "=== Icon Pack Status ===");
            if (packs.isEmpty()) {
                send(sender, EnumChatFormatting.GRAY + "  No icon packs installed in config/textech/web-icons/.");
            } else {
                for (IconStore.PackInfo p : packs) {
                    StringBuilder line = new StringBuilder();
                    line.append(EnumChatFormatting.WHITE)
                        .append("  ")
                        .append(p.packName)
                        .append(" — ")
                        .append(p.iconCount)
                        .append(" icons");
                    if (p.modeCounts != null && !p.modeCounts.isEmpty()) {
                        line.append(EnumChatFormatting.GRAY)
                            .append(" [");
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
                    send(sender, line.toString());
                }
            }
            send(sender, EnumChatFormatting.WHITE + "  iconCacheEnabled: " + Config.webIconCacheEnabled);
            send(sender, EnumChatFormatting.WHITE + "  iconUploadEnabled: " + Config.webIconUploadEnabled);
            send(sender, EnumChatFormatting.WHITE + "  iconPackEnabled: " + Config.webIconPackEnabled);
            send(sender, EnumChatFormatting.WHITE + "  missingIconQueue: " + IconMissingQueue.instance()
                .pendingCount());
        } else {
            send(sender, EnumChatFormatting.RED + "Unknown icons subcommand. Use: upload, render, verify, import, modes, status");
        }
    }

    private void handleIconsUpload(ICommandSender sender, String[] args) {
        if (!canUseOpCommands(sender) || !(sender instanceof EntityPlayerMP)) {
            send(sender, EnumChatFormatting.RED + "OP player required.");
            return;
        }
        if (!Config.webIconCacheEnabled || !Config.webIconUploadEnabled) {
            send(sender, EnumChatFormatting.RED + "Icon cache/upload disabled.");
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
            send(sender, EnumChatFormatting.RED + "Invalid pack or mode.");
            return;
        }
        java.util.List<String> itemIds = scope == IconExportScope.SNAPSHOT
            ? IconSnapshotItemCollector.collectItemIds()
            : new java.util.ArrayList<String>();
        if (scope == IconExportScope.SNAPSHOT && itemIds.isEmpty()) {
            send(sender, EnumChatFormatting.YELLOW + "No cached AE storage snapshots yet.");
            return;
        }
        IconMissingQueue.instance()
            .setProviderUuid(player.getUniqueID()
                .toString());
        AdvanceDataMonitor.ADMCHANEL.sendTo(new PacketWebIconExportScope(scope, itemIds), player);
        AdvanceDataMonitor.ADMCHANEL.sendTo(
            new com.imgood.textech.webae.network.PacketWebUploadTrigger(
                com.imgood.textech.webae.network.PacketWebUploadTrigger.TYPE_ICONS,
                packName,
                renderMode),
            player);
        send(
            sender,
            EnumChatFormatting.GREEN + "Icon upload started: pack=" + packName + " mode=" + renderMode + " scope="
                + scope.getId());
    }

    private void handleIconsRender(ICommandSender sender, String[] args) {
        if (!canUseOpCommands(sender) || !(sender instanceof EntityPlayerMP) || args.length < 3) {
            send(sender, EnumChatFormatting.RED + "Usage: /admweb icons render <itemId> [pack] [mode]");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        String itemId = args[2];
        String packName = args.length >= 4 ? args[3] : "default";
        String renderMode = args.length >= 5 ? args[4] : IconRenderMode.NEI.getId();
        java.util.List<String> ids = new java.util.ArrayList<String>();
        ids.add(itemId);
        IconMissingQueue.instance()
            .setProviderUuid(player.getUniqueID()
                .toString());
        AdvanceDataMonitor.ADMCHANEL.sendTo(new PacketWebIconExportScope(IconExportScope.LIST, ids), player);
        AdvanceDataMonitor.ADMCHANEL.sendTo(
            new com.imgood.textech.webae.network.PacketWebUploadTrigger(
                com.imgood.textech.webae.network.PacketWebUploadTrigger.TYPE_ICONS,
                packName,
                renderMode),
            player);
        send(sender, EnumChatFormatting.GREEN + "Rendering " + itemId);
    }

    private void handleIconsVerify(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP) || args.length < 3) {
            send(sender, EnumChatFormatting.RED + "Usage: /admweb icons verify <itemId> [pack]");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        AdvanceDataMonitor.ADMCHANEL.sendTo(
            new com.imgood.textech.webae.network.PacketWebUploadTrigger(
                com.imgood.textech.webae.network.PacketWebUploadTrigger.TYPE_ICON_VERIFY,
                args.length >= 4 ? args[3] : "default",
                args[2]),
            player);
        send(sender, EnumChatFormatting.GREEN + "Opening verify GUI for " + args[2]);
    }

    private void handleIconsImport(ICommandSender sender, String[] args) {
        if (!canUseOpCommands(sender) || args.length < 3) {
            send(sender, EnumChatFormatting.RED + "Usage: /admweb icons import <folder> [pack]");
            return;
        }
        String packName = args.length >= 4 ? args[3] : "default";
        java.io.File src = new java.io.File(args[2]);
        if (!IconStore.isValidPackName(packName) || !src.isDirectory()) {
            send(sender, EnumChatFormatting.RED + "Invalid pack or folder.");
            return;
        }
        java.io.File destDir = new java.io.File(IconStore.instance()
            .getBaseDir(), packName + java.io.File.separator + IconRenderMode.NEI.getId());
        destDir.mkdirs();
        int copied = 0;
        java.io.File[] files = src.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f == null || !f.getName()
                    .endsWith(".png")) continue;
                String base = f.getName()
                    .substring(0, f.getName()
                        .length() - 4);
                java.io.File out = new java.io.File(destDir, IconStore.sanitizeItemId(base.replace(':', '_')) + ".png");
                try {
                    java.nio.file.Files.copy(f.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
        send(sender, EnumChatFormatting.GREEN + "Imported " + copied + " PNGs to " + packName + "/nei/");
    }

    private void handleIconsImportNesql(ICommandSender sender, String[] args) {
        if (!canUseOpCommands(sender)) {
            send(sender, EnumChatFormatting.RED + "OP required.");
            return;
        }
        String packName = args.length >= 3 ? args[2] : "default";
        String subPath = args.length >= 4 ? args[3] : "";
        if (!IconStore.isValidPackName(packName)) {
            send(sender, EnumChatFormatting.RED + "Invalid pack name.");
            return;
        }
        String repoPath = WebAeLocalDataDir.resolveNesqlRepositoryPath();
        int copied = com.imgood.textech.webae.icon.NesqlIconImporter.importFromRepository(packName, subPath);
        send(
            sender,
            EnumChatFormatting.GREEN + "NESQL import: " + copied + " icons → " + packName + "/nei/ (from "
                + repoPath + ")");
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

    private void sendUsage(ICommandSender sender) {
        send(sender, EnumChatFormatting.YELLOW + "/admweb issue  - Generate your owner access token (requires a monitor)");
        send(sender, EnumChatFormatting.YELLOW + "/admweb guest <player>  - Issue a guest token to an online player");
        send(sender, EnumChatFormatting.YELLOW + "/admweb copy  - Copy your active Web token to clipboard");
        send(sender, EnumChatFormatting.YELLOW + "/admweb revoke [guestName]  - Revoke your token or a guest token");
        send(sender, EnumChatFormatting.YELLOW + "/admweb list  - List all active tokens (OP only)");
        send(sender, EnumChatFormatting.YELLOW + "/admweb reload  - Reload configuration (OP only)");
        send(sender, EnumChatFormatting.YELLOW + "/admweb recipes upload|export  - Upload NEI recipes (client only)");
        send(
            sender,
            EnumChatFormatting.YELLOW + "/admweb recipes status  - View recipe cache status (incl. disk cache)");
        send(sender, EnumChatFormatting.YELLOW + "/admweb recipes clear  - Clear recipe cache (OP only)");
        send(sender, EnumChatFormatting.YELLOW + "/admweb icons upload [pack] [mode|all]  - Upload item icons (client only)");
        send(sender, EnumChatFormatting.YELLOW + "/admweb icons modes  - List icon render modes");
        send(sender, EnumChatFormatting.YELLOW + "/admweb icons status  - View icon pack status");
        send(sender, EnumChatFormatting.YELLOW + "/admweb refresh [network]  - Force snapshot re-collect (OP only)");
        send(sender, EnumChatFormatting.YELLOW + "/admweb server status  - Show WebAE HTTP server state");
        send(sender, EnumChatFormatting.YELLOW + "/admweb server restart  - Restart HTTP server (OP only)");
    }

    private boolean canUseOpCommands(ICommandSender sender) {
        return sender == null || sender.canCommandSenderUseCommand(2, getCommandName());
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> filtered = new ArrayList<>();
            for (String s : SUBCOMMANDS) {
                if (s.startsWith(args[0].toLowerCase())) {
                    filtered.add(s);
                }
            }
            return filtered;
        }
        if (args.length == 2 && "server".equalsIgnoreCase(args[0])) {
            List<String> opts = Arrays.asList("status", "restart");
            List<String> filtered = new ArrayList<>();
            for (String s : opts) {
                if (s.startsWith(args[1].toLowerCase())) {
                    filtered.add(s);
                }
            }
            return filtered;
        }
        if (args.length == 2 && "icons".equalsIgnoreCase(args[0])) {
            List<String> opts = Arrays.asList("upload", "modes", "status");
            List<String> filtered = new ArrayList<>();
            for (String s : opts) {
                if (s.startsWith(args[1].toLowerCase())) {
                    filtered.add(s);
                }
            }
            return filtered;
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
            return filtered;
        }
        if (args.length == 2 && "revoke".equalsIgnoreCase(args[0])) {
            return null;
        }
        if (args.length == 2 && "guest".equalsIgnoreCase(args[0])) {
            return null;
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

    private void send(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
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
}
