package xuanmo.arcartxsuite.cloud;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalModuleFilesTest {
    @TempDir Path root;
    @Test void customerProofIdentifiesTheCurrentEncryptedFiles() throws Exception {
        var files = new LocalModuleFiles(root);
        assertEquals(List.of(), files.customerChecksums());
        byte[] original = new byte[33];
        byte[] replacement = new byte[34];
        var market = root.resolve("market.axb");
        Files.write(market, original);
        Files.write(root.resolve("mail.axb"), original);
        Files.write(root.resolve("debug.jar"), replacement);
        Files.write(root.resolve("truncated.axb"), new byte[31]);
        assertEquals(List.of(checksum(original)), files.customerChecksums());
        Files.write(market, replacement);
        assertEquals(Stream.of(checksum(original), checksum(replacement)).sorted().toList(), files.customerChecksums());
        Files.delete(market);
        assertEquals(List.of(checksum(original)), files.customerChecksums());
    }

    private static String checksum(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    @Test void readsTheInstalledSnapshotAndDoesNotSubstituteAnotherModule() throws Exception {
        byte[] first = new byte[32];
        Files.write(root.resolve("market.axb"), first);
        var files = new LocalModuleFiles(root);
        assertArrayEquals(first, files.read("market"));
        byte[] next = new byte[33];
        next[32] = 1;
        Files.write(root.resolve("market.axb"), next);
        assertArrayEquals(next, files.read("market"));
        assertThrows(IOException.class, () -> files.read("mail"));
    }
    @Test void rejectsMissingTruncatedAndDirectoryInputs() throws Exception {
        var files = new LocalModuleFiles(root);
        assertThrows(IOException.class, () -> files.read("missing"));
        Files.write(root.resolve("tiny.axb"), new byte[31]);
        assertThrows(IOException.class, () -> files.read("tiny"));
        Files.createDirectory(root.resolve("folder.axb"));
        assertThrows(IOException.class, () -> files.read("folder"));
        Path nested = Files.createDirectory(root.resolve("nested"));
        Files.write(root.resolve("escape.axb"), new byte[32]);
        assertThrows(IOException.class, () -> new LocalModuleFiles(nested).read("../escape"));
    }
}
