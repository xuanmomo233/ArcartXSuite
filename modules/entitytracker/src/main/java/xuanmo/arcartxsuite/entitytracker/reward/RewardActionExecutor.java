package xuanmo.arcartxsuite.entitytracker.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardAction;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardMessageTarget;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;

/**
 * 通用奖励动作执行器，可由死亡即时结算和排行榜定时奖励共用。
 * <p>
 * 调用方通过 {@code variables} Map 注入占位符上下文（如 {player}, {rank}, {boss_display_name} 等），
 * 执行器负责模板渲染、物品发放、命令执行、消息发送、邮件派发和信号分发。
 * <p>
 * 物品发放统一通过 {@link ItemRewardDispatcher}（放入背包→背包满转邮件→无邮件则掉落）。
 */
public final class RewardActionExecutor {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

    private final JavaPlugin plugin;
    private final java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailDispatchableProvider;
    private final java.util.function.BiConsumer<String, Player> signalDispatcher;
    private final ItemSourceRegistry itemSourceRegistry;
    private final ItemRewardDispatcher itemRewardDispatcher;
    private final PlaceholderResolverAPI placeholderResolver;

    public RewardActionExecutor(
        JavaPlugin plugin,
        java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailDispatchableProvider,
        java.util.function.BiConsumer<String, Player> signalDispatcher,
        ItemSourceRegistry itemSourceRegistry,
        ItemRewardDispatcher itemRewardDispatcher,
        PlaceholderResolverAPI placeholderResolver
    ) {
        this.plugin = plugin;
        this.mailDispatchableProvider = mailDispatchableProvider == null ? () -> null : mailDispatchableProvider;
        this.signalDispatcher = signalDispatcher;
        this.itemSourceRegistry = itemSourceRegistry;
        this.itemRewardDispatcher = itemRewardDispatcher;
        this.placeholderResolver = placeholderResolver;
    }

    /**
     * 执行一组奖励动作
     */
    public List<RewardActionResult> executeActions(
        List<BossDamageRewardAction> actions,
        OfflinePlayer target,
        Map<String, String> variables
    ) {
        List<RewardActionResult> results = new ArrayList<>();
        for (BossDamageRewardAction action : actions) {
            results.add(executeAction(action, target, variables));
        }
        return results;
    }

    /**
     * 执行单个奖励动作
     */
    public RewardActionResult executeAction(
        BossDamageRewardAction action,
        OfflinePlayer target,
        Map<String, String> variables
    ) {
        if (action == null || action.type() == null) {
            return RewardActionResult.fail("unknown", "奖励动作类型无效");
        }

        try {
            return switch (action.type()) {
                case ITEM -> executeItemAction(action, target, variables);
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
        Map<String, String> variables
    ) {
        if (action.id().isBlank()) {
            return RewardActionResult.fail(action.type().configKey(), "缺少 id");
        }
        if (!(target instanceof Player player) || !player.isOnline()) {
            return RewardActionResult.fail(action.type().configKey(), "目标玩家不在线");
        }

        ItemStack itemStack = itemSourceRegistry.generateItem(action.source(), action.id(), action.amount(), action.nbt(), player);
        if (itemStack == null || itemStack.getType().isAir()) {
            return RewardActionResult.fail(action.type().configKey(), "物品不存在: " + action.id());
        }

        String sourceDetail = "ranking:" + variables.getOrDefault("boss_id", "unknown");
        ItemRewardDispatcher.DispatchOptions options = ItemRewardDispatcher.DispatchOptions.builder("entitytracker")
            .sourceDetail(sourceDetail)
            .overflowMailSubject("Boss 奖励溢出物品")
            .overflowMailBody("您的背包已满，Boss 排行榜奖励物品通过邮件补发。")
            .overflowMailSenderName("Boss 追踪")
            .build();
        ItemRewardDispatcher.DispatchResult result = itemRewardDispatcher.dispatchItem(player, itemStack, options);
        if (result.success()) {
            String suffix = "";
            if (result.mailedCount() > 0) suffix = "（部分通过邮件补发）";
            else if (result.pendingCount() > 0) suffix = "（部分已暂存待发放）";
            String desc = itemStack.getAmount() + "x " + action.id() + suffix;
            return RewardActionResult.ok(action.type().configKey(), desc);
        }
        return RewardActionResult.fail(action.type().configKey(), result.message());
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
        // 占位符/PAPI 展开后统一校验最终命令：非玩家名占位符与 PAPI 值可能携带分隔符
        try {
            rendered = xuanmo.arcartxsuite.api.command.CommandSanitizer.validateExpandedCommand(rendered);
        } catch (IllegalArgumentException e) {
            return RewardActionResult.fail(action.type().configKey(), "命令含分隔符已拒绝: " + rendered);
        }
        boolean dispatched = xuanmo.arcartxsuite.api.command.ThrottledCommandExecutor.dispatch(stripLeadingSlash(rendered));
        return new RewardActionResult(
            action.type().configKey(),
            dispatched,
            dispatched ? rendered : "控制台命令受理失败(队列已满或执行异常): " + rendered
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
        xuanmo.arcartxsuite.api.capability.mail.MailSendResult mailResult = mailService.dispatchPreset(
            xuanmo.arcartxsuite.api.capability.mail.MailPresetRequest.builder(action.presetId(), targetName)
                .sourceModule("entitytracker")
                .sourceDetail(source)
                .build());
        boolean ok = mailResult.success();
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
                    case "player", "player_name" -> sanitizeName(resolvePlayerName(target, variables));
                    case "player_display_name" -> sanitizeName(resolvePlayerDisplayName(target, variables));
                    case "uuid", "player_uuid" -> variables.getOrDefault("player_uuid", target != null ? target.getUniqueId().toString() : "");
                    default -> null;
                };
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(resolved == null ? matcher.group(0) : resolved));
        }
        matcher.appendTail(rendered);
        return applyPlaceholderApi(target, rendered.toString());
    }

    // ─── 工具方法 ───────────────────────────────────────

    /** 玩家名净化：合法则原样返回，非法则清空命令分隔符 */
    private static String sanitizeName(String name) {
        if (name == null) return "";
        try {
            return xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(name);
        } catch (IllegalArgumentException e) {
            return xuanmo.arcartxsuite.api.command.CommandSanitizer.stripCommandSeparators(name);
        }
    }

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

    private static String stripLeadingSlash(String command) {
        if (command == null) return "";
        String trimmed = command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    private String applyPlaceholderApi(OfflinePlayer player, String text) {
        if (placeholderResolver == null || text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        if (player instanceof Player online) {
            return placeholderResolver.applyPlaceholders(online, text);
        }
        return text;
    }
}
