package xuanmo.arcartxsuite.regions.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.bukkit.configuration.InvalidConfigurationException;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.storage.RegionSnapshots;

public final class RegionBackups {

    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_REGIONS = 4096;

    private final Path directory;

    public RegionBackups(Path dataFolder) {
        directory = dataFolder.resolve("region-backups");
    }

    public Path exportRegions(String name, List<Region> regions) throws IOException {
        Path target = file(name);
        byte[] bytes = RegionSnapshots.write(regions).getBytes(StandardCharsets.UTF_8);
        checkLimits(bytes.length, regions.size());
        Files.createDirectories(directory);
        // CREATE_NEW prevents overwriting a snapshot, including a symbolic link.
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return target;
    }

    public Snapshot load(String name) throws IOException, InvalidConfigurationException {
        Path path = file(name);
        byte[] bytes;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        checkLimits(bytes.length, 0);
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        List<Region> regions = RegionSnapshots.read(text);
        checkLimits(bytes.length, regions.size());
        try {
            String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new Snapshot(checksum.substring(0, 16), List.copyOf(regions));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private Path file(String name) {
        if (!name.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("快照名须为 1 至 64 位英文字母、数字、下划线或连字符，不带扩展名。");
        }
        return directory.resolve(name + ".yml");
    }

    private static void checkLimits(int bytes, int regions) {
        if (bytes > MAX_BYTES || regions > MAX_REGIONS) {
            throw new IllegalArgumentException("快照最多包含 4096 个区域或 4 MiB 数据，请按世界拆分。");
        }
    }

    public record Snapshot(String checksum, List<Region> regions) {}
}
