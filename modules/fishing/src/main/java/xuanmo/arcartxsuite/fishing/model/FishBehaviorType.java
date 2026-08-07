package xuanmo.arcartxsuite.fishing.model;

/**
 * 鱼类行为类型枚举，决定小游戏中鱼图标的运动模式。
 * <p>
 * 关联模块：{@code fishing}。由 {@link xuanmo.arcartxsuite.fishing.minigame.FishBehaviorEngine} 使用。
 */
public enum FishBehaviorType {
    SMOOTH,
    DART,
    SINKER,
    FLOATER
}
