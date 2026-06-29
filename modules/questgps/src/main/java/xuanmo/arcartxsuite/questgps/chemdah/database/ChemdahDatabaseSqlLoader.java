package xuanmo.arcartxsuite.questgps.chemdah.database;

import ink.ptms.chemdah.Chemdah;
import ink.ptms.chemdah.core.database.Database;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * 尝试加载 Chemdah 付费版 {@code DatabaseSQL} 实现（免费版 JAR 中已移除该类）。
 */
public final class ChemdahDatabaseSqlLoader {

    private ChemdahDatabaseSqlLoader() {
    }

    public static Database tryLoad(Logger logger) {
        try {
            DatabaseMySQL database = new DatabaseMySQL();
            database.setup();
            logger.info("QuestGPS: 已加载内置 DatabaseMySQL 实现（基于 Chemdah 免费版 Relational，无需 patched JAR）");
            return database;
        } catch (Throwable ex) {
            logger.warning(
                "QuestGPS: 内置 DatabaseMySQL 初始化失败，回退到 Chemdah 付费版 DatabaseSQL 反射加载: " + ex
            );
            return tryLoadPatched(logger);
        }
    }

    private static Database tryLoadPatched(Logger logger) {
        try {
            Class<?> clazz = Class.forName("ink.ptms.chemdah.core.database.DatabaseSQL");
            Database database = (Database) clazz.getDeclaredConstructor().newInstance();
            Method setup = clazz.getMethod("setup");
            setup.invoke(database);
            logger.info("QuestGPS: 已加载 Chemdah DatabaseSQL 实现");
            return database;
        } catch (ClassNotFoundException ex) {
            logger.warning(
                "QuestGPS: 未找到 ink.ptms.chemdah.core.database.DatabaseSQL。"
                    + "免费版需使用 Chemdah-1.1.33-FREE-patched.jar，或在 Chemdah 配置中保持 database.use=LOCAL。"
            );
            return null;
        } catch (ReflectiveOperationException ex) {
            logger.warning("QuestGPS: 初始化 DatabaseSQL 失败: " + ex.getMessage());
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
