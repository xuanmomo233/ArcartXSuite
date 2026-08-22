package xuanmo.arcartxsuite.questgps.chemdah.database;

import ink.ptms.chemdah.Chemdah;
import ink.ptms.chemdah.core.database.Database;
import java.util.logging.Logger;

/**
 * 加载 QuestGPS 内置的 Chemdah MySQL 数据库实现。
 * <p>
 * 基于 Chemdah 免费版已有的 {@code Relational} 基类，无需任何修改版 JAR。
 */
public final class ChemdahDatabaseSqlLoader {

    private ChemdahDatabaseSqlLoader() {
    }

    public static Database tryLoad(Logger logger) {
        try {
            DatabaseMySQL database = new DatabaseMySQL();
            database.setup();
            logger.info("QuestGPS: 已加载内置 DatabaseMySQL 实现（基于 Chemdah 免费版 Relational）");
            return database;
        } catch (Throwable ex) {
            logger.warning("QuestGPS: 内置 DatabaseMySQL 初始化失败: " + ex);
            return null;
        }
    }

    public static void register(Logger logger, Database database, QuestGpsDatabaseSettings settings) {
        if (database == null) {
            return;
        }
        Database.Companion companion = Database.Companion;
        companion.setLoadInJoinEvent(settings.loadInJoinEvent());
        companion.setReleaseInQuitEvent(settings.releaseInQuitEvent());
        companion.setDisableAutoSave(settings.disableAutoSave());
        companion.setDisableAutoCreateTable(settings.disableAutoCreateTable());
        Chemdah.INSTANCE.registerDatabaseImpl(database);
        logger.info(
            "QuestGPS: 已通过 registerDatabaseImpl 注册 MySQL Database"
                + " (join-load=" + settings.loadInJoinEvent()
                + ", quit-release=" + settings.releaseInQuitEvent() + ")"
        );
    }
}
