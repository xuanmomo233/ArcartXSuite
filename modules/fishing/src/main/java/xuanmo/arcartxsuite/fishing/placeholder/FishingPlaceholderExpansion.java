package xuanmo.arcartxsuite.fishing.placeholder;

import java.util.UUID;
import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.fishing.model.FishingPermanentData;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;
import xuanmo.arcartxsuite.fishing.service.FishingService;

/**
 * 钓鱼模块的 PlaceholderAPI 扩展。
 * <p>
 * 关联模块：{@code fishing}。提供 {@code %axsfishing_level%}、
 * {@code %axsfishing_xp%}、{@code %axsfishing_collection%} 等占位符，
 * 供其他插件或显示系统读取玩家钓鱼数据。
 */
public final class FishingPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<FishingService> serviceSupplier;

    public FishingPlaceholderExpansion(@NotNull JavaPlugin plugin, @NotNull Supplier<FishingService> serviceSupplier) {
        this.plugin = plugin;
        this.serviceSupplier = serviceSupplier;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "axsfishing";
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
        FishingSeasonData season = service.getSeasonData(uuid);
        FishingPermanentData perm = service.getPermanentData(uuid);

        return switch (identifier.toLowerCase()) {
            case "level" -> String.valueOf(season.level());
            case "current_xp" -> String.valueOf(season.currentXp());
            case "xp" -> String.valueOf(season.currentXp());
            case "xp_for_next" -> String.valueOf(season.xpForNextLevel(service.getXpCurve()));
            case "talent_points" -> String.valueOf(season.unspentTalentPoints());
            case "total_talent_points" -> String.valueOf(season.talentPoints());
            case "season_id" -> season.seasonId();
            case "total_caught" -> String.valueOf(perm.totalCaught());
            case "perfect_catches" -> String.valueOf(perm.perfectCatches());
            case "treasure_caught" -> String.valueOf(perm.treasureCaught());
            case "collection_count" -> String.valueOf(service.getCollectionCount(uuid));
            case "collection" -> service.getCollectionCount(uuid) + "/" + service.getTotalFishTypes();
            case "collection_percent" -> {
                int total = service.getTotalFishTypes();
                if (total <= 0) yield "0";
                int count = service.getCollectionCount(uuid);
                yield String.valueOf((count * 100) / total);
            }
            case "favorite_fish" -> service.getFavoriteFishDisplayName(uuid);
            case "daily_tasks" -> {
                var tasks = service.getTaskEngine().getTasks(uuid,
                    xuanmo.arcartxsuite.fishing.task.FishingTask.TaskCategory.DAILY);
                long completed = tasks.stream().filter(xuanmo.arcartxsuite.fishing.task.PlayerTaskInstance::completed).count();
                yield completed + "/" + tasks.size();
            }
            case "weekly_tasks" -> {
                var tasks = service.getTaskEngine().getTasks(uuid,
                    xuanmo.arcartxsuite.fishing.task.FishingTask.TaskCategory.WEEKLY);
                long completed = tasks.stream().filter(xuanmo.arcartxsuite.fishing.task.PlayerTaskInstance::completed).count();
                yield completed + "/" + tasks.size();
            }
            case "season_tasks" -> {
                var tasks = service.getTaskEngine().getTasks(uuid,
                    xuanmo.arcartxsuite.fishing.task.FishingTask.TaskCategory.SEASON);
                long completed = tasks.stream().filter(xuanmo.arcartxsuite.fishing.task.PlayerTaskInstance::completed).count();
                yield completed + "/" + tasks.size();
            }
            case "fatigue" -> String.valueOf(service.getFatigue(uuid));
            case "fatigue_efficiency" -> {
                double eff = service.getFatigueEfficiency(uuid);
                yield String.format("%.0f%%", eff * 100);
            }
            default -> null;
        };
    }
}
