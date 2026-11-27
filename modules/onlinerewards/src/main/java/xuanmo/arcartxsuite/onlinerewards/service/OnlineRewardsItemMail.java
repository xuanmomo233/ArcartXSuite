package xuanmo.arcartxsuite.onlinerewards.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.mail.AxsMailRequest;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.Delivery;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.ItemCheckpoint;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.ItemSink;

/** Delivers online rewards through Mail's durable receipts. */
final class OnlineRewardsItemMail {
    private final Supplier<AxsMailService> provider;
    private final boolean sharedMysql;
    private final boolean mysqlRewards;
    private final String localNode;

    OnlineRewardsItemMail(Supplier<AxsMailService> provider, boolean sharedMysql, boolean mysqlRewards, String localNode) {
        this.provider = provider;
        this.sharedMysql = sharedMysql;
        this.mysqlRewards = mysqlRewards;
        this.localNode = localNode == null ? "" : localNode.toLowerCase(Locale.ROOT);
    }

    boolean available() {
        return resolve() != null;
    }

    boolean canRecoverAcrossNodes() {
        // In this mode every send also requires Mail to use the shared host database.
        return BuildEdition.CROSS_SERVER_INCLUDED && sharedMysql;
    }

    boolean canRetryPreset() {
        // A self-managed MySQL queue could be read by another node with a different mail store.
        return !mysqlRewards || sharedMysql;
    }

    CompletionStage<Boolean> deliverPreset(Delivery delivery) {
        AxsMailService mail = provider.get();
        if (mail == null) return CompletableFuture.completedFuture(false);
        boolean sharedMail = mail.usesSharedHostStorage();
        if (sharedMysql && !sharedMail) return CompletableFuture.completedFuture(false);
        // Mail also scopes the key to the recipient, so a retried SQL claim uses the same original reward.
        String scope = "";
        if (!sharedMysql && sharedMail) {
            // Independent reward queues may reuse the same batch and step on different nodes.
            if (localNode.isBlank() || "default".equals(localNode)) {
                return CompletableFuture.failedFuture(new IllegalStateException("本服在线奖励通过共享邮件发放需要配置唯一节点 ID"));
            }
            scope = "node:" + localNode.length() + ":" + localNode + ":";
        }
        String key = "preset:v1:" + scope + delivery.batchId() + ":" + delivery.step();
        return mail.sendPresetDurable(delivery.playerUuid(), delivery.reward().value(), "onlinerewards", key)
            .thenApply(receipt -> receipt != null && receipt.mailId() > 0);
    }

    CompletionStage<Boolean> deliver(ItemCheckpoint checkpoint, ItemStack item) {
        if (checkpoint.sink() != ItemSink.MAIL || item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        try {
            AxsMailService mail = resolve();
            if (mail == null) return CompletableFuture.completedFuture(false);
            var delivery = checkpoint.delivery();
            // A shared mailbox can receive identical batch numbers from independent reward queues.
            String key = !sharedMysql && mail.usesSharedHostStorage()
                ? "item:checkpoint:" + checkpoint.attempt()
                : "item:v1:" + delivery.batchId() + ":" + delivery.step();
            var request = AxsMailRequest.builder(key, "在线奖励溢出物品")
                .recipient(delivery.playerUuid())
                .content("您的背包已满，在线奖励物品通过邮件补发。")
                .senderName("在线奖励")
                .sourcePlugin("onlinerewards")
                .item(item.clone())
                // Match the existing overflow convention. A duplicate receipt never extends expiry.
                .expireAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();
            return mail.sendDurable(request).thenApply(receipt -> receipt != null && receipt.mailId() > 0);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private AxsMailService resolve() {
        AxsMailService mail = provider.get();
        return mail != null && (!sharedMysql || mail.usesSharedHostStorage()) ? mail : null;
    }
}
