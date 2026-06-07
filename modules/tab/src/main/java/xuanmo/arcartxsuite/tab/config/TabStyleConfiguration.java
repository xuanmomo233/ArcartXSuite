package xuanmo.arcartxsuite.tab.config;

/**
 * 全局视觉风格配置（settings.style.*）。
 *
 * <p>当前仅保留 PVP 检测相关字段，其余样式字段已随 PAPI 扩展移除而失效。
 *
 * @param pvpEnabled   是否启用 pvp-highlight
 * @param pvpWindowMs  玩家最近一次参与 PVP 的有效窗口（毫秒）
 */
public record TabStyleConfiguration(
    boolean pvpEnabled,
    long pvpWindowMs
) {

    public static TabStyleConfiguration defaults() {
        return new TabStyleConfiguration(false, 5_000L);
    }
}
