package xuanmo.arcartxsuite.fishing.reward;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 赛季等级奖励定义。
 *
 * @param rewardId    奖励唯一标识
 * @param level       达到该等级可领取
 * @param type        奖励类型：item / command / currency / mail
 * @param icon        UI 显示图标定义（与 menu/market 的 icon 用法一致），可为 null
 * @param item        物品定义（type=item 时有效，使用 IconDefinition 结构化字段），可为 null
 * @param currencyId  货币 ID（type=currency 时有效）
 * @param currencyAmount 货币数量（type=currency 时有效）
 * @param commands    命令列表（type=command 时有效）
 * @param mailPresets 邮件预设 ID 列表（type=mail 时有效）
 */
public record SeasonRewardDefinition(
    @NotNull String rewardId,
    int level,
    @NotNull String type,
    @Nullable IconDefinition icon,
    @Nullable IconDefinition item,
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
