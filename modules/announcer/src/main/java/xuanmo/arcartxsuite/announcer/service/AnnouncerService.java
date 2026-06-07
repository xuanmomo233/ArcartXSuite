package xuanmo.arcartxsuite.announcer.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import me.clip.placeholderapi.PlaceholderAPI;
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
import xuanmo.arcartxsuite.api.capability.QQBotBroadcastable;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.bridge.ArcartXClientBridge;
import xuanmo.arcartxsuite.bridge.ArcartXPacketBridge;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;

public final class AnnouncerService implements Listener {

    public static final String CLICK_PACKET_ID = "AXS_announcer_click";
    private static final String TEXT_VARIABLE_NAME = "AXS_announcer_text";
    private static final String ID_VARIABLE_NAME = "AXS_announcer_id";
    private static final String CLICKABLE_VARIABLE_NAME = "AXS_announcer_clickable";
    private static final String REVISION_VARIABLE_NAME = "AXS_announcer_revision";

    private static final int DEDUPE_MAX_SIZE = 128;

    private final JavaPlugin plugin;
    private final AnnouncerModuleConfiguration configuration;
    private final ArcartXPacketBridge bridge;
    private final ArcartXClientBridge clientBridge;
    private final PacketGuardAPI packetGuard;
    private final java.util.List<String> uiIds;
    private final CrossServerAPI crossServer;

    private CrossServerChannel crossServerChannel;
    private final Set<UUID> initializedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> openedPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicLong revisionSequence = new AtomicLong();
    private final Set<String> recentDedupeKeys = Collections.newSetFromMap(
        Collections.synchronizedMap(new LinkedHashMap<String, Boolean>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > DEDUPE_MAX_SIZE;
            }
        })
    );

    private volatile java.util.function.Supplier<QQBotBroadcastable> qqBotProvider;

    private BukkitTask broadcastTask;
    private int currentEntryIndex;
    private long nextBroadcastAtMs;
    private AnnouncerDisplay currentDisplay;

    /** 手动广播队列，在当前广播结束后立即播报，不受冷却限制 */
    private final Queue<String> manualBroadcastQueue = new ConcurrentLinkedQueue<>();

    public AnnouncerService(
        JavaPlugin plugin,
        AnnouncerModuleConfiguration configuration,
        ArcartXPacketBridge bridge,
        ArcartXClientBridge clientBridge,
        PacketGuardAPI packetGuard,
        java.util.List<String> uiIds,
        CrossServerAPI crossServer
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.bridge = bridge;
        this.clientBridge = clientBridge;
        this.packetGuard = packetGuard;
        this.uiIds = uiIds;
        this.crossServer = crossServer;
        this.currentDisplay = AnnouncerDisplay.hidden(nextRevision());
    }

    public void setQQBotProvider(java.util.function.Supplier<QQBotBroadcastable> provider) {
        this.qqBotProvider = provider;
    }

    public void start() {
        crossServerChannel = crossServer.openChannel(
            "announcer",
            configuration.crossServer(),
            delivery -> handleRemotePayload(delivery.payload())
        );
        Bukkit.getPluginManager().registerEvents(this, plugin);
        broadcastTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::tickBroadcast,
            1L,
            configuration.checkIntervalTicks()
        );
    }

    public void shutdown() {
        if (crossServerChannel != null) {
            crossServerChannel.close();
            crossServerChannel = null;
        }
        if (broadcastTask != null) {
            broadcastTask.cancel();
            broadcastTask = null;
        }
        initializedPlayers.clear();
        openedPlayers.clear();
        HandlerList.unregisterAll(this);
    }

    public void markClientInitialized(Player player) {
        if (player == null) {
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
        if (packetGuard != null && !packetGuard.allow(player, "announcer", "click", configuration.debug())) {
            return true;
        }

        AnnouncerEntry entry = data == null || data.isEmpty()
            ? null
            : findEntryById(configuration.activeEntries(), data.get(0));
        if (entry == null || entry.clickCommand().isBlank()) {
            return true;
        }

        String command = entry.clickCommand().replace("<player>", player.getName());
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        boolean dispatched = Bukkit.dispatchCommand(console, command);
        if (configuration.debug()) {
            plugin.getLogger().info(
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
        return configuration.activeEntries().size();
    }

    public int initializedPlayerCount() {
        return initializedPlayers.size();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        initializedPlayers.remove(playerId);
        openedPlayers.remove(playerId);
    }

    private void tickBroadcast() {
        long now = System.currentTimeMillis();

        // 每个检查周期尝试为尚未打开 UI 的已初始化玩家打开 HUD 并同步当前内容
        // 覆盖 reload 后 UI 重新注册的延迟场景
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (initializedPlayers.contains(playerId) && !openedPlayers.contains(playerId)) {
                if (ensureUiOpen(player)) {
                    sendDisplay(player, currentDisplay, "late-open");
                }
            }
        }

        if (now >= nextBroadcastAtMs) {
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
    public void enqueueManualBroadcast(String text, boolean forward) {
        if (text != null && !text.isBlank()) {
            manualBroadcastQueue.offer(text);
            if (forward) {
                forwardCrossServer(text, false);
            }
        }
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
        if (text == null || text.isBlank()) return;
        broadcastManualDisplay(text);
        if (forward) {
            forwardCrossServer(text, true);
        }
    }

    public int pendingManualBroadcasts() {
        return manualBroadcastQueue.size();
    }

    private void broadcastManualDisplay(String text) {
        AnnouncerDisplay display = AnnouncerDisplay.visible(
            "manual", text, false, nextRevision()
        );
        currentDisplay = display;
        // 手动广播后使用普通间隔时间作为展示时长，不走冷却
        nextBroadcastAtMs = System.currentTimeMillis() + configuration.betweenEntryIntervalMs();
        for (Player player : Bukkit.getOnlinePlayers()) {
            ensureUiOpen(player);
            AnnouncerDisplay rendered = renderDisplayFor(player, display);
            sendDisplay(player, rendered, "manual");
        }
        forwardToQQBot(text);
    }

    // ─── 跨服传输 ──────────────────────────────────────────────

    public boolean crossServerActive() {
        return crossServerChannel != null && crossServerChannel.isActive();
    }

    private void handleRemotePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        try {
            handleRemoteEnvelope(AnnouncerEnvelopeCodec.decode(payload));
        } catch (Exception exception) {
            plugin.getLogger().warning("Announcer 跨服消息解码失败: " + exception.getMessage());
        }
    }

    /**
     * 处理从其他子服收到的公告信封。
     * 在主线程中调度本地展示，跳过自身节点和重复消息。
     */
    public void handleRemoteEnvelope(AnnouncerEnvelope envelope) {
        if (envelope == null) return;
        if (crossServer.nodeId().equals(envelope.originNode())) return;
        if (!recentDedupeKeys.add(envelope.dedupeKey())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (envelope.immediate()) {
                broadcastManualDisplay(envelope.text());
            } else {
                enqueueManualBroadcast(envelope.text(), false);
            }
        });
    }

    private void forwardCrossServer(String text, boolean immediate) {
        if (crossServerChannel == null || !crossServerChannel.isActive()) return;
        AnnouncerEnvelope envelope = new AnnouncerEnvelope(
            UUID.randomUUID().toString(), crossServer.nodeId(), text, immediate
        );
        recentDedupeKeys.add(envelope.dedupeKey());
        crossServerChannel.publish(AnnouncerEnvelopeCodec.encode(envelope));
    }

    private void broadcastNextDisplay(long now, String reason) {
        AnnouncerDisplay display = nextDisplay(now);
        currentDisplay = display;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ensureUiOpen(player);
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
        List<AnnouncerEntry> activeEntries = configuration.activeEntries();
        if (!configuration.autoPlay() || activeEntries.isEmpty()) {
            nextBroadcastAtMs = Long.MAX_VALUE;
            return AnnouncerDisplay.hidden(nextRevision());
        }

        int entryIndex = Math.max(0, Math.min(currentEntryIndex, activeEntries.size() - 1));
        AnnouncerEntry entry = activeEntries.get(entryIndex);
        boolean lastEntry = entryIndex >= activeEntries.size() - 1;
        long nextDelayMs = nextDelayMs(configuration, lastEntry);
        currentEntryIndex = nextEntryIndex(entryIndex, activeEntries.size());
        nextBroadcastAtMs = now + nextDelayMs;
        return AnnouncerDisplay.visible(
            entry.id(),
            renderText(null, entry.text(), false),
            !entry.clickCommand().isBlank(),
            nextRevision()
        );
    }

    private boolean ensureUiOpen(Player player) {
        if (player == null || !player.isOnline() || !bridge.isAvailable()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (openedPlayers.contains(playerId)) {
            return true;
        }
        if (!bridge.openUiAll(player, uiIds)) {
            return false;
        }
        openedPlayers.add(playerId);
        return true;
    }

    private void sendDisplay(Player player, AnnouncerDisplay display, String reason) {
        if (player == null || !player.isOnline() || !bridge.isAvailable()) {
            return;
        }
        if (!openedPlayers.contains(player.getUniqueId()) && !ensureUiOpen(player)) {
            return;
        }
        AnnouncerDisplay renderedDisplay = renderDisplayFor(player, display);
        boolean variablesSent = sendDisplayVariables(player, renderedDisplay);
        scheduleDisplayPacket(player, renderedDisplay, reason, variablesSent);
    }

    private void scheduleDisplayPacket(Player player, AnnouncerDisplay display, String reason, boolean variablesSent) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !bridge.isAvailable() || !openedPlayers.contains(player.getUniqueId())) {
                return;
            }
            boolean packetSent = bridge.sendPacketToAll(player, uiIds, "display", display.toPayload(configuration.textWidthFontSize()));
            logDisplaySync(player, reason, variablesSent, packetSent, display);
        });
    }

    private void logDisplaySync(
        Player player,
        String reason,
        boolean variablesSent,
        boolean packetSent,
        AnnouncerDisplay display
    ) {
        if (configuration.debug()) {
            plugin.getLogger().info(
                "ArcartXAnnouncer 播放同步 -> player="
                    + player.getName()
                    + " | reason="
                    + reason
                    + " | variables="
                    + variablesSent
                    + " | packet="
                    + packetSent
                    + " | payload="
                    + display.toPayload(configuration.textWidthFontSize())
            );
        }
    }

    private boolean sendDisplayVariables(Player player, AnnouncerDisplay display) {
        if (clientBridge == null || !clientBridge.isAvailable()) {
            return false;
        }

        boolean textSent = clientBridge.sendServerVariable(player, TEXT_VARIABLE_NAME, display.text());
        boolean idSent = clientBridge.sendServerVariable(player, ID_VARIABLE_NAME, display.id());
        boolean clickableSent = clientBridge.sendServerVariable(player, CLICKABLE_VARIABLE_NAME, display.clickable());
        boolean revisionSent = clientBridge.sendServerVariable(player, REVISION_VARIABLE_NAME, display.revision());
        return textSent && idSent && clickableSent && revisionSent;
    }

    private AnnouncerDisplay renderDisplayFor(Player player, AnnouncerDisplay display) {
        if (!display.isShow() || Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return display;
        }
        return new AnnouncerDisplay(
            true,
            display.id(),
            renderText(player, display.text(), true),
            display.clickable(),
            display.revision()
        );
    }

    private String nextRevision() {
        return System.currentTimeMillis() + "-" + revisionSequence.incrementAndGet();
    }

    static AnnouncerDisplay buildDisplayPayload(
        AnnouncerEntry entry,
        String revision,
        Player player,
        boolean placeholderApiAvailable
    ) {
        return AnnouncerDisplay.visible(
            entry.id(),
            renderText(player, entry.text(), placeholderApiAvailable),
            !entry.clickCommand().isBlank(),
            revision
        );
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

    static AnnouncerEntry findEntryById(List<AnnouncerEntry> activeEntries, String entryId) {
        if (entryId == null || entryId.isBlank()) {
            return null;
        }
        for (AnnouncerEntry entry : activeEntries) {
            if (entry.id().equals(entryId)) {
                return entry;
            }
        }
        return null;
    }

    private static String renderText(Player player, String text, boolean placeholderApiAvailable) {
        String rendered = text == null ? "" : text;
        if (placeholderApiAvailable && player != null) {
            rendered = PlaceholderAPI.setPlaceholders(player, rendered);
        }
        return rendered;
    }

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

        Map<String, Object> toPayload(int fontSize) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("isShow", isShow);
            payload.put("id", id);
            payload.put("text", text);
            payload.put("clickable", clickable);
            payload.put("revision", revision);
            payload.put("textWidth", estimateTextWidth(text, fontSize));
            return payload;
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
