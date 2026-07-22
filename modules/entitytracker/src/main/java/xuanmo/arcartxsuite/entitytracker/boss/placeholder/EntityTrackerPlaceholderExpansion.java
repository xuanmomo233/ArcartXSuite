package xuanmo.arcartxsuite.entitytracker.boss.placeholder;

import java.util.Locale;
import java.util.Optional;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamagePlayerSettlementView;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementPlaceholderContext;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossTrackerService;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossViewSlot;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.PlayerBossViewSnapshot;
import xuanmo.arcartxsuite.entitytracker.target.service.EntityTargetHudService;
import xuanmo.arcartxsuite.entitytracker.target.service.EntityTargetSnapshot;

public final class EntityTrackerPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final java.util.function.Supplier<xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration> configurationProvider;
    private final java.util.function.Supplier<BossTrackerService> serviceProvider;
    private final java.util.function.Supplier<String> runtimeUiIdProvider;
    private final java.util.function.Supplier<xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI> packetBridgeProvider;
    private final java.util.function.Supplier<EntityTargetHudService> targetServiceProvider;

    public EntityTrackerPlaceholderExpansion(
        JavaPlugin plugin,
        java.util.function.Supplier<xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration> configurationProvider,
        java.util.function.Supplier<BossTrackerService> serviceProvider,
        java.util.function.Supplier<String> runtimeUiIdProvider,
        java.util.function.Supplier<xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI> packetBridgeProvider,
        java.util.function.Supplier<EntityTargetHudService> targetServiceProvider
    ) {
        this.plugin = plugin;
        this.configurationProvider = configurationProvider == null ? () -> null : configurationProvider;
        this.serviceProvider = serviceProvider == null ? () -> null : serviceProvider;
        this.runtimeUiIdProvider = runtimeUiIdProvider == null ? () -> "" : runtimeUiIdProvider;
        this.packetBridgeProvider = packetBridgeProvider == null ? () -> null : packetBridgeProvider;
        this.targetServiceProvider = targetServiceProvider == null ? () -> null : targetServiceProvider;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "axsentitytracker";
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
        String normalized = params.trim().toLowerCase(Locale.ROOT);
        PluginConfiguration configuration = configurationProvider.get();
        BossTrackerService trackerService = serviceProvider.get();
        switch (normalized) {
            case "sort_mode":
                return configuration == null ? "" : configuration.sortMode().configKey();
            case "max_visible_bars":
                return configuration == null ? "0" : Integer.toString(configuration.maxVisibleBars());
            case "configured_boss_count":
                return configuration == null ? "0" : Integer.toString(configuration.getTrackedBossCount());
            case "damage_ranking_boss_count":
                return configuration == null ? "0" : Integer.toString(configuration.getDamageRankingBossCount());
            case "max_damage_ranking_entries":
                return configuration == null ? "0" : Integer.toString(configuration.getMaxDamageRankingEntries());
            case "ui_id":
                return configuration == null ? "" : configuration.uiId();
            case "runtime_ui_id":
                String runtimeUiId = runtimeUiIdProvider.get();
                return runtimeUiId == null || runtimeUiId.isBlank()
                    ? (configuration == null ? "" : configuration.uiId())
                    : runtimeUiId;
            case "bridge_ready":
                xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI pb = packetBridgeProvider.get();
                return Boolean.toString(pb != null && pb.isAvailable());
            case "password_gate_locked":
                return "false";
            case "first_boss_reload_status":
                return "";
            case "active_viewer_count":
                return trackerService == null ? "0" : Integer.toString(trackerService.getActiveViewerCount());
            case "active_session_count":
                return trackerService == null ? "0" : Integer.toString(trackerService.getActiveSessionCount());
            case "target_active_count":
                EntityTargetHudService countService = targetServiceProvider.get();
                return countService == null ? "0" : Integer.toString(countService.activeTargetCount());
            case "target_viewer_count":
                EntityTargetHudService viewerService = targetServiceProvider.get();
                return viewerService == null ? "0" : Integer.toString(viewerService.activeViewerCount());
            default:
                break;
        }

        if (normalized.equals("has_target") || normalized.equals("target_present")) {
            return Boolean.toString(resolveTargetSnapshot(offlinePlayer).isPresent());
        }
        if (normalized.startsWith("target_")) {
            return resolveTargetPlaceholder(offlinePlayer, normalized.substring("target_".length()));
        }

        PlayerBossViewSnapshot snapshot = resolveSnapshot(offlinePlayer);
        return switch (normalized) {
            case "boss_count" -> Integer.toString(snapshot.bossCount());
            case "total_boss_count" -> Long.toString(snapshot.totalBossCount());
            default -> resolveDynamicPlaceholder(offlinePlayer, snapshot, normalized);
        };
    }

    private String resolveTargetPlaceholder(OfflinePlayer offlinePlayer, String field) {
        Optional<EntityTargetSnapshot> snapshot = resolveTargetSnapshot(offlinePlayer);
        if (snapshot.isEmpty()) {
            return "";
        }
        String resolved = snapshot.get().resolvePlaceholder(field);
        return resolved == null ? "" : resolved;
    }

    private Optional<EntityTargetSnapshot> resolveTargetSnapshot(OfflinePlayer offlinePlayer) {
        EntityTargetHudService targetService = targetServiceProvider.get();
        if (targetService == null || offlinePlayer == null) {
            return Optional.empty();
        }
        Player player = offlinePlayer.getPlayer();
        if (player == null || !player.isOnline()) {
            return Optional.empty();
        }
        return targetService.resolveViewerTargetSnapshot(player);
    }

    private PlayerBossViewSnapshot resolveSnapshot(OfflinePlayer offlinePlayer) {
        PluginConfiguration configuration = configurationProvider.get();
        int maxVisibleBars = configuration == null ? 5 : configuration.maxVisibleBars();
        var sortMode = configuration == null ? null : configuration.sortMode();
        if (sortMode == null) {
            sortMode = xuanmo.arcartxsuite.entitytracker.boss.config.BossSortMode.SPAWN_ORDER;
        }
        BossTrackerService trackerService = serviceProvider.get();
        Player player = offlinePlayer == null ? null : offlinePlayer.getPlayer();
        if (trackerService == null || player == null || !player.isOnline()) {
            return PlayerBossViewSnapshot.empty(maxVisibleBars, sortMode);
        }
        return trackerService.getViewerSnapshot(player);
    }

    private String resolveDynamicPlaceholder(OfflinePlayer offlinePlayer, PlayerBossViewSnapshot snapshot, String normalized) {
        if (normalized.startsWith("last_")) {
            return resolveLastSettlementPlaceholder(offlinePlayer, normalized);
        }
        if (isRankingPlaceholder(normalized)) {
            return resolveAlias(snapshot.slot(1), normalized);
        }
        if (normalized.startsWith("current_")) {
            return resolveAlias(snapshot.slot(1), normalized.substring("current_".length()));
        }
        if (normalized.startsWith("top_")) {
            return resolveAlias(snapshot.slot(1), normalized.substring("top_".length()));
        }
        return resolveSlotPlaceholder(snapshot, normalized);
    }

    private String resolveSlotPlaceholder(PlayerBossViewSnapshot snapshot, String normalized) {
        if (!normalized.startsWith("slot_")) {
            return null;
        }
        String[] parts = normalized.split("_", 3);
        if (parts.length < 3) {
            return null;
        }

        int slotIndex;
        try {
            slotIndex = Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            return null;
        }

        BossViewSlot slot = snapshot.slot(slotIndex);
        return resolveAlias(slot, parts[2]);
    }

    private String resolveLastSettlementPlaceholder(OfflinePlayer offlinePlayer, String normalized) {
        BossDamageSettlementPlaceholderContext context = resolveLastSettlementContext(offlinePlayer);
        String resolved = context.resolve(normalized.substring("last_".length()));
        return resolved == null ? "" : resolved;
    }

    private BossDamageSettlementPlaceholderContext resolveLastSettlementContext(OfflinePlayer offlinePlayer) {
        BossTrackerService trackerService = serviceProvider.get();
        if (trackerService == null) {
            return BossDamageSettlementPlaceholderContext.empty();
        }
        BossDamagePlayerSettlementView lastSettlement = resolveLastSettlement(offlinePlayer);
        return new BossDamageSettlementPlaceholderContext(
            lastSettlement.settlement(),
            lastSettlement.entry()
        );
    }

    private BossDamagePlayerSettlementView resolveLastSettlement(OfflinePlayer offlinePlayer) {
        BossTrackerService trackerService = serviceProvider.get();
        if (trackerService == null || offlinePlayer == null || offlinePlayer.getUniqueId() == null) {
            return BossDamagePlayerSettlementView.empty();
        }
        return trackerService.getLastSettlement(offlinePlayer.getUniqueId());
    }

    private String resolveAlias(BossViewSlot slot, String field) {
        String resolved = slot.resolve(field);
        return resolved == null ? "" : resolved;
    }

    private boolean isRankingPlaceholder(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        return startsWithRankPrefix(normalized, "top_");
    }

    private boolean startsWithRankPrefix(String normalized, String prefix) {
        if (!normalized.startsWith(prefix) || normalized.length() <= prefix.length()) {
            return false;
        }
        int separatorIndex = normalized.indexOf('_', prefix.length());
        if (separatorIndex <= prefix.length()) {
            return false;
        }
        for (int index = prefix.length(); index < separatorIndex; index++) {
            if (!Character.isDigit(normalized.charAt(index))) {
                return false;
            }
        }
        return separatorIndex + 1 < normalized.length();
    }
}


