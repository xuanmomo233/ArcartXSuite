package xuanmo.arcartxsuite.mail.command;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.mail.model.MailCdkDefinition;
import xuanmo.arcartxsuite.mail.model.MailOperationResult;
import xuanmo.arcartxsuite.mail.model.MailPresetDefinition;
import xuanmo.arcartxsuite.mail.service.MailService;

/**
 * 邮件管理命令处理器，提供状态查看、预设分发、CDK 管理、打开他人邮箱等管理功能。
 * <p>
 * 关联模块：mail。实现 {@link ModuleCommandHandler}，由宿主统一命令系统调度。
 */
public final class MailAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "open", "preset", "cdk", "admin", "claim-review");
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smhd])$", Pattern.CASE_INSENSITIVE);
    private static final List<String> MULTI_TARGETS = List.of("all-online", "all-registered");
    private static final Duration CONFIRM_TTL = Duration.ofSeconds(30);

    /** 待确认的多人邮件派发请求，按发送者名称隔离。使用 ConcurrentHashMap 保证线程安全。 */
    private final Map<String, PendingDispatch> pendingConfirmations = new java.util.concurrent.ConcurrentHashMap<>();

    private final Supplier<MailService> serviceProvider;
    private final MessageProvider messages;

    /** 待确认派发会话。 */
    private static final class PendingDispatch {
        final String presetId;
        final String target;
        final String actorName;
        private final Instant expireAt;

        PendingDispatch(String presetId, String target, String actorName, Instant expireAt) {
            this.presetId = presetId;
            this.target = target;
            this.actorName = actorName;
            this.expireAt = expireAt;
        }

        boolean isExpired(Instant now) {
            return !now.isBefore(expireAt);
        }
    }

    /**
     * 构造管理命令处理器。
     *
     * @param serviceProvider 邮件服务提供者（延迟获取，允许服务未就绪时返回 {@code null}）
     * @param messages        消息提供者
     */
    public MailAdminCommand(Supplier<MailService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override public String commandId() { return "mail"; }
    @Override public List<String> actions() { return ACTIONS; }

    /**
     * 处理管理命令分发。
     *
     * @param sender 命令发送者
     * @param label  命令标签
     * @param args   参数数组
     * @return {@code true} 表示已处理
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "open" -> handleOpen(sender, args);
            case "preset" -> handlePreset(sender, args);
            case "cdk" -> handleCdk(sender, args);
            case "admin" -> handleAdmin(sender);
            case "claim-review" -> handleClaimReview(sender, args);
            default -> sender.sendMessage(fullMsg("common.unknown", label));
        }
        return true;
    }

    private void handleClaimReview(CommandSender sender, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(fullMsg("admin.claim-review-console"));
            return;
        }
        if (args.length != 4 && args.length != 6) {
            sender.sendMessage(fullMsg("admin.claim-review-usage"));
            return;
        }
        MailService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(fullMsg("common.service-down"));
            return;
        }
        try {
            UUID owner = UUID.fromString(args[2]);
            long mailId = Long.parseLong(args[3]);
            if (mailId <= 0) throw new IllegalArgumentException("Invalid mail ID");
            MailOperationResult result;
            if (args.length == 4) {
                result = service.inspectClaimReview(owner, mailId);
            } else {
                String resolution = args[5].toLowerCase(Locale.ROOT);
                if (!resolution.equals("confirmed") && !resolution.equals("retry") && !resolution.equals("interrupted")) {
                    sender.sendMessage(fullMsg("admin.claim-review-usage"));
                    return;
                }
                UUID attempt = UUID.fromString(args[4]);
                result = resolution.equals("interrupted") ? service.isolateInterruptedClaim(owner, mailId, attempt, sender.getName())
                    : service.resolveClaimReview(owner, mailId, attempt, resolution.equals("confirmed"), sender.getName());
            }
            sender.sendMessage((result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(fullMsg("admin.claim-review-usage"));
        }
    }

    private void handleAdmin(CommandSender sender) {
        MailService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(fullMsg("common.only-player"));
            return;
        }
        svc.openAdminUi(player);
    }

    /**
     * 提供管理命令的 Tab 补全。
     *
     * @param sender 命令发送者
     * @param args   参数数组
     * @return 补全候选列表，{@code null} 表示交由 Bukkit 补全玩家名
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        MailService svc = serviceProvider.get();
        if (args.length == 3) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "preset" -> filter(List.of("list", "send", "info", "delete", "reload"), args[2]);
                case "cdk" -> filter(List.of("create", "info", "list", "delete"), args[2]);
                case "open" -> null; // player names
                default -> null;
            };
        }
        if (args.length == 4 && "preset".equalsIgnoreCase(args[1]) && svc != null) {
            String sub = args[2].toLowerCase(Locale.ROOT);
            if ("send".equals(sub)) {
                // preset send 后可接 confirm（确认上一次多人派发）或 presetId
                List<String> candidates = new ArrayList<>(svc.presetIds());
                candidates.add("confirm");
                return filter(candidates, args[3]);
            }
            if ("info".equals(sub) || "delete".equals(sub)) {
                return filter(svc.presetIds(), args[3]);
            }
        }
        if (args.length == 5 && "preset".equalsIgnoreCase(args[1]) && "send".equalsIgnoreCase(args[2])) {
            // 目标位置：补全 all-online / all-registered + 在线玩家名
            if ("confirm".equalsIgnoreCase(args[3])) {
                return List.of();
            }
            List<String> candidates = new ArrayList<>(MULTI_TARGETS);
            for (Player online : Bukkit.getOnlinePlayers()) {
                candidates.add(online.getName());
            }
            return filter(candidates, args[4]);
        }
        if (args.length == 6 && "preset".equalsIgnoreCase(args[1]) && "send".equalsIgnoreCase(args[2])) {
            // force-bypass 不提供 tab 补全
            return List.of();
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " mail";
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(fullMsg("help.status", cmd));
        sender.sendMessage(fullMsg("help.open", cmd));
        sender.sendMessage(fullMsg("help.preset.list", cmd));
        sender.sendMessage(fullMsg("help.preset.send", cmd));
        sender.sendMessage(fullMsg("help.preset.send-confirm", cmd));
        sender.sendMessage(fullMsg("help.preset.info", cmd));
        sender.sendMessage(fullMsg("help.preset.delete", cmd));
        sender.sendMessage(fullMsg("help.preset.reload", cmd));
        sender.sendMessage(fullMsg("help.admin", cmd));
        if (sender instanceof ConsoleCommandSender) sender.sendMessage(fullMsg("help.claim-review", cmd));
        sender.sendMessage(fullMsg("help.cdk.create", cmd));
        sender.sendMessage(fullMsg("help.cdk.info", cmd));
        sender.sendMessage(fullMsg("help.cdk.list", cmd));
        sender.sendMessage(fullMsg("help.cdk.delete", cmd));
    }

    private void sendStatus(CommandSender sender) {
        MailService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        sender.sendMessage(fullMsg("status.title"));
        sender.sendMessage(fullMsg("status.preset", svc.presetCount()));
        sender.sendMessage(fullMsg("status.cross-server",
            svc.crossServerActive() ? messages.get("status.cross-server-enabled") : messages.get("status.cross-server-disabled")));
        sender.sendMessage(fullMsg("status.compose", svc.composeSessionCount()));
    }

    private void handleOpen(CommandSender sender, String[] args) {
        MailService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.open.usage")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { sender.sendMessage(fullMsg("open.not-online", args[2])); return; }
        MailOperationResult result = svc.openInbox(target);
        sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void handlePreset(CommandSender sender, String[] args) {
        MailService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.preset.usage"));
            return;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "list" -> handlePresetList(sender, svc);
            case "send" -> handlePresetSend(sender, svc, args);
            case "info" -> {
                if (args.length < 4) {
                    sender.sendMessage(fullMsg("admin.preset.info-usage"));
                    return;
                }
                handlePresetInfo(sender, svc, args[3]);
            }
            case "delete" -> {
                if (args.length < 4) {
                    sender.sendMessage(fullMsg("admin.preset.delete-usage"));
                    return;
                }
                MailOperationResult result = svc.deletePreset(args[3]);
                sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
            }
            case "reload" -> {
                var reload = svc.reloadPresets().toCompletableFuture();
                if (!reload.isDone()) sender.sendMessage(fullMsg("admin.preset-reloading"));
                reload.thenAccept(result -> {
                    if (Bukkit.isPrimaryThread() && !svc.isStopping()) sender.sendMessage(
                        (messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
                });
            }
            default -> sender.sendMessage(fullMsg("admin.preset.sub-list"));
        }
    }

    private void handlePresetList(CommandSender sender, MailService svc) {
        List<MailPresetDefinition> definitions = svc.getPresetDefinitions();
        if (definitions.isEmpty()) {
            sender.sendMessage(fullMsg("admin.preset.empty"));
            return;
        }
        sender.sendMessage(fullMsg("admin.preset.list-title", definitions.size()));
        for (MailPresetDefinition def : definitions) {
            String check = def.enabled() ? messages.get("admin.preset.enabled-marker") : messages.get("admin.preset.disabled-marker");
            int attCount = def.attachments() != null ? def.attachments().size() : 0;
            int cmdCount = def.claimCommands() != null ? def.claimCommands().size() : 0;
            sender.sendMessage(fullMsg("admin.preset.item-format", check, def.id(), ChatColor.translateAlternateColorCodes('&', def.displayName()), attCount, cmdCount));
        }
    }

    /**
     * 处理 preset send 子命令，支持多人派发二次确认与 force-bypass 绕过。
     * <ul>
     *   <li>{@code preset send confirm} —— 确认并执行上一次发起的多人派发请求。</li>
     *   <li>{@code preset send <presetId> <target> [force-bypass]} —— 派发邮件；
     *       当 target 为 all-online/all-registered 时需二次确认，附加 force-bypass 可直接派发。</li>
     * </ul>
     */
    private void handlePresetSend(CommandSender sender, MailService svc, String[] args) {
        // /axs mail preset send confirm
        if (args.length >= 4 && "confirm".equalsIgnoreCase(args[3])) {
            handlePresetSendConfirm(sender, svc);
            return;
        }
        if (args.length < 5) {
            sender.sendMessage(fullMsg("admin.preset.send-usage"));
            return;
        }
        String presetId = args[3];
        String target = args[4];
        boolean forceBypass = args.length >= 6 && "force-bypass".equalsIgnoreCase(args[5]);
        boolean isMultiTarget = isMultiTarget(target);

        // force-bypass 仅对多人派发生效
        if (forceBypass && !isMultiTarget) {
            sender.sendMessage(fullMsg("admin.preset.send-force-bypass-invalid"));
            return;
        }

        // 单人 target 或带 force-bypass 的多人 target 直接派发
        if (!isMultiTarget || forceBypass) {
            MailOperationResult result = svc.dispatchPreset(presetId, target, sender.getName());
            sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
            return;
        }

        // 多人 target 且未绕过：进入待确认会话
        String senderKey = sender.getName();
        PendingDispatch pending = new PendingDispatch(presetId, target, sender.getName(), Instant.now().plus(CONFIRM_TTL));
        pendingConfirmations.put(senderKey, pending);
        sender.sendMessage(fullMsg("admin.preset.send-confirm-prompt", target, presetId));
    }

    /** 处理 {@code preset send confirm}：执行上一次待确认的多人派发。 */
    private void handlePresetSendConfirm(CommandSender sender, MailService svc) {
        // 顺便清理过期的待确认条目，避免内存泄漏
        Instant now = Instant.now();
        pendingConfirmations.entrySet().removeIf(entry -> entry.getValue().isExpired(now));

        String senderKey = sender.getName();
        PendingDispatch pending = pendingConfirmations.remove(senderKey);
        if (pending == null) {
            sender.sendMessage(fullMsg("admin.preset.send-confirm-none"));
            return;
        }
        if (pending.isExpired(now)) {
            sender.sendMessage(fullMsg("admin.preset.send-confirm-expired"));
            return;
        }
        sender.sendMessage(fullMsg("admin.preset.send-confirm-accepted", pending.presetId, pending.target));
        MailOperationResult result = svc.dispatchPreset(pending.presetId, pending.target, pending.actorName);
        sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private static boolean isMultiTarget(String target) {
        if (target == null) return false;
        String lower = target.toLowerCase(Locale.ROOT);
        return MULTI_TARGETS.contains(lower);
    }

    private void handlePresetInfo(CommandSender sender, MailService svc, String presetId) {
        MailPresetDefinition def = svc.getPreset(presetId);
        if (def == null) {
            sender.sendMessage(fullMsg("admin.preset.not-found", presetId));
            return;
        }
        sender.sendMessage(fullMsg("admin.preset.info-title", def.id()));
        sender.sendMessage(fullMsg("admin.preset.info-enabled", def.enabled()));
        sender.sendMessage(fullMsg("admin.preset.info-display", ChatColor.translateAlternateColorCodes('&', def.displayName())));
        sender.sendMessage(fullMsg("admin.preset.info-subject", def.subject()));
        String displayBody = def.body().length() > 50 ? def.body().substring(0, 50) + "..." : def.body();
        sender.sendMessage(fullMsg("admin.preset.info-body", displayBody));
        sender.sendMessage(fullMsg("admin.preset.info-expires", def.expiresAfter() != null ? def.expiresAfter().toDays() + " 天" : messages.get("admin.preset.info-expires-default")));
        sender.sendMessage(fullMsg("admin.preset.info-attachments", def.attachments() != null ? def.attachments().size() : 0));
        sender.sendMessage(fullMsg("admin.preset.info-commands", def.claimCommands() != null ? def.claimCommands().size() : 0));
        sender.sendMessage(fullMsg("admin.preset.info-conditions", def.claimConditions() != null ? def.claimConditions().size() : 0));
        sender.sendMessage(fullMsg("admin.preset.info-cdks", def.cdks() != null ? def.cdks().size() : 0));
    }

    private void handleCdk(CommandSender sender, String[] args) {
        MailService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.cdk.usage")); return; }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "create" -> handleCdkCreate(sender, svc, args);
            case "info" -> handleCdkInfo(sender, svc, args);
            case "list" -> handleCdkList(sender, svc, args);
            case "delete" -> handleCdkDelete(sender, svc, args);
            default -> sender.sendMessage(fullMsg("admin.cdk.sub-list"));
        }
    }

    private void handleCdkCreate(CommandSender sender, MailService svc, String[] args) {
        if (args.length < 7) {
            sender.sendMessage(fullMsg("admin.cdk.create-usage"));
            return;
        }
        int maxClaims;
        try { maxClaims = Integer.parseInt(args[5]); } catch (NumberFormatException e) {
            sender.sendMessage(fullMsg("admin.cdk.invalid-claims", args[5])); return;
        }
        Instant expiresAt = parseTtl(args[6]);
        MailOperationResult result = svc.createCdk(args[3], args[4], maxClaims, expiresAt, sender.getName());
        sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void handleCdkInfo(CommandSender sender, MailService svc, String[] args) {
        if (args.length < 4) { sender.sendMessage(fullMsg("admin.cdk.info-usage")); return; }
        Optional<MailCdkDefinition> opt = svc.loadCdk(args[3]);
        if (opt.isEmpty()) { sender.sendMessage(fullMsg("admin.cdk.not-found", args[3])); return; }
        MailCdkDefinition cdk = opt.get();
        sender.sendMessage(fullMsg("admin.cdk.info-title", cdk.code()));
        sender.sendMessage(fullMsg("admin.cdk.info-preset", cdk.presetId()));
        sender.sendMessage(fullMsg("admin.cdk.info-claims", cdk.claimedCount(), cdk.maxClaims()));
        sender.sendMessage(fullMsg("admin.cdk.info-expires", cdk.expiresAt() == null ? messages.get("admin.cdk.info-expires-never") : cdk.expiresAt().toString()));
        sender.sendMessage(fullMsg("admin.cdk.info-enabled", cdk.enabled()));
    }

    private void handleCdkList(CommandSender sender, MailService svc, String[] args) {
        int page = args.length >= 4 ? parsePageNumber(args[3]) : 1;
        List<MailCdkDefinition> cdks = svc.listCdks(page, 10);
        if (cdks.isEmpty()) { sender.sendMessage(fullMsg("admin.cdk.list-empty", page)); return; }
        sender.sendMessage(fullMsg("admin.cdk.list-title", page));
        for (MailCdkDefinition c : cdks) {
            String stateStr = c.enabled() ? messages.get("admin.cdk.enabled") : messages.get("admin.cdk.disabled");
            sender.sendMessage(fullMsg("admin.cdk.list-item", c.code(), c.presetId(), c.claimedCount(), c.maxClaims(), stateStr));
        }
    }

    private void handleCdkDelete(CommandSender sender, MailService svc, String[] args) {
        if (args.length < 4) { sender.sendMessage(fullMsg("admin.cdk.delete-usage")); return; }
        MailOperationResult result = svc.deleteCdk(args[3]);
        sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private Instant parseTtl(String input) {
        if ("permanent".equalsIgnoreCase(input)) return null;
        Matcher m = DURATION_PATTERN.matcher(input.toLowerCase(Locale.ROOT));
        if (!m.matches()) return Instant.now().plus(Duration.ofDays(7));
        long val = Long.parseLong(m.group(1));
        return Instant.now().plus(switch (m.group(2)) {
            case "s" -> Duration.ofSeconds(val);
            case "m" -> Duration.ofMinutes(val);
            case "h" -> Duration.ofHours(val);
            case "d" -> Duration.ofDays(val);
            default -> Duration.ofDays(7);
        });
    }

    private static int parsePageNumber(String raw) {
        try { return Math.max(1, Integer.parseInt(raw)); } catch (NumberFormatException e) { return 1; }
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(n)) r.add(c);
        return r;
    }
}
