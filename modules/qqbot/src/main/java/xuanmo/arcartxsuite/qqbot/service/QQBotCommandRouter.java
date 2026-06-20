package xuanmo.arcartxsuite.qqbot.service;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.EssentialsQueryable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.qqbot.config.QQBotConfiguration;
import xuanmo.arcartxsuite.qqbot.config.QQBotCustomCommand;
import xuanmo.arcartxsuite.qqbot.onebot.OneBotClient;
import xuanmo.arcartxsuite.qqbot.onebot.OneBotEvent;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.QQBotBinding;

public final class QQBotCommandRouter {

    private final JavaPlugin plugin;
    private final QQBotConfiguration config;
    private final OneBotClient client;
    private final QQBotBindService bindService;
    private final QQBotRepository repository;
    private final Logger logger;
    private final PlaceholderResolverAPI placeholderResolver;
    private volatile Supplier<EssentialsQueryable> essentialsProvider;
    private volatile Supplier<MailDispatchable> mailProvider;
    private volatile QQBotSignInService signInService;

    public QQBotCommandRouter(
        JavaPlugin plugin,
        QQBotConfiguration config,
        OneBotClient client,
        QQBotBindService bindService,
        QQBotRepository repository,
        Logger logger,
        PlaceholderResolverAPI placeholderResolver
    ) {
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        this.bindService = bindService;
        this.repository = repository;
        this.logger = logger;
        this.placeholderResolver = placeholderResolver;
    }

    public void setEssentialsProvider(Supplier<EssentialsQueryable> provider) {
        this.essentialsProvider = provider;
    }

    public void setMailProvider(Supplier<MailDispatchable> provider) {
        this.mailProvider = provider;
    }

    public void setSignInService(QQBotSignInService signInService) {
        this.signInService = signInService;
    }

    /**
     * 处理群消息中的指令。返回 true 表示已处理。
     */
    public boolean handleCommand(OneBotEvent event) {
        // 黑名单拦截（防御性检查，主拦截在 QQBotService.handleOneBotEvent）
        if (config.blacklist().isConfigBlacklisted(event.userId()) || repository.isBlacklisted(event.userId())) {
            reply(event, config.messages().blacklistBlocked());
            return true;
        }

        String raw = event.rawMessage().trim();
        String prefix = config.commandPrefix();

        // 管理员控制台指令（优先级最高）
        if (config.adminEnabled()) {
            String adminPrefix = config.adminCommandPrefix();
            if (raw.startsWith(adminPrefix + " ") || raw.equals(adminPrefix)) {
                handleAdminConsoleCommand(event, raw.length() > adminPrefix.length()
                    ? raw.substring(adminPrefix.length()).trim() : "");
                return true;
            }
        }

        // 绑定/解绑/查绑 特殊指令
        if (config.binding().enabled()) {
            if (raw.startsWith(config.binding().bindPrefix())) {
                handleBindCommand(event, raw.substring(config.binding().bindPrefix().length()).trim());
                return true;
            }
            if (raw.startsWith(config.binding().unbindPrefix())) {
                handleUnbindCommand(event);
                return true;
            }
            if (raw.startsWith(config.binding().queryPrefix())) {
                handleQueryBindCommand(event, raw.substring(config.binding().queryPrefix().length()).trim());
                return true;
            }
        }

        // 帮助指令
        if (raw.equals(config.helpPrefix())) {
            handleHelp(event);
            return true;
        }

        // 白名单指令
        var wlCfg = config.whitelist();
        if (wlCfg.enabled()) {
            if (raw.startsWith(prefix + "加白") || raw.startsWith(wlCfg.addPrefix())) {
                String args = raw.startsWith(wlCfg.addPrefix())
                    ? raw.substring(wlCfg.addPrefix().length()).trim()
                    : raw.substring((prefix + "加白").length()).trim();
                handleWhitelistAdd(event, args);
                return true;
            }
            if (raw.startsWith(prefix + "删白") || raw.startsWith(wlCfg.removePrefix())) {
                String args = raw.startsWith(wlCfg.removePrefix())
                    ? raw.substring(wlCfg.removePrefix().length()).trim()
                    : raw.substring((prefix + "删白").length()).trim();
                handleWhitelistRemove(event, args);
                return true;
            }
            if (raw.equals(wlCfg.listPrefix())) {
                handleWhitelistList(event);
                return true;
            }
        }

        // 发邮件指令
        if (raw.startsWith(prefix + "发邮件")) {
            handleSendMailCommand(event, raw.substring((prefix + "发邮件").length()).trim());
            return true;
        }

        // 签到积分指令
        var signinCfg = config.signin();
        if (signinCfg.enabled() && signInService != null) {
            if (raw.equals(signinCfg.signPrefix()) || signinCfg.aliases().contains(raw)) {
                handleSignIn(event);
                return true;
            }
            if (raw.equals(signinCfg.pointsQueryPrefix())) {
                handlePointsQuery(event);
                return true;
            }
            if (raw.equals(signinCfg.shopPrefix())) {
                handleShop(event);
                return true;
            }
            if (raw.startsWith(signinCfg.redeemPrefix())) {
                handleRedeem(event, raw.substring(signinCfg.redeemPrefix().length()).trim());
                return true;
            }
            if (raw.startsWith(signinCfg.transferPrefix())) {
                handleTransfer(event, raw.substring(signinCfg.transferPrefix().length()).trim());
                return true;
            }
            if (raw.startsWith(signinCfg.redPacketPrefix())) {
                handleRedPacket(event, raw.substring(signinCfg.redPacketPrefix().length()).trim());
                return true;
            }
            if (raw.equals(signinCfg.grabRedPacketPrefix())) {
                handleGrabRedPacket(event);
                return true;
            }
            if (raw.startsWith(signinCfg.activityPrefix())) {
                handleActivity(event, raw.substring(signinCfg.activityPrefix().length()).trim());
                return true;
            }
        }

        // 公告指令
        var announceCfg = config.announce();
        if (announceCfg.enabled() && raw.startsWith(announceCfg.prefix())) {
            handleAnnounce(event, raw.substring(announceCfg.prefix().length()).trim());
            return true;
        }

        // moderation 指令（踢人/封禁）
        var modCfg = config.moderation();
        if (modCfg.enabled()) {
            if (raw.startsWith(modCfg.kickPrefix())) {
                handleKick(event, raw.substring(modCfg.kickPrefix().length()).trim());
                return true;
            }
            if (raw.startsWith(modCfg.banPrefix())) {
                handleBan(event, raw.substring(modCfg.banPrefix().length()).trim());
                return true;
            }
        }

        // 黑名单管理指令（仅管理员可用）
        var blCfg = config.blacklist();
        if (blCfg.enabled()) {
            if (raw.startsWith(blCfg.addPrefix())) {
                handleBlacklistAdd(event, raw.substring(blCfg.addPrefix().length()).trim());
                return true;
            }
            if (raw.startsWith(blCfg.removePrefix())) {
                handleBlacklistRemove(event, raw.substring(blCfg.removePrefix().length()).trim());
                return true;
            }
            if (raw.equals(blCfg.listPrefix())) {
                handleBlacklistList(event);
                return true;
            }
        }

        // 自定义指令匹配
        if (!raw.startsWith(prefix)) return false;
        String commandBody = raw.substring(prefix.length()).trim();

        for (Map.Entry<String, QQBotCustomCommand> entry : config.customCommands().entrySet()) {
            String cmdName = entry.getKey();
            QQBotCustomCommand cmd = entry.getValue();

            if (commandBody.equals(cmdName) || commandBody.startsWith(cmdName + " ")) {
                // 权限检查：permission > 0 时仅允许配置文件 admin.qq-list 中的 QQ 使用
                if (cmd.permission() > 0 && !config.isAdmin(event.userId())) {
                    reply(event, config.messages().noPermission());
                    return true;
                }

                String args = commandBody.length() > cmdName.length()
                    ? commandBody.substring(cmdName.length()).trim()
                    : "";

                if (cmd.isBuiltin()) {
                    handleBuiltinCommand(event, cmd.builtinId(), args);
                } else if (cmd.isServerCommand()) {
                    handleServerCommand(event, cmd, args);
                } else if (cmd.isPapiQuery()) {
                    handlePapiQuery(event, cmd, args);
                }
                return true;
            }
        }
        return false;
    }

    private void handleBindCommand(OneBotEvent event, String playerName) {
        if (playerName.isEmpty()) {
            reply(event, "用法: " + config.binding().bindPrefix() + " <游戏名>");
            return;
        }
        QQBotBindService.BindResult result = bindService.requestBind(event.userId(), playerName);
        if (result.success()) {
            String msg = config.messages().bindCodeSent()
                .replace("{code}", result.message())
                .replace("{expire}", String.valueOf(config.binding().codeExpireSeconds()));
            reply(event, msg);
        } else {
            reply(event, result.message());
        }
    }

    private void handleUnbindCommand(OneBotEvent event) {
        QQBotBindService.BindResult result = bindService.unbindByQq(event.userId());
        if (result.success()) {
            String msg = config.messages().unbindSuccess().replace("{name}", result.message());
            reply(event, msg);

            // 解绑后自动移除白名单
            if (config.whitelist().enabled() && config.whitelist().autoRemoveOnUnbind()) {
                String wlCmd = config.whitelist().removeCommand().replace("{name}", result.message());
                Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), wlCmd));
            }
        } else {
            reply(event, result.message());
        }
    }

    private void handleQueryBindCommand(OneBotEvent event, String args) {
        long qqId = event.userId();
        if (!args.isEmpty()) {
            // 查询指定玩家的绑定
            QQBotBinding binding = bindService.findByPlayerName(args);
            if (binding != null) {
                reply(event, "玩家 " + binding.playerName() + " 绑定的QQ: " + binding.qqId());
            } else {
                reply(event, "玩家 " + args + " 未绑定QQ");
            }
            return;
        }
        QQBotBinding binding = bindService.findByQq(qqId);
        if (binding != null) {
            reply(event, "你已绑定玩家: " + binding.playerName());
        } else {
            reply(event, config.messages().notBound());
        }
    }

    private void handleWhitelistAdd(OneBotEvent event, String args) {
        if (!event.isSenderAdmin()) {
            reply(event, config.messages().noPermission());
            return;
        }
        if (args.isEmpty()) {
            reply(event, "用法: " + config.whitelist().addPrefix() + " <玩家名|QQ号>");
            return;
        }
        String input = args.trim();
        String playerName = input;
        if (input.matches("\\d+")) {
            long qqId = parseLong(input);
            QQBotBinding binding = bindService.findByQq(qqId);
            if (binding == null) {
                reply(event, "未找到QQ " + qqId + " 的绑定记录，请直接输入玩家名");
                return;
            }
            playerName = binding.playerName();
        }
        String cmd = config.whitelist().addCommand().replace("{name}", playerName);
        Bukkit.getScheduler().runTask(plugin, () ->
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        reply(event, config.messages().whitelistAdded().replace("{name}", playerName));
    }

    private void handleWhitelistRemove(OneBotEvent event, String args) {
        if (!event.isSenderAdmin()) {
            reply(event, config.messages().noPermission());
            return;
        }
        if (args.isEmpty()) {
            reply(event, "用法: " + config.whitelist().removePrefix() + " <玩家名|QQ号>");
            return;
        }
        String input = args.trim();
        String playerName = input;
        if (input.matches("\\d+")) {
            long qqId = parseLong(input);
            QQBotBinding binding = bindService.findByQq(qqId);
            if (binding == null) {
                reply(event, "未找到QQ " + qqId + " 的绑定记录，请直接输入玩家名");
                return;
            }
            playerName = binding.playerName();
        }
        String cmd = config.whitelist().removeCommand().replace("{name}", playerName);
        Bukkit.getScheduler().runTask(plugin, () ->
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        reply(event, config.messages().whitelistRemoved().replace("{name}", playerName));
    }

    private void handleWhitelistList(OneBotEvent event) {
        if (!event.isSenderAdmin()) {
            reply(event, config.messages().noPermission());
            return;
        }
        var whitelist = Bukkit.getWhitelistedPlayers();
        if (whitelist.isEmpty()) {
            reply(event, "白名单为空");
            return;
        }
        StringBuilder sb = new StringBuilder("═══ 白名单列表 ═══ (共 " + whitelist.size() + " 人)");
        for (var entry : whitelist) {
            sb.append("\n- ").append(entry.getName());
        }
        reply(event, sb.toString());
    }

    private void handleBuiltinCommand(OneBotEvent event, String builtinId, String args) {
        switch (builtinId) {
            case "online-list" -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    var players = Bukkit.getOnlinePlayers();
                    EssentialsQueryable ess = essentialsProvider != null ? essentialsProvider.get() : null;
                    StringBuilder sb = new StringBuilder();
                    sb.append("在线玩家 (").append(players.size()).append("/").append(Bukkit.getMaxPlayers()).append(")");
                    if (!players.isEmpty()) {
                        sb.append("\n");
                        List<String> names = new ArrayList<>();
                        for (Player p : players) {
                            StringBuilder tag = new StringBuilder(p.getName());
                            if (ess != null) {
                                java.util.UUID uid = p.getUniqueId();
                                if (ess.isAfk(uid)) tag.append(" [AFK]");
                                if (ess.isVanished(uid)) tag.append(" [\u9690\u8eab]");
                                if (ess.isGodMode(uid)) tag.append(" [\u65e0\u654c]");
                            }
                            names.add(tag.toString());
                        }
                        sb.append(String.join(", ", names));
                    }
                    reply(event, sb.toString());
                });
            }
            case "server-status" -> {
                // 必须在主线程获取实体/区块数据
                Bukkit.getScheduler().runTask(plugin, () -> {
                    double[] tps = getServerTps();
                    Runtime rt = Runtime.getRuntime();
                    long maxMem = rt.maxMemory() / 1024 / 1024;
                    long usedMem = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
                    int online = Bukkit.getOnlinePlayers().size();
                    int maxPlayers = Bukkit.getMaxPlayers();
                    String version = Bukkit.getVersion();

                    StringBuilder sb = new StringBuilder();
                    sb.append("服务器状态\n");
                    sb.append("版本: ").append(version).append("\n");
                    sb.append("在线: ").append(online).append("/").append(maxPlayers).append("\n");
                    sb.append("TPS: ").append(String.format("%.1f", tps[0])).append("\n");
                    sb.append("内存: ").append(usedMem).append("MB / ").append(maxMem).append("MB\n");

                    List<World> worlds = Bukkit.getWorlds();
                    int totalEntities = 0;
                    int totalChunks = 0;
                    for (World w : worlds) {
                        totalEntities += w.getEntities().size();
                        totalChunks += w.getLoadedChunks().length;
                    }
                    sb.append("实体: ").append(totalEntities).append("\n");
                    sb.append("区块: ").append(totalChunks);
                    reply(event, sb.toString());
                });
            }
            case "points-leaderboard" -> {
                if (signInService == null) {
                    reply(event, "积分系统未启用");
                    return;
                }
                var top = signInService.leaderboard(10);
                StringBuilder sb = new StringBuilder("═══ 积分排行榜 ═══");
                if (top.isEmpty()) {
                    sb.append("\n（暂无数据）");
                } else {
                    int rank = 1;
                    for (var acc : top) {
                        var binding = bindService.findByQq(acc.qqId());
                        String name = binding != null ? binding.playerName() : String.valueOf(acc.qqId());
                        sb.append("\n").append(rank++).append(". ").append(name)
                            .append(" - ").append(acc.balance()).append("积分");
                    }
                }
                reply(event, sb.toString());
            }
            default -> reply(event, "未知内置指令: " + builtinId);
        }
    }

    // ─── 签到积分处理 ─────────────────────────────────────

    private void handleSignIn(OneBotEvent event) {
        String result = signInService.signIn(event.userId());
        reply(event, result);
    }

    private void handlePointsQuery(OneBotEvent event) {
        reply(event, signInService.queryPoints(event.userId()));
    }

    private void handleShop(OneBotEvent event) {
        reply(event, signInService.buildShopList(event.userId()));
    }

    private void handleRedeem(OneBotEvent event, String prizeId) {
        if (prizeId.isEmpty()) {
            reply(event, "用法: " + config.signin().redeemPrefix() + " <奖品编号>\n发送 " + config.signin().shopPrefix() + " 查看可兑换奖品");
            return;
        }
        reply(event, signInService.redeem(event.userId(), prizeId));
    }

    private void handleTransfer(OneBotEvent event, String args) {
        if (args.isEmpty()) {
            reply(event, "用法: " + config.signin().transferPrefix() + " <QQ号> <数量>");
            return;
        }
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) {
            reply(event, "用法: " + config.signin().transferPrefix() + " <QQ号> <数量>");
            return;
        }
        long toQq;
        int amount;
        try {
            toQq = Long.parseLong(parts[0].trim());
            amount = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            reply(event, "参数格式错误，QQ号和数量都必须是数字");
            return;
        }
        reply(event, signInService.transfer(event.userId(), toQq, amount));
    }

    private void handleRedPacket(OneBotEvent event, String args) {
        if (args.isEmpty()) {
            reply(event, "用法: " + config.signin().redPacketPrefix() + " <总积分> <份数>");
            return;
        }
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) {
            reply(event, "用法: " + config.signin().redPacketPrefix() + " <总积分> <份数>");
            return;
        }
        int totalAmount;
        int count;
        try {
            totalAmount = Integer.parseInt(parts[0].trim());
            count = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            reply(event, "参数格式错误，积分和份数都必须是数字");
            return;
        }
        reply(event, signInService.sendRedPacket(event.userId(), event.groupId(), totalAmount, count));
    }

    private void handleGrabRedPacket(OneBotEvent event) {
        reply(event, signInService.grabRedPacket(event.userId(), event.groupId()));
    }

    private void handleActivity(OneBotEvent event, String args) {
        String mode = args.isEmpty() ? "month" : args.trim();
        reply(event, signInService.buildActivityLeaderboard(mode, 10));
    }

    // ─── 公告处理 ─────────────────────────────────────────

    private void handleAnnounce(OneBotEvent event, String content) {
        if (!event.isSenderAdmin() && !config.isAdmin(event.userId())) {
            reply(event, config.messages().noPermission());
            return;
        }
        if (content.isEmpty()) {
            reply(event, "用法: " + config.announce().prefix() + " <公告内容>");
            return;
        }
        var ac = config.announce();
        String gameMsg = org.bukkit.ChatColor.translateAlternateColorCodes('&',
            ac.gameFormat().replace("{content}", content));
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage(gameMsg);
            if (ac.titleEnabled()) {
                String title = org.bukkit.ChatColor.translateAlternateColorCodes('&', ac.titleText());
                String subtitle = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    ac.subtitleFormat().replace("{content}", content));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle(title, subtitle, 10, 70, 20);
                }
            }
        });
        reply(event, ac.qqReceipt());
    }

    // ─── moderation 处理 ─────────────────────────────────

    private void handleKick(OneBotEvent event, String args) {
        if (!event.isSenderAdmin() && !config.isAdmin(event.userId())) {
            reply(event, config.messages().noPermission());
            return;
        }
        if (args.isEmpty()) {
            reply(event, "用法: " + config.moderation().kickPrefix() + " <玩家名> [原因]");
            return;
        }
        String[] parts = args.split("\\s+", 2);
        String name = parts[0];
        String reason = parts.length > 1 ? parts[1] : "QQ群管理踢出";
        String cmd = config.moderation().kickCommand()
            .replace("{name}", name)
            .replace("{reason}", reason);
        Bukkit.getScheduler().runTask(plugin, () ->
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        reply(event, "已踢出玩家: " + name + "（原因: " + reason + "）");
    }

    private void handleBan(OneBotEvent event, String args) {
        if (!event.isSenderAdmin() && !config.isAdmin(event.userId())) {
            reply(event, config.messages().noPermission());
            return;
        }
        if (args.isEmpty()) {
            reply(event, "用法: " + config.moderation().banPrefix() + " <玩家名> [原因]");
            return;
        }
        String[] parts = args.split("\\s+", 2);
        String name = parts[0];
        String reason = parts.length > 1 ? parts[1] : "QQ群管理封禁";
        String cmd = config.moderation().banCommand()
            .replace("{name}", name)
            .replace("{reason}", reason);
        Bukkit.getScheduler().runTask(plugin, () ->
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        reply(event, "已封禁玩家: " + name + "（原因: " + reason + "）");
    }

    private void handleServerCommand(OneBotEvent event, QQBotCustomCommand cmd, String args) {
        if (args.isEmpty()) {
            reply(event, "用法: " + config.commandPrefix() + cmd.name() + " <命令>");
            return;
        }
        String command = cmd.command().replace("{args}", args);
        Bukkit.getScheduler().runTask(plugin, () ->
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        reply(event, config.messages().commandExecuted().replace("{command}", command));
    }

    private void handlePapiQuery(OneBotEvent event, QQBotCustomCommand cmd, String args) {
        String playerName = args.isEmpty() ? resolvePlayerNameFromQq(event.userId()) : args;
        if (playerName == null || playerName.isEmpty()) {
            reply(event, "请指定玩家名，或先绑定QQ");
            return;
        }
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null || !player.isOnline()) {
            reply(event, config.messages().playerNotFound().replace("{name}", playerName));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                String format = cmd.format().replace("{name}", player.getName());
                List<String> placeholders = cmd.placeholders();
                for (int i = 0; i < placeholders.size(); i++) {
                    String placeholder = placeholders.get(i);
                    String value = parsePlaceholder(player, placeholder);
                    format = format.replace("{" + i + "}", value);
                }
                reply(event, format);
            } catch (Exception e) {
                logger.warning("[QQBot] PAPI 查询失败: " + e.getMessage());
                reply(event, "查询失败: " + e.getMessage());
            }
        });
    }

    @Nullable
    private String resolvePlayerNameFromQq(long qqId) {
        QQBotBinding binding = bindService.findByQq(qqId);
        return binding != null ? binding.playerName() : null;
    }

    private String parsePlaceholder(Player player, String placeholder) {
        return placeholderResolver.applyPlaceholders(player, placeholder);
    }


    private static double[] getServerTps() {
        try {
            // Paper API
            var method = Bukkit.class.getMethod("getTPS");
            return (double[]) method.invoke(null);
        } catch (Exception ignored) {}
        // Fallback: NMS via reflection
        try {
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            java.lang.reflect.Field tpsField = server.getClass().getField("recentTps");
            return (double[]) tpsField.get(server);
        } catch (Exception ignored) {}
        return new double[]{20.0, 20.0, 20.0};
    }

    private void handleAdminConsoleCommand(OneBotEvent event, String commandLine) {
        long qqId = event.userId();
        if (!config.isAdmin(qqId)) {
            reply(event, config.messages().noPermission());
            return;
        }
        if (commandLine.isEmpty()) {
            reply(event, "用法: " + config.adminCommandPrefix() + " <服务器命令>");
            return;
        }
        if (config.debug()) {
            logger.info("[QQBot/Admin] QQ=" + qqId + " 执行控制台命令: " + commandLine);
        }
        String cmd = commandLine;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                if (dispatched) {
                    reply(event, config.messages().adminCommandExecuted()
                        .replace("{command}", cmd)
                        .replace("{qq}", String.valueOf(qqId)));
                } else {
                    reply(event, config.messages().adminCommandFailed()
                        .replace("{command}", cmd));
                }
            } catch (Exception e) {
                reply(event, config.messages().adminCommandFailed()
                    .replace("{command}", cmd) + "\n错误: " + e.getMessage());
                logger.warning("[QQBot/Admin] 命令执行异常: " + cmd + " → " + e.getMessage());
            }
        });
    }

    private void handleBlacklistAdd(OneBotEvent event, String args) {
        if (!event.isSenderAdmin() && !config.isAdmin(event.userId())) {
            reply(event, config.messages().blacklistNoPermission());
            return;
        }
        if (args.isEmpty()) {
            reply(event, "用法: " + config.blacklist().addPrefix() + " <QQ号|玩家名>");
            return;
        }
        String input = args.trim();
        long targetQq;
        if (!input.matches("\\d+")) {
            QQBotBinding binding = bindService.findByPlayerName(input);
            if (binding == null) {
                reply(event, "未找到玩家 " + input + " 的绑定记录，请直接输入QQ号");
                return;
            }
            targetQq = binding.qqId();
        } else {
            targetQq = parseLong(input);
        }
        if (targetQq <= 0) {
            reply(event, "用法: " + config.blacklist().addPrefix() + " <QQ号|玩家名>");
            return;
        }
        if (config.blacklist().isConfigBlacklisted(targetQq)) {
            reply(event, "QQ " + targetQq + " 已在配置文件黑名单中，请修改配置文件后重载。");
            return;
        }
        if (repository.isBlacklisted(targetQq)) {
            reply(event, config.messages().blacklistAlready().replace("{qq}", String.valueOf(targetQq)));
            return;
        }
        repository.addBlacklist(targetQq, event.userId());
        reply(event, config.messages().blacklistAdded().replace("{qq}", String.valueOf(targetQq)));
    }

    private void handleBlacklistRemove(OneBotEvent event, String args) {
        if (!event.isSenderAdmin() && !config.isAdmin(event.userId())) {
            reply(event, config.messages().blacklistNoPermission());
            return;
        }
        if (args.isEmpty()) {
            reply(event, "用法: " + config.blacklist().removePrefix() + " <QQ号|玩家名>");
            return;
        }
        String input = args.trim();
        long targetQq;
        if (!input.matches("\\d+")) {
            QQBotBinding binding = bindService.findByPlayerName(input);
            if (binding == null) {
                reply(event, "未找到玩家 " + input + " 的绑定记录，请直接输入QQ号");
                return;
            }
            targetQq = binding.qqId();
        } else {
            targetQq = parseLong(input);
        }
        if (targetQq <= 0) {
            reply(event, "用法: " + config.blacklist().removePrefix() + " <QQ号|玩家名>");
            return;
        }
        if (config.blacklist().isConfigBlacklisted(targetQq)) {
            reply(event, "QQ " + targetQq + " 在配置文件黑名单中，请修改配置文件后重载。");
            return;
        }
        if (!repository.isBlacklisted(targetQq)) {
            reply(event, config.messages().blacklistNotFound().replace("{qq}", String.valueOf(targetQq)));
            return;
        }
        repository.removeBlacklist(targetQq);
        reply(event, config.messages().blacklistRemoved().replace("{qq}", String.valueOf(targetQq)));
    }

    private void handleBlacklistList(OneBotEvent event) {
        if (!event.isSenderAdmin() && !config.isAdmin(event.userId())) {
            reply(event, config.messages().blacklistNoPermission());
            return;
        }
        List<Long> list = repository.getBlacklist();
        for (Long qq : config.blacklist().qqList()) {
            if (!list.contains(qq)) list.add(qq);
        }
        if (list.isEmpty()) {
            reply(event, config.messages().blacklistListEmpty());
            return;
        }
        StringBuilder sb = new StringBuilder(config.messages().blacklistListHeader());
        for (Long qq : list) {
            sb.append("\n").append(config.messages().blacklistListItem().replace("{qq}", String.valueOf(qq)));
        }
        reply(event, sb.toString());
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private void handleSendMailCommand(OneBotEvent event, String args) {
        // 仅管理员可用
        if (!event.isSenderAdmin() && !config.isAdmin(event.userId())) {
            reply(event, config.messages().noPermission());
            return;
        }
        // 格式: 玩家名 预设ID
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            reply(event, "用法: #发邮件 <玩家名> <预设ID>");
            return;
        }
        String playerName = parts[0];
        String presetId = parts[1];
        if (mailProvider == null) {
            reply(event, "邮件模块不可用");
            return;
        }
        MailDispatchable mail = mailProvider.get();
        if (mail == null) {
            reply(event, "邮件模块不可用");
            return;
        }
        boolean success = mail.dispatchPreset(presetId, playerName, "QQBot");
        if (success) {
            reply(event, "已向 " + playerName + " 发送邮件预设: " + presetId);
        } else {
            reply(event, "邮件发送失败，请检查预设ID或玩家名是否正确");
        }
    }

    private void handleHelp(OneBotEvent event) {
        StringBuilder sb = new StringBuilder(config.messages().helpHeader());
        sb.append("\n\n【通用指令】");
        sb.append("\n").append(config.helpPrefix()).append(" — 显示本帮助");

        var bindCfg = config.binding();
        if (bindCfg.enabled()) {
            sb.append("\n").append(bindCfg.bindPrefix()).append(" <玩家名> — 申请绑定");
            sb.append("\n").append(bindCfg.unbindPrefix()).append(" — 解除绑定");
            sb.append("\n").append(bindCfg.queryPrefix()).append(" [玩家名] — 查询绑定");
        }

        var wlCfg = config.whitelist();
        if (wlCfg.enabled()) {
            sb.append("\n\n【白名单】");
            sb.append("\n").append(wlCfg.addPrefix()).append(" <玩家名|QQ号>");
            sb.append("\n").append(wlCfg.removePrefix()).append(" <玩家名|QQ号>");
            sb.append("\n").append(wlCfg.listPrefix());
        }

        var signCfg = config.signin();
        if (signCfg.enabled()) {
            sb.append("\n\n【签到积分】");
            sb.append("\n").append(signCfg.signPrefix()).append(" — 每日签到");
            for (String alias : signCfg.aliases()) {
                sb.append(" / ").append(alias);
            }
            sb.append("\n").append(signCfg.pointsQueryPrefix()).append(" — 查询积分");
            sb.append("\n").append(signCfg.shopPrefix()).append(" — 查看兑换商店");
            sb.append("\n").append(signCfg.redeemPrefix()).append(" <编号> — 兑换奖品");
            sb.append("\n").append(signCfg.transferPrefix()).append(" <QQ号> <数量> — 积分转账");
            sb.append("\n").append(signCfg.redPacketPrefix()).append(" <总积分> <份数> — 发拼手气红包");
            sb.append("\n").append(signCfg.grabRedPacketPrefix()).append(" — 抢红包");
            sb.append("\n").append(signCfg.activityPrefix()).append(" [week|month] — 活跃度排行");
        }

        if (config.announce().enabled()) {
            sb.append("\n\n【公告】");
            sb.append("\n").append(config.announce().prefix()).append(" <内容> — 发布公告");
        }

        var modCfg = config.moderation();
        if (modCfg.enabled()) {
            sb.append("\n\n【群管理】");
            sb.append("\n").append(modCfg.kickPrefix()).append(" <玩家名> [原因] — 踢出玩家");
            sb.append("\n").append(modCfg.banPrefix()).append(" <玩家名> [原因] — 封禁玩家");
        }

        var blCfg = config.blacklist();
        if (blCfg.enabled()) {
            sb.append("\n\n【黑名单】");
            sb.append("\n").append(blCfg.addPrefix()).append(" <QQ号|玩家名>");
            sb.append("\n").append(blCfg.removePrefix()).append(" <QQ号|玩家名>");
            sb.append("\n").append(blCfg.listPrefix());
        }

        if (!config.customCommands().isEmpty()) {
            sb.append("\n\n【自定义指令】");
            for (Map.Entry<String, QQBotCustomCommand> entry : config.customCommands().entrySet()) {
                sb.append("\n").append(config.commandPrefix()).append(entry.getKey());
            }
        }

        sb.append("\n\n").append(config.messages().helpFooter());
        reply(event, sb.toString());
    }

    private void reply(OneBotEvent event, String message) {
        client.sendGroupMessage(event.groupId(), message);
    }
}
