package xuanmo.arcartxsuite.mail.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.mail.model.MailMailboxStats;
import xuanmo.arcartxsuite.mail.service.MailService;

/**
 * 邮件模块 PlaceholderAPI 扩展，提供玩家邮箱统计占位符。
 * <p>
 * 关联模块：mail。注册标识符为 {@code axsmail}，
 * 支持 {@code %axsmail_unread_count%}、{@code %axsmail_claimable_count%}、{@code %axsmail_total_count%}。
 */
public final class MailPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<MailService> serviceProvider;

    /**
     * 构造占位符扩展。
     *
     * @param plugin          宿主插件实例
     * @param serviceProvider 邮件服务提供者（延迟获取）
     */
    public MailPlaceholderExpansion(JavaPlugin plugin, Supplier<MailService> serviceProvider) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "axsmail";
    }

    @Override
    public @NotNull String getAuthor() {
        return "墨墨墨";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    /**
     * 处理占位符请求，返回玩家邮箱统计数据。
     *
     * @param offlinePlayer 离线玩家
     * @param params        占位符参数
     * @return 占位符值，无法识别时返回 {@code null}
     */
    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        MailService mailService = serviceProvider.get();
        if (offlinePlayer == null || offlinePlayer.getUniqueId() == null || mailService == null) {
            return "";
        }
        MailMailboxStats stats = mailService.loadStats(offlinePlayer.getUniqueId());
        return switch (params.trim().toLowerCase()) {
            case "unread_count" -> Integer.toString(stats.unreadCount());
            case "claimable_count" -> Integer.toString(stats.claimableCount());
            case "total_count" -> Integer.toString(stats.totalCount());
            default -> null;
        };
    }
}
