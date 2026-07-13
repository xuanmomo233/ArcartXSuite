package xuanmo.arcartxsuite.afkreward.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.afkreward.config.AfkRewardConfiguration;

public final class AfkRewardAuditLogger {

    private final File dataFolder;
    private final AfkRewardConfiguration.AuditConfig config;
    private final Logger logger;
    private final Object lock = new Object();

    public AfkRewardAuditLogger(File dataFolder,
                                AfkRewardConfiguration.AuditConfig config,
                                Logger logger) {
        this.dataFolder = dataFolder;
        this.config = config;
        this.logger = logger;
    }

    public void log(org.bukkit.entity.Player player, UUID uuid, String area,
                    String mode, String tier, int count, double multiplier) {
        if (!config.enable()) return;
        String line = OffsetDateTime.now() + " player=" + player.getName()
            + " uuid=" + uuid + " area=" + area + " mode=" + mode
            + " tier=" + tier + " count=" + count
            + " multiplier=" + String.format(java.util.Locale.ROOT, "%.2f", multiplier)
            + System.lineSeparator();
        synchronized (lock) {
            try {
                Path path = new File(dataFolder, config.file()).toPath();
                File parent = path.toFile().getParentFile();
                if (parent != null) parent.mkdirs();
                Files.writeString(path, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                logger.warning("[AfkReward] 写入审计日志失败: " + e.getMessage());
            }
        }
    }
}
