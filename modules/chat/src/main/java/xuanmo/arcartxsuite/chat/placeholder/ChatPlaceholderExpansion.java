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

public final class ChatPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<ChatService> serviceProvider;

    public ChatPlaceholderExpansion(JavaPlugin plugin, Supplier<ChatService> serviceProvider) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "AXSchat";
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
