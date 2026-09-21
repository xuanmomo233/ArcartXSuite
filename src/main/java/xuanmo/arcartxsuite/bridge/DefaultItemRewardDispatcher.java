package xuanmo.arcartxsuite.bridge;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.WarehouseAutoDepositable;
import xuanmo.arcartxsuite.api.capability.mail.MailAttachmentSpec;
import xuanmo.arcartxsuite.api.capability.mail.MailSendRequest;
import xuanmo.arcartxsuite.api.capability.mail.MailSendResult;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.api.item.PendingRewardService;

import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.module.ModuleRegistry;

/**
 * 统一物品奖励发放器的宿主默认实现。
 * <p>
 * 三级兜底链路：放入背包 → 背包满时尝试存入仓库 → 仓库不可用或满时通过 Mail 模块发邮件补发 →
 * Mail 模块不可用时暂存 PendingRewardService → PendingRewardService 不可用时掉落地面。
 * <p>
 * 物品栏操作与掉落地面在主线程执行；邮件发送在异步线程执行。
 * Mail/Warehouse/PendingRewardService 通过 {@link ModuleRegistry#getCapability} 延迟查找，
 * 确保模块加载顺序不影响发放。
 */
public final class DefaultItemRewardDispatcher implements ItemRewardDispatcher {

    private static final String DEFAULT_OVERFLOW_SUBJECT = "背包已满，奖励物品通过邮件补发";
    private static final String DEFAULT_OVERFLOW_BODY = "您的背包空间不足，部分奖励物品已通过邮件附件补发，请查收。";

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final Logger logger;
    private final ModuleRegistry registry;

    public DefaultItemRewardDispatcher(org.bukkit.plugin.java.JavaPlugin plugin, Logger logger, ModuleRegistry registry) {
        this.plugin = plugin;
        this.logger = logger;
        this.registry = registry;
    }

    @Override
    public @NotNull DispatchResult dispatchItem(@NotNull Player player, @NotNull ItemStack item,
                                                @NotNull DispatchOptions options) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(options, "options");
        if (!player.isOnline()) {
            return new DispatchResult(false, 0, 0, 0, 0, 0, "玩家不在线");
        }
        if (item.getType().isAir() || item.getAmount() <= 0) {
            return new DispatchResult(false, 0, 0, 0, 0, 0, "物品无效");
        }

        // 物品栏操作必须在主线程
        return Bukkit.isPrimaryThread()
            ? doDispatch(player, item, options)
            : runSyncAndWait(player, item, options);
    }

    @Override
    public @NotNull DispatchResult dispatchItems(@NotNull Player player, @NotNull List<ItemStack> items,
                                                 @NotNull DispatchOptions options) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(options, "options");
        if (items.isEmpty()) return DispatchResult.empty();
        DispatchResult aggregated = DispatchResult.empty();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) continue;
            aggregated = aggregated.merge(dispatchItem(player, item, options));
        }
        return aggregated;
    }

    // ─── 内部实现 ──────────────────────────────────────────────

    private DispatchResult runSyncAndWait(Player player, ItemStack item, DispatchOptions options) {
        // 异步调用时调度到主线程并等待结果（通过同步阻塞队列）
        final java.util.concurrent.atomic.AtomicReference<DispatchResult> ref = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                ref.set(doDispatch(player, item, options));
            } finally {
                latch.countDown();
            }
        });
        try {
            // 最多等待 10 秒，防止主线程任务永远不执行时调用线程永久阻塞
            if (!latch.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                return new DispatchResult(false, 0, 0, 0, 0, 0, "主线程调度超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DispatchResult(false, 0, 0, 0, 0, 0, "主线程调度被中断");
        }
        return ref.get() != null ? ref.get() : new DispatchResult(false, 0, 0, 0, 0, 0, "主线程调度无返回");
    }

    private DispatchResult doDispatch(@NotNull Player player, @NotNull ItemStack item, @NotNull DispatchOptions options) {
        int requestedAmount = item.getAmount();
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
        if (overflow.isEmpty()) {
            return new DispatchResult(true, 1, 0, 0, 0, 0, "");
        }

        // 计算实际放入背包的数量
        int overflowAmount = 0;
        int warehoused = 0;
        int mailed = 0;
        int pending = 0;
        int dropped = 0;
        java.util.List<ItemStack> remainingOverflow = new java.util.ArrayList<>();
        for (ItemStack leftover : overflow.values()) {
            if (leftover != null && !leftover.getType().isAir() && leftover.getAmount() > 0) {
                overflowAmount += leftover.getAmount();
                remainingOverflow.add(leftover);
            }
        }
        // deliveredCount: 请求数量 - 溢出数量（部分放入背包的情况）
        int delivered = requestedAmount > overflowAmount ? 1 : 0;

        // 第一级兜底：仓库（如果未跳过）
        if (!options.skipWarehouseOverflow() && !remainingOverflow.isEmpty()) {
            WarehouseAutoDepositable warehouse = resolveWarehouse();
            if (warehouse != null) {
                java.util.List<ItemStack> stillRemaining = new java.util.ArrayList<>();
                for (ItemStack leftover : remainingOverflow) {
                    try {
                        WarehouseAutoDepositable.DepositResult result =
                            warehouse.depositToPersonalWarehouse(player, leftover);
                        if (result.success() && result.remainingAmount() <= 0) {
                            warehoused++;
                        } else if (result.success() && result.remainingAmount() > 0) {
                            // 部分存入，剩余继续走下一级兜底
                            ItemStack partial = leftover.clone();
                            partial.setAmount(result.remainingAmount());
                            stillRemaining.add(partial);
                        } else {
                            stillRemaining.add(leftover);
                        }
                    } catch (Exception e) {
                        logger.warning("物品转仓库异常: source=" + options.sourceModule()
                            + " player=" + player.getName() + " | " + e.getMessage());
                        stillRemaining.add(leftover);
                    }
                }
                remainingOverflow = stillRemaining;
            }
        }

        // 第二级兜底：邮件
        if (!remainingOverflow.isEmpty()) {
            MailDispatchable mail = resolveMail();
            if (mail != null) {
                java.util.List<ItemStack> stillRemaining = new java.util.ArrayList<>();
                for (ItemStack leftover : remainingOverflow) {
                    if (sendOverflowMail(mail, player, leftover, options)) {
                        mailed++;
                    } else {
                        stillRemaining.add(leftover);
                    }
                }
                remainingOverflow = stillRemaining;
            }
        }

        // 第三级兜底：PendingRewardService（如果未跳过）
        if (!options.skipPendingOverflow() && !remainingOverflow.isEmpty()) {
            PendingRewardService pendingService = resolvePending();
            if (pendingService != null) {
                java.util.List<ItemStack> stillRemaining = new java.util.ArrayList<>();
                for (ItemStack leftover : remainingOverflow) {
                    try {
                        String base64 = java.util.Base64.getEncoder().encodeToString(ItemSerializer.serialize(leftover));
                        pendingService.savePending(PendingRewardService.PendingReward.itemSerialized(
                            player.getUniqueId(), options.sourceModule(), base64,
                            options.sourceDetail() != null ? options.sourceDetail() : "overflow-item"));
                        pending++;
                    } catch (Exception e) {
                        logger.warning("物品转 pending 异常: source=" + options.sourceModule()
                            + " player=" + player.getName() + " | " + e.getMessage());
                        stillRemaining.add(leftover);
                    }
                }
                remainingOverflow = stillRemaining;
            }
        }

        // 最终兜底：掉落地面
        if (!remainingOverflow.isEmpty()) {
            for (ItemStack leftover : remainingOverflow) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                dropped++;
            }
        }

        boolean success = warehoused + mailed + pending + dropped > 0 || remainingOverflow.isEmpty();
        StringBuilder msg = new StringBuilder();
        if (delivered > 0) msg.append("部分物品已放入背包");
        if (warehoused > 0) msg.append(msg.length() > 0 ? "，" : "").append("部分物品已存入仓库");
        if (mailed > 0) msg.append(msg.length() > 0 ? "，" : "").append("部分物品已通过邮件补发");
        if (pending > 0) msg.append(msg.length() > 0 ? "，" : "").append("部分物品已暂存待发放");
        if (dropped > 0) msg.append(msg.length() > 0 ? "，" : "").append("部分物品已掉落地面");
        if (msg.length() == 0) msg.append("背包满且无溢出物品");
        return new DispatchResult(success, delivered, warehoused, mailed, pending, dropped, msg.toString());
    }

    private boolean sendOverflowMail(@NotNull MailDispatchable mail, @NotNull Player player,
                                     @NotNull ItemStack leftover, @NotNull DispatchOptions options) {
        String subject = options.overflowMailSubject() != null ? options.overflowMailSubject() : DEFAULT_OVERFLOW_SUBJECT;
        String body = options.overflowMailBody() != null ? options.overflowMailBody() : DEFAULT_OVERFLOW_BODY;
        String sender = options.overflowMailSenderName() != null ? options.overflowMailSenderName() : options.sourceModule();
        try {
            MailSendRequest request = MailSendRequest.builder(player.getUniqueId(), subject, body)
                .attachment(MailAttachmentSpec.item(leftover))
                .senderName(sender)
                .sourceModule(options.sourceModule())
                .sourceDetail(options.sourceDetail() != null ? options.sourceDetail() : "overflow-item")
                .build();
            MailSendResult result = mail.sendMail(request);
            if (!result.success()) {
                logger.warning("物品转邮件失败: source=" + options.sourceModule()
                    + " player=" + player.getName() + " reason=" + result.message());
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.warning("物品转邮件异常: source=" + options.sourceModule()
                + " player=" + player.getName() + " | " + e.getMessage());
            return false;
        }
    }

    @Nullable
    private MailDispatchable resolveMail() {
        try {
            return registry.getCapability(MailDispatchable.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private WarehouseAutoDepositable resolveWarehouse() {
        try {
            return registry.getCapability(WarehouseAutoDepositable.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private PendingRewardService resolvePending() {
        try {
            return registry.getCapability(PendingRewardService.class);
        } catch (Exception e) {
            return null;
        }
    }
}
