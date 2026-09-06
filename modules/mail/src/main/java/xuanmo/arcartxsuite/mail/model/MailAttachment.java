package xuanmo.arcartxsuite.mail.model;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 邮件附件，表示一封邮件中携带的物品或货币附件。
 * <p>
 * 关联模块：mail。附件类型由 {@link MailAttachmentType} 区分。
 *
 * @param id          附件唯一 ID
 * @param sortOrder   排序序号
 * @param type        附件类型
 * @param itemData    物品序列化数据（仅 {@link MailAttachmentType#ITEM} 时有效）
 * @param currencyId  货币 ID（仅货币类附件有效）
 * @param amount      数量
 * @param description 附件展示描述
 * @param icon        货币附件的图标定义（支持 material / custom-model-data / texture 等，与 menu 模块 IconDefinition 一致），物品附件为 null
 */
public record MailAttachment(
    long id,
    int sortOrder,
    MailAttachmentType type,
    String itemData,
    String currencyId,
    double amount,
    String description,
    @Nullable IconDefinition icon
) {
    /**
     * 判断该附件是否为物品类型。
     *
     * @return {@code true} 表示物品附件
     */
    public boolean isItem() {
        return type == MailAttachmentType.ITEM;
    }

    /**
     * 判断该附件是否为货币类型（含 Vault）。
     *
     * @return {@code true} 表示货币附件
     */
    public boolean isCurrency() {
        return type == MailAttachmentType.CURRENCY || type == MailAttachmentType.VAULT;
    }

    /**
     * 返回规范化（小写、去空白）的货币 ID，空白时默认 {@code "money"}。
     *
     * @return 规范化货币 ID
     */
    public String normalizedCurrencyId() {
        if (currencyId == null || currencyId.isBlank()) {
            return "money";
        }
        return currencyId.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 返回该附件的 Vault 货币金额，仅当货币 ID 为 {@code "money"} 时有效。
     *
     * @return Vault 金额，非 money 货币返回 {@code 0.0}
     */
    public double vaultAmount() {
        return "money".equals(normalizedCurrencyId()) ? amount : 0.0D;
    }
}
