package xuanmo.arcartxsuite.fishing.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.BaitDeposit;

class FishingBaitDepositsTest {
    @TempDir Path directory;

    @Test
    void checksTheSourceSaveBeforePublishingOrReleasingADeposit() throws Exception {
        var repository = mock(FishingRepository.class);
        var published = new ArrayList<BaitDeposit>();
        var deposits = new FishingBaitDeposits(repository, null, directory, directory, Logger.getAnonymousLogger(), published::add);
        UUID uuid = UUID.randomUUID();
        var current = new BaitDeposit(uuid, UUID.randomUUID(), "node", FishingBaitDeposits.BOOT, 0, "worm", 2);
        var older = new BaitDeposit(uuid, UUID.randomUUID(), "node", FishingBaitDeposits.BOOT - 1, 0, "worm", 2);
        Path file = directory.resolve(uuid + ".dat");

        assertThrows(NoSuchFileException.class, () -> deposits.inspect(older));
        verifyNoInteractions(repository);
        playerData(file, "old-receipt");
        deposits.inspect(current);
        verifyNoInteractions(repository);
        deposits.inspect(older);
        verify(repository).cancelBaitDeposit(older);
        verify(repository, never()).publishBaitDeposit(any());

        playerData(file, current.request().toString());
        when(repository.publishBaitDeposit(current)).thenReturn(true);
        deposits.inspect(current);
        org.junit.jupiter.api.Assertions.assertEquals(List.of(current), published);
    }

    private static void playerData(Path file, String marker) throws Exception {
        try (var output = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            output.writeByte(10);
            output.writeUTF("");
            output.writeByte(10);
            output.writeUTF("BukkitValues");
            output.writeByte(8);
            output.writeUTF(FishingBaitDeposits.RECEIPT.toString());
            output.writeUTF(marker);
            output.writeByte(0);
            output.writeByte(0);
        }
    }
}
