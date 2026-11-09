package xuanmo.arcartxsuite.api.auth;

import java.io.PrintWriter;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Host-lifetime retirement fence for durable auth consumers, shared across module class loaders. */
public final class AuthConsumerStore implements DataSource {
    private static final Map<String,CompletableFuture<Void>> RETIRED=new HashMap<>();
    private static final Map<String,AuthConsumerStore> ACTIVE=new HashMap<>();
    private static final ExecutorService CLOSER=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(64),
        r->{ Thread t=new Thread(r,"AXS-AuthStoreClose"); t.setDaemon(true); return t; },new ThreadPoolExecutor.AbortPolicy());
    private final String name;
    private final DataSource source;
    private final CompletableFuture<Void> returned=new CompletableFuture<>(),retired=new CompletableFuture<>();
    private int borrowed;
    private boolean sealed;
    private final ThreadLocal<Boolean> finalizing=ThreadLocal.withInitial(()->false);
    private AuthConsumerStore(String name,DataSource source) { this.name=name; this.source=Objects.requireNonNull(source); }
    public static synchronized void requireReady(String name) {
        var previous=RETIRED.get(name);
        if(ACTIVE.containsKey(name) || previous!=null && (!previous.isDone() || previous.isCompletedExceptionally()))
            throw new IllegalStateException(name+" 的旧数据库请求尚未释放，请稍后重新加载模块");
    }
    public static synchronized AuthConsumerStore open(String name,DataSource source) {
        requireReady(name); var store=new AuthConsumerStore(name,source); ACTIVE.put(name,store); return store;
    }
    private synchronized void reserve() throws SQLException {
        if(sealed && !finalizing.get()) throw new SQLException("Authentication consumer store retired");
        borrowed++;
    }
    private synchronized void release() { if(--borrowed==0 && sealed) returned.complete(null); }
    @FunctionalInterface private interface Open { Connection get() throws SQLException; }
    private Connection borrow(Open open) throws SQLException {
        reserve();
        Connection connection;
        try { connection=open.get(); } catch(SQLException | RuntimeException | Error e) { release(); throw e; }
        var closed=new AtomicBoolean();
        return (Connection)Proxy.newProxyInstance(AuthConsumerStore.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            if(method.getDeclaringClass()==Object.class) return switch(method.getName()) {
                case "equals" -> proxy==args[0]; case "hashCode" -> System.identityHashCode(proxy);
                default -> "AuthConsumerConnection[closed="+closed.get()+"]";
            };
            if(method.getName().equals("isClosed") && closed.get()) return true;
            if(method.getName().equals("close")) {
                if(closed.compareAndSet(false,true)) try { connection.close(); } finally { release(); }
                return null;
            }
            if(method.getName().equals("unwrap")) {
                if(((Class<?>)args[0]).isInstance(proxy)) return proxy;
                throw new SQLFeatureNotSupportedException("Raw connections cannot escape the retirement fence");
            }
            if(method.getName().equals("isWrapperFor")) return ((Class<?>)args[0]).isInstance(proxy);
            if(closed.get() && !method.getName().equals("isClosed")) throw new SQLException("Connection closed");
            try { return method.invoke(connection,args); } catch(InvocationTargetException e) { throw e.getCause(); }
        });
    }
    /** Idle stores close immediately. Active SQL drains and releases its pool off the server thread. */
    public CompletionStage<Void> retire(Runnable closePool) {
        return retire(CompletableFuture.completedFuture(null),null,closePool);
    }
    /** A final snapshot flush may borrow only on the retirement worker, after old operations have drained. */
    public CompletionStage<Void> retire(CompletionStage<Void> operations,Runnable finalFlush,Runnable closePool) {
        boolean idle;
        synchronized(this) {
            if(sealed) return retired;
            sealed=true; idle=borrowed==0;
            if(idle) returned.complete(null);
        }
        synchronized(AuthConsumerStore.class) { ACTIVE.remove(name,this); RETIRED.put(name,retired); }
        Runnable finish=()->{
            Throwable failure=null;
            try { if(finalFlush!=null) { finalizing.set(true); finalFlush.run(); } }
            catch(RuntimeException | LinkageError e) { failure=e; }
            finally { finalizing.remove(); }
            try { closePool.run(); } catch(RuntimeException | LinkageError e) { failure=e; }
            if(failure==null) retired.complete(null); else retired.completeExceptionally(failure);
        };
        if(idle && finalFlush==null && operations.toCompletableFuture().isDone()) finish.run();
        else CompletableFuture.allOf(returned,operations.toCompletableFuture()).whenComplete((ignored,error)->{
            if(error!=null) { retired.completeExceptionally(error); return; }
            try { CLOSER.execute(finish); } catch(RejectedExecutionException e) { retired.completeExceptionally(e); }
        });
        return retired;
    }
    @Override public Connection getConnection() throws SQLException { return borrow(source::getConnection); }
    @Override public Connection getConnection(String user,String password) throws SQLException { return borrow(()->source.getConnection(user,password)); }
    @Override public PrintWriter getLogWriter() throws SQLException { return source.getLogWriter(); }
    @Override public void setLogWriter(PrintWriter writer) throws SQLException { source.setLogWriter(writer); }
    @Override public void setLoginTimeout(int seconds) throws SQLException { source.setLoginTimeout(seconds); }
    @Override public int getLoginTimeout() throws SQLException { return source.getLoginTimeout(); }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return source.getParentLogger(); }
    @Override public <T> T unwrap(Class<T> type) throws SQLException {
        if(type.isInstance(this)) return type.cast(this);
        throw new SQLFeatureNotSupportedException("Raw data source cannot escape the retirement fence");
    }
    @Override public boolean isWrapperFor(Class<?> type) { return type.isInstance(this); }
}
