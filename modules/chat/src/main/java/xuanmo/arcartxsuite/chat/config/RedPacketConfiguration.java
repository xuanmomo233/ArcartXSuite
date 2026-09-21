package xuanmo.arcartxsuite.chat.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 红包功能配置。
 * <p>
 * 对应 {@code ArcartXChat.yml} 的 {@code redpacket} 配置节。
 *
 * @param enabled            功能总开关
 * @param sendPermission     发红包权限节点，空串=所有人可用
 * @param claimPermission    抢红包权限节点，空串=所有人可抢
 * @param adminPermission    管理员发红包权限节点，空串=仅需管理员命令本身的权限
 * @param currency           玩家红包使用的货币 ID
 * @param cardId             红包聊天卡片 ID，空串=仅文字播报
 * @param presetsDirectory   预设红包目录（相对模块数据目录）
 * @param adminChannel       控制台发红包时使用的频道，空串=默认频道
 * @param minTotal           红包最小总金额
 * @param maxTotal           红包最大总金额
 * @param minPerClaim        每份最小金额
 * @param maxCount           最大份数
 * @param sendCooldownSeconds 发红包冷却（秒）
 * @param expireMinutes      红包有效期（分钟）
 * @param refundOnExpire     过期未领完是否退款给发送者（仅玩家货币红包）
 * @param senderCanClaim     发送者能否抢自己的红包
 * @param announceClaims     是否在频道内播报领取信息
 * @param announceFinish     抢完时是否播报（含手气王）
 * @param textAlongside      发卡片的同时是否附带可点击的文字行
 */
public record RedPacketConfiguration(
    boolean enabled,
    String sendPermission,
    String claimPermission,
    String adminPermission,
    String currency,
    String cardId,
    String presetsDirectory,
    String adminChannel,
    double minTotal,
    double maxTotal,
    double minPerClaim,
    int maxCount,
    long sendCooldownSeconds,
    long expireMinutes,
    boolean refundOnExpire,
    boolean senderCanClaim,
    boolean announceClaims,
    boolean announceFinish,
    boolean textAlongside
) {

    /**
     * 从配置节加载红包配置。
     *
     * @param section redpacket 配置节，为 {@code null} 时使用默认值
     * @return 红包配置实例
     */
    public static RedPacketConfiguration load(ConfigurationSection section) {
        if (section == null) {
            return defaults();
        }
        return new RedPacketConfiguration(
            section.getBoolean("enabled", true),
            string(section.getString("send-permission", "")),
            string(section.getString("claim-permission", "")),
            string(section.getString("admin-permission", "")),
            string(section.getString("currency", "money")),
            string(section.getString("card-id", "axs_chat_redpacket")),
            string(section.getString("presets-directory", "chat/redpackets")),
            string(section.getString("admin-channel", "")),
            Math.max(0.0D, section.getDouble("min-total", 1.0D)),
            Math.max(0.0D, section.getDouble("max-total", 1000000.0D)),
            Math.max(0.0D, section.getDouble("min-per-claim", 0.01D)),
            Math.max(1, section.getInt("max-count", 50)),
            Math.max(0L, section.getLong("send-cooldown-seconds", 10L)),
            Math.max(1L, section.getLong("expire-minutes", 5L)),
            section.getBoolean("refund-on-expire", true),
            section.getBoolean("sender-can-claim", true),
            section.getBoolean("announce-claims", true),
            section.getBoolean("announce-finish", true),
            section.getBoolean("text-alongside", true)
        );
    }

    private static RedPacketConfiguration defaults() {
        return new RedPacketConfiguration(
            true, "", "", "", "money", "axs_chat_redpacket", "chat/redpackets", "",
            1.0D, 1000000.0D, 0.01D, 50, 10L, 5L, true, true, true, true, true
        );
    }

    private static String string(String value) {
        return value == null ? "" : value.trim();
    }
}
