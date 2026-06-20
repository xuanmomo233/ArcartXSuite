package xuanmo.arcartxsuite.rgb.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.rgb.service.ArcartRgbService;

public final class ArcartRgbPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final ArcartRgbService service;

    public ArcartRgbPlaceholderExpansion(JavaPlugin plugin, ArcartRgbService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "axsrgb";
    }

    @Override
    public @NotNull String getAuthor() {
        return "墨墨墨";
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
        if (params.isBlank()) {
            return "";
        }
        return service.render(params, offlinePlayer);
    }
}
