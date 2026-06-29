package ink.ptms.chemdah.taboolib.module.database;

import kotlin1822.jvm.functions.Function1;

/**
 * 编译期占位：运行时由 TabooLib 重定位的 module-database 提供。
 * 打包时通过 build.gradle.kts 排除，不会进入最终 JAR。
 */
public class ActionSelect {
    public void rows(String[] rows) {
    }

    public void where(Filterable.Criteria criteria) {
    }

    public Filterable.Criteria eq(String key, Object value) {
        return null;
    }

    public Filterable.Criteria and(Filterable.Criteria a, Filterable.Criteria b) {
        return null;
    }

    public void limit(int limit) {
    }

    public void innerJoin(String table, Function1<JoinFilter, ?> filter) {
    }
}
