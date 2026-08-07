package xuanmo.arcartxsuite.chat.placeholder;

import java.time.Instant;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.chat.model.ChatMuteRecord;
import xuanmo.arcartxsuite.chat.model.ChatPlayerState;
import xuanmo.arcartxsuite.chat.service.ChatService;

/**
 * 聊天模块 PlaceholderAPI 扩展，提供 {@code %axschat_*%} 系列占位符。
 * <p>
 * 支持的占位符：{@code current_channel}、{@code current_channel_display}、
 * {@code reply_target}、{@code spy_enabled}、{@code ignore_count}、{@code muted}。
 * 通过 {@link Supplier} 延迟获取 {@link ChatService}，避免模块加载顺序问题。
 */
public final class ChatPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<ChatService> serviceProvider;

    /**
     * @param plugin          宿主插件实例
     * @param serviceProvider 聊天服务延迟供应者
     */
    public ChatPlaceholderExpansion(JavaPlugin plugin, Supplier<ChatService> serviceProvider) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "axschat";
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
     * 解析占位符请求。
     *
     * @param offlinePlayer 离线玩家上下文
     * @param params        占位符参数（如 "current_channel"）
     * @return 占位符值，无法解析时返回 {@code null}
     */
    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) {
            return "";
        }
        ChatService service = serviceProvider.get();
        if (service == null) {
            return "";
        }
        ChatPlayerState state = service.getCachedState(offlinePlayer.getUniqueId());
        if (state == null) {
            return "";
        }
        String normalized = params.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "current_channel" -> state.currentChannelId();
            case "current_channel_display" -> service.channelDisplayName(state.currentChannelId());
            case "reply_target" -> service.replyTargetName(offlinePlayer.getUniqueId());
            case "spy_enabled" -> Boolean.toString(state.socialSpyEnabled());
            case "ignore_count" -> Integer.toString(state.ignoredPlayers().size());
            case "muted" -> {
                ChatMuteRecord muteRecord = service.getCachedMute(offlinePlayer.getUniqueId());
                yield Boolean.toString(muteRecord != null && muteRecord.active(Instant.now()));
            }
            default -> null;
        };
    }
}
