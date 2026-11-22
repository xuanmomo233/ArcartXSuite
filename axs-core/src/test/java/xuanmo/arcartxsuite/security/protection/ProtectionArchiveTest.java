package xuanmo.arcartxsuite.security.protection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionArchiveTest {
    @TempDir
    Path directory;

    @Test
    void partialReleaseStillRequiresVerification() throws IOException {
        assertTrue(requiresVerification("META-INF/BOOTSTRAP.MF"));
        assertTrue(requiresVerification("META-INF/BOOTSTRAP.SIG"));
        assertTrue(requiresVerification("native/axs-native.enc"));
        assertTrue(requiresVerification("native/libaxs-native.enc"));
        assertTrue(requiresVerification("ENCRYPTED/example.class.enc"));
    }

    @Test
    void unsignedDevelopmentCatalogAndNativeRemainSupported() throws IOException {
        assertFalse(requiresVerification("META-INF/axs-libraries.properties", "native/axs-native.dll"));
    }

    private boolean requiresVerification(String... names) throws IOException {
        Path jar = Files.createTempFile(directory, "archive-", ".jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String name : names) {
                output.putNextEntry(new JarEntry(name));
                output.closeEntry();
            }
        }
        try (var archive = new JarFile(jar.toFile())) {
            return ProtectionInit.requiresReleaseVerification(archive);
        }
    }
}
