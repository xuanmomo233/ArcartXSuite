package xuanmo.arcartxsuite.mail.service;

import java.time.Instant;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.mail.AxsClaimResult;
import xuanmo.arcartxsuite.api.mail.AxsDeleteResult;
import xuanmo.arcartxsuite.api.mail.AxsDeliveryReceipt;
import xuanmo.arcartxsuite.api.mail.AxsMailAttachmentView;
import xuanmo.arcartxsuite.api.mail.AxsMailQuery;
import xuanmo.arcartxsuite.api.mail.AxsMailRequest;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.api.mail.AxsMailState;
import xuanmo.arcartxsuite.api.mail.AxsMailView;
import xuanmo.arcartxsuite.mail.model.MailAttachment;
import xuanmo.arcartxsuite.mail.model.MailAttachmentType;
import xuanmo.arcartxsuite.mail.model.MailInboxFilter;
import xuanmo.arcartxsuite.mail.model.MailInboxQuery;
import xuanmo.arcartxsuite.mail.model.MailMessage;
import xuanmo.arcartxsuite.mail.model.MailOperationResult;
import xuanmo.arcartxsuite.mail.model.MailPage;
import xuanmo.arcartxsuite.mail.model.MailSourceType;
import xuanmo.arcartxsuite.mail.model.MailStatus;

/**
 * {@link AxsMailService} 的适配实现，委托到 {@link MailService}。
 * <p>
 * 查询类方法（getMail/getInbox/getUnreadCount/markRead/delete）在异步线程池执行，
 * 不阻塞 Bukkit 主线程。
 * 发送和领取方法涉及 Bukkit API（在线通知、物品发放），在主线程执行。
 */
public final class AxsMailServiceAdapter implements AxsMailService {

    private final JavaPlugin plugin;
    private final MailService mailService;
    private final Logger logger;
    private final Executor asyncExecutor;

    public AxsMailServiceAdapter(@NotNull JavaPlugin plugin, @NotNull MailService mailService, @NotNull Logger logger) {
        this.plugin = plugin;
        this.mailService = mailService;
        this.logger = logger;
        this.asyncExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "AxsMailService-Async");
            t.setDaemon(true);
            return t;
        });
    }

    // ── 发送 ──────────────────────────────────────────────

    @Override
    public @NotNull CompletableFuture<AxsDeliveryReceipt> send(@NotNull AxsMailRequest request) {
        return sendInternal(request, false);
    }

    @Override
    public @NotNull CompletableFuture<AxsDeliveryReceipt> sendDurable(@NotNull AxsMailRequest request) {
        return sendInternal(request, true);
    }

    private CompletableFuture<AxsDeliveryReceipt> sendInternal(AxsMailRequest request, boolean durable) {
        if (mailService == null) {
            return CompletableFuture.completedFuture(AxsDeliveryReceipt.noOp());
        }
        CompletableFuture<AxsDeliveryReceipt> future = new CompletableFuture<>();
        // 发送涉及在线通知（Bukkit API），在主线程执行
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                List<MailAttachment> attachments = buildAttachments(request);
                List<String> claimCommands = buildClaimCommands(request);
                String logContent = request.sourcePlugin() + ":" + request.idempotencyKey();
                Instant expiresAt = request.expireAt() != null
                    ? request.expireAt()
                    : Instant.now().plus(java.time.Duration.ofDays(30));
                String senderName = request.senderName() != null && !request.senderName().isBlank()
                    ? request.senderName() : "系统";

                int successCount = 0;
                for (UUID recipient : request.recipients()) {
                    MailOperationResult result = mailService.sendSystemMail(
                        recipient, logContent, request.title(), request.content(),
                        attachments, claimCommands, expiresAt, senderName
                    );
                    if (result.success()) {
                        successCount++;
                    }
                }
                if (successCount > 0) {
                    future.complete(AxsDeliveryReceipt.ok(1L, successCount));
                } else {
                    future.complete(AxsDeliveryReceipt.noOp());
                }
            } catch (Exception e) {
                logger.warning("AxsMailService send 失败: " + e.getMessage());
                future.complete(AxsDeliveryReceipt.noOp());
            }
        });
        return future;
    }

    // ── 查询 ──────────────────────────────────────────────

    @Override
    public @NotNull CompletableFuture<AxsMailView> getMail(@NotNull UUID playerId, long mailId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<MailMessage> mail = mailService.repository().loadMail(playerId, mailId);
                return mail.map(this::toView).orElse(null);
            } catch (Exception e) {
                logger.warning("AxsMailService getMail 失败: " + e.getMessage());
                return null;
            }
        }, asyncExecutor);
    }

    @Override
    public @NotNull CompletableFuture<List<AxsMailView>> getInbox(@NotNull UUID playerId, @NotNull AxsMailQuery query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MailInboxFilter filter = mapFilter(query);
                MailInboxQuery inboxQuery = new MailInboxQuery(filter, query.page(), query.pageSize());
                MailPage<MailMessage> page = mailService.repository().loadInboxPage(playerId, inboxQuery);
                List<AxsMailView> views = new ArrayList<>();
                for (MailMessage msg : page.entries()) {
                    if (query.hasAvailableAttachments() && !msg.claimable()) {
                        continue;
                    }
                    views.add(toView(msg));
                }
                return views;
            } catch (Exception e) {
                logger.warning("AxsMailService getInbox 失败: " + e.getMessage());
                return List.of();
            }
        }, asyncExecutor);
    }

    @Override
    public @NotNull CompletableFuture<Integer> getUnreadCount(@NotNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return mailService.unreadCount(playerId);
            } catch (Exception e) {
                logger.warning("AxsMailService getUnreadCount 失败: " + e.getMessage());
                return 0;
            }
        }, asyncExecutor);
    }

    // ── 操作 ──────────────────────────────────────────────

    @Override
    public @NotNull CompletableFuture<AxsMailView> markRead(@NotNull UUID playerId, long mailId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<MailMessage> mail = mailService.markMailRead(playerId, mailId);
                return mail.map(this::toView).orElse(null);
            } catch (Exception e) {
                logger.warning("AxsMailService markRead 失败: " + e.getMessage());
                return null;
            }
        }, asyncExecutor);
    }

    @Override
    public @NotNull CompletableFuture<AxsClaimResult> claim(@NotNull UUID playerId, long mailId) {
        CompletableFuture<AxsClaimResult> future = new CompletableFuture<>();
        // 领取涉及物品发放（Bukkit API），在主线程执行
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                MailOperationResult result = mailService.claimMailByUuid(playerId, mailId);
                if (result.success()) {
                    future.complete(AxsClaimResult.ok(result.message()));
                } else {
                    future.complete(mapClaimFailure(result.message()));
                }
            } catch (Exception e) {
                logger.warning("AxsMailService claim 失败: " + e.getMessage());
                future.complete(AxsClaimResult.failure("CLAIM_FAILED"));
            }
        });
        return future;
    }

    @Override
    public @NotNull CompletableFuture<AxsDeleteResult> delete(@NotNull UUID playerId, long mailId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MailOperationResult result = mailService.deleteMailByUuid(playerId, mailId);
                if (result.success()) {
                    return AxsDeleteResult.ok();
                }
                return mapDeleteFailure(result.message());
            } catch (Exception e) {
                logger.warning("AxsMailService delete 失败: " + e.getMessage());
                return AxsDeleteResult.failure("DELETE_FAILED");
            }
        }, asyncExecutor);
    }

    // ── 转换工具 ──────────────────────────────────────────

    private AxsMailView toView(MailMessage msg) {
        List<AxsMailAttachmentView> attachmentViews = new ArrayList<>();
        if (msg.attachments() != null) {
            for (MailAttachment att : msg.attachments()) {
                attachmentViews.add(toAttachmentView(att));
            }
        }
        return new AxsMailView(
            msg.id(),
            msg.ownerUuid(),
            msg.senderName(),
            msg.subject(),
            msg.body(),
            mapState(msg.status()),
            List.copyOf(attachmentViews),
            msg.createdAt(),
            msg.expiresAt(),
            msg.claimedAt()
        );
    }

    private AxsMailAttachmentView toAttachmentView(MailAttachment att) {
        AxsMailAttachmentView.Type type = switch (att.type()) {
            case ITEM -> AxsMailAttachmentView.Type.ITEM;
            case VAULT -> AxsMailAttachmentView.Type.VAULT;
            case CURRENCY -> AxsMailAttachmentView.Type.CURRENCY;
        };
        return new AxsMailAttachmentView(type, att.currencyId(), att.amount(), att.description());
    }

    private AxsMailState mapState(MailStatus status) {
        if (status == null) return AxsMailState.UNREAD;
        return switch (status) {
            case UNREAD -> AxsMailState.UNREAD;
            case READ -> AxsMailState.READ;
            case CLAIMED -> AxsMailState.CLAIMED;
            case DELETED -> AxsMailState.DELETED;
            case EXPIRED -> AxsMailState.EXPIRED;
        };
    }

    private MailInboxFilter mapFilter(AxsMailQuery query) {
        if (query.state() == null) return MailInboxFilter.ALL;
        return switch (query.state()) {
            case UNREAD -> MailInboxFilter.UNREAD;
            case CLAIMED -> MailInboxFilter.ALL; // AXS mail 没有 CLAIMED filter，用 ALL 后在内存过滤
            default -> MailInboxFilter.ALL;
        };
    }

    private AxsClaimResult mapClaimFailure(@Nullable String reason) {
        if (reason == null) return AxsClaimResult.failure("UNKNOWN");
        return switch (reason) {
            case "PLAYER_OFFLINE" -> AxsClaimResult.playerOffline();
            case "INVENTORY_FULL", "player.mail-claim-inventory-full" -> AxsClaimResult.inventoryFull();
            case "NOT_FOUND", "player.mail-not-found" -> AxsClaimResult.notFound();
            case "player.mail-not-claimable" -> AxsClaimResult.nothingClaimable();
            default -> AxsClaimResult.failure(reason);
        };
    }

    private AxsDeleteResult mapDeleteFailure(@Nullable String reason) {
        if (reason == null) return AxsDeleteResult.failure("UNKNOWN");
        return switch (reason) {
            case "NOT_FOUND" -> AxsDeleteResult.notFound();
            case "PROTECTED_ATTACHMENTS" -> AxsDeleteResult.protectedAttachments();
            default -> AxsDeleteResult.failure(reason);
        };
    }

    private List<MailAttachment> buildAttachments(AxsMailRequest request) throws IOException {
        List<MailAttachment> attachments = new ArrayList<>();
        int sortOrder = 0;
        for (ItemStack item : request.items()) {
            if (item == null) continue;
            String itemData = xuanmo.arcartxsuite.mail.util.MailItemSerializer.serialize(item);
            attachments.add(new MailAttachment(0L, sortOrder++, MailAttachmentType.ITEM, itemData, "", 0.0D, "", null));
        }
        for (AxsMailRequest.CurrencyAttachment currency : request.currencies()) {
            String currencyId = currency.provider();
            MailAttachmentType type = "money".equalsIgnoreCase(currencyId)
                ? MailAttachmentType.VAULT : MailAttachmentType.CURRENCY;
            attachments.add(new MailAttachment(0L, sortOrder++, type, "", currencyId, currency.amount(), currency.displayName(), null));
        }
        return attachments;
    }

    private List<String> buildClaimCommands(AxsMailRequest request) {
        List<String> commands = new ArrayList<>();
        for (AxsMailRequest.CommandAttachment cmd : request.commands()) {
            commands.add(cmd.command());
        }
        return commands;
    }
}
