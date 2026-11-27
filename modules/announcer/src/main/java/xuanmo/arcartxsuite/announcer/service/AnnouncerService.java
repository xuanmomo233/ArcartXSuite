package xuanmo.arcartxsuite.announcer.service;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.announcer.config.AnnouncerEntry;
import xuanmo.arcartxsuite.announcer.config.AnnouncerModuleConfiguration;
import xuanmo.arcartxsuite.announcer.transport.AnnouncerEnvelope;
import xuanmo.arcartxsuite.announcer.transport.AnnouncerEnvelopeCodec;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.capability.QQBotBroadcastable;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;

public final class AnnouncerService implements Listener {

    public static final String CLICK_PACKET_ID = "AXS_announcer_click";

    private static final int DEDUPE_MAX_SIZE = 128;
    private static final int MAX_PENDING_BROADCASTS = 128;
    private static final int DISPLAY_PLAYERS_PER_TICK = 32;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final AnnouncerModuleConfiguration configuration;
    private final List<AnnouncerEntry> activeEntries;
    private final PacketBridgeAPI bridge;
    private final java.util.List<String> uiIds;
    private final CrossServerAPI crossServer;
    private final PlaceholderResolverAPI placeholderResolver;

    private volatile CrossServerChannel crossServerChannel;
    private final Set<UUID> initializedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> openedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, DisplayClick> displayClicks = new HashMap<>();
    private final Map<UUID, PendingDisplay> pendingDisplays = new LinkedHashMap<>();
    private long revisionSequence;
    private final Set<String> recentDedupeKeys = Collections.newSetFromMap(
        new LinkedHashMap<String, Boolean>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > DEDUPE_MAX_SIZE;
            }
        }
    );

    private volatile java.util.function.Supplier<QQBotBroadcastable> qqBotProvider;

    private BukkitTask broadcastTask;
    private BukkitTask displayTask;
    private int currentEntryIndex;
    private long nextBroadcastAtMs;
    private AnnouncerDisplay currentDisplay;
    private DisplayRender displayRender;
    private AnnouncerEntry currentEntry;
    private volatile boolean closed;

    /** 手动广播队列，在当前广播结束后立即播报，不受冷却限制 */
    private final Queue<String> manualBroadcastQueue = new ArrayBlockingQueue<>(MAX_PENDING_BROADCASTS);
    private final Queue<ImmediateBroadcast> immediateBroadcasts = new ArrayDeque<>();
    private final Object immediateLock = new Object();
    private BukkitTask immediateTask;
    private final AtomicLong droppedManualBroadcasts = new AtomicLong();

    public AnnouncerService(
        JavaPlugin plugin,
        Logger logger,
        AnnouncerModuleConfiguration configuration,
        PacketBridgeAPI bridge,
        java.util.List<String> uiIds,
        CrossServerAPI crossServer,
        PlaceholderResolverAPI placeholderResolver
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.configuration = configuration;
        this.activeEntries = configuration.activeEntries();
        this.bridge = bridge;
        this.uiIds = uiIds;
        this.crossServer = crossServer;
        this.placeholderResolver = placeholderResolver;
        this.currentDisplay = AnnouncerDisplay.hidden(nextRevision());
    }

    public void setQQBotProvider(java.util.function.Supplier<QQBotBroadcastable> provider) {
        this.qqBotProvider = provider;
    }

    public void start() {
        if (BuildEdition.CROSS_SERVER_INCLUDED) {
            crossServerChannel = crossServer.openChannel(
                "announcer",
                configuration.crossServer(),
                delivery -> handleRemotePayload(delivery.payload())
            );
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        broadcastTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::tickBroadcast,
            1L,
            configuration.checkIntervalTicks()
        );
    }

    public void shutdown() {
        synchronized (immediateLock) {
            closed = true;
            if (immediateTask != null) immediateTask.cancel();
            immediateTask = null;
            immediateBroadcasts.clear();
        }
        if (BuildEdition.CROSS_SERVER_INCLUDED && crossServerChannel != null) {
            crossServerChannel.close();
            crossServerChannel = null;
        }
        if (broadcastTask != null) {
            broadcastTask.cancel();
            broadcastTask = null;
        }
        if (displayTask != null) {
            displayTask.cancel();
            displayTask = null;
        }
        initializedPlayers.clear();
        openedPlayers.clear();
        displayClicks.clear();
        displayRender = null;
        pendingDisplays.clear();
        manualBroadcastQueue.clear();
        HandlerList.unregisterAll(this);
    }

    public void markClientInitialized(Player player) {
        if (closed || player == null) {
            return;
        }
        initializedPlayers.add(player.getUniqueId());
        // announcer HUD 使用 defaultOpen: true，客户端初始化时已自动打开。
        // 不调用 ensureUiOpen / openUiAll，避免二次触发 load action 重置变量。
        openedPlayers.add(player.getUniqueId());
        sendDisplay(player, currentDisplay, "client-init");
    }

    /**
     * 模块 reload 后同步所有在线玩家。
     * <p>
     * reload 期间 UI 未注销（客户端仍持有已打开的 HUD），因此不调用 openUiAll，
     * 直接将所有在线玩家标记为已初始化+已打开，然后立即广播当前内容。
     */
    public void syncAfterReload() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            initializedPlayers.add(player.getUniqueId());
            openedPlayers.add(player.getUniqueId());
        }
        resetPlayback();
        broadcastNextDisplay(System.currentTimeMillis(), "reload");
    }


    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (player == null || packetId == null || !CLICK_PACKET_ID.equalsIgnoreCase(packetId)) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        DisplayClick click = displayClicks.get(playerId);
        AnnouncerDisplay display = currentDisplay;
        if (closed || !display.clickable() || click == null || click.player() != player || click.used()
            || !click.revision().equals(display.revision())
            || data == null || data.size() != 1 || !click.handle().equals(data.get(0))
            || !player.isOnline() || Bukkit.getPlayer(playerId) != player
            || !click.matchesAdmission(AuthAccess.snapshot(playerId))) {
            return true;
        }
        AnnouncerEntry entry = currentEntry;
        if (entry == null || entry.clickCommand().isBlank()) {
            return true;
        }

        displayClicks.put(playerId, click.consumed());
        String playerName = xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(player.getName());
        String command = entry.clickCommand().replace("{player}", playerName).replace("<player>", playerName);
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        boolean dispatched = Bukkit.dispatchCommand(console, command);
        if (configuration.debug()) {
            this.logger.info(
                "ArcartXAnnouncer 点击回包 -> player="
                    + player.getName()
                    + " | entry="
                    + entry.id()
                    + " | command="
                    + command
                    + " | dispatched="
                    + dispatched
            );
        }
        return true;
    }

    public int activeEntryCount() {
        return activeEntries.size();
    }

    public int initializedPlayerCount() {
        return initializedPlayers.size();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        initializedPlayers.remove(playerId);
        openedPlayers.remove(playerId);
        displayClicks.remove(playerId);
        pendingDisplays.remove(playerId);
    }

    private void tickBroadcast() {
        long now = System.currentTimeMillis();

        if (now >= nextBroadcastAtMs || (nextBroadcastAtMs == Long.MAX_VALUE && !manualBroadcastQueue.isEmpty())) {
            // 优先消费手动广播队列
            String manualText = manualBroadcastQueue.poll();
            if (manualText != null) {
                broadcastManualDisplay(manualText);
            } else {
                broadcastNextDisplay(now, "tick");
            }
        }
    }

    private void resetPlayback() {
        currentEntryIndex = 0;
        nextBroadcastAtMs = 0L;
        currentDisplay = AnnouncerDisplay.hidden(nextRevision());
        currentEntry = null;
    }

    /**
     * 将一条手动广播加入队列。
     * 当当前广播展示时间到期后立即播报，不受广播切换冷却限制。
     *
     * @param text 广播文本，支持 PlaceholderAPI 变量
     */
    public void enqueueManualBroadcast(String text) {
        enqueueManualBroadcast(text, false);
    }

    /**
     * 将一条手动广播加入队列。
     *
     * @param text    广播文本
     * @param forward 是否跨服转发
     */
    public boolean enqueueManualBroadcast(String text, boolean forward) {
        if (closed || text == null || text.isBlank()) return false;
        if (!manualBroadcastQueue.offer(text)) {
            droppedManualBroadcasts.incrementAndGet();
            return false;
        }
        if (BuildEdition.CROSS_SERVER_INCLUDED && forward) forwardCrossServer(text, false);
        return true;
    }

    /**
     * 将一条手动广播加入队列并立即播报（强制打断当前展示）。
     *
     * @param text 广播文本
     */
    public void broadcastNow(String text) {
        broadcastNow(text, false);
    }

    /**
     * 将一条手动广播加入队列并立即播报（强制打断当前展示）。
     *
     * @param text    广播文本
     * @param forward 是否跨服转发
     */
    public void broadcastNow(String text, boolean forward) {
        if (closed || text == null || text.isBlank()) return;
        if (!Bukkit.isPrimaryThread()) {
            enqueueImmediate(new ImmediateBroadcast(text, forward));
            return;
        }
        broadcastImmediately(new ImmediateBroadcast(text, forward));
    }

    private void broadcastImmediately(ImmediateBroadcast broadcast) {
        String text = broadcast.text();
        broadcastManualDisplay(text);
        if (BuildEdition.CROSS_SERVER_INCLUDED && broadcast.forward()) {
            forwardCrossServer(text, true);
        }
    }

    public int pendingManualBroadcasts() {
        synchronized (immediateLock) {
            return manualBroadcastQueue.size() + immediateBroadcasts.size();
        }
    }

    public long droppedManualBroadcasts() {
        return droppedManualBroadcasts.get();
    }

    private void broadcastManualDisplay(String text) {
        AnnouncerDisplay display = AnnouncerDisplay.visible(
            "manual", text, false, nextRevision()
        );
        currentEntry = null;
        currentDisplay = display;
        // 手动广播后使用普通间隔时间作为展示时长，不走冷却
        nextBroadcastAtMs = System.currentTimeMillis() + configuration.betweenEntryIntervalMs();
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendDisplay(player, display, "manual");
        }
        forwardToQQBot(text);
    }

    // ─── 跨服传输 ──────────────────────────────────────────────

    public boolean crossServerActive() {
        return BuildEdition.CROSS_SERVER_INCLUDED && crossServerChannel != null && crossServerChannel.isActive();
    }

    private void handleRemotePayload(String payload) {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return;
        if (payload == null || payload.isBlank()) {
            return;
        }
        try {
            handleRemoteEnvelope(AnnouncerEnvelopeCodec.decode(payload));
        } catch (Exception exception) {
            this.logger.warning("Announcer 跨服消息解码失败: " + exception.getMessage());
        }
    }

    /**
     * 处理从其他子服收到的公告信封。
     * 在主线程中调度本地展示，跳过自身节点和重复消息。
     */
    public void handleRemoteEnvelope(AnnouncerEnvelope envelope) {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return;
        if (closed || envelope == null || envelope.text().isBlank()) return;
        if (crossServer.nodeId().equals(envelope.originNode())) return;
        String key = envelope.dedupeKey();
        synchronized (recentDedupeKeys) {
            if (recentDedupeKeys.contains(key)) return;
            boolean accepted = envelope.immediate()
                ? enqueueImmediate(new ImmediateBroadcast(envelope.text(), false))
                : enqueueManualBroadcast(envelope.text(), false);
            if (accepted) recentDedupeKeys.add(key);
        }
    }

    private boolean enqueueImmediate(ImmediateBroadcast broadcast) {
        synchronized (immediateLock) {
            if (closed) return false;
            if (immediateBroadcasts.size() >= MAX_PENDING_BROADCASTS) {
                droppedManualBroadcasts.incrementAndGet();
                return false;
            }
            immediateBroadcasts.add(broadcast);
            if (immediateTask == null) scheduleImmediate();
            return true;
        }
    }

    private void scheduleImmediate() {
        immediateTask = Bukkit.getScheduler().runTask(plugin, this::drainImmediate);
    }

    private void drainImmediate() {
        ImmediateBroadcast broadcast;
        synchronized (immediateLock) {
            if (closed) return;
            broadcast = immediateBroadcasts.poll();
        }
        try {
            if (broadcast != null) broadcastImmediately(broadcast);
        } finally {
            synchronized (immediateLock) {
                immediateTask = null;
                if (!closed && !immediateBroadcasts.isEmpty()) scheduleImmediate();
            }
        }
    }

    private void forwardCrossServer(String text, boolean immediate) {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return;
        CrossServerChannel channel = crossServerChannel;
        if (channel == null || !channel.isActive()) return;
        AnnouncerEnvelope envelope = new AnnouncerEnvelope(
            UUID.randomUUID().toString(), crossServer.nodeId(), text, immediate
        );
        channel.publish(AnnouncerEnvelopeCodec.encode(envelope));
    }

    private void broadcastNextDisplay(long now, String reason) {
        AnnouncerDisplay display = nextDisplay(now);
        currentDisplay = display;
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendDisplay(player, display, reason);
        }
        if (display.isShow() && configuration.forwardToQQ()) {
            forwardToQQBot(display.text());
        }
    }

    private void forwardToQQBot(String text) {
        if (text == null || text.isBlank() || qqBotProvider == null) return;
        QQBotBroadcastable qqBot = qqBotProvider.get();
        if (qqBot == null) return;
        // 去除颜色代码后发送到 QQ 群
        String clean = org.bukkit.ChatColor.stripColor(
            org.bukkit.ChatColor.translateAlternateColorCodes('&', text));
        if (clean != null && !clean.isBlank()) {
            qqBot.sendToAllGroups("[公告] " + clean);
        }
    }

    private AnnouncerDisplay nextDisplay(long now) {
        if (!configuration.autoPlay() || activeEntries.isEmpty()) {
            currentEntry = null;
            nextBroadcastAtMs = Long.MAX_VALUE;
            return AnnouncerDisplay.hidden(nextRevision());
        }

        int entryIndex = Math.max(0, Math.min(currentEntryIndex, activeEntries.size() - 1));
        AnnouncerEntry entry = activeEntries.get(entryIndex);
        currentEntry = entry;
        boolean lastEntry = entryIndex >= activeEntries.size() - 1;
        long nextDelayMs = nextDelayMs(configuration, lastEntry);
        currentEntryIndex = nextEntryIndex(entryIndex, activeEntries.size());
        nextBroadcastAtMs = now + nextDelayMs;
        return AnnouncerDisplay.visible(
            entry.id(),
            entry.text(),
            !entry.clickCommand().isBlank(),
            nextRevision()
        );
    }

    private boolean ensureUiOpen(Player player, UUID playerId) {
        if (!bridge.openUiAll(player, uiIds)) {
            return false;
        }
        openedPlayers.add(playerId);
        return true;
    }

    private void sendDisplay(Player player, AnnouncerDisplay display, String reason) {
        if (closed || player == null || !player.isOnline()) return;
        // Updating an existing entry preserves its place in the queue when a newer announcement arrives.
        pendingDisplays.put(player.getUniqueId(), new PendingDisplay(player, display, reason));
        scheduleDisplayPackets();
    }

    private void scheduleDisplayPackets() {
        if (displayTask == null) displayTask = Bukkit.getScheduler().runTask(plugin, this::flushDisplayPackets);
    }

    private void flushDisplayPackets() {
        try {
            if (closed || !bridge.isAvailable()) {
                pendingDisplays.clear();
                return;
            }
            if (pendingDisplays.isEmpty()) return;
            AnnouncerDisplay display = currentDisplay;
            DisplayRender render = renderFor(display);
            boolean expandPlaceholders = false;
            try {
                expandPlaceholders = render.hasPlaceholders() && placeholderResolver != null && placeholderResolver.available();
            } catch (RuntimeException | LinkageError failure) {
                logger.fine("无法检查公告占位符桥接，使用原文: " + failure.getMessage());
            }
            for (int processed = 0; processed < DISPLAY_PLAYERS_PER_TICK
                && !closed && currentDisplay == display && !pendingDisplays.isEmpty(); processed++) {
                var waiting = pendingDisplays.values().iterator();
                var pending = waiting.next();
                waiting.remove();
                try {
                    deliverDisplay(pending, render, expandPlaceholders);
                } catch (RuntimeException | LinkageError failure) {
                    logger.fine("公告显示同步失败: " + failure.getMessage());
                }
            }
        } finally {
            displayTask = null;
            if (!closed && !pendingDisplays.isEmpty()) scheduleDisplayPackets();
        }
    }

    private DisplayRender renderFor(AnnouncerDisplay display) {
        if (displayRender == null || displayRender.display() != display) {
            int width = estimateTextWidth(display.text(), configuration.textWidthFontSize());
            boolean hasPlaceholders = display.isShow() && display.text().indexOf('%') >= 0;
            displayRender = new DisplayRender(display, hasPlaceholders, width, display.toPayload(display.text(), width, display.id(), false));
        }
        return displayRender;
    }

    private boolean canReceive(Player player, UUID playerId) {
        return player.isOnline() && Bukkit.getPlayer(playerId) == player && AuthAccess.admitted(playerId);
    }

    private void deliverDisplay(PendingDisplay pending, DisplayRender render, boolean expandPlaceholders) {
        AnnouncerDisplay display = render.display();
        if (pending.display() != display) return;
        Player player = pending.player();
        UUID playerId = player.getUniqueId();
        if (!canReceive(player, playerId)) return;
        if (!openedPlayers.contains(playerId) && (!ensureUiOpen(player, playerId) || !canReceive(player, playerId))) return;
        if (closed || currentDisplay != display) return;

        String text = expandPlaceholders ? placeholderResolver.applyPlaceholders(player, display.text()) : display.text();
        if (closed || currentDisplay != display
            || (expandPlaceholders && !canReceive(player, playerId))) return;
        boolean plainText = display.text().equals(text);
        DisplayClick click = display.clickable() ? displayClicks.get(playerId) : null;
        if (display.clickable()) {
            AuthSnapshot admission = AuthAccess.snapshot(playerId);
            if (!admission.admitted()) return;
            if (click == null || click.player() != player || !display.revision().equals(click.revision())
                || (!click.used() && !click.matchesAdmission(admission))) {
                click = new DisplayClick(player, display.revision(), UUID.randomUUID().toString(), false,
                    admission.connectionId(), admission.generation());
            }
        }
        boolean clickable = click != null && !click.used();
        Map<String, Object> payload = !clickable && plainText ? render.plainPayload()
            : display.toPayload(text, plainText ? render.plainWidth() : estimateTextWidth(text, configuration.textWidthFontSize()),
                clickable ? click.handle() : display.id(), clickable);
        boolean packetSent = bridge.sendPacketToAll(player, uiIds, "display", payload);
        if (!closed && currentDisplay == display) {
            if (click == null) displayClicks.remove(playerId);
            else if (packetSent) displayClicks.put(playerId, click);
        }
        logDisplaySync(player, pending.reason(), packetSent, display);
    }

    private void logDisplaySync(
        Player player,
        String reason,
        boolean packetSent,
        AnnouncerDisplay display
    ) {
        if (configuration.debug()) {
            this.logger.info(
                "ArcartXAnnouncer 播放同步 -> player="
                    + player.getName()
                    + " | reason="
                    + reason
                    + " | packet="
                    + packetSent
                    + " | entry="
                    + display.id()
                    + " | revision="
                    + display.revision()
            );
        }
    }

    private String nextRevision() {
        return System.currentTimeMillis() + "-" + ++revisionSequence;
    }

    static long nextDelayMs(AnnouncerModuleConfiguration configuration, boolean lastEntry) {
        long configuredDelay = lastEntry ? configuration.cooldownMs() : configuration.betweenEntryIntervalMs();
        return Math.max(1000L, configuredDelay);
    }

    static int nextEntryIndex(int currentEntryIndex, int entryCount) {
        if (entryCount <= 0 || currentEntryIndex >= entryCount - 1) {
            return 0;
        }
        return currentEntryIndex + 1;
    }

    private record DisplayClick(Player player, String revision, String handle, boolean used, UUID connectionId,
                                long admissionGeneration) {
        boolean matchesAdmission(AuthSnapshot admission) {
            return admission.admitted() && admission.generation() == admissionGeneration
                && java.util.Objects.equals(connectionId, admission.connectionId());
        }

        DisplayClick consumed() {
            return new DisplayClick(player, revision, handle, true, connectionId, admissionGeneration);
        }
    }
    private record DisplayRender(AnnouncerDisplay display, boolean hasPlaceholders, int plainWidth,
                                 Map<String, Object> plainPayload) {}
    private record ImmediateBroadcast(String text, boolean forward) {}
    private record PendingDisplay(Player player, AnnouncerDisplay display, String reason) {}

    record AnnouncerDisplay(
        boolean isShow,
        String id,
        String text,
        boolean clickable,
        String revision
    ) {

        static AnnouncerDisplay visible(
            String id,
            String text,
            boolean clickable,
            String revision
        ) {
            return new AnnouncerDisplay(true, id, text, clickable, revision);
        }

        static AnnouncerDisplay hidden(String revision) {
            return new AnnouncerDisplay(false, "", "", false, revision);
        }

        Map<String, Object> toPayload(String renderedText, int width, String callbackId, boolean canClick) {
            return Map.of("isShow", isShow, "entryId", id, "id", callbackId, "text", renderedText,
                "clickable", canClick, "revision", revision, "textWidth", width);
        }
    }

    /**
     * 根据字符类型估算文字在 UI 自适应坐标中的渲染宽度。
     * <p>
     * CJK 全角字符宽度 ≈ fontSize，Latin 半角字符宽度 ≈ fontSize × 0.55。
     * Minecraft 颜色代码（§x / &x）不占渲染宽度，直接跳过。
     *
     * @param text     要测量的文本
     * @param fontSize UI 中 Text 控件的 fontSize（自适应单位）
     * @return 估算的渲染宽度（自适应单位）
     */
    static int estimateTextWidth(String text, int fontSize) {
        if (text == null || text.isEmpty()) return 0;
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 跳过 Minecraft 颜色代码（§x 或 &x）
            if ((c == '§' || c == '&') && i + 1 < text.length()) {
                i++; // 跳过后面的颜色字符
                continue;
            }
            width += isFullWidth(c) ? fontSize : (int) (fontSize * 0.55);
        }
        return width;
    }

    private static boolean isFullWidth(char c) {
        // U+2E80 及以上基本都是全角：CJK 部首、符号、标点、平假名、片假名、汉字、全角形式等
        return c >= '\u2E80';
    }
}


