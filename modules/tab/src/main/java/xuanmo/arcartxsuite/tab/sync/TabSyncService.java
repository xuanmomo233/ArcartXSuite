package xuanmo.arcartxsuite.tab.sync;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import xuanmo.arcartxsuite.tab.config.UiTarget;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.bridge.ArcartXPacketBridge;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.tab.config.TabDefinition;
import xuanmo.arcartxsuite.tab.config.TabFilterRule;
import xuanmo.arcartxsuite.tab.config.TabGroupingConfiguration;
import xuanmo.arcartxsuite.tab.config.TabModuleConfiguration;
import xuanmo.arcartxsuite.tab.config.TabPaginationConfiguration;
import xuanmo.arcartxsuite.tab.config.TabSortKey;
import xuanmo.arcartxsuite.tab.config.TabStyleConfiguration;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.tab.transport.TabRemoteEntry;
import xuanmo.arcartxsuite.tab.transport.TabServerSnapshot;
import xuanmo.arcartxsuite.tab.transport.TabSnapshotCodec;

public final class TabSyncService implements Listener, TabRefreshRequester, xuanmo.arcartxsuite.api.capability.TabRefreshable {

    private static final Pattern ARCARTX_ICON_TOKEN_PATTERN = Pattern.compile("%[0-9A-Za-z_:-]+<icon>");

    private final JavaPlugin plugin;
    private final TabModuleConfiguration configuration;
    private final ArcartXPacketBridge bridge;
    private final PacketGuardAPI packetGuard;
    private final CrossServerAPI crossServer;
    private final PlaceholderResolverAPI placeholderResolver;
    private final Map<String, Map<UUID, Object>> lastPayloads = new LinkedHashMap<>();
    private final Map<String, TabDefinition> definitionsById = new LinkedHashMap<>();
    private final TabRefreshQueue refreshQueue = new TabRefreshQueue();
    private final TabClientRefreshGuard clientRefreshGuard;
    private final Map<UUID, Set<String>> forcedViewerDefinitionRefreshes = new LinkedHashMap<>();
    private final Map<String, Map<String, List<TabRemoteEntry>>> remoteSnapshots = new LinkedHashMap<>();
    private final Map<String, Long> remoteSnapshotTimestamps = new LinkedHashMap<>();
    /** 每个玩家当前所选 view，未设置者使用 "default"。 */
    private final Map<UUID, String> viewerCurrentView = new LinkedHashMap<>();
    /** 每个玩家在每个 definition 上的当前页码（0 起）。 */
    private final Map<UUID, Map<String, Integer>> viewerPages = new LinkedHashMap<>();
    /** 上次跨服快照广播的 system tick（毫秒）每 definition，用于 batch.window-ticks 节流。 */
    private final Map<String, Long> lastBroadcastTimestamps = new LinkedHashMap<>();
    /** 退服宽限缓存：在 leave-grace-ms 内仍把这些玩家计入跨服快照。 */
    private final Map<UUID, GraceEntry> leaveGraceCache = new LinkedHashMap<>();
    /** 最近一次 {@link #sortPlayers} 的本服 ordered UUID 列表，供 PAPI rank/count 读取。 */
    private final Map<String, List<UUID>> lastSortedByDef = new LinkedHashMap<>();

    private record GraceEntry(String name, long expireAt, Map<String, Object> renderedPackByDefinition,
                              Map<String, Double> sortNumericByDefinition,
                              Map<String, String> sortStringByDefinition) {}

    private CrossServerChannel crossServerChannel;
    private BukkitTask refreshTask;
    private BukkitTask flushTask;

    public TabSyncService(
        JavaPlugin plugin,
        TabModuleConfiguration configuration,
        ArcartXPacketBridge bridge,
        PacketGuardAPI packetGuard,
        CrossServerAPI crossServer,
        PlaceholderResolverAPI placeholderResolver
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.bridge = bridge;
        this.packetGuard = packetGuard;
        this.crossServer = crossServer;
        this.placeholderResolver = placeholderResolver;
        this.clientRefreshGuard = new TabClientRefreshGuard(plugin.getLogger());
        for (TabDefinition definition : configuration.definitions()) {
            definitionsById.put(definition.id(), definition);
        }
    }

    /**
     * 统一占位符解析入口：先走内置解析器处理 %player_xxx% / %server_xxx%，
     * 若 PlaceholderAPI 存在则继续解析剩余外部占位符（如 %axstitle_xxx% 等）。
     */
    private String resolvePlaceholders(Player player, String text) {
        if (text == null) {
            return "";
        }
        String rendered = BuiltinPlaceholderResolver.resolve(text, player);
        if (player != null) {
            rendered = placeholderResolver.applyPlaceholders(player, rendered);
        }
        return rendered;
    }

    public void start() {
        shutdown();
        crossServerChannel = crossServer.openChannel(
            "tab",
            configuration.crossServer(),
            delivery -> handleRemoteSnapshotPayload(delivery.payload(), delivery.nodeId())
        );
        if (crossServerChannel.isActive()) {
            plugin.getLogger().fine("ArcartXTab 跨服通道已启用");
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        refreshTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::refresh,
            configuration.refreshIntervalTicks(),
            configuration.refreshIntervalTicks()
        );
        refresh();
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        if (crossServerChannel != null) {
            crossServerChannel.close();
            crossServerChannel = null;
        }
        HandlerList.unregisterAll(this);
        refreshQueue.clear();
        clientRefreshGuard.clear();
        forcedViewerDefinitionRefreshes.clear();
        lastPayloads.clear();
        remoteSnapshots.clear();
        remoteSnapshotTimestamps.clear();
        viewerCurrentView.clear();
        viewerPages.clear();
        lastBroadcastTimestamps.clear();
        leaveGraceCache.clear();
    }

    /** 公开 API：切换玩家当前 Tab 视图，触发一次强制刷新。 */
    public boolean setViewerView(Player viewer, String view) {
        if (viewer == null || !viewer.isOnline()) {
            return false;
        }
        String normalized = (view == null || view.isBlank()) ? "default" : view.trim();
        String previous = viewerCurrentView.put(viewer.getUniqueId(), normalized);
        if (normalized.equals(previous)) {
            return false;
        }
        // 强制刷新所有 definition：旧 view 的会被清空，新 view 的会重发
        List<String> allIds = new ArrayList<>(definitionsById.keySet());
        if (!allIds.isEmpty()) {
            requestForcedViewerRefresh(viewer, allIds, "view-switch:" + normalized);
        }
        return true;
    }

    public String currentView(Player viewer) {
        if (viewer == null) {
            return "default";
        }
        return viewerCurrentView.getOrDefault(viewer.getUniqueId(), "default");
    }

    /** 公开 API：调整 viewer 在指定 definition 上的页码（0 起）。 */
    public boolean setViewerPage(Player viewer, String definitionId, int page) {
        if (viewer == null || !viewer.isOnline() || definitionId == null || definitionId.isBlank()) {
            return false;
        }
        TabDefinition definition = definitionsById.get(definitionId);
        if (definition == null || !definition.pagination().enabled()) {
            return false;
        }
        Map<String, Integer> pageMap = viewerPages.computeIfAbsent(viewer.getUniqueId(), ignored -> new LinkedHashMap<>());
        Integer previous = pageMap.put(definitionId, Math.max(0, page));
        if (previous != null && previous == page) {
            return false;
        }
        requestForcedViewerRefresh(viewer, List.of(definitionId), "page-set:" + page);
        return true;
    }

    public int currentPage(Player viewer, String definitionId) {
        if (viewer == null) {
            return 0;
        }
        Map<String, Integer> pageMap = viewerPages.get(viewer.getUniqueId());
        if (pageMap == null) {
            return 0;
        }
        return pageMap.getOrDefault(definitionId, 0);
    }

    public boolean handleClientRefreshPacket(Player player, String packetId, List<String> data) {
        if (player == null || !player.isOnline() || !bridge.isAvailable()) {
            return false;
        }

        // 优先尝试匹配 pagination 翻页包
        if (handlePaginationPacket(player, packetId, data)) {
            return true;
        }

        boolean matched = false;
        List<String> matchedDefinitionIds = new ArrayList<>();
        for (TabDefinition definition : configuration.definitions()) {
            if (!definition.enabled() || !matchesClientRefreshPacket(definition, packetId, data)) {
                continue;
            }
            matched = true;
            if (!clientRefreshGuard.allow(player, definition, configuration.debug())) {
                continue;
            }
            matchedDefinitionIds.add(definition.id());
        }

        if (!matched) {
            return false;
        }

        if (matchedDefinitionIds.isEmpty()) {
            return true;
        }

        if (packetGuard != null && !packetGuard.allow(player, "tab", "refresh", configuration.debug())) {
            return true;
        }

        requestForcedViewerRefresh(player, matchedDefinitionIds, "client-packet:" + matchedDefinitionIds.get(0));
        return true;
    }

    private boolean handlePaginationPacket(Player player, String packetId, List<String> data) {
        if (packetId == null || data == null || data.isEmpty()) {
            return false;
        }
        for (TabDefinition definition : configuration.definitions()) {
            TabPaginationConfiguration pagination = definition.pagination();
            if (!pagination.enabled() || !pagination.packetId().equalsIgnoreCase(packetId)) {
                continue;
            }
            String action = data.get(0);
            int currentPage = currentPage(player, definition.id());
            int newPage;
            if (pagination.nextAction().equalsIgnoreCase(action)) {
                newPage = currentPage + 1;
            } else if (pagination.prevAction().equalsIgnoreCase(action)) {
                newPage = Math.max(0, currentPage - 1);
            } else if (pagination.setAction().equalsIgnoreCase(action) && data.size() >= 2) {
                try {
                    newPage = Math.max(0, Integer.parseInt(data.get(1).trim()));
                } catch (NumberFormatException ex) {
                    continue;
                }
            } else {
                continue;
            }
            if (packetGuard != null && !packetGuard.allow(player, "tab", "page", configuration.debug())) {
                return true;
            }
            if (!clientRefreshGuard.allow(player, definition, configuration.debug())) {
                return true;
            }
            setViewerPage(player, definition.id(), newPage);
            return true;
        }
        return false;
    }

    public boolean refreshViewer(Player viewer, String reason) {
        if (viewer == null || !viewer.isOnline() || !bridge.isAvailable()) {
            return false;
        }
        requestViewerRefresh(viewer, reason);
        return true;
    }

    @Override
    public void requestViewerRefresh(Player viewer, String reason) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        if (refreshQueue.requestViewer(viewer.getUniqueId())) {
            scheduleFlush(reason);
        }
    }

    @Override
    public void requestGlobalRefresh(String reason) {
        if (refreshQueue.requestGlobal()) {
            scheduleFlush(reason);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        leaveGraceCache.remove(event.getPlayer().getUniqueId());
        requestGlobalRefresh("player-join");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        for (Map<UUID, Object> deliveredPayloads : lastPayloads.values()) {
            deliveredPayloads.remove(playerId);
        }
        forcedViewerDefinitionRefreshes.remove(playerId);
        viewerCurrentView.remove(playerId);
        viewerPages.remove(playerId);

        // 退服宽限：把玩家最后一次的渲染快照存起来，跨服快照在 grace 期内仍带这一条
        long graceMs = configuration.leaveGraceMs();
        if (graceMs > 0L && crossServerChannel != null && crossServerChannel.isActive()) {
            cacheGraceEntry(player);
        }

        requestGlobalRefresh("player-quit");
    }

    private void cacheGraceEntry(Player player) {
        Map<String, Object> renderedByDef = new LinkedHashMap<>();
        Map<String, Double> sortNumByDef = new LinkedHashMap<>();
        Map<String, String> sortStrByDef = new LinkedHashMap<>();
        for (TabDefinition definition : configuration.definitions()) {
            if (!definition.enabled() || !isDefinitionCrossServer(definition)) {
                continue;
            }
            renderedByDef.put(definition.id(), renderNode(definition.packTemplate(), player));
            sortNumByDef.put(definition.id(), computeSortNumericValue(player, definition));
            sortStrByDef.put(definition.id(), computeSortStringValue(player, definition));
        }
        if (renderedByDef.isEmpty()) {
            return;
        }
        leaveGraceCache.put(player.getUniqueId(), new GraceEntry(
            player.getName(),
            System.currentTimeMillis() + configuration.leaveGraceMs(),
            renderedByDef,
            sortNumByDef,
            sortStrByDef
        ));
    }

    private void cleanupGraceCache() {
        if (leaveGraceCache.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        leaveGraceCache.entrySet().removeIf(entry -> entry.getValue().expireAt() < now);
    }

    private void refresh() {
        if (!bridge.isAvailable()) {
            return;
        }

        cleanupStaleSnapshots();
        cleanupGraceCache();
        List<Player> onlinePlayers = onlinePlayers();
        dispatchRefresh(onlinePlayers, onlinePlayers, "periodic", Map.of());
        broadcastLocalSnapshots(onlinePlayers);
        clientRefreshGuard.cleanup(definitionsById);
    }

    private void scheduleFlush(String reason) {
        if (flushTask != null) {
            return;
        }
        flushTask = Bukkit.getScheduler().runTask(plugin, () -> {
            flushTask = null;
            flushPendingRefreshes(reason);
        });
    }

    private void flushPendingRefreshes(String reason) {
        TabRefreshQueue.DrainResult drainResult = refreshQueue.drain();
        Map<UUID, Set<String>> forcedRefreshes = drainForcedRefreshes();
        if (!bridge.isAvailable()) {
            clientRefreshGuard.cleanup(definitionsById);
            return;
        }

        List<Player> onlinePlayers = onlinePlayers();
        if (drainResult.global()) {
            dispatchRefresh(onlinePlayers, onlinePlayers, reason + ":global", forcedRefreshes);
            clientRefreshGuard.cleanup(definitionsById);
            return;
        }
        if (drainResult.viewerIds().isEmpty()) {
            clientRefreshGuard.cleanup(definitionsById);
            return;
        }

        List<Player> viewers = new ArrayList<>();
        for (Player player : onlinePlayers) {
            if (drainResult.viewerIds().contains(player.getUniqueId())) {
                viewers.add(player);
            }
        }
        if (!viewers.isEmpty()) {
            dispatchRefresh(onlinePlayers, viewers, reason + ":viewer", forcedRefreshes);
        }
        clientRefreshGuard.cleanup(definitionsById);
    }

    private void dispatchRefresh(
        List<Player> onlinePlayers,
        Collection<Player> viewers,
        String reason,
        Map<UUID, Set<String>> forcedRefreshes
    ) {
        for (TabDefinition definition : configuration.definitions()) {
            if (!definition.enabled()) {
                continue;
            }

            // 与 viewer 当前 view 不匹配的 definition：发空 payload 用于"清空"，仅在 view 切换时触发
            // （即 forcedRefreshes 中包含此 definition 时）。
            Map<UUID, Object> deliveredPayloads = lastPayloads.computeIfAbsent(definition.id(), ignored -> new LinkedHashMap<>());
            deliveredPayloads.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);

            int deliveredCount = 0;
            // 按 viewer 维度构建 payload（pagination 时不同 viewer 可能在不同页）
            for (Player viewer : viewers) {
                if (viewer == null || !viewer.isOnline()) {
                    continue;
                }
                String viewerView = currentView(viewer);
                boolean viewMatches = definition.view().equals(viewerView);
                boolean force = isForcedRefresh(forcedRefreshes, viewer.getUniqueId(), definition.id());

                Object payload;
                if (!viewMatches) {
                    // view 不匹配：仅在强制刷新（view 刚切换）时发送一次空列表来清空 UI
                    if (!force) {
                        continue;
                    }
                    payload = TabPayloadAssembler.create(definition.packTemplate(), 0);
                } else {
                    payload = buildPayloadForViewer(definition, onlinePlayers, viewer);
                }

                Object previousPayload = deliveredPayloads.get(viewer.getUniqueId());
                if (!force && TabPayloadAssembler.structurallyEquals(payload, previousPayload)) {
                    continue;
                }
                if (configuration.dryRun()) {
                    // dry-run：不发送、不更新缓存，仅记日志（每个 viewer / definition 一次）
                    if (configuration.debug()) {
                        plugin.getLogger().info(
                            "ArcartXTab[dry-run] skip send def=" + definition.id()
                                + " viewer=" + viewer.getName()
                                + " | reason=" + reason
                        );
                    }
                    deliveredCount++;
                    continue;
                }
                boolean sent = false;
                for (UiTarget target : definition.uiTargets()) {
                    sent |= bridge.sendPacket(viewer, target.uiId(), target.packetHandler(), payload);
                }
                if (sent) {
                    deliveredPayloads.put(viewer.getUniqueId(), TabPayloadAssembler.snapshot(payload));
                    deliveredCount++;
                }
            }

            if ((configuration.debug() || configuration.dryRun()) && deliveredCount > 0) {
                plugin.getLogger().info(
                    "ArcartXTab 发包[" + definition.id() + "] -> viewers="
                        + deliveredCount
                        + "/"
                        + viewers.size()
                        + " | reason="
                        + reason
                        + " | targets="
                        + definition.uiTargets()
                );
            }
        }
    }

    /**
     * 为单个 viewer 构建 payload：先按是否跨服分两支，再依次应用 pagination / grouping。
     */
    private Object buildPayloadForViewer(TabDefinition definition, List<Player> onlinePlayers, Player viewer) {
        // aggregate 模式：每节点一行，不展开玩家
        if (definition.aggregate().enabled() && isDefinitionCrossServer(definition)) {
            return buildAggregatePayload(definition, onlinePlayers);
        }

        if (isDefinitionCrossServer(definition)) {
            // cross-server 路径已支持多键排序 / grouping / pagination（按 viewer 维度切片）
            return buildCrossServerPayload(definition, onlinePlayers, viewer);
        }

        List<Player> sorted = sortPlayers(onlinePlayers, definition);
        sorted = applyPagination(sorted, definition, viewer);
        return buildPayload(definition, sorted);
    }

    private List<Player> applyPagination(List<Player> sorted, TabDefinition definition, Player viewer) {
        TabPaginationConfiguration pagination = definition.pagination();
        if (!pagination.enabled() || sorted.isEmpty()) {
            return sorted;
        }
        int pageSize = pagination.pageSize();
        int totalPages = Math.max(1, (sorted.size() + pageSize - 1) / pageSize);
        int currentPage = Math.min(totalPages - 1, currentPage(viewer, definition.id()));
        if (currentPage < 0) {
            currentPage = 0;
        }
        // 修正越界的 viewer 页码
        if (currentPage != currentPage(viewer, definition.id())) {
            Map<String, Integer> pageMap = viewerPages.computeIfAbsent(viewer.getUniqueId(), ignored -> new LinkedHashMap<>());
            pageMap.put(definition.id(), currentPage);
        }
        int from = currentPage * pageSize;
        int to = Math.min(sorted.size(), from + pageSize);
        return sorted.subList(from, to);
    }

    private void requestForcedViewerRefresh(Player viewer, Collection<String> definitionIds, String reason) {
        if (viewer == null || !viewer.isOnline() || definitionIds == null || definitionIds.isEmpty()) {
            return;
        }
        forcedViewerDefinitionRefreshes
            .computeIfAbsent(viewer.getUniqueId(), ignored -> new LinkedHashSet<>())
            .addAll(definitionIds);
        if (refreshQueue.requestViewer(viewer.getUniqueId())) {
            scheduleFlush(reason);
        }
    }

    private Map<UUID, Set<String>> drainForcedRefreshes() {
        if (forcedViewerDefinitionRefreshes.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<String>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : forcedViewerDefinitionRefreshes.entrySet()) {
            snapshot.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        forcedViewerDefinitionRefreshes.clear();
        return Map.copyOf(snapshot);
    }

    private static boolean isForcedRefresh(Map<UUID, Set<String>> forcedRefreshes, UUID viewerId, String definitionId) {
        if (forcedRefreshes == null || forcedRefreshes.isEmpty() || viewerId == null || definitionId == null) {
            return false;
        }
        Set<String> definitionIds = forcedRefreshes.get(viewerId);
        return definitionIds != null && definitionIds.contains(definitionId);
    }

    private List<Player> onlinePlayers() {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return onlinePlayers;
    }

    private static boolean matchesClientRefreshPacket(TabDefinition definition, String packetId, List<String> data) {
        if (definition.clientRefreshPacketId().isBlank() || packetId == null) {
            return false;
        }
        if (!definition.clientRefreshPacketId().equalsIgnoreCase(packetId)) {
            return false;
        }
        if (definition.clientRefreshAction().isBlank()) {
            return true;
        }
        return data != null && !data.isEmpty() && definition.clientRefreshAction().equalsIgnoreCase(data.get(0));
    }

    private Object buildPayload(TabDefinition definition, List<Player> onlinePlayers) {
        Object packTemplate = definition.packTemplate();
        Object payload = TabPayloadAssembler.create(packTemplate, onlinePlayers.size());

        TabGroupingConfiguration grouping = definition.grouping();
        if (grouping.enabled() && !grouping.groupByPapi().isBlank()) {
            // map pack 不支持分组，退化
            if (packTemplate instanceof Map<?, ?>) {
                if (configuration.debug()) {
                    plugin.getLogger().fine("ArcartXTab 分组在 map pack 模式下不生效，已退化为不分组");
                }
            } else {
                appendGroupedEntries(payload, packTemplate, onlinePlayers, definition, grouping);
                return payload;
            }
        }

        for (Player target : onlinePlayers) {
            Object rendered = renderNode(packTemplate, target);
            TabPayloadAssembler.append(payload, packTemplate, rendered, definition.omitBlankValues());
        }
        return payload;
    }

    private void appendGroupedEntries(
        Object payload,
        Object packTemplate,
        List<Player> sorted,
        TabDefinition definition,
        TabGroupingConfiguration grouping
    ) {
        // 按玩家解析 group key
        Map<String, List<Player>> grouped = new LinkedHashMap<>();
        for (Player player : sorted) {
            String key = resolvePlaceholders(player, grouping.groupByPapi());
            if (key == null || key.isBlank() || key.equals(grouping.groupByPapi())) {
                key = "default";
            }
            key = key.trim();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(player);
        }

        // 按 groupOrder 顺序输出
        Set<String> emitted = new LinkedHashSet<>();
        for (String orderedKey : grouping.groupOrder()) {
            String normalized = orderedKey == null ? "" : orderedKey.trim();
            if (normalized.isBlank()) {
                continue;
            }
            List<Player> members = grouped.get(normalized);
            if (members == null || members.isEmpty()) {
                continue;
            }
            emitGroup(payload, packTemplate, definition, grouping, normalized, members);
            emitted.add(normalized);
        }
        // 未列出的组：是否输出
        if (grouping.includeUnordered()) {
            for (Map.Entry<String, List<Player>> entry : grouped.entrySet()) {
                if (emitted.contains(entry.getKey())) {
                    continue;
                }
                emitGroup(payload, packTemplate, definition, grouping, entry.getKey(), entry.getValue());
            }
        }
    }

    private void emitGroup(
        Object payload,
        Object packTemplate,
        TabDefinition definition,
        TabGroupingConfiguration grouping,
        String groupKey,
        List<Player> members
    ) {
        if (members.isEmpty()) {
            return;
        }
        // header（{group} 占位符替换）
        if (grouping.headerPack() != null) {
            Object header = renderGroupHeader(grouping.headerPack(), groupKey, members.get(0));
            TabPayloadAssembler.append(payload, packTemplate, header, false);
        }
        for (Player member : members) {
            Object rendered = renderNode(packTemplate, member);
            TabPayloadAssembler.append(payload, packTemplate, rendered, definition.omitBlankValues());
        }
    }

    private Object renderGroupHeader(Object headerTemplate, String groupKey, Player anchor) {
        if (headerTemplate instanceof String stringHeader) {
            String replaced = stringHeader.replace("{group}", groupKey);
            return renderString(replaced, anchor);
        }
        if (headerTemplate instanceof List<?> listHeader) {
            List<Object> rendered = new ArrayList<>(listHeader.size());
            for (Object entry : listHeader) {
                rendered.add(renderGroupHeader(entry, groupKey, anchor));
            }
            return rendered;
        }
        if (headerTemplate instanceof Map<?, ?> mapHeader) {
            Map<String, Object> rendered = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapHeader.entrySet()) {
                String rawKey = String.valueOf(entry.getKey()).replace("{group}", groupKey);
                rendered.put(rawKey, renderGroupHeader(entry.getValue(), groupKey, anchor));
            }
            return rendered;
        }
        return headerTemplate;
    }

    private Object buildAggregatePayload(TabDefinition definition, List<Player> onlinePlayers) {
        Object packTemplate = definition.packTemplate();
        Object linePack = definition.aggregate().linePack();
        if (linePack == null) {
            linePack = packTemplate;
        }
        Object payload = TabPayloadAssembler.create(packTemplate, 1 + remoteSnapshots.size());

        // 本服一行
        Object localLine = renderAggregateLine(
            linePack,
            configuration.serverId(),
            onlinePlayers.size(),
            onlinePlayers.isEmpty() ? null : onlinePlayers.get(0)
        );
        TabPayloadAssembler.append(payload, packTemplate, localLine, definition.omitBlankValues());

        // 远程节点逐一一行（基于该 definition 的快照计数）
        for (Map.Entry<String, Map<String, List<TabRemoteEntry>>> nodeEntry : remoteSnapshots.entrySet()) {
            List<TabRemoteEntry> remoteEntries = nodeEntry.getValue().get(definition.id());
            if (remoteEntries == null) {
                continue;
            }
            Object remoteLine = renderAggregateLine(
                linePack,
                nodeEntry.getKey(),
                remoteEntries.size(),
                onlinePlayers.isEmpty() ? null : onlinePlayers.get(0)
            );
            TabPayloadAssembler.append(payload, packTemplate, remoteLine, definition.omitBlankValues());
        }
        return payload;
    }

    private Object renderAggregateLine(Object linePack, String serverId, int onlineCount, Player anchor) {
        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("server-id", serverId);
        tokens.put("server-display", serverId);
        tokens.put("server-online", String.valueOf(onlineCount));
        return renderAggregateNode(linePack, tokens, anchor);
    }

    private Object renderAggregateNode(Object node, Map<String, String> tokens, Player anchor) {
        if (node instanceof String stringNode) {
            String replaced = stringNode;
            for (Map.Entry<String, String> entry : tokens.entrySet()) {
                replaced = replaced.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            return anchor == null ? replaced : renderString(replaced, anchor);
        }
        if (node instanceof List<?> listNode) {
            List<Object> rendered = new ArrayList<>(listNode.size());
            for (Object entry : listNode) {
                rendered.add(renderAggregateNode(entry, tokens, anchor));
            }
            return rendered;
        }
        if (node instanceof Map<?, ?> mapNode) {
            Map<String, Object> rendered = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapNode.entrySet()) {
                String rawKey = String.valueOf(entry.getKey());
                for (Map.Entry<String, String> tok : tokens.entrySet()) {
                    rawKey = rawKey.replace("{" + tok.getKey() + "}", tok.getValue());
                }
                rendered.put(rawKey, renderAggregateNode(entry.getValue(), tokens, anchor));
            }
            return rendered;
        }
        return node;
    }

    private void handleRemoteSnapshotPayload(String payload, String nodeId) {
        if (payload == null || payload.isBlank() || nodeId == null || nodeId.isBlank()) {
            return;
        }
        try {
            TabServerSnapshot snapshot = TabSnapshotCodec.decode(payload);
            handleRemoteSnapshot(snapshot);
        } catch (Exception exception) {
            plugin.getLogger().warning("ArcartXTab 解析跨服快照失败: " + exception.getMessage());
        }
    }

    private void handleRemoteSnapshot(TabServerSnapshot snapshot) {
        if (snapshot == null || snapshot.nodeId().isBlank()) {
            return;
        }
        if (crossServer.nodeId().equalsIgnoreCase(snapshot.nodeId())) {
            return;
        }
        remoteSnapshots
            .computeIfAbsent(snapshot.nodeId(), ignored -> new LinkedHashMap<>())
            .put(snapshot.definitionId(), snapshot.entries());
        remoteSnapshotTimestamps.put(snapshot.nodeId(), System.currentTimeMillis());
        if (configuration.debug()) {
            plugin.getLogger().info(
                "ArcartXTab 收到跨服快照 node=" + snapshot.nodeId()
                    + " | def=" + snapshot.definitionId()
                    + " | entries=" + snapshot.entries().size()
            );
        }
        requestGlobalRefresh("cross-server");
    }

    private boolean isDefinitionCrossServer(TabDefinition definition) {
        if (definition.crossServer() != null) {
            return definition.crossServer();
        }
        return configuration.crossServerDefault();
    }

    /**
     * 跨服合并条目：本服 entry 携带 Player，远程 entry 携带 TabRemoteEntry。
     * 多键排序值按 {@code definition.sortKeys()} 顺序展开，远程 entry 直接复用 v2 协议字段。
     */
    private record SortableEntry(
        Player localPlayer,
        TabRemoteEntry remoteEntry,
        List<Double> sortValues,
        List<String> sortStringValues,
        String groupKey,
        String playerName,
        UUID playerId
    ) {
    }

    private Object buildCrossServerPayload(TabDefinition definition, List<Player> onlinePlayers, Player viewer) {
        Object packTemplate = definition.packTemplate();

        // 1. 收集本服 + 远程条目（本服先应用 filters / hide-vanished）
        List<Player> filteredLocals = applyFilters(onlinePlayers, definition);
        List<SortableEntry> allEntries = new ArrayList<>(filteredLocals.size() + 16);
        for (Player player : filteredLocals) {
            allEntries.add(new SortableEntry(
                player,
                null,
                computeSortValuesPerKey(player, definition),
                computeSortStringValuesPerKey(player, definition),
                computeGroupKey(player, definition),
                player.getName(),
                player.getUniqueId()
            ));
        }
        for (Map.Entry<String, Map<String, List<TabRemoteEntry>>> nodeEntry : remoteSnapshots.entrySet()) {
            List<TabRemoteEntry> remoteEntries = nodeEntry.getValue().get(definition.id());
            if (remoteEntries == null) {
                continue;
            }
            for (TabRemoteEntry remote : remoteEntries) {
                UUID id;
                try {
                    id = UUID.fromString(remote.playerUuid());
                } catch (Exception ex) {
                    id = null;
                }
                allEntries.add(new SortableEntry(
                    null,
                    remote,
                    remote.sortValues(),
                    remote.sortStringValues(),
                    remote.groupKey(),
                    remote.playerName(),
                    id
                ));
            }
        }

        // 2. pinned 分桶（远程条目无法判定 permission/papi rule，统一进入 middle）
        List<SortableEntry> top = new ArrayList<>();
        List<SortableEntry> middle = new ArrayList<>();
        List<SortableEntry> bottom = new ArrayList<>();
        for (SortableEntry entry : allEntries) {
            Player local = entry.localPlayer();
            if (local != null && matchesAnyRule(local, definition.pinnedTop())) {
                top.add(entry);
            } else if (local != null && matchesAnyRule(local, definition.pinnedBottom())) {
                bottom.add(entry);
            } else {
                middle.add(entry);
            }
        }

        // 3. 多键 comparator
        Comparator<SortableEntry> comparator = composedSortableComparator(definition.sortKeys());
        top.sort(comparator);
        middle.sort(comparator);
        bottom.sort(comparator);

        List<SortableEntry> ordered = new ArrayList<>(top.size() + middle.size() + bottom.size());
        ordered.addAll(top);
        ordered.addAll(middle);
        ordered.addAll(bottom);

        // 4. maxEntries 截断
        if (definition.maxEntries() >= 0 && ordered.size() > definition.maxEntries()) {
            ordered = new ArrayList<>(ordered.subList(0, definition.maxEntries()));
        }

        // 5. 同步 lastSortedByDef：跨服模式下也让 PAPI rank/count 可用
        List<UUID> sortedIds = new ArrayList<>(ordered.size());
        for (SortableEntry e : ordered) {
            if (e.playerId() != null) {
                sortedIds.add(e.playerId());
            }
        }
        lastSortedByDef.put(definition.id(), List.copyOf(sortedIds));

        // 6. pagination 切片（基于 viewer）
        ordered = applyPaginationToSortable(ordered, definition, viewer);

        // 7. 渲染 + grouping
        Object payload = TabPayloadAssembler.create(packTemplate, ordered.size());
        TabGroupingConfiguration grouping = definition.grouping();
        boolean groupingEnabled = grouping.enabled() && !grouping.groupByPapi().isBlank()
            && !(packTemplate instanceof Map<?, ?>);
        if (groupingEnabled) {
            emitCrossServerGrouped(payload, packTemplate, definition, grouping, ordered);
        } else {
            for (SortableEntry entry : ordered) {
                Object rendered = entry.localPlayer() != null
                    ? renderNode(packTemplate, entry.localPlayer())
                    : entry.remoteEntry().renderedPack();
                TabPayloadAssembler.append(payload, packTemplate, rendered, definition.omitBlankValues());
            }
        }
        return payload;
    }

    private List<SortableEntry> applyPaginationToSortable(List<SortableEntry> sorted, TabDefinition definition, Player viewer) {
        TabPaginationConfiguration pagination = definition.pagination();
        if (!pagination.enabled() || sorted.isEmpty() || viewer == null) {
            return sorted;
        }
        int pageSize = pagination.pageSize();
        int totalPages = Math.max(1, (sorted.size() + pageSize - 1) / pageSize);
        int currentPage = Math.min(totalPages - 1, currentPage(viewer, definition.id()));
        if (currentPage < 0) {
            currentPage = 0;
        }
        if (currentPage != currentPage(viewer, definition.id())) {
            Map<String, Integer> pageMap = viewerPages.computeIfAbsent(viewer.getUniqueId(), ignored -> new LinkedHashMap<>());
            pageMap.put(definition.id(), currentPage);
        }
        int from = currentPage * pageSize;
        int to = Math.min(sorted.size(), from + pageSize);
        return sorted.subList(from, to);
    }

    private void emitCrossServerGrouped(
        Object payload,
        Object packTemplate,
        TabDefinition definition,
        TabGroupingConfiguration grouping,
        List<SortableEntry> ordered
    ) {
        // 按 groupKey 分桶（本服现算，远程使用快照中的 groupKey）
        Map<String, List<SortableEntry>> grouped = new LinkedHashMap<>();
        for (SortableEntry entry : ordered) {
            String key = entry.groupKey();
            if (key == null || key.isBlank()) {
                key = "default";
            }
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        }

        Set<String> emitted = new LinkedHashSet<>();
        for (String orderedKey : grouping.groupOrder()) {
            String normalized = orderedKey == null ? "" : orderedKey.trim();
            if (normalized.isBlank()) {
                continue;
            }
            List<SortableEntry> members = grouped.get(normalized);
            if (members == null || members.isEmpty()) {
                continue;
            }
            emitCrossGroup(payload, packTemplate, definition, grouping, normalized, members);
            emitted.add(normalized);
        }
        if (grouping.includeUnordered()) {
            for (Map.Entry<String, List<SortableEntry>> e : grouped.entrySet()) {
                if (emitted.contains(e.getKey())) {
                    continue;
                }
                emitCrossGroup(payload, packTemplate, definition, grouping, e.getKey(), e.getValue());
            }
        }
    }

    private void emitCrossGroup(
        Object payload,
        Object packTemplate,
        TabDefinition definition,
        TabGroupingConfiguration grouping,
        String groupKey,
        List<SortableEntry> members
    ) {
        if (members.isEmpty()) {
            return;
        }
        if (grouping.headerPack() != null) {
            // 找一个 anchor player 用于 header 内 PAPI 渲染：优先本服玩家
            Player anchor = null;
            for (SortableEntry e : members) {
                if (e.localPlayer() != null) {
                    anchor = e.localPlayer();
                    break;
                }
            }
            Object header = renderGroupHeader(grouping.headerPack(), groupKey, anchor);
            TabPayloadAssembler.append(payload, packTemplate, header, false);
        }
        for (SortableEntry entry : members) {
            Object rendered = entry.localPlayer() != null
                ? renderNode(packTemplate, entry.localPlayer())
                : entry.remoteEntry().renderedPack();
            TabPayloadAssembler.append(payload, packTemplate, rendered, definition.omitBlankValues());
        }
    }

    private Comparator<SortableEntry> composedSortableComparator(List<TabSortKey> sortKeys) {
        Comparator<SortableEntry> composed = null;
        for (int i = 0; i < sortKeys.size(); i++) {
            int index = i;
            TabSortKey key = sortKeys.get(i);
            Comparator<SortableEntry> step;
            if (key.mode() == xuanmo.arcartxsuite.tab.config.TabSortMode.NAME
                || (key.mode() == xuanmo.arcartxsuite.tab.config.TabSortMode.PAPI && !key.papiNumeric())
                || key.mode() == xuanmo.arcartxsuite.tab.config.TabSortMode.PREM) {
                step = Comparator.comparing(
                    (SortableEntry e) -> sortStringAt(e, index),
                    String.CASE_INSENSITIVE_ORDER
                );
                // PREM 同时需要数值优先（与本地 comparatorForKey 一致：先 index 后 group 名）
                if (key.mode() == xuanmo.arcartxsuite.tab.config.TabSortMode.PREM) {
                    Comparator<SortableEntry> numeric = Comparator.comparingDouble(e -> sortNumericAt(e, index));
                    step = numeric.thenComparing(step);
                }
            } else {
                step = Comparator.comparingDouble(e -> sortNumericAt(e, index));
            }
            if (key.descending()) {
                step = step.reversed();
            }
            composed = composed == null ? step : composed.thenComparing(step);
        }
        if (composed == null) {
            composed = Comparator.comparing(SortableEntry::playerName, String.CASE_INSENSITIVE_ORDER);
        }
        return composed.thenComparing(SortableEntry::playerName, String.CASE_INSENSITIVE_ORDER);
    }

    private static double sortNumericAt(SortableEntry entry, int index) {
        List<Double> values = entry.sortValues();
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        return values.get(Math.min(index, values.size() - 1));
    }

    private static String sortStringAt(SortableEntry entry, int index) {
        List<String> values = entry.sortStringValues();
        if (values == null || values.isEmpty()) {
            return entry.playerName() == null ? "" : entry.playerName();
        }
        String value = values.get(Math.min(index, values.size() - 1));
        return value == null ? "" : value;
    }

    /** 按 {@code definition.sortKeys()} 顺序计算每个键的 numeric 值。 */
    private List<Double> computeSortValuesPerKey(Player player, TabDefinition definition) {
        List<TabSortKey> keys = definition.sortKeys();
        List<Double> result = new ArrayList<>(keys.size());
        for (TabSortKey key : keys) {
            switch (key.mode()) {
                case NAME -> result.add(0.0);
                case PREM -> result.add((double) resolvePremIndex(player, key.premGroups()));
                case PAPI -> result.add(key.papiNumeric()
                    ? resolvePapiNumericValue(player, key.papiKey())
                    : 0.0);
            }
        }
        return result;
    }

    /** 按 {@code definition.sortKeys()} 顺序计算每个键的 string 值。 */
    private List<String> computeSortStringValuesPerKey(Player player, TabDefinition definition) {
        List<TabSortKey> keys = definition.sortKeys();
        List<String> result = new ArrayList<>(keys.size());
        for (TabSortKey key : keys) {
            switch (key.mode()) {
                case NAME -> result.add(player.getName());
                case PREM -> result.add(resolvePremGroup(player, key.premGroups()));
                case PAPI -> result.add(key.papiNumeric() ? "" : resolvePapiStringValue(player, key.papiKey()));
            }
        }
        return result;
    }

    /** 计算玩家的 grouping key（PAPI 解析），未启用 grouping 时返回空串。 */
    private String computeGroupKey(Player player, TabDefinition definition) {
        TabGroupingConfiguration grouping = definition.grouping();
        if (!grouping.enabled() || grouping.groupByPapi().isBlank()) {
            return "";
        }
        String resolved = resolvePlaceholders(player, grouping.groupByPapi());
        if (resolved == null || resolved.isBlank() || resolved.equals(grouping.groupByPapi())) {
            return "default";
        }
        return resolved.trim();
    }

    private double computeSortNumericValue(Player player, TabDefinition definition) {
        return switch (definition.sortMode()) {
            case NAME -> 0.0;
            case PREM -> resolvePremIndex(player, definition);
            case PAPI -> definition.sortPapiNumeric()
                ? resolvePapiNumericValue(player, definition.sortPapiKey())
                : 0.0;
        };
    }

    private String computeSortStringValue(Player player, TabDefinition definition) {
        return switch (definition.sortMode()) {
            case NAME -> player.getName();
            case PREM -> resolvePremGroup(player, definition);
            case PAPI -> definition.sortPapiNumeric()
                ? ""
                : resolvePapiStringValue(player, definition.sortPapiKey());
        };
    }

    private void broadcastLocalSnapshots(List<Player> onlinePlayers) {
        if (crossServerChannel == null || !crossServerChannel.isActive()) {
            return;
        }
        long now = System.currentTimeMillis();
        long batchWindowMs = configuration.batchWindowTicks() * 50L; // 1 tick = 50ms
        String nodeId = crossServer.nodeId();
        for (TabDefinition definition : configuration.definitions()) {
            if (!definition.enabled() || !isDefinitionCrossServer(definition)) {
                continue;
            }
            Long lastBroadcast = lastBroadcastTimestamps.get(definition.id());
            if (batchWindowMs > 0L && lastBroadcast != null && now - lastBroadcast < batchWindowMs) {
                continue;
            }

            List<TabRemoteEntry> entries = collectLocalEntriesForBroadcast(definition, onlinePlayers);

            TabServerSnapshot snapshot = new TabServerSnapshot(
                nodeId,
                definition.id(),
                now,
                List.copyOf(entries)
            );
            crossServerChannel.publish(TabSnapshotCodec.encode(snapshot));
            lastBroadcastTimestamps.put(definition.id(), now);
            if (configuration.debug()) {
                plugin.getLogger().info(
                    "ArcartXTab 广播跨服快照 def=" + definition.id()
                        + " | entries=" + entries.size()
                        + " | grace=" + leaveGraceCache.size()
                );
            }
        }
    }

    /**
     * 构造单个 definition 的本地条目（在线玩家 + 退服宽限缓存），
     * 供 {@link #broadcastLocalSnapshots} 与 snapshot save 共享。
     */
    public List<TabRemoteEntry> collectLocalEntriesForBroadcast(TabDefinition definition, List<Player> onlinePlayers) {
        List<TabRemoteEntry> entries = new ArrayList<>(onlinePlayers.size() + leaveGraceCache.size());
        for (Player player : onlinePlayers) {
            List<Double> sortValues = computeSortValuesPerKey(player, definition);
            List<String> sortStrings = computeSortStringValuesPerKey(player, definition);
            String groupKey = computeGroupKey(player, definition);
            entries.add(new TabRemoteEntry(
                player.getUniqueId().toString(),
                player.getName(),
                sortValues.isEmpty() ? 0.0 : sortValues.get(0),
                sortStrings.isEmpty() ? player.getName() : sortStrings.get(0),
                sortValues,
                sortStrings,
                groupKey,
                renderNode(definition.packTemplate(), player)
            ));
        }
        if (!leaveGraceCache.isEmpty()) {
            for (Map.Entry<UUID, GraceEntry> entry : leaveGraceCache.entrySet()) {
                if (onlinePlayers.stream().anyMatch(player -> player.getUniqueId().equals(entry.getKey()))) {
                    continue;
                }
                GraceEntry grace = entry.getValue();
                Object rendered = grace.renderedPackByDefinition().get(definition.id());
                if (rendered == null) {
                    continue;
                }
                double primaryNum = grace.sortNumericByDefinition().getOrDefault(definition.id(), 0.0);
                String primaryStr = grace.sortStringByDefinition().getOrDefault(definition.id(), grace.name());
                entries.add(new TabRemoteEntry(
                    entry.getKey().toString(),
                    grace.name(),
                    primaryNum,
                    primaryStr,
                    List.of(primaryNum),
                    List.of(primaryStr),
                    "",
                    rendered
                ));
            }
        }
        return entries;
    }

    /** 当前服务节点 id（用于 snapshot save 元数据）。 */
    public String serverId() {
        return configuration.serverId();
    }

    /** 提供给 snapshot save 使用：当前本服每个 cross-server definition 的本地条目快照。 */
    public Map<String, List<TabRemoteEntry>> snapshotLocalEntries() {
        Map<String, List<TabRemoteEntry>> result = new LinkedHashMap<>();
        List<Player> online = onlinePlayers();
        for (TabDefinition definition : configuration.definitions()) {
            if (!definition.enabled()) {
                continue;
            }
            result.put(definition.id(), collectLocalEntriesForBroadcast(definition, online));
        }
        return result;
    }

    /** 提供给 snapshot save 使用：当前 remoteSnapshots 的不可变浅拷贝。 */
    public Map<String, Map<String, List<TabRemoteEntry>>> snapshotRemoteEntries() {
        Map<String, Map<String, List<TabRemoteEntry>>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<TabRemoteEntry>>> e : remoteSnapshots.entrySet()) {
            result.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        }
        return result;
    }

    /**
     * snapshot load 注入入口：把存档的快照装入 {@link #remoteSnapshots}，使用 {@code "snapshot:"} 前缀的虚拟 nodeId
     * 防止与真实跨服节点冲突，并在 {@link #cleanupStaleSnapshots} 中豁免清理。
     *
     * @param virtualNodeId 必须以 {@code "snapshot:"} 开头
     */
    public void installSnapshotPayload(String virtualNodeId, Map<String, List<TabRemoteEntry>> entriesByDefinition) {
        if (virtualNodeId == null || !virtualNodeId.startsWith("snapshot:")) {
            throw new IllegalArgumentException("virtualNodeId must start with 'snapshot:'");
        }
        Map<String, List<TabRemoteEntry>> defMap = new LinkedHashMap<>();
        for (Map.Entry<String, List<TabRemoteEntry>> e : entriesByDefinition.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            defMap.put(e.getKey(), List.copyOf(e.getValue()));
        }
        remoteSnapshots.put(virtualNodeId, defMap);
        remoteSnapshotTimestamps.put(virtualNodeId, System.currentTimeMillis());
        requestGlobalRefresh("snapshot-install:" + virtualNodeId);
    }

    /** 移除已注入的快照（按完整 virtualNodeId）。 */
    public boolean uninstallSnapshotPayload(String virtualNodeId) {
        boolean removed = remoteSnapshots.remove(virtualNodeId) != null;
        remoteSnapshotTimestamps.remove(virtualNodeId);
        if (removed) {
            requestGlobalRefresh("snapshot-uninstall:" + virtualNodeId);
        }
        return removed;
    }

    /** 列出当前所有已注入的虚拟节点 id。 */
    public List<String> installedSnapshotNodeIds() {
        List<String> ids = new ArrayList<>();
        for (String nodeId : remoteSnapshots.keySet()) {
            if (nodeId != null && nodeId.startsWith("snapshot:")) {
                ids.add(nodeId);
            }
        }
        return ids;
    }

    private void cleanupStaleSnapshots() {
        long now = System.currentTimeMillis();
        long ttl = configuration.staleSnapshotMs();
        remoteSnapshotTimestamps.entrySet().removeIf(entry -> {
            // 豁免 snapshot:* 虚拟节点（由命令显式 uninstall 控制）
            if (entry.getKey() != null && entry.getKey().startsWith("snapshot:")) {
                return false;
            }
            if (now - entry.getValue() > ttl) {
                remoteSnapshots.remove(entry.getKey());
                if (configuration.debug()) {
                    plugin.getLogger().info("ArcartXTab 清理过期跨服快照 node=" + entry.getKey());
                }
                return true;
            }
            return false;
        });
    }

    private List<Player> sortPlayers(List<Player> onlinePlayers, TabDefinition definition) {
        List<Player> filtered = applyFilters(onlinePlayers, definition);

        Comparator<Player> comparator = composedComparator(definition.sortKeys(), definition);

        List<Player> top = new ArrayList<>();
        List<Player> middle = new ArrayList<>();
        List<Player> bottom = new ArrayList<>();
        for (Player player : filtered) {
            if (matchesAnyRule(player, definition.pinnedTop())) {
                top.add(player);
            } else if (matchesAnyRule(player, definition.pinnedBottom())) {
                bottom.add(player);
            } else {
                middle.add(player);
            }
        }
        top.sort(comparator);
        middle.sort(comparator);
        bottom.sort(comparator);

        List<Player> ordered = new ArrayList<>(top.size() + middle.size() + bottom.size());
        ordered.addAll(top);
        ordered.addAll(middle);
        ordered.addAll(bottom);

        if (definition.maxEntries() >= 0 && ordered.size() > definition.maxEntries()) {
            ordered = new ArrayList<>(ordered.subList(0, definition.maxEntries()));
        }
        // 记录最近一次本服排序结果（含 maxEntries 截断后），供 PAPI rank/count 使用
        List<UUID> uuids = new ArrayList<>(ordered.size());
        for (Player p : ordered) {
            uuids.add(p.getUniqueId());
        }
        lastSortedByDef.put(definition.id(), List.copyOf(uuids));
        return ordered;
    }

    // ===== Style / Privacy / Debug 公开访问器（供 PAPI 扩展与命令使用） =====

    /** 记录玩家最近一次参与 PVP 的时间戳（毫秒），由 {@link xuanmo.arcartxsuite.tab.TabModule} 注册的监听器写入。 */
    private final Map<UUID, Long> lastPvpAtByUuid = new LinkedHashMap<>();

    /** 由 TabModule 的 EntityDamageByEntityEvent 监听器调用。 */
    public void recordPvpEvent(UUID attackerId, UUID victimId) {
        long now = System.currentTimeMillis();
        if (attackerId != null) {
            lastPvpAtByUuid.put(attackerId, now);
        }
        if (victimId != null) {
            lastPvpAtByUuid.put(victimId, now);
        }
    }

    /** 是否处于 PVP 高亮窗口内。 */
    public boolean isPvpActive(Player player) {
        if (player == null) {
            return false;
        }
        TabStyleConfiguration style = configuration.style();
        if (!style.pvpEnabled()) {
            return false;
        }
        Long ts = lastPvpAtByUuid.get(player.getUniqueId());
        if (ts == null) {
            return false;
        }
        return System.currentTimeMillis() - ts <= style.pvpWindowMs();
    }

    /** 当前 ping（ms），不可解析返回 0。 */
    public int pingOf(Player player) {
        return player == null ? 0 : Math.max(0, player.getPing());
    }

    /**
     * 收集 viewer 在指定 definition 的调试快照：sortKeys 值、groupKey、pinned 状态、view、page、是否可见。
     * 仅供 {@code /axstab debug <player>} 与日志输出使用。
     */
    public Map<String, Object> debugSnapshot(Player viewer, String definitionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (viewer == null) {
            result.put("error", "viewer-null");
            return result;
        }
        TabDefinition definition = definitionsById.get(definitionId);
        if (definition == null) {
            result.put("error", "definition-not-found");
            return result;
        }
        result.put("definition", definitionId);
        result.put("view", currentView(viewer));
        result.put("page", currentPage(viewer, definitionId));
        result.put("sort-values-numeric", computeSortValuesPerKey(viewer, definition));
        result.put("sort-values-string", computeSortStringValuesPerKey(viewer, definition));
        result.put("group-key", computeGroupKey(viewer, definition));
        result.put("pinned-top", matchesAnyRule(viewer, definition.pinnedTop()));
        result.put("pinned-bottom", matchesAnyRule(viewer, definition.pinnedBottom()));
        result.put("vanished", isVanished(viewer));
        result.put("pvp-active", isPvpActive(viewer));
        result.put("ping", pingOf(viewer));
        result.put("rank", rankOf(viewer.getUniqueId(), definitionId));
        result.put("local-visible-count", localVisibleCount(definitionId));
        result.put("total-visible-count", totalVisibleCount(definitionId));
        return result;
    }

    /** PAPI: 本服在指定 definition 下当前可见的玩家数（已应用 filters / pinned / maxEntries）。 */
    public int localVisibleCount(String definitionId) {
        List<UUID> uuids = lastSortedByDef.get(definitionId);
        return uuids == null ? 0 : uuids.size();
    }

    /** PAPI: 本服 + 跨服节点合计的玩家数。 */
    public int totalVisibleCount(String definitionId) {
        int total = localVisibleCount(definitionId);
        for (Map<String, List<TabRemoteEntry>> nodeSnapshots : remoteSnapshots.values()) {
            List<TabRemoteEntry> entries = nodeSnapshots.get(definitionId);
            if (entries != null) {
                total += entries.size();
            }
        }
        return total;
    }

    /** PAPI: 玩家在指定 definition 排序中的位次（1 起；不可见返回 0）。 */
    public int rankOf(UUID playerId, String definitionId) {
        List<UUID> uuids = lastSortedByDef.get(definitionId);
        if (uuids == null || playerId == null) {
            return 0;
        }
        int index = uuids.indexOf(playerId);
        return index < 0 ? 0 : index + 1;
    }

    /** PAPI 列出全部 definition id（按配置顺序）。 */
    public List<String> definitionIds() {
        return List.copyOf(definitionsById.keySet());
    }

    private List<Player> applyFilters(List<Player> onlinePlayers, TabDefinition definition) {
        boolean hasInclude = !definition.includeFilters().isEmpty();
        boolean hasExclude = !definition.excludeFilters().isEmpty();
        boolean hideVanished = definition.hideVanished();
        if (!hasInclude && !hasExclude && !hideVanished) {
            return onlinePlayers;
        }
        List<Player> result = new ArrayList<>(onlinePlayers.size());
        for (Player player : onlinePlayers) {
            if (hasInclude && !matchesAnyRule(player, definition.includeFilters())) {
                continue;
            }
            if (hasExclude && matchesAnyRule(player, definition.excludeFilters())) {
                continue;
            }
            if (hideVanished && isVanished(player)) {
                continue;
            }
            result.add(player);
        }
        return result;
    }

    public boolean isVanished(Player player) {
        for (org.bukkit.metadata.MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAnyRule(Player player, List<TabFilterRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        for (TabFilterRule rule : rules) {
            if (matchesRule(player, rule)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesRule(Player player, TabFilterRule rule) {
        boolean matched;
        if (rule.isPapi()) {
            String resolved = resolvePlaceholders(player, rule.papi());
            String expected = rule.equalsValue();
            if (expected == null || expected.isBlank()) {
                // 未指定 equals：判断 PAPI 渲染结果非空且不等于原表达式（即被实际渲染了）
                matched = resolved != null
                    && !resolved.isBlank()
                    && !resolved.equals(rule.papi());
            } else {
                matched = resolved != null && resolved.trim().equalsIgnoreCase(expected.trim());
            }
        } else if (rule.isPermission()) {
            matched = player.hasPermission(rule.permission());
        } else {
            matched = false;
        }
        return rule.invert() != matched;
    }

    private Comparator<Player> composedComparator(List<TabSortKey> sortKeys, TabDefinition definition) {
        Comparator<Player> composed = null;
        for (TabSortKey key : sortKeys) {
            Comparator<Player> step = comparatorForKey(key, definition);
            if (key.descending()) {
                step = step.reversed();
            }
            composed = composed == null ? step : composed.thenComparing(step);
        }
        if (composed == null) {
            composed = Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER);
        }
        return composed.thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER);
    }

    private Comparator<Player> comparatorForKey(TabSortKey key, TabDefinition definition) {
        return switch (key.mode()) {
            case NAME -> Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER);
            case PREM -> Comparator.<Player>comparingInt(
                    player -> resolvePremIndex(player, key.premGroups())
                )
                .thenComparing(
                    player -> resolvePremGroup(player, key.premGroups()),
                    String.CASE_INSENSITIVE_ORDER
                );
            case PAPI -> key.papiNumeric()
                ? Comparator.comparingDouble(player -> resolvePapiNumericValue(player, key.papiKey()))
                : Comparator.comparing(
                    player -> resolvePapiStringValue(player, key.papiKey()),
                    String.CASE_INSENSITIVE_ORDER
                );
        };
    }

    private Object renderNode(Object node, Player target) {
        if (node == null) {
            return "";
        }

        if (node instanceof String stringValue) {
            return renderString(stringValue, target);
        }

        if (node instanceof List<?> listValue) {
            List<Object> rendered = new ArrayList<>(listValue.size());
            for (Object entry : listValue) {
                rendered.add(renderNode(entry, target));
            }
            return rendered;
        }

        if (node instanceof Map<?, ?> mapValue) {
            Map<String, Object> rendered = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                Object rawKey = entry.getKey();
                String key = rawKey instanceof String stringKey
                    ? renderString(stringKey, target)
                    : String.valueOf(rawKey);
                rendered.put(key, renderNode(entry.getValue(), target));
            }
            return rendered;
        }

        return node;
    }

    private String renderString(String template, Player target) {
        String rendered = template;
        for (Map.Entry<String, String> entry : buildValues(target).entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        Map<String, String> preservedTokens = new LinkedHashMap<>();
        rendered = preserveArcartXTokens(rendered, preservedTokens);
        rendered = resolvePlaceholders(target, rendered);
        rendered = restorePreservedTokens(rendered, preservedTokens);
        return rendered.replace("\\n", "\n");
    }

    private static String preserveArcartXTokens(String input, Map<String, String> preservedTokens) {
        Matcher matcher = ARCARTX_ICON_TOKEN_PATTERN.matcher(input);
        StringBuilder buffer = new StringBuilder();
        int index = 0;
        while (matcher.find()) {
            String marker = "__AX_TAB_ICON_" + index++ + "__";
            preservedTokens.put(marker, matcher.group());
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(marker));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String restorePreservedTokens(String input, Map<String, String> preservedTokens) {
        String restored = input;
        for (Map.Entry<String, String> entry : preservedTokens.entrySet()) {
            restored = restored.replace(entry.getKey(), entry.getValue());
        }
        return restored;
    }

    private Map<String, String> buildValues(Player target) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player_name", target.getName());
        values.put("player_display_name", nullToEmpty(target.getDisplayName()));
        values.put("player_uuid", target.getUniqueId().toString());
        values.put("player_world", target.getWorld().getName());
        values.put("player_x", String.valueOf(target.getLocation().getBlockX()));
        values.put("player_y", String.valueOf(target.getLocation().getBlockY()));
        values.put("player_z", String.valueOf(target.getLocation().getBlockZ()));
        values.put("player_health", formatNumber(target.getHealth()));
        values.put("player_max_health", formatNumber(resolveMaxHealth(target)));
        values.put("player_ping", Integer.toString(resolvePingValue(target)));
        values.put("player_group", resolvePlayerGroup(target));
        return values;
    }

    private String resolvePremGroup(Player player, TabDefinition definition) {
        return resolvePremGroup(player, definition.primarySortKey().premGroups());
    }

    private String resolvePremGroup(Player player, List<String> premGroups) {
        String playerGroup = resolvePlayerGroup(player).toLowerCase(Locale.ROOT);
        if (premGroups.contains(playerGroup)) {
            return playerGroup;
        }
        return "default";
    }

    private int resolvePremIndex(Player player, TabDefinition definition) {
        return resolvePremIndex(player, definition.primarySortKey().premGroups());
    }

    private int resolvePremIndex(Player player, List<String> premGroups) {
        String group = resolvePremGroup(player, premGroups);
        int index = premGroups.indexOf(group);
        if (index >= 0) {
            return index;
        }

        int defaultIndex = premGroups.indexOf("default");
        return defaultIndex >= 0 ? defaultIndex : premGroups.size();
    }

    private String resolvePlayerGroup(Player player) {
        String group = resolveVaultPrimaryGroup(player);
        if (group != null) {
            return group;
        }
        group = resolveLuckPermsPrimaryGroup(player);
        if (group != null) {
            return group;
        }
        Team team = player.getScoreboard().getEntryTeam(player.getName());
        if (team != null) {
            String teamName = team.getName();
            if (teamName != null && !teamName.isBlank()) {
                return teamName.toLowerCase(Locale.ROOT);
            }
        }
        return "default";
    }

    private static String resolveVaultPrimaryGroup(Player player) {
        try {
            Class<?> permissionClass = Class.forName("net.milkbowl.vault.permission.Permission");
            org.bukkit.plugin.RegisteredServiceProvider<?> provider =
                Bukkit.getServicesManager().getRegistration(permissionClass);
            if (provider == null) {
                return null;
            }
            Object permission = provider.getProvider();
            java.lang.reflect.Method getPrimaryGroup = permission.getClass()
                .getMethod("getPrimaryGroup", org.bukkit.World.class, String.class);
            String group = (String) getPrimaryGroup.invoke(permission, player.getWorld(), player.getName());
            if (group != null && !group.isBlank()) {
                return group.toLowerCase(Locale.ROOT);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static String resolveLuckPermsPrimaryGroup(Player player) {
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            java.lang.reflect.Method get = providerClass.getMethod("get");
            Object api = get.invoke(null);
            java.lang.reflect.Method getUserManager = api.getClass().getMethod("getUserManager");
            Object userManager = getUserManager.invoke(api);
            java.lang.reflect.Method getUser = userManager.getClass().getMethod("getUser", UUID.class);
            Object user = getUser.invoke(userManager, player.getUniqueId());
            if (user != null) {
                java.lang.reflect.Method getPrimaryGroup = user.getClass().getMethod("getPrimaryGroup");
                String group = (String) getPrimaryGroup.invoke(user);
                if (group != null && !group.isBlank()) {
                    return group.toLowerCase(Locale.ROOT);
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private String resolvePapiStringValue(Player player, String placeholder) {
        if (placeholder == null || placeholder.isBlank()) {
            return player.getName();
        }
        String value = resolvePlaceholders(player, placeholder);
        if (value == null || value.isBlank() || value.equals(placeholder)) {
            return "";
        }
        return value.trim();
    }

    private double resolvePapiNumericValue(Player player, String placeholder) {
        String value = resolvePapiStringValue(player, placeholder);
        if (value.isBlank()) {
            return 0.0D;
        }
        try {
            return Double.parseDouble(value.replace(",", ""));
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    private static int resolvePingValue(Player target) {
        return Math.max(0, target.getPing());
    }

    private static double resolveMaxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attribute != null ? attribute.getValue() : 20.0D;
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
