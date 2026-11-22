package xuanmo.arcartxsuite.mail.service;

import java.time.Instant;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
import xuanmo.arcartxsuite.mail.model.MailLogEntry;
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
 * 发送在主线程序列化附件，异步提交邮件，主线程通知在线玩家。
 */
public final class AxsMailServiceAdapter implements AxsMailService {

    // Paper closes the outer plugin JAR before asynchronous module retirement finishes.
    // Resolve this response type while the adapter is created, before any JDBC work starts.
    private static final AxsDeliveryReceipt UNAVAILABLE_RECEIPT = AxsDeliveryReceipt.noOp();

    private final JavaPlugin plugin;
    private final MailService mailService;
    private final Logger logger;
    private final ModuleExecutor asyncExecutor;
    private final java.util.Set<PrimaryCall> primaryCalls = new java.util.HashSet<>();
    private final boolean sharedHostStorage;
    private volatile boolean active = true;

    public AxsMailServiceAdapter(@NotNull JavaPlugin plugin, @NotNull MailService mailService, @NotNull Logger logger,
                                 boolean sharedHostStorage) {
        this.plugin = plugin;
        this.mailService = mailService;
        this.logger = logger;
        this.sharedHostStorage = sharedHostStorage;
        this.asyncExecutor = new ModuleExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(128), r -> {
            Thread t = new Thread(r, "AxsMailService-Async");
            t.setDaemon(true);
            return t;
        });
    }

    public void close() {
        java.util.List<PrimaryCall> pending;
        synchronized (primaryCalls) { active = false; pending = java.util.List.copyOf(primaryCalls); }
        pending.forEach(PrimaryCall::cancel);
        CompletableFuture.allOf(pending.stream().map(call -> call.stopped).toArray(CompletableFuture[]::new))
            .whenComplete((ignored,failure) -> asyncExecutor.shutdown());
    }

    public java.util.concurrent.CompletionStage<Void> stopped() { return asyncExecutor.stopped(); }

    @Override public boolean usesSharedHostStorage() {
        return sharedHostStorage;
    }

    // ── 发送 ──────────────────────────────────────────────

    @Override
    public @NotNull CompletableFuture<AxsDeliveryReceipt> send(@NotNull AxsMailRequest request) {
        return sendDurable(request);
    }

    @Override
    public @NotNull CompletableFuture<AxsDeliveryReceipt> sendDurable(@NotNull AxsMailRequest request) {
        if (!active) {
            return CompletableFuture.completedFuture(UNAVAILABLE_RECEIPT);
        }
        CompletableFuture<AxsDeliveryReceipt> future = new CompletableFuture<>();
        Runnable prepare = () -> {
            try {
                List<MailAttachment> attachments = buildAttachments(request);
                List<String> claimCommands = buildClaimCommands(request);
                Instant expiresAt = request.expireAt() != null
                    ? request.expireAt()
                    : Instant.now().plus(java.time.Duration.ofDays(30));
                String senderName = request.senderName() != null && !request.senderName().isBlank()
                    ? request.senderName() : "系统";
                var messages = request.recipients().stream().distinct().map(recipient ->
                    mailService.systemMail(recipient, request.title(), request.content(), attachments,
                        claimCommands, expiresAt, senderName)).toList();
                asyncExecutor.execute(() -> commitMail(request.sourcePlugin(), request.idempotencyKey(), messages, future));
            } catch (Exception | LinkageError e) {
                future.completeExceptionally(e);
            }
        };
        primary(prepare, () -> future.complete(UNAVAILABLE_RECEIPT), future::completeExceptionally,
            request.items().isEmpty() || Bukkit.isPrimaryThread());
        return future;
    }

    @Override
    public @NotNull CompletableFuture<AxsDeliveryReceipt> sendPresetDurable(@NotNull UUID recipient, @NotNull String presetId,
            @NotNull String sourcePlugin, @NotNull String idempotencyKey) {
        if (idempotencyKey.isBlank()) return CompletableFuture.failedFuture(new IllegalArgumentException("idempotencyKey 不能为空"));
        if (!active) return CompletableFuture.completedFuture(UNAVAILABLE_RECEIPT);
        var future = new CompletableFuture<AxsDeliveryReceipt>();
        try {
            var mail = mailService.durablePreset(recipient, presetId, MailTexts.crop(sourcePlugin, 64));
            asyncExecutor.execute(() -> {
                if (mail != null) {
                    commitMail(sourcePlugin, idempotencyKey, List.of(mail), future);
                    return;
                }
                // A preset may be disabled or removed after this exact delivery already committed.
                try {
                    long existing = mailService.repository().readPlayerSendReceipt(durableKey(sourcePlugin, idempotencyKey), recipient);
                    future.complete(existing > 0 ? AxsDeliveryReceipt.duplicate(existing) : UNAVAILABLE_RECEIPT);
                } catch (Exception | LinkageError failure) {
                    future.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException | LinkageError failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    private void commitMail(String sourcePlugin, String idempotencyKey, List<MailMessage> messages,
                            CompletableFuture<AxsDeliveryReceipt> future) {
        var created = new ArrayList<MailMessage>();
        try {
            if (messages.isEmpty()) {
                future.complete(UNAVAILABLE_RECEIPT);
                return;
            }
            String key = durableKey(sourcePlugin, idempotencyKey);
            long firstId = 0L;
            for (MailMessage message : messages) {
                var receipt = mailService.repository().insertDurableMail(key, message);
                if (firstId == 0L) firstId = receipt.id();
                if (!receipt.duplicate()) {
                    created.add(message);
                    try {
                        String action = message.sourceType() == MailSourceType.PRESET ? "preset" : "system";
                        mailService.repository().appendLog(new MailLogEntry(0L, message.ownerUuid(), action,
                            sourcePlugin + ":" + idempotencyKey, Instant.now()));
                    } catch (Exception failure) {
                        logger.warning("邮件已入库，日志写入失败: " + failure.getMessage());
                    }
                }
            }
            future.complete(created.isEmpty() ? AxsDeliveryReceipt.duplicate(firstId)
                : AxsDeliveryReceipt.ok(firstId, created.size()));
        } catch (Exception | LinkageError failure) {
            future.completeExceptionally(failure);
        } finally {
            if (active && !created.isEmpty()) {
                try {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (active) created.forEach(mailService::notifyDurableMail);
                    });
                } catch (RuntimeException failure) {
                    logger.fine("邮件已入库，在线通知调度失败: " + failure.getMessage());
                }
            }
        }
    }

    private static String durableKey(String sourcePlugin, String idempotencyKey) throws java.security.NoSuchAlgorithmException {
        String identity = sourcePlugin.length() + ":" + sourcePlugin + idempotencyKey;
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(identity.getBytes(StandardCharsets.UTF_8)));
    }

    // ── 查询 ──────────────────────────────────────────────

    @Override
    public @NotNull CompletableFuture<AxsMailView> getMail(@NotNull UUID playerId, long mailId) {
        return submit(() -> {
            try {
                Optional<MailMessage> mail = mailService.repository().loadMail(playerId, mailId);
                return mail.map(this::toView).orElse(null);
            } catch (Exception e) {
                logger.warning("AxsMailService getMail 失败: " + e.getMessage());
                return null;
            }
        }, null);
    }

    @Override
    public @NotNull CompletableFuture<List<AxsMailView>> getInbox(@NotNull UUID playerId, @NotNull AxsMailQuery query) {
        return submit(() -> {
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
        }, List.of());
    }

    @Override
    public @NotNull CompletableFuture<Integer> getUnreadCount(@NotNull UUID playerId) {
        return submit(() -> {
            try {
                return mailService.unreadCount(playerId);
            } catch (Exception e) {
                logger.warning("AxsMailService getUnreadCount 失败: " + e.getMessage());
                return 0;
            }
        }, 0);
    }

    // ── 操作 ──────────────────────────────────────────────

    @Override
    public @NotNull CompletableFuture<AxsMailView> markRead(@NotNull UUID playerId, long mailId) {
        return submit(() -> {
            try {
                Optional<MailMessage> mail = mailService.markMailRead(playerId, mailId);
                return mail.map(this::toView).orElse(null);
            } catch (Exception e) {
                logger.warning("AxsMailService markRead 失败: " + e.getMessage());
                return null;
            }
        }, null);
    }

    @Override
    public @NotNull CompletableFuture<AxsClaimResult> claim(@NotNull UUID playerId, long mailId) {
        CompletableFuture<AxsClaimResult> future = new CompletableFuture<>();
        primary(() -> {
            try {
                mailService.claimMailByUuid(playerId, mailId).whenComplete((result,failure) -> {
                    if (failure != null) future.complete(AxsClaimResult.failure("CLAIM_FAILED"));
                    else future.complete(result.success() ? AxsClaimResult.ok(result.message()) : mapClaimFailure(result.message()));
                });
            } catch (RuntimeException | LinkageError failure) {
                future.complete(AxsClaimResult.failure("CLAIM_FAILED"));
            }
        }, () -> future.complete(AxsClaimResult.failure("MODULE_DISABLED")),
            failure -> future.complete(AxsClaimResult.failure("CLAIM_FAILED")), false);
        return future;
    }

    @Override
    public @NotNull CompletableFuture<AxsDeleteResult> delete(@NotNull UUID playerId, long mailId) {
        return submit(() -> {
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
        }, AxsDeleteResult.failure("MODULE_DISABLED"));
    }

    private <T> CompletableFuture<T> submit(java.util.function.Supplier<T> work, T unavailable) {
        if (!active) return CompletableFuture.completedFuture(unavailable);
        try { return CompletableFuture.supplyAsync(work,asyncExecutor); }
        catch (java.util.concurrent.RejectedExecutionException busy) { return CompletableFuture.failedFuture(busy); }
    }

    private void primary(Runnable action,Runnable cancelled,java.util.function.Consumer<Throwable> failed,boolean inline) {
        PrimaryCall call;
        synchronized (primaryCalls) {
            if (!active) { cancelled.run(); return; }
            if (primaryCalls.size() >= 128) {
                failed.accept(new java.util.concurrent.RejectedExecutionException("邮件准备队列已满")); return;
            }
            call = new PrimaryCall(action,cancelled,failed);
            primaryCalls.add(call);
        }
        try {
            if (inline) call.run(); else Bukkit.getScheduler().runTask(plugin,call);
        } catch (RuntimeException | LinkageError failure) { call.reject(failure); }
    }

    private final class PrimaryCall extends java.util.concurrent.atomic.AtomicBoolean implements Runnable {
        private final Runnable action;
        private final Runnable cancelled;
        private final java.util.function.Consumer<Throwable> failed;
        private final CompletableFuture<Void> stopped = new CompletableFuture<>();
        PrimaryCall(Runnable action,Runnable cancelled,java.util.function.Consumer<Throwable> failed) {
            this.action=action;this.cancelled=cancelled;this.failed=failed;
        }
        @Override public void run() {
            if (!compareAndSet(false,true)) return;
            try { action.run(); }
            catch (Throwable failure) { failed.accept(failure); }
            finally { finish(); }
        }
        void cancel() {
            if (!compareAndSet(false,true)) return;
            try { cancelled.run(); } finally { finish(); }
        }
        void reject(Throwable failure) {
            if (!compareAndSet(false,true)) return;
            try { failed.accept(failure); } finally { finish(); }
        }
        private void finish() {
            synchronized (primaryCalls) { primaryCalls.remove(this); }
            stopped.complete(null);
        }
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
            case CLAIM_PENDING -> AxsMailState.CLAIM_PENDING;
            case CLAIM_REVIEW -> AxsMailState.CLAIM_REVIEW;
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
