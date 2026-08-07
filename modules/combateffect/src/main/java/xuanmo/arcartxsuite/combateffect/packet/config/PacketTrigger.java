package xuanmo.arcartxsuite.combateffect.packet.config;

/**
 * 数据包触发器类型枚举。
 * <p>
 * 属于 combateffect 模块的 packet 子模块，定义战斗数据包的触发时机：
 * 击杀（KILL）、攻击（ATTACK）、死亡（DEATH）、连击（COMBO）、
 * 手动（MANUAL）、按键绑定（KEYBIND）、状态切换（STATE）、控制器（CONTROLLER）。
 */
public enum PacketTrigger {
    KILL("kill"),
    ATTACK("attack"),
    DEATH("death"),
    COMBO("combo"),
    MANUAL("manual"),
    KEYBIND("keybind"),
    STATE("state"),
    CONTROLLER("controller");

    private final String configValue;

    PacketTrigger(String configValue) {
        this.configValue = configValue;
    }

    /**
     * 返回配置文件中使用的字符串标识。
     *
     * @return 配置标识
     */
    public String configValue() {
        return configValue;
    }

    /**
     * 从配置字符串解析触发器类型，大小写不敏感。
     *
     * @param value 配置字符串
     * @return 对应的触发器类型，无法识别时返回 {@code null}
     */
    public static PacketTrigger fromConfig(String value) {
        if (value == null) {
            return null;
        }
        for (PacketTrigger trigger : values()) {
            if (trigger.configValue.equalsIgnoreCase(value.trim())) {
                return trigger;
            }
        }
        return null;
    }
}
