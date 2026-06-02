package xuanmo.arcartxsuite.tab.placeholder;

import java.util.Locale;
import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.tab.config.TabModuleConfiguration;
import xuanmo.arcartxsuite.tab.sync.TabSyncService;

/**
 * Tab 模块的 PlaceholderAPI 扩展。
 *
 * <p>支持以下占位符：
 * <ul>
 *   <li>{@code %AXStab_<defId>_count%} — 本服当前 definition 可见玩家数（已应用 filters/pinned/maxEntries）</li>
 *   <li>{@code %AXStab_<defId>_total%} — 本服 + 所有跨服节点合计</li>
 *   <li>{@code %AXStab_<defId>_rank%} — 当前玩家在本服排序中的位次（1 起；不可见返回 0）</li>
 *   <li>{@code %AXStab_<defId>_view%} — 当前玩家所在 view（默认 "default"）</li>
 *   <li>{@code %AXStab_<defId>_page%} — 当前玩家在该 definition 的页码（0 起）</li>
 * </ul>
 *
 * <p>identifier 默认 {@code AXStab}，可通过 {@code settings.papi.expansion-id} 自定义。
 */
public final class TabPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<TabSyncService> serviceProvider;
    private final Supplier<TabModuleConfiguration> configurationProvider;

    public TabPlaceholderExpansion(
        JavaPlugin plugin,
        Supplier<TabSyncService> serviceProvider,
        Supplier<TabModuleConfiguration> configurationProvider
    ) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
        this.configurationProvider = configurationProvider;
    }

    @Override
    public @NotNull String getIdentifier() {
        TabModuleConfiguration cfg = configurationProvider.get();
        return cfg == null ? "AXStab" : cfg.papiExpansionId();
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
        TabSyncService service = serviceProvider.get();
        if (service == null || params.isBlank()) {
            return "";
        }

        String normalized = params.trim().toLowerCase(Locale.ROOT);

        // 全局 / 玩家维度占位符（无 defId 前缀）：ping / ping_icon / pvp / pvp_color / vanished / vanish_color / uuid / ip
        String globalMetric = resolveGlobalMetric(normalized, offlinePlayer);
        if (globalMetric != null) {
            return globalMetric;
        }

        int lastUnderscore = normalized.lastIndexOf('_');
        if (lastUnderscore < 1 || lastUnderscore == normalized.length() - 1) {
            return "";
        }
        String defId = normalized.substring(0, lastUnderscore);
        String metric = normalized.substring(lastUnderscore + 1);

        switch (metric) {
            case "count":
                return Integer.toString(service.localVisibleCount(defId));
            case "total":
                return Integer.toString(service.totalVisibleCount(defId));
            case "rank": {
                Player player = resolvePlayer(offlinePlayer);
                if (player == null) {
                    return "0";
                }
                return Integer.toString(service.rankOf(player.getUniqueId(), defId));
            }
            case "view": {
                Player player = resolvePlayer(offlinePlayer);
                return player == null ? "default" : service.currentView(player);
            }
            case "page": {
                Player player = resolvePlayer(offlinePlayer);
                return player == null ? "0" : Integer.toString(service.currentPage(player, defId));
            }
            default:
                return "";
        }
    }

    /**
     * 解析 style/privacy 等无 defId 前缀的占位符，返回 null 表示走 defId_metric 路径。
     */
    private @Nullable String resolveGlobalMetric(String normalized, OfflinePlayer offlinePlayer) {
        TabSyncService service = serviceProvider.get();
        TabModuleConfiguration cfg = configurationProvider.get();
        if (service == null || cfg == null) {
            return null;
        }
        switch (normalized) {
            case "ping": {
                Player player = resolvePlayer(offlinePlayer);
                return player == null ? "0" : Integer.toString(service.pingOf(player));
            }
            case "ping_icon": {
                Player player = resolvePlayer(offlinePlayer);
                return player == null ? "" : service.pingIcon(service.pingOf(player));
            }
            case "pvp": {
                Player player = resolvePlayer(offlinePlayer);
                return player != null && service.isPvpActive(player) ? "1" : "0";
            }
            case "pvp_color": {
                Player player = resolvePlayer(offlinePlayer);
                return player != null && service.isPvpActive(player) ? cfg.style().pvpColor() : "";
            }
            case "vanished": {
                Player player = resolvePlayer(offlinePlayer);
                return player != null && service.isVanishedPublic(player) ? "1" : "0";
            }
            case "vanish_color": {
                Player player = resolvePlayer(offlinePlayer);
                if (player == null || !cfg.style().vanishGreyEnabled()) {
                    return "";
                }
                return service.isVanishedPublic(player) ? cfg.style().vanishColor() : "";
            }
            case "uuid": {
                if (cfg.privacy().hideUuid()) {
                    return "";
                }
                return offlinePlayer == null || offlinePlayer.getUniqueId() == null
                    ? ""
                    : offlinePlayer.getUniqueId().toString();
            }
            case "ip": {
                if (cfg.privacy().hideIp()) {
                    return "";
                }
                Player player = resolvePlayer(offlinePlayer);
                if (player == null || player.getAddress() == null || player.getAddress().getAddress() == null) {
                    return "";
                }
                return player.getAddress().getAddress().getHostAddress();
            }
            default:
                return null;
        }
    }

    private static @Nullable Player resolvePlayer(OfflinePlayer offlinePlayer) {
        if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) {
            return null;
        }
        return Bukkit.getPlayer(offlinePlayer.getUniqueId());
    }
}
