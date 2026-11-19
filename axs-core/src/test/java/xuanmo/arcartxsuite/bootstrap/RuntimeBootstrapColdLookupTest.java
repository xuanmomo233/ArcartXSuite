package xuanmo.arcartxsuite.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.*;
import java.net.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeBootstrapColdLookupTest {
    @TempDir Path directory;

    @Test void ownershipLookupDoesNotRecursivelyResolveColdStreamOrLambdaSupport() throws Exception {
        URL code=RuntimeBootstrap.class.getProtectionDomain().getCodeSource().getLocation();
        try(var cold=new ColdLoader(code)) {
            Class<?> artifact=Class.forName(RuntimeBootstrap.class.getName()+"$Artifact",true,cold);
            Constructor<?> artifactConstructor=artifact.getDeclaredConstructor(String.class,String.class,long.class,List.class);
            artifactConstructor.setAccessible(true);
            Object descriptor=artifactConstructor.newInstance("test","0".repeat(64),1L,List.of("com/mysql/"));
            Class<?> type=Class.forName(RuntimeBootstrap.class.getName()+"$Loader",true,cold);
            Constructor<?> constructor=type.getDeclaredConstructor(ClassLoader.class,List.class,Path.class,Path.class);
            constructor.setAccessible(true);
            try(var runtime=(URLClassLoader)constructor.newInstance(ClassLoader.getPlatformClassLoader(),List.of(descriptor),directory,null)) {
                cold.target=runtime;
                cold.owns=type.getMethod("ownsResource",String.class);
                cold.active=true;
                assertEquals(true,cold.owns.invoke(runtime,"com/mysql/cj/Driver.class"));
                assertEquals(false,cold.owns.invoke(runtime,"java/lang/Object.class"));
                assertEquals(false,cold.owns.invoke(runtime,"org/bukkit/Server.class"));
                assertEquals(0,cold.recursiveRequests);
            }
        }
    }

    /** Models PluginClassLoader asking the runtime owner again while resolving a predicate dependency. */
    private static final class ColdLoader extends URLClassLoader {
        boolean active;
        int recursiveRequests;
        Object target;
        Method owns;
        ColdLoader(URL code){super(new URL[]{code},RuntimeBootstrapColdLookupTest.class.getClassLoader());}
        @Override protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(active&&(name.startsWith("java.util.stream.")||name.startsWith("java.util.function."))) {
                if(++recursiveRequests>1)throw new ClassNotFoundException("Recursive bootstrap ownership: "+name);
                try{owns.invoke(target,"java/lang/Object.class");}
                catch(ReflectiveOperationException failure){throw new ClassNotFoundException("Recursive bootstrap ownership",failure);}
            }
            Class<?> result=findLoadedClass(name);
            if(result==null&&name.startsWith(RuntimeBootstrap.class.getName()))result=findClass(name);
            if(result==null)result=super.loadClass(name,false);
            if(resolve)resolveClass(result);
            return result;
        }
    }
}
