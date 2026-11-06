package xuanmo.arcartxsuite.auth.minecraft;

import java.nio.file.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProxyKeysTest {
    @TempDir Path directory;
    @Test void existingKeysAreNotRegeneratedOrOverwritten() throws Exception {
        var first=ProxyKeys.loadOrCreate(directory);
        byte[] before=Files.readAllBytes(directory.resolve("auth-forwarding-key.json"));
        var second=ProxyKeys.loadOrCreate(directory);
        assertTrue(Arrays.equals(first.getPublic().getEncoded(),second.getPublic().getEncoded()));
        assertTrue(Arrays.equals(before,Files.readAllBytes(directory.resolve("auth-forwarding-key.json"))),"Existing key material must remain unchanged");
    }
    @Test void malformedExistingKeyFileFailsInsteadOfReplacingIt() throws Exception {
        Files.writeString(directory.resolve("auth-forwarding-key.json"),"invalid");
        assertThrows(Exception.class,()->ProxyKeys.loadOrCreate(directory));
        assertEquals("invalid",Files.readString(directory.resolve("auth-forwarding-key.json")));
    }
}
