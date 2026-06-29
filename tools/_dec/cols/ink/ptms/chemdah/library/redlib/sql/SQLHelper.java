/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.library.redlib.sql;

import ink.ptms.chemdah.library.redlib.Task;
import ink.ptms.chemdah.library.redlib.sql.SQLCache;
import java.io.Closeable;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

public class SQLHelper
implements Closeable {
    private Connection connection;
    private final List<SQLCache> caches = new ArrayList<SQLCache>();
    private Task commitTask = null;

    public static Connection openSQLite(Path file) {
        try {
            Class.forName("org.sqlite.JDBC");
            Properties properties = new Properties();
            properties.setProperty("foreign_keys", "on");
            properties.setProperty("busy_timeout", "1000");
            return DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath(), properties);
        }
        catch (ClassNotFoundException | SQLException e) {
            SQLHelper.sneakyThrow(e);
            return null;
        }
    }

    public static Connection openMySQL(String ip, int port, String username, String password, String database) {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            Connection connection = DriverManager.getConnection("jdbc:mysql://" + ip + ":" + port + "/?user=" + username + "&password=" + password);
            connection.createStatement().execute("CREATE DATABASE IF NOT EXISTS " + database + ";");
            connection.createStatement().execute("USE " + database + ";");
            return connection;
        }
        catch (ClassNotFoundException | SQLException e) {
            SQLHelper.sneakyThrow(e);
            return null;
        }
    }

    public static Connection openMySQL(String username, String password, String database) {
        return SQLHelper.openMySQL("localhost", 3306, username, password, database);
    }

    private static <T extends Exception> void sneakyThrow(Exception e) throws T {
        throw e;
    }

    public SQLHelper(Connection connection) {
        this.connection = connection;
    }

    public SQLCache createCache(String tableName, String columnName, String ... primaryKeyNames) {
        SQLCache cache = new SQLCache(this, tableName, columnName, primaryKeyNames);
        this.caches.add(cache);
        return cache;
    }

    public void flushMatchingCaches(String pattern, Object ... primaryKeys) {
        this.getMatchingCaches(pattern).forEach(c -> c.flush(primaryKeys));
    }

    public void removeFromMatchingCaches(String pattern, Object ... primaryKeys) {
        this.getMatchingCaches(pattern).forEach(c -> c.remove(primaryKeys));
    }

    public void flushAndRemoveFromMatchingCaches(String pattern, Object ... primaryKeys) {
        List<SQLCache> caches = this.getMatchingCaches(pattern);
        caches.forEach(c -> c.flush(primaryKeys));
        caches.forEach(c -> c.remove(primaryKeys));
    }

    public List<SQLCache> getMatchingCaches(String pattern) {
        ArrayList<SQLCache> list2 = new ArrayList<SQLCache>();
        String[] split = pattern.split("\\.");
        if (split.length != 2) {
            throw new IllegalArgumentException("Pattern to match caches must match tableName.columnName (use * to match all of either)");
        }
        String[] tableName = split[0].split("\\|");
        String[] columnName = split[1].split("\\|");
        for (SQLCache cache : this.caches) {
            if (!tableName[0].equals("*") && !Arrays.stream(tableName).anyMatch(s -> s.equals(cache.getTableName())) || !columnName[0].equals("*") && !cache.keyNamesMatch(columnName)) continue;
            list2.add(cache);
        }
        return list2;
    }

    public List<SQLCache> getCaches() {
        return this.caches;
    }

    public void flushAllCaches() {
        this.caches.forEach(SQLCache::flush);
    }

    public void clearAllCaches() {
        this.caches.forEach(SQLCache::clear);
    }

    public void execute(String command, Object ... fields) {
        try {
            PreparedStatement statement = this.prepareStatement(command, fields);
            statement.execute();
            statement.close();
        }
        catch (SQLException e) {
            SQLHelper.sneakyThrow(e);
        }
    }

    public int executeUpdate(String command, Object ... fields) {
        int updatedRows = 0;
        try {
            PreparedStatement statement = this.prepareStatement(command, fields);
            updatedRows = statement.executeUpdate();
            statement.close();
        }
        catch (SQLException e) {
            SQLHelper.sneakyThrow(e);
        }
        return updatedRows;
    }

    public <T> T querySingleResult(String query, Object ... fields) {
        try {
            PreparedStatement statement = this.prepareStatement(query, fields);
            ResultSet results = statement.executeQuery();
            if (!results.next()) {
                return null;
            }
            Object obj = results.getObject(1);
            results.close();
            statement.close();
            return (T)obj;
        }
        catch (SQLException e) {
            SQLHelper.sneakyThrow(e);
            return null;
        }
    }

    public String querySingleResultString(String query, Object ... fields) {
        try {
            PreparedStatement statement = this.prepareStatement(query, fields);
            ResultSet results = statement.executeQuery();
            if (!results.next()) {
                return null;
            }
            String val = results.getString(1);
            results.close();
            statement.close();
            return val;
        }
        catch (SQLException e) {
            SQLHelper.sneakyThrow(e);
            return null;
        }
    }

    public byte[] querySingleResultBytes(String query, Object ... fields) {
        try {
            PreparedStatement statement = this.prepareStatement(query, fields);
            ResultSet results = statement.executeQuery();
            if (!results.next()) {
                return null;
            }
            byte[] val = results.getBytes(1);
            results.close();
            statement.close();
            return val;
        }
        catch (SQLException e) {
            SQLHelper.sneakyThrow(e);
            return null;
        }
    }

    public Long querySingleResultLong(String query, Object ... fields) {
        try {
            PreparedStatement statement = this.prepareStatement(query, fields);
            ResultSet results = statement.executeQuery();
            if (!results.next()) {
                return null;
            }
            long val = results.getLong(1);
            results.close();
            statement.close();
            return val;
        }
        catch (SQLException e) {
            SQLHelper.sneakyThrow(e);
            return null;
        }
    }

    public <T> List<T> queryResultList(String query, Object ... fields) {
        ArrayList<Object> list2 = new ArrayList<Object>();
        try {
            PreparedStatement statement = this.prepareStatement(query, fields);
            ResultSet results = statement.executeQuery();
            while (results.next()) {
                list2.add(results.getObject(1));
            }
            results.close();
            statement.close();
        }
        catch (SQLException e) {
            SQLHelper.sneakyThrow(e);
        }
        return list2;
    }

    public List<String> queryResultStringList(String query, Object ... fields) {
        ArrayList<String> list2 = new ArrayList<String>();
        try {
            PreparedStatement statement = this.prepareStatement(query, fields);
            ResultSet results = statement.executeQuery();
            while (results.next()) {
                list2.add(results.getString(1));
            }
            results.close();
            statement.close();
        }
        catch (SQLException e) {
            SQLHelper.sneakyThrow(e);
        }
        return list2;
    }

    public Results queryResults(String query, Object ... fields) {
        try {
            PreparedStatement statement = this.prepareStatement(query, fields);
            ResultSet results = statement.executeQuery();
            return new Results(results, statement);
        }
        catch (SQLException e) {
            SQLHelper.sneakyThrow(e);
            return null;
        }
    }

    public Connection getConnection() {
        return this.connection;
    }

    public void setAutoCommit(boolean autoCommit) {
        try {
            this.setCommitInterval(-1);
            this.connection.setAutoCommit(autoCommit);
        }
        catch (SQLException e) {
            SQLHelper.sneakyThrow(e);
        }
    }

    public boolean isAutoCommit() {
        try {
            return this.connection.getAutoCommit();
        }
        catch (SQLException e) {
            SQLHelper.sneakyThrow(e);
            return false;
        }
    }

    public void setCommitInterval(int ticks) {
        if (this.commitTask != null) {
            this.commitTask.cancel();
            this.commitTask = null;
        }
        if (ticks == -1) {
            return;
        }
        this.setAutoCommit(false);
        this.commitTask = Task.syncRepeating(this::commit, (long)ticks, (long)ticks);
    }

    public void commit() {
        try {
            this.flushAllCaches();
            this.connection.commit();
        }
        catch (SQLException e) {
            SQLHelper.sneakyThrow(e);
        }
    }

    public PreparedStatement prepareStatement(String query, Object ... fields) {
        try {
            PreparedStatement statement = this.connection.prepareStatement(query);
            int i = 1;
            for (Object object : fields) {
                statement.setObject(i, object);
                ++i;
            }
            return statement;
        }
        catch (SQLException e) {
            SQLHelper.sneakyThrow(e);
            return null;
        }
    }

    @Override
    public void close() {
        try {
            this.setCommitInterval(-1);
            this.connection.close();
            this.connection = null;
            System.gc();
        }
        catch (SQLException e) {
            SQLHelper.sneakyThrow(e);
        }
    }

    public static class Results
    implements AutoCloseable {
        private final ResultSet results;
        private boolean empty;
        private final PreparedStatement statement;

        private Results(ResultSet results, PreparedStatement statement) {
            this.results = results;
            this.statement = statement;
            try {
                this.empty = !results.next();
            }
            catch (SQLException e) {
                SQLHelper.sneakyThrow(e);
            }
        }

        public boolean isEmpty() {
            return this.empty;
        }

        public boolean next() {
            try {
                return this.results.next();
            }
            catch (SQLException e) {
                SQLHelper.sneakyThrow(e);
                return false;
            }
        }

        public void forEach(Consumer<Results> lambda) {
            if (this.isEmpty()) {
                return;
            }
            lambda.accept(this);
            while (this.next()) {
                lambda.accept(this);
            }
            this.close();
        }

        public <T> T get(int column) {
            try {
                return (T)this.results.getObject(column);
            }
            catch (SQLException e) {
                SQLHelper.sneakyThrow(e);
                return null;
            }
        }

        public byte[] getBytes(int column) {
            try {
                return this.results.getBytes(column);
            }
            catch (SQLException e) {
                SQLHelper.sneakyThrow(e);
                return null;
            }
        }

        public String getString(int column) {
            try {
                return this.results.getString(column);
            }
            catch (SQLException e) {
                SQLHelper.sneakyThrow(e);
                return null;
            }
        }

        public Long getLong(int column) {
            try {
                return this.results.getLong(column);
            }
            catch (SQLException e) {
                SQLHelper.sneakyThrow(e);
                return null;
            }
        }

        public int getColumnCount() {
            try {
                return this.results.getMetaData().getColumnCount();
            }
            catch (SQLException e) {
                SQLHelper.sneakyThrow(e);
                return 0;
            }
        }

        @Override
        public void close() {
            try {
                this.results.close();
                this.statement.close();
            }
            catch (SQLException e) {
                SQLHelper.sneakyThrow(e);
            }
        }
    }
}

