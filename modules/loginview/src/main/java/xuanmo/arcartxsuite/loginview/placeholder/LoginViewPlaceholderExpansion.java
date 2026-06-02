package xuanmo.arcartxsuite.loginview.placeholder;

import java.util.Locale;
import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.account.AccountType;
import xuanmo.arcartxsuite.loginview.service.LoginViewService;

public final class LoginViewPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<LoginViewService> serviceProvider;

    public LoginViewPlaceholderExpansion(JavaPlugin plugin, Supplier<LoginViewService> serviceProvider) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "AXSloginview";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ArcartXSuite";
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
        LoginViewService service = serviceProvider.get();
        if (service == null) {
            return "";
        }

        AccountType accountType = service.accountType(offlinePlayer);
        String normalized = params.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "account_type" -> accountType.id();
            case "account_type_display", "account_type_name" -> accountType.displayName();
            case "is_microsoft" -> Boolean.toString(accountType == AccountType.MICROSOFT);
            case "is_littleskin" -> Boolean.toString(accountType == AccountType.LITTLESKIN);
            case "is_offline" -> Boolean.toString(accountType == AccountType.OFFLINE);
            case "is_premium" -> Boolean.toString(accountType.premium());
            default -> null;
        };
    }
}
