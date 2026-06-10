package xuanmo.arcartxsuite.fishing.placeholder;

import java.util.UUID;
import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.fishing.model.FishingPlayerData;
import xuanmo.arcartxsuite.fishing.service.FishingService;

public final class FishingPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<FishingService> serviceSupplier;

    public FishingPlaceholderExpansion(@NotNull JavaPlugin plugin, @NotNull Supplier<FishingService> serviceSupplier) {
        this.plugin = plugin;
        this.serviceSupplier = serviceSupplier;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "axs_fishing";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty()
            ? "ArcartXSuite" : plugin.getDescription().getAuthors().get(0);
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
    public @Nullable String onPlaceholderRequest(@Nullable Player player, @NotNull String identifier) {
        if (player == null) return "";

        FishingService service = serviceSupplier.get();
        if (service == null) return "";

        UUID uuid = player.getUniqueId();

        return switch (identifier.toLowerCase()) {
            case "level" -> String.valueOf(service.getPlayerData(uuid).level());
            case "total_xp" -> String.valueOf(service.getPlayerData(uuid).totalXp());
            case "total_caught" -> String.valueOf(service.getPlayerData(uuid).totalCaught());
            case "perfect_catches" -> String.valueOf(service.getPlayerData(uuid).perfectCatches());
            case "collection_count" -> String.valueOf(service.getCollectionCount(uuid));
            case "collection_percent" -> {
                int total = service.getTotalFishTypes();
                if (total <= 0) yield "0";
                int count = service.getCollectionCount(uuid);
                yield String.valueOf((count * 100) / total);
            }
            default -> null;
        };
    }
}
