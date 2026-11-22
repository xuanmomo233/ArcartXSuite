package xuanmo.arcartxsuite.storage;

import static org.junit.jupiter.api.Assertions.*;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.*;
import java.net.*;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class JdbcRuntimeTypesTest {
    @Test void preparedProxyCanBeCreatedAfterItsOwningJarCloses() throws Exception {
        try(var loader=new ClosingLibrary()) {
            JdbcRuntimeTypes.prepare(loader);
            Method factory=factory(loader);loader.closed=true;
            Object result=factory.invoke(null,null,null,delegate());
            assertEquals("com.zaxxer.hikari.pool.HikariProxyResultSet",result.getClass().getName());
        }
    }
    @Test void unpreparedFirstResultSetActuallyReproducesTheClosedJarFailure() throws Exception {
        try(var loader=new ClosingLibrary()) {
            Method factory=factory(loader);loader.closed=true;
            var failure=assertThrows(InvocationTargetException.class,()->factory.invoke(null,null,null,delegate()));
            assertInstanceOf(IllegalStateException.class,failure.getCause());
        }
    }
    private Method factory(ClassLoader loader)throws Exception {
        Class<?> type=loader.loadClass("com.zaxxer.hikari.pool.ProxyFactory");
        for(Method method:type.getDeclaredMethods())if(method.getName().equals("getProxyResultSet")){method.setAccessible(true);return method;}
        throw new NoSuchMethodException("getProxyResultSet");
    }
    private ResultSet delegate(){return (ResultSet)java.lang.reflect.Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},(proxy,method,args)->null);}
    private static final class ClosingLibrary extends URLClassLoader {
        volatile boolean closed;
        ClosingLibrary(){super(new URL[]{HikariDataSource.class.getProtectionDomain().getCodeSource().getLocation()},JdbcRuntimeTypesTest.class.getClassLoader());}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(!name.startsWith("com.zaxxer.hikari."))return super.loadClass(name,resolve);
            synchronized(getClassLoadingLock(name)) {
                Class<?> type=findLoadedClass(name);
                if(type==null){if(closed)throw new IllegalStateException("zip file closed");type=findClass(name);}
                if(resolve)resolveClass(type);return type;
            }
        }
    }
}
