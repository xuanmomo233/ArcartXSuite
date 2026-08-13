package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 邮件发送能力接口。
 * <p>
 * 由 Mail 模块实现，供 EventPacket 等模块跨模块调用。
 */
public interface MailDispatchable {

    /**
     * 按预设模板发送邮件。
     *
     * @param presetId   预设 id
     * @param playerName 收件人名
     * @param source     来源标识（如 "EventPacket"）
     * @return {@code true} 表示发送成功
     */
    boolean dispatchPreset(@NotNull String presetId, @NotNull String playerName, @NotNull String source);

    /**
     * 发送一封附带物品的系统邮件。
     * <p>
     * 收件人无需在线，邮件存入收件箱，玩家上线后查看领取附件。
     * Mail 模块未启用或不支持动态物品附件时返回 {@code false}，调用方应自行降级处理。
     *
     * @param playerUuid 收件人 UUID
     * @param subject    邮件主题
     * @param body       邮件正文
     * @param item       附件物品（不可为空）
     * @param senderName 发件人显示名（如 "拍卖行"），null 时默认 "System"
     * @return {@code true} 表示发送成功
     */
    default boolean sendItemMail(@NotNull UUID playerUuid,
                                 @NotNull String subject,
                                 @NotNull String body,
                                 @NotNull ItemStack item,
                                 @Nullable String senderName) {
        return false;
    }
}
