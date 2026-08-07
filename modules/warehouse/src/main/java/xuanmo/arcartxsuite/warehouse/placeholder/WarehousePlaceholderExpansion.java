package xuanmo.arcartxsuite.warehouse.placeholder;

import java.math.BigDecimal;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.warehouse.service.WarehouseService;

/**
 * 仓库模块的 PlaceholderAPI 变量扩展。
 * <p>
 * 继承 {@link PlaceholderExpansion}，向 PlaceholderAPI 注册 {@code %axswarehouse_*%} 系列占位符，
 * 提供物品总数、个人仓库已用/容量、共享仓库数量、分类数量、银行余额、定期存款等查询能力。
 * 关联模块：warehouse。
 */
public final class WarehousePlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<WarehouseService> serviceProvider;

    /**
     * 构造占位符扩展。
     *
     * @param plugin           宿主插件实例，用于提供版本号
     * @param serviceProvider 仓库服务提供者，延迟获取 {@link WarehouseService} 实例
     */
    public WarehousePlaceholderExpansion(JavaPlugin plugin, Supplier<WarehouseService> serviceProvider) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
    }

    /** @return 占位符标识符，固定为 {@code "axswarehouse"} */
    @Override
    public @NotNull String getIdentifier() {
        return "axswarehouse";
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

    /**
     * 是否在 PlaceholderAPI 重载后保持注册。
     *
     * @return 固定返回 {@code true}，避免被 PAPI 自动注销
     */
    @Override
    public boolean persist() {
        return true;
    }

    /**
     * 处理占位符请求，根据 {@code params} 解析对应的仓库数据并返回字符串结果。
     * <p>
     * 支持的占位符包括：{@code total_items}、{@code personal_used}、{@code personal_capacity}、
     * {@code shared_owned_count}、{@code shared_joined_count}、{@code category_<id>_amount}、
     * {@code bank_balance_<currency>}、{@code bank_fixed_active_<currency>}、{@code bank_fixed_matured_<currency>}。
     *
     * @param offlinePlayer 离线玩家对象
     * @param params        占位符参数
     * @return 解析结果字符串；玩家或服务无效时返回空串或 {@code "0"}
     */
    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) {
            return "";
        }
        WarehouseService service = serviceProvider.get();
        if (service == null) {
            return "0";
        }
        String normalized = params.toLowerCase(Locale.ROOT);
        if ("total_items".equals(normalized)) {
            return Long.toString(service.totalItems(offlinePlayer.getUniqueId()));
        }
        if ("personal_used".equals(normalized)) {
            return Long.toString(service.personalUsed(offlinePlayer.getUniqueId()));
        }
        if ("personal_capacity".equals(normalized)) {
            return Long.toString(service.personalCapacity(offlinePlayer.getUniqueId()));
        }
        if ("shared_owned_count".equals(normalized)) {
            return Integer.toString(service.sharedOwnedCount(offlinePlayer.getUniqueId()));
        }
        if ("shared_joined_count".equals(normalized)) {
            return Integer.toString(service.sharedJoinedCount(offlinePlayer.getUniqueId()));
        }
        if (normalized.startsWith("category_") && normalized.endsWith("_amount")) {
            String categoryId = normalized.substring("category_".length(), normalized.length() - "_amount".length());
            return Long.toString(service.categoryAmount(offlinePlayer.getUniqueId(), categoryId));
        }
        if (normalized.startsWith("bank_balance_")) {
            String currencyId = normalized.substring("bank_balance_".length());
            BigDecimal balance = service.bankBalance(offlinePlayer.getUniqueId(), currencyId);
            return balance.stripTrailingZeros().toPlainString();
        }
        if (normalized.startsWith("bank_fixed_active_")) {
            String currencyId = normalized.substring("bank_fixed_active_".length());
            return Long.toString(service.fixedActive(offlinePlayer.getUniqueId(), currencyId));
        }
        if (normalized.startsWith("bank_fixed_matured_")) {
            String currencyId = normalized.substring("bank_fixed_matured_".length());
            return Long.toString(service.fixedMatured(offlinePlayer.getUniqueId(), currencyId));
        }
        return "";
    }
}
