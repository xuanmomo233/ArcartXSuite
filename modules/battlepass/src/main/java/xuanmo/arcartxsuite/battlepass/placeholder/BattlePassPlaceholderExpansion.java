package xuanmo.arcartxsuite.battlepass.placeholder;

import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.service.BattlePassService;

/**
 * 战令 PlaceholderAPI 扩展，继承 {@link PlaceholderExpansion}。
 * <p>
 * 向 PlaceholderAPI 注册 {@code %axsbattlepass_xxx%} 系列占位符，
 * 提供赛季信息、玩家等级、经验、档位、活跃任务数等数据的占位符解析。
 */
public final class BattlePassPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<BattlePassService> serviceProvider;

    /**
     * 构造战令 PlaceholderAPI 扩展。
     *
     * @param plugin           插件实例
     * @param serviceProvider  BattlePassService 提供者（延迟获取，可能为 null）
     */
    public BattlePassPlaceholderExpansion(JavaPlugin plugin, Supplier<BattlePassService> serviceProvider) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
    }

    @Override public @NotNull String getIdentifier() { return "axsbattlepass"; }
    @Override public @NotNull String getAuthor() { return "ArcartXSuite"; }
    @Override public @NotNull String getVersion() { return "1.0.0"; }
    @Override public boolean persist() { return true; }

    /**
     * 解析占位符请求，根据标识符返回对应的战令数据。
     *
     * @param player    请求占位符的玩家
     * @param identifier 占位符标识（如 level、xp、season 等）
     * @return 占位符值字符串，未知标识符返回空串
     */
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
