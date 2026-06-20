package xuanmo.arcartxsuite.essentials.placeholder;

import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.essentials.service.PlayerManagementService;

/**
 * Essentials 模块的 PlaceholderAPI 扩展。
 *
 * <p>支持以下占位符：
 * <ul>
 *   <li>{@code %axsessentials_afk%} — 是否 AFK（true/false）</li>
 *   <li>{@code %axsessentials_afk_symbol%} — AFK 标记符号（可用于 Tab）</li>
 *   <li>{@code %axsessentials_vanish%} — 是否隐身（true/false）</li>
 *   <li>{@code %axsessentials_vanish_symbol%} — 隐身标记符号</li>
 *   <li>{@code %axsessentials_flying%} — 是否飞行中</li>
 *   <li>{@code %axsessentials_god%} — 是否无敌</li>
 *   <li>{@code %axsessentials_nick%} — 昵称（未设置则返回玩家名）</li>
 * </ul>
 */
public final class EssentialsPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<PlayerManagementService> serviceProvider;

    public EssentialsPlaceholderExpansion(JavaPlugin plugin, Supplier<PlayerManagementService> serviceProvider) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "axsessentials";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ArcartXSuite";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";
        PlayerManagementService service = serviceProvider.get();
        if (service == null) return "";

        return switch (params.toLowerCase()) {
            case "afk" -> String.valueOf(service.isAfk(player.getUniqueId()));
            case "afk_symbol" -> service.isAfk(player.getUniqueId()) ? "&7[AFK]" : "";
            case "vanish" -> String.valueOf(service.isVanished(player.getUniqueId()));
            case "vanish_symbol" -> service.isVanished(player.getUniqueId()) ? "&b[V]" : "";
            case "flying" -> String.valueOf(player.isFlying());
            case "god" -> String.valueOf(player.isInvulnerable());
            case "nick" -> player.getDisplayName();
            default -> null;
        };
    }
}
