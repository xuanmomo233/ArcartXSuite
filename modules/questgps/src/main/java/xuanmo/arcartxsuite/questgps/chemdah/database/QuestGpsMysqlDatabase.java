package xuanmo.arcartxsuite.questgps.chemdah.database;

import ink.ptms.chemdah.core.database.Database;
import java.util.logging.Logger;

/**
 * QuestGPS 侧 MySQL 数据库注册入口。
 * <p>
 * 通过反射加载 Chemdah 付费版 {@code DatabaseSQL} 实现（免费版 JAR 中已移除该类），
 * 再经 {@code Chemdah.registerDatabaseImpl} 注册，无需修改 Chemdah 本体。
 */
public final class QuestGpsMysqlDatabase {

    private static volatile boolean registered;

    private QuestGpsMysqlDatabase() {
    }

    public static Database tryCreate(Logger logger) {
        return ChemdahDatabaseSqlLoader.tryLoad(logger);
    }

    public static boolean registerIfNeeded(Logger logger, QuestGpsDatabaseSettings settings) {
        if (!settings.enabled()) {
            return false;
        }
        if (registered) {
            return true;
        }
        Database database = tryCreate(logger);
        if (database == null) {
            logger.warning(
                "QuestGPS: database.enabled=true 但无法注册 MySQL。"
                    + "请确保 Chemdah config.yml 中 database.use=SQL，"
                    + "并使用 Chemdah-1.1.33-FREE-patched.jar 或付费版 JAR。"
            );
            return false;
        }
        ChemdahDatabaseSqlLoader.register(logger, database, settings);
        registered = true;
        return true;
    }

    public static boolean isRegistered() {
        return registered;
    }
}
