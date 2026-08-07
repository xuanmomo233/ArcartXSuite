package xuanmo.arcartxsuite.battlepass.model;

import java.time.LocalDate;

/**
 * 战令赛季 record，定义一个赛季的基本参数与时间范围。
 *
 * @param seasonId    赛季唯一标识
 * @param displayName 赛季显示名称
 * @param maxLevel    赛季最大等级
 * @param xpPerLevel  每级所需经验值
 * @param startDate   赛季开始日期
 * @param endDate     赛季结束日期
 */
public record BattlePassSeason(
    String seasonId,
    String displayName,
    int maxLevel,
    int xpPerLevel,
    LocalDate startDate,
    LocalDate endDate
) {

    /**
     * 判断当前日期是否在赛季有效时间范围内。
     *
     * @return {@code true} 表示赛季正在进行中
     */
    public boolean isActive() {
        LocalDate now = LocalDate.now();
        return !now.isBefore(startDate) && !now.isAfter(endDate);
    }

    /**
     * 返回升到指定等级所需的经验值。
     *
     * @param level 目标等级
     * @return 所需经验值，等级 ≤ 1 时返回 0
     */
    public int xpRequiredForLevel(int level) {
        if (level <= 1) return 0;
        return xpPerLevel;
    }
}
