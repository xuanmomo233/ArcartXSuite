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
    @Test void restoredKeyWithAnotherReadersAccessIsRejectedWithoutChangingIt() throws Exception {
        ProxyKeys.loadOrCreate(directory);
        Path key=directory.resolve("auth-forwarding-key.json");
        byte[] original=Files.readAllBytes(key);
        var posix=Files.getFileAttributeView(key,java.nio.file.attribute.PosixFileAttributeView.class);
        org.junit.jupiter.api.Assumptions.assumeTrue(posix!=null);
        Files.setPosixFilePermissions(key,java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
        assertThrows(IllegalStateException.class,()->ProxyKeys.loadOrCreate(directory));
        assertArrayEquals(original,Files.readAllBytes(key));
    }
    @Test void restoredAclCannotGrantAnotherPrincipalAccess() {
        java.nio.file.attribute.UserPrincipal owner=()->"service",other=()->"other";
        var entry=java.nio.file.attribute.AclEntry.newBuilder().setType(java.nio.file.attribute.AclEntryType.ALLOW)
            .setPrincipal(other).setPermissions(java.nio.file.attribute.AclEntryPermission.READ_DATA).build();
        assertThrows(IllegalStateException.class,()->ProxyKeys.requirePrivateAcl(owner,java.util.List.of(entry)));
    }
}
