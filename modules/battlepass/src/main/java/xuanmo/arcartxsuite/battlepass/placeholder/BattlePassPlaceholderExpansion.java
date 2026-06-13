package xuanmo.arcartxsuite.battlepass.placeholder;

import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.service.BattlePassService;

public final class BattlePassPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<BattlePassService> serviceProvider;

    public BattlePassPlaceholderExpansion(JavaPlugin plugin, Supplier<BattlePassService> serviceProvider) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
    }

    @Override public @NotNull String getIdentifier() { return "axsbattlepass"; }
    @Override public @NotNull String getAuthor() { return "ArcartXSuite"; }
    @Override public @NotNull String getVersion() { return "1.0.0"; }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "";
        BattlePassService service = serviceProvider.get();
        if (service == null) return "";
        BattlePassPlayerProgress progress = service.getProgress(player);
        var config = service.configuration().season();

        return switch (identifier) {
            case "season" -> config.seasonId();
            case "season_display" -> config.displayName();
            case "level" -> String.valueOf(progress.currentLevel());
            case "max_level" -> String.valueOf(config.maxLevel());
            case "xp" -> String.valueOf(progress.currentXp());
            case "xp_per_level" -> String.valueOf(config.xpPerLevel());
            case "xp_needed" -> String.valueOf(Math.max(0, config.xpPerLevel() - progress.currentXp()));
            case "premium" -> progress.unlockedPremium() ? "已激活" : "未激活";
            case "deluxe" -> progress.unlockedDeluxe() ? "已激活" : "未激活";
            case "tier" -> switch (progress.passTier()) {
                case FREE -> "免费";
                case PREMIUM -> "高级";
                case DELUXE -> "典藏";
            };
            case "active_tasks" -> {
                var instances = service.getActiveTaskInstances(player);
                yield String.valueOf(instances.size());
            }
            default -> "";
        };
    }
}
