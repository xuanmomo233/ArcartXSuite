package xuanmo.arcartxsuite.conversation.cinematic;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.SoundPlayerBridgeAPI;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;

/**
 * 电影序列回放引擎。
 * <p>
 * 负责按顺序执行序列中的动作，管理玩家状态保存/恢复，
 * 以及 letterbox 黑边 UI 的显示/隐藏。
 */
public final class CinematicService {

    private static final String LETTERBOX_UI_RESOURCE_PATH = "arcartx/ui/conversation_letterbox.yml";
    private static final String LETTERBOX_UI_FILE_PATH = "ui/conversation_letterbox.yml";
    private static final String HANDLER_SYNC = "sync";
    private static final String HANDLER_CLOSE = "close";
    private static final AtomicLong SESSION_SEQUENCE = new AtomicLong(System.currentTimeMillis());

    private final JavaPlugin plugin;
    private final Logger logger;
    private final CinematicConfiguration configuration;
    private final AdyeshachNpcBridgeAPI npcBridge;
    private final PacketBridgeAPI packetBridge;
    private final SoundPlayerBridgeAPI soundPlayer;
    private final SubtitlePlayable subtitlePlayable;
    private final List<String> letterboxUiIds;

    private final CinematicActionContext actionContext;

    private final ConcurrentMap<UUID, CinematicSession> activeSessions = new ConcurrentHashMap<>();
    private volatile boolean debug = false;

    public CinematicService(
        JavaPlugin plugin,
        Logger logger,
        CinematicConfiguration configuration,
        AdyeshachNpcBridgeAPI npcBridge,
        PacketBridgeAPI packetBridge,
        SoundPlayerBridgeAPI soundPlayer,
        SubtitlePlayable subtitlePlayable,
        List<String> letterboxUiIds
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.configuration = configuration;
        this.npcBridge = npcBridge;
        this.packetBridge = packetBridge;
        this.soundPlayer = soundPlayer;
        this.subtitlePlayable = subtitlePlayable;
        this.letterboxUiIds = letterboxUiIds != null ? List.copyOf(letterboxUiIds) : List.of();
        this.actionContext = new CinematicActionContext(npcBridge, packetBridge, soundPlayer, subtitlePlayable);
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean hasSequence(String id) {
        return configuration.getSequence(id) != null;
    }

    public Map<String, CinematicSequence> sequences() {
        return configuration.sequences();
    }

    public boolean isPlaying(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public int activeCount() {
        return activeSessions.size();
    }

    public List<String> sequenceIds() {
        return new ArrayList<>(configuration.sequences().keySet());
    }

    /**
     * 开始播放指定序列。
     *
     * @return true 如果成功开始播放
     */
    public boolean play(Player player, String sequenceId) {
        if (player == null || !player.isOnline()) return false;
        CinematicSequence sequence = configuration.getSequence(sequenceId);
        if (sequence == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (activeSessions.containsKey(playerId)) {
            return false;
        }

        long sessionId = SESSION_SEQUENCE.incrementAndGet();
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        CinematicSession session = new CinematicSession(sessionId, playerId, sequence, snapshot);
        activeSessions.put(playerId, session);

        showLetterbox(player);
        scheduleNextAction(player, session, 0);
        if (debug) {
            logger.info("[Cinematic] 开始播放序列 '" + sequenceId + "' 给 " + player.getName()
                + " | 动作数=" + sequence.actionCount());
        }
        return true;
    }

    /**
     * 强制停止玩家的电影序列并恢复状态。
     */
    public void stop(Player player) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();
        CinematicSession session = activeSessions.remove(playerId);
        if (session == null) return;
        session.cancelTasks();
        restorePlayer(player, session.snapshot());
        hideLetterbox(player);
        if (debug) {
            logger.info("[Cinematic] 停止播放序列给 " + player.getName());
        }
    }

    public void shutdown() {
        for (UUID playerId : new ArrayList<>(activeSessions.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                stop(player);
            } else {
                CinematicSession session = activeSessions.remove(playerId);
                if (session != null) session.cancelTasks();
            }
        }
    }

    public void onPlayerQuit(Player player) {
        stop(player);
    }

    private void scheduleNextAction(Player player, CinematicSession session, int actionIndex) {
        if (!player.isOnline() || session.cancelled()) {
            finishSession(player, session);
            return;
        }
        if (actionIndex >= session.sequence().actionCount()) {
            finishSession(player, session);
            return;
        }

        CinematicAction action = session.sequence().actions().get(actionIndex);
        BukkitTask task = AxsScheduler.runTask(plugin, () -> {
            if (session.cancelled() || !player.isOnline()) {
                finishSession(player, session);
                return;
            }
            try {
                action.execute(player, actionContext);
            } catch (RuntimeException e) {
                logger.warning("[Cinematic] 执行动作失败: type=" + action.type()
                    + " | player=" + player.getName()
                    + " | " + e.getMessage());
            }
            if (debug) {
                logger.info("[Cinematic] 执行动作 " + actionIndex + "/" + session.sequence().actionCount()
                    + " type=" + action.type() + " player=" + player.getName()
                    + " delayAfter=" + action.delayAfterMs() + "ms");
            }

            long delayMs = action.delayAfterMs();
            long delayTicks = Math.max(1L, delayMs / 50L);
            BukkitTask nextTask = AxsScheduler.runTaskLater(plugin, () ->
                scheduleNextAction(player, session, actionIndex + 1), delayTicks);
            session.setPendingTask(nextTask);
        });
        session.setPendingTask(task);
    }

    private void finishSession(Player player, CinematicSession session) {
        if (activeSessions.remove(player.getUniqueId(), session)) {
            restorePlayer(player, session.snapshot());
            hideLetterbox(player);
            if (debug) {
                logger.info("[Cinematic] 序列播放完成: player=" + player.getName());
            }
        }
    }

    private void showLetterbox(Player player) {
        if (packetBridge == null || letterboxUiIds.isEmpty()) return;
        boolean opened = packetBridge.openUiAll(player, letterboxUiIds);
        if (opened) {
            java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("visible", true);
            packetBridge.sendPacketToAll(player, letterboxUiIds, HANDLER_SYNC, payload);
        }
    }

    private void hideLetterbox(Player player) {
        if (packetBridge == null || letterboxUiIds.isEmpty()) return;
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("visible", false);
        packetBridge.sendPacketToAll(player, letterboxUiIds, HANDLER_CLOSE, payload);
        packetBridge.closeUiAll(player, letterboxUiIds);
    }

    private void restorePlayer(Player player, PlayerSnapshot snapshot) {
        try {
            player.setGameMode(snapshot.gameMode());
            player.setWalkSpeed(snapshot.walkSpeed());
            player.setFlySpeed(snapshot.flySpeed());
            player.setAllowFlight(snapshot.allowFlight());
            player.setFlying(snapshot.isFlying());
            if (snapshot.location() != null) {
                player.teleport(snapshot.location());
            }
        } catch (RuntimeException e) {
            logger.warning("[Cinematic] 恢复玩家状态失败: " + e.getMessage());
        }
    }

    // ─── 内部类 ────────────────────────────────────────────

    private static final class CinematicSession {
        private final long sessionId;
        private final UUID playerId;
        private final CinematicSequence sequence;
        private final PlayerSnapshot snapshot;
        private volatile boolean cancelled;
        private volatile BukkitTask pendingTask;

        CinematicSession(long sessionId, UUID playerId, CinematicSequence sequence, PlayerSnapshot snapshot) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.sequence = sequence;
            this.snapshot = snapshot;
        }

        long sessionId() { return sessionId; }
        UUID playerId() { return playerId; }
        CinematicSequence sequence() { return sequence; }
        PlayerSnapshot snapshot() { return snapshot; }
        boolean cancelled() { return cancelled; }

        void setPendingTask(BukkitTask task) {
            BukkitTask old = this.pendingTask;
            this.pendingTask = task;
            if (old != null && !old.isCancelled()) {
                old.cancel();
            }
        }

        void cancelTasks() {
            cancelled = true;
            if (pendingTask != null && !pendingTask.isCancelled()) {
                pendingTask.cancel();
            }
        }
    }

    /**
     * 玩家状态快照，用于序列结束后恢复。
     */
    public static final class PlayerSnapshot {
        private final GameMode gameMode;
        private final float walkSpeed;
        private final float flySpeed;
        private final boolean allowFlight;
        private final boolean isFlying;
        private final Location location;

        private PlayerSnapshot(GameMode gameMode, float walkSpeed, float flySpeed,
                               boolean allowFlight, boolean isFlying, Location location) {
            this.gameMode = gameMode;
            this.walkSpeed = walkSpeed;
            this.flySpeed = flySpeed;
            this.allowFlight = allowFlight;
            this.isFlying = isFlying;
            this.location = location;
        }

        static PlayerSnapshot capture(Player player) {
            return new PlayerSnapshot(
                player.getGameMode(),
                player.getWalkSpeed(),
                player.getFlySpeed(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getLocation().clone()
            );
        }

        public GameMode gameMode() { return gameMode; }
        public float walkSpeed() { return walkSpeed; }
        public float flySpeed() { return flySpeed; }
        public boolean allowFlight() { return allowFlight; }
        public boolean isFlying() { return isFlying; }
        public Location location() { return location; }
    }
}
