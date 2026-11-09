package xuanmo.arcartxsuite.api.auth;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthConsumerStoreTest {
    private DataSource source(AtomicInteger returned) {
        return (DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{DataSource.class},(proxy,method,args)->{
            if(!method.getName().equals("getConnection")) throw new UnsupportedOperationException();
            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(connection,operation,values)->{
                if(operation.getName().equals("close")) { returned.incrementAndGet(); return null; }
                if(operation.getName().equals("isClosed")) return false;
                throw new UnsupportedOperationException();
            });
        });
    }
    @Test void retiredStoreRejectsNewLoansAndCannotBeReplacedUntilExistingConnectionsReturn() throws Exception {
        String key="test-"+UUID.randomUUID(); var returned=new AtomicInteger(); var released=new AtomicBoolean();
        var store=AuthConsumerStore.open(key,source(returned)); Connection connection=store.getConnection();
        var retired=store.retire(()->released.set(true)).toCompletableFuture();
        assertFalse(retired.isDone()); assertFalse(released.get());
        assertThrows(SQLException.class,store::getConnection);
        assertThrows(IllegalStateException.class,()->AuthConsumerStore.requireReady(key));
        connection.close(); connection.close(); retired.get(2,TimeUnit.SECONDS);
        assertEquals(1,returned.get()); assertTrue(released.get()); AuthConsumerStore.requireReady(key);
    }
    @Test void finalFlushRunsOffTheRetiringThreadAfterAuthJobsAndMayBorrowOnlyInItsPrivateScope() throws Exception {
        String key="test-"+UUID.randomUUID(); var store=AuthConsumerStore.open(key,source(new AtomicInteger()));
        var operations=new CompletableFuture<Void>(); var thread=new AtomicReference<Thread>(); var original=Thread.currentThread();
        var retired=store.retire(operations,()->{
            thread.set(Thread.currentThread());
            try(var ignored=store.getConnection()) { } catch(SQLException e) { throw new IllegalStateException(e); }
        },()->{}).toCompletableFuture();
        assertFalse(retired.isDone()); assertThrows(SQLException.class,store::getConnection);
        operations.complete(null); retired.get(2,TimeUnit.SECONDS);
        assertNotSame(original,thread.get()); assertThrows(SQLException.class,store::getConnection);
    }
}
