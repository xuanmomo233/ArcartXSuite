/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.library.redlib.sql;

import ink.ptms.chemdah.library.redlib.sql.SQLCacheEntry;
import ink.ptms.chemdah.library.redlib.sql.SQLHelper;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class SQLCache {
    private final String tableName;
    private final String columnName;
    private final String[] primaryKeyNames;
    private final String deleteQuery;
    private final String selectQuery;
    private final String updateQuery;
    private final Map<SQLCacheEntry, Object> cache = Collections.synchronizedMap(new HashMap());
    private final Set<SQLCacheEntry> modified = Collections.synchronizedSet(new HashSet());
    private final SQLHelper sql;

    protected SQLCache(SQLHelper sql, String tableName, String columnName, String ... primaryKeyNames) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.primaryKeyNames = primaryKeyNames;
        this.deleteQuery = "DELETE FROM " + this.tableName + " WHERE " + this.repeat(primaryKeyNames, " = ?", " AND ");
        this.selectQuery = "SELECT " + columnName + " FROM " + this.tableName + " WHERE " + this.repeat(primaryKeyNames, " = ?", " AND ");
        this.updateQuery = "UPDATE " + this.tableName + " SET " + columnName + " = ? WHERE " + this.repeat(primaryKeyNames, " = ?", " AND ");
        this.sql = sql;
    }

    private String repeat(String[] values, String str, String delimeter) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; ++i) {
            builder.append(values[i]).append(str);
            if (i == values.length - 1) continue;
            builder.append(delimeter);
        }
        return builder.toString();
    }

    public String getTableName() {
        return this.tableName;
    }

    public String getColumnName() {
        return this.columnName;
    }

    public String[] getPrimaryKeyNames() {
        return this.primaryKeyNames;
    }

    protected boolean keyNamesMatch(String[] matches) {
        for (String match : matches) {
            if (!match.equals(this.columnName)) continue;
            return true;
        }
        for (String key : this.primaryKeyNames) {
            for (String match : matches) {
                if (!key.equals(match)) continue;
                return true;
            }
        }
        return false;
    }

    private void checkKeys(Object ... primaryKeys) {
        if (primaryKeys.length != this.primaryKeyNames.length) {
            throw new IllegalArgumentException("Expected " + this.primaryKeyNames.length + " primary keys, got " + primaryKeys.length);
        }
    }

    public synchronized void delete(Object ... primaryKeys) {
        this.remove(primaryKeys);
        this.sql.execute(this.deleteQuery, primaryKeys);
    }

    public synchronized void remove(Object ... primaryKeys) {
        this.checkKeys(primaryKeys);
        SQLCacheEntry entry = new SQLCacheEntry(primaryKeys);
        this.modified.remove(entry);
        this.cache.remove(entry);
    }

    public synchronized void update(Object value2, Object ... primaryKeys) {
        this.checkKeys(primaryKeys);
        SQLCacheEntry entry = new SQLCacheEntry(primaryKeys);
        if (!this.cache.containsKey(entry)) {
            return;
        }
        this.cache.remove(entry);
        this.modified.add(entry);
        this.cache.put(entry, value2);
    }

    public <T> T select(Object ... primaryKeys) {
        return (T)this.select((Object[] o) -> this.sql.querySingleResult(this.selectQuery, primaryKeys), primaryKeys);
    }

    public String selectString(Object ... primaryKeys) {
        return (String)this.select((Object[] o) -> this.sql.querySingleResultString(this.selectQuery, primaryKeys), primaryKeys);
    }

    public Long selectLong(Object ... primaryKeys) {
        return (Long)this.select((Object[] o) -> this.sql.querySingleResultLong(this.selectQuery, primaryKeys), primaryKeys);
    }

    public boolean isCached(Object ... primaryKeys) {
        return this.cache.containsKey(new SQLCacheEntry(primaryKeys));
    }

    private synchronized Object select(Function<Object[], ?> supplier, Object ... primaryKeys) {
        Object value2;
        this.checkKeys(primaryKeys);
        SQLCacheEntry entry = new SQLCacheEntry(primaryKeys);
        if (!this.cache.containsKey(entry)) {
            value2 = supplier.apply(primaryKeys);
            this.cache.put(entry, value2);
        } else {
            value2 = this.cache.get(entry);
        }
        return value2;
    }

    public void clear() {
        this.cache.clear();
    }

    public synchronized void flush() {
        this.modified.forEach(s -> {
            Object val = this.cache.get(s);
            Object[] objs = new Object[s.getParams().length + 1];
            objs[0] = val;
            for (int i = 0; i < s.getParams().length; ++i) {
                objs[i + 1] = s.getParams()[i];
            }
            this.sql.execute(this.updateQuery, objs);
        });
        this.modified.clear();
    }

    public synchronized void flush(Object ... primaryKeys) {
        SQLCacheEntry entry = new SQLCacheEntry(primaryKeys);
        Object val = this.cache.get(entry);
        if (val == null) {
            return;
        }
        Object[] objs = new Object[entry.getParams().length + 1];
        objs[0] = val;
        for (int i = 0; i < entry.getParams().length; ++i) {
            objs[i + 1] = entry.getParams()[i];
        }
        this.sql.execute(this.updateQuery, objs);
        this.modified.remove(entry);
    }
}

