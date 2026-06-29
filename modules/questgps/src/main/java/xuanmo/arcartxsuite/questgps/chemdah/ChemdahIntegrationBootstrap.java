package xuanmo.arcartxsuite.questgps.chemdah;

import java.util.logging.Logger;
import xuanmo.arcartxsuite.questgps.chemdah.database.QuestGpsDatabaseSettings;
import xuanmo.arcartxsuite.questgps.chemdah.database.QuestGpsMysqlDatabase;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

/**
 * QuestGPS 模块启动时初始化 Chemdah 桥接（数据库、元数据、追踪）。
 */
public final class ChemdahIntegrationBootstrap {

    private final Logger logger;
    private final QuestGpsModuleConfiguration configuration;
    private final ChemdahMetadataReader metadataReader;
    private final ChemdahRewardReader rewardReader;
    private final ChemdahTrackerBridge trackerBridge;

    public ChemdahIntegrationBootstrap(
        Logger logger,
        QuestGpsModuleConfiguration configuration,
        ChemdahMetadataReader metadataReader,
        ChemdahRewardReader rewardReader,
        ChemdahTrackerBridge trackerBridge
    ) {
        this.logger = logger;
        this.configuration = configuration;
        this.metadataReader = metadataReader;
        this.rewardReader = rewardReader;
        this.trackerBridge = trackerBridge;
    }

    public void initialize() {
        registerDatabaseIfNeeded();
        logger.fine(
            "QuestGPS: Chemdah 整合: navigation.mode="
                + configuration.navigation().mode()
                + ", tracker="
                + trackerBridge.available()
        );
    }

    private void registerDatabaseIfNeeded() {
        QuestGpsMysqlDatabase.registerIfNeeded(logger, configuration.database());
    }

    public ChemdahMetadataReader metadataReader() {
        return metadataReader;
    }

    public ChemdahRewardReader rewardReader() {
        return rewardReader;
    }

    public ChemdahTrackerBridge trackerBridge() {
        return trackerBridge;
    }
}
