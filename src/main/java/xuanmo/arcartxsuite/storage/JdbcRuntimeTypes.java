package xuanmo.arcartxsuite.storage;

import com.zaxxer.hikari.HikariDataSource;

/** Links the small generated proxy family before Paper can close the host plugin JAR. */
final class JdbcRuntimeTypes {
    private JdbcRuntimeTypes() {}
    // Class literals retain the correct identities through release obfuscation.
    private static final Class<?>[] PROXIES={
        com.zaxxer.hikari.pool.HikariProxyConnection.class,
        com.zaxxer.hikari.pool.HikariProxyStatement.class,
        com.zaxxer.hikari.pool.HikariProxyPreparedStatement.class,
        com.zaxxer.hikari.pool.HikariProxyCallableStatement.class,
        com.zaxxer.hikari.pool.HikariProxyResultSet.class,
        com.zaxxer.hikari.pool.HikariProxyDatabaseMetaData.class
    };
    private static final class Ready {
        static final boolean VALUE=prepare(HikariDataSource.class.getClassLoader());
    }
    static void prepare() { if(!Ready.VALUE)throw new IllegalStateException("JDBC proxy types unavailable"); }
    static boolean prepare(ClassLoader loader) {
        try {
            for(Class<?> type:PROXIES) {
                Class.forName(type.getName(),true,loader);
            }
            return true;
        }catch(ClassNotFoundException failure){throw new IllegalStateException("Unable to link JDBC proxy types",failure);}
    }
}
