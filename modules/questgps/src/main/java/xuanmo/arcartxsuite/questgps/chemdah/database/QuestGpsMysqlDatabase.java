package xuanmo.arcartxsuite.questgps.chemdah.database;

import ink.ptms.chemdah.core.database.Database;
import java.util.logging.Logger;

/**
 * QuestGPS 侧 MySQL 数据库注册入口。
 * <p>
 * 使用 QuestGPS 内置 {@link DatabaseMySQL}（继承 Chemdah 免费版已有的 {@code Relational}），
 * 在免费版上即可使用 MySQL 存档。
 * 经 {@code Chemdah.registerDatabaseImpl} 注册，无需修改 Chemdah 本体。
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
                    + "请确保 Chemdah config.yml 中 database.use=SQL。"
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
