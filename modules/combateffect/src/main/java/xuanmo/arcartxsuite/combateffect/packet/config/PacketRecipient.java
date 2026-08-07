package xuanmo.arcartxsuite.combateffect.packet.config;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * 数据包接收者类型枚举。
 * <p>
 * 属于 combateffect 模块的 packet 子模块，定义战斗数据包的接收方：
 * {@link #ATTACKER} 表示攻击者，{@link #TARGET} 表示目标（仅当目标为玩家时有效）。
 */
public enum PacketRecipient {
    ATTACKER("attacker"),
    TARGET("target");

    private final String configValue;

    PacketRecipient(String configValue) {
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
     * 根据接收者类型从攻击者和目标中解析出实际接收玩家。
     *
     * @param attacker 攻击者玩家
     * @param target   目标实体
     * @return 接收玩家，当目标非玩家且类型为 {@link #TARGET} 时返回 {@code null}
     */
    public Player resolve(Player attacker, LivingEntity target) {
        return switch (this) {
            case ATTACKER -> attacker;
            case TARGET -> target instanceof Player p ? p : null;
        };
    }

    /**
     * 从配置字符串解析接收者类型，大小写不敏感。
     *
     * @param value 配置字符串
     * @return 对应的接收者类型，无法识别时返回 {@code null}
     */
    public static PacketRecipient fromConfig(String value) {
        if (value == null) {
            return null;
        }
        for (PacketRecipient recipient : values()) {
            if (recipient.configValue.equalsIgnoreCase(value.trim())) {
                return recipient;
            }
        }
        return null;
    }
}
