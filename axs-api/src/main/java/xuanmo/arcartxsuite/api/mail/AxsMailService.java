package xuanmo.arcartxsuite.api.mail;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.AxsCapabilities;
import xuanmo.arcartxsuite.api.bridge.ApiStability;
import xuanmo.arcartxsuite.api.capability.PublicCapability;

/**
 * 邮件公开服务接口，面向外部 Bukkit 插件（公开 capability）。
 * <p>
 * 所有方法均返回 {@link CompletableFuture}，异步执行，不阻塞调用线程。
 * 调用方不应在 Bukkit 主线程对返回值调用 {@code join()} 或 {@code get()}，
 * 应使用 {@code thenAccept}、{@code whenComplete} 等回调处理结果。
 * <p>
 * 获取方式（唯一入口）：
 * <pre>{@code
 * AxsMailService service = AxsCapabilities.get(AxsMailService.class);
 * if (service == null) return; // mail 模块未启用
 * }</pre>
 * AXS 模块内部通过 {@code getCapability(AxsMailService.class)} 获取同一实例。
 *
 * @since 1.5.0
 */
@ApiStability.Stable
@PublicCapability
public interface AxsMailService {

    /** 邮件是否写入 Suite 宿主共享 MySQL，供跨服持久规则检查存储一致性。 */
    boolean usesSharedHostStorage();

    /**
     * 发送一封系统邮件。
     * <p>
     * 收件人无需在线，邮件存入收件箱，玩家上线后查看领取附件。
     * 按来源插件、{@link AxsMailRequest#idempotencyKey()} 和收件人去重。
     * 同一收件人的邮件、附件和幂等回执在同一事务内提交，重复请求返回原邮件 ID。
     * 多收件人分别提交，部分失败时可使用原请求重试，仅补发尚未成功的收件人。
     *
     * @param request 邮件发送请求
     * @return 异步回执，{@link AxsDeliveryReceipt#isNoOp()} 为 true 表示模块未就绪
     */
    @ApiStability.Stable
    @NotNull CompletableFuture<AxsDeliveryReceipt> send(@NotNull AxsMailRequest request);

    /**
     * 恢复持久化的邮件发送意图。
     * <p>
     * 与 {@link #send} 使用相同的事务和去重语义，直接尝试入库。
     * 定时触发由调用方安排，{@code deliverAt} 不会延迟本次调用。
     * 回执确认邮件入库，不代表玩家已领取附件。
     *
     * @param request 邮件发送请求
     * @return 异步回执
     */
    @ApiStability.Stable
    @NotNull CompletableFuture<AxsDeliveryReceipt> sendDurable(@NotNull AxsMailRequest request);

    /** 按收件人 UUID 投递预设邮件，沿用普通邮件的事务和去重回执。 */
    @NotNull CompletableFuture<AxsDeliveryReceipt> sendPresetDurable(@NotNull UUID recipient, @NotNull String presetId,
        @NotNull String sourcePlugin, @NotNull String idempotencyKey);

    /**
     * 查询玩家自己的单封邮件。
     *
     * @param playerId 玩家 UUID
     * @param mailId   邮件 ID
     * @return 异步邮件视图，不存在时返回 {@code null}
     */
    @ApiStability.Stable
    @NotNull CompletableFuture<AxsMailView> getMail(@NotNull UUID playerId, long mailId);

    /**
     * 查询玩家收件箱。
     *
     * @param playerId 玩家 UUID
     * @param query    查询条件
     * @return 异步邮件视图列表
     */
    @ApiStability.Stable
    @NotNull CompletableFuture<List<AxsMailView>> getInbox(@NotNull UUID playerId, @NotNull AxsMailQuery query);

    /**
     * 查询玩家未读邮件数量。
     *
     * @param playerId 玩家 UUID
     * @return 异步未读数量
     */
    @ApiStability.Stable
    @NotNull CompletableFuture<Integer> getUnreadCount(@NotNull UUID playerId);

    /**
     * 标记邮件为已读并返回详情。
     * <p>
     * 如果邮件已经是已读或更后状态，不改变状态但仍返回邮件视图。
     *
     * @param playerId 玩家 UUID
     * @param mailId   邮件 ID
     * @return 异步邮件视图，不存在时返回 {@code null}
     */
    @ApiStability.Stable
    @NotNull CompletableFuture<AxsMailView> markRead(@NotNull UUID playerId, long mailId);

    /**
     * 领取邮件的全部可领附件。
     * <p>
     * 领取物品附件需要玩家在线（发放到背包）。玩家离线时返回
     * {@link AxsClaimResult#playerOffline()}。
     *
     * @param playerId 玩家 UUID
     * @param mailId   邮件 ID
     * @return 异步领取结果
     */
    @ApiStability.Stable
    @NotNull CompletableFuture<AxsClaimResult> claim(@NotNull UUID playerId, long mailId);

    /**
     * 删除没有受保护附件的邮件。
     * <p>
     * 还有未领取附件时返回 {@link AxsDeleteResult#protectedAttachments()}。
     *
     * @param playerId 玩家 UUID
     * @param mailId   邮件 ID
     * @return 异步删除结果
     */
    @ApiStability.Stable
    @NotNull CompletableFuture<AxsDeleteResult> delete(@NotNull UUID playerId, long mailId);
}
