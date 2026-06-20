package xuanmo.arcartxsuite.tab.placeholder;

import java.net.InetSocketAddress;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import xuanmo.arcartxsuite.tab.config.TabPrivacyConfiguration;
import xuanmo.arcartxsuite.tab.config.TabStyleConfiguration;
import xuanmo.arcartxsuite.tab.sync.TabSyncService;

/**
 * Tab 模块的 PlaceholderAPI 扩展，identifier 为 {@code axstab}。
 * <p>
 * 支持占位符：
 * <ul>
 *   <li>{@code %axstab_pvp%} — 是否处于 PVP 高亮窗口（true/false）</li>
 *   <li>{@code %axstab_pvp_color%} — PVP 高亮颜色，未启用/不在窗口返回空串</li>
 *   <li>{@code %axstab_vanished%} — 是否隐身（true/false）</li>
 *   <li>{@code %axstab_vanish_color%} — 隐身颜色，未启用/未隐身返回空串</li>
 *   <li>{@code %axstab_ping%} — 当前延迟（ms）</li>
 *   <li>{@code %axstab_ping_icon%} — 按 tiers 匹配的延迟图标</li>
 *   <li>{@code %axstab_uuid%} — 玩家 UUID，受 privacy.hide-uuid 脱敏</li>
 *   <li>{@code %axstab_ip%} — 玩家 IP，受 privacy.hide-ip 脱敏</li>
 * </ul>
 */
public final class TabPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final TabSyncService service;
    private final TabStyleConfiguration style;
    private final TabPrivacyConfiguration privacy;

    public TabPlaceholderExpansion(
        JavaPlugin plugin,
        TabSyncService service,
        TabStyleConfiguration style,
        TabPrivacyConfiguration privacy
    ) {
        this.plugin = plugin;
        this.service = service;
        this.style = style;
        this.privacy = privacy;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "axstab";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty()
            ? "ArcartXSuite" : plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }
        return switch (identifier) {
            case "pvp" -> String.valueOf(style.pvpEnabled() && service.isPvpActive(player));
            case "pvp_color" -> (style.pvpEnabled() && service.isPvpActive(player)) ? style.pvpColor() : "";
            case "vanished" -> String.valueOf(service.isVanished(player));
            case "vanish_color" -> (style.vanishEnabled() && service.isVanished(player)) ? style.vanishColor() : "";
            case "ping" -> String.valueOf(service.pingOf(player));
            case "ping_icon" -> style.pingEnabled() ? resolvePingIcon(service.pingOf(player)) : "";
            case "uuid" -> resolveUuid(player);
            case "ip" -> resolveIp(player);
            default -> resolveDefinitionMetric(player, identifier);
        };
    }

    private String resolveDefinitionMetric(Player player, String identifier) {
        int lastUnderscore = identifier.lastIndexOf('_');
        if (lastUnderscore <= 0 || lastUnderscore >= identifier.length() - 1) {
            return "";
        }
        String definitionId = identifier.substring(0, lastUnderscore);
        String metric = identifier.substring(lastUnderscore + 1);
        return switch (metric) {
            case "count" -> String.valueOf(service.localVisibleCount(definitionId));
            case "total" -> String.valueOf(service.totalVisibleCount(definitionId));
            case "rank" -> String.valueOf(service.rankOf(player.getUniqueId(), definitionId));
            case "view" -> service.currentView(player);
            case "page" -> String.valueOf(service.currentPage(player, definitionId));
            default -> "";
        };
    }

    private String resolvePingIcon(int ping) {
        List<TabStyleConfiguration.TabPingTier> tiers = style.pingTiers();
        for (TabStyleConfiguration.TabPingTier tier : tiers) {
            if (ping <= tier.maxMs()) {
                return tier.icon();
            }
        }
        return tiers.isEmpty() ? "" : tiers.get(tiers.size() - 1).icon();
    }

    private String resolveUuid(Player player) {
        String uuid = player.getUniqueId().toString();
        if (privacy.hideUuid()) {
            return uuid.substring(0, Math.min(8, uuid.length())) + "...";
        }
        return uuid;
    }

    private String resolveIp(Player player) {
        InetSocketAddress address = player.getAddress();
        String ip = address == null ? "" : address.getAddress().getHostAddress();
        if (privacy.hideIp() && !ip.isEmpty()) {
            int lastDot = ip.lastIndexOf('.');
            return lastDot > 0 ? ip.substring(0, lastDot) + ".***" : "***";
        }
        return ip;
    }
}
