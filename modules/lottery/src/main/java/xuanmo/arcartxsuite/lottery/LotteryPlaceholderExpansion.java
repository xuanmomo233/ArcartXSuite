package xuanmo.arcartxsuite.lottery;

import java.util.UUID;
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

        String[] parts = params.split("_");
        UUID uuid = player.getUniqueId();
        if (parts.length == 2 && (parts[0].equalsIgnoreCase("point")
                || parts[0].equalsIgnoreCase("points"))) {
            try {
                return String.valueOf(service.getPoint(uuid, parts[1]));
            } catch (IllegalArgumentException ignored) {
                return "0";
            }
        }
        if (parts.length < 2) return "";

        String type = parts[0].toLowerCase();
        String poolId = parts[1];

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
                return state.guaranteedUp()
                    ? service.message("placeholder.guaranteed-yes")
                    : service.message("placeholder.guaranteed-no");
            }
            case "fatepoints" -> {
                var state = service.getGachaState(player.getPlayer(), poolId);
                return String.valueOf(state.fatePoints());
            }
        }
        return "";
    }
}
