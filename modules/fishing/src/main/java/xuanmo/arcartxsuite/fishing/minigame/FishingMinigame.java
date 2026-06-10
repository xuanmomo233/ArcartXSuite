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

    // 结果
    private volatile boolean success = false;
    private volatile boolean finished = false;

    public FishingMinigame(@NotNull JavaPlugin plugin, @NotNull PacketBridgeAPI packetBridge,
                           @NotNull FishingSettings settings, @NotNull FishingService service,
                           @NotNull FishingSession session, @NotNull String uiId) {
        this.plugin = plugin;
        this.packetBridge = packetBridge;
        this.settings = settings;
        this.service = service;
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

        // 打开 UI
        packetBridge.openUi(player, uiId);
        sendInitPacket(player);

        // 启动 tick 任务
        long interval = Math.max(1, settings.minigameTickInterval());
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    private void tick() {
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

        // 更新绿条位置（物理：惯性 + 重力 + 点击力）
        boolean pressing = session.isPressing();
        double force = pressing ? settings.barClickForce() : settings.barGravity();
        barVelocity += force * 0.1;
        barVelocity *= 0.92; // 阻尼
        barPosition = clamp(barPosition + barVelocity, 0.0, 1.0);

        // 底部反弹
        if (barPosition <= 0.0) {
            barPosition = 0.0;
            barVelocity = Math.abs(barVelocity) * settings.barBounceDamping();
        }
        if (barPosition >= 1.0) {
            barPosition = 1.0;
            barVelocity = -Math.abs(barVelocity) * settings.barBounceDamping();
        }

        // 计算绿条高度（基于玩家等级）
        double barHeightRatio = calculateBarHeightRatio();

        // 检查鱼是否在绿条内
        double halfBar = barHeightRatio / 2.0;
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
            cleanup();
            service.onMinigameComplete(session, true, wasPerfect);
        } else if (progress <= 0.0 || session.remainingTicks() <= 0) {
            success = false;
            finished = true;
            cleanup();
            service.onMinigameComplete(session, false, false);
        }
    }

    private double calculateBarHeightRatio() {
        // 将像素高度转换为 [0,1] 比例
        // 假设轨道总高度为 400 像素
        int barHeightPx = settings.baseGreenBarHeight() + session.playerLevel() * settings.heightPerLevel();
        return Math.min(barHeightPx / 400.0, 0.9);
    }

    private void sendInitPacket(@NotNull Player player) {
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
