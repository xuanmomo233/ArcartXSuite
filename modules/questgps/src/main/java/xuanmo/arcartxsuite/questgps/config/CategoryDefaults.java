package xuanmo.arcartxsuite.questgps.config;

/**
 * 任务分类全局策略：只使用一种数据源，见 {@link CategorySource}。
 */
public record CategoryDefaults(CategorySource source) {

    public static CategoryDefaults defaults() {
        return new CategoryDefaults(CategorySource.CHEMDAH);
    }
}
