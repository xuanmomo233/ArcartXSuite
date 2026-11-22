package xuanmo.arcartxsuite.essentials.command;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Function;
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
import xuanmo.arcartxsuite.api.RegistrationContext.CommandRegistrationInfo;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.essentials.config.EssentialsConfiguration;
import xuanmo.arcartxsuite.essentials.packet.EssentialsAdminPacketHandler;
import xuanmo.arcartxsuite.essentials.packet.EssentialsMenuPacketHandler;
import xuanmo.arcartxsuite.essentials.service.InventoryActionsService;
import xuanmo.arcartxsuite.essentials.service.PlayerManagementService;
import xuanmo.arcartxsuite.essentials.service.TeleportService;
import xuanmo.arcartxsuite.essentials.service.EssentialsWork;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;
import xuanmo.arcartxsuite.essentials.service.InventoryInspection;
import xuanmo.arcartxsuite.essentials.service.ModerationService;
import xuanmo.arcartxsuite.essentials.service.WarpDirectory;

/**
 * Essentials 模块的命令委派器。
 * <p>
 * 负责解析并分发 {@code /essentials <action>} 形式的子命令，
 * 涵盖玩家管理、传送、世界控制、安全管理、交互与工具等全部功能。
 * 通过权限校验、参数校验后将请求转发至对应的服务层
 * （{@link PlayerManagementService}、{@link TeleportService} 等）。
 */
public final class EssentialsCommandDelegate {
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback poseCallbacks =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(() -> {});
    private boolean closed;

    public void shutdown() { closed = true; poseCallbacks.close(); }

    public java.util.concurrent.CompletionStage<Void> stopped() { return poseCallbacks.stopped(); }

    private Runnable poseCallback(Runnable operation) {
        Supplier<Void> invocation = () -> { operation.run(); return null; };
        return () -> poseCallbacks.call(invocation, null);
    }

    private static final List<String> ACTIONS = List.of(
        "help", "status", "reload", "menu", "admin", "conflicts",
        "fly", "god", "heal", "feed", "gamemode", "speed", "vanish", "afk", "back",
        "repair", "hat", "enderchest", "workbench", "anvil", "trash", "nick", "seen",
        "home", "sethome", "delhome", "warp", "warps", "warpfavorite", "setwarp", "delwarp",
        "spawn", "setspawn", "tpa", "tpahere", "tpaccept", "tpdeny", "tptoggle", "tpcancel", "tp", "top", "tppos", "unstuck",
        "time", "weather",
        "ban", "tempban", "unban", "banhistory", "mute", "tempmute", "unmute", "chatreview",
        "kick", "warn", "sudo", "inv",
        "sit", "lay",
        "replant", "autotool"
    );

    private final EssentialsConfiguration configuration;
    private final PlayerManagementService playerService;
    private final TeleportService teleportService;
    private final EssentialsRepository repository;
    private final EssentialsWork work;
    private final InventoryActionsService inventoryActionsService;
    private final Supplier<ChatMutable> chatMutableSupplier;
    private final Supplier<EssentialsMenuPacketHandler> menuPacketHandler;
    private final Supplier<EssentialsAdminPacketHandler> adminPacketHandler;
    private final ModerationService moderation;
    private final InventoryInspection inventoryInspection;
    private final Function<String, CommandRegistrationInfo> commandInspector;
    private final org.bukkit.plugin.Plugin plugin;
    private final Supplier<MessageProvider> messagesSupplier;
    private final Supplier<java.util.concurrent.CompletionStage<Void>> reloadAction;
    private final Map<UUID, BukkitTask> poseMonitorTasks;
    /** 坐下姿势生成的坐垫实体（玩家 UUID → ArmorStand），用于退出/重坐/卸载时清理，防实体泄漏 */
    private final Map<UUID, org.bukkit.entity.Entity> seatEntities;

    /**
     * 构造命令委派器，注入所有依赖的服务与组件。
     *
     * @param configuration          模块配置
     * @param playerService          玩家管理服务
     * @param teleportService        传送服务
     * @param repository             数据仓储
     * @param inventoryActionsService 背包操作服务
     * @param chatMutableSupplier    ChatMutable 能力延迟供应者（禁言/解禁）
     * @param menuPacketHandler      菜单 UI 包处理器
     * @param adminPacketHandler     管理 UI 包处理器
     * @param plugin                 宿主插件实例
     * @param messagesSupplier       消息提供者延迟供应者
     * @param poseMonitorTasks       姿势监控任务映射（坐/躺）
     * @param seatEntities           坐下姿势的坐垫实体映射（与模块共享，用于统一清理）
     */
    public EssentialsCommandDelegate(
        EssentialsConfiguration configuration,
        PlayerManagementService playerService,
        TeleportService teleportService,
        EssentialsRepository repository,
        InventoryActionsService inventoryActionsService,
        Supplier<ChatMutable> chatMutableSupplier,
        Supplier<EssentialsMenuPacketHandler> menuPacketHandler,
        Supplier<EssentialsAdminPacketHandler> adminPacketHandler,
        ModerationService moderation,
        InventoryInspection inventoryInspection,
        Function<String, CommandRegistrationInfo> commandInspector,
        org.bukkit.plugin.Plugin plugin,
        Supplier<MessageProvider> messagesSupplier,
        Supplier<java.util.concurrent.CompletionStage<Void>> reloadAction,
        Map<UUID, BukkitTask> poseMonitorTasks,
        Map<UUID, org.bukkit.entity.Entity> seatEntities,
        EssentialsWork work
    ) {
        this.configuration = configuration;
        this.playerService = playerService;
        this.teleportService = teleportService;
        this.repository = repository;
        this.work = work;
        this.inventoryActionsService = inventoryActionsService;
        this.chatMutableSupplier = chatMutableSupplier;
        this.menuPacketHandler = menuPacketHandler;
        this.adminPacketHandler = adminPacketHandler;
        this.moderation = moderation;
        this.inventoryInspection = inventoryInspection;
        this.commandInspector = commandInspector;
        this.plugin = plugin;
        this.messagesSupplier = messagesSupplier;
        this.reloadAction = reloadAction;
        this.poseMonitorTasks = poseMonitorTasks;
        this.seatEntities = seatEntities;
    }

    /**
     * 处理 {@code /essentials} 命令，根据子动作分发到对应处理器。
     *
     * @param sender 命令发送者
     * @param label  命令标签
     * @param args   命令参数（args[0] 为模块名，args[1] 为动作）
     * @return {@code true} 表示命令已处理
     */
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (closed) return true;
        String action = args.length < 2 ? "help" : args[1].toLowerCase(Locale.ROOT);
        if (!hasActionPermission(sender, action)) {
            sender.sendMessage(prefix() + configuration.messages().noPermission());
            return true;
        }
        if (!actionEnabled(action)) {
            sender.sendMessage(prefix() + ChatColor.RED + "此功能已在 Essentials 配置中停用。");
            return true;
        }
        switch (action) {
            case "help" -> sendHelp(sender, label, args.length == 0 ? "essentials" : args[0]);
            case "status" -> sendStatus(sender);
            case "reload" -> reload(sender);
            case "conflicts" -> showCommandBindings(sender, arg(args, 2, ""));
            case "menu" -> requirePlayer(sender, p -> {
                EssentialsMenuPacketHandler handler = menuPacketHandler.get();
                if (handler != null) handler.openMenu(p);
                else p.sendMessage(fullMsg("admin.ui.offline"));
            });
            case "admin" -> requirePlayer(sender, p -> {
                EssentialsAdminPacketHandler handler = adminPacketHandler.get();
                if (handler != null) handler.openMenu(p);
                else p.sendMessage(fullMsg("admin.ui.offline"));
            });
            case "fly" -> requirePlayer(sender, p -> withTarget(sender, args, p, target -> playerService.toggleFly(p, target)));
            case "god" -> requirePlayer(sender, p -> withTarget(sender, args, p, target -> playerService.toggleGod(target)));
            case "heal" -> requirePlayer(sender, p -> withTarget(sender, args, p, target -> playerService.heal(target)));
            case "feed" -> requirePlayer(sender, p -> withTarget(sender, args, p, target -> playerService.feed(target)));
            case "gamemode" -> handleGameMode(sender, args);
            case "speed" -> handleSpeed(sender, args);
            case "vanish" -> requirePlayer(sender, p -> playerService.toggleVanish(p));
            case "afk" -> requirePlayer(sender, p -> playerService.toggleAfk(p));
            case "back" -> requirePlayer(sender, p -> teleportService.back(p));
            case "repair" -> requirePlayer(sender, p -> playerService.repair(p));
            case "hat" -> requirePlayer(sender, p -> playerService.hat(p));
            case "enderchest" -> requirePlayer(sender, p -> withTarget(sender, args, p, target -> playerService.openEnderChest(p, target)));
            case "workbench" -> requirePlayer(sender, p -> playerService.openWorkbench(p));
            case "anvil" -> requirePlayer(sender, p -> playerService.openAnvil(p));
            case "trash" -> requirePlayer(sender, p -> playerService.openTrash(p));
            case "nick" -> handleNick(sender, args);
            case "seen" -> handleSeen(sender, args);
            case "home" -> requirePlayer(sender, p -> teleportService.teleportHome(p, arg(args, 2, "home")));
            case "sethome" -> requirePlayer(sender, p -> teleportService.setHome(p, arg(args, 2, "home")));
            case "delhome" -> requirePlayer(sender, p -> teleportService.deleteHome(p, arg(args, 2, "home")));
            case "warp" -> requirePlayer(sender, p -> teleportService.teleportWarp(p, joinArgs(args, 2, "")));
            case "warps" -> requirePlayer(sender, p -> showWarps(p, arg(args, 2, ""), page(args, 3)));
            case "warpfavorite" -> requirePlayer(sender, p -> toggleWarpFavorite(p, joinArgs(args, 2, "")));
            case "setwarp" -> handleRequireArg(sender, args, 2, "name", name ->
                    requirePlayer(sender, p -> teleportService.setWarp(p, name)));
            case "delwarp" -> handleRequireArg(sender, args, 2, "name", name ->
                    requirePlayer(sender, p -> teleportService.deleteWarp(p, name)));
            case "spawn" -> requirePlayer(sender, p -> teleportService.teleportSpawn(p));
            case "setspawn" -> requirePlayer(sender, p -> teleportService.setSpawn(p));
            case "tpa" -> handleTpa(sender, args, false);
            case "tpahere" -> handleTpa(sender, args, true);
            case "tpaccept" -> requirePlayer(sender, p -> teleportService.respondTpa(p, arg(args, 2, ""), true));
            case "tpdeny" -> requirePlayer(sender, p -> teleportService.respondTpa(p, arg(args, 2, ""), false));
            case "tptoggle" -> requirePlayer(sender, teleportService::toggleTpa);
            case "tpcancel" -> requirePlayer(sender, p -> teleportService.cancelTpa(p, arg(args, 2, "")));
            case "unstuck" -> requirePlayer(sender, teleportService::unstuck);
            case "tp" -> handleTp(sender, args);
            case "top" -> requirePlayer(sender, p -> teleportService.teleportTop(p));
            case "tppos" -> handleTpPos(sender, args);
            case "time" -> handleTime(sender, args);
            case "weather" -> handleWeather(sender, args);
            case "ban" -> handleBan(sender, args, false);
            case "tempban" -> handleBan(sender, args, true);
            case "unban" -> handleUnban(sender, args);
            case "banhistory" -> showBanHistory(sender, arg(args, 2, ""), page(args, 3));
            case "mute" -> handleMute(sender, args, false);
            case "tempmute" -> handleMute(sender, args, true);
            case "unmute" -> handleUnmute(sender, args);
            case "chatreview" -> {
                var chat = xuanmo.arcartxsuite.api.AxsCapabilities.get(
                    xuanmo.arcartxsuite.api.capability.ChatModerationAccess.class);
                if (chat == null) sender.sendMessage(fullMsg("admin.mute.offline-warn"));
                else chat.inspect(sender, arg(args, 2, "").toLowerCase(Locale.ROOT), arg(args, 3, ""));
            }
            case "kick" -> handleKick(sender, args);
            case "warn" -> handleWarn(sender, args);
            case "sudo" -> handleSudo(sender, args);
            case "inv" -> handleInv(sender, args);
            case "sit" -> requirePlayer(sender, this::handleSit);
            case "lay" -> requirePlayer(sender, this::handleLay);
            case "replant" -> requirePlayer(sender, p -> {
                if (inventoryActionsService != null) {
                    boolean on = inventoryActionsService.toggleReplant(p.getUniqueId());
                    p.sendMessage(prefix() + (on ? configuration.messages().replantDisabled() : configuration.messages().replantEnabled()));
                }
            });
            case "autotool" -> requirePlayer(sender, p -> {
                if (inventoryActionsService != null) {
                    boolean on = inventoryActionsService.toggleAutoTool(p.getUniqueId());
                    p.sendMessage(prefix() + (on ? configuration.messages().autotoolDisabled() : configuration.messages().autotoolEnabled()));
                }
            });
            default -> {
                sender.sendMessage(fullMsg("common.unknown", action));
                sendHelp(sender, label, args.length == 0 ? "essentials" : args[0]);
            }
        }
        return true;
    }

    /**
     * 提供 {@code /essentials} 命令的 Tab 补全。
     *
     * @param sender 命令发送者
     * @param args   当前参数数组
     * @return 补全候选列表，无补全时返回空列表
     */
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (closed) return List.of();
        if (args.length == 2) return filter(ACTIONS.stream().filter(this::actionEnabled)
            .filter(action -> hasActionPermission(sender, action)).toList(), args[1]);
        if (args.length >= 3) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (!actionEnabled(action) || !hasActionPermission(sender, action)) return List.of();
        }
        if (args.length == 3) {
            String action = args[1].toLowerCase(Locale.ROOT);
            switch (action) {
                case "fly", "god", "heal", "feed", "enderchest", "tp", "sudo", "inv",
                     "ban", "tempban", "unban", "mute", "tempmute", "unmute", "kick", "warn" -> {
                    return filterOnline(sender, args[2]);
                }
                case "home", "delhome" -> {
                    if (sender instanceof Player p) return filter(teleportService.getHomeNames(p), args[2]);
                }
                case "warp", "warpfavorite", "delwarp" -> {
                    if (sender instanceof Player p) return filter(teleportService.warps().names(p, action.equals("delwarp")), args[2]);
                }
                case "warps" -> {
                    return filter(List.of("favorites"), args[2]);
                }
                case "tpaccept", "tpdeny" -> {
                    if (sender instanceof Player p) return filter(teleportService.tpaRequests(p).stream()
                        .map(TeleportService.TpaView::sender).toList(), args[2]);
                }
                case "conflicts" -> {
                    return filter(new ArrayList<>(commandLabels().keySet()), args[2]);
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
                case "tpa", "tpahere", "tpcancel", "banhistory" -> {
                    return filterOnline(sender, args[2]);
                }
            }
        }
        if (args.length == 4) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if ("gamemode".equals(action)) return filterOnline(sender, args[3]);
            if ("speed".equals(action)) return filter(List.of("reset", "1", "2", "3", "5", "10"), args[3]);
            if ("inv".equals(action) && sender.hasPermission(InventoryInspection.EDIT_PERMISSION)) {
                return filter(List.of("edit"), args[3]);
            }
        }
        return List.of();
    }

    // --- Command Handlers ---

    private boolean active(CommandSender sender, String action) {
        return !closed && actionEnabled(action) && hasActionPermission(sender, action)
            && (!(sender instanceof Player player) || (player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player));
    }

    private <T> void background(CommandSender sender, String action, java.util.concurrent.Callable<T> operation,
                                java.util.function.Consumer<T> reply) {
        if (!active(sender, action)) return;
        complete(sender, action, work.submit(operation), reply);
    }

    private <T> void query(CommandSender sender, String action, java.util.concurrent.Callable<T> operation,
                           java.util.function.Consumer<T> reply) {
        if (!active(sender, action)) return;
        complete(sender, action, work.read(operation), reply);
    }

    private <T> void complete(CommandSender sender, String action, java.util.concurrent.CompletionStage<T> pending,
                              java.util.function.Consumer<T> reply) {
        complete(sender, action, pending, null, reply);
    }

    private <T> void complete(CommandSender sender, String action, java.util.concurrent.CompletionStage<T> pending,
                              java.util.function.Consumer<T> committedEffect, java.util.function.Consumer<T> reply) {
        pending.whenComplete((value, failure) -> work.onMain(() -> {
            if (closed) return;
            if (failure != null) {
                if (!active(sender, action)) return;
                Throwable cause = failure;
                while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
                if (cause instanceof ModerationService.IdentityException) { sender.sendMessage(prefix() + ChatColor.RED + cause.getMessage()); return; }
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Essentials " + action + "失败", cause);
                sender.sendMessage(prefix() + ChatColor.RED + "操作未完成，请稍后重试或查看服务端日志。");
            } else {
                if (committedEffect != null) committedEffect.accept(value);
                if (reply != null && active(sender, action)) reply.accept(value);
            }
        }));
    }

    public static List<String> actions() {
        return ACTIONS;
    }

    public boolean actionEnabled(String action) {
        return configuration.features().actionEnabled(action);
    }

    private void showWarps(Player player, String query, int page) {
        if (page < 1) {
            player.sendMessage(prefix() + ChatColor.RED + "用法 /ess warps [搜索词或 favorites] [页码]");
            return;
        }
        WarpDirectory warps = teleportService.warps();
        boolean favorites = "favorites".equalsIgnoreCase(query);
        UUID id = player.getUniqueId();
        var visible = warps.visible(player);
        query(player, "warps", () -> warps.list(id, visible, favorites ? "" : query, favorites), loaded -> {
            var allowed = warps.visible(player).stream().map(warp -> warp.name().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
            var entries = loaded.stream().filter(entry -> allowed.contains(entry.name().toLowerCase(Locale.ROOT))).toList();
            int start = (int)Math.min(entries.size(), (page - 1L) * 10);
            int end = Math.min(entries.size(), start + 10);
            player.sendMessage(prefix() + "传送点 · 第 " + page + " 页 · 共 " + entries.size() + " 个");
            for (var warp : entries.subList(start, end)) {
                player.sendMessage(ChatColor.GRAY + "[" + warp.group() + "] " + (warp.favorite() ? "★ " : "") + warp.name());
            }
            if (start == end) player.sendMessage(ChatColor.GRAY + "本页没有匹配的传送点。");
        });
    }

    private void toggleWarpFavorite(Player player, String name) {
        if (name.isBlank()) {
            player.sendMessage(prefix() + ChatColor.RED + "用法 /ess warpfavorite <传送点>");
            return;
        }
        try {
            var warps = teleportService.warps();
            String key = warps.favoriteKey(player, name);
            UUID id = player.getUniqueId();
            background(player, "warpfavorite", () -> warps.toggleFavorite(id, key), favorite ->
                player.sendMessage(prefix() + (favorite ? "已收藏传送点 " : "已取消收藏 ") + name));
        } catch (IllegalArgumentException e) {
            player.sendMessage(prefix() + ChatColor.RED + e.getMessage());
        }
    }

    private Map<String, String> commandLabels() {
        Map<String, String> labels = new java.util.LinkedHashMap<>(configuration.commandAliases());
        labels.put("ess", "essentials");
        labels.put("axsess", "essentials");
        return labels;
    }

    private void showCommandBindings(CommandSender sender, String filter) {
        String term = filter.startsWith("/") ? filter.substring(1) : filter;
        int page = term.matches("[0-9]+") ? page(new String[]{term}, 0) : 1;
        if (page < 1) {
            sender.sendMessage(prefix() + ChatColor.RED + "无效页码。");
            return;
        }
        String search = term.matches("[0-9]+") ? "" : term.toLowerCase(Locale.ROOT);
        List<String> issues = new ArrayList<>();
        List<String> registered = new ArrayList<>();
        commandLabels().forEach((label, action) -> {
            if (!label.contains(search)) return;
            var binding = commandInspector.apply(label);
            boolean root = action.equals("essentials");
            boolean valid = root || ACTIONS.contains(action);
            boolean enabled = root || actionEnabled(action);
            String detail = !valid ? "动作无效" : !enabled ? "已由配置停用"
                : binding.moduleAlias() == null ? "Essentials 前缀未注册" : "/" + binding.moduleAlias();
            String line = "/" + label + " → " + binding.owner() + " | " + action + " | " + detail;
            if (binding.previousOwner() != null) line += " | 原来源 " + binding.previousOwner();
            if (!valid || !enabled || !binding.owner().equals("essentials") || binding.previousOwner() != null) issues.add(line);
            else registered.add(line);
        });
        issues.addAll(registered);
        int start = Math.min(issues.size(), (page - 1) * 12);
        sender.sendMessage(prefix() + "命令归属 · 第 " + page + " 页 · 共 " + issues.size() + " 项");
        issues.subList(start, Math.min(issues.size(), start + 12)).forEach(sender::sendMessage);
        if (issues.size() > start + 12) sender.sendMessage(ChatColor.GRAY + "下一页 /ess conflicts " + (page + 1));
    }

    private void showBanHistory(CommandSender sender, String target, int page) {
        if (target.isBlank() || page < 1) {
            sender.sendMessage(prefix() + ChatColor.RED + "用法 /ess banhistory <玩家名或 UUID> [页码]");
            return;
        }
        int size = configuration.features().historyPageSize();
        long offset = (page - 1L) * size;
        if (offset > Integer.MAX_VALUE) { sender.sendMessage(prefix() + "页码过大。"); return; }
        var pending = moderation.resolve(target).thenCompose(identity -> {
            if (identity == null) return java.util.concurrent.CompletableFuture.<HistoryPage>failedFuture(new ModerationService.IdentityException("找不到已知玩家 " + target));
            return work.read(() -> new HistoryPage(identity, repository.history(identity.id(), size + 1, (int)offset)));
        });
        complete(sender, "banhistory", pending, result -> {
            var identity = result.identity();
            var history = result.entries();
            sender.sendMessage(prefix() + identity.name() + " 的处罚记录 · 第 " + page + " 页");
            for (var entry : history.subList(0, Math.min(size, history.size()))) {
                String action = switch (entry.action()) {
                    case "BAN" -> "永久封禁";
                    case "TEMPBAN" -> "临时封禁";
                    case "AUTO_BAN" -> "自动封禁";
                    case "UNBAN" -> "解封";
                    case "EXPIRE" -> "封禁到期";
                    case "WARN" -> "警告";
                    default -> entry.action();
                };
                sender.sendMessage(ChatColor.GRAY + ModerationService.formatDate(entry.createdAt()) + " · " + action
                    + " · " + entry.operator() + " · " + (entry.reason() == null ? "无" : entry.reason())
                    + ("TEMPBAN".equals(entry.action()) ? " · 到期 " + ModerationService.formatDate(entry.expiresAt()) : ""));
            }
            if (history.isEmpty()) sender.sendMessage(ChatColor.GRAY + "本页没有处罚记录。");
            if (history.size() > size) sender.sendMessage(ChatColor.GRAY + "下一页 /ess banhistory " + identity.id() + " " + (page + 1));
        });
    }

    private record HistoryPage(ModerationService.Identity identity, List<EssentialsRepository.ModerationEntry> entries) {}

    private static int page(String[] args, int index) {
        if (args.length <= index) return 1;
        try {
            int page = Integer.parseInt(args[index]);
            return page >= 1 && page <= 100_000 ? page : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void handleGameMode(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.gamemode.usage")); return; }
        GameMode mode = parseGameMode(args[2]);
        if (mode == null) { sender.sendMessage(fullMsg("admin.gamemode.invalid", args[2])); return; }
        Player target = args.length >= 4 ? visibleTarget(sender, args[3]) : (sender instanceof Player p ? p : null);
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
        String mode = arg(args, 2, "").toLowerCase(Locale.ROOT);
        if (!mode.equals("fly") && !mode.equals("walk")) {
            sender.sendMessage(fullMsg("admin.speed.usage"));
            return;
        }
        boolean isFly = mode.equals("fly");
        String value = arg(args, 3, "reset");
        try {
            float speed = value.equalsIgnoreCase("reset")
                ? (isFly ? configuration.player().defaultFlySpeed() : configuration.player().defaultWalkSpeed())
                : Float.parseFloat(value);
            if (!Float.isFinite(speed)) throw new NumberFormatException();
            requirePlayer(sender, p -> playerService.setSpeed(p, speed, isFly));
        } catch (NumberFormatException e) {
            sender.sendMessage(fullMsg("admin.speed.invalid", value));
        }
    }

    private void handleNick(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.nick.usage")); return; }
        requirePlayer(sender, p -> {
            String raw = args[2];
            if (raw.length() > 128 || raw.chars().anyMatch(Character::isISOControl)) {
                p.sendMessage(prefix() + ChatColor.RED + "昵称须不超过 128 个字符，且不能包含控制字符。"); return;
            }
            boolean reset = "off".equalsIgnoreCase(raw) || "reset".equalsIgnoreCase(raw);
            UUID id = p.getUniqueId();
            String display = reset ? p.getName() : ChatColor.translateAlternateColorCodes('&', raw);
            background(p, "nick", () -> {
                if (reset) repository.removeNickname(id); else repository.setNickname(id, raw);
                return true;
            }, ignored -> {
                p.setDisplayName(display);
                p.sendMessage(prefix() + (reset ? configuration.messages().nickReset() : configuration.messages().nickSet().replace("{nick}", display)));
            });
        });
    }

    private void handleSeen(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.seen.usage")); return; }
        Player target = visibleTarget(sender, args[2]);
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
            Player target = visibleTarget(sender, args[2]);
            if (target == null || !target.isOnline()) {
                p.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
                return;
            }
            teleportService.sendTpa(p, target, here);
        });
    }

    private void handleTp(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.tp.usage")); return; }
        Player target = visibleTarget(sender, args[2]);
        if (target == null) {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
            return;
        }
        if (args.length >= 4) {
            Player dest = visibleTarget(sender, args[3]);
            if (dest == null) {
                sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[3]));
                return;
            }
            if (teleportService.teleportNow(target, dest.getLocation())) {
                sender.sendMessage(fullMsg("admin.tp.success-other", target.getName(), dest.getName()));
            } else if (!sender.equals(target)) {
                sender.sendMessage(prefix() + configuration.messages().teleportCancelled());
            }
        } else {
            requirePlayer(sender, p -> {
                if (teleportService.teleportNow(p, target.getLocation())) {
                    p.sendMessage(fullMsg("admin.tp.success-self", target.getName()));
                }
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
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                    throw new NumberFormatException();
                }
                teleportService.delayedTeleport(p, new org.bukkit.Location(p.getWorld(), x, y, z, p.getLocation().getYaw(), p.getLocation().getPitch()),
                    messages().get("admin.tppos.success", x, y, z));
            } catch (NumberFormatException e) { p.sendMessage(fullMsg("admin.tppos.invalid")); }
        });
    }

    private void handleTime(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.time.usage")); return; }
        requirePlayer(sender, p -> {
            World world = p.getWorld();
            if (!configuration.world().allowedWorlds().isEmpty()
                && configuration.world().allowedWorlds().stream().noneMatch(world.getName()::equalsIgnoreCase)) {
                p.sendMessage(fullMsg("admin.world-not-allowed", world.getName()));
                return;
            }
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
            if (!configuration.world().allowedWorlds().isEmpty()
                && configuration.world().allowedWorlds().stream().noneMatch(world.getName()::equalsIgnoreCase)) {
                p.sendMessage(fullMsg("admin.world-not-allowed", world.getName()));
                return;
            }
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
        Player target = visibleTarget(sender, args[2]);
        String operator = sender.getName();
        long expiresAt = -1;
        int reasonStart = 3;
        if (temp) {
            if (args.length < 4) { sender.sendMessage(fullMsg("admin.ban.require-duration")); return; }
            try {
                expiresAt = ModerationService.expiryAt(args[3], System.currentTimeMillis());
            } catch (IllegalArgumentException | ArithmeticException e) {
                sender.sendMessage(fullMsg("admin.ban.require-duration"));
                return;
            }
            reasonStart = 4;
        }
        String reason = joinArgs(args, reasonStart, "\u65e0");
        String requested = args[2];
        String action = temp ? "tempban" : "ban";
        long expiry = expiresAt;
        if (target == null) try { target = Bukkit.getPlayer(UUID.fromString(requested)); } catch (IllegalArgumentException ignored) {}
        var online = target != null && playerService.canSee(sender, target) ? new ModerationService.Identity(target.getUniqueId(), target.getName()) : null;
        java.util.concurrent.CompletionStage<ModerationService.Identity> resolution = configuration.features().offlineBans()
            ? moderation.resolve(requested) : java.util.concurrent.CompletableFuture.completedFuture(online);
        var pending = resolution.thenCompose(identity -> work.main(() -> {
            if (!active(sender, action)) throw new java.util.concurrent.CancellationException("Command sender unavailable");
            if (identity == null) throw new ModerationService.IdentityException("找不到已知玩家 " + requested);
            Player current = Bukkit.getPlayer(identity.id());
            String ip = configuration.moderation().recordBanIp() && current != null && current.getAddress() != null ? current.getAddress().getHostString() : null;
            return new BanIntent(identity, ip);
        })).thenCompose(intent -> work.submit(() -> moderation.ban(intent.identity(), operator, reason, expiry, intent.ip())));
        complete(sender, action, pending, record -> {
            moderation.enforce(record, Bukkit.getPlayer(record.uuid()));
            Bukkit.broadcastMessage(prefix() + configuration.messages().banBroadcast()
                .replace("{player}", record.playerName()).replace("{operator}", operator).replace("{reason}", reason));
        }, null);
    }

    private record BanIntent(ModerationService.Identity identity, String ip) {}

    private void handleUnban(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.unban.usage")); return; }
        String requested = args[2]; String operator = sender.getName();
        java.util.concurrent.CompletionStage<ModerationService.Identity> resolution;
        try { resolution = java.util.concurrent.CompletableFuture.completedFuture(new ModerationService.Identity(UUID.fromString(requested), requested)); }
        catch (IllegalArgumentException name) { resolution = moderation.resolve(requested); }
        var pending = resolution.thenCompose(identity -> work.main(() -> {
            if (!active(sender, "unban")) throw new java.util.concurrent.CancellationException("Command sender unavailable");
            return identity;
        })).thenCompose(identity -> identity == null ? java.util.concurrent.CompletableFuture.completedFuture(false)
            : work.submit(() -> moderation.unban(identity.id(), operator)));
        complete(sender, "unban", pending, removed -> {
            if (removed) {
                sender.sendMessage(prefix() + configuration.messages().unbanSuccess().replace("{player}", args[2]));
            } else {
                sender.sendMessage(fullMsg("admin.unban.not-banned", args[2]));
            }
        });
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
            try {
                expiresAt = Instant.ofEpochMilli(ModerationService.expiryAt(args[3], System.currentTimeMillis()));
            } catch (IllegalArgumentException | ArithmeticException e) {
                sender.sendMessage(fullMsg("admin.mute.require-duration"));
                return;
            }
            reasonStart = 4;
        }
        String reason = joinArgs(args, reasonStart, "\u65e0");
        complete(sender, temp ? "tempmute" : "mute", chatMutable.mutePlayer(playerName, expiresAt, reason, sender.getName()),
            result -> sender.sendMessage(prefix() + result));
    }

    private void handleUnmute(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.unmute.usage")); return; }
        ChatMutable chatMutable = chatMutableSupplier.get();
        if (chatMutable == null) { sender.sendMessage(fullMsg("admin.unmute.offline-warn")); return; }
        complete(sender, "unmute", chatMutable.unmutePlayer(args[2], sender.getName()), result -> sender.sendMessage(prefix() + result));
    }

    private void handleKick(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.kick.usage")); return; }
        Player target = visibleTarget(sender, args[2]);
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
        Player target = visibleTarget(sender, args[2]);
        if (target == null) {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
            return;
        }
        String reason = joinArgs(args, 3, "\u65e0");
        var identity = new ModerationService.Identity(target.getUniqueId(), target.getName());
        String operator = sender.getName();
        int maximum = configuration.moderation().maxWarningsBeforeBan();
        long since = configuration.moderation().warningExpireDays() > 0
            ? System.currentTimeMillis() - configuration.moderation().warningExpireDays() * 86400000L : 0;
        var pending = work.submit(() -> moderation.warn(identity, reason, operator, maximum, since));
        complete(sender, "warn", pending, result -> {
            if (result.ban() != null) moderation.enforce(result.ban(), Bukkit.getPlayer(identity.id()));
            Bukkit.broadcastMessage(prefix() + configuration.messages().warnBroadcast()
                .replace("{player}", identity.name()).replace("{operator}", operator).replace("{reason}", reason));
        }, result -> { if (result.ban() != null) sender.sendMessage(fullMsg("admin.warn.ban-tip", identity.name(), result.count())); });
    }

    private void handleSudo(CommandSender sender, String[] args) {
        if (args.length < 4) { sender.sendMessage(fullMsg("admin.sudo.usage")); return; }
        Player target = visibleTarget(sender, args[2]);
        if (target == null) {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
            return;
        }
        String cmd = joinArgs(args, 3, "");
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.isBlank()) {
            sender.sendMessage(fullMsg("admin.sudo.usage"));
            return;
        }
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)
            && !sender.equals(target) && !sender.hasPermission("axs.essentials.sudo.others")) {
            sender.sendMessage(prefix() + configuration.messages().noPermission());
            return;
        }
        boolean handled = target.performCommand(cmd);
        plugin.getLogger().info("Essentials sudo: " + sender.getName() + " -> " + target.getName()
            + " /" + cmd.split("\\s+", 2)[0] + " (handled=" + handled + ")");
        sender.sendMessage(fullMsg(handled ? "admin.sudo.success" : "admin.sudo.failed", target.getName(), cmd));
    }

    private void handleInv(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage(fullMsg("common.only-player")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.inv.usage")); return; }
        Player target = visibleTarget(sender, args[2]);
        if (target == null) {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
            return;
        }
        boolean editing = args.length > 3 && args[3].equalsIgnoreCase("edit");
        if (args.length > 3 && !editing) {
            sender.sendMessage(fullMsg("admin.inv.usage"));
            return;
        }
        if (editing && !p.hasPermission(InventoryInspection.EDIT_PERMISSION)) {
            sender.sendMessage(prefix() + configuration.messages().noPermission());
            return;
        }
        if (editing && p.equals(target)) {
            sender.sendMessage(fullMsg("admin.inv.self-edit"));
            return;
        }
        inventoryInspection.open(p, target, editing);
    }

    // --- Sit / Lay ---

    private void handleSit(Player player) {
        sitAt(player, player.getLocation());
    }

    public boolean sitAt(Player player, org.bukkit.Location location) {
        if (!actionEnabled("sit") || !hasActionPermission(player, "sit") || player.isInsideVehicle() || player.isSleeping()) return false;
        cancelPoseTask(player.getUniqueId());
        org.bukkit.entity.ArmorStand seat = player.getWorld().spawn(
            location.clone().subtract(0, 0.2, 0), org.bukkit.entity.ArmorStand.class, stand -> {
                stand.setVisible(false); stand.setGravity(false); stand.setSmall(true);
                stand.setMarker(true); stand.setInvulnerable(true);
            });
        if (closed || !seat.addPassenger(player) || closed) {
            seat.remove();
            return false;
        }
        seatEntities.put(player.getUniqueId(), seat);
        player.sendMessage(prefix() + configuration.messages().sitDown());
        BukkitTask monitorTask = Bukkit.getScheduler().runTaskTimer(plugin, poseCallback(() -> {
            if (!seat.isValid() || seat.getPassengers().isEmpty()) {
                seat.remove();
                seatEntities.remove(player.getUniqueId(), seat);
                cancelPoseTask(player.getUniqueId());
            }
        }), 20L, 20L);
        poseMonitorTasks.put(player.getUniqueId(), monitorTask);
        return true;
    }

    private void handleLay(Player player) {
        cancelPoseTask(player.getUniqueId());
        // 如果已经在睡觉状态，先唤醒
        if (player.isSleeping()) {
            player.wakeup(true);
            player.sendMessage(prefix() + configuration.messages().layUp());
            return;
        }
        // 用 sleep API 让玩家进入躺床姿势（身体水平），force=true 不需要真实床
        org.bukkit.Location sleepLoc = player.getLocation();
        boolean success = player.sleep(sleepLoc, true);
        if (closed) {
            if (success && player.isSleeping()) player.wakeup(true);
            return;
        }
        if (!success) {
            player.sendMessage(prefix() + ChatColor.RED + "当前无法躺下。");
            return;
        }
        player.sendMessage(prefix() + configuration.messages().layDown());
        // 定时检查玩家是否还处于睡觉状态，脱离后清理任务
        BukkitTask monitorTask = Bukkit.getScheduler().runTaskTimer(plugin, poseCallback(() -> {
            if (!player.isOnline() || !player.isSleeping()) {
                cancelPoseTask(player.getUniqueId());
            }
        }), 20L, 20L);
        poseMonitorTasks.put(player.getUniqueId(), monitorTask);
    }

    private void cancelPoseTask(UUID playerId) {
        BukkitTask task = poseMonitorTasks.remove(playerId);
        if (task != null) task.cancel();
        // 同步移除坐垫实体，避免重坐/退出后遗留隐形 ArmorStand
        org.bukkit.entity.Entity seat = seatEntities.remove(playerId);
        if (seat != null && seat.isValid()) {
            seat.remove();
        }
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
        sender.sendMessage(fullMsg("help.reload", label, alias));
        sender.sendMessage(ChatColor.GRAY + messages().get("help.interact-header"));
        sender.sendMessage(fullMsg("help.sit-lay", label, alias));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(fullMsg("status.title"));
        String runningState = playerService == null ? messages().get("status.stopped") : messages().get("status.running");
        sender.sendMessage(fullMsg("status.service", runningState));
        if (configuration != null) {
            sender.sendMessage(fullMsg("status.storage", configuration.storage().mode()));
            sender.sendMessage(fullMsg("status.afk-timeout", configuration.player().afkTimeout()));
            sender.sendMessage(fullMsg("status.teleport-delay", configuration.teleport().teleportDelay()));
            sender.sendMessage(fullMsg("status.max-homes", configuration.teleport().maxHomes()));
        }
    }

    private void reload(CommandSender sender) {
        try {
            reloadAction.get().whenComplete((ignored,failure)->{
                String message=failure==null?fullMsg("reload.success"):fullMsg("reload.failed",failure.getMessage());
                Runnable reply=()->{if(!(sender instanceof Player player)||player.isOnline())sender.sendMessage(message);};
                if(Bukkit.isPrimaryThread())reply.run();else if(plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,reply);
            });
        } catch (Exception e) { sender.sendMessage(fullMsg("reload.failed", e.getMessage())); }
    }

    // --- Utilities ---

    private void withTarget(CommandSender sender, String[] args, Player fallback,
                            java.util.function.Consumer<Player> action) {
        if (args.length <= 2) {
            action.accept(fallback);
            return;
        }
        Player target = visibleTarget(sender, args[2]);
        if (target != null) action.accept(target);
        else sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
    }

    private Player visibleTarget(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        return target != null && playerService.canSee(sender, target) ? target : null;
    }

    private void requirePlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (sender instanceof Player p) { action.accept(p); }
        else { sender.sendMessage(fullMsg("common.only-player")); }
    }

    public static boolean hasActionPermission(CommandSender sender, String action) {
        String permission = permissionForAction(action);
        return permission.isEmpty() || sender.hasPermission(permission);
    }

    public static String permissionForAction(String action) {
        String permission = switch (action) {
            case "help", "status" -> null;
            case "reload" -> "admin";
            case "tp", "top", "tppos" -> "teleport";
            case "tpahere", "tpaccept", "tpdeny", "tptoggle", "tpcancel" -> "tpa";
            case "warps", "warpfavorite" -> "warp";
            case "tempban" -> "ban";
            case "tempmute" -> "mute";
            case "chatreview" -> "mute";
            default -> ACTIONS.contains(action) ? action : null;
        };
        return permission == null ? "" : "axs.essentials." + permission;
    }

    public void executePanelAction(Player player, String action, String... arguments) {
        String[] args = new String[arguments.length + 2];
        args[0] = "essentials";
        args[1] = action;
        System.arraycopy(arguments, 0, args, 2, arguments.length);
        onCommand(player, "ess", args);
    }

    private void handleRequireArg(CommandSender sender, String[] args, int index, String name,
                                   java.util.function.Consumer<String> action) {
        String value = joinArgs(args, index, "").trim();
        if (value.isEmpty()) {
            sender.sendMessage(fullMsg("admin.require-arg", name)); return;
        }
        action.accept(value);
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

    private List<String> filterOnline(CommandSender sender, String prefix) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (playerService.canSee(sender, p)) names.add(p.getName());
        }
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

