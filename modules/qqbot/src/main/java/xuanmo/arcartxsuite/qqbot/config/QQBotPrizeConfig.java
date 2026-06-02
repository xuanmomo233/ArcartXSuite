package xuanmo.arcartxsuite.qqbot.config;

/**
 * 积分兑换商店中的单个奖品配置。
 * 兑换后通过邮件系统将奖品发放给绑定玩家。
 */
public record QQBotPrizeConfig(
    String id,
    String name,
    int cost,
    String mailPresetId,
    String description,
    int limitPerDay,
    boolean requireBind,
    double discountRate,
    long discountUntil
) {
    /** 是否有每日限购 */
    public boolean hasDailyLimit() {
        return limitPerDay > 0;
    }

    /** 当前是否处于折扣期 */
    public boolean isDiscountActive() {
        if (discountRate <= 0 || discountRate >= 1.0) return false;
        if (discountUntil <= 0) return false;
        return System.currentTimeMillis() < discountUntil;
    }

    /** 计算当前实际价格 */
    public int currentCost() {
        if (!isDiscountActive()) return cost;
        return Math.max(1, (int) Math.ceil(cost * discountRate));
    }
}
