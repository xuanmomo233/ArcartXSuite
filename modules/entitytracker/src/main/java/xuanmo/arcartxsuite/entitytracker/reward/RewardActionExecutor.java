package xuanmo.arcartxsuite.entitytracker.reward;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardAction;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardActionType;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardInventoryFullStrategy;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardMessageTarget;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;

/**
 * 通用奖励动作执行器，可由死亡即时结算和排行榜定时奖励共用。
 * <p>
 * 调用方通过 {@code variables} Map 注入占位符上下文（如 {player}, {rank}, {boss_display_name} 等），
 * 执行器负责模板渲染、物品发放、命令执行、消息发送、邮件派发和信号分发。
 */
public final class RewardActionExecutor {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

    private final JavaPlugin plugin;
    private final java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailDispatchableProvider;
    private final java.util.function.BiConsumer<String, Player> signalDispatcher;
    private final ItemSourceRegistry itemSourceRegistry;
    private Method papiSetPlaceholdersMethod;

    public RewardActionExecutor(
        JavaPlugin plugin,
        java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailDispatchableProvider,
        java.util.function.BiConsumer<String, Player> signalDispatcher,
        ItemSourceRegistry itemSourceRegistry
    ) {
        this.plugin = plugin;
        this.mailDispatchableProvider = mailDispatchableProvider == null ? () -> null : mailDispatchableProvider;
        this.signalDispatcher = signalDispatcher;
        this.itemSourceRegistry = itemSourceRegistry;
        initializePlaceholderApi();
    }

    /**
     * 执行一组奖励动作
     */
    public List<RewardActionResult> executeActions(
        List<BossDamageRewardAction> actions,
        OfflinePlayer target,
        Map<String, String> variables,
        BossDamageRewardInventoryFullStrategy inventoryFullStrategy
    ) {
        List<RewardActionResult> results = new ArrayList<>();
        for (BossDamageRewardAction action : actions) {
            results.add(executeAction(action, target, variables, inventoryFullStrategy));
        }
        return results;
    }

    /**
     * 执行单个奖励动作
     */
    public RewardActionResult executeAction(
        BossDamageRewardAction action,
        OfflinePlayer target,
        Map<String, String> variables,
        BossDamageRewardInventoryFullStrategy inventoryFullStrategy
    ) {
        if (action == null || action.type() == null) {
            return RewardActionResult.fail("unknown", "奖励动作类型无效");
        }

        try {
            return switch (action.type()) {
                case NEIGE_ITEMS -> executeItemAction(action, target, variables, inventoryFullStrategy, BossDamageRewardActionType.NEIGE_ITEMS);
                case MYTHIC_ITEMS -> executeItemAction(action, target, variables, inventoryFullStrategy, BossDamageRewardActionType.MYTHIC_ITEMS);
                case OVERTURE_ITEMS -> executeItemAction(action, target, variables, inventoryFullStrategy, BossDamageRewardActionType.OVERTURE_ITEMS);
                case COMMAND -> executeCommandAction(action, target, variables);
                case MESSAGE -> executeMessageAction(action, target, variables);
                case MAIL -> executeMailAction(action, target, variables);
                case SIGNAL -> executeSignalAction(action, target, variables);
            };
        } catch (Exception e) {
            return RewardActionResult.fail(action.type().configKey(), "执行异常: " + e.getMessage());
        }
    }

    // ─── 物品 ───────────────────────────────────────────

    private RewardActionResult executeItemAction(
        BossDamageRewardAction action,
        OfflinePlayer target,
        Map<String, String> variables,
        BossDamageRewardInventoryFullStrategy inventoryFullStrategy,
        BossDamageRewardActionType actionType
    ) {
        if (action.itemId().isBlank()) {
            return RewardActionResult.fail(actionType.configKey(), "缺少 item-id");
        }
        if (!(target instanceof Player player) || !player.isOnline()) {
            return RewardActionResult.fail(actionType.configKey(), "目标玩家不在线");
        }

        ItemStack itemStack = switch (actionType) {
            case NEIGE_ITEMS -> itemSourceRegistry.generateNeigeItem(action.itemId(), action.amount());
            case MYTHIC_ITEMS -> itemSourceRegistry.generateMythicItem(action.itemId(), action.amount());
            case OVERTURE_ITEMS -> itemSourceRegistry.generateOvertureItem(action.itemId(), player, action.amount());
            default -> null;
        };
        if (itemStack == null || itemStack.getType().isAir()) {
            return RewardActionResult.fail(actionType.configKey(), "物品不存在: " + action.itemId());
        }

        if (inventoryFullStrategy == BossDamageRewardInventoryFullStrategy.FAIL && !canFullyFit(player, itemStack)) {
            return RewardActionResult.fail(actionType.configKey(), "背包空间不足");
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack.clone());
        if (leftovers.isEmpty()) {
            return RewardActionResult.ok(actionType.configKey(), itemStack.getAmount() + "x " + action.itemId());
        }

        if (inventoryFullStrategy == BossDamageRewardInventoryFullStrategy.FAIL) {
            return RewardActionResult.fail(actionType.configKey(), "背包空间不足");
        }

        Location location = player.getLocation();
        for (ItemStack leftover : leftovers.values()) {
            if (leftover == null || leftover.getType().isAir()) continue;
            player.getWorld().dropItemNaturally(location, leftover);
        }
        if (inventoryFullStrategy == BossDamageRewardInventoryFullStrategy.DROP) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&e你的背包空间不足，" + action.itemId() + " 已掉落到脚下。"));
        }
        return RewardActionResult.ok(actionType.configKey(), "部分掉落到脚下");
    }

    // ─── 命令 ───────────────────────────────────────────

    private RewardActionResult executeCommandAction(
        BossDamageRewardAction action,
        OfflinePlayer target,
        Map<String, String> variables
    ) {
        if (action.command().isBlank()) {
            return RewardActionResult.fail(action.type().configKey(), "缺少 command");
        }

        String rendered = renderTemplate(action.command(), target, variables);
        boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripLeadingSlash(rendered));
        return new RewardActionResult(
            action.type().configKey(),
            dispatched,
            dispatched ? rendered : "控制台命令执行失败: " + rendered
        );
    }

    // ─── 消息 ───────────────────────────────────────────

    private RewardActionResult executeMessageAction(
        BossDamageRewardAction action,
        OfflinePlayer target,
        Map<String, String> variables
    ) {
        if (action.text().isBlank()) {
            return RewardActionResult.fail(action.type().configKey(), "缺少 text");
        }

        String rendered = ChatColor.translateAlternateColorCodes('&', renderTemplate(action.text(), target, variables));
        BossDamageRewardMessageTarget targetType = action.target() == null ? BossDamageRewardMessageTarget.PLAYER : action.target();
        switch (targetType) {
            case PLAYER -> {
                if (!(target instanceof Player player) || !player.isOnline()) {
                    return RewardActionResult.fail(action.type().configKey(), "目标玩家不在线");
                }
                player.sendMessage(rendered);
            }
            case BROADCAST -> Bukkit.broadcastMessage(rendered);
            case CONSOLE -> {
                ConsoleCommandSender console = Bukkit.getConsoleSender();
                console.sendMessage(rendered);
            }
        }
        return RewardActionResult.ok(action.type().configKey(), rendered);
    }

    // ─── 邮件 ───────────────────────────────────────────

    private RewardActionResult executeMailAction(
        BossDamageRewardAction action,
        OfflinePlayer target,
        Map<String, String> variables
    ) {
        if (action.presetId().isBlank()) {
            return RewardActionResult.fail("mail", "缺少 preset-id");
        }
        String targetName = resolvePlayerName(target, variables);
        xuanmo.arcartxsuite.api.capability.MailDispatchable mailService = mailDispatchableProvider.get();
        if (mailService == null) {
            return RewardActionResult.fail("mail", "Mail 模块未启用");
        }
        String source = "RankingReward:" + variables.getOrDefault("boss_id", "unknown");
        boolean ok = mailService.dispatchPreset(action.presetId(), targetName, source);
        return new RewardActionResult("mail", ok, ok ? "邮件已派发" : "邮件派发失败");
    }

    // ─── 信号 ───────────────────────────────────────────

    private RewardActionResult executeSignalAction(
        BossDamageRewardAction action,
        OfflinePlayer target,
        Map<String, String> variables
    ) {
        if (action.signal().isBlank()) {
            return RewardActionResult.fail("signal", "缺少 signal");
        }
        if (!(target instanceof Player player) || !player.isOnline()) {
            return RewardActionResult.fail("signal", "目标玩家不在线");
        }
        if (signalDispatcher != null) {
            signalDispatcher.accept(action.signal(), player);
        }
        return RewardActionResult.ok("signal", "信号 " + action.signal() + " 已发送");
    }

    // ─── 模板渲染 ───────────────────────────────────────

    private String renderTemplate(String template, OfflinePlayer target, Map<String, String> variables) {
        String safeTemplate = template == null ? "" : template;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(safeTemplate);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String resolved = variables.getOrDefault(key, variables.getOrDefault(key.toLowerCase(java.util.Locale.ROOT), null));
            if (resolved == null) {
                // 内建 player 变量兜底
                resolved = switch (key.toLowerCase(java.util.Locale.ROOT)) {
                    case "player", "player_name" -> resolvePlayerName(target, variables);
                    case "player_display_name" -> resolvePlayerDisplayName(target, variables);
                    case "player_uuid" -> target != null ? target.getUniqueId().toString() : "";
                    default -> null;
                };
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(resolved == null ? matcher.group(0) : resolved));
        }
        matcher.appendTail(rendered);
        return applyPlaceholderApi(target, rendered.toString());
    }

    // ─── 工具方法 ───────────────────────────────────────

    private static String resolvePlayerName(OfflinePlayer target, Map<String, String> variables) {
        if (target != null && target.getName() != null && !target.getName().isBlank()) {
            return target.getName();
        }
        return variables.getOrDefault("player_name", variables.getOrDefault("player", ""));
    }

    private static String resolvePlayerDisplayName(OfflinePlayer target, Map<String, String> variables) {
        if (target instanceof Player player && player.isOnline()) {
            String displayName = player.getDisplayName();
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
        }
        return resolvePlayerName(target, variables);
    }

    private static boolean canFullyFit(Player player, ItemStack itemStack) {
        if (player == null || itemStack == null || itemStack.getType().isAir()) return false;
        int remaining = Math.max(1, itemStack.getAmount());
        for (ItemStack slotItem : player.getInventory().getStorageContents()) {
            if (slotItem == null || slotItem.getType().isAir()) {
                remaining -= itemStack.getMaxStackSize();
            } else if (slotItem.isSimilar(itemStack)) {
                remaining -= Math.max(0, slotItem.getMaxStackSize() - slotItem.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return remaining <= 0;
    }

    private static String stripLeadingSlash(String command) {
        if (command == null) return "";
        String trimmed = command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    private void initializePlaceholderApi() {
        papiSetPlaceholdersMethod = null;
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        try {
            Class<?> placeholderApiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            papiSetPlaceholdersMethod = placeholderApiClass.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("初始化 RewardActionExecutor PlaceholderAPI 支持失败: " + exception.getMessage());
        }
    }

    private String applyPlaceholderApi(OfflinePlayer player, String text) {
        if (papiSetPlaceholdersMethod == null || text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        try {
            Object result = papiSetPlaceholdersMethod.invoke(null, player, text);
            return result == null ? "" : String.valueOf(result);
        } catch (ReflectiveOperationException exception) {
            return text;
        }
    }
}
