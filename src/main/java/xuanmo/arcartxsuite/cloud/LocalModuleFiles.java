package xuanmo.arcartxsuite.cloud;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Reads an encrypted distribution from the existing module directory, without any network fallback. */
final class LocalModuleFiles {
    private static final long MAX_BYTES = 128L * 1024 * 1024;
    private final Path directory;
    LocalModuleFiles(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    byte[] read(String moduleId) throws IOException {
        if (moduleId == null || !moduleId.matches("[a-z][a-z0-9_-]{0,63}")) throw new IOException("Invalid local module ID");
        Path file = directory.resolve(moduleId + ".axb").normalize();
        if (!file.getParent().equals(directory) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Missing local protected module: " + moduleId + ".axb");
        long size = Files.size(file);
        if (size < 32 || size > MAX_BYTES) throw new IOException("Invalid local protected module size");
        byte[] bytes;
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes((int) MAX_BYTES + 1);
        }
        if (bytes.length < 32 || bytes.length > MAX_BYTES) throw new IOException("Invalid local protected module size");
        return bytes;
    }
}
