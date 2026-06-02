package xuanmo.arcartxsuite.config.diagnostic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.logging.Logger;

/**
 * 清理过期的 diagnosis / backup 目录。
 */
public final class RetentionCleaner {

    private final Logger logger;
    private final Duration diagnosisRetention;
    private final Duration backupRetention;

    public RetentionCleaner(Logger logger, Duration diagnosisRetention, Duration backupRetention) {
        this.logger = logger;
        this.diagnosisRetention = diagnosisRetention;
        this.backupRetention = backupRetention;
    }

    public void cleanup(File pluginDataFolder) {
        cleanDir(new File(pluginDataFolder, "diagnosis"), diagnosisRetention);
        cleanDir(new File(pluginDataFolder, "backup"), backupRetention);
    }

    private void cleanDir(File root, Duration retention) {
        if (!root.isDirectory()) {
            return;
        }
        Instant cutoff = Instant.now().minus(retention);
        File[] children = root.listFiles(File::isDirectory);
        if (children == null) {
            return;
        }
        for (File child : children) {
            try {
                Instant lastModified = Instant.ofEpochMilli(child.lastModified());
                if (lastModified.isBefore(cutoff)) {
                    deleteRecursive(child.toPath());
                    logger.fine("已清理过期目录: " + child);
                }
            } catch (IOException exception) {
                logger.warning("清理失败 " + child + ": " + exception.getMessage());
            }
        }
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walker = Files.walk(path)) {
            walker.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
