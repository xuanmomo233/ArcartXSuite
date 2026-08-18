package xuanmo.arcartxsuite.fishing.minigame;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration.FishingSettings;
import xuanmo.arcartxsuite.fishing.model.FishDefinition;
import xuanmo.arcartxsuite.fishing.service.FishingItemGenerator;
import xuanmo.arcartxsuite.fishing.service.FishingService;

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
    private final FishingItemGenerator itemGenerator;
    private final FishingSession session;
    private final FishBehaviorEngine fishEngine;
    private final String uiId;

    // 物理状态
    private double fishPosition = 0.5;      // [0, 1]
    private double fishVelocity = 0.0;
    private double barPosition = 0.5;       // 绿条中心 [0, 1]
    private double barVelocity = 0.0;
    private double progress = 0.5;          // [0, 1]
    private long tick = 0;
    private boolean wasPerfect = true;      // 是否全程在绿条内
    private BukkitTask tickTask;

    // 驱赶机制：鱼偶尔迅速逃离绿条范围
    private long nextFleeCheckTick = 0;     // 下次驱赶检查的 tick
    private boolean fleeing = false;        // 是否正在逃离
    private long fleeUntilTick = 0;         // 逃离持续到哪个 tick
    private double fleeDirection = 0.0;     // 逃离方向（+1 向上，-1 向下）

    // 结果
    private volatile boolean success = false;
    private volatile boolean finished = false;

    public FishingMinigame(@NotNull JavaPlugin plugin, @NotNull PacketBridgeAPI packetBridge,
                           @NotNull FishingSettings settings, @NotNull FishingService service,
                           @NotNull FishingItemGenerator itemGenerator,
                           @NotNull FishingSession session, @NotNull String uiId) {
        this.plugin = plugin;
        this.packetBridge = packetBridge;
        this.settings = settings;
        this.service = service;
        this.itemGenerator = itemGenerator;
        this.session = session;
        this.uiId = uiId;
        this.fishEngine = new FishBehaviorEngine(session.fish().randomBehavior(), session.adjustedDifficulty());
    }

    public void start() {
        Player player = Bukkit.getPlayer(session.playerUuid());
        if (player == null) {
            cleanup();
            return;
        }

        // 先打开 UI，再延迟 2 tick 发送 init 包（参考 battlepass 的 openTasks 模式）
        packetBridge.openUi(player, uiId);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && session.isActive()) {
                sendInitPacket(player);
            }
        }, 2L);

        // 启动 tick 任务（延迟 2 tick 开始，确保 init 包先到达）
        long interval = Math.max(1, settings.minigameTickInterval());
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, interval + 2, interval);
    }

    private void tick() {
        try {
            tickInternal();
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().severe("[Fishing] tick 异常，强制清理小游戏: " + t.getClass().getSimpleName() + " - " + t.getMessage());
            t.printStackTrace();
            if (!finished) {
                finished = true;
                try { finishWithResult(Bukkit.getPlayer(session.playerUuid()), false, false, calculateBarHeightRatio()); } catch (Throwable ignored) {}
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

        tick++;
        session.decrementRemainingTicks();

        // 更新鱼位置
        double fishSpeed = fishEngine.tick(fishPosition, fishVelocity, tick, session.fish().difficulty());
        fishVelocity = fishSpeed;
        fishPosition = clamp(fishPosition + fishSpeed, 0.0, 1.0);
        if (fishPosition <= 0.0 || fishPosition >= 1.0) {
            fishVelocity = -fishVelocity * 0.5;
            fishPosition = clamp(fishPosition, 0.0, 1.0);
        }

        // ── 驱赶机制：鱼偶尔迅速逃离绿条范围 ──
        // 先算绿条高度和半高，供驱赶判定
        double barHeightRatioForFlee = calculateBarHeightRatio();
        double halfBarForFlee = barHeightRatioForFlee / 2.0;
        double barBottomForFlee = barPosition - halfBarForFlee;
        double barTopForFlee = barPosition + halfBarForFlee;
        boolean fishInsideForFlee = fishPosition >= barBottomForFlee && fishPosition <= barTopForFlee;

        if (fleeing) {
            // 正在逃离：施加逃离速度
            if (tick < fleeUntilTick) {
                double fleeSpeed = 0.04 + (session.fish().difficulty() / 100.0) * 0.03;
                fishVelocity = fleeDirection * fleeSpeed;
                fishPosition = clamp(fishPosition + fishVelocity, 0.0, 1.0);
                if (fishPosition <= 0.02 || fishPosition >= 0.98) {
                    fleeing = false; // 撞到边缘停止逃离
                }
            } else {
                fleeing = false;
            }
            // 逃离期间重置下次检查时间
            nextFleeCheckTick = tick + 60 + ThreadLocalRandom.current().nextInt(80);
        } else if (tick >= nextFleeCheckTick) {
            // 检查是否触发驱赶：鱼在绿条内时有概率逃离
            // 难度越高，逃离概率越大、间隔越短
            double fleeChance = 0.12 + (session.fish().difficulty() / 100.0) * 0.18;
            if (fishInsideForFlee && ThreadLocalRandom.current().nextDouble() < fleeChance) {
                fleeing = true;
                // 逃离方向：朝远离绿条中心的方向
                fleeDirection = fishPosition < barPosition ? -1.0 : 1.0;
                // 如果鱼在轨道边缘，反向逃离
                if (fleeDirection < 0 && fishPosition < 0.15) fleeDirection = 1.0;
                if (fleeDirection > 0 && fishPosition > 0.85) fleeDirection = -1.0;
                fleeUntilTick = tick + 15 + ThreadLocalRandom.current().nextInt(20);
            }
            nextFleeCheckTick = tick + 40 + ThreadLocalRandom.current().nextInt(60);
        }

        // 计算绿条高度（基于玩家等级）—— 先算高度，碰撞用绿条顶底
        double barHeightRatio = calculateBarHeightRatio();
        double halfBar = barHeightRatio / 2.0;

        // 更新绿条位置（物理：惯性 + 重力 + 点击力）
        // 力系数较小 + 阻尼较高，让绿条操控细腻、不暴冲
        boolean pressing = session.isPressing();
        double force = pressing ? settings.barClickForce() : settings.barGravity();
        barVelocity += force * 0.04;
        barVelocity *= 0.82; // 阻尼较高，速度衰减快，便于精准操控
        barPosition = barPosition + barVelocity;

        // 底部反弹（绿条底部碰轨道底）
        if (barPosition - halfBar <= 0.0) {
            barPosition = halfBar;
            barVelocity = Math.abs(barVelocity) * settings.barBounceDamping();
        }
        // 顶部反弹（绿条顶部碰轨道顶）
        if (barPosition + halfBar >= 1.0) {
            barPosition = 1.0 - halfBar;
            barVelocity = -Math.abs(barVelocity) * settings.barBounceDamping();
        }
        barPosition = clamp(barPosition, halfBar, 1.0 - halfBar);

        // 检查鱼是否在绿条内
        double barBottom = barPosition - halfBar;
        double barTop = barPosition + halfBar;
        boolean fishInside = fishPosition >= barBottom && fishPosition <= barTop;

        if (fishInside) {
            progress += settings.progressGainRate() * 0.005;
        } else {
            progress -= settings.progressDrainRate() * 0.005;
            wasPerfect = false;
        }
        progress = clamp(progress, 0.0, 1.0);

        // 发送更新包
        sendUpdatePacket(player, fishInside, barHeightRatio);

        // 检查结束条件
        if (progress >= 1.0) {
            success = true;
            finished = true;
            finishWithResult(player, true, fishInside, barHeightRatio);
            service.onMinigameComplete(session, true, wasPerfect);
        } else if (progress <= 0.0 || session.remainingTicks() <= 0) {
            success = false;
            finished = true;
            finishWithResult(player, false, fishInside, barHeightRatio);
            service.onMinigameComplete(session, false, false);
        }
    }

    /**
     * 结束小游戏：先向客户端发送 state=success/failed 的最终结果包，
     * 停止 tick 任务，并延迟约 1.5 秒（30 ticks）关闭 HUD，让玩家看到结果覆盖层。
     */
    private void finishWithResult(@NotNull Player player, boolean won, boolean fishInside, double barHeightRatio) {
        session.setActive(false);
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", PACKET_ID);
        payload.put("fishY", fishPosition);
        payload.put("barY", barPosition);
        payload.put("barHeight", barHeightRatio);
        payload.put("progress", progress);
        payload.put("state", won ? "success" : "failed");
        payload.put("timeLeft", session.remainingTicks());
        payload.put("fishInside", fishInside);
        packetBridge.sendPacket(player, uiId, "update", payload);

        new BukkitRunnable() {
            @Override
            public void run() {
                Player p = Bukkit.getPlayer(session.playerUuid());
                if (p != null) {
                    packetBridge.closeUi(p, uiId);
                }
            }
        }.runTaskLater(plugin, 30L);
    }

    private double calculateBarHeightRatio() {
        // 将像素高度转换为 [0,1] 比例
        // 假设轨道总高度为 400 像素
        int barHeightPx = settings.baseGreenBarHeight()
            + session.playerLevel() * settings.heightPerLevel()
            + session.greenBarHeightBonus();
        return Math.min(barHeightPx / 400.0, 0.9);
    }

    public void sendInitPacket(@NotNull Player player) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", PACKET_ID);
        payload.put("fishId", session.fish().id());
        payload.put("fishName", session.fish().displayName());
        payload.put("fishY", fishPosition);
        payload.put("barY", barPosition);
        payload.put("barHeight", calculateBarHeightRatio());
        payload.put("progress", progress);
        payload.put("state", "playing");
        payload.put("timeLeft", session.remainingTicks());
        payload.put("caughtSize", session.caughtSize());
        payload.put("rarity", session.fish().rarity().name().toLowerCase());
        // 鱼物品图标 JSON（UI 端 Slot 控件通过 seticon 渲染）
        String fishIconJson = itemGenerator.generateItemJson(session.fish().itemRef(), player);
        org.bukkit.Bukkit.getLogger().info("[Fishing] sendInitPacket fishIconJson=" + (fishIconJson.isEmpty() ? "empty" : fishIconJson.substring(0, Math.min(fishIconJson.length(), 80))) + " for " + session.fish().id());
        payload.put("fishIconJson", fishIconJson);
        packetBridge.sendPacket(player, uiId, "init", payload);
    }

    private void sendUpdatePacket(@NotNull Player player, boolean fishInside, double barHeightRatio) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", PACKET_ID);
        payload.put("fishY", fishPosition);
        payload.put("barY", barPosition);
        payload.put("barHeight", barHeightRatio);
        payload.put("progress", progress);
        payload.put("state", "playing");
        payload.put("timeLeft", session.remainingTicks());
        payload.put("fishInside", fishInside);
        packetBridge.sendPacket(player, uiId, "update", payload);
    }

    public void cleanup() {
        session.setActive(false);
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
        }
        Player player = Bukkit.getPlayer(session.playerUuid());
        if (player != null) {
            packetBridge.closeUi(player, uiId);
        }
        // 确保从 activeMinigames 中移除，防止状态残留导致后续钓鱼无法触发
        service.removeMinigame(session.playerUuid());
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isSuccess() {
        return success;
    }

    public @NotNull FishingSession getSession() {
        return session;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
