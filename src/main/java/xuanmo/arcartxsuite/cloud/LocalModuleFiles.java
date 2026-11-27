package xuanmo.arcartxsuite.cloud;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Reads an encrypted distribution from the existing module directory, without any network fallback. */
final class LocalModuleFiles {
    private static final long MAX_BYTES = 128L * 1024 * 1024;
    private static final Pattern PROTECTED_FILE = Pattern.compile("([a-z][a-z0-9_-]{0,63})\\.axb");
    private final Path directory;
    private Map<Path, Digest> digests = Map.of();

    private record Digest(BasicFileAttributes file, String checksum) {
        boolean matches(BasicFileAttributes current) {
            return file.size() == current.size() && file.lastModifiedTime().equals(current.lastModifiedTime())
                && Objects.equals(file.fileKey(), current.fileKey());
        }
    }

    LocalModuleFiles(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }

    synchronized List<String> customerChecksums() throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        var updated = new HashMap<Path, Digest>();
        var checksums = new TreeSet<String>();
        try (var entries = Files.list(directory)) {
            var iterator = entries.iterator();
            while (iterator.hasNext()) {
                Path entry = iterator.next();
                var filename = PROTECTED_FILE.matcher(entry.getFileName().toString());
                if (!filename.matches() || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) continue;
                var attributes = Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.size() < 32 || attributes.size() > MAX_BYTES) continue;
                var digest = digests.get(entry);
                if (digest == null || !digest.matches(attributes)) {
                    byte[] hash;
                    try {
                        hash = MessageDigest.getInstance("SHA-256").digest(read(filename.group(1)));
                    } catch (NoSuchAlgorithmException unavailable) {
                        throw new IllegalStateException("SHA-256 unavailable", unavailable);
                    }
                    digest = new Digest(attributes, HexFormat.of().formatHex(hash));
                }
                updated.put(entry, digest);
                checksums.add(digest.checksum());
                if (checksums.size() > 64 || updated.size() > 64) {
                    throw new IOException("Too many local protected modules for one customer package");
                }
            }
        }
        digests = updated;
        return List.copyOf(checksums);
    }

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
