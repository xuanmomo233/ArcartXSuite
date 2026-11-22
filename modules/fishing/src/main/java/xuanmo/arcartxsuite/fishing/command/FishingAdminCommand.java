package xuanmo.arcartxsuite.fishing.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.selection.PolygonSelection;
import xuanmo.arcartxsuite.api.selection.PolygonSelectionCapability;
import xuanmo.arcartxsuite.api.selection.PolygonSelectionManager;
import xuanmo.arcartxsuite.api.util.PointInPolygon;
import xuanmo.arcartxsuite.fishing.model.FishingPermanentData;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;
import xuanmo.arcartxsuite.fishing.service.FishingService;
import xuanmo.arcartxsuite.fishing.service.FishingService.TrialHook;

/**
 * 钓鱼模块的管理员命令处理器。
 * <p>
 * 关联模块：{@code fishing}。提供 {@code stats}（查看玩家统计）、
 * {@code givexp}（给予经验）、{@code reset}（重置玩家数据）、
 * {@code water}（水域管理：create/save/delete/list/reload）等管理操作，
 * 需要 {@code axs.fishing.admin} 权限。
 * <p>
 * 入参约定遵循 {@link ModuleCommandHandler}：{@code args[0]} 为模块 id，
 * {@code args[1]} 为子动作，后续为参数。
 */
public final class FishingAdminCommand implements ModuleCommandHandler {
    private static final String ADMIN_PERMISSION = "axs.fishing.admin";
    private static final String PERSONALITY_OPTION = "personality=";
    private static final String TEST_ARGUMENTS = "test <鱼种ID> <体型cm> <reeds|clear> [水流区ID] [personality=<ID>] [hook|hook-loop]";

    private final java.util.function.Supplier<FishingService> serviceSupplier;
    private final java.util.function.BiFunction<String, Object[], String> msg;
    private final java.util.function.Supplier<PolygonSelectionCapability> selectionCapabilitySupplier;
    private final File dataFolder;
    private final Runnable reloadCallback;

    public FishingAdminCommand(@NotNull java.util.function.Supplier<FishingService> serviceSupplier,
                               @NotNull java.util.function.BiFunction<String, Object[], String> msg,
                               @NotNull java.util.function.Supplier<PolygonSelectionCapability> selectionCapabilitySupplier,
                               @NotNull File dataFolder,
                               @NotNull Runnable reloadCallback) {
        this.serviceSupplier = serviceSupplier;
        this.msg = msg;
        this.selectionCapabilitySupplier = selectionCapabilitySupplier;
        this.dataFolder = dataFolder;
        this.reloadCallback = reloadCallback;
    }

    @Override public String commandId() { return "fishing"; }

    @Override public List<String> actions() {
        return List.of("help", "stats", "givexp", "reset", "settlement", "resetseason", "water", "fatigue", "test");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        FishingService service = serviceSupplier.get();
        if (service == null) {
            sender.sendMessage(msg.apply("command_service_unavailable", new Object[]{}));
            return true;
        }

        if (args.length < 2) {
            showHelp(sender, label);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if ((action.equals("stats") || action.equals("givexp") || action.equals("reset")
            || action.equals("settlement") || action.equals("resetseason") || action.equals("water") || action.equals("test"))
            && !sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(msg.apply("command_no_permission", new Object[]{}));
            return true;
        }
        switch (action) {
            case "help" -> showHelp(sender, label);
            case "stats" -> handleStats(sender, args, service);
            case "givexp" -> handleGiveXp(sender, args, service);
            case "reset" -> handleReset(sender, args, service);
            case "settlement" -> handleSettlement(sender, service);
            case "resetseason" -> handleResetSeason(sender, service);
            case "water" -> handleWater(sender, args);
            case "fatigue" -> handleFatigue(sender, args, service);
            case "test" -> handleTest(sender, label, args, service);
            default -> showHelp(sender, label);
        }
        return true;
    }

    private void handleTest(CommandSender sender, String label, String[] args, FishingService service) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c请在游戏内执行试钓命令。");
            return;
        }
        if (args.length < 5 || args.length > 8 || !(args[4].equalsIgnoreCase("reeds") || args[4].equalsIgnoreCase("clear"))) {
            sender.sendMessage("§e/" + label + " fishing " + TEST_ARGUMENTS);
            return;
        }
        int size;
        try {
            size = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c体型必须填写整数厘米数，例如 80。");
            return;
        }
        String current = null;
        String personality = null;
        TrialHook hook = TrialHook.NONE;
        for (int index = 5; index < args.length; index++) {
            String option = args[index];
            TrialHook requestedHook = trialHookOption(option);
            if (requestedHook != TrialHook.NONE && hook == TrialHook.NONE) {
                hook = requestedHook;
            } else if (isPersonalityOption(option) && personality == null && option.length() > PERSONALITY_OPTION.length()) {
                personality = option.substring(PERSONALITY_OPTION.length());
            } else if (current == null && requestedHook == TrialHook.NONE && !option.contains("=")) {
                current = option;
            } else {
                sender.sendMessage("§e/" + label + " fishing " + TEST_ARGUMENTS);
                return;
            }
        }
        service.startTrial(player, args[2], size, args[4].equalsIgnoreCase("reeds"), current,
            personality == null ? "normal" : personality, hook);
    }

    private static boolean isPersonalityOption(String option) {
        return option.regionMatches(true, 0, PERSONALITY_OPTION, 0, PERSONALITY_OPTION.length());
    }

    private static TrialHook trialHookOption(String option) {
        return switch (option.toLowerCase(Locale.ROOT)) {
            case "hook" -> TrialHook.SINGLE;
            case "hook-loop" -> TrialHook.LOOP;
            default -> TrialHook.NONE;
        };
    }

    private void handleStats(@NotNull CommandSender sender, @NotNull String[] args, @NotNull FishingService service) {
        Player target;
        if (args.length > 2) {
            target = Bukkit.getPlayer(args[2]);
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(msg.apply("command_player_required", new Object[]{""}));
            return;
        }

        if (target == null) {
            sender.sendMessage(msg.apply("command_player_not_found", new Object[]{""}));
            return;
        }

        FishingSeasonData seasonData = service.getSeasonData(target.getUniqueId());
        FishingPermanentData permData = service.getPermanentData(target.getUniqueId());
        int collectionCount = service.getCollectionCount(target.getUniqueId());
        int totalTypes = service.getTotalFishTypes();

        sender.sendMessage(msg.apply("command_stats_header", new Object[]{target.getName()}));
        sender.sendMessage(msg.apply("command_stats_level", new Object[]{String.valueOf(seasonData.level())}));
        sender.sendMessage(msg.apply("command_stats_xp", new Object[]{String.valueOf(seasonData.currentXp())}));
        sender.sendMessage(msg.apply("command_stats_total_caught", new Object[]{String.valueOf(permData.totalCaught())}));
        sender.sendMessage(msg.apply("command_stats_perfect", new Object[]{String.valueOf(permData.perfectCatches())}));
        sender.sendMessage(msg.apply("command_stats_collection", new Object[]{collectionCount + "/" + totalTypes}));
    }

    private void handleGiveXp(@NotNull CommandSender sender, @NotNull String[] args, @NotNull FishingService service) {
        if (args.length < 4) {
            sender.sendMessage(msg.apply("command_usage_givexp", new Object[]{}));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(msg.apply("command_player_not_found", new Object[]{args[2]}));
            return;
        }
        try {
            int amount = Integer.parseInt(args[3]);
            service.giveXp(target.getUniqueId(), amount);
            sender.sendMessage(msg.apply("command_give_xp", new Object[]{target.getName(), String.valueOf(amount)}));
        } catch (NumberFormatException e) {
            sender.sendMessage(msg.apply("command_invalid_xp", new Object[]{}));
        }
    }

    private void handleReset(@NotNull CommandSender sender, @NotNull String[] args, @NotNull FishingService service) {
        Player target;
        if (args.length > 2) {
            target = Bukkit.getPlayer(args[2]);
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(msg.apply("command_usage_reset", new Object[]{}));
            return;
        }

        if (target == null) {
            sender.sendMessage(msg.apply("command_player_not_found", new Object[]{""}));
            return;
        }

        service.resetPlayerData(target.getUniqueId());
        sender.sendMessage(msg.apply("command_reset", new Object[]{target.getName()}));
    }

    private void handleSettlement(@NotNull CommandSender sender, @NotNull FishingService service) {
        service.triggerSettlement().whenComplete((failed, failure) -> service.onMain(() -> {
            if (serviceSupplier.get() != service || !sender.hasPermission(ADMIN_PERMISSION)) return;
            if (sender instanceof Player player && Bukkit.getPlayer(player.getUniqueId()) != player) return;
            if (failure != null) sender.sendMessage("§c赛季结算未能确认，请查看服务端日志。");
            else sender.sendMessage(failed == 0 ? "§a在线玩家赛季奖励已进入待发队列。"
                : "§e赛季结算有 " + failed + " 项未确认，请查看服务端日志。");
        }));
    }

    private void handleResetSeason(@NotNull CommandSender sender, @NotNull FishingService service) {
        // 重置当前赛季数据（天赋/等级/经验/奖励领取记录）
        // 注意：此操作不可逆，仅用于测试
        sender.sendMessage("§c赛季重置功能需要手动清除数据库表 axs_fishing_season 中的当前赛季数据。");
        sender.sendMessage("§7赛季ID: " + service.getSeasonData(((org.bukkit.entity.Player) sender).getUniqueId()).seasonId());
    }

    private void showHelp(@NotNull CommandSender sender, @NotNull String label) {
        String cmd = "/" + label + " fishing";
        sender.sendMessage(msg.apply("help.title", new Object[]{}));
        sender.sendMessage(msg.apply("help.stats", new Object[]{cmd}));
        sender.sendMessage(msg.apply("help.givexp", new Object[]{cmd}));
        sender.sendMessage(msg.apply("help.reset", new Object[]{cmd}));
        sender.sendMessage("§e" + cmd + " settlement §7— 手动触发赛季结算");
        sender.sendMessage("§e" + cmd + " resetseason §7— 查看赛季重置说明");
        sender.sendMessage("§e" + cmd + " water create <id> §7— 开始多边形选区创建水域");
        sender.sendMessage("§e" + cmd + " water save <id> §7— 保存当前选区为水域文件");
        sender.sendMessage("§e" + cmd + " water delete <id> §7— 删除指定水域");
        sender.sendMessage("§e" + cmd + " water list §7— 列出所有水域");
        sender.sendMessage("§e" + cmd + " water reload §7— 重载水域配置");
        sender.sendMessage("§e" + cmd + " fatigue set <player> <value> §7— 设置玩家疲惫值");
        sender.sendMessage("§e" + cmd + " fatigue reset <player> §7— 重置玩家疲惫值为0");
        sender.sendMessage("§e" + cmd + " fatigue view <player> §7— 查看玩家疲惫值");
        if (sender instanceof Player && sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§e" + cmd + " " + TEST_ARGUMENTS + " §7— 在龙泉湖配置下直接试钓");
        }
    }

    // ─── 疲惫值管理 ────────────────────────────────────────────

    private void handleFatigue(@NotNull CommandSender sender, @NotNull String[] args,
                                @NotNull xuanmo.arcartxsuite.fishing.service.FishingService service) {
        if (args.length < 3) {
            sender.sendMessage("§e/axs fishing fatigue <set|reset|view> <player> [value]");
            return;
        }
        String subAction = args[2].toLowerCase();
        if (subAction.equals("view")) {
            if (args.length < 4) {
                sender.sendMessage("§c用法: /axs fishing fatigue view <player>");
                return;
            }
            org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                sender.sendMessage("§c玩家不在线或不存在: " + args[3]);
                return;
            }
            int fatigue = service.getFatigue(target.getUniqueId());
            double eff = service.getFatigueEfficiency(target.getUniqueId());
            sender.sendMessage("§6" + target.getName() + " 的疲惫值: §e" + fatigue + "/100");
            sender.sendMessage("§6当前钓鱼效率: §e" + String.format("%.0f%%", eff * 100));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage("§c用法: /axs fishing fatigue " + subAction + " <player> [value]");
            return;
        }
        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[3]);
        if (target == null) {
            sender.sendMessage("§c玩家不在线或不存在: " + args[3]);
            return;
        }
        switch (subAction) {
            case "set" -> {
                if (args.length < 5) {
                    sender.sendMessage("§c用法: /axs fishing fatigue set <player> <value>");
                    return;
                }
                try {
                    int value = Math.max(0, Math.min(100, Integer.parseInt(args[4])));
                    service.setFatigue(target.getUniqueId(), value);
                    sender.sendMessage("§a已将 " + target.getName() + " 的疲惫值设为 " + value);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c值必须是 0-100 的整数");
                }
            }
            case "reset" -> {
                service.setFatigue(target.getUniqueId(), 0);
                sender.sendMessage("§a已重置 " + target.getName() + " 的疲惫值为 0");
            }
            default -> sender.sendMessage("§e/axs fishing fatigue <set|reset|view> <player> [value]");
        }
    }

    // ─── 水域管理 ──────────────────────────────────────────────

    private void handleWater(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e/axs fishing water <create|save|delete|list|reload> [id]");
            return;
        }
        String subAction = args[2].toLowerCase();
        switch (subAction) {
            case "create" -> handleWaterCreate(sender, args);
            case "save" -> handleWaterSave(sender, args);
            case "delete" -> handleWaterDelete(sender, args);
            case "list" -> handleWaterList(sender);
            case "reload" -> handleWaterReload(sender);
            default -> sender.sendMessage("§e/axs fishing water <create|save|delete|list|reload> [id]");
        }
    }

    private void handleWaterCreate(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行。");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage("§e/axs fishing water create <id>");
            return;
        }
        String waterId = args[3].toLowerCase();
        PolygonSelectionCapability cap = selectionCapabilitySupplier.get();
        if (cap == null || cap.manager() == null) {
            sender.sendMessage("§c选区工具不可用（宿主未提供 PolygonSelectionCapability）。");
            return;
        }
        PolygonSelectionManager manager = cap.manager();
        manager.startSelection(player, sel -> {
            // 选区闭合后自动提示保存
            player.sendMessage("§a选区已闭合！共 " + sel.pointCount() + " 个顶点。");
            player.sendMessage("§e请执行 /axs fishing water save " + waterId + " 保存水域文件。");
        });
        sender.sendMessage("§a已开始选区，请手持选区工具（默认木棍）：");
        sender.sendMessage("§7  左键方块 → 添加顶点");
        sender.sendMessage("§7  右键方块 → 闭合多边形（至少3点）");
        sender.sendMessage("§7  Shift+右键 → 撤销上一个顶点");
    }

    private void handleWaterSave(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行。");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage("§e/axs fishing water save <id>");
            return;
        }
        String waterId = args[3].toLowerCase();
        PolygonSelectionCapability cap = selectionCapabilitySupplier.get();
        if (cap == null || cap.manager() == null) {
            sender.sendMessage("§c选区工具不可用。");
            return;
        }
        PolygonSelection sel = cap.manager().getSelection(player);
        if (sel == null || !sel.isClosed()) {
            sender.sendMessage("§c没有已闭合的选区，请先执行 /axs fishing water create " + waterId);
            return;
        }

        File watersDir = new File(dataFolder, "waters");
        if (!watersDir.exists()) watersDir.mkdirs();
        File waterFile = new File(watersDir, "water-" + waterId + ".yml");

        // 写入水域 yml 文件
        StringBuilder sb = new StringBuilder();
        sb.append("# 水域配置：").append(waterId).append("（由选区工具生成）\n");
        sb.append("display-name: \"&b").append(waterId).append("\"\n");
        sb.append("world: \"").append(sel.worldName() != null ? sel.worldName() : "").append("\"\n");
        sb.append("enabled: true\n");
        sb.append("\n# 多边形边界点（x,z）\npos:\n");
        for (PointInPolygon.Vertex v : sel.points()) {
            sb.append("  - \"").append(v.x()).append(",").append(v.z()).append("\"\n");
        }
        sb.append("\n# 鱼类列表（请手动添加鱼种 ID）\nfishes: []\n");
        sb.append("\n# 宝藏列表（请手动添加宝藏 ID）\ntreasures: []\n");
        sb.append("\n# 难度修正公式（ARIA 表达式，变量 global.level = 玩家等级）\n");
        sb.append("difficulty-formula: \"1.0\"\n");
        sb.append("\nmin-player-level: 0\n");
        sb.append("max-player-level: 0\n");
        sb.append("\nbait-multipliers: {}\n");
        sb.append("require-permission: \"\"\n");

        try {
            java.nio.file.Files.writeString(waterFile.toPath(), sb.toString());
            sender.sendMessage("§a水域文件已保存: " + waterFile.getName());
            sender.sendMessage("§7请手动编辑该文件添加鱼类、宝藏、难度公式等配置。");
            sender.sendMessage("§e编辑完成后执行 /axs fishing water reload 重载配置。");
            // 清理选区
            cap.manager().stopSelection(player);
        } catch (IOException e) {
            sender.sendMessage("§c保存水域文件失败: " + e.getMessage());
        }
    }

    private void handleWaterDelete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§e/axs fishing water delete <id>");
            return;
        }
        String waterId = args[3].toLowerCase();
        File waterFile = new File(new File(dataFolder, "waters"), "water-" + waterId + ".yml");
        if (!waterFile.exists()) {
            sender.sendMessage("§c水域文件不存在: " + waterFile.getName());
            return;
        }
        if (waterFile.delete()) {
            sender.sendMessage("§a水域文件已删除: " + waterFile.getName());
            sender.sendMessage("§e执行 /axs fishing water reload 重载配置。");
        } else {
            sender.sendMessage("§c删除水域文件失败。");
        }
    }

    private void handleWaterList(@NotNull CommandSender sender) {
        File watersDir = new File(dataFolder, "waters");
        if (!watersDir.isDirectory()) {
            sender.sendMessage("§7waters/ 目录不存在。");
            return;
        }
        File[] files = watersDir.listFiles((dir, name) ->
            name.startsWith("water-") && name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            sender.sendMessage("§7没有水域文件。");
            return;
        }
        sender.sendMessage("§a=== 水域列表 ===");
        for (File f : files) {
            String id = f.getName().substring("water-".length(), f.getName().length() - ".yml".length());
            String tag = id.equals("default") ? " §7(默认)" : "";
            sender.sendMessage("§e  " + id + tag);
        }
    }

    private void handleWaterReload(@NotNull CommandSender sender) {
        try {
            reloadCallback.run();
            sender.sendMessage("§a水域配置已重载。");
        } catch (Exception e) {
            sender.sendMessage("§c重载失败: " + e.getMessage());
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            boolean canTest = sender instanceof Player && sender.hasPermission(ADMIN_PERMISSION);
            return actions().stream()
                .filter(a -> canTest || !a.equals("test"))
                .filter(a -> a.startsWith(args[1].toLowerCase()))
                .toList();
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("test")) {
            if (!(sender instanceof Player) || !sender.hasPermission(ADMIN_PERMISSION)) return List.of();
            FishingService service = serviceSupplier.get();
            if (service == null) return List.of();
            List<String> options = switch (args.length) {
                case 3 -> service.trialFishIds();
                case 4 -> service.trialSizes(args[2]);
                case 5 -> List.of("reeds", "clear");
                case 6, 7, 8 -> trialOptions(args, service);
                default -> List.of();
            };
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("stats") || args[1].equalsIgnoreCase("givexp")
            || args[1].equalsIgnoreCase("reset") || args[1].equalsIgnoreCase("settlement")
            || args[1].equalsIgnoreCase("resetseason"))) {
            String partial = args[2].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(partial))
                .toList();
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("water")) {
            return java.util.stream.Stream.of("create", "save", "delete", "list", "reload")
                .filter(s -> s.startsWith(args[2].toLowerCase()))
                .toList();
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("water")
            && (args[2].equalsIgnoreCase("create") || args[2].equalsIgnoreCase("save")
                || args[2].equalsIgnoreCase("delete"))) {
            // 列出已有水域 ID 作为补全
            File watersDir = new File(dataFolder, "waters");
            if (watersDir.isDirectory()) {
                File[] files = watersDir.listFiles((dir, name) ->
                    name.startsWith("water-") && name.endsWith(".yml"));
                if (files != null) {
                    return java.util.Arrays.stream(files)
                        .map(f -> f.getName().substring("water-".length(), f.getName().length() - ".yml".length()))
                        .filter(id -> id.startsWith(args[3].toLowerCase()))
                        .toList();
                }
            }
            return List.of();
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("givexp")) {
            return java.util.stream.Stream.of("10", "100", "500", "1000", "5000")
                .filter(v -> v.startsWith(args[3]))
                .toList();
        }
        return List.of();
    }

    private List<String> trialOptions(String[] args, FishingService service) {
        List<String> used = Arrays.asList(args).subList(5, args.length - 1);
        var options = new ArrayList<String>();
        if (used.stream().allMatch(option -> trialHookOption(option) != TrialHook.NONE || isPersonalityOption(option))) {
            options.addAll(service.trialCurrentIds());
        }
        if (used.stream().noneMatch(FishingAdminCommand::isPersonalityOption)) {
            service.trialPersonalityIds(args[2]).forEach(id -> options.add(PERSONALITY_OPTION + id));
        }
        if (used.stream().noneMatch(option -> trialHookOption(option) != TrialHook.NONE)) {
            options.add("hook");
            options.add("hook-loop");
        }
        return options;
    }
}
