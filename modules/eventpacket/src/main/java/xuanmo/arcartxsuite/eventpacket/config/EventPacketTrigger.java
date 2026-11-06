package xuanmo.arcartxsuite.eventpacket.config;

/**
 * 事件包触发器类型枚举。
 * <p>
 * 定义所有支持的事件触发来源，包括玩家进退服、PAPI 占位符变化、
 * 击杀计数、命令信号、客户端回包、Chemdah 任务事件以及脚本触发等。
 */
public enum EventPacketTrigger {
    ADMITTED("admitted"),
    FIRST_ADMITTED("first-admitted"),
    QUIT("quit"),
    PAPI_INCREASE("papi-increase"),
    PAPI_DECREASE("papi-decrease"),
    PAPI_THRESHOLD("placeholder-threshold"),
    MOB_KILL_COUNT("mob-kill-count"),
    COMMAND_SIGNAL("command-signal"),
    CLIENT_PACKET("client-packet"),
    QUEST_ACCEPT("quest-accept"),
    QUEST_COMPLETE("quest-complete"),
    QUEST_FAIL("quest-fail"),
    OBJECTIVE_COMPLETE("objective-complete"),
    OBJECTIVE_CONTINUE("objective-continue"),
    OBJECTIVE_RESTART("objective-restart"),
    CHEMDAH_LEVEL_CHANGE("chemdah-level-change"),
    SCRIPT_JS("script-js"),
    SCRIPT_ARIA("script-aria");

    private final String configValue;

    EventPacketTrigger(String configValue) {
        this.configValue = configValue;
    }

    /**
     * 返回此触发器在配置文件中使用的值。
     *
     * @return 配置值字符串
     */
    public String configValue() {
        return configValue;
    }

    /**
     * 判断此触发器是否属于 PAPI（PlaceholderAPI）类型。
     *
     * @return 是 PAPI 触发器返回 {@code true}
     */
    public boolean papiTrigger() {
        return this == PAPI_INCREASE || this == PAPI_DECREASE || this == PAPI_THRESHOLD;
    }

    /**
     * 判断此触发器是否属于 Chemdah 任务相关类型。
     *
     * @return 是 Chemdah 触发器返回 {@code true}
     */
    public boolean chemdahTrigger() {
        return this == QUEST_ACCEPT || this == QUEST_COMPLETE || this == QUEST_FAIL
            || this == OBJECTIVE_COMPLETE || this == OBJECTIVE_CONTINUE || this == OBJECTIVE_RESTART
            || this == CHEMDAH_LEVEL_CHANGE;
    }

    /**
     * 判断此触发器是否支持按信号过滤。
     *
     * @return 可按信号过滤返回 {@code true}
     */
    public boolean signalFilterable() {
        return this == COMMAND_SIGNAL || chemdahTrigger();
    }

    /**
     * 判断此触发器是否属于脚本类型（JS / ARIA）。
     *
     * @return 是脚本触发器返回 {@code true}
     */
    public boolean scriptTrigger() {
        return this == SCRIPT_JS || this == SCRIPT_ARIA;
    }

    /**
     * 判断此触发器是否属于 Chemdah 目标（objective）类型。
     *
     * @return 是目标触发器返回 {@code true}
     */
    public boolean isObjectiveTrigger() {
        return this == OBJECTIVE_COMPLETE || this == OBJECTIVE_CONTINUE || this == OBJECTIVE_RESTART;
    }

    /**
     * 从配置字符串解析触发器类型，支持别名兼容。
     *
     * @param raw 原始配置值
     * @return 解析得到的触发器枚举，无法识别时返回 {@code null}
     */
    public static EventPacketTrigger parse(String raw) {
        if (raw == null) {
            return null;
        }

        String normalized = raw.trim().toLowerCase().replace('_', '-');
        if ("placeholder-increase".equals(normalized)) {
            return PAPI_INCREASE;
        }
        if ("placeholder-decrease".equals(normalized)) {
            return PAPI_DECREASE;
        }
        for (EventPacketTrigger value : values()) {
            if (value.configValue.equals(normalized)) {
                return value;
            }
        }
        return null;
    }
}
