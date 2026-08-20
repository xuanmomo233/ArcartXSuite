package xuanmo.arcartxsuite.fishing.reward;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 赛季等级奖励定义。
 *
 * @param rewardId    奖励唯一标识
 * @param level       达到该等级可领取
 * @param type        奖励类型：item / command / currency / mail
 * @param iconMaterial 图标材质
 * @param iconName    图标显示名称
 * @param iconAmount  图标显示数量
 * @param source      物品来源（type=item 时有效）
 * @param id          物品 ID（type=item 时有效）
 * @param amount      物品数量（type=item 时有效）
 * @param nbt         物品 NBT（type=item 时有效）
 * @param currencyId  货币 ID（type=currency 时有效）
 * @param currencyAmount 货币数量（type=currency 时有效）
 * @param commands    命令列表（type=command 时有效）
 * @param mailPresets 邮件预设 ID 列表（type=mail 时有效）
 */
public record SeasonRewardDefinition(
    @NotNull String rewardId,
    int level,
    @NotNull String type,
    @NotNull String iconMaterial,
    @NotNull String iconName,
    int iconAmount,
    @Nullable String source,
    @Nullable String id,
    int amount,
    @Nullable String nbt,
    @Nullable String currencyId,
    double currencyAmount,
    @Nullable List<String> commands,
    @Nullable List<String> mailPresets
) {

    public enum Type {
        ITEM("item"),
        COMMAND("command"),
        CURRENCY("currency"),
        MAIL("mail");

        private final String key;

        Type(String key) { this.key = key; }

        public String key() { return key; }

        public static Type fromKey(String key) {
            for (Type t : values()) {
                if (t.key.equals(key)) return t;
            }
            return ITEM;
        }
    }
}
