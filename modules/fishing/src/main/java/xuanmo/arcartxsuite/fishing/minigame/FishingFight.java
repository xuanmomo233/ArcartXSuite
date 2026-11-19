package xuanmo.arcartxsuite.fishing.minigame;

import java.util.Map;
import java.util.EnumSet;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.script.AriaBridge;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration.FishingSettings;
import xuanmo.arcartxsuite.fishing.model.FishBehaviorType;
import xuanmo.arcartxsuite.fishing.model.FishDefinition;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.BehaviorEntry;
import xuanmo.arcartxsuite.fishing.model.FishDefinition.BehaviorPlan;
import xuanmo.arcartxsuite.fishing.model.RodDefinition;
import xuanmo.arcartxsuite.fishing.talent.TalentBonuses;

/** 服务端和离线试钓共用的钓鱼对抗逻辑。一次 step 对应一次小游戏更新。 */
public final class FishingFight {
    private final FishingSettings settings;
    private final FishingSession session;
    private final BarMotion barMotion;
    private final TalentBonuses talentBonuses;
    private final double fatigueMultiplier;
    private final @Nullable AriaBridge ariaBridge;
    private final RandomGenerator random;
    private final FishBehaviorEngine fishEngine;
    private final double barHeightRatio;
    private final @Nullable FishDefinition.Stamina staminaSettings;
    private final @Nullable FishDefinition.HookSet hookSet;
    private boolean fishInside;

    // 物理状态
    private double fishPosition = 0.5;      // [0, 1]
    private double fishVelocity = 0.0;
    private double barPosition = 0.5;       // 绿条中心 [0, 1]
    private double barVelocity = 0.0;
    private double progress = 0.0;          // [0, 1]，从 0 开始，需持续命中绿条积累到 1.0
    private long tick = 0;
    private int currentStageIndex = -1;     // 当前行为阶段索引，-1 = 使用默认 behaviors
    private BehaviorPlan behaviorPlan;
    private BehaviorEntry activeBehavior;
    private int behaviorIndex;
    private long behaviorStartedAt = 1;
    private long moveId;
    private long rushStarts;
    private long recoveryStarts;
    private long moveEnds;
    private long exhaustionEnds;
    private double stamina = 100;
    private double currentDrift;
    private double moveTarget = 0.5;
    private int moveDirection;
    private boolean announcedFlee;
    private boolean personalityRushPending;
    private boolean personalityRushUsed;
    private boolean personalityRush;
    private int hookSteps;
    private int hookWaitSteps;
    private int hookErrorSteps;
    private int hookFeedbackSteps;
    private static final int HOOK_FEEDBACK_STEPS = 20;
    private static final int HOOK_LOOP_MAX_EXTRA_WAIT_STEPS = 20;
    private long hookRoundsCompleted;
    private long hookRoundsOnTime;
    private HookResult hookResult;
    private final EnumSet<FishBehaviorType> usedRecoveryBonuses = EnumSet.noneOf(FishBehaviorType.class);
    private double tension;
    private long obstacleContactTicks;
    private long slackUntil;
    private long slackReadyAt;
    private int overloadedSteps;
    private boolean lineSnapped;
    private long treasureExpires;
    private double treasureY;
    private int treasureHold;
    private boolean treasureClaimed;
    private static final int TREASURE_HOLD_STEPS = 8;
    private boolean wasPerfect = true;      // 是否全程在绿条内（需至少 tick 一次才有效）
    private int consecutiveInsideTicks = 0; // 连续在绿条内的 tick 数（用于 streak 加成）
    private int imperfectTicks = 0;         // 累计不在绿条内的 tick 数（用于 perfect_window 容错）
    // 判定容错：鱼图标有视觉尺寸(80px/604px≈0.066)，中心点判定会与视觉边缘不符
    // hitTolerance = 判定半径，鱼中心在绿条边缘外此距离内仍算命中
    private static final double HIT_TOLERANCE = 0.05;
    // graceTicks = 边缘宽恕期，鱼刚离开绿条后此 tick 数内仍算命中（避免边缘频繁进出）
    private static final int GRACE_TICKS = 3;
    private int graceCounter = 0;           // 剩余宽恕 tick 数

    // 驱赶机制：鱼偶尔迅速逃离绿条范围
    private long nextFleeCheckTick = 0;     // 下次驱赶检查的 tick
    private boolean fleeing = false;        // 是否正在逃离
    private long fleeUntilTick = 0;         // 逃离持续到哪个 tick
    private double fleeDirection = 0.0;     // 逃离方向（+1 向上，-1 向下）

    public FishingFight(FishingSettings settings, FishingSession session, TalentBonuses talentBonuses,
                        double fatigueMultiplier, @Nullable AriaBridge ariaBridge, RandomGenerator random) {
        this.settings = settings;
        this.session = session;
        this.talentBonuses = talentBonuses;
        this.fatigueMultiplier = Math.max(0.1, fatigueMultiplier);
        this.ariaBridge = ariaBridge;
        this.random = random;
        this.barMotion = new BarMotion(
            settings.barGravity() * (1.0 - talentBonuses.barGravityReduction()),
            settings.barClickForce() * (1.0 + talentBonuses.barClickForceBonus()),
            settings.barForceMultiplier() * (1.0 + talentBonuses.barForceBonus()),
            settings.barDamping() + talentBonuses.barDampingBonus(),
            settings.barBounceDamping() * (1.0 - talentBonuses.barBounceReduction()),
            session.gear().turnResponse());
        this.behaviorPlan = session.fish().behaviorPlan();
        this.behaviorIndex = behaviorPlan.nextIndex(-1, random);
        this.activeBehavior = behaviorPlan.entryAt(behaviorIndex);
        this.fishEngine = new FishBehaviorEngine(activeBehavior.type(), session.adjustedDifficulty(), random);
        this.progress = clamp(talentBonuses.startProgressBonus(), 0.0, 0.5);
        this.barHeightRatio = calculateBarHeightRatio();
        this.staminaSettings = session.fish().stamina();
        this.hookSet = session.opening().hookSet();
        this.hookWaitSteps = nextHookWaitSteps();
        this.hookResult = hookSet == null ? HookResult.NONE : HookResult.PENDING;
    }

    public record BarMotion(double releasedForce, double heldForce, double forceMultiplier,
                            double damping, double bounceDamping, double turnResponse) {
        public static final double REVERSE_VELOCITY_RETENTION = 0.20;

        public boolean isFinite() {
            return Double.isFinite(releasedForce) && Double.isFinite(heldForce) && Double.isFinite(forceMultiplier)
                && Double.isFinite(damping) && Double.isFinite(bounceDamping) && Double.isFinite(turnResponse);
        }
    }

    public BarMotion barMotion() { return barMotion; }

    public record Frame(long step, double fishY, double barY, double barVelocity, double barHeight, double progress,
                        long remainingTicks, boolean fishInside, int behaviorStage, FishBehaviorType behavior, boolean fleeing,
                        boolean landed, boolean timedOut, boolean perfect, MovePhase phase, long moveId,
                        int direction, long phaseSteps, double tension, boolean slackened, long slackCooldown,
                        boolean lineSnapped, boolean treasureVisible, double treasureY, double treasureProgress,
                        boolean treasureClaimed, boolean obstacleWarning, boolean obstacleScraping,
                        double stamina, long exhaustionSteps, double currentDrift, boolean personalityRush,
                        HookPhase hookPhase, HookResult hookResult, int hookStep, int hookErrorSteps, long hookRound,
                        long hookRoundsCompleted, int hookAccuracyPercent) {
        public boolean finished() {
            return landed || timedOut || lineSnapped;
        }
    }

    public enum MovePhase { FREE, TELL, RUSH, RECOVERY }
    public enum HookPhase { DONE, TAP, READY, LATE, FEEDBACK }
    public enum HookResult { NONE, PENDING, EARLY, GOOD, LATE, MISSED }

    public Frame frame() {
        boolean landed = tick > 0 && !lineSnapped && progress >= 1.0;
        boolean timedOut = tick > 0 && !landed && !lineSnapped && session.remainingTicks() <= 0;
        MovePhase phase = movePhase();
        long phaseEnd = switch (phase) {
            case TELL -> rushStarts;
            case RUSH -> recoveryStarts;
            case RECOVERY -> moveEnds;
            case FREE -> tick;
        };
        boolean scraping = obstacleScraping();
        int hookAccuracyPercent = hookRoundsCompleted == 0 ? 0
            : (int) Math.round(100.0 * hookRoundsOnTime / hookRoundsCompleted);
        return new Frame(tick, fishPosition, barPosition, barVelocity, barHeightRatio, progress, session.remainingTicks(),
            fishInside, currentStageIndex, activeBehavior.type(), fleeing || (announcedFlee && phase == MovePhase.RUSH),
            landed, timedOut, landed && wasPerfect, phase, moveId, moveDirection, Math.max(0, phaseEnd - tick),
            tension, tick < slackUntil, Math.max(0, slackReadyAt - tick), lineSnapped,
            !treasureClaimed && tick < treasureExpires, treasureY, (double) treasureHold / TREASURE_HOLD_STEPS,
            treasureClaimed, obstacleContactTicks > 0 && !scraping, scraping, stamina,
            Math.max(0, exhaustionEnds - tick), currentDrift, personalityRush,
            hookPhase(), hookResult, hookSteps, hookErrorSteps, session.hookRound(), hookRoundsCompleted, hookAccuracyPercent);
    }

    public Frame step(boolean pressing) {
        return step(pressing, false);
    }

    public Frame step(boolean pressing, boolean slackenRequested) {
        return step(pressing, slackenRequested, false);
    }

    public Frame step(boolean pressing, boolean slackenRequested, boolean hookSetRequested) {
        if (tick > 0 && (lineSnapped || progress >= 1.0 || session.remainingTicks() <= 0)) return frame();
        if (hookSet != null && hookResult == HookResult.PENDING) {
            updateHookSet(hookSet, hookSetRequested);
            return frame();
        }
        if (session.opening().repeatHook()) {
            if (--hookFeedbackSteps == 0) {
                hookSteps = 0;
                hookWaitSteps = nextHookWaitSteps();
                hookErrorSteps = 0;
                hookResult = HookResult.PENDING;
                session.advanceHookRound();
            }
            return frame();
        }

        tick++;
        session.decrementRemainingTicks();

        updateBehavior();
        MovePhase phase = movePhase();
        if (phase == MovePhase.RUSH && staminaSettings != null) {
            stamina = Math.max(0, stamina - staminaSettings.rushDrainPerTick()
                * session.opening().personality().staminaDrainMultiplier());
        }
        currentDrift = phase == MovePhase.RECOVERY ? recoveryDrift() : 0;

        // 更新鱼位置（等级越高鱼越慢，使用加权混合等级修正模型）
        // 疲惫效率系数：效率高时鱼速度降低（除以 multiplier）
        double lvlMod = levelModifier();
        double fishSpeedMultiplier = (1.0 - lvlMod * settings.fishSpeedLevelScale()) * (1.0 - talentBonuses.fishSpeedReduction());
        long behaviorTick = activeBehavior.durationTicks() > 0 ? tick - behaviorStartedAt + 1 : tick;
        double rushSpeed = phase == MovePhase.RUSH ? rushSpeed(lvlMod, fishSpeedMultiplier) : 0;
        double fishSpeed = switch (phase) {
            case FREE -> fishEngine.tick(fishPosition, fishVelocity, behaviorTick, session.fish().difficulty())
                * fishSpeedMultiplier / fatigueMultiplier;
            case TELL -> 0.0;
            case RECOVERY -> currentDrift;
            case RUSH -> clamp(moveTarget - fishPosition, -rushSpeed, rushSpeed);
        };
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
        double halfBarForFlee = barHeightRatio / 2.0;
        double barBottomForFlee = barPosition - halfBarForFlee;
        double barTopForFlee = barPosition + halfBarForFlee;
        boolean fishInsideForFlee = fishPosition >= (barBottomForFlee - HIT_TOLERANCE) && fishPosition <= (barTopForFlee + HIT_TOLERANCE);

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

        if (phase == MovePhase.FREE && fleeing) {
            // 正在逃离：施加逃离速度（天赋降低逃离速度，疲惫效率高时逃离速度降低）
            if (tick < fleeUntilTick) {
                double fleeSpeed = (fleeBaseSpeed + (session.fish().difficulty() / 100.0) * fleeSpeedPerDiff)
                    * (1.0 - lvlMod * settings.fleeSpeedLevelScale()) * (1.0 - talentBonuses.fleeSpeedReduction())
                    * session.sizeModifiers().rushSpeed() / fatigueMultiplier;
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
            nextFleeCheckTick = tick + cooldownMin + random.nextInt(cooldownMax - cooldownMin);
        } else if (phase == MovePhase.FREE && tick >= nextFleeCheckTick) {
            // 检查是否触发驱赶：鱼在绿条内时有概率逃离
            // 难度越高，逃离概率越大；玩家等级越高，逃离概率越低（等级修正模型）
            // 天赋降低逃离概率，疲惫效率高时逃离概率降低
            double fleeChance = (fleeBaseChance + (session.fish().difficulty() / 100.0) * fleeChancePerDiff)
                * (1.0 - lvlMod * settings.fleeChanceLevelScale()) * (1.0 - talentBonuses.fleeChanceReduction())
                / fatigueMultiplier;
            if (fishInsideForFlee && random.nextDouble() < fleeChance) {
                fleeing = true;
                // 逃离方向：朝远离绿条中心的方向
                fleeDirection = fishPosition < barPosition ? -1.0 : 1.0;
                // 如果鱼在轨道边缘，反向逃离
                if (fleeDirection < 0 && fishPosition < 0.15) fleeDirection = 1.0;
                if (fleeDirection > 0 && fishPosition > 0.85) fleeDirection = -1.0;
                // 天赋缩短逃离持续时间
                int adjMinDur = Math.max(1, (int) (fleeMinDur * (1.0 - talentBonuses.fleeDurationReduction())));
                int adjMaxDur = Math.max(adjMinDur + 1, (int) (fleeMaxDur * (1.0 - talentBonuses.fleeDurationReduction())));
                fleeUntilTick = tick + sizeAdjustedRushDuration(adjMinDur + random.nextInt(adjMaxDur - adjMinDur));
            }
            if (fleeCheckMax <= fleeCheckMin) fleeCheckMax = fleeCheckMin + 1;
            nextFleeCheckTick = tick + fleeCheckMin + random.nextInt(fleeCheckMax - fleeCheckMin);
        }

        double halfBar = barHeightRatio / 2.0;

        // 更新绿条位置（物理：惯性 + 重力 + 点击力 + 法向力 + 反弹）
        // 天赋：降低重力（bar_gravity）、增加点击力（bar_click_force）
        double force = pressing ? barMotion.heldForce() : barMotion.releasedForce();
        double forceMultiplier = barMotion.forceMultiplier();
        if (barVelocity * force < 0) {
            barVelocity *= BarMotion.REVERSE_VELOCITY_RETENTION;
            forceMultiplier *= barMotion.turnResponse();
        }

        // 施加持续力（重力/点击力）
        barVelocity += force * forceMultiplier;
        barVelocity *= barMotion.damping();
        barPosition = barPosition + barVelocity;

        // 边界碰撞处理：反弹 + 法向力
        // 法向力：绿条撞到边界后，如果持续力朝向边界，抵消该力（防止被钉住）。
        // 反弹：碰撞后速度反转并衰减。
        double bounceDamping = barMotion.bounceDamping();
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

        // 检查鱼是否在绿条内（含判定半径 + 边缘宽恕期）
        double barBottom = barPosition - halfBar;
        double barTop = barPosition + halfBar;
        // 扩大判定边界：鱼中心在绿条边缘外 HIT_TOLERANCE 内仍算命中（匹配鱼图标的视觉尺寸）
        boolean fishInExpandedRange = fishPosition >= (barBottom - HIT_TOLERANCE) && fishPosition <= (barTop + HIT_TOLERANCE);
        // 边缘宽恕期：鱼刚离开扩大判定范围后，GRACE_TICKS 内仍算命中
        if (fishInExpandedRange) {
            graceCounter = GRACE_TICKS;
        } else if (graceCounter > 0) {
            graceCounter--;
            fishInExpandedRange = true;
        }
        if (fishInside && !fishInExpandedRange && !personalityRushUsed && session.opening().personality().firstMissRush()) {
            personalityRushPending = true;
        }
        fishInside = fishInExpandedRange;
        updateTreasure(phase);
        updateTension(slackenRequested, phase);
        if (lineSnapped) return frame();

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
            if (staminaSettings != null && tick < exhaustionEnds) delta *= staminaSettings.progressGainMultiplier();
            if (Double.isNaN(delta) || Double.isInfinite(delta)) delta = 0.0;
            if (tick >= slackUntil) progress += delta;
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

        return frame();
    }

    /** 单局对抗参数由游戏与试钓工具从同一份定义解析。 */
    public record Setup(int difficulty, long durationTicks, double fatigueMultiplier) {}

    public static Setup setup(FishingSettings settings, FishDefinition fish, String waterDifficultyFormula,
                              int playerLevel, RodDefinition rod, TalentBonuses bonuses, double fatigueMultiplier,
                              @Nullable AriaBridge ariaBridge, Logger logger) {
        double waterModifier = evaluateWaterDifficulty(waterDifficultyFormula, playerLevel, ariaBridge, logger);
        int difficulty = Math.max(1, Math.min(100, (int) (fish.difficulty() * waterModifier)));
        double difficultyFactor = 1.0 + (difficulty / 100.0) * settings.catchDurationDifficultyScale();
        long durationTicks = (long) (settings.catchDurationTicks() * difficultyFactor * (1.0 + bonuses.catchDurationBonus()))
            + rod.catchDurationBonus();

        double mitigation = bonuses.fatigueEfficiencyBonus();
        if (mitigation > 0 && fatigueMultiplier < 1.0) {
            fatigueMultiplier += (1.0 - fatigueMultiplier) * mitigation;
        }
        return new Setup(difficulty, durationTicks, fatigueMultiplier);
    }

    private static double evaluateWaterDifficulty(String formula, int level, @Nullable AriaBridge ariaBridge, Logger logger) {
        if (formula == null || formula.isBlank() || "1.0".equals(formula.trim())) return 1.0;
        if (ariaBridge == null || !ariaBridge.available()) {
            logger.warning("Aria 桥不可用，水域难度公式无法求值，回退为 1.0: " + formula);
            return 1.0;
        }
        try {
            Object result = ariaBridge.eval(formula, Map.of("level", level));
            return result instanceof Number number ? number.doubleValue() : 1.0;
        } catch (Exception e) {
            logger.warning("水域难度公式求值失败，回退为 1.0: " + formula + " - " + e.getMessage());
            return 1.0;
        }
    }

    private int nextHookWaitSteps() {
        if (hookSet == null) return 0;
        return hookSet.tapTicks() + (session.opening().repeatHook() ? random.nextInt(HOOK_LOOP_MAX_EXTRA_WAIT_STEPS + 1) : 0);
    }

    private void updateHookSet(FishDefinition.HookSet timing, boolean requested) {
        int readyEnd = hookWaitSteps + timing.windowTicks();
        if (requested) {
            int nearestReady = Math.max(hookWaitSteps, Math.min(readyEnd - 1, hookSteps));
            hookErrorSteps = hookSteps - nearestReady;
            hookResult = hookErrorSteps < 0 ? HookResult.EARLY : (hookErrorSteps > 0 ? HookResult.LATE : HookResult.GOOD);
        } else if (++hookSteps >= readyEnd + timing.lateTicks()) {
            hookResult = HookResult.MISSED;
        }
        if (hookResult != HookResult.PENDING && session.opening().repeatHook()) {
            hookFeedbackSteps = HOOK_FEEDBACK_STEPS;
            hookRoundsCompleted++;
            if (hookResult == HookResult.GOOD) hookRoundsOnTime++;
        }
    }

    private HookPhase hookPhase() {
        if (hookSet == null) return HookPhase.DONE;
        if (hookResult != HookResult.PENDING) return session.opening().repeatHook() ? HookPhase.FEEDBACK : HookPhase.DONE;
        if (hookSteps < hookWaitSteps) return HookPhase.TAP;
        return hookSteps < hookWaitSteps + hookSet.windowTicks() ? HookPhase.READY : HookPhase.LATE;
    }

    private void updateBehavior() {
        if (tick == exhaustionEnds) stamina = 100;
        if (moveEnds > 0 && tick == recoveryStarts) {
            if (moveId == 1 && hookSet != null) {
                int adjustment = hookResult == HookResult.GOOD ? hookSet.goodRecoveryBonus() : -hookSet.missRecoveryPenalty();
                moveEnds = Math.max(recoveryStarts, moveEnds + adjustment);
            }
            if (!announcedFlee) {
                int extra = session.gear().firstRecoveryBonus().getOrDefault(activeBehavior.type(), 0);
                if (extra > 0 && usedRecoveryBonuses.add(activeBehavior.type())) moveEnds += extra;
            }
            if (staminaSettings != null && stamina == 0) {
                exhaustionEnds = tick + staminaSettings.exhaustedTicks();
                moveEnds = Math.max(moveEnds, exhaustionEnds);
            }
        }
        if (moveEnds > tick) return;
        personalityRush = false;
        if (personalityRushPending) {
            personalityRushPending = false;
            personalityRushUsed = true;
            beginAnnouncedEscape();
            personalityRush = true;
            return;
        }

        var fish = session.fish();
        FishDefinition.BehaviorStage stage = fish.selectBehaviorStage(progress);
        int newStageIndex = stage == null ? -1 : fish.behaviorStages().indexOf(stage);
        if (moveEnds > 0 && !announcedFlee && tryAnnouncedEscape()) return;
        boolean finishedMove = moveEnds > 0;
        moveEnds = 0;
        announcedFlee = false;
        if (newStageIndex != currentStageIndex) {
            currentStageIndex = newStageIndex;
            behaviorPlan = stage == null || stage.behaviorPlan().entries().isEmpty()
                ? fish.behaviorPlan() : stage.behaviorPlan();
            behaviorIndex = -1;
            advanceBehavior();
        } else if (finishedMove || (activeBehavior.durationTicks() > 0 && tick - behaviorStartedAt >= activeBehavior.durationTicks())) {
            advanceBehavior();
        }
        if (tick == behaviorStartedAt && (activeBehavior.tellTicks() > 0 || activeBehavior.recoveryTicks() > 0)) {
            beginMove();
        }
    }

    private void advanceBehavior() {
        behaviorIndex = behaviorPlan.nextIndex(behaviorIndex, random);
        activeBehavior = behaviorPlan.entryAt(behaviorIndex);
        behaviorStartedAt = tick;
        fishEngine.switchBehavior(activeBehavior.type());
    }

    private boolean tryAnnouncedEscape() {
        if (!fishInside || tick < nextFleeCheckTick) return false;
        var fp = session.fish().fleeParams();
        double base = fp != null && fp.baseChance() != null ? fp.baseChance() : settings.fleeBaseChance();
        double perDifficulty = fp != null && fp.chancePerDifficulty() != null
            ? fp.chancePerDifficulty() : settings.fleeChancePerDifficulty();
        double chance = (base + session.fish().difficulty() / 100.0 * perDifficulty)
            * (1 - levelModifier() * settings.fleeChanceLevelScale())
            * (1 - talentBonuses.fleeChanceReduction()) / fatigueMultiplier;
        if (!(random.nextDouble() < chance)) return false;
        beginAnnouncedEscape();
        return true;
    }

    private void beginAnnouncedEscape() {
        var fp = session.fish().fleeParams();
        int min = fp != null && fp.minDuration() != null ? fp.minDuration() : settings.fleeMinDuration();
        int max = fp != null && fp.maxDuration() != null ? fp.maxDuration() : settings.fleeMaxDuration();
        double factor = 1 - talentBonuses.fleeDurationReduction();
        int scaledMin = Math.max(1, (int) (min * factor));
        int scaledMax = Math.max(scaledMin, (int) (max * factor));
        int duration = scaledMax > scaledMin ? random.nextInt(scaledMin, scaledMax) : scaledMin;
        activeBehavior = new BehaviorEntry(FishBehaviorType.DART, 1, duration, 9, 14);
        announcedFlee = true;
        behaviorStartedAt = tick;
        beginMove();
        int cooldownMin = fp != null && fp.cooldownMin() != null ? fp.cooldownMin() : settings.fleeCooldownMin();
        int cooldownMax = fp != null && fp.cooldownMax() != null ? fp.cooldownMax() : settings.fleeCooldownMax();
        double cooldownFactor = 1 + talentBonuses.fleeCooldownBonus();
        int minDelay = Math.max(1, (int) (cooldownMin * cooldownFactor));
        int maxDelay = Math.max(minDelay, (int) (cooldownMax * cooldownFactor));
        nextFleeCheckTick = moveEnds + (maxDelay > minDelay ? random.nextInt(minDelay, maxDelay) : minDelay);
    }

    private void beginMove() {
        moveId++;
        fleeing = false;
        rushStarts = tick + activeBehavior.tellTicks();
        recoveryStarts = rushStarts + sizeAdjustedRushDuration(activeBehavior.durationTicks());
        moveEnds = recoveryStarts + activeBehavior.recoveryTicks();
        moveDirection = switch (activeBehavior.type()) {
            case SINKER -> -1;
            case FLOATER -> 1;
            default -> announcedFlee ? (fishPosition < barPosition ? -1 : 1) : (random.nextBoolean() ? 1 : -1);
        };
        if (fishPosition < 0.15 && moveDirection < 0 || fishPosition > 0.85 && moveDirection > 0) {
            moveDirection = -moveDirection;
        }
        moveTarget = clamp(fishPosition + moveDirection * (announcedFlee ? 0.55 : 0.34), 0.03, 0.97);
    }

    private MovePhase movePhase() {
        if (tick >= moveEnds) return MovePhase.FREE;
        if (tick < rushStarts) return MovePhase.TELL;
        return tick < recoveryStarts ? MovePhase.RUSH : MovePhase.RECOVERY;
    }

    private double recoveryDrift() {
        var current = session.current();
        if (current == null || fishPosition < current.trackMin() || fishPosition > current.trackMax()) return 0;
        long elapsed = tick - recoveryStarts;
        if (elapsed >= current.carryTicks()) return 0;
        double drift = current.driftPerTick() * (1.0 - (double) elapsed / current.carryTicks());
        return clamp(fishPosition + drift, 0, 1) - fishPosition;
    }

    private double rushSpeed(double levelModifier, double fishSpeedMultiplier) {
        double sizeSpeed = session.sizeModifiers().rushSpeed();
        if (!announcedFlee) return clamp(0.045 * fishSpeedMultiplier * sizeSpeed / fatigueMultiplier, 0, 0.12);
        var fp = session.fish().fleeParams();
        double base = fp != null && fp.baseSpeed() != null ? fp.baseSpeed() : settings.fleeBaseSpeed();
        double perDifficulty = fp != null && fp.speedPerDifficulty() != null
            ? fp.speedPerDifficulty() : settings.fleeSpeedPerDifficulty();
        double speed = (base + session.fish().difficulty() / 100.0 * perDifficulty)
            * (1 - levelModifier * settings.fleeSpeedLevelScale()) * (1 - talentBonuses.fleeSpeedReduction());
        return clamp(speed * sizeSpeed / fatigueMultiplier, 0, 0.12);
    }

    private long sizeAdjustedRushDuration(int duration) {
        return Math.max(1, Math.round(duration * session.sizeModifiers().rushDuration()));
    }

    private boolean obstacleScraping() {
        var obstacle = session.obstacle();
        return obstacle != null && obstacleContactTicks > obstacle.warningTicks();
    }

    private void updateTension(boolean requested, MovePhase phase) {
        if (!session.tensionEnabled()) return;
        if (requested && tick >= slackReadyAt) {
            slackUntil = tick + 8;
            slackReadyAt = tick + 24;
        }
        double change;
        if (tick < slackUntil) {
            obstacleContactTicks = 0;
            change = -8;
        } else {
            var obstacle = session.obstacle();
            obstacleContactTicks = obstacle != null && obstacle.containsTrackPosition(fishPosition)
                ? obstacleContactTicks + 1 : 0;
            boolean rushing = phase == MovePhase.RUSH || fleeing;
            double pull = rushing ? (fishInside ? 6.0 : 3.0) * session.sizeModifiers().pull() : 0;
            double scrape = obstacle != null && obstacleScraping() ? obstacle.tensionPerTick() : 0;
            double release = rushing ? 0 : (phase == MovePhase.RECOVERY ? 2.5 : 1.25);
            change = (pull + scrape) / session.gear().lineStrength() - release;
        }
        tension = clamp(tension + change, 0, 100);
        overloadedSteps = tension >= 100 ? overloadedSteps + 1 : 0;
        int grace = Math.max(2, (int) Math.round(5 * Math.min(1, fatigueMultiplier)));
        lineSnapped = overloadedSteps >= grace;
    }

    private void updateTreasure(MovePhase phase) {
        if (session.treasureOffer() == null || treasureClaimed) return;
        if (treasureExpires == 0 && phase == MovePhase.RECOVERY && moveEnds - tick >= TREASURE_HOLD_STEPS
                && session.remainingTicks() >= TREASURE_HOLD_STEPS) {
            double candidate = fishPosition < 0.5 ? 0.86 : 0.14;
            if (Math.abs(candidate - fishPosition) > barHeightRatio + HIT_TOLERANCE
                && Math.abs(candidate - barPosition) > barHeightRatio / 2) {
                treasureY = candidate;
                treasureExpires = Math.min(moveEnds, tick + 30);
            }
        }
        if (tick >= treasureExpires) return;
        treasureHold = Math.abs(barPosition - treasureY) <= barHeightRatio / 2 ? treasureHold + 1 : 0;
        treasureClaimed = treasureHold >= TREASURE_HOLD_STEPS;
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
