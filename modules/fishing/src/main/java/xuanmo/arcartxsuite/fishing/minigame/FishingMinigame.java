package xuanmo.arcartxsuite.fishing.minigame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration.FishingSettings;
import xuanmo.arcartxsuite.fishing.model.FishBehaviorType;
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
    private final IconResolver iconResolver;
    private final FishingSession session;
    private final FishBehaviorEngine fishEngine;
    private final String uiId;
    private final xuanmo.arcartxsuite.fishing.talent.TalentBonuses talentBonuses;
    private final double fatigueMultiplier; // 疲惫效率系数（>1 提升，<1 降低，=1 无影响）
    private final @Nullable xuanmo.arcartxsuite.api.script.AriaBridge ariaBridge;

    // 物理状态
    private double fishPosition = 0.5;      // [0, 1]
    private double fishVelocity = 0.0;
    private double barPosition = 0.5;       // 绿条中心 [0, 1]
    private double barVelocity = 0.0;
    private double progress = 0.0;          // [0, 1]，从 0 开始，需持续命中绿条积累到 1.0
    private long tick = 0;
    private int currentStageIndex = -1;     // 当前行为阶段索引，-1 = 使用默认 behaviors
    private double barHeightRatioCache = 0.15; // 绿条高度缓存（每 tick 更新一次）
    private boolean wasPerfect = true;      // 是否全程在绿条内（需至少 tick 一次才有效）
    private int consecutiveInsideTicks = 0; // 连续在绿条内的 tick 数（用于 streak 加成）
    private int imperfectTicks = 0;         // 累计不在绿条内的 tick 数（用于 perfect_window 容错）
    private BukkitTask tickTask;
    private final AtomicBoolean cleanedUp = new AtomicBoolean(false); // cleanup 幂等保护

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
                           @NotNull IconResolver iconResolver,
                           @NotNull FishingSession session, @NotNull String uiId,
                           @NotNull xuanmo.arcartxsuite.fishing.talent.TalentBonuses talentBonuses,
                           double fatigueMultiplier,
                           @Nullable xuanmo.arcartxsuite.api.script.AriaBridge ariaBridge) {
        this.plugin = plugin;
        this.packetBridge = packetBridge;
        this.settings = settings;
        this.service = service;
        this.iconResolver = iconResolver;
        this.session = session;
        this.uiId = uiId;
        this.talentBonuses = talentBonuses;
        this.fatigueMultiplier = Math.max(0.1, fatigueMultiplier); // 防止除零和过度缩减
        this.ariaBridge = ariaBridge;
        this.fishEngine = new FishBehaviorEngine(session.fish().randomBehavior(), session.adjustedDifficulty());
        // 天赋：起始进度（开局直接获得一定进度）
        this.progress = Math.max(0.0, Math.min(0.5, talentBonuses.startProgressBonus()));
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
        // 使用 finished 的 CAS 防止与 tick 竞态（giveUp 可能从事件线程调用）
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
        // 疲惫效率系数：效率高时鱼速度降低（除以 multiplier）
        double lvlMod = levelModifier();
        double fishSpeedMultiplier = (1.0 - lvlMod * settings.fishSpeedLevelScale()) * (1.0 - talentBonuses.fishSpeedReduction());
        double fishSpeed = fishEngine.tick(fishPosition, fishVelocity, tick, session.fish().difficulty())
            * fishSpeedMultiplier / fatigueMultiplier;
        // M1: NaN/Infinity 恢复，防止配置错误或数值溢出导致游戏永久失效
        if (Double.isNaN(fishSpeed) || Double.isInfinite(fishSpeed)) {
            fishSpeed = 0.0;
        }
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
            // 正在逃离：施加逃离速度（天赋降低逃离速度，疲惫效率高时逃离速度降低）
            if (tick < fleeUntilTick) {
                double fleeSpeed = (fleeBaseSpeed + (session.fish().difficulty() / 100.0) * fleeSpeedPerDiff)
                    * (1.0 - lvlMod * settings.fleeSpeedLevelScale()) * (1.0 - talentBonuses.fleeSpeedReduction())
                    / fatigueMultiplier;
                fishVelocity = fleeDirection * fleeSpeed;
                fishPosition = clamp(fishPosition + fishVelocity, 0.0, 1.0);
                if (fishPosition <= 0.02 || fishPosition >= 0.98) {
                    fleeing = false; // 撞到边缘停止逃离
                }
            } else {
                fleeing = false;
            }
            // 逃离期间重置下次检查时间（天赋延长冷却）
            int cooldownMin = Math.max(1, (int) (fleeCooldownMin * (1.0 + talentBonuses.fleeCooldownBonus())));
            int cooldownMax = Math.max(cooldownMin + 1, (int) (fleeCooldownMax * (1.0 + talentBonuses.fleeCooldownBonus())));
            nextFleeCheckTick = tick + cooldownMin + ThreadLocalRandom.current().nextInt(cooldownMax - cooldownMin);
        } else if (tick >= nextFleeCheckTick) {
            // 检查是否触发驱赶：鱼在绿条内时有概率逃离
            // 难度越高，逃离概率越大；玩家等级越高，逃离概率越低（等级修正模型）
            // 天赋降低逃离概率，疲惫效率高时逃离概率降低
            double fleeChance = (fleeBaseChance + (session.fish().difficulty() / 100.0) * fleeChancePerDiff)
                * (1.0 - lvlMod * settings.fleeChanceLevelScale()) * (1.0 - talentBonuses.fleeChanceReduction())
                / fatigueMultiplier;
            if (fishInsideForFlee && ThreadLocalRandom.current().nextDouble() < fleeChance) {
                fleeing = true;
                // 逃离方向：朝远离绿条中心的方向
                fleeDirection = fishPosition < barPosition ? -1.0 : 1.0;
                // 如果鱼在轨道边缘，反向逃离
                if (fleeDirection < 0 && fishPosition < 0.15) fleeDirection = 1.0;
                if (fleeDirection > 0 && fishPosition > 0.85) fleeDirection = -1.0;
                // 天赋缩短逃离持续时间
                int adjMinDur = Math.max(1, (int) (fleeMinDur * (1.0 - talentBonuses.fleeDurationReduction())));
                int adjMaxDur = Math.max(adjMinDur + 1, (int) (fleeMaxDur * (1.0 - talentBonuses.fleeDurationReduction())));
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
        // 天赋：降低重力（bar_gravity）、增加点击力（bar_click_force）
        double gravity = settings.barGravity() * (1.0 - talentBonuses.barGravityReduction());
        double clickForce = settings.barClickForce() * (1.0 + talentBonuses.barClickForceBonus());
        double force = pressing ? clickForce : gravity;
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
        // M1: NaN/Infinity 恢复
        if (Double.isNaN(barPosition) || Double.isInfinite(barPosition)) {
            barPosition = 0.5; barVelocity = 0.0;
        }
        if (Double.isNaN(fishPosition) || Double.isInfinite(fishPosition)) {
            fishPosition = 0.5; fishVelocity = 0.0;
        }

        // 检查鱼是否在绿条内
        double barBottom = barPosition - halfBar;
        double barTop = barPosition + halfBar;
        boolean fishInside = fishPosition >= barBottom && fishPosition <= barTop;

        // ── 进度计算（曲线模型） ────────────────────────────────
        // 进度从 0 开始，需持续命中绿条积累到 1.0 才成功。
        // gain 和 drain 各自使用独立的 ProgressCurve（linear/accelerating/sin-wave/power），
        //   曲线值随当前进度变化，产生非线性的进度增长/衰减节奏。
        // 连续命中加成（streak）：连续在绿条内每达到 streak-threshold ticks，gain 系数递增 streak-bonus-per-step，
        //   最高叠加到 streak-max-bonus。离开绿条则 streak 重置为 0。
        // 等级修正：gain 随等级加快，drain 随等级减慢，各自独立缩放。
        // 天赋加成：gain 增速加成，drain 减免。
        // 鱼独立 baseRate 覆盖（可选）：鱼 yml 中的 progress-gain-rate/drain-rate 覆盖全局曲线的 baseRate。
        ProgressCurve gainCurve = settings.gainCurve();
        ProgressCurve drainCurve = settings.drainCurve();
        if (session.fish().progressGainRate() != null) {
            gainCurve = new ProgressCurve(session.fish().progressGainRate(), gainCurve.formula());
        }
        if (session.fish().progressDrainRate() != null) {
            drainCurve = new ProgressCurve(session.fish().progressDrainRate(), drainCurve.formula());
        }

        // ── 进度计算（曲线模型） ────────────────────────────────
        // 等级修正：gain 随等级加快，drain 随等级减慢，各自独立缩放
        double gainLevelFactor = 1.0 + lvlMod * settings.progressGainLevelScale();
        double drainLevelFactor = 1.0 - lvlMod * settings.progressDrainLevelScale();
        // 天赋加成
        double gainTalentFactor = 1.0 + talentBonuses.progressRateBonus();
        double drainTalentFactor = 1.0 - talentBonuses.progressDrainReduction();
        // 疲惫效率系数：效率高时 gain↑、drain↓
        double gainFatigueFactor = fatigueMultiplier;
        double drainFatigueFactor = 1.0 / fatigueMultiplier;

        if (fishInside) {
            // 连续命中 streak 加成
            consecutiveInsideTicks++;
            int streakThreshold = Math.max(1, settings.streakThreshold());
            int streakSteps = consecutiveInsideTicks / streakThreshold;
            double streakBonus = Math.max(0, Math.min(streakSteps * settings.streakBonusPerStep(), settings.streakMaxBonus()));
            double curveValue = gainCurve.evaluate(progress, true, ariaBridge,
                session.playerLevel(), session.adjustedDifficulty());
            double delta = curveValue * gainLevelFactor * gainTalentFactor * gainFatigueFactor * (1.0 + streakBonus);
            if (Double.isNaN(delta) || Double.isInfinite(delta)) delta = 0.0;
            progress += delta;
        } else {
            consecutiveInsideTicks = 0;
            // perfect_window 天赋：允许短暂离开绿条仍算完美（容错 tick 数 = bonus * 100）
            imperfectTicks++;
            int perfectTolerance = (int) (talentBonuses.perfectWindowBonus() * 100);
            if (imperfectTicks > perfectTolerance) {
                wasPerfect = false;
            }
            double curveValue = drainCurve.evaluate(progress, false, ariaBridge,
                session.playerLevel(), session.adjustedDifficulty());
            double delta = curveValue * drainLevelFactor * drainTalentFactor * drainFatigueFactor;
            if (Double.isNaN(delta) || Double.isInfinite(delta)) delta = 0.0;
            progress -= delta;
        }
        progress = clamp(progress, 0.0, 1.0);
        // L1: NaN 兜底（clamp 无法修复 NaN）
        if (Double.isNaN(progress)) progress = 0.0;

        // 发送更新包
        sendUpdatePacket(player, fishInside, barHeightRatio);

        // 检查结束条件：进度满 1.0 = 成功；超时 = 失败
        // 注意：进度从 0 开始，不会因 progress <= 0 失败（0 是下限，clamp 保证不会为负）
        if (progress >= 1.0) {
            success = true;
            finished = true;
            finishWithResult(player, true, fishInside, barHeightRatio);
            // M4: 完美捕获需至少 tick 过一次，防止未开始就结束时误判
            service.onMinigameComplete(session, true, wasPerfect && tick > 0);
        } else if (session.remainingTicks() <= 0) {
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
        // 疲惫效率系数：效率高时绿条高度增加（× multiplier）
        int barHeightPx = settings.baseGreenBarHeight()
            + session.playerLevel() * settings.heightPerLevel()
            + session.greenBarHeightBonus()
            + (int) talentBonuses.barHeightBonus();
        // 应用疲惫效率系数到绿条高度
        barHeightPx = (int) (barHeightPx * fatigueMultiplier);
        // 转换为 [0,1] 比例，轨道实际高度 644 像素
        double ratio = barHeightPx / 644.0;
        // 难度越高绿条越窄：难度 0 时不缩减，难度 100 时缩减 difficultyBarShrink
        double difficultyShrink = 1.0 - (session.adjustedDifficulty() / 100.0) * settings.difficultyBarShrink();
        double shrunkRatio = ratio * difficultyShrink;
        // 动态下限：difficultyBarShrink 越大，下限越低，让 shrink 系数始终有可见削减空间。
        //   - 0.02 为绝对底线（约 13 像素），防止绿条完全消失导致无法钓鱼
        //   - ratio × (1 - difficultyBarShrink) × 0.3 为动态部分：shrink 小时下限高（保可玩性），
        //     shrink 大时下限低（让激进 shrink 生效）
        double dynamicFloor = Math.max(0.02, ratio * (1.0 - settings.difficultyBarShrink()) * 0.3);
        // 限制在合理范围 [dynamicFloor, 0.9]
        return Math.max(dynamicFloor, Math.min(shrunkRatio, 0.9));
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
        String fishIconJson = iconResolver.resolveItemJson(player, session.fish().itemRef());
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
        // 幂等保护：cleanup 可能从 tickInternal、giveUp、异常处理多处调用
        if (!cleanedUp.compareAndSet(false, true)) return;
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
