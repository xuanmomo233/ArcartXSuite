package xuanmo.arcartxsuite.fishing.minigame;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.random.RandomGenerator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.api.script.AriaBridge;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration.FishingSettings;
import xuanmo.arcartxsuite.fishing.talent.TalentBonuses;
import xuanmo.arcartxsuite.fishing.service.FishingService;
import xuanmo.arcartxsuite.api.scheduler.ModuleCallback;

/**
 * 单场钓鱼小游戏实例。
 * <p>
 * 管理鱼的位置、绿条物理、进度条计算，并通过 PacketBridge 实时同步到客户端。
 */
public final class FishingMinigame {

    public static final String PACKET_ID = "AXS_FISHING";

    private final JavaPlugin plugin;
    private final PacketBridgeAPI packetBridge;
    private final FishingSettings settings;
    private final FishingService service;
    private final IconResolver iconResolver;
    private final FishingSession session;
    private final String uiId;
    private final FishingFight fight;
    private int appliedBarInputSequence;
    private BukkitTask tickTask;
    private final ModuleCallback tickPulse = new ModuleCallback(this::tick);
    private final AtomicBoolean cleanedUp = new AtomicBoolean(false);

    // 结果
    private volatile boolean success = false;
    private volatile boolean finished = false;
    private boolean opened;
    private volatile boolean ready;

    public FishingMinigame(@NotNull JavaPlugin plugin, @NotNull PacketBridgeAPI packetBridge,
                           @NotNull FishingSettings settings, @NotNull FishingService service,
                           @NotNull IconResolver iconResolver,
                           @NotNull FishingSession session, @NotNull String uiId,
                           @NotNull TalentBonuses talentBonuses,
                           double fatigueMultiplier,
                           @Nullable AriaBridge ariaBridge, RandomGenerator random) {
        this.plugin = plugin;
        this.packetBridge = packetBridge;
        this.settings = settings;
        this.service = service;
        this.iconResolver = iconResolver;
        this.session = session;
        this.uiId = uiId;
        this.fight = new FishingFight(settings, session, talentBonuses, fatigueMultiplier, ariaBridge, random);
    }

    public boolean start() {
        if (!open()) return false;
        if (beginFight()) return true;
        cleanup();
        return false;
    }

    public boolean open() {
        if (service.isStopping()) return false;
        Player player = Bukkit.getPlayer(session.playerUuid());
        if (player == null) {
            cleanup();
            return false;
        }

        if (!packetBridge.openUi(player, uiId)) {
            cleanup();
            return false;
        }
        opened = true;
        return true;
    }

    public boolean isReady() {
        return ready;
    }

    public boolean beginFight() {
        Player player = Bukkit.getPlayer(session.playerUuid());
        if (!opened || ready || player == null || service.isStopping() || !session.isActive()) {
            return false;
        }
        ready = true;
        // 先打开 UI，再延迟 2 tick 发送 init 包（参考 battlepass 的 openTasks 模式）
        Bukkit.getScheduler().runTaskLater(plugin, service.guard(() -> {
            if (player.isOnline() && session.isActive()) {
                sendInitPacket(player);
            }
        }), 2L);

        // 启动 tick 任务（延迟 2 tick 开始，确保 init 包先到达）
        long interval = Math.max(1, settings.minigameTickInterval());
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickPulse.run();
            }
        }.runTaskTimer(plugin, interval + 2, interval);
        return true;
    }

    /**
     * 玩家主动放弃钓鱼，直接以失败结束小游戏。
     */
    public void giveUp(@NotNull Player player) {
        if(service.isStopping())return;
        if (!ready) {
            cleanup();
            return;
        }
        // 使用 finished 的 CAS 防止与 tick 竞态（giveUp 可能从事件线程调用）
        if (finished) return;
        finished = true;
        if (session.opening().repeatHook()) {
            cleanup();
            return;
        }
        finishWithResult(player, false, false, fight.frame());
        service.onMinigameComplete(session, false, false);
        cleanup();
    }

    private void tick() {
        try {
            tickInternal();
        } catch (Throwable t) {
            service.getLogger().severe("tick 异常，强制清理小游戏: " + t.getClass().getSimpleName() + " - " + t.getMessage());
            t.printStackTrace();
            if (!finished) {
                finished = true;
                try { finishWithResult(Bukkit.getPlayer(session.playerUuid()), false, false, fight.frame()); } catch (Throwable ignored) {}
                try { service.onMinigameComplete(session, false, false); } catch (Throwable ignored) {}
            }
            try { cleanup(); } catch (Throwable ignored) {}
        }
    }

    private void tickInternal() {
        Player player = Bukkit.getPlayer(session.playerUuid());
        if (player == null || !session.isActive() || finished) {
            cleanup();
            return;
        }

        var barInput = session.barInput();
        FishingFight.Frame frame = fight.step(barInput.pressing(), session.consumeSlackenRequest(), session.consumeHookSetRequest());
        appliedBarInputSequence = barInput.sequence();
        sendUpdatePacket(player, frame, "playing", frame.fishInside());
        if (frame.finished()) {
            success = frame.landed();
            finished = true;
            finishWithResult(player, success, frame.fishInside(), frame);
            service.onMinigameComplete(session, success, frame.perfect(), frame.treasureClaimed());
        }
    }

    /**
     * 结束小游戏：先向客户端发送 state=success/failed 的最终结果包，
     * 停止 tick 任务，并延迟约 1.5 秒（30 ticks）关闭 HUD，让玩家看到结果覆盖层。
     */
    private void finishWithResult(@NotNull Player player, boolean won, boolean fishInside, FishingFight.Frame frame) {
        session.setActive(false);
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
        }

        sendUpdatePacket(player, frame, won ? "success" : "failed", fishInside);

        new BukkitRunnable() {
            @Override
            public void run() {
                service.callApi(() -> {
                Player p = Bukkit.getPlayer(session.playerUuid());
                FishingMinigame current = service.getActiveMinigame(session.playerUuid());
                if (p != null && (current == null || current == FishingMinigame.this)) {
                    packetBridge.closeUi(p, uiId);
                }
                return null;
                },null);
            }
        }.runTaskLater(plugin, 30L);
    }

    public void sendInitPacket(@NotNull Player player) {
        if (!ready) return;
        FishingFight.Frame frame = fight.frame();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", PACKET_ID);
        payload.put("fishId", session.fish().id());
        payload.put("fishName", session.fish().displayName());
        payload.put("fishY", frame.fishY());
        payload.put("barY", frame.barY());
        payload.put("barHeight", frame.barHeight());
        payload.put("progress", frame.progress());
        payload.put("state", "playing");
        payload.put("timeLeft", frame.remainingTicks());
        payload.put("caughtSize", session.caughtSize());
        payload.put("rarity", session.fish().rarity().name().toLowerCase());
        payload.put("fightId", session.id());
        payload.put("tensionEnabled", session.tensionEnabled());
        var obstacle = session.obstacle();
        payload.put("obstacleName", obstacle == null ? "" : obstacle.displayName());
        payload.put("obstacleMin", obstacle == null ? 0.0 : obstacle.trackMin());
        payload.put("obstacleMax", obstacle == null ? 0.0 : obstacle.trackMax());
        var current = session.current();
        payload.put("currentName", current == null ? "" : current.displayName());
        payload.put("currentMin", current == null ? 0.0 : current.trackMin());
        payload.put("currentMax", current == null ? 0.0 : current.trackMax());
        payload.put("currentDirection", current == null ? 0 : (current.driftPerTick() > 0 ? 1 : -1));
        payload.put("staminaEnabled", session.fish().stamina() != null);
        payload.put("personalityName", session.opening().personality().displayName());
        payload.put("repeatHook", session.opening().repeatHook());
        var motion = fight.barMotion();
        if (motion.isFinite()) {
            payload.put("barMotion", Map.of(
                "releasedForce", motion.releasedForce(), "heldForce", motion.heldForce(),
                "forceMultiplier", motion.forceMultiplier(), "damping", motion.damping(),
                "bounceDamping", motion.bounceDamping(), "turnResponse", motion.turnResponse(),
                "reverseRetention", FishingFight.BarMotion.REVERSE_VELOCITY_RETENTION,
                "stepMillis", Math.max(1, settings.minigameTickInterval()) * 50.0));
        }
        putFightState(payload, frame);
        // 鱼物品图标 JSON（UI 端 Slot 控件通过 seticon 渲染）
        String fishIconJson = iconResolver.resolveItemJson(player, session.fish().displayIcon());
        service.debug("sendInitPacket: fish=" + session.fish().id() + " iconLen=" + fishIconJson.length());
        payload.put("fishIconJson", fishIconJson);
        packetBridge.sendPacket(player, uiId, "init", payload);
    }

    private void sendUpdatePacket(@NotNull Player player, FishingFight.Frame frame, String state, boolean fishInside) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", PACKET_ID);
        payload.put("fishY", frame.fishY());
        payload.put("barY", frame.barY());
        payload.put("barHeight", frame.barHeight());
        payload.put("progress", frame.progress());
        payload.put("state", state);
        payload.put("timeLeft", frame.remainingTicks());
        payload.put("fishInside", fishInside);
        payload.put("fightId", session.id());
        putFightState(payload, frame);
        packetBridge.sendPacket(player, uiId, "update", payload);
    }

    private void putFightState(Map<String, Object> payload, FishingFight.Frame frame) {
        payload.put("barVelocity", Double.isFinite(frame.barVelocity()) ? frame.barVelocity() : 0.0);
        payload.put("barInputSequence", appliedBarInputSequence);
        payload.put("phase", frame.phase().name());
        payload.put("moveId", frame.moveId());
        payload.put("direction", frame.direction());
        payload.put("phaseSteps", frame.phaseSteps());
        payload.put("tension", frame.tension());
        payload.put("slackened", frame.slackened());
        payload.put("slackCooldown", frame.slackCooldown());
        payload.put("lineSnapped", frame.lineSnapped());
        payload.put("obstacleWarning", frame.obstacleWarning());
        payload.put("obstacleScraping", frame.obstacleScraping());
        payload.put("currentDrift", frame.currentDrift());
        payload.put("stamina", frame.stamina());
        payload.put("exhaustionSteps", frame.exhaustionSteps());
        payload.put("personalityRush", frame.personalityRush());
        payload.put("hookPhase", frame.hookPhase().name());
        payload.put("hookStep", frame.hookStep());
        payload.put("hookErrorSteps", frame.hookErrorSteps());
        payload.put("hookRound", Long.toString(frame.hookRound()));
        payload.put("hookRoundsCompleted", frame.hookRoundsCompleted());
        payload.put("hookAccuracyPercent", frame.hookAccuracyPercent());
        payload.put("hookResult", frame.step() < 24 ? frame.hookResult().name() : FishingFight.HookResult.NONE.name());
        payload.put("treasureVisible", frame.treasureVisible());
        payload.put("treasureY", frame.treasureY());
        payload.put("treasureProgress", frame.treasureProgress());
        payload.put("treasureClaimed", frame.treasureClaimed());
    }

    public void cleanup() {
        // 幂等保护：cleanup 可能从 tickInternal、giveUp、异常处理多处调用
        if (!cleanedUp.compareAndSet(false, true)) return;
        tickPulse.close();
        session.setActive(false);
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
        }
        Player player = Bukkit.getPlayer(session.playerUuid());
        FishingMinigame current = service.getActiveMinigame(session.playerUuid());
        try {
            if (player != null && (current == null || current == this)) {
                packetBridge.closeUi(player, uiId);
            }
        } finally {
            service.removeMinigame(session.playerUuid(), this);
        }
    }

    public java.util.concurrent.CompletionStage<Void> stopped() { return tickPulse.stopped(); }

    public boolean isFinished() {
        return finished;
    }

    public boolean isSuccess() {
        return success;
    }

    public @NotNull FishingSession getSession() {
        return session;
    }

}
