package xuanmo.arcartxsuite.qqbot.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public record QQBotConfiguration(
    boolean debug,
    String serverId,
    QQBotOneBotConfig onebot,
    List<QQBotGroupConfig> groups,
    QQBotBindingConfig binding,
    QQBotWhitelistConfig whitelist,
    QQBotWhitelistLoginConfig whitelistLogin,
    boolean adminEnabled,
    List<Long> adminQQs,
    String adminCommandPrefix,
    String commandPrefix,
    String helpPrefix,
    Map<String, QQBotCustomCommand> customCommands,
    QQBotStorageConfig storage,
    QQBotMessagesConfig messages,
    QQBotSignInConfig signin,
    List<QQBotPrizeConfig> prizes,
    QQBotMonitorConfig monitor,
    List<QQBotScheduledMessageConfig> scheduledMessages,
    QQBotBroadcastConfig broadcast,
    QQBotWelcomeConfig welcome,
    QQBotAutoReplyConfig autoReply,
    QQBotAnnounceConfig announce,
    QQBotModerationConfig moderation,
    QQBotBlacklistConfig blacklist,
    boolean atToGameEnabled
) {

    public boolean isAdmin(long qqId) {
        return adminEnabled && adminQQs.contains(qqId);
    }

    public QQBotPrizeConfig findPrize(String id) {
        for (QQBotPrizeConfig p : prizes) {
            if (p.id().equalsIgnoreCase(id)) return p;
        }
        return null;
    }

    public static QQBotConfiguration load(YamlConfiguration yaml, Logger logger) {
        boolean debug = yaml.getBoolean("settings.debug", false);
        String serverId = yaml.getString("settings.server-id", "survival");

        QQBotOneBotConfig onebot = new QQBotOneBotConfig(
            yaml.getString("onebot.ws-url", "ws://127.0.0.1:8080"),
            yaml.getString("onebot.access-token", ""),
            yaml.getInt("onebot.reconnect-interval-seconds", 10),
            yaml.getInt("onebot.heartbeat-interval-seconds", 30),
            yaml.getString("onebot.snowluma.dir", "snowluma"),
            yaml.getBoolean("onebot.snowluma.auto-start", false)
        );

        List<QQBotGroupConfig> groups = new ArrayList<>();
        var groupsList = yaml.getMapList("groups");
        for (Object groupObj : groupsList) {
            if (groupObj instanceof Map<?, ?> map) {
                long groupId = toLong(map.get("group-id"));
                if (groupId <= 0) {
                    logger.warning("QQBot: 忽略无效 group-id: " + map.get("group-id"));
                    continue;
                }
                groups.add(new QQBotGroupConfig(
                    groupId,
                    toStr(map.get("sync-mode"), "both"),
                    toStr(map.get("game-to-qq"), "[MC] {player}: {message}"),
                    toStr(map.get("qq-to-game"), "&7[QQ] &f{nick}: &7{message}"),
                    toBoolean(map.get("commands-enabled")),
                    toStr(map.get("join-message"), "[MC] {player} 加入了服务器"),
                    toStr(map.get("quit-message"), "[MC] {player} 离开了服务器")
                ));
            }
        }

        QQBotBindingConfig binding = new QQBotBindingConfig(
            yaml.getBoolean("binding.enabled", true),
            yaml.getString("binding.method", "code"),
            yaml.getInt("binding.code-expire-seconds", 300),
            yaml.getInt("binding.max-bindings-per-qq", 1),
            yaml.getString("binding.bind-prefix", "#绑定"),
            yaml.getString("binding.unbind-prefix", "#解绑"),
            yaml.getString("binding.query-prefix", "#查绑")
        );

        QQBotWhitelistConfig whitelist = new QQBotWhitelistConfig(
            yaml.getBoolean("whitelist.enabled", true),
            yaml.getBoolean("whitelist.auto-add-on-bind", true),
            yaml.getBoolean("whitelist.auto-remove-on-unbind", true),
            yaml.getString("whitelist.add-command", "whitelist add {name}"),
            yaml.getString("whitelist.remove-command", "whitelist remove {name}"),
            yaml.getString("whitelist.add-prefix", "#白名单添加"),
            yaml.getString("whitelist.remove-prefix", "#白名单移除"),
            yaml.getString("whitelist.list-prefix", "#白名单列表")
        );

        QQBotWhitelistLoginConfig whitelistLogin = new QQBotWhitelistLoginConfig(
            yaml.getBoolean("whitelist-login.enabled", false),
            yaml.getBoolean("whitelist-login.microsoft-pass", true),
            yaml.getBoolean("whitelist-login.littleskin-require-bind", true),
            yaml.getBoolean("whitelist-login.deny-offline", true),
            yaml.getString("whitelist-login.kick-not-bound", "&c\u4f60\u8fd8\u672a\u5728QQ\u7fa4\u5b8c\u6210\u7ed1\u5b9a\u8ba4\u8bc1\n&7\u8bf7\u5728QQ\u7fa4\u53d1\u9001: #\u7ed1\u5b9a {name}\n&7\u5b8c\u6210\u9a8c\u8bc1\u540e\u65b9\u53ef\u8fdb\u5165\u6e38\u620f"),
            yaml.getString("whitelist-login.kick-offline", "&c\u672c\u670d\u52a1\u5668\u4ec5\u5141\u8bb8\u6b63\u7248/LittleSkin \u8d26\u53f7\u767b\u5f55"),
            yaml.getString("whitelist-login.kick-denied", "&c\u4f60\u6ca1\u6709\u6743\u9650\u8fdb\u5165\u672c\u670d\u52a1\u5668")
        );

        boolean adminEnabled = yaml.getBoolean("admin.enabled", false);
        List<Long> adminQQs = new ArrayList<>();
        for (Object obj : yaml.getList("admin.qq-list", List.of())) {
            long qq = toLong(obj);
            if (qq > 0) adminQQs.add(qq);
        }
        String adminCommandPrefix = yaml.getString("admin.command-prefix", "#cmd");

        String commandPrefix = yaml.getString("command-prefix", "#");
        String helpPrefix = yaml.getString("help-prefix", "#帮助");

        Map<String, QQBotCustomCommand> customCommands = new LinkedHashMap<>();
        ConfigurationSection cmdSection = yaml.getConfigurationSection("custom-commands");
        if (cmdSection != null) {
            for (String key : cmdSection.getKeys(false)) {
                ConfigurationSection cs = cmdSection.getConfigurationSection(key);
                if (cs == null) continue;
                customCommands.put(key, new QQBotCustomCommand(
                    key,
                    cs.getInt("permission", 0),
                    cs.getString("type", "builtin"),
                    cs.getString("builtin-id", ""),
                    cs.getString("command", ""),
                    cs.getString("usage", ""),
                    cs.getStringList("placeholders"),
                    cs.getString("format", "")
                ));
            }
        }

        QQBotStorageConfig storage = new QQBotStorageConfig(
            yaml.getString("storage.mode", "sqlite"),
            yaml.getString("storage.sqlite-file", "qqbot.db"),
            yaml.getString("storage.mysql.host", "127.0.0.1"),
            yaml.getInt("storage.mysql.port", 3306),
            yaml.getString("storage.mysql.database", "arcartxsuite"),
            yaml.getString("storage.mysql.username", "root"),
            yaml.getString("storage.mysql.password", ""),
            yaml.getString("storage.mysql.table-prefix", "axs_qqbot_"),
            yaml.getInt("storage.pool-size", 4)
        );

        QQBotMessagesConfig messages = new QQBotMessagesConfig(
            yaml.getString("messages.prefix", "&8[&bQQBot&8] &7"),
            yaml.getString("messages.bind-code-sent", "验证码已生成: {code}\n请在 {expire} 秒内到游戏中输入:\n/qqbot bind {code}"),
            yaml.getString("messages.bind-success", "绑定成功！\nQQ: {qq} ↔ 游戏: {name}"),
            yaml.getString("messages.bind-success-game", "&a绑定成功！QQ: &e{qq} &a↔ 游戏: &e{name}"),
            yaml.getString("messages.already-bound", "该QQ已绑定玩家 {name}，如需换绑请先解绑"),
            yaml.getString("messages.unbind-success", "已解除绑定: {name}"),
            yaml.getString("messages.unbind-success-game", "&c已解除与 QQ &e{qq} &c的绑定"),
            yaml.getString("messages.not-bound", "该账号尚未绑定"),
            yaml.getString("messages.code-expired", "验证码已过期，请重新获取"),
            yaml.getString("messages.code-invalid", "&c验证码无效或已过期"),
            yaml.getString("messages.whitelist-added", "已为 {name} 添加白名单"),
            yaml.getString("messages.whitelist-removed", "已移除 {name} 的白名单"),
            yaml.getString("messages.bot-connected", "&aQQ 机器人已连接"),
            yaml.getString("messages.bot-disconnected", "&cQQ 机器人连接断开"),
            yaml.getString("messages.no-permission", "你没有权限执行此指令"),
            yaml.getString("messages.player-not-found", "找不到玩家: {name}"),
            yaml.getString("messages.command-executed", "命令已执行: {command}"),
            yaml.getString("messages.admin-command-executed", "[管理] 命令已执行: {command}"),
            yaml.getString("messages.admin-command-failed", "[管理] 命令执行失败: {command}"),
            yaml.getString("messages.signin-success", "签到成功！获得 {points} 积分\n连续签到 {streak} 天，当前积分 {balance}"),
            yaml.getString("messages.signin-already", "你今天已经签到过了\n连续签到 {streak} 天，当前积分 {balance}"),
            yaml.getString("messages.points-query", "你的积分: {balance}\n累计获得 {earned}，已消费 {spent}"),
            yaml.getString("messages.shop-header", "═══ 积分兑换商店 ═══\n（发送 #兑换 <编号> 兑换奖品）"),
            yaml.getString("messages.shop-item", "[{id}] {name} - {cost}积分\n  {desc}"),
            yaml.getString("messages.shop-footer", "═══════════════\n你的积分: {balance}"),
            yaml.getString("messages.redeem-success", "兑换成功！\n奖品 [{name}] 将通过邮件发放给 {player}\n已扣除 {cost} 积分，剩余 {balance}"),
            yaml.getString("messages.redeem-no-points", "积分不足！兑换 [{name}] 需要 {cost} 积分，你只有 {balance}"),
            yaml.getString("messages.redeem-not-found", "找不到该奖品编号: {id}"),
            yaml.getString("messages.redeem-not-bound", "兑换该奖品需要先绑定游戏账号\n请发送: #绑定 <游戏名>"),
            yaml.getString("messages.redeem-limit", "[{name}] 今日兑换次数已达上限"),
            yaml.getString("messages.redeem-mail-failed", "奖品发放失败（邮件系统不可用），积分已退还"),
            yaml.getString("messages.blacklist-blocked", "你的账号已被禁止使用本机器人的功能"),
            yaml.getString("messages.blacklist-added", "已将 QQ {qq} 加入黑名单"),
            yaml.getString("messages.blacklist-removed", "已将 QQ {qq} 移出黑名单"),
            yaml.getString("messages.blacklist-already", "QQ {qq} 已在黑名单中"),
            yaml.getString("messages.blacklist-not-found", "QQ {qq} 不在黑名单中"),
            yaml.getString("messages.blacklist-list-header", "═══ 黑名单列表 ═══"),
            yaml.getString("messages.blacklist-list-item", "- {qq}"),
            yaml.getString("messages.blacklist-list-empty", "黑名单为空"),
            yaml.getString("messages.blacklist-no-permission", "你没有权限管理黑名单"),
            yaml.getString("messages.help-header", "═══ 可用指令 ═══"),
            yaml.getString("messages.help-footer", "═══════════════")
        );

        QQBotSignInConfig signin = new QQBotSignInConfig(
            yaml.getBoolean("signin.enabled", true),
            yaml.getInt("signin.base-points", 10),
            yaml.getInt("signin.streak-bonus", 2),
            yaml.getInt("signin.max-streak-bonus", 50),
            yaml.getString("signin.sign-prefix", "#签到"),
            yaml.getStringList("signin.aliases"),
            yaml.getString("signin.shop-prefix", "#商店"),
            yaml.getString("signin.redeem-prefix", "#兑换"),
            yaml.getString("signin.points-query-prefix", "#积分"),
            yaml.getString("signin.transfer-prefix", "#转账"),
            yaml.getString("signin.red-packet-prefix", "#红包"),
            yaml.getString("signin.grab-red-packet-prefix", "#抢红包"),
            yaml.getString("signin.activity-prefix", "#活跃排行")
        );

        List<QQBotPrizeConfig> prizes = new ArrayList<>();
        for (Object obj : yaml.getMapList("prizes")) {
            if (obj instanceof Map<?, ?> map) {
                String id = toStr(map.get("id"), "");
                if (id.isBlank()) continue;
                prizes.add(new QQBotPrizeConfig(
                    id,
                    toStr(map.get("name"), id),
                    (int) toLong(map.get("cost")),
                    toStr(map.get("mail-preset-id"), ""),
                    toStr(map.get("description"), ""),
                    (int) toLong(map.get("limit-per-day")),
                    toBoolean(map.get("require-bind")),
                    toDouble(map.get("discount-rate"), 1.0),
                    toLong(map.get("discount-until"))
                ));
            }
        }

        QQBotMonitorConfig monitor = new QQBotMonitorConfig(
            yaml.getBoolean("monitor.enabled", false),
            yaml.getDouble("monitor.tps-threshold", 15.0),
            yaml.getInt("monitor.memory-threshold-percent", 90),
            yaml.getInt("monitor.check-interval-seconds", 60),
            yaml.getInt("monitor.cooldown-seconds", 300),
            parseLongList(yaml.getList("monitor.alarm-groups", List.of())),
            yaml.getString("monitor.tps-alarm-format", "⚠ 服务器告警\nTPS 过低: {tps}（阈值 {threshold}）"),
            yaml.getString("monitor.memory-alarm-format", "⚠ 服务器告警\n内存占用过高: {used}MB/{max}MB ({percent}%)")
        );

        List<QQBotScheduledMessageConfig> scheduledMessages = new ArrayList<>();
        for (Object obj : yaml.getMapList("scheduled-messages")) {
            if (obj instanceof Map<?, ?> map) {
                String id = toStr(map.get("id"), "");
                if (id.isBlank()) continue;
                scheduledMessages.add(new QQBotScheduledMessageConfig(
                    id,
                    toStr(map.get("mode"), "interval"),
                    (int) toLong(map.get("interval-seconds")),
                    toStr(map.get("daily-time"), "12:00"),
                    toStr(map.get("message"), ""),
                    parseLongList(map.get("target-groups"))
                ));
            }
        }

        QQBotBroadcastConfig broadcast = new QQBotBroadcastConfig(
            yaml.getBoolean("broadcast.kill.enabled", false),
            yaml.getString("broadcast.kill.format", "🗡 {killer} 击杀了 {victim}"),
            yaml.getBoolean("broadcast.kill.boss-only", true),
            yaml.getStringList("broadcast.kill.boss-keywords"),
            yaml.getBoolean("broadcast.kill.player-kill-only", false),
            yaml.getBoolean("broadcast.death.enabled", false),
            yaml.getString("broadcast.death.format", "☠ {player} 死亡了")
        );

        QQBotWelcomeConfig welcome = new QQBotWelcomeConfig(
            yaml.getBoolean("welcome.enabled", false),
            yaml.getString("welcome.message", "欢迎新成员加入！\n发送 #绑定 <游戏名> 关联你的游戏账号\n发送 #帮助 查看可用指令")
        );

        List<QQBotAutoReplyConfig.AutoReplyRule> autoReplyRules = new ArrayList<>();
        for (Object obj : yaml.getMapList("auto-reply.rules")) {
            if (obj instanceof Map<?, ?> map) {
                List<String> keywords = parseStringList(map.get("keywords"));
                if (keywords.isEmpty()) continue;
                autoReplyRules.add(new QQBotAutoReplyConfig.AutoReplyRule(
                    keywords,
                    toStr(map.get("response"), ""),
                    toBoolean(map.get("exact-match"))
                ));
            }
        }
        QQBotAutoReplyConfig autoReply = new QQBotAutoReplyConfig(
            yaml.getBoolean("auto-reply.enabled", false),
            yaml.getInt("auto-reply.cooldown-seconds", 30),
            autoReplyRules
        );

        QQBotAnnounceConfig announce = new QQBotAnnounceConfig(
            yaml.getBoolean("announce.enabled", false),
            yaml.getString("announce.prefix", "#公告"),
            yaml.getString("announce.game-format", "&e&l[群公告] &f{content}"),
            yaml.getString("announce.qq-receipt", "✓ 公告已发布到游戏内"),
            yaml.getBoolean("announce.title-enabled", true),
            yaml.getString("announce.title-text", "&e群公告"),
            yaml.getString("announce.subtitle-format", "&f{content}")
        );

        QQBotModerationConfig.AutoModeration autoMod = new QQBotModerationConfig.AutoModeration(
            yaml.getBoolean("moderation.auto-moderation.enabled", false),
            parseStringList(yaml.getList("moderation.auto-moderation.keywords", List.of())),
            yaml.getInt("moderation.auto-moderation.ban-duration-seconds", 600),
            yaml.getInt("moderation.auto-moderation.cooldown-seconds", 300)
        );
        QQBotModerationConfig moderation = new QQBotModerationConfig(
            yaml.getBoolean("moderation.enabled", false),
            yaml.getString("moderation.kick-prefix", "#踢"),
            yaml.getString("moderation.ban-prefix", "#封禁"),
            yaml.getString("moderation.kick-command", "kick {name} {reason}"),
            yaml.getString("moderation.ban-command", "ban {name} {reason}"),
            yaml.getBoolean("moderation.sync-ban.enabled", false),
            yaml.getString("moderation.sync-ban.command", "tempban {name} {duration} {reason}"),
            yaml.getBoolean("moderation.sync-ban.use-duration", true),
            yaml.getString("moderation.sync-ban.reason", "QQ群禁言同步"),
            autoMod
        );

        List<Long> blacklistQQs = new ArrayList<>();
        for (Object obj : yaml.getList("blacklist.qq-list", List.of())) {
            long qq = toLong(obj);
            if (qq > 0) blacklistQQs.add(qq);
        }
        QQBotBlacklistConfig blacklist = new QQBotBlacklistConfig(
            yaml.getBoolean("blacklist.enabled", false),
            blacklistQQs,
            yaml.getString("blacklist.add-prefix", "#黑名单添加"),
            yaml.getString("blacklist.remove-prefix", "#黑名单移除"),
            yaml.getString("blacklist.list-prefix", "#黑名单列表")
        );

        return new QQBotConfiguration(
            debug, serverId, onebot, groups, binding, whitelist, whitelistLogin,
            adminEnabled, adminQQs, adminCommandPrefix,
            commandPrefix, helpPrefix, customCommands, storage, messages,
            signin, prizes, monitor, scheduledMessages, broadcast,
            welcome, autoReply, announce, moderation, blacklist,
            yaml.getBoolean("at-to-game.enabled", true)
        );
    }

    private static List<Long> parseLongList(Object obj) {
        List<Long> list = new ArrayList<>();
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                long v = toLong(o);
                if (v > 0) list.add(v);
            }
        }
        return list;
    }

    private static List<String> parseStringList(Object obj) {
        List<String> list = new ArrayList<>();
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o != null) list.add(String.valueOf(o));
            }
        }
        return list;
    }

    private static long toLong(Object obj) {
        if (obj instanceof Number num) return num.longValue();
        if (obj instanceof String str) {
            try { return Long.parseLong(str); } catch (NumberFormatException ignored) {}
        }
        return 0L;
    }

    private static boolean toBoolean(Object obj) {
        if (obj == null) return true;
        if (obj instanceof Boolean b) return b;
        if (obj instanceof String str) return Boolean.parseBoolean(str);
        return false;
    }

    private static double toDouble(Object obj, double defaultValue) {
        if (obj instanceof Number num) return num.doubleValue();
        if (obj instanceof String str) {
            try { return Double.parseDouble(str); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private static String toStr(Object obj, String defaultValue) {
        if (obj == null) return defaultValue;
        return String.valueOf(obj);
    }
}
