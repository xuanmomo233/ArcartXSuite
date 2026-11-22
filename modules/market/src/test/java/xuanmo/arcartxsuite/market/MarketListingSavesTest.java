package xuanmo.arcartxsuite.market;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
import xuanmo.arcartxsuite.market.storage.AuctionListingDraft;
import xuanmo.arcartxsuite.market.storage.MarketListingDraft;
import xuanmo.arcartxsuite.market.storage.MarketListingDraft.Kind;
import xuanmo.arcartxsuite.market.storage.MarketRepository;
import xuanmo.arcartxsuite.market.storage.MarketListingDraft.Save;

class MarketListingSavesTest {
    @TempDir Path playerData;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void auctionPublicationFutureWaitsForNaturalSaveAndDeductsOnlyTheRequestedAmount(boolean lostCommitResponse) throws Exception {
        var work = mock(MarketWork.class);
        var workers = new ArrayDeque<Runnable>();
        var callbacks = new ArrayDeque<Runnable>();
        doAnswer(call -> { workers.add(call.getArgument(0)); return null; }).when(work).async(any());
        doAnswer(call -> { callbacks.add(call.getArgument(0)); return null; }).when(work).main(any());
        var scan = new AtomicReference<Runnable>();
        when(work.repeatAsync(any(), eq(20L), eq(100L))).thenAnswer(call -> {
            scan.set(call.getArgument(0));
            return mock(BukkitTask.class);
        });
        var repository = mock(MarketRepository.class);
        var reserved = new AtomicReference<Save>();
        when(repository.reserveListingDraft(any(), eq(10))).thenAnswer(call -> {
            var plan = (AuctionListingDraft) call.getArgument(0);
            plan.listing().setId(9);
            reserved.set(plan.save());
            return MarketListingDraft.Reservation.RESERVED;
        });
        when(repository.listingSaves("source", "", 32)).thenAnswer(call -> List.of(reserved.get()));
        when(repository.publishListingDraft(any(), eq("source"))).thenReturn(true);
        if (lostCommitResponse) {
            when(repository.publishListingDraft(any(), eq("source"))).thenAnswer(call -> {
                when(repository.listingSaves("source", "", 32)).thenReturn(List.of());
                throw new IllegalStateException("连接在发布事务提交后断开");
            });
            when(repository.isListingDraftPublished(any(), eq("source")))
                .thenThrow(new IllegalStateException("连接尚未恢复")).thenReturn(true);
        }
        var seller = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(seller.getUniqueId()).thenReturn(id);
        when(seller.getName()).thenReturn("Seller");
        var inventory = mock(PlayerInventory.class);
        var pdc = mock(PersistentDataContainer.class);
        when(seller.getInventory()).thenReturn(inventory);
        when(seller.getPersistentDataContainer()).thenReturn(pdc);
        var held = mock(ItemStack.class);
        var requested = mock(ItemStack.class);
        var remainder = mock(ItemStack.class);
        when(held.getType()).thenReturn(Material.STONE);
        when(requested.getType()).thenReturn(Material.STONE);
        when(held.getAmount()).thenReturn(8);
        when(requested.getAmount()).thenReturn(2);
        when(held.isSimilar(requested)).thenReturn(true);
        when(held.clone()).thenReturn(held, remainder);
        when(requested.clone()).thenReturn(requested);
        when(inventory.getItem(0)).thenReturn(held);
        var serializer = mock(AuctionItemSerializer.class);
        when(serializer.serialize(requested)).thenReturn("snapshot");
        try (var auth = mockStatic(AuthAccess.class);
             var listings = new MarketListings(work, repository, new MarketLedger(repository, Logger.getAnonymousLogger()), null,
                 serializer, new MarketDebits(work, Logger.getAnonymousLogger()), Logger.getAnonymousLogger(), playerData,
                 "source", (save, published) -> {})) {
            var session = new AuthSnapshot(id, UUID.randomUUID(), 1, id, AuthSnapshot.Registration.REGISTERED,
                AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(session);
            var debitsField = MarketListings.class.getDeclaredField("debits");
            debitsField.setAccessible(true);
            var debits = (MarketDebits) debitsField.get(listings);
            var submission = listings.submit(seller, 0, requested,
                new MarketListings.Terms(Kind.AUCTION, 20, "coin", "", 60, "Item", "other", 0, "", 10),
                () -> true, true);
            workers.removeFirst().run();
            debits.drain();
            workers.removeFirst().run();
            callbacks.removeFirst().run();
            assertTrue(submission.staged().join().staged());
            assertFalse(submission.published().isDone());
            verify(remainder).setAmount(6);
            verify(inventory).setItem(0, remainder);
            verify(pdc).set(MarketListingSaves.receipt(Kind.AUCTION), PersistentDataType.STRING, reserved.get().id());
            verify(seller, never()).saveData();

            playerFile(id, Kind.PLAYER_SHOP, reserved.get().id());
            scan.get().run();
            assertFalse(submission.published().isDone());
            playerFile(id, Kind.AUCTION, reserved.get().id());
            scan.get().run();
            if (lostCommitResponse) {
                assertFalse(submission.published().isDone());
                scan.get().run();
                assertFalse(submission.published().isDone());
                scan.get().run();
                verify(repository, times(2)).isListingDraftPublished(reserved.get(), "source");
            } else {
                verify(repository, never()).isListingDraftPublished(any(), anyString());
            }
            assertTrue(submission.published().join());
            verify(repository).publishListingDraft(reserved.get(), "source");
        }
    }

    @Test
    void readablePlayerFileRequiresTheMatchingRemovalReceiptForPublication() throws Exception {
        UUID seller = UUID.randomUUID();
        var row = new Save(Kind.PLAYER_SHOP, "listing", seller, 1, 60_000, MarketListingSaves.SERVER_START);
        var repository = mock(MarketRepository.class);
        when(repository.listingSaves("source", "", 32)).thenReturn(List.of(row));
        when(repository.publishListingDraft(row, "source")).thenReturn(true);
        try (var watcher = new MarketListingSaves(mock(MarketWork.class), repository, Logger.getAnonymousLogger(),
                playerData, "source", (save, published) -> {})) {
            playerFile(seller, null);
            watcher.scan();
            verify(repository, never()).publishListingDraft(any(), anyString());
            verify(repository, never()).abortListingDraft(any(), anyString(), anyString());
            playerFile(seller, row.id());
            watcher.scan();
            verify(repository).publishListingDraft(row, "source");
        }
    }

    @Test
    void coldRestartDoesNotReleaseAnUnreadableFileButReleasesAReadableUnpersistedRemoval() throws Exception {
        UUID seller = UUID.randomUUID();
        var row = new Save(Kind.PLAYER_SHOP, "before-crash", seller, 1, 60_000, MarketListingSaves.SERVER_START - 1);
        var repository = mock(MarketRepository.class);
        when(repository.listingSaves("source", "", 32)).thenReturn(List.of(row));
        try (var watcher = new MarketListingSaves(mock(MarketWork.class), repository, Logger.getAnonymousLogger(),
                playerData, "source", (save, published) -> {})) {
            watcher.scan();
            verify(repository, never()).abortListingDraft(any(), anyString(), anyString());
            playerFile(seller, null);
            watcher.scan();
            verify(repository).abortListingDraft(eq(row), eq("source"), anyString());
            verify(repository, never()).publishListingDraft(any(), anyString());
        }
    }

    private void playerFile(UUID seller, String receipt) throws Exception {
        playerFile(seller, Kind.PLAYER_SHOP, receipt);
    }

    private void playerFile(UUID seller, Kind kind, String receipt) throws Exception {
        try (var out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(playerData.resolve(seller + ".dat"))))) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(10);
            out.writeUTF("BukkitValues");
            if (receipt != null) {
                out.writeByte(8);
                out.writeUTF(MarketListingSaves.receipt(kind).toString());
                out.writeUTF(receipt);
            }
            out.writeByte(0);
            out.writeByte(0);
        }
    }
}
