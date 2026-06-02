package xuanmo.arcartxsuite.mail.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.mail.model.MailMailboxStats;
import xuanmo.arcartxsuite.mail.service.MailService;

public final class MailPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<MailService> serviceProvider;

    public MailPlaceholderExpansion(JavaPlugin plugin, Supplier<MailService> serviceProvider) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "AXSmail";
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
