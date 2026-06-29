package ink.ptms.chemdah.taboolib.module.database;

import kotlin1822.jvm.functions.Function1;

/**
 * 编译期占位：运行时由 TabooLib 重定位的 module-database 提供。
 * 打包时通过 build.gradle.kts 排除，不会进入最终 JAR。
 */
public class SQL extends ColumnSQL {
    public void id() {
    }

    public void type(ColumnTypeSQL type, int length, int index, Function1<SQL, ?> columnBuilder) {
    }

    public static void type$default(SQL self, ColumnTypeSQL type, int length, int index,
                                    Function1 columnBuilder, int flags, Object marker) {
    }
}
