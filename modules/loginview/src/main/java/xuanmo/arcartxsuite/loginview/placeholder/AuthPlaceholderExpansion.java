package xuanmo.arcartxsuite.loginview.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.auth.AuthAccess;

public final class AuthPlaceholderExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    public AuthPlaceholderExpansion(JavaPlugin plugin) { this.plugin=plugin; }
    @Override public @NotNull String getIdentifier() { return "axsauth"; }
    @Override public @NotNull String getAuthor() { return String.join(",",plugin.getDescription().getAuthors()); }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public @Nullable String onRequest(OfflinePlayer player,@NotNull String key) {
        if(player==null) return "unknown";
        var state=AuthAccess.snapshot(player.getUniqueId());
        return switch(key) {
            case "stage" -> state.stage().name().toLowerCase(java.util.Locale.ROOT);
            case "admitted" -> Boolean.toString(state.admitted());
            case "verified" -> Boolean.toString(state.identityVerified());
            case "provider" -> state.provider().isEmpty()?"unknown":state.provider();
            case "registered" -> state.registration().name().toLowerCase(java.util.Locale.ROOT);
            default -> null;
        };
    }
}
