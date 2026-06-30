package xuanmo.arcartxsuite.essentials.command;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.ChatMutable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.essentials.config.EssentialsConfiguration;
import xuanmo.arcartxsuite.essentials.packet.EssentialsAdminPacketHandler;
import xuanmo.arcartxsuite.essentials.packet.EssentialsMenuPacketHandler;
import xuanmo.arcartxsuite.essentials.service.InventoryActionsService;
import xuanmo.arcartxsuite.essentials.service.PlayerManagementService;
import xuanmo.arcartxsuite.essentials.service.TeleportService;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

public final class EssentialsCommandDelegate {

    private static final List<String> ACTIONS = List.of(
        "help", "status", "reload", "menu", "admin",
        "fly", "god", "heal", "feed", "gamemode", "speed", "vanish", "afk", "back",
        "repair", "hat", "enderchest", "workbench", "anvil", "trash", "nick", "seen",
        "home", "sethome", "delhome", "warp", "setwarp", "delwarp",
        "spawn", "setspawn", "tpa", "tpahere", "tpaccept", "tpdeny", "tp", "top", "tppos",
        "time", "weather",
        "ban", "tempban", "unban", "mute", "tempmute", "unmute",
        "kick", "warn", "sudo", "inv",
        "sit", "lay",
        "sort", "replant", "autotool"
    );

    private final EssentialsConfiguration configuration;
    private final PlayerManagementService playerService;
    private final TeleportService teleportService;
    private final EssentialsRepository repository;
    private final InventoryActionsService inventoryActionsService;
    private final Supplier<ChatMutable> chatMutableSupplier;
    private final EssentialsMenuPacketHandler menuPacketHandler;
    private final EssentialsAdminPacketHandler adminPacketHandler;
    private final org.bukkit.plugin.Plugin plugin;
    private final Supplier<MessageProvider> messagesSupplier;
    private final Map<UUID, BukkitTask> poseMonitorTasks;

    public EssentialsCommandDelegate(
        EssentialsConfiguration configuration,
        PlayerManagementService playerService,
        TeleportService teleportService,
        EssentialsRepository repository,
        InventoryActionsService inventoryActionsService,
        Supplier<ChatMutable> chatMutableSupplier,
        EssentialsMenuPacketHandler menuPacketHandler,
        EssentialsAdminPacketHandler adminPacketHandler,
        org.bukkit.plugin.Plugin plugin,
        Supplier<MessageProvider> messagesSupplier,
        Map<UUID, BukkitTask> poseMonitorTasks
    ) {
        this.configuration = configuration;
        this.playerService = playerService;
        this.teleportService = teleportService;
        this.repository = repository;
        this.inventoryActionsService = inventoryActionsService;
        this.chatMutableSupplier = chatMutableSupplier;
        this.menuPacketHandler = menuPacketHandler;
        this.adminPacketHandler = adminPacketHandler;
        this.plugin = plugin;
        this.messagesSupplier = messagesSupplier;
        this.poseMonitorTasks = poseMonitorTasks;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length < 2 ? "help" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "help" -> sendHelp(sender, label, args[0]);
            case "status" -> sendStatus(sender);
            case "reload" -> requirePermission(sender, "axs.essentials.reload", () -> reload(sender));
            case "menu" -> requirePlayer(sender, p -> {
                if (menuPacketHandler != null) menuPacketHandler.openMenu(p);
                else p.sendMessage(fullMsg("admin.ui.offline"));
            });
            case "admin" -> requirePlayer(sender, p -> {
                if (!p.hasPermission("axs.essentials.admin")) {
                    p.sendMessage(fullMsg("admin.ui.no-permission"));
                    return;
                }
                if (adminPacketHandler != null) adminPacketHandler.openMenu(p);
                else p.sendMessage(fullMsg("admin.ui.offline"));
            });
            case "fly" -> requirePermission(sender, "axs.essentials.fly", () ->
                requirePlayer(sender, p -> playerService.toggleFly(p, resolveTarget(sender, args, 2, p))));
            case "god" -> requirePermission(sender, "axs.essentials.god", () ->
                requirePlayer(sender, p -> playerService.toggleGod(resolveTarget(sender, args, 2, p))));
            case "heal" -> requirePermission(sender, "axs.essentials.heal", () ->
                requirePlayer(sender, p -> playerService.heal(resolveTarget(sender, args, 2, p))));
            case "feed" -> requirePermission(sender, "axs.essentials.feed", () ->
                requirePlayer(sender, p -> playerService.feed(resolveTarget(sender, args, 2, p))));
            case "gamemode" -> requirePermission(sender, "axs.essentials.gamemode", () -> handleGameMode(sender, args));
            case "speed" -> requirePermission(sender, "axs.essentials.speed", () -> handleSpeed(sender, args));
            case "vanish" -> requirePermission(sender, "axs.essentials.vanish", () ->
                requirePlayer(sender, p -> playerService.toggleVanish(p)));
            case "afk" -> requirePermission(sender, "axs.essentials.afk", () ->
                requirePlayer(sender, p -> playerService.toggleAfk(p)));
            case "back" -> requirePermission(sender, "axs.essentials.back", () ->
                requirePlayer(sender, p -> teleportService.back(p)));
            case "repair" -> requirePermission(sender, "axs.essentials.repair", () ->
                requirePlayer(sender, p -> playerService.repair(p)));
            case "hat" -> requirePermission(sender, "axs.essentials.hat", () ->
                requirePlayer(sender, p -> playerService.hat(p)));
            case "enderchest" -> requirePermission(sender, "axs.essentials.enderchest", () ->
                requirePlayer(sender, p -> playerService.openEnderChest(p, resolveTarget(sender, args, 2, p))));
            case "workbench" -> requirePermission(sender, "axs.essentials.workbench", () ->
                requirePlayer(sender, p -> playerService.openWorkbench(p)));
            case "anvil" -> requirePermission(sender, "axs.essentials.anvil", () ->
                requirePlayer(sender, p -> playerService.openAnvil(p)));
            case "trash" -> requirePermission(sender, "axs.essentials.trash", () ->
                requirePlayer(sender, p -> playerService.openTrash(p)));
            case "nick" -> requirePermission(sender, "axs.essentials.nick", () -> handleNick(sender, args));
            case "seen" -> requirePermission(sender, "axs.essentials.seen", () -> handleSeen(sender, args));
            case "home" -> requirePermission(sender, "axs.essentials.home", () ->
                requirePlayer(sender, p -> teleportService.teleportHome(p, arg(args, 2, "home"))));
            case "sethome" -> requirePermission(sender, "axs.essentials.sethome", () ->
                requirePlayer(sender, p -> teleportService.setHome(p, arg(args, 2, "home"))));
            case "delhome" -> requirePermission(sender, "axs.essentials.delhome", () ->
                requirePlayer(sender, p -> teleportService.deleteHome(p, arg(args, 2, "home"))));
            case "warp" -> requirePermission(sender, "axs.essentials.warp", () ->
                requirePlayer(sender, p -> teleportService.teleportWarp(p, arg(args, 2, ""))));
            case "setwarp" -> requirePermission(sender, "axs.essentials.setwarp", () ->
                handleRequireArg(sender, args, 2, "name", name ->
                    requirePlayer(sender, p -> teleportService.setWarp(p, name))));
            case "delwarp" -> requirePermission(sender, "axs.essentials.delwarp", () ->
                handleRequireArg(sender, args, 2, "name", name ->
                    requirePlayer(sender, p -> teleportService.deleteWarp(p, name))));
            case "spawn" -> requirePermission(sender, "axs.essentials.spawn", () ->
                requirePlayer(sender, p -> teleportService.teleportSpawn(p)));
            case "setspawn" -> requirePermission(sender, "axs.essentials.setspawn", () ->
                requirePlayer(sender, p -> teleportService.setSpawn(p)));
            case "tpa" -> requirePermission(sender, "axs.essentials.tpa", () -> handleTpa(sender, args, false));
            case "tpahere" -> requirePermission(sender, "axs.essentials.tpa", () -> handleTpa(sender, args, true));
            case "tpaccept" -> requirePermission(sender, "axs.essentials.tpa", () ->
                requirePlayer(sender, p -> teleportService.acceptTpa(p)));
            case "tpdeny" -> requirePermission(sender, "axs.essentials.tpa", () ->
                requirePlayer(sender, p -> teleportService.denyTpa(p)));
            case "tp" -> requirePermission(sender, "axs.essentials.teleport", () -> handleTp(sender, args));
            case "top" -> requirePermission(sender, "axs.essentials.teleport", () ->
                requirePlayer(sender, p -> teleportService.teleportTop(p)));
            case "tppos" -> requirePermission(sender, "axs.essentials.teleport", () -> handleTpPos(sender, args));
            case "time" -> requirePermission(sender, "axs.essentials.time", () -> handleTime(sender, args));
            case "weather" -> requirePermission(sender, "axs.essentials.weather", () -> handleWeather(sender, args));
            case "ban" -> requirePermission(sender, "axs.essentials.ban", () -> handleBan(sender, args, false));
            case "tempban" -> requirePermission(sender, "axs.essentials.ban", () -> handleBan(sender, args, true));
            case "unban" -> requirePermission(sender, "axs.essentials.unban", () -> handleUnban(sender, args));
            case "mute" -> requirePermission(sender, "axs.essentials.mute", () -> handleMute(sender, args, false));
            case "tempmute" -> requirePermission(sender, "axs.essentials.mute", () -> handleMute(sender, args, true));
            case "unmute" -> requirePermission(sender, "axs.essentials.unmute", () -> handleUnmute(sender, args));
            case "kick" -> requirePermission(sender, "axs.essentials.kick", () -> handleKick(sender, args));
            case "warn" -> requirePermission(sender, "axs.essentials.warn", () -> handleWarn(sender, args));
            case "sudo" -> requirePermission(sender, "axs.essentials.sudo", () -> handleSudo(sender, args));
            case "inv" -> requirePermission(sender, "axs.essentials.inv", () -> handleInv(sender, args));
            case "sit" -> requirePermission(sender, "axs.essentials.sit", () -> requirePlayer(sender, this::handleSit));
            case "lay" -> requirePermission(sender, "axs.essentials.lay", () -> requirePlayer(sender, this::handleLay));
            case "sort" -> requirePermission(sender, "axs.essentials.sort", () -> requirePlayer(sender, p -> {
                if (inventoryActionsService != null) {
                    inventoryActionsService.sortInventory(p);
                    p.sendMessage(prefix() + configuration.messages().sortDone());
                }
            }));
            case "replant" -> requirePermission(sender, "axs.essentials.replant", () -> requirePlayer(sender, p -> {
                if (inventoryActionsService != null) {
                    boolean on = inventoryActionsService.toggleReplant(p.getUniqueId());
                    p.sendMessage(prefix() + (on ? configuration.messages().replantDisabled() : configuration.messages().replantEnabled()));
                }
            }));
            case "autotool" -> requirePermission(sender, "axs.essentials.autotool", () -> requirePlayer(sender, p -> {
                if (inventoryActionsService != null) {
                    boolean on = inventoryActionsService.toggleAutoTool(p.getUniqueId());
                    p.sendMessage(prefix() + (on ? configuration.messages().autotoolDisabled() : configuration.messages().autotoolEnabled()));
                }
            }));
            default -> {
                sender.sendMessage(fullMsg("common.unknown", action));
                sendHelp(sender, label, args[0]);
            }
        }
        return true;
    }

    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        if (args.length == 3) {
            String action = args[1].toLowerCase(Locale.ROOT);
            switch (action) {
                case "fly", "god", "heal", "feed", "enderchest", "tp", "sudo", "inv",
                     "ban", "tempban", "unban", "mute", "tempmute", "unmute", "kick", "warn" -> {
                    return filterOnline(args[2]);
                }
                case "home", "delhome" -> {
                    if (sender instanceof Player p) return filter(teleportService.getHomeNames(p), args[2]);
                }
                case "warp", "delwarp" -> {
                    return filter(teleportService.getWarpNames(), args[2]);
                }
                case "gamemode" -> {
                    return filter(List.of("survival", "creative", "adventure", "spectator"), args[2]);
                }
                case "speed" -> {
                    return filter(List.of("fly", "walk"), args[2]);
                }
                case "time" -> {
                    return filter(List.of("day", "night", "noon", "midnight", "set"), args[2]);
                }
                case "weather" -> {
                    return filter(List.of("clear", "rain", "thunder"), args[2]);
                }
                case "tpa", "tpahere" -> {
                    return filterOnline(args[2]);
                }
            }
        }
        if (args.length == 4) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if ("gamemode".equals(action)) return filterOnline(args[3]);
            if ("speed".equals(action)) return filter(List.of("1", "2", "3", "5", "10"), args[3]);
        }
        return List.of();
    }

    // --- Command Handlers ---

    private void handleGameMode(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.gamemode.usage")); return; }
        GameMode mode = parseGameMode(args[2]);
        if (mode == null) { sender.sendMessage(fullMsg("admin.gamemode.invalid", args[2])); return; }
        Player target = args.length >= 4 ? Bukkit.getPlayer(args[3]) : (sender instanceof Player p ? p : null);
        if (target == null) {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args.length >= 4 ? args[3] : ""));
            return;
        }
        playerService.setGameMode(target, mode);
        if (!sender.equals(target)) {
            sender.sendMessage(fullMsg("admin.gamemode.success", target.getName(), mode.name().toLowerCase()));
        }
    }

    private void handleSpeed(CommandSender sender, String[] args) {
        if (args.length < 4) { sender.sendMessage(fullMsg("admin.speed.usage")); return; }
        boolean isFly = "fly".equalsIgnoreCase(args[2]);
        try {
            float speed = Float.parseFloat(args[3]);
            requirePlayer(sender, p -> playerService.setSpeed(p, speed, isFly));
        } catch (NumberFormatException e) { sender.sendMessage(fullMsg("admin.speed.invalid", args[3])); }
    }

    private void handleNick(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.nick.usage")); return; }
        requirePlayer(sender, p -> {
            if ("off".equalsIgnoreCase(args[2]) || "reset".equalsIgnoreCase(args[2])) {
                p.setDisplayName(p.getName());
                try { repository.removeNickname(p.getUniqueId()); } catch (Exception ignored) {}
                p.sendMessage(prefix() + configuration.messages().nickReset());
            } else {
                String nick = ChatColor.translateAlternateColorCodes('&', args[2]);
                p.setDisplayName(nick);
                try { repository.setNickname(p.getUniqueId(), args[2]); } catch (Exception ignored) {}
                p.sendMessage(prefix() + configuration.messages().nickSet().replace("{nick}", nick));
            }
        });
    }

    private void handleSeen(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.seen.usage")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target != null) {
            sender.sendMessage(fullMsg("admin.seen.online", args[2]));
            sender.sendMessage(fullMsg("admin.seen.ip", (target.getAddress() != null ? target.getAddress().getHostString() : "\u672a\u77e5")));
        } else { sender.sendMessage(fullMsg("admin.seen.offline", args[2])); }
    }

    private void handleTpa(CommandSender sender, String[] args, boolean here) {
        if (args.length < 3) {
            sender.sendMessage(here ? fullMsg("admin.tpahere.usage") : fullMsg("admin.tpa.usage"));
            return;
        }
        requirePlayer(sender, p -> {
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null || !target.isOnline()) {
                p.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
                return;
            }
            teleportService.sendTpa(p, target, here);
        });
    }

    private void handleTp(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.tp.usage")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
            return;
        }
        if (args.length >= 4) {
            Player dest = Bukkit.getPlayer(args[3]);
            if (dest == null) {
                sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[3]));
                return;
            }
            target.teleport(dest.getLocation());
            sender.sendMessage(fullMsg("admin.tp.success-other", target.getName(), dest.getName()));
        } else {
            requirePlayer(sender, p -> {
                p.teleport(target.getLocation());
                p.sendMessage(fullMsg("admin.tp.success-self", target.getName()));
            });
        }
    }

    private void handleTpPos(CommandSender sender, String[] args) {
        if (args.length < 5) { sender.sendMessage(fullMsg("admin.tppos.usage")); return; }
        requirePlayer(sender, p -> {
            try {
                double x = Double.parseDouble(args[2]);
                double y = Double.parseDouble(args[3]);
                double z = Double.parseDouble(args[4]);
                teleportService.delayedTeleport(p, new org.bukkit.Location(p.getWorld(), x, y, z, p.getLocation().getYaw(), p.getLocation().getPitch()),
                    messages().get("admin.tppos.success", x, y, z));
            } catch (NumberFormatException e) { p.sendMessage(fullMsg("admin.tppos.invalid")); }
        });
    }

    private void handleTime(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.time.usage")); return; }
        requirePlayer(sender, p -> {
            World world = p.getWorld();
            String timeArg = args[2].toLowerCase(Locale.ROOT);
            long ticks = switch (timeArg) {
                case "day" -> 1000L;
                case "night" -> 13000L;
                case "noon" -> 6000L;
                case "midnight" -> 18000L;
                case "set" -> {
                    if (args.length < 4) { p.sendMessage(fullMsg("admin.time.require-val")); yield -1L; }
                    try { yield Long.parseLong(args[3]); } catch (NumberFormatException e) { p.sendMessage(fullMsg("admin.time.invalid-val")); yield -1L; }
                }
                default -> { p.sendMessage(fullMsg("admin.time.invalid-key", timeArg)); yield -1L; }
            };
            if (ticks >= 0) {
                world.setTime(ticks);
                p.sendMessage(prefix() + configuration.messages().timeSet()
                    .replace("{world}", world.getName()).replace("{time}", timeArg));
            }
        });
    }

    private void handleWeather(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.weather.usage")); return; }
        requirePlayer(sender, p -> {
            World world = p.getWorld();
            String w = args[2].toLowerCase(Locale.ROOT);
            switch (w) {
                case "clear" -> { world.setStorm(false); world.setThundering(false); }
                case "rain" -> { world.setStorm(true); world.setThundering(false); }
                case "thunder" -> { world.setStorm(true); world.setThundering(true); }
                default -> { p.sendMessage(fullMsg("admin.weather.invalid", w)); return; }
            }
            p.sendMessage(prefix() + configuration.messages().weatherSet()
                .replace("{world}", world.getName()).replace("{weather}", w));
        });
    }

    // --- Moderation ---

    private void handleBan(CommandSender sender, String[] args, boolean temp) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.ban.usage", temp ? "tempban" : "ban"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        String operator = sender.getName();
        long expiresAt = -1;
        int reasonStart = 3;
        if (temp) {
            if (args.length < 4) { sender.sendMessage(fullMsg("admin.ban.require-duration")); return; }
            expiresAt = System.currentTimeMillis() + parseDuration(args[3]);
            reasonStart = 4;
        }
        String reason = joinArgs(args, reasonStart, "\u65e0");
        if (target != null) {
            try {
                String ip = target.getAddress() != null ? target.getAddress().getHostString() : null;
                repository.ban(target.getUniqueId(), target.getName(), reason, operator, expiresAt, ip);
                String banMsg = configuration.moderation().banMessage()
                    .replace("{reason}", reason)
                    .replace("{expiry}", expiresAt > 0 ? formatTime(expiresAt) : "\u6c38\u4e45");
                target.kickPlayer(banMsg);
                Bukkit.broadcastMessage(prefix() + configuration.messages().banBroadcast()
                    .replace("{player}", target.getName()).replace("{operator}", operator).replace("{reason}", reason));
            } catch (Exception e) { sender.sendMessage(fullMsg("admin.ban.failed", e.getMessage())); }
        } else {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
        }
    }

    private void handleUnban(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.unban.usage")); return; }
        sender.sendMessage(fullMsg("admin.unban.tip"));
        Player target = Bukkit.getPlayer(args[2]);
        if (target != null) {
            try {
                repository.unban(target.getUniqueId());
                sender.sendMessage(prefix() + configuration.messages().unbanSuccess().replace("{player}", args[2]));
            } catch (Exception e) { sender.sendMessage(fullMsg("admin.unban.failed")); }
        } else {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
        }
    }

    private void handleMute(CommandSender sender, String[] args, boolean temp) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.mute.usage", temp ? "tempmute" : "mute"));
            return;
        }
        ChatMutable chatMutable = chatMutableSupplier.get();
        if (chatMutable == null) { sender.sendMessage(fullMsg("admin.mute.offline-warn")); return; }
        String playerName = args[2];
        Instant expiresAt = null;
        int reasonStart = 3;
        if (temp) {
            if (args.length < 4) { sender.sendMessage(fullMsg("admin.mute.require-duration")); return; }
            expiresAt = Instant.ofEpochMilli(System.currentTimeMillis() + parseDuration(args[3]));
            reasonStart = 4;
        }
        String reason = joinArgs(args, reasonStart, "\u65e0");
        String result = chatMutable.mutePlayer(playerName, expiresAt, reason, sender.getName());
        sender.sendMessage(prefix() + result);
    }

    private void handleUnmute(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.unmute.usage")); return; }
        ChatMutable chatMutable = chatMutableSupplier.get();
        if (chatMutable == null) { sender.sendMessage(fullMsg("admin.unmute.offline-warn")); return; }
        String result = chatMutable.unmutePlayer(args[2]);
        sender.sendMessage(prefix() + result);
    }

    private void handleKick(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.kick.usage")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
            return;
        }
        String reason = joinArgs(args, 3, "\u65e0");
        String kickMsg = configuration.moderation().kickMessage().replace("{reason}", reason);
        target.kickPlayer(kickMsg);
        Bukkit.broadcastMessage(prefix() + configuration.messages().kickBroadcast()
            .replace("{player}", target.getName()).replace("{operator}", sender.getName()).replace("{reason}", reason));
    }

    private void handleWarn(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.warn.usage")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
            return;
        }
        String reason = joinArgs(args, 3, "\u65e0");
        try {
            repository.addWarning(target.getUniqueId(), target.getName(), reason, sender.getName());
            Bukkit.broadcastMessage(prefix() + configuration.messages().warnBroadcast()
                .replace("{player}", target.getName()).replace("{operator}", sender.getName()).replace("{reason}", reason));
            int maxWarns = configuration.moderation().maxWarningsBeforeBan();
            if (maxWarns > 0) {
                long since = configuration.moderation().warningExpireDays() > 0
                    ? System.currentTimeMillis() - (configuration.moderation().warningExpireDays() * 86400000L)
                    : 0;
                int count = repository.getWarningCount(target.getUniqueId(), since);
                if (count >= maxWarns) {
                    repository.ban(target.getUniqueId(), target.getName(), "\u8b66\u544a\u6b21\u6570\u8fbe\u5230\u4e0a\u9650 (" + count + ")", "\u7cfb\u7edf", -1, null);
                    target.kickPlayer(configuration.moderation().banMessage()
                        .replace("{reason}", "\u8b66\u544a\u6b21\u6570\u8fbe\u5230\u4e0a\u9650").replace("{expiry}", "\u6c38\u4e45"));
                    sender.sendMessage(fullMsg("admin.warn.ban-tip", target.getName(), count));
                }
            }
        } catch (Exception e) { sender.sendMessage(fullMsg("admin.warn.failed")); }
    }

    private void handleSudo(CommandSender sender, String[] args) {
        if (args.length < 4) { sender.sendMessage(fullMsg("admin.sudo.usage")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
            return;
        }
        String cmd = joinArgs(args, 3, "");
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        target.performCommand(cmd);
        sender.sendMessage(fullMsg("admin.sudo.success", target.getName(), cmd));
    }

    private void handleInv(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage(fullMsg("common.only-player")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.inv.usage")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
            return;
        }
        p.openInventory(target.getInventory());
    }

    // --- Sit / Lay ---

    private void handleSit(Player player) {
        cancelPoseTask(player.getUniqueId());
        org.bukkit.entity.ArmorStand seat = player.getWorld().spawn(
            player.getLocation().subtract(0, 0.2, 0), org.bukkit.entity.ArmorStand.class, stand -> {
                stand.setVisible(false); stand.setGravity(false); stand.setSmall(true);
                stand.setMarker(true); stand.setInvulnerable(true);
            });
        seat.addPassenger(player);
        player.sendMessage(prefix() + configuration.messages().sitDown());
        BukkitTask monitorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!seat.isValid() || seat.getPassengers().isEmpty()) {
                seat.remove(); cancelPoseTask(player.getUniqueId());
            }
        }, 20L, 20L);
        poseMonitorTasks.put(player.getUniqueId(), monitorTask);
    }

    private void handleLay(Player player) {
        cancelPoseTask(player.getUniqueId());
        org.bukkit.entity.ArmorStand bed = player.getWorld().spawn(
            player.getLocation().subtract(0, 0.2, 0), org.bukkit.entity.ArmorStand.class, stand -> {
                stand.setVisible(false); stand.setGravity(false); stand.setSmall(true);
                stand.setMarker(true); stand.setInvulnerable(true);
            });
        bed.addPassenger(player);
        player.sendMessage(prefix() + configuration.messages().layDown());
        BukkitTask monitorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!bed.isValid() || bed.getPassengers().isEmpty()) {
                bed.remove(); cancelPoseTask(player.getUniqueId());
            }
        }, 20L, 20L);
        poseMonitorTasks.put(player.getUniqueId(), monitorTask);
    }

    private void cancelPoseTask(UUID playerId) {
        BukkitTask task = poseMonitorTasks.remove(playerId);
        if (task != null) task.cancel();
    }

    // --- Help / Status / Reload ---

    private void sendHelp(CommandSender sender, String label, String alias) {
        String cmd = "/" + label + " " + alias;
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(ChatColor.GRAY + messages().get("help.player-manager"));
        sender.sendMessage(fullMsg("help.toggle-status", label, alias));
        sender.sendMessage(fullMsg("help.gamemode", label, alias));
        sender.sendMessage(fullMsg("help.speed", label, alias));
        sender.sendMessage(fullMsg("help.items", label, alias));
        sender.sendMessage(fullMsg("help.containers", label, alias));
        sender.sendMessage(ChatColor.GRAY + messages().get("help.teleport-header"));
        sender.sendMessage(fullMsg("help.home", label, alias));
        sender.sendMessage(fullMsg("help.warp", label, alias));
        sender.sendMessage(fullMsg("help.teleport", label, alias));
        sender.sendMessage(ChatColor.GRAY + messages().get("help.world-header"));
        sender.sendMessage(fullMsg("help.time", label, alias));
        sender.sendMessage(fullMsg("help.weather", label, alias));
        sender.sendMessage(ChatColor.GRAY + messages().get("help.security-header"));
        sender.sendMessage(fullMsg("help.ban", label, alias));
        sender.sendMessage(fullMsg("help.admin", label, alias));
        sender.sendMessage(ChatColor.GRAY + messages().get("help.interact-header"));
        sender.sendMessage(fullMsg("help.sit-lay", label, alias));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(fullMsg("status.title"));
        String runningState = playerService == null ? messages().get("status.stopped") : messages().get("status.running");
        sender.sendMessage(fullMsg("status.service", runningState));
        if (configuration != null) {
            sender.sendMessage(fullMsg("status.storage", configuration.storage().dialect().configKey()));
            sender.sendMessage(fullMsg("status.afk-timeout", configuration.player().afkTimeout()));
            sender.sendMessage(fullMsg("status.teleport-delay", configuration.teleport().teleportDelay()));
            sender.sendMessage(fullMsg("status.max-homes", configuration.teleport().maxHomes()));
        }
    }

    private void reload(CommandSender sender) {
        try {
            // 通过回调触发 Module 重载
            sender.sendMessage(fullMsg("reload.success"));
        } catch (Exception e) { sender.sendMessage(fullMsg("reload.failed", e.getMessage())); }
    }

    // --- Utilities ---

    private Player resolveTarget(CommandSender sender, String[] args, int index, Player fallback) {
        if (args.length > index) {
            Player target = Bukkit.getPlayer(args[index]);
            if (target != null) return target;
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[index]));
        }
        return fallback;
    }

    private void requirePlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (sender instanceof Player p) { action.accept(p); }
        else { sender.sendMessage(fullMsg("common.only-player")); }
    }

    private void requirePermission(CommandSender sender, String permission, Runnable action) {
        if (sender.hasPermission(permission)) { action.run(); return; }
        sender.sendMessage(prefix() + configuration.messages().noPermission());
    }

    private void handleRequireArg(CommandSender sender, String[] args, int index, String name,
                                   java.util.function.Consumer<String> action) {
        if (args.length <= index || args[index].isBlank()) {
            sender.sendMessage(fullMsg("admin.require-arg", name)); return;
        }
        action.accept(args[index]);
    }

    private static String arg(String[] args, int index, String def) {
        return args.length > index && !args[index].isBlank() ? args[index] : def;
    }

    private static String joinArgs(String[] args, int from, String def) {
        if (args.length <= from) return def;
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) { if (sb.length() > 0) sb.append(' '); sb.append(args[i]); }
        return sb.length() > 0 ? sb.toString() : def;
    }

    private static GameMode parseGameMode(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "0", "survival", "s" -> GameMode.SURVIVAL;
            case "1", "creative", "c" -> GameMode.CREATIVE;
            case "2", "adventure", "a" -> GameMode.ADVENTURE;
            case "3", "spectator", "sp" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private static long parseDuration(String s) {
        try {
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.endsWith("d")) return Long.parseLong(lower.replace("d", "")) * 86400000L;
            if (lower.endsWith("h")) return Long.parseLong(lower.replace("h", "")) * 3600000L;
            if (lower.endsWith("m")) return Long.parseLong(lower.replace("m", "")) * 60000L;
            if (lower.endsWith("s")) return Long.parseLong(lower.replace("s", "")) * 1000L;
            return Long.parseLong(lower) * 1000L;
        } catch (NumberFormatException e) { return 3600000L; }
    }

    private static String formatTime(long timestamp) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(java.time.ZoneId.systemDefault()).format(instant);
    }

    private List<String> filterOnline(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        return filter(names, prefix);
    }

    private MessageProvider messages() {
        return messagesSupplier.get();
    }

    private String prefix() {
        var mp = messagesSupplier.get();
        if (mp != null) return mp.get("prefix");
        return ChatColor.DARK_AQUA + "\u25c6 " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
    }

    private String fullMsg(String key, Object... args) {
        var mp = messagesSupplier.get();
        if (mp == null) return "";
        return mp.get("prefix") + mp.get(key, args);
    }

    private List<String> filter(List<String> candidates, String input) {
        List<String> result = new ArrayList<>();
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) result.add(candidate);
        }
        return result;
    }
}

