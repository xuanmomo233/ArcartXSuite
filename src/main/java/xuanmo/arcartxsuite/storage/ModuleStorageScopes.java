package xuanmo.arcartxsuite.storage;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.sql.DataSource;
import xuanmo.arcartxsuite.api.storage.ModuleStorage;

/** 每个模块保留一个当前实例和一个待启用实例。 */
final class ModuleStorageScopes {
    @FunctionalInterface
    interface Factory {
        OpenedSource open() throws SQLException;
    }

    static final class OpenedSource {
        private final DataSource source;
        private final Runnable close;
        private boolean released;
        private Throwable closeFailure;

        OpenedSource(DataSource source, Runnable close) {
            this.source = source;
            this.close = close;
        }

        DataSource source() { return source; }

        synchronized void release() {
            if (close != null && !released) {
                released = true;
                try { close.run(); }
                catch (RuntimeException | Error failure) { closeFailure = failure; throw failure; }
            }
            if (closeFailure != null) throw new java.util.concurrent.CompletionException(closeFailure);
        }
    }

    private static final class Lane {
        Scope active;
        Scope pending;
    }

    private final Map<String, Lane> lanes = new ConcurrentHashMap<>();
    private final xuanmo.arcartxsuite.api.scheduler.ModuleExecutor io =
        new xuanmo.arcartxsuite.api.scheduler.ModuleExecutor(2, 2, 0, java.util.concurrent.TimeUnit.MILLISECONDS,
        new java.util.concurrent.LinkedBlockingQueue<>(), task -> {
        Thread thread = new Thread(task, "AXS-Storage-Lifecycle");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean stopped;
    private final CompletableFuture<Void> shutdown = new CompletableFuture<>();

    boolean manages(String moduleId) {
        Lane lane = lanes.get(moduleId);
        if (lane == null) return false;
        synchronized (lane) { return lane.active != null || lane.pending != null; }
    }

    <T> CompletableFuture<T> prepare(java.util.function.Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, io);
    }

    synchronized ModuleStorage open(String moduleId, Factory factory) throws SQLException {
        if (stopped) throw new SQLException("存储管理器正在停止");
        Lane lane = lanes.computeIfAbsent(moduleId, ignored -> new Lane());
        synchronized (lane) {
            if (stopped) throw new SQLException("存储管理器正在停止");
            if (lane.pending != null || lane.active != null && !lane.active.closing) {
                throw new SQLException("模块数据源已有活动实例: " + moduleId);
            }
            Scope scope = new Scope(lane, factory);
            lane.pending = scope;
            advance(lane);
            return scope;
        }
    }

    private void advance(Lane lane) {
        if (stopped || lane.active != null || lane.pending == null) return;
        Scope scope = lane.pending;
        lane.pending = null;
        lane.active = scope;
        try {
            io.execute(scope::initialize);
        } catch (RejectedExecutionException failure) {
            scope.initialized.completeExceptionally(failure);
            scope.ready.completeExceptionally(failure);
        }
    }

    void forceClose(String moduleId) {
        Lane lane = lanes.get(moduleId);
        if (lane == null) return;
        Scope active;
        Scope pending;
        synchronized (lane) {
            active = lane.active;
            pending = lane.pending;
        }
        if (active != null) active.forceClose();
        if (pending != null) pending.forceClose();
    }

    CompletionStage<Void> shutdown() { return shutdown(() -> { }); }

    CompletionStage<Void> shutdown(Runnable closeShared) {
        var scopes = new java.util.ArrayList<Scope>();
        var waiting = new java.util.ArrayList<CompletableFuture<?>>();
        synchronized (this) {
            if (stopped) return shutdown.copy();
            stopped = true;
            for (Lane lane : lanes.values()) {
                synchronized (lane) {
                    if (lane.active != null) scopes.add(lane.active);
                    if (lane.pending != null) {
                        scopes.add(lane.pending);
                        lane.pending.initialized.complete(null);
                        lane.pending = null;
                    }
                }
            }
            for (Scope scope : scopes) {
                synchronized (scope.lane) {
                    scope.closing = true;
                    scope.connectionsClosed = true;
                    scope.forced = true;
                    scope.ready.completeExceptionally(new SQLException("存储管理器正在停止"));
                    waiting.add(scope.initialized.handle((value, failure) -> null));
                    waiting.add(scope.preparation.handle((value, failure) -> null));
                    waiting.add(scope.connectionsIdle);
                }
            }
        }
        var released = new CompletableFuture<Void>();
        CompletableFuture.allOf(waiting.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) ->
            io.execute(() -> {
                Throwable problem = failure;
                for (Scope scope : scopes) {
                    try { scope.forceClose(); }
                    catch (Throwable error) { if (problem == null) problem = error; else problem.addSuppressed(error); }
                }
                try { closeShared.run(); }
                catch (Throwable error) { if (problem == null) problem = error; else problem.addSuppressed(error); }
                if (problem == null) released.complete(null); else released.completeExceptionally(problem);
                io.shutdown();
            }));
        CompletableFuture.allOf(released, io.stopped().toCompletableFuture()).whenComplete((ignored, failure) -> {
            if (failure == null) shutdown.complete(null); else shutdown.completeExceptionally(failure);
        });
        return shutdown.copy();
    }

    private final class Scope implements ModuleStorage {
        private final Lane lane;
        private final Factory factory;
        private final CompletableFuture<DataSource> ready = new CompletableFuture<>();
        private final CompletableFuture<OpenedSource> initialized = new CompletableFuture<>();
        private final CompletableFuture<Void> retired = new CompletableFuture<>();
        private final DataSource facade = new ScopedDataSource(this);
        private volatile boolean closing;
        private volatile boolean connectionsClosed;
        private volatile boolean forced;
        private boolean retirementRegistered;
        private CompletableFuture<Void> preparation = CompletableFuture.completedFuture(null);
        private volatile OpenedSource opened;
        private int borrowed;
        private CompletableFuture<Void> connectionsIdle = CompletableFuture.completedFuture(null);

        Scope(Lane lane, Factory factory) {
            this.lane = lane;
            this.factory = factory;
        }

        private void initialize() {
            if (connectionsClosed || stopped) {
                initialized.complete(null);
                ready.completeExceptionally(new SQLException("模块数据源已退役"));
                return;
            }
            try {
                OpenedSource result = factory.open();
                opened = result;
                if (stopped || forced) release(result);
                initialized.complete(result);
                if (!connectionsClosed && !stopped) ready.complete(result.source());
                else ready.completeExceptionally(new SQLException("模块数据源已退役"));
            } catch (SQLException | RuntimeException | Error failure) {
                initialized.completeExceptionally(failure);
                ready.completeExceptionally(failure);
                if (failure instanceof Error error && !(error instanceof LinkageError)) throw error;
            }
        }

        @Override public DataSource dataSource() { return facade; }
        @Override public CompletionStage<Void> ready() { return ready.thenApply(ignored -> null); }
        @Override public CompletionStage<Void> retired() { return retired.copy(); }

        @Override public CompletionStage<Void> initialize(Initializer initializer) {
            synchronized (lane) {
                if (stopped || closing) return CompletableFuture.failedFuture(new SQLException("模块数据源已退役"));
                preparation = preparation.thenCombine(ready, (ignored, dataSource) -> null).thenRunAsync(() -> {
                    try { initializer.run(); }
                    catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
                }, io);
                return preparation.copy();
            }
        }

        @Override public void retireAfter(CompletionStage<?> databaseStopped) {
            boolean pending;
            CompletableFuture<Void> prepared;
            synchronized (lane) {
                if (retirementRegistered) return;
                retirementRegistered = true;
                closing = true;
                prepared = preparation.handle((value, failure) -> null);
                pending = lane.pending == this;
                if (pending) {
                    lane.pending = null;
                    connectionsClosed = true;
                    ready.completeExceptionally(new SQLException("模块数据源已退役"));
                }
            }
            var idle = databaseStopped.handle((value, failure) -> null).thenCombine(prepared, (work, startup) -> null);
            if (pending) {
                initialized.complete(null);
                idle.thenRun(() -> retired.complete(null));
                return;
            }
            idle.thenCompose(ignored -> {
                    synchronized (lane) {
                        connectionsClosed = true;
                        ready.completeExceptionally(new SQLException("模块数据源已退役"));
                        return connectionsIdle;
                    }
                })
                .thenCombine(initialized.handle((value, failure) -> value), (ignored, source) -> source)
                .whenComplete((source, failure) -> {
                    if (stopped || forced) {
                        finishRetirement(null);
                        return;
                    }
                    try {
                        io.execute(() -> finishRetirement(source));
                    } catch (RejectedExecutionException rejected) {
                        retired.completeExceptionally(rejected);
                    }
                });
        }

        private void finishRetirement(OpenedSource source) {
            Throwable failure = null;
            try {
                release(source);
            } catch (RuntimeException | Error error) {
                failure = error;
            } finally {
                synchronized (lane) {
                    if (lane.active == this) lane.active = null;
                    advance(lane);
                }
            }
            if (failure == null) retired.complete(null);
            else retired.completeExceptionally(failure);
            if (failure instanceof Error error && !(error instanceof LinkageError)) throw error;
        }

        private void forceClose() {
            closing = true;
            connectionsClosed = true;
            forced = true;
            ready.completeExceptionally(new SQLException("模块数据源已关闭"));
            release(opened);
        }

        private DataSource delegate() throws SQLException {
            if (connectionsClosed || stopped) throw new SQLException("模块数据源已退役");
            try {
                DataSource source = ready.get();
                if (connectionsClosed || stopped) throw new SQLException("模块数据源已退役");
                return source;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new SQLException("等待模块数据源已取消", interrupted);
            } catch (ExecutionException failure) {
                throw new SQLException("模块数据源不可用", failure.getCause());
            }
        }

        private Connection borrow(String user, String password, boolean credentials) throws SQLException {
            synchronized (lane) {
                if (connectionsClosed || stopped) throw new SQLException("模块数据源已退役");
                if (borrowed++ == 0) connectionsIdle = new CompletableFuture<>();
            }
            Connection acquired = null;
            try {
                DataSource source = delegate();
                acquired = credentials ? source.getConnection(user, password) : source.getConnection();
                if (connectionsClosed || stopped) {
                    throw new SQLException("模块数据源已退役");
                }
                Connection actual = acquired;
                AtomicBoolean closed = new AtomicBoolean();
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("close")) {
                            if (closed.compareAndSet(false, true)) {
                                try { actual.close(); } finally { returned(); }
                            }
                            return null;
                        }
                        try {
                            return method.invoke(actual, args);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
            } catch (SQLException | RuntimeException | Error failure) {
                try {
                    if (acquired != null) acquired.close();
                } catch (SQLException | RuntimeException | Error closeFailure) {
                    if (closeFailure != failure) failure.addSuppressed(closeFailure);
                } finally {
                    returned();
                }
                throw failure;
            }
        }

        private void returned() {
            CompletableFuture<Void> idle = null;
            synchronized (lane) {
                if (--borrowed == 0) idle = connectionsIdle;
            }
            if (idle != null) idle.complete(null);
        }
    }

    private static void release(OpenedSource source) {
        if (source != null) source.release();
    }

    private static final class ScopedDataSource implements DataSource {
        private final Scope scope;
        ScopedDataSource(Scope scope) { this.scope = scope; }
        @Override public Connection getConnection() throws SQLException { return scope.borrow(null, null, false); }
        @Override public Connection getConnection(String user, String password) throws SQLException {
            return scope.borrow(user, password, true);
        }
        @Override public PrintWriter getLogWriter() throws SQLException { return scope.delegate().getLogWriter(); }
        @Override public void setLogWriter(PrintWriter writer) throws SQLException { scope.delegate().setLogWriter(writer); }
        @Override public void setLoginTimeout(int timeout) throws SQLException { scope.delegate().setLoginTimeout(timeout); }
        @Override public int getLoginTimeout() throws SQLException { return scope.delegate().getLoginTimeout(); }
        @Override public Logger getParentLogger() { return Logger.getLogger("AXS-Module-Storage"); }
        @Override public <T> T unwrap(Class<T> type) throws SQLException {
            return type.isInstance(this) ? type.cast(this) : scope.delegate().unwrap(type);
        }
        @Override public boolean isWrapperFor(Class<?> type) throws SQLException {
            return type.isInstance(this) || scope.delegate().isWrapperFor(type);
        }
    }
}
