package xuanmo.arcartxsuite.fishing.minigame;

import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.script.AriaBridge;

/**
 * 钓鱼进度曲线模型，通过 ARIA 表达式定义进度随当前进度值变化的增长/衰减规律。
 * <p>
 * 关联模块：{@code fishing}。管理员在 yml 中编写 ARIA 公式，可访问以下变量
 * （绑定到 ARIA {@code global.} 命名空间，公式中需用 {@code global.} 前缀访问）：
 * <ul>
 *   <li>{@code global.progress} — 当前进度值 [0, 1]。gain 用当前进度，drain 用 (1 - progress)（对称处理）。</li>
 *   <li>{@code global.base_rate} — 基础速率（即 base-rate 配置值），方便整体缩放。</li>
 *   <li>{@code global.level} — 玩家钓鱼等级（整数）。</li>
 *   <li>{@code global.difficulty} — 鱼调整后难度（1~100）。</li>
 * </ul>
 * 表达式返回值 = 本 tick 的基础进度变化量，再由调用方乘以等级修正、天赋加成、streak 加成（仅 gain）。
 * Aria 不可用或求值失败时回退为线性（返回 baseRate）。
 *
 * @param baseRate 基础速率（每 tick 的基础进度变化量，如 0.01 = 每 tick 1%），同时作为公式中 global.base_rate 变量的绑定值和回退值
 * @param formula  ARIA 表达式（非空，可访问 global.progress/global.base_rate/global.level/global.difficulty）
 */
public record ProgressCurve(
    double baseRate,
    @NotNull String formula
) {

    /** 默认 gain 曲线：S 曲线模型，用 cos 实现连续平滑加速（0.6x→1.0x→1.4x）。 */
    public static ProgressCurve defaultGain() {
        return new ProgressCurve(0.01, "global.base_rate * (0.6 + 0.8 * (0.5 - 0.5 * math.cos(global.progress * math.PI())))");
    }

    /** 默认 drain 曲线：U 型模型，低进度和高进度 drain 都大，中进度稍缓和。(0.588x→0.85x→0.85x) × 难度系数。 */
    public static ProgressCurve defaultDrain() {
        return new ProgressCurve(0.025, "global.base_rate * (1 + global.difficulty / 90) * (0.5 + 0.25 * global.progress + 0.35 * (1 - global.progress) * (1 - global.progress))");
    }

    /**
     * 计算当前 tick 的进度变化量。
     * <p>
     * 对于 gain，progress 为当前进度（0~1），返回正值表示进度增加量。
     * 对于 drain，使用对称处理：以 (1 - progress) 代入公式的 progress 变量，
     * 使进度高时衰减慢、进度低时衰减快。
     *
     * @param progress   当前进度 [0, 1]
     * @param isGain     true = 计算 gain，false = 计算 drain
     * @param ariaBridge Aria 桥接（可为 null，不可用时回退为 baseRate）
     * @param level      玩家钓鱼等级
     * @param difficulty 鱼调整后难度（1~100）
     * @return 本 tick 的进度变化量（非负值）
     */
    public double evaluate(double progress, boolean isGain,
                           @Nullable AriaBridge ariaBridge, int level, int difficulty) {
        if (ariaBridge == null || !ariaBridge.available()) {
            return baseRate; // Aria 不可用时回退为线性
        }
        // L6: 输入验证，防止 NaN/Infinity 传入 ARIA 公式
        if (Double.isNaN(progress) || Double.isInfinite(progress)) {
            progress = isGain ? 0.0 : 1.0;
        }
        double clampedProgress = Math.max(0.0, Math.min(1.0, progress));
        double p = isGain ? clampedProgress : (1.0 - clampedProgress);
        try {
            Object result = ariaBridge.eval(formula, Map.of(
                "progress", p,
                "base_rate", baseRate,
                "level", Math.max(0, level),
                "difficulty", Math.max(0, Math.min(100, difficulty))
            ));
            if (result instanceof Number n) {
                double val = n.doubleValue();
                if (Double.isNaN(val) || Double.isInfinite(val)) return baseRate;
                return val >= 0 ? val : 0; // 不允许负值（负 gain 会导致进度倒退）
            }
            return baseRate;
        } catch (Exception e) {
            return baseRate; // 求值失败回退为线性
        }
    }
}
