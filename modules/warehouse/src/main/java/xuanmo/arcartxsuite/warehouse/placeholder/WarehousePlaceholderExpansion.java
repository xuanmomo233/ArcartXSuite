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

public final class WarehousePlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<WarehouseService> serviceProvider;

    public WarehousePlaceholderExpansion(JavaPlugin plugin, Supplier<WarehouseService> serviceProvider) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "axswarehouse";
    }

    @Override
    public @NotNull String getAuthor() {
        return "arcartxsuite";
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
