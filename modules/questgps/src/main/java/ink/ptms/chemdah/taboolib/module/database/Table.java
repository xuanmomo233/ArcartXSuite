package ink.ptms.chemdah.taboolib.module.database;

import javax.sql.DataSource;
import kotlin1822.jvm.functions.Function1;

/**
 * 编译期占位：运行时由 TabooLib 重定位的 module-database 提供。
 * 打包时通过 build.gradle.kts 排除，不会进入最终 JAR。
 */
public class Table<T extends Host<E>, E extends ColumnBuilder> {

    public Table(String name, T host, Function1<Table<T, E>, ?> builder) {
    }

    public String getName() {
        return null;
    }

    public Table<T, E> add(String name, Function1<E, ?> builder) {
        return this;
    }

    public static Table add$default(Table self, String name, Function1 builder, int flags, Object marker) {
        return self;
    }

    public int insert(DataSource dataSource, String[] rows, Function1<ActionInsert, ?> action) {
        return 0;
    }

    public ResultProcessorList select(DataSource dataSource, Function1<ActionSelect, ?> action) {
        return null;
    }

    public int update(DataSource dataSource, Function1<ActionUpdate, ?> action) {
        return 0;
    }

    public static void createTable$default(Table self, DataSource dataSource, boolean checkExists,
                                           int flags, Object marker) {
    }
}
