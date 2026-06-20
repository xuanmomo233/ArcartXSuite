package xuanmo.arcartxsuite.lottery;

import java.util.UUID;
import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LotteryPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<LotteryService> serviceSupplier;

    public LotteryPlaceholderExpansion(JavaPlugin plugin, Supplier<LotteryService> serviceSupplier) {
        this.plugin = plugin;
        this.serviceSupplier = serviceSupplier;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "axslottery";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ArcartX";
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
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        LotteryService service = serviceSupplier.get();
        if (service == null) return "";

        String[] parts = params.split("_");
        if (parts.length < 2) return "";

        String type = parts[0].toLowerCase();
        String poolId = parts[1];

        UUID uuid = player.getUniqueId();

        switch (type) {
            case "pity5" -> {
                var state = service.getGachaState(player.getPlayer(), poolId);
                return String.valueOf(state.pity5star());
            }
            case "pity4" -> {
                var state = service.getGachaState(player.getPlayer(), poolId);
                return String.valueOf(state.pity4star());
            }
            case "opens" -> {
                var state = service.getCaseState(player.getPlayer(), poolId);
                return String.valueOf(state.openCount());
            }
            case "guaranteed" -> {
                var state = service.getGachaState(player.getPlayer(), poolId);
                return state.guaranteedUp() ? "是" : "否";
            }
            case "fatepoints" -> {
                var state = service.getGachaState(player.getPlayer(), poolId);
                return String.valueOf(state.fatePoints());
            }
        }
        return "";
    }
}
