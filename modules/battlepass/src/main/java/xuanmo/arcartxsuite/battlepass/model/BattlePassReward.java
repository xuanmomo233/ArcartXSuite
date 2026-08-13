package xuanmo.arcartxsuite.battlepass.model;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 战令奖励 record，描述某个等级与档位对应的具体奖励内容。
 * <p>
 * 支持三种奖励类型：
 * <ul>
 *   <li>{@code item} — 通过 {@link BattlePassItemDefinition} 生成物品并直接放入玩家背包</li>
 *   <li>{@code command} — 以控制台身份执行命令列表，支持 {@code {player}} 占位符</li>
 *   <li>{@code currency} — 向玩家发放指定货币</li>
 * </ul>
 * 每个奖励可携带 {@link IconDefinition} 用于 UI 图标显示。
 *
 * @param rewardId  奖励唯一标识
 * @param level     所属等级
 * @param tier      奖励档位（FREE/PREMIUM/DELUXE）
 * @param type      奖励类型（item / command / currency）
 * @param icon      UI 显示图标定义，可为 null
 * @param item      物品定义（type: item 时使用），可为 null
 * @param commands  命令列表（type: command 时使用），可为空列表
 * @param currency  货币 ID（type: currency 时使用），可为 null
 * @param currencyAmount 货币数量（type: currency 时使用）
 */
public record BattlePassReward(
    String rewardId,
    int level,
    RewardTier tier,
    String type,
    @Nullable IconDefinition icon,
    @Nullable BattlePassItemDefinition item,
    List<String> commands,
    @Nullable String currency,
    double currencyAmount
) {

    /**
     * 奖励档位枚举，与战令档位（{@link BattlePassPlayerProgress.PassTier}）对应。
     */
    public enum RewardTier {
        FREE, PREMIUM, DELUXE
    }
}
