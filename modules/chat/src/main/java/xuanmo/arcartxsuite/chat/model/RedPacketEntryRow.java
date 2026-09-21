package xuanmo.arcartxsuite.chat.model;

import java.math.BigDecimal;
import org.jetbrains.annotations.Nullable;

/**
 * 奖励池红包的数据库条目行，对应 {@code chat_red_packet_entries} 表的一行。
 * <p>
 * 奖励内容在红包创建时快照写入该表，领取时即使预设文件变更也不影响已发出的红包。
 *
 * @param entryIndex  条目序号（池内下标）
 * @param reward      奖励内容快照
 * @param weight      抽取权重
 * @param remaining   剩余可中次数；{@code -1} 表示不限（有放回且未设上限）
 */
public record RedPacketEntryRow(
    int entryIndex,
    RedPacketReward reward,
    int weight,
    int remaining
) {

    /**
     * 条目是否仍可被抽中。
     */
    public boolean drawable() {
        return remaining != 0;
    }

    /**
     * 构造货币条目行。
     */
    public static RedPacketEntryRow of(int index, RedPacketReward reward, int weight, int remaining) {
        return new RedPacketEntryRow(index, reward, Math.max(1, weight), remaining);
    }
}
