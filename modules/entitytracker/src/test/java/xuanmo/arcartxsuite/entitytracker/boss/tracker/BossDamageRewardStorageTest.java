package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.mail.AxsDeliveryReceipt;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankRewardDefinition;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingRewardsSettings;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardAction;

class BossDamageRewardStorageTest {
    @Test
    void aLateMailReceiptUpdatesItsOwnSettlementAndKeepsTheLatestPlayerView() {
        UUID player = UUID.randomUUID();
        UUID firstBoss = UUID.randomUUID();
        UUID secondBoss = UUID.randomUUID();
        var firstMail = new CompletableFuture<AxsDeliveryReceipt>();
        var secondMail = new CompletableFuture<AxsDeliveryReceipt>();
        var mail = mock(AxsMailService.class);
        when(mail.sendPresetDurable(player, "reward", "entitytracker", key(firstBoss, player, 0))).thenReturn(firstMail);
        when(mail.sendPresetDurable(player, "reward", "entitytracker", key(secondBoss, player, 0))).thenReturn(secondMail);
        var service = service(mail, null);
        var action = BossDamageRewardAction.from(Map.of("type", "mail", "preset-id", "reward"));
        var entity = mock(LivingEntity.class);

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var first = service.settle(session(entity, firstBoss, player, action), entity);
            assertTrue(service.issuing(first.settlementId()));
            assertFalse(first.entry(player).rewarded());
            assertFalse(service.reissueReward(first.settlementId(), 1, null).toCompletableFuture().getNow(null).success());
            var second = service.settle(session(entity, secondBoss, player, action), entity);

            secondMail.complete(AxsDeliveryReceipt.ok(22, 1));
            firstMail.complete(AxsDeliveryReceipt.duplicate(21));
            assertFalse(service.issuing(first.settlementId()));
            assertTrue(service.settlement(first.settlementId()).entry(player).rewarded());
            assertTrue(service.lastSettlement(player).entry().rewarded());
            assertEquals(second.settlementId(), service.lastSettlement(player).settlement().settlementId());

            UUID retiringBoss = UUID.randomUUID();
            var retiringMail = new CompletableFuture<AxsDeliveryReceipt>();
            when(mail.sendPresetDurable(player, "reward", "entitytracker", key(retiringBoss, player, 0))).thenReturn(retiringMail);
            service.settle(session(entity, retiringBoss, player, action), entity);
            service.shutdown();
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
            retiringMail.complete(AxsDeliveryReceipt.ok(23, 1));
            assertTrue(service.databaseStopped().toCompletableFuture().isDone());
        } finally { service.shutdown(); }
    }

    @Test
    void alreadyAcceptedOfflineActionsDrainOffMainWhenTheModuleStops() throws Exception {
        UUID player = UUID.randomUUID();
        UUID boss = UUID.randomUUID();
        Thread primary = Thread.currentThread();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pending = mock(PendingRewardService.class);
        when(pending.savePendingBatchConfirmed(anyString(), anyList())).thenAnswer(call -> {
            assertNotSame(primary, Thread.currentThread());
            started.countDown();
            return release.await(5, TimeUnit.SECONDS);
        });
        var service = service(null, pending);
        var item = BossDamageRewardAction.from(Map.of("type", "item", "id", "DIAMOND"));
        var entity = mock(LivingEntity.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var record = service.settle(session(entity, boss, player, item, item), entity);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(service.issuing(record.settlementId()));
            assertFalse(record.entry(player).rewarded());
            service.shutdown();
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
            release.countDown();
            service.databaseStopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
            verify(pending).savePendingBatchConfirmed(eq(key(boss, player, 0)), anyList());
            verify(pending).savePendingBatchConfirmed(eq(key(boss, player, 1)), anyList());
        } finally {
            release.countDown();
            service.shutdown();
        }
    }

    private static BossDamageSettlementService service(AxsMailService mail, PendingRewardService pending) {
        return service(mail, pending, null);
    }

    private static BossDamageSettlementService service(AxsMailService mail, PendingRewardService pending, ItemSourceRegistry items) {
        var messages = mock(MessageProvider.class);
        when(messages.get("admin.reissue.pending")).thenReturn("处理中");
        return new BossDamageSettlementService(mock(JavaPlugin.class), Logger.getAnonymousLogger(),
            () -> mail, null, items, null, messages, pending);
    }

    @Test
    void overflowPersistsTheWholeGeneratedItemWithoutApplyingPartialInventoryChanges() throws Exception {
        UUID recipient = UUID.randomUUID();
        UUID boss = UUID.randomUUID();
        Thread primary = Thread.currentThread();
        var accepted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var player = mock(Player.class);
        var inventory = mock(PlayerInventory.class);
        var item = mock(ItemStack.class);
        var stock = mock(ItemStack.class);
        var merged = mock(ItemStack.class);
        when(player.getUniqueId()).thenReturn(recipient);
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{stock});
        when(inventory.getMaxStackSize()).thenReturn(64);
        when(stock.getType()).thenReturn(Material.DIAMOND);
        when(stock.getAmount()).thenReturn(63);
        when(stock.isSimilar(item)).thenReturn(true);
        when(stock.clone()).thenReturn(merged);
        when(item.getType()).thenReturn(Material.DIAMOND);
        when(item.getAmount()).thenReturn(5);
        when(item.getMaxStackSize()).thenReturn(64);
        var items = mock(ItemSourceRegistry.class);
        when(items.generateItem("minecraft", "DIAMOND", 5, null, player)).thenReturn(item);
        var pending = mock(PendingRewardService.class);
        when(pending.savePendingBatchConfirmed(eq(key(boss, recipient, 0)), anyList())).thenAnswer(call -> {
            assertNotSame(primary, Thread.currentThread());
            var reward = call.<List<PendingRewardService.PendingReward>>getArgument(1).get(0);
            assertEquals(PendingRewardService.PendingReward.Type.ITEM_SERIALIZED, reward.type());
            assertEquals(recipient, reward.playerUuid());
            assertTrue(reward.payload().contains("AQID"));
            accepted.countDown();
            return release.await(5, TimeUnit.SECONDS);
        });
        var service = service(null, pending, items);
        var action = BossDamageRewardAction.from(Map.of("type", "item", "id", "DIAMOND", "amount", 5));
        var entity = mock(LivingEntity.class);
        try (var bukkit = mockStatic(Bukkit.class); var serialization = mockStatic(ItemSerializer.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(recipient)).thenReturn(player);
            serialization.when(() -> ItemSerializer.serialize(item)).thenReturn(new byte[]{1, 2, 3});
            var settlement = service.settle(session(entity, boss, recipient, action), entity);
            assertTrue(service.issuing(settlement.settlementId()));
            verify(inventory, never()).setItem(anyInt(), any());
            verify(player, never()).saveData();
            assertTrue(accepted.await(5, TimeUnit.SECONDS));
            service.shutdown();
            release.countDown();
            service.databaseStopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
            verify(pending).savePendingBatchConfirmed(eq(key(boss, recipient, 0)), anyList());
        } finally {
            release.countDown();
            service.shutdown();
        }
    }

    private static BossSession session(LivingEntity entity, UUID boss, UUID player, BossDamageRewardAction... actions) {
        var ranked = new BossDamageRankingEntry(player, "Tester", 1, true, 100, 100, 0);
        var ranking = new BossDamageRankingSnapshot(true, 1, 1, 1, 100, List.of(ranked), List.of(ranked), Map.of(player, ranked));
        var rewards = new BossDamageRankingRewardsSettings(true, Map.of(1, new BossDamageRankRewardDefinition(1, List.of(actions))));
        var session = mock(BossSession.class);
        when(session.markSettled()).thenReturn(true);
        when(session.createSettlementSnapshot(entity)).thenReturn(new BossSession.BossSettlementSnapshot(
            boss, "dragon", "Dragon", 0, 100, 0, 1, ranking, rewards));
        return session;
    }

    private static String key(UUID boss, UUID player, int step) {
        return "boss-rank:kill:" + boss + ":" + player + ":" + step;
    }
}
