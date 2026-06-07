package xuanmo.arcartxsuite.market.storage;

import java.util.UUID;

/**
 * 待发放记录：用于离线补发或背包溢出补发。
 * <p>
 * 当拍卖结算 / 退款 / 退还物品时收件人离线，或在线但背包装不下，
 * 相应的物品或货币会写入此队列，待玩家上线时在主线程统一发放，
 * 从根本上避免"钱货两失"。
 */
public record PendingDelivery(
    long id,
    UUID player,
    String type,
    String itemData,
    String currency,
    double amount,
    String reason,
    long createdAt
) {

    /** 物品补发 */
    public static final String TYPE_ITEM = "ITEM";

    /** 货币补发 */
    public static final String TYPE_CURRENCY = "CURRENCY";

    public boolean isItem() {
        return TYPE_ITEM.equals(type);
    }

    public boolean isCurrency() {
        return TYPE_CURRENCY.equals(type);
    }
}
