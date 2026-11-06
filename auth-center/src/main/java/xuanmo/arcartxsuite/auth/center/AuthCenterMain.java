package xuanmo.arcartxsuite.auth.center;

import java.nio.channels.*;
import java.nio.file.*;
import java.sql.*;
import java.util.concurrent.CountDownLatch;
import xuanmo.arcartxsuite.auth.engine.JdbcAuthStore;

/** Independent process. No Minecraft plugin loading, native security bridge, or shared host pool. */
public final class AuthCenterMain {
    private AuthCenterMain() {}
    public static void main(String[] arguments) throws Exception {
        if(arguments.length!=1) throw new IllegalArgumentException("Usage: auth-center <absolute-config.json>");
        Path path=Path.of(arguments[0]);
        if(!path.isAbsolute()) throw new IllegalArgumentException("Configuration path must be absolute");
        CenterConfiguration config=CenterConfiguration.load(path);
        PrivateStateDirectory.prepare(config.database().getParent());
        if(Files.exists(config.database(),LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(config.database(),LinkOption.NOFOLLOW_LINKS))
            throw new IllegalStateException("Center database cannot be a link or special file");
        Path lock=config.database().resolveSibling(config.database().getFileName()+".lock");
        try(FileChannel channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                FileLock held=channel.tryLock()) {
            if(held==null) throw new IllegalStateException("Database already owned by another center");
            Class.forName("org.sqlite.JDBC");
            JdbcAuthStore.Connections connections=()->{
                Connection connection=DriverManager.getConnection("jdbc:sqlite:"+config.database());
                try(Statement statement=connection.createStatement()) { statement.execute("PRAGMA busy_timeout=2000"); }
                catch(SQLException e) { connection.close(); throw e; }
                return connection;
            };
            var service=new CenterService(config,connections);
            try(var server=new AuthCenterServer(config,service);
                    var external=config.external()==null?null:new ExternalAuthServer(config.external(),service)) {
                CountDownLatch stopped=new CountDownLatch(1);
                Thread shutdown=new Thread(()->{
                    try { if(external!=null) external.close(); }
                    finally { try { server.close(); } finally { stopped.countDown(); } }
                },"AXS-AuthCenter-Shutdown");
                Runtime.getRuntime().addShutdownHook(shutdown);
                server.start();
                if(external!=null) external.start();
                System.out.println("AXS 认证中心已启动，监听 127.0.0.1:"+config.port()+"；远程节点需经 HTTPS 反向代理接入。");
                stopped.await();
                try { Runtime.getRuntime().removeShutdownHook(shutdown); } catch(IllegalStateException ignored) { }
            }
        }
    }
}
