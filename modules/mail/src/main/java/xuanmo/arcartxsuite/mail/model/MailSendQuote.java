package xuanmo.arcartxsuite.mail.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 发送邮件报价，封装玩家发送邮件时的手续费、附件金额与税费明细。
 * <p>
 * 关联模块：mail。由 {@link xuanmo.arcartxsuite.mail.service.MailService#calculateComposeQuote} 计算。
 *
 * @param success            报价是否有效
 * @param message            提示消息（如失败原因）
 * @param feeCurrencyId      手续费货币 ID
 * @param baseFee            基础手续费
 * @param itemFee            物品附件手续费
 * @param totalFee           总手续费
 * @param attachmentAmounts  各附件货币金额映射
 * @param attachmentTaxes    各附件税费映射
 */
public record MailSendQuote(
    boolean success,
    String message,
    String feeCurrencyId,
    BigDecimal baseFee,
    BigDecimal itemFee,
    BigDecimal totalFee,
    Map<String, BigDecimal> attachmentAmounts,
    Map<String, BigDecimal> attachmentTaxes
) {
    /**
     * 构造失败的报价。
     *
     * @param message       失败原因
     * @param feeCurrencyId 手续费货币 ID
     * @return 失败报价
     */
    public static MailSendQuote failure(String message, String feeCurrencyId) {
        return new MailSendQuote(false, message, feeCurrencyId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Map.of(), Map.of());
    }
}
