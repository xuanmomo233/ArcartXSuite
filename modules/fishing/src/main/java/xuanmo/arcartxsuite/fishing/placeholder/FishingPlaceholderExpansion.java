package xuanmo.arcartxsuite.fishing.placeholder;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.fishing.service.FishingService;
import xuanmo.arcartxsuite.fishing.task.FishingTask.TaskCategory;
import xuanmo.arcartxsuite.fishing.task.PlayerTaskInstance;

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

        String name = identifier.toLowerCase(Locale.ROOT);
        return switch (name) {
            case "daily_tasks" -> taskProgress(service, uuid, TaskCategory.DAILY);
            case "weekly_tasks" -> taskProgress(service, uuid, TaskCategory.WEEKLY);
            case "season_tasks" -> taskProgress(service, uuid, TaskCategory.SEASON);
            default -> service.displayPlaceholder(uuid, name);
        };
    }

    private static String taskProgress(FishingService service, UUID uuid, TaskCategory category) {
        var tasks = service.getTaskEngine().getTasks(uuid, category);
        long completed = tasks.stream().filter(PlayerTaskInstance::completed).count();
        return completed + "/" + tasks.size();
    }
}
