package xuanmo.arcartxsuite.security.protection;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedApiClassLoaderTest {
    @TempDir Path directory;
    static final String API="xuanmo.arcartxsuite.api.exportprobe.Shared";

    @Test void publicApiHasOnePluginOwnerAndPrivateImplementationKeepsProtectionOwner() throws Exception {
        Path file=fixture(false);
        try(var plugin=new PluginOwner(file);var jar=new JarFile(file.toFile())) {
            var protection=new ProtectedClassLoader(getClass().getClassLoader(),jar,Map.of(),plugin,null);
            plugin.injected=protection;
            Class<?> exported=plugin.export(API);
            assertNotNull(exported);assertSame(plugin,exported.getClassLoader());
            assertSame(exported,protection.loadClass(API));
            assertSame(plugin,plugin.export(API+"$Reply").getClassLoader());
            Object reply=exported.getMethod("reply").invoke(null);
            assertSame(plugin.export(API+"$Reply"),reply.getClass());
            Object implementation=exported.getMethod("implementation").invoke(null);
            assertSame(protection,implementation.getClass().getClassLoader());
        }
    }

    @Test void eagerDefinitionDoesNotRunInitializersAndSurvivesPluginJarClosing() throws Exception {
        System.clearProperty("axs.export.initialized");
        Path file=fixture(false);
        try(var plugin=new PluginOwner(file);var jar=new JarFile(file.toFile())) {
            var protection=new ProtectedClassLoader(getClass().getClassLoader(),jar,Map.of(),plugin,null);
            plugin.injected=protection;
            assertEquals(2,ProtectionInit.prepareSharedApiTypes(plugin,jar));
            assertNull(System.getProperty("axs.export.initialized"));
            plugin.close();
            Class<?> late=protection.loadClass(API+"$Reply");
            assertSame(plugin,late.getClassLoader());
            assertEquals(7,late.getMethod("number").invoke(late.getConstructor().newInstance()));
        }finally{System.clearProperty("axs.export.initialized");}
    }

    @Test void simultaneousParentAndPluginLookupsDoNotInvertClassLoadingLocks() throws Exception {
        Path file=fixture(false);
        try(var plugin=new PluginOwner(file);var jar=new JarFile(file.toFile())) {
            var protection=new ProtectedClassLoader(getClass().getClassLoader(),jar,Map.of(),plugin,null);
            plugin.injected=protection;
            var executor=Executors.newFixedThreadPool(2,work->{var thread=new Thread(work,"api-test");thread.setDaemon(true);return thread;});
            try {
                var start=new CountDownLatch(1);
                var one=executor.submit(()->{start.await();return protection.loadClass(API);});
                var two=executor.submit(()->{start.await();return plugin.loadClass(API);});
                start.countDown();assertSame(one.get(5,TimeUnit.SECONDS),two.get(5,TimeUnit.SECONDS));
            }finally{executor.shutdownNow();}
        }
    }

    @Test void encryptedMarkerDoesNotFallBackToPublicPlainBytes() throws Exception {
        Path file=fixture(true);
        try(var plugin=new PluginOwner(file);var jar=new JarFile(file.toFile())) {
            var protection=new ProtectedClassLoader(getClass().getClassLoader(),jar,Map.of(),plugin,null);
            plugin.injected=protection;protection.deactivate();
            assertThrows(ClassNotFoundException.class,()->protection.loadClass(API));
        }
    }

    @Test void aMissingHostApiCannotBeBorrowedFromTheOriginalParent() throws Exception {
        Path file=fixture(false);
        try(var plugin=new PluginOwner(file);var jar=new JarFile(file.toFile())) {
            var protection=new ProtectedClassLoader(getClass().getClassLoader(),jar,Map.of(),plugin,null);
            plugin.injected=protection;
            assertThrows(ClassNotFoundException.class,()->protection.loadClass("xuanmo.arcartxsuite.api.AXSModule"));
        }
    }

    private Path fixture(boolean encrypted) throws Exception {
        Path sources=directory.resolve("src"),classes=directory.resolve("classes");Files.createDirectories(sources);Files.createDirectories(classes);
        Path api=sources.resolve("Shared.java"),body=sources.resolve("Body.java");
        Files.writeString(api,"""
            package xuanmo.arcartxsuite.api.exportprobe;
            public final class Shared {
                static { System.setProperty("axs.export.initialized","yes"); }
                public static final class Reply { public int number(){return 7;} }
                public static Reply reply(){return new Reply();}
                public static Object implementation(){return new xuanmo.arcartxsuite.internal.exportprobe.Body();}
            }
            """);
        Files.writeString(body,"package xuanmo.arcartxsuite.internal.exportprobe; public final class Body {}");
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",classes.toString(),api.toString(),body.toString()));
        Path file=directory.resolve(encrypted?"encrypted.jar":"plain.jar");
        try(var output=new JarOutputStream(Files.newOutputStream(file));var paths=Files.walk(classes)) {
            for(var path:paths.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(path).toString().replace('\\','/')));Files.copy(path,output);output.closeEntry();
            }
            if(encrypted){output.putNextEntry(new JarEntry("ENCRYPTED/"+API.replace('.','/')+".enc"));output.write(new byte[]{1,2,3});output.closeEntry();}
        }
        return file;
    }

    /** Paper's shared lookup accepts only a class owned by this PluginClassLoader. */
    static final class PluginOwner extends URLClassLoader {
        volatile ClassLoader injected;
        PluginOwner(Path jar)throws Exception{super(new URL[]{jar.toUri().toURL()},SharedApiClassLoaderTest.class.getClassLoader());}
        Class<?> export(String name)throws Exception{var type=loadClass(name);return type.getClassLoader()==this?type:null;}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            synchronized(getClassLoadingLock(name)) {
                Class<?> type=findLoadedClass(name);
                if(type==null&&injected!=null)try{type=injected.loadClass(name);}catch(ClassNotFoundException ignored){}
                if(type==null&&name.startsWith("xuanmo.arcartxsuite.api.exportprobe."))type=findClass(name);
                if(type==null)type=super.loadClass(name,false);
                if(resolve)resolveClass(type);return type;
            }
        }
    }
}
