package xuanmo.arcartxsuite.mail.model;

import java.util.Locale;

public record MailAttachment(
    long id,
    int sortOrder,
    MailAttachmentType type,
    String itemData,
    String currencyId,
    double amount,
    String description
) {
    public boolean isItem() {
        return type == MailAttachmentType.ITEM;
    }

    public boolean isCurrency() {
        return type == MailAttachmentType.CURRENCY || type == MailAttachmentType.VAULT;
    }

    public String normalizedCurrencyId() {
        if (currencyId == null || currencyId.isBlank()) {
            return "money";
        }
        return currencyId.trim().toLowerCase(Locale.ROOT);
    }

    public double vaultAmount() {
        return "money".equals(normalizedCurrencyId()) ? amount : 0.0D;
    }
}
