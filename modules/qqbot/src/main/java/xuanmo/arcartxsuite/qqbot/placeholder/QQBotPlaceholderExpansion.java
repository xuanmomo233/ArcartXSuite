package xuanmo.arcartxsuite.qqbot.placeholder;

import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.qqbot.service.QQBotService;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.QQBotBinding;

public final class QQBotPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<QQBotService> serviceProvider;

    public QQBotPlaceholderExpansion(JavaPlugin plugin, Supplier<QQBotService> serviceProvider) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "AXSqqbot";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty()
            ? "ArcartXSuite" : plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    @Nullable
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        QQBotService service = serviceProvider.get();
        if (service == null) return "";

        switch (params.toLowerCase()) {
            case "connected" -> {
                return service.isConnected() ? "true" : "false";
            }
            case "connected_display" -> {
                return service.isConnected() ? "已连接" : "未连接";
            }
            case "bound_qq" -> {
                if (player == null) return "";
                QQBotBinding binding = service.bindService().findByPlayer(player.getUniqueId());
                return binding != null ? String.valueOf(binding.qqId()) : "";
            }
            case "is_bound" -> {
                if (player == null) return "false";
                QQBotBinding binding = service.bindService().findByPlayer(player.getUniqueId());
                return binding != null ? "true" : "false";
            }
            case "bound_name" -> {
                if (player == null) return "";
                QQBotBinding binding = service.bindService().findByPlayer(player.getUniqueId());
                return binding != null ? binding.playerName() : "";
            }
            case "group_count" -> {
                return String.valueOf(service.configuration().groups().size());
            }
            default -> {
                return null;
            }
        }
    }
}
