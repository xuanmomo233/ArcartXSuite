package xuanmo.arcartxsuite.mail.model;

import java.math.BigDecimal;
import java.util.Map;

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
    public static MailSendQuote failure(String message, String feeCurrencyId) {
        return new MailSendQuote(false, message, feeCurrencyId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Map.of(), Map.of());
    }
}
