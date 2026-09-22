package xuanmo.arcartxsuite.extrabackpack.placeholder;

import java.util.Locale;
import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.extrabackpack.service.ExtraBackpackService;

/**
 * 额外背包模块的 PlaceholderAPI 变量扩展。
 * <p>
 * 注册 {@code %axsextrabackpack_*}% 系列占位符，提供总已用/总容量与按分类的已用/容量查询：
 * {@code total_used}、{@code total_capacity}、{@code used_<分类id>}、{@code capacity_<分类id>}。
 * 关联模块：extrabackpack。
 */
public final class ExtraBackpackPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<ExtraBackpackService> serviceProvider;

    /**
     * 构造占位符扩展。
     *
     * @param plugin           宿主插件实例，用于提供版本号
     * @param serviceProvider 服务提供者，延迟获取 {@link ExtraBackpackService} 实例
     */
    public ExtraBackpackPlaceholderExpansion(JavaPlugin plugin, Supplier<ExtraBackpackService> serviceProvider) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
    }

    /** @return 占位符标识符，固定为 {@code "axsextrabackpack"} */
    @Override
    public @NotNull String getIdentifier() {
        return "axsextrabackpack";
    }

    /** @return 扩展作者，固定为 {@code "arcartxsuite"} */
    @Override
    public @NotNull String getAuthor() {
        return "arcartxsuite";
    }

    /** @return 扩展版本号，取自宿主插件描述文件 */
    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** @return 固定返回 {@code true}，避免被 PAPI 自动注销 */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) {
            return "";
        }
        ExtraBackpackService service = serviceProvider.get();
        if (service == null) {
            return "0";
        }
        String normalized = params.toLowerCase(Locale.ROOT);
        if ("total_used".equals(normalized)) {
            return Long.toString(service.totalUsed(offlinePlayer.getUniqueId()));
        }
        if ("total_capacity".equals(normalized)) {
            return Long.toString(service.totalCapacity(offlinePlayer.getUniqueId()));
        }
        if (normalized.startsWith("used_")) {
            return Long.toString(service.categoryUsed(offlinePlayer.getUniqueId(), normalized.substring("used_".length())));
        }
        if (normalized.startsWith("capacity_")) {
            return Long.toString(service.categoryCapacity(offlinePlayer.getUniqueId(), normalized.substring("capacity_".length())));
        }
        return "";
    }
}
