package xuanmo.arcartxsuite.conversation.cinematic;

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
        return start(player, sequenceId, false);
    }

    public boolean preview(Player player, String sequenceId) {
        return start(player, sequenceId, true);
    }

    private boolean start(Player player, String sequenceId, boolean rehearsal) {
        if (!Bukkit.isPrimaryThread()) return false;
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
        session.rehearsal = rehearsal;
        session.paused = rehearsal;
        activeSessions.put(playerId, session);

        try {
            showLetterbox(player);
            session.setPendingTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(player, session), 1, 1));
        } catch (RuntimeException exception) {
            fail(player, session, exception);
            return false;
        }
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
        cleanup(player, session);
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

    public String control(Player player, String operation, long first, long second) {
        CinematicSession session = activeSessions.get(player.getUniqueId());
        if (session == null || !session.rehearsal) return "请先使用 preview 打开彩排。";
        long duration = session.sequence().durationTicks();
        if ((operation.equals("seek") || operation.equals("loop")) && (first < 0 || first > duration)) {
            return "时间超出序列范围";
        }
        if (operation.equals("loop") && (second > duration || first >= second)) {
            return "循环终点必须晚于起点，且不能超出序列时长";
        }
        try {
            switch (operation) {
                case "pause" -> session.paused = true;
                case "resume" -> session.paused = false;
                case "seek" -> {
                    resetTo(player, session, first);
                    session.paused = true;
                }
                case "step" -> {
                    var cues = session.sequence().cues();
                    if (session.cursor >= cues.size()) return "没有下一条提示。";
                    long next = cues.get(session.cursor).tick();
                    resetTo(player, session, next);
                    processFrame(player, session);
                    session.position++;
                    session.paused = true;
                }
                case "loop" -> {
                    resetTo(player, session, first);
                    session.loopStart = first;
                    session.loopEnd = second;
                    session.paused = false;
                }
                case "unloop" -> session.loopEnd = -1;
                default -> { }
            }
            return session.sequence().id() + " · " + Math.min(session.position, session.sequence().durationTicks()) * 50
                + "/" + session.sequence().durationTicks() * 50 + " ms · " + (session.paused ? "暂停" : "播放");
        } catch (RuntimeException exception) {
            fail(player, session, exception);
            return "彩排已停止: " + exception.getMessage();
        }
    }

    private void tick(Player player, CinematicSession session) {
        if (activeSessions.get(player.getUniqueId()) != session) return;
        if (!player.isOnline() || player.isDead()) {
            finishSession(player, session);
            return;
        }
        if (session.paused) return;
        try {
            if (session.loopEnd >= 0 && session.position >= session.loopEnd) {
                resetTo(player, session, session.loopStart);
            }
            if (session.position > session.sequence().durationTicks()) {
                if (session.rehearsal) session.paused = true;
                else finishSession(player, session);
                return;
            }
            processFrame(player, session);
            session.position++;
        } catch (RuntimeException exception) {
            fail(player, session, exception);
        }
    }

    private void processFrame(Player player, CinematicSession session) {
        var cues = session.sequence().cues();
        if (session.camera != null) {
            long elapsed = session.position - session.cameraStart;
            if (elapsed % 2 == 0 || elapsed >= session.camera.durationTicks()) {
                sampleCamera(player, session, session.position);
            }
        }
        sampleActors(player, session, session.position, false);
        while (session.cursor < cues.size() && cues.get(session.cursor).tick() <= session.position) {
            if (activeSessions.get(player.getUniqueId()) != session) return;
            var cue = cues.get(session.cursor++);
            applyCue(player, session, cue, false);
        }
    }

    private void applyCue(Player player, CinematicSession session, CinematicSequence.Cue cue, boolean rebuilding) {
        CinematicAction action = cue.action();
        if (action instanceof CinematicActions.CameraPathAction camera) {
            session.camera = camera;
            session.cameraStart = cue.tick();
            if (!rebuilding) sampleCamera(player, session, cue.tick());
        } else if (action instanceof CinematicActorCue actor) {
            String markerId = markerId(session, actor.actor());
            if (actor.placesActor()) session.movements.remove(actor.actor());
            session.actors.add(markerId);
            actor.apply(player, actionContext, markerId, rebuilding);
        } else if (action instanceof CinematicActorMove.Action movement) {
            var move = movement.move;
            session.movements.put(move.actor(), new ActiveMovement(cue.tick(), move));
            if (!rebuilding) move.sample(player, actionContext, markerId(session, move.actor()), 0);
        } else if (isRebuildable(action)) {
            if (action instanceof CinematicActions.TeleportAction teleport) {
                session.camera = null;
                if (rebuilding) {
                    session.rebuildPosition = teleport;
                    return;
                }
            }
            action.execute(player, actionContext);
        } else if (!rebuilding && action instanceof CinematicActions.SoundAction sound) {
            session.sounds.add(sound);
            sound.execute(player, actionContext);
        } else if (!rebuilding && (!session.rehearsal || isRehearsalEffect(action))) {
            action.execute(player, actionContext);
        } else if (!rebuilding && session.rehearsal) {
            player.sendMessage("§7[彩排] 跳过 " + action.type() + " · " + cue.tick() * 50 + " ms");
        }
    }

    private static boolean isRebuildable(CinematicAction action) {
        return action instanceof CinematicActions.TeleportAction || action instanceof CinematicActions.GameModeAction
            || action instanceof CinematicActions.SpeedAction || action instanceof CinematicActions.LockMovementAction;
    }

    private static boolean isRehearsalEffect(CinematicAction action) {
        return action instanceof CinematicActions.TitleAction || action instanceof CinematicActions.ActionBarAction;
    }

    private void sampleCamera(Player player, CinematicSession session, long tick) {
        if (session.camera != null) {
            long elapsed = tick - session.cameraStart;
            session.camera.sample(player, elapsed);
            if (elapsed >= session.camera.durationTicks()) session.camera = null;
        }
    }

    private void resetTo(Player player, CinematicSession session, long tick) {
        player.resetTitle();
        stopSounds(player, session);
        clearActors(player, session);
        restorePlayerSettings(player, session.snapshot());
        session.camera = null;
        session.rebuildPosition = null;
        session.cursor = 0;
        session.position = tick;
        var cues = session.sequence().cues();
        while (session.cursor < cues.size() && cues.get(session.cursor).tick() < tick) {
            applyCue(player, session, cues.get(session.cursor++), true);
        }
        if (session.camera != null) {
            sampleCamera(player, session, tick);
        } else if (session.rebuildPosition != null) {
            session.rebuildPosition.execute(player, actionContext);
        } else if (!player.teleport(session.snapshot().location())) {
            throw new IllegalStateException("无法回到彩排起点");
        }
        session.rebuildPosition = null;
        sampleActors(player, session, tick, true);
    }

    private static String markerId(CinematicSession session, String actor) {
        return "cinematic:" + session.sessionId() + ":" + actor;
    }

    private void sampleActors(Player player, CinematicSession session, long tick, boolean force) {
        var iterator = session.movements.values().iterator();
        while (iterator.hasNext()) {
            var active = iterator.next();
            long elapsed = tick - active.start();
            var move = active.move();
            if (force || elapsed % 2 == 0 || elapsed >= move.durationTicks()) {
                move.sample(player, actionContext, markerId(session, move.actor()), elapsed);
            }
            if (elapsed >= move.durationTicks()) iterator.remove();
        }
    }

    private record ActiveMovement(long start, CinematicActorMove move) {
    }

    private void fail(Player player, CinematicSession session, RuntimeException exception) {
        logger.log(java.util.logging.Level.WARNING, "[Cinematic] " + session.sequence().id() + " @"
            + session.position * 50 + "ms / " + player.getName(), exception);
        finishSession(player, session);
    }

    private void cleanup(Player player, CinematicSession session) {
        session.cancelTasks();
        stopSounds(player, session);
        clearActors(player, session);
        restorePlayer(player, session.snapshot());
        try {
            player.resetTitle();
        } finally {
            hideLetterbox(player);
        }
    }

    private void stopSounds(Player player, CinematicSession session) {
        for (var sound : session.sounds) {
            try {
                sound.stop(player);
            } catch (RuntimeException exception) {
                logger.warning("停止场景音效失败: " + exception.getMessage());
            }
        }
        session.sounds.clear();
    }

    private void clearActors(Player player, CinematicSession session) {
        if (npcBridge != null) {
            for (String markerId : session.actors) {
                try {
                    npcBridge.removePrivateMarker(player, markerId);
                } catch (RuntimeException exception) {
                    logger.warning("清理场景演员失败: " + exception.getMessage());
                }
            }
        }
        session.actors.clear();
        session.movements.clear();
    }

    private void finishSession(Player player, CinematicSession session) {
        if (activeSessions.remove(player.getUniqueId(), session)) {
            cleanup(player, session);
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
            restorePlayerSettings(player, snapshot);
        } catch (RuntimeException exception) {
            logger.warning("[Cinematic] 恢复玩家设置失败: " + exception.getMessage());
        }
        try {
            if (!player.isDead() && !player.teleport(snapshot.location())) {
                logger.warning("[Cinematic] 返回场景起点的传送被取消: " + player.getName());
            }
        } catch (RuntimeException e) {
            logger.warning("[Cinematic] 返回场景起点失败: " + e.getMessage());
        }
    }

    private static void restorePlayerSettings(Player player, PlayerSnapshot snapshot) {
        player.setGameMode(snapshot.gameMode());
        player.setWalkSpeed(snapshot.walkSpeed());
        player.setFlySpeed(snapshot.flySpeed());
        player.setAllowFlight(snapshot.allowFlight());
        player.setFlying(snapshot.isFlying());
    }

    // ─── 内部类 ────────────────────────────────────────────

    private static final class CinematicSession {
        private final long sessionId;
        private final UUID playerId;
        private final CinematicSequence sequence;
        private final PlayerSnapshot snapshot;
        private volatile boolean cancelled;
        private volatile BukkitTask pendingTask;
        private boolean rehearsal;
        private boolean paused;
        private int cursor;
        private long position;
        private long loopStart;
        private long loopEnd = -1;
        private CinematicActions.CameraPathAction camera;
        private CinematicActions.TeleportAction rebuildPosition;
        private long cameraStart;
        private final java.util.Set<String> actors = new java.util.LinkedHashSet<>();
        private final Map<String, ActiveMovement> movements = new java.util.LinkedHashMap<>();
        private final java.util.Set<CinematicActions.SoundAction> sounds = new java.util.LinkedHashSet<>();

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
