package xuanmo.arcartxsuite.lottery;

import java.util.UUID;
import java.util.Set;
import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lottery 模块的 PlaceholderAPI 扩展。
 * <p>
 * 提供保底计数、开箱次数、大保底状态、命运点等占位符查询，
 * 占位符格式为 {@code %axslottery_<type>_<poolId>%}。
 */
public class LotteryPlaceholderExpansion extends PlaceholderExpansion {

    private static final Set<String> POOL_TYPES = Set.of("opens", "pity5", "pity4", "guaranteed", "fatepoints");

    private final JavaPlugin plugin;
    private final Supplier<LotteryService> serviceSupplier;

    /**
     * 构造占位符扩展。
     *
     * @param plugin          宿主插件实例
     * @param serviceSupplier Lottery 服务延迟供应器
     */
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

    /**
     * 处理占位符请求。
     *
     * @param player 离线玩家对象
     * @param params 占位符参数，格式为 {@code <type>_<poolId>}
     * @return 占位符值，无法解析时返回空字符串
     */
    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        LotteryService service = serviceSupplier.get();
        if (service == null) return "";

        String[] parts = params.split("_", 2);
        if (parts.length < 2 || parts[1].isBlank()) return "";
        UUID id = player.getUniqueId();
        String type = parts[0].toLowerCase(java.util.Locale.ROOT);
        String key = parts[1];
        if (type.equals("point") || type.equals("points")) {
            Long value = service.placeholderPoint(id, key);
            return value == null ? "" : value.toString();
        }
        if (!POOL_TYPES.contains(type)) return "";
        if (!player.isOnline()) return type.equals("guaranteed") ? service.message("placeholder.guaranteed-no") : "0";
        if (type.equals("opens")) {
            var state = service.placeholderOpened(id, key);
            return state == null ? "" : String.valueOf(state.openCount());
        }
        var state = service.placeholderPity(id, key);
        if (state == null) return "";
        return switch (type) {
            case "pity5" -> String.valueOf(state.pity5star());
            case "pity4" -> String.valueOf(state.pity4star());
            case "guaranteed" -> {
                var pool = service.getPool(key);
                boolean guaranteedHere = state.guaranteedUp() && pool != null && pool.gacha() != null
                    && pool.gacha().hasUp5starItems();
                yield service.message(guaranteedHere ? "placeholder.guaranteed-yes" : "placeholder.guaranteed-no");
            }
            case "fatepoints" -> String.valueOf(state.fatePoints());
            default -> "";
        };
    }
}
