package xuanmo.arcartxsuite.chat.model;

/**
 * 奖励池条目，预设红包 {@code rewards} 列表中的单个奖项。
 * <p>
 * 无放回模式（replacement=false）：{@code quantity} 表示该奖项在池中的份数，抽中即消耗一份。
 * 有放回模式（replacement=true）：{@code quantity} 表示该奖项的全局最大中奖次数（0/负数=不限）。
 *
 * @param reward   奖项内容
 * @param weight   抽取权重（≥1）
 * @param quantity 无放回时为池内份数；有放回时为最大中奖次数
 */
public record RedPacketPoolEntry(
    RedPacketReward reward,
    int weight,
    int quantity
) {
}
