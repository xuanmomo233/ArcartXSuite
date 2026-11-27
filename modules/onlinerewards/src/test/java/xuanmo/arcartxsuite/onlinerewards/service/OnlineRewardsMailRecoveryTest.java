package xuanmo.arcartxsuite.onlinerewards.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.mail.AxsDeliveryReceipt;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;
import xuanmo.arcartxsuite.onlinerewards.storage.JdbcOnlineRewardsRepository;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.*;

class OnlineRewardsMailRecoveryTest {
    @TempDir Path directory;

    @Test void anotherNodeReplaysTheOriginalKeyAndWaitsForDurableMailConfirmation() throws Exception {
        assumeTrue(BuildEdition.CROSS_SERVER_INCLUDED);
        replayPersistedSharedMail(true);
    }

    @Test void originalNodeReplaysPreviouslySharedMailWithItsOriginalKey() throws Exception {
        replayPersistedSharedMail(false);
    }

    private void replayPersistedSharedMail(boolean anotherNode) throws Exception {
        var source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("rewards.sqlite"));
        var descriptor = StorageDescriptor.sqlite("rewards.sqlite");
        Logger logger = Logger.getAnonymousLogger();
        var firstRepository = new JdbcOnlineRewardsRepository(source, descriptor, directory.toFile(), logger);
        var secondRepository = new JdbcOnlineRewardsRepository(source, descriptor, directory.toFile(), logger);
        firstRepository.initialize();
        UUID playerId = UUID.randomUUID();
        var reward = new Reward("ITEM", "fixture", "");
        firstRepository.saveProgressBatch(List.of(new ProgressCommit(playerId, new OnlineRewardsPlayerState(),
            List.of(new RewardBatch("time:mail", List.of(reward))))));
        var delivery = firstRepository.readyDeliveries(playerId, 1).get(0);
        UUID attempt = UUID.randomUUID(), origin = UUID.randomUUID();
        var checkpoint = new ItemCheckpoint(delivery, attempt, origin.toString(), "AQ==", ItemSink.MAIL);
        assertTrue(firstRepository.beginDelivery(delivery, attempt));
        assertTrue(firstRepository.stageItemCheckpoint(checkpoint));

        Path first = Files.createDirectory(directory.resolve("first"));
        Path second = Files.createDirectory(directory.resolve("second"));
        Files.writeString(first.resolve(".arcartxsuite-onlinerewards-node"), origin.toString());
        Files.writeString(second.resolve(".arcartxsuite-onlinerewards-node"),
            (anotherNode ? UUID.randomUUID() : origin).toString());
        var mainTasks = new LinkedBlockingQueue<Runnable>();
        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskLater(any(), any(Runnable.class), eq(1L))).thenAnswer(call -> {
            mainTasks.add(call.getArgument(1));
            return null;
        });
        var field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        Object oldServer = field.get(null);
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        var plugin = mock(JavaPlugin.class);
        var mail = mock(AxsMailService.class);
        when(mail.usesSharedHostStorage()).thenReturn(true);
        var mailPending = new CompletableFuture<AxsDeliveryReceipt>();
        var observedKeys = new java.util.ArrayList<String>();
        when(mail.sendDurable(any())).thenAnswer(call -> {
            xuanmo.arcartxsuite.api.mail.AxsMailRequest request = call.getArgument(0);
            observedKeys.add(request.idempotencyKey());
            // The first send represents an existing durable mail whose success reply was lost.
            return observedKeys.size() == 1 ? mailPending : CompletableFuture.completedFuture(AxsDeliveryReceipt.duplicate(42));
        });
        var overflow = new OnlineRewardsItemMail(() -> mail, true, true, "survival");
        field.set(null, server);
        try (var serializer = mockStatic(ItemSerializer.class)) {
            serializer.when(() -> ItemSerializer.deserializeStrict(any(byte[].class))).thenReturn(new ItemStack(Material.DIAMOND));
            try (var service = new OnlineRewardsItemDelivery(plugin, firstRepository, first, () -> overflow, logger)) {
                deliverOnce(service, player, mainTasks);
                assertEquals(checkpoint, firstRepository.itemCheckpoint(playerId).orElseThrow());
            }
            mailPending.complete(AxsDeliveryReceipt.ok(42, 1));
            try (var service = new OnlineRewardsItemDelivery(plugin, secondRepository, second, () -> overflow, logger)) {
                deliverOnce(service, player, mainTasks);
                service.close();
                service.stopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
            }
            String key = "item:v1:" + delivery.batchId() + ":" + delivery.step();
            assertEquals(List.of(key, key), observedKeys);
            assertTrue(firstRepository.itemCheckpoint(playerId).isEmpty());
            assertTrue(secondRepository.readyDeliveries(playerId, 1).isEmpty());
            assertFalse(secondRepository.completeItemCheckpoint(checkpoint));
            verify(player, never()).saveData();
        } finally {
            field.set(null, oldServer);
        }
    }

    private static void deliverOnce(OnlineRewardsItemDelivery service, Player player, LinkedBlockingQueue<Runnable> mainTasks)
        throws InterruptedException {
        service.recover(player, () -> true);
        Runnable callback = mainTasks.poll(3, TimeUnit.SECONDS);
        assertNotNull(callback, "persisted mail should be eligible for recovery on the current node");
        callback.run();
    }
}
