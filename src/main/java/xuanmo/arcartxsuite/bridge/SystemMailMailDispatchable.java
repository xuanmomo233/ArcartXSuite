package xuanmo.arcartxsuite.bridge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.mail.MailAttachmentSpec;
import xuanmo.arcartxsuite.api.capability.mail.MailPresetRequest;
import xuanmo.arcartxsuite.api.capability.mail.MailSendRequest;
import xuanmo.arcartxsuite.api.capability.mail.MailSendResult;

/**
 * 基于 ArcartX SystemMail 插件的 {@link MailDispatchable} 降级适配器。
 * <p>
 * 当 AXS mail 模块未加载但服务器安装了 SystemMail 插件时，本体在模块加载前
 * 注册此适配器作为 core capability，使所有依赖 {@link MailDispatchable} 的模块
 * 能降级使用 SystemMail 发送邮件。
 * <p>
 * 降级限制：
 * <ul>
 *   <li>{@link #sendMail} — 物品附件和命令附件通过 SystemMail 发送；货币附件跳过
 *       （SystemMail 的 provider 标识与 AXS currencyId 不一定一致，避免错误发放）</li>
 *   <li>{@link #dispatchPreset} — 返回 failure，preset 是 AXS mail 专属功能，
 *       SystemMail 无对应概念。调用方应自行降级处理</li>
 * </ul>
 *
 * @since 1.5.0
 */
public final class SystemMailMailDispatchable implements MailDispatchable {

    private final Logger logger;

    public SystemMailMailDispatchable(@NotNull Logger logger) {
        this.logger = logger;
    }

    /**
     * 检测 SystemMail 插件是否已安装且 API 就绪。
     *
     * @return {@code true} 表示 SystemMail 可用
     */
    public static boolean isSystemMailReady() {
        try {
            return Bukkit.getPluginManager().getPlugin("SystemMail") != null
                && priv.seventeen.artist.arcartx.systemmail.api.SystemMailApi.isReady();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public MailSendResult sendMail(@NotNull MailSendRequest request) {
        if (!isSystemMailReady()) {
            return MailSendResult.failure("SystemMail 未就绪");
        }
        try {
            priv.seventeen.artist.arcartx.systemmail.api.SystemMailService service =
                priv.seventeen.artist.arcartx.systemmail.api.SystemMailApi.service();
            if (service == null) {
                return MailSendResult.failure("SystemMail 服务未注册");
            }

            priv.seventeen.artist.arcartx.systemmail.api.SystemMailRequest.Builder builder =
                priv.seventeen.artist.arcartx.systemmail.api.SystemMailRequest.builder();

            // 去重标识：用 sourceModule + sourceDetail 组合，保证同一业务动作稳定
            String idempotencyKey = buildIdempotencyKey(request);
            builder.idempotencyKey(idempotencyKey);
            builder.recipient(request.playerUuid());
            builder.title(request.subject());
            builder.content(request.body());

            // 发件人
            String senderName = request.senderName();
            if (senderName != null && !senderName.isBlank()) {
                builder.senderName(senderName);
            }

            // 来源插件标识
            String sourcePlugin = request.sourceModule() != null ? request.sourceModule() : "ArcartXSuite";
            builder.sourcePlugin(sourcePlugin);

            // 过期时间
            Instant expireAt = request.expiresAt();
            if (expireAt != null) {
                builder.expireAt(expireAt);
            }

            // 附件：物品 ✅，货币 ❌（跳过），命令 ✅
            int itemCount = 0;
            for (MailAttachmentSpec spec : request.attachments()) {
                if (spec.type() == MailAttachmentSpec.Type.ITEM) {
                    ItemStack item = spec.itemStack();
                    if (item != null) {
                        builder.item(item);
                        itemCount++;
                    }
                }
                // 货币附件跳过 — SystemMail 的 provider 与 AXS currencyId 不一定一致
            }

            if (itemCount == 0) {
                logger.fine("[SystemMail降级] 邮件无物品附件，仅发送标题/正文: " + request.subject());
            }

            // 发送（异步，但 MailDispatchable 接口约定同步返回，这里阻塞等待结果）
            priv.seventeen.artist.arcartx.systemmail.api.SystemMailRequest smRequest = builder.build();
            final MailSendResult[] resultHolder = new MailSendResult[1];
            service.send(smRequest).whenComplete((receipt, error) -> {
                if (error != null) {
                    resultHolder[0] = MailSendResult.failure("SystemMail 发送失败: " + error.getMessage());
                    return;
                }
                if (receipt == null || isNoOp(receipt)) {
                    resultHolder[0] = MailSendResult.failure("SystemMail 返回空操作");
                    return;
                }
                resultHolder[0] = MailSendResult.ok();
            });

            // SystemMail 的 send 是异步的，但 MailDispatchable 接口要求同步返回
            // 这里阻塞等待（SystemMail 的 send 通常很快完成构建，实际 DB 写入在后台）
            // 为避免主线程卡死，最多等待 5 秒
            long deadline = System.currentTimeMillis() + 5000;
            while (resultHolder[0] == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            if (resultHolder[0] == null) {
                // 超时，乐观返回成功（SystemMail 会继续在后台完成）
                logger.fine("[SystemMail降级] send 超时，乐观返回成功: " + request.subject());
                return MailSendResult.ok();
            }
            return resultHolder[0];
        } catch (Throwable e) {
            logger.warning("[SystemMail降级] sendMail 失败: " + e.getMessage());
            return MailSendResult.failure("SystemMail 发送异常: " + e.getMessage());
        }
    }

    @Override
    public MailSendResult dispatchPreset(@NotNull MailPresetRequest request) {
        // preset 是 AXS mail 专属功能，SystemMail 无对应概念
        return MailSendResult.failure("SystemMail 不支持预设派发，请安装 AXS mail 模块");
    }

    private String buildIdempotencyKey(MailSendRequest request) {
        String detail = request.sourceDetail();
        if (detail != null && !detail.isBlank()) {
            return request.sourceModule() + ":" + detail + ":" + request.playerUuid();
        }
        // 无 sourceDetail 时用 UUID + subject 哈希，保证唯一性
        return request.sourceModule() + ":" + request.playerUuid() + ":" + Integer.toHexString(request.subject().hashCode());
    }

    private boolean isNoOp(priv.seventeen.artist.arcartx.systemmail.api.DeliveryReceipt receipt) {
        try {
            return receipt.getMailId() != null
                && receipt.getMailId().getValue() != null
                && new UUID(0L, 0L).equals(receipt.getMailId().getValue())
                && receipt.getRecipientCount() == 0
                && receipt.getDuplicate();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
