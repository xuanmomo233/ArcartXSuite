package xuanmo.arcartxsuite.prop.config;

import java.util.Locale;

/**
 * Prop 模块自动触发全局配置。
 * <p>
 * 从 {@code ArcartXProp.yml} 的 {@code auto-trigger} 节解析。服务端按固定间隔轮询在线玩家的
 * ArcartX 快捷槽，对满足触发条件的道具自动执行使用流程。
 *
 * @param enabled       自动触发总开关
 * @param intervalTicks 全局轮询间隔（tick），同时也是道具 {@code auto.interval} 的实际粒度下限
 * @param mode          同一轮多个槽位道具满足触发条件时的触发模式
 */
public record PropAutoTriggerConfiguration(
    boolean enabled,
    int intervalTicks,
    Mode mode
) {

    /**
     * 同轮多道具满足条件时的触发模式。
     */
    public enum Mode {
        /** 全部满足条件的道具都尝试触发（各自受冷却限制）。 */
        ALL,
        /** 按槽位顺序，第一个成功触发后停止本轮。 */
        FIRST;

        /**
         * 解析配置字符串为模式，无法识别时回退 {@link #ALL}。
         *
         * @param raw 配置原文
         * @return 解析后的模式
         */
        public static Mode parse(String raw) {
            if (raw != null && "first".equals(raw.trim().toLowerCase(Locale.ROOT))) {
                return FIRST;
            }
            return ALL;
        }
    }
}
