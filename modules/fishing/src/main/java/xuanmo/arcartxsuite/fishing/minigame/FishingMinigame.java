package xuanmo.arcartxsuite.fishing.minigame;

import java.util.LinkedHashMap;
import java.util.List;
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
import xuanmo.arcartxsuite.fishing.model.FishBehaviorType;
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
    private final xuanmo.arcartxsuite.fishing.talent.TalentBonuses talentBonuses;

    // 物理状态
    private double fishPosition = 0.5;      // [0, 1]
    private double fishVelocity = 0.0;
    private double barPosition = 0.5;       // 绿条中心 [0, 1]
    private double barVelocity = 0.0;
    private double progress = 0.5;          // [0, 1]
    private long tick = 0;
    private int currentStageIndex = -1;     // 当前行为阶段索引，-1 = 使用默认 behaviors
    private double barHeightRatioCache = 0.15; // 绿条高度缓存（每 tick 更新一次）
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
                           @NotNull FishingSession session, @NotNull String uiId,
                           @NotNull xuanmo.arcartxsuite.fishing.talent.TalentBonuses talentBonuses) {
        this.plugin = plugin;
        this.packetBridge = packetBridge;
        this.settings = settings;
        this.service = service;
        this.itemGenerator = itemGenerator;
        this.session = session;
        this.uiId = uiId;
        this.talentBonuses = talentBonuses;
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

    /**
     * 玩家主动放弃钓鱼，直接以失败结束小游戏。
     */
    public void giveUp(@NotNull Player player) {
        if (finished) return;
        finished = true;
        double barHeightRatio = calculateBarHeightRatio();
        finishWithResult(player, false, false, barHeightRatio);
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

        // 缓存绿条高度（本 tick 内不变，避免重复计算）
        barHeightRatioCache = calculateBarHeightRatio();

        // 多阶段行为：根据进度切换鱼的行为模式
        List<FishDefinition.BehaviorStage> stages = session.fish().behaviorStages();
        if (!stages.isEmpty()) {
            FishDefinition.BehaviorStage stage = session.fish().selectBehaviorStage(progress);
            int newStageIndex = stage != null ? stages.indexOf(stage) : -1;
            if (newStageIndex != currentStageIndex) {
                currentStageIndex = newStageIndex;
                // 重新选择行为并重建引擎
                FishBehaviorType newType = stage != null && !stage.behaviors().isEmpty()
                    ? selectWeightedBehavior(stage.behaviors())
                    : session.fish().randomBehavior();
                fishEngine.switchBehavior(newType);
            }
        }

        // 更新鱼位置（等级越高鱼越慢，使用加权混合等级修正模型）
        double lvlMod = levelModifier();
        double fishSpeedMultiplier = (1.0 - lvlMod * settings.fishSpeedLevelScale()) * (1.0 - talentBonuses.fishSpeedReduction());
        double fishSpeed = fishEngine.tick(fishPosition, fishVelocity, tick, session.fish().difficulty()) * fishSpeedMultiplier;
        fishVelocity = fishSpeed;
        double newFishPosition = fishPosition + fishSpeed;
        // 鱼边界反弹：先检测碰撞再 clamp，确保反弹速度方向正确
        if (newFishPosition <= 0.0) {
            fishPosition = 0.0;
            fishVelocity = Math.abs(fishVelocity) * settings.fishEdgeBounce() * (1.0 - talentBonuses.fishEdgeReduction());
        } else if (newFishPosition >= 1.0) {
            fishPosition = 1.0;
            fishVelocity = -Math.abs(fishVelocity) * settings.fishEdgeBounce() * (1.0 - talentBonuses.fishEdgeReduction());
        } else {
            fishPosition = newFishPosition;
        }

        // ── 驱赶机制：鱼偶尔迅速逃离绿条范围 ──
        // 绿条高度本 tick 内不变，复用上方计算结果（避免重复调用 calculateBarHeightRatio）
        double barHeightRatioForFlee = barHeightRatioCache;
        double halfBarForFlee = barHeightRatioForFlee / 2.0;
        double barBottomForFlee = barPosition - halfBarForFlee;
        double barTopForFlee = barPosition + halfBarForFlee;
        boolean fishInsideForFlee = fishPosition >= barBottomForFlee && fishPosition <= barTopForFlee;

        // 鱼独立逃离参数（可选覆盖全局配置）
        FishDefinition.FleeParams fp = session.fish().fleeParams();
        double fleeBaseSpeed = fp != null && fp.baseSpeed() != null ? fp.baseSpeed() : settings.fleeBaseSpeed();
        double fleeSpeedPerDiff = fp != null && fp.speedPerDifficulty() != null ? fp.speedPerDifficulty() : settings.fleeSpeedPerDifficulty();
        double fleeBaseChance = fp != null && fp.baseChance() != null ? fp.baseChance() : settings.fleeBaseChance();
        double fleeChancePerDiff = fp != null && fp.chancePerDifficulty() != null ? fp.chancePerDifficulty() : settings.fleeChancePerDifficulty();
        int fleeMinDur = fp != null && fp.minDuration() != null ? fp.minDuration() : settings.fleeMinDuration();
        int fleeMaxDur = fp != null && fp.maxDuration() != null ? fp.maxDuration() : settings.fleeMaxDuration();
        int fleeCheckMin = fp != null && fp.checkMinInterval() != null ? fp.checkMinInterval() : settings.fleeCheckMinInterval();
        int fleeCheckMax = fp != null && fp.checkMaxInterval() != null ? fp.checkMaxInterval() : settings.fleeCheckMaxInterval();
        int fleeCooldownMin = fp != null && fp.cooldownMin() != null ? fp.cooldownMin() : settings.fleeCooldownMin();
        int fleeCooldownMax = fp != null && fp.cooldownMax() != null ? fp.cooldownMax() : settings.fleeCooldownMax();

        if (fleeing) {
            // 正在逃离：施加逃离速度（天赋降低逃离速度）
            if (tick < fleeUntilTick) {
                double fleeSpeed = (fleeBaseSpeed + (session.fish().difficulty() / 100.0) * fleeSpeedPerDiff)
                    * (1.0 - lvlMod * settings.fleeSpeedLevelScale()) * (1.0 - talentBonuses.fleeSpeedReduction());
                fishVelocity = fleeDirection * fleeSpeed;
                fishPosition = clamp(fishPosition + fishVelocity, 0.0, 1.0);
                if (fishPosition <= 0.02 || fishPosition >= 0.98) {
                    fleeing = false; // 撞到边缘停止逃离
                }
            } else {
                fleeing = false;
            }
            // 逃离期间重置下次检查时间（天赋延长冷却）
            int cooldownMin = (int) (fleeCooldownMin * (1.0 + talentBonuses.fleeCooldownBonus()));
            int cooldownMax = (int) (fleeCooldownMax * (1.0 + talentBonuses.fleeCooldownBonus()));
            if (cooldownMax <= cooldownMin) cooldownMax = cooldownMin + 1;
            nextFleeCheckTick = tick + cooldownMin + ThreadLocalRandom.current().nextInt(cooldownMax - cooldownMin);
        } else if (tick >= nextFleeCheckTick) {
            // 检查是否触发驱赶：鱼在绿条内时有概率逃离
            // 难度越高，逃离概率越大；玩家等级越高，逃离概率越低（等级修正模型）
            // 天赋降低逃离概率
            double fleeChance = (fleeBaseChance + (session.fish().difficulty() / 100.0) * fleeChancePerDiff)
                * (1.0 - lvlMod * settings.fleeChanceLevelScale()) * (1.0 - talentBonuses.fleeChanceReduction());
            if (fishInsideForFlee && ThreadLocalRandom.current().nextDouble() < fleeChance) {
                fleeing = true;
                // 逃离方向：朝远离绿条中心的方向
                fleeDirection = fishPosition < barPosition ? -1.0 : 1.0;
                // 如果鱼在轨道边缘，反向逃离
                if (fleeDirection < 0 && fishPosition < 0.15) fleeDirection = 1.0;
                if (fleeDirection > 0 && fishPosition > 0.85) fleeDirection = -1.0;
                // 天赋缩短逃离持续时间
                int adjMinDur = (int) (fleeMinDur * (1.0 - talentBonuses.fleeDurationReduction()));
                int adjMaxDur = (int) (fleeMaxDur * (1.0 - talentBonuses.fleeDurationReduction()));
                if (adjMaxDur <= adjMinDur) adjMaxDur = adjMinDur + 1;
                fleeUntilTick = tick + adjMinDur + ThreadLocalRandom.current().nextInt(adjMaxDur - adjMinDur);
            }
            if (fleeCheckMax <= fleeCheckMin) fleeCheckMax = fleeCheckMin + 1;
            nextFleeCheckTick = tick + fleeCheckMin + ThreadLocalRandom.current().nextInt(fleeCheckMax - fleeCheckMin);
        }

        // 计算绿条高度（基于玩家等级）—— 使用本 tick 缓存的值
        double barHeightRatio = barHeightRatioCache;
        double halfBar = barHeightRatio / 2.0;

        // 更新绿条位置（物理：惯性 + 重力 + 点击力 + 法向力 + 反弹）
        boolean pressing = session.isPressing();
        double force = pressing ? settings.barClickForce() : settings.barGravity();
        double forceMultiplier = settings.barForceMultiplier() * (1.0 + talentBonuses.barForceBonus());
        double damping = settings.barDamping() + talentBonuses.barDampingBonus();

        // 施加持续力（重力/点击力）
        barVelocity += force * forceMultiplier;
        barVelocity *= damping;
        barPosition = barPosition + barVelocity;

        // 边界碰撞处理：反弹 + 法向力
        // 法向力：绿条撞到边界后，如果持续力朝向边界，抵消该力（防止被钉住）。
        // 反弹：碰撞后速度反转并衰减。
        double bounceDamping = settings.barBounceDamping() * (1.0 - talentBonuses.barBounceReduction());
        if (barPosition - halfBar <= 0.0) {
            barPosition = halfBar;
            // 反弹：速度反转并衰减
            barVelocity = Math.abs(barVelocity) * bounceDamping;
            // 法向力：如果持续力朝下（按住鼠标），抵消下推力使绿条不被钉住
            if (force < 0) {
                barVelocity = Math.max(barVelocity, 0.0); // 确保速度朝上（逃离边界）
            }
        }
        if (barPosition + halfBar >= 1.0) {
            barPosition = 1.0 - halfBar;
            barVelocity = -Math.abs(barVelocity) * bounceDamping;
            if (force > 0) {
                barVelocity = Math.min(barVelocity, 0.0); // 确保速度朝下（逃离边界）
            }
        }
        barPosition = clamp(barPosition, halfBar, 1.0 - halfBar);

        // 检查鱼是否在绿条内
        double barBottom = barPosition - halfBar;
        double barTop = barPosition + halfBar;
        boolean fishInside = fishPosition >= barBottom && fishPosition <= barTop;

        // 进度变化：等级越高进度增长越快（等级修正模型），天赋加成增速/减少下降
        // 鱼独立进度速率（可选覆盖全局配置）
        double fishProgressGainRate = session.fish().progressGainRate() != null ? session.fish().progressGainRate() : settings.progressGainRate();
        double fishProgressDrainRate = session.fish().progressDrainRate() != null ? session.fish().progressDrainRate() : settings.progressDrainRate();

        double progressGainMultiplier = settings.progressRateMultiplier() * (1.0 + lvlMod * settings.progressRateLevelScale()) * (1.0 + talentBonuses.progressRateBonus());
        // 进度衰减也受等级修正：等级越高衰减越慢（×(1 - lvlMod × scale)）
        double progressDrainMultiplier = settings.progressRateMultiplier() * (1.0 - lvlMod * settings.progressRateLevelScale()) * (1.0 - talentBonuses.progressDrainReduction());
        if (fishInside) {
            progress += fishProgressGainRate * progressGainMultiplier;
        } else {
            progress -= fishProgressDrainRate * progressDrainMultiplier;
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

    private FishBehaviorType selectWeightedBehavior(List<FishDefinition.BehaviorEntry> entries) {
        double totalWeight = entries.stream().mapToDouble(FishDefinition.BehaviorEntry::weight).sum();
        if (totalWeight <= 0) return FishBehaviorType.SMOOTH;
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;
        for (FishDefinition.BehaviorEntry entry : entries) {
            cumulative += entry.weight();
            if (roll <= cumulative) return entry.type();
        }
        return entries.get(entries.size() - 1).type();
    }

    private double calculateBarHeightRatio() {
        // 绿条像素高度 = 基础高度 + 玩家等级加成 + 鱼竿加成 + 天赋加成
        int barHeightPx = settings.baseGreenBarHeight()
            + session.playerLevel() * settings.heightPerLevel()
            + session.greenBarHeightBonus()
            + (int) talentBonuses.barHeightBonus();
        // 转换为 [0,1] 比例，轨道实际高度 644 像素
        double ratio = barHeightPx / 644.0;
        // 难度越高绿条越窄：难度 0 时不缩减，难度 100 时缩减 difficultyBarShrink
        double difficultyShrink = 1.0 - (session.adjustedDifficulty() / 100.0) * settings.difficultyBarShrink();
        ratio *= difficultyShrink;
        // 限制在合理范围 [0.08, 0.9]
        return Math.max(0.08, Math.min(ratio, 0.9));
    }

    /**
     * 加权混合多函数等级修正模型。
     * <p>
     * 三个基础函数：
     * - f1 = sqrt(x)        — 前期主导（快速起步）
     * - f2 = (1-cos(πx))/2  — 中期主导（S型加速）
     * - f3 = x²             — 后期主导（递减收敛）
     * <p>
     * 权重随等级变化（Beta分布权重）：
     * - w1 = (1-x)²  — 前期权重高
     * - w2 = 2x(1-x) — 中期权重高
     * - w3 = x²      — 后期权重高
     * <p>
     * 最终修正 = (w1×f1 + w2×f2 + w3×f3) / (w1+w2+w3) × scale
     * <p>
     * 返回值范围 [0, scale]，等级越高修正越大，但递减收敛。
     *
     * @return 等级修正值 [0, levelModifierScale]
     */
    private double levelModifier() {
        int level = session.playerLevel();
        int maxLevel = settings.maxLevel();
        if (maxLevel <= 0 || level <= 0) return 0.0;
        double x = Math.min(1.0, (double) level / maxLevel);

        // 三个基础函数
        double f1 = Math.sqrt(x);
        double f2 = (1.0 - Math.cos(Math.PI * x)) / 2.0;
        double f3 = x * x;

        // Beta分布权重
        double w1 = (1.0 - x) * (1.0 - x);
        double w2 = 2.0 * x * (1.0 - x);
        double w3 = x * x;

        // 加权混合
        double weightedSum = w1 * f1 + w2 * f2 + w3 * f3;
        double weightTotal = w1 + w2 + w3;
        double modifier = weightTotal > 0 ? weightedSum / weightTotal : 0.0;

        return modifier * settings.levelModifierScale();
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
        service.debug("sendInitPacket: fish=" + session.fish().id() + " iconLen=" + fishIconJson.length());
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
