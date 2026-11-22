package xuanmo.arcartxsuite.fishing.minigame;

import java.util.random.RandomGenerator;
import xuanmo.arcartxsuite.fishing.model.FishBehaviorType;

/**
 * 鱼类行为引擎，实现 {@link FishBehavior} 接口。
 * <p>
 * 关联模块：{@code fishing}。根据 {@link FishBehaviorType} 与难度参数，
 * 在每个 tick 计算鱼图标的目标位移量，模拟平滑游动、突进、下沉、上浮等行为模式。
 * <p>
 * 行为模型采用多频率正弦叠加 + 随机突进 + 停留抖动，让鱼的运动轨迹更变幻莫测。
 */
public final class FishBehaviorEngine implements FishBehavior {

    private final RandomGenerator random;
    private final int difficulty;
    private FishBehaviorType activeType; // 当前生效的行为类型（支持多阶段切换）
    private long lastDartTick = 0;
    private double dartTarget = 0.5;
    private double dartSpeed = 0.02;
    private long sinkerPhase = 0;
    private long floaterPhase = 0;
    // 混合行为状态：鱼会在不同模式间切换
    private long nextModeSwitchTick = 0;
    private int currentMode = 0; // 0=游荡, 1=突进, 2=停留抖动
    private double wanderFreq1 = 0.03;
    private double wanderFreq2 = 0.07;
    private double wanderAmp = 0.006;
    private long stayUntilTick = 0;

    public FishBehaviorEngine(FishBehaviorType type, int difficulty, RandomGenerator random) {
        this.random = random;
        this.activeType = type;
        this.difficulty = difficulty;
        // 随机初始化游荡频率，让每条鱼的节奏不同
        wanderFreq1 = 0.02 + random.nextDouble() * 0.04;
        wanderFreq2 = 0.05 + random.nextDouble() * 0.06;
        wanderAmp = 0.004 + random.nextDouble() * 0.006;
    }

    /**
     * 切换行为类型（多阶段行为用），重置内部状态。
     * <p>
     * M10: 完整重置所有行为相关状态，包括 dartTarget/dartSpeed/wander 参数，
     * 防止切回 DART 时跳到旧目标位置。
     */
    public void switchBehavior(FishBehaviorType newType) {
        this.activeType = newType;
        // 重置所有状态
        lastDartTick = 0;
        dartTarget = 0.5;
        dartSpeed = 0.02;
        sinkerPhase = 0;
        floaterPhase = 0;
        nextModeSwitchTick = 0;
        currentMode = 0;
        stayUntilTick = 0;
        // 重新随机化游荡参数，让切换后的行为节奏不同
        wanderFreq1 = 0.02 + random.nextDouble() * 0.04;
        wanderFreq2 = 0.05 + random.nextDouble() * 0.06;
        wanderAmp = 0.004 + random.nextDouble() * 0.006;
    }

    @Override
    public double tick(double currentPosition, double currentVelocity, long tick, int difficulty) {
        double jitter = this.difficulty / 100.0;
        FishBehaviorType type = this.activeType;
        switch (type) {
            case SMOOTH -> {
                return smoothBehavior(currentPosition, tick, jitter);
            }
            case DART -> {
                return dartBehavior(currentPosition, tick, jitter);
            }
            case SINKER -> {
                return sinkerBehavior(currentPosition, tick, jitter);
            }
            case FLOATER -> {
                return floaterBehavior(currentPosition, tick, jitter);
            }
            case MIXED -> {
                return mixedBehavior(currentPosition, tick, jitter);
            }
            case ZIGZAG -> {
                return zigzagBehavior(currentPosition, tick, jitter);
            }
            case IDLE -> {
                return idleBehavior(currentPosition, tick, jitter);
            }
        }
        return 0.0;
    }

    /**
     * 平滑游动：多频率正弦叠加 + 随机噪声 + 偶发突进 + 停留抖动。
     * 鱼会在"游荡→突进→停留抖动"三种模式间随机切换，轨迹不规则。
     */
    private double smoothBehavior(double position, long tick, double jitter) {
        // 模式切换
        if (tick >= nextModeSwitchTick) {
            double roll = random.nextDouble();
            if (roll < 0.15 + jitter * 0.1) {
                // 突进模式
                currentMode = 1;
                // 确保突进目标离当前位置足够远，避免一开始就到达
                dartTarget = random.nextDouble(0.1, 0.9);
                if (Math.abs(dartTarget - position) < 0.2) {
                    // 太近，选反方向
                    dartTarget = position < 0.5
                        ? random.nextDouble(0.5, 0.9)
                        : random.nextDouble(0.1, 0.5);
                }
                dartSpeed = 0.01 + jitter * 0.02;
                nextModeSwitchTick = tick + 15 + random.nextInt(25);
            } else if (roll < 0.30 + jitter * 0.1) {
                // 停留抖动模式（缩短持续时间，避免长时间不动）
                currentMode = 2;
                stayUntilTick = tick + 8 + random.nextInt(15);
                nextModeSwitchTick = stayUntilTick;
            } else {
                // 游荡模式
                currentMode = 0;
                // 随机刷新游荡频率，避免节奏单一
                wanderFreq1 = 0.02 + random.nextDouble() * 0.04;
                wanderFreq2 = 0.05 + random.nextDouble() * 0.06;
                nextModeSwitchTick = tick + 30 + random.nextInt(60);
            }
        }

        switch (currentMode) {
            case 1 -> {
                // 突进：朝目标快速移动
                double diff = dartTarget - position;
                if (Math.abs(diff) < 0.03) {
                    // 到达目标，立即切换到游荡模式
                    currentMode = 0;
                    nextModeSwitchTick = tick;
                    return (random.nextDouble() - 0.5) * 0.003;
                }
                return Math.signum(diff) * dartSpeed + (random.nextDouble() - 0.5) * 0.003 * jitter;
            }
            case 2 -> {
                // 停留抖动：在当前位置小幅抖动
                if (tick < stayUntilTick) {
                    return (random.nextDouble() - 0.5) * 0.006 * (1 + jitter);
                }
                currentMode = 0;
                nextModeSwitchTick = tick;
                return 0.0;
            }
            default -> {
                // 游荡：双频率正弦叠加 + 随机噪声 + 回中力
                double amp = wanderAmp + jitter * 0.008;
                double wave = Math.sin(tick * wanderFreq1) * amp + Math.sin(tick * wanderFreq2) * amp * 0.4;
                double noise = (random.nextDouble() - 0.5) * 0.003 * jitter;
                // 偶发小突变（5% 概率）
                if (random.nextDouble() < 0.05) {
                    wave += (random.nextDouble() - 0.5) * 0.015 * jitter;
                }
                // 回中力：鱼偏离中心越远，向中心的拉力越大，防止卡在边缘
                double centerPull = (0.5 - position) * 0.015;
                return wave + noise + centerPull;
            }
        }
    }

    /**
     * 突进型：不规则间隔突进 + 游荡 + 偶发停留。
     */
    private double dartBehavior(double position, long tick, double jitter) {
        // 突进间隔更不规则
        if (tick - lastDartTick > 20 + random.nextInt(60)) {
            lastDartTick = tick;
            // 突进目标有时靠近当前位置（小幅调整），有时跳到远处
            double roll = random.nextDouble();
            if (roll < 0.3 + jitter * 0.2) {
                // 远距离突进
                dartTarget = random.nextDouble(0.05, 0.95);
                dartSpeed = 0.02 + jitter * 0.04;
            } else {
                // 近距离调整
                dartTarget = position + (random.nextDouble() - 0.5) * 0.3;
                dartTarget = Math.max(0.05, Math.min(0.95, dartTarget));
                dartSpeed = 0.01 + jitter * 0.015;
            }
        }
        double diff = dartTarget - position;
        if (Math.abs(diff) < 0.02) {
            // 到达目标后立即选新目标，避免长时间停留
            lastDartTick = tick;
            double roll = random.nextDouble();
            if (roll < 0.3 + jitter * 0.2) {
                dartTarget = random.nextDouble(0.05, 0.95);
                dartSpeed = 0.02 + jitter * 0.04;
            } else {
                dartTarget = position + (random.nextDouble() - 0.5) * 0.3;
                dartTarget = Math.max(0.05, Math.min(0.95, dartTarget));
                dartSpeed = 0.01 + jitter * 0.015;
            }
            // 返回小幅抖动 + 回中力，不卡在原地
            double centerPull = (0.5 - position) * 0.01;
            return (random.nextDouble() - 0.5) * 0.006 * (1 + jitter) + centerPull;
        }
        return Math.signum(diff) * dartSpeed + (random.nextDouble() - 0.5) * 0.006 * jitter;
    }

    /**
     * 下沉型：整体下沉 + 不规则波动 + 偶发上突。
     */
    private double sinkerBehavior(double position, long tick, double jitter) {
        sinkerPhase++;
        double base = -0.002 - jitter * 0.006;
        // 双频率波动
        double periodic = Math.sin(sinkerPhase * 0.025) * 0.004 * jitter
                        + Math.sin(sinkerPhase * 0.06) * 0.002 * jitter;
        double noise = (random.nextDouble() - 0.5) * 0.003 * jitter;
        // 偶发上突（10% 概率）
        if (random.nextDouble() < 0.1 + jitter * 0.05) {
            base += 0.01 + jitter * 0.02;
        }
        if (position <= 0.02) {
            return Math.abs(base) + periodic + noise;
        }
        return base + periodic + noise;
    }

    /**
     * 上浮型：整体上浮 + 不规则波动 + 偶发下突。
     */
    private double floaterBehavior(double position, long tick, double jitter) {
        floaterPhase++;
        double base = 0.002 + jitter * 0.006;
        // 双频率波动
        double periodic = Math.sin(floaterPhase * 0.025) * 0.004 * jitter
                        + Math.sin(floaterPhase * 0.06) * 0.002 * jitter;
        double noise = (random.nextDouble() - 0.5) * 0.003 * jitter;
        // 偶发下突（10% 概率）
        if (random.nextDouble() < 0.1 + jitter * 0.05) {
            base -= 0.01 + jitter * 0.02;
        }
        if (position >= 0.98) {
            return -(base + periodic + noise);
        }
        return base + periodic + noise;
    }

    /**
     * 混合型：在 smooth/dart/sinker/floater 之间随机切换，无法预测的运动模式。
     */
    private double mixedBehavior(double position, long tick, double jitter) {
        if (tick >= nextModeSwitchTick) {
            // 随机切换到一种子行为
            int mode = random.nextInt(4);
            currentMode = mode;
            nextModeSwitchTick = tick + 40 + random.nextInt(60);
            // 重置子行为状态
            lastDartTick = tick;
            sinkerPhase = 0;
            floaterPhase = 0;
        }
        return switch (currentMode) {
            case 1 -> dartBehavior(position, tick, jitter);
            case 2 -> sinkerBehavior(position, tick, jitter);
            case 3 -> floaterBehavior(position, tick, jitter);
            default -> smoothBehavior(position, tick, jitter);
        };
    }

    /**
     * 之字型：规律性左右摆动，幅度和频率随难度增加。
     */
    private double zigzagBehavior(double position, long tick, double jitter) {
        // 主频率之字摆动
        double freq = 0.04 + jitter * 0.03;
        double amp = 0.008 + jitter * 0.006;
        double wave = Math.sin(tick * freq) * amp;
        // 叠加高频小波增加不规则性
        double ripple = Math.sin(tick * 0.12) * 0.002 * jitter;
        double noise = (random.nextDouble() - 0.5) * 0.002 * jitter;
        // 回中力防止飘到边缘
        double centerPull = (0.5 - position) * 0.008;
        return wave + ripple + noise + centerPull;
    }

    /**
     * 停滞型：长时间几乎不动，偶尔突然突进。模拟潜伏型鱼类。
     */
    private double idleBehavior(double position, long tick, double jitter) {
        if (tick >= nextModeSwitchTick) {
            // 进入突进模式
            if (currentMode == 0) {
                currentMode = 1;
                dartTarget = random.nextDouble(0.1, 0.9);
                if (Math.abs(dartTarget - position) < 0.3) {
                    dartTarget = position < 0.5
                        ? random.nextDouble(0.5, 0.9)
                        : random.nextDouble(0.1, 0.5);
                }
                dartSpeed = 0.03 + jitter * 0.04;
                nextModeSwitchTick = tick + 10 + random.nextInt(15);
            } else {
                // 回到停滞
                currentMode = 0;
                nextModeSwitchTick = tick + 60 + random.nextInt(80);
            }
        }
        if (currentMode == 1) {
            double diff = dartTarget - position;
            if (Math.abs(diff) < 0.03) {
                currentMode = 0;
                nextModeSwitchTick = tick + 60 + random.nextInt(80);
                return (random.nextDouble() - 0.5) * 0.002;
            }
            return Math.signum(diff) * dartSpeed;
        }
        // 停滞：微小抖动
        return (random.nextDouble() - 0.5) * 0.001 * (1 + jitter);
    }
}
