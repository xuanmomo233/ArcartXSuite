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

/**
 * 挂机奖励审计日志记录器。
 * <p>
 * 关联模块：afkreward。在每次发放奖励时将玩家、区域、模式、档位、数量、倍率等信息
 * 追加写入到配置指定的审计文件，便于事后核查。通过内部锁保证多线程写入安全。
 */
public final class AfkRewardAuditLogger {

    private final File dataFolder;
    private final AfkRewardConfiguration.AuditConfig config;
    private final Logger logger;
    private final Object lock = new Object();

    /**
     * 构造审计日志记录器。
     *
     * @param dataFolder 插件数据目录，审计文件相对此目录解析
     * @param config     审计配置（是否启用、文件名）
     * @param logger     用于记录写入失败警告的日志器
     */
    public AfkRewardAuditLogger(File dataFolder,
                                AfkRewardConfiguration.AuditConfig config,
                                Logger logger) {
        this.dataFolder = dataFolder;
        this.config = config;
        this.logger = logger;
    }

    /**
     * 记录一次奖励发放事件。当审计未启用时直接返回。
     *
     * @param player     被奖励的玩家
     * @param uuid       玩家 UUID
     * @param area       区域名称
     * @param mode       挂机模式（REGION / MANUAL）
     * @param tier       奖励档位标识
     * @param count      本次发放数量
     * @param multiplier 本次结算使用的倍率
     */
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
                logger.warning("写入审计日志失败: " + e.getMessage());
            }
        }
    }
}
