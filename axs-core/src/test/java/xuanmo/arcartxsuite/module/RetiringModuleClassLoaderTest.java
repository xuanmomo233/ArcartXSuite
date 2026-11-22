package xuanmo.arcartxsuite.module;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import xuanmo.arcartxsuite.security.NativeBridge;

class RetiringModuleClassLoaderTest {
    @TempDir Path directory;
    private static final String PROBE="xuanmo.arcartxsuite.fixture.Probe",LATE=PROBE+"$Late";

    @ParameterizedTest @ValueSource(booleans={false,true})
    void ownLateClassesFinishLoadingAfterTheParentJarCloses(boolean memory) throws Exception {
        byte[] jar=jar(false);var parent=new ClosingParent();ClassLoader loader=loader(memory,"fixture",jar,parent);
        var workers=Executors.newFixedThreadPool(8);
        try {
            for(String name:List.of(PROBE,"RelocatedProbe")) {
                Class<?> probe=loader.loadClass(name);parent.closed=true;
                var loaded=new ArrayList<Future<Class<?>>>();for(int i=0;i<16;i++)loaded.add(workers.submit(()->loader.loadClass(name+"$Late")));
                Class<?> late=loaded.get(0).get(2,TimeUnit.SECONDS);
                for(var result:loaded)assertSame(late,result.get(2,TimeUnit.SECONDS));
                assertEquals(7,probe.getMethod("value").invoke(null));
                assertSame(loader,late.getClassLoader());
            }
        }finally{workers.shutdownNow();close(loader);}
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void moduleContentCannotReplaceHostApiOrBootstrapTypes(boolean memory) throws Exception {
        byte[] jar=jar(false);
        for(String reserved:List.of("api","bootstrap")) {
            var parent=new ClosingParent();ClassLoader loader=loader(memory,reserved,jar,parent);parent.closed=true;
            try {assertThrows(IllegalStateException.class,()->loader.loadClass("xuanmo.arcartxsuite."+reserved+".Shadow"));}
            finally{close(loader);}
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void unrelatedParentFailuresStillPropagate(boolean memory) throws Exception {
        var parent=new ClosingParent();ClassLoader loader=loader(memory,"fixture",jar(false),parent);
        parent.closed=true;parent.failure="parent policy rejected";
        try {assertEquals(parent.failure,assertThrows(IllegalStateException.class,()->loader.loadClass(PROBE)).getMessage());}
        finally{close(loader);}
    }

    @Test
    void encryptedEntriesStillRequireNativeAndNeverFallBackToTheirPlaintextCopy() throws Exception {
        var parent=new ClosingParent();var loader=new ByteArrayModuleClassLoader("fixture",jar(true),parent);
        parent.closed=true;
        try(var nativeBridge=mockStatic(NativeBridge.class)) {
            nativeBridge.when(NativeBridge::isAvailable).thenReturn(false);
            for(String name:List.of(LATE,"RelocatedProbe$Late")) {
                var failure=assertThrows(ClassNotFoundException.class,()->loader.loadClass(name));
                assertTrue(failure.getMessage().contains("native"));
                assertEquals(1,failure.getSuppressed().length);
            }
        }finally{loader.close();}
    }

    private byte[] jar(boolean encrypted) throws Exception {
        Path source=directory.resolve("source");Path classes=directory.resolve("classes");Files.createDirectories(classes);
        var files=new ArrayList<String>();
        for(String packageName:List.of("fixture","api","bootstrap")) {
            Path folder=source.resolve(packageName);Files.createDirectories(folder);
            Path file=folder.resolve(packageName.equals("fixture")?"Probe.java":"Shadow.java");
            String body=packageName.equals("fixture")?"public class Probe { public static int value(){return new Late().value();} public static class Late {public int value(){return 7;}} }":"public class Shadow {}";
            Files.writeString(file,"package xuanmo.arcartxsuite."+packageName+"; "+body,StandardCharsets.UTF_8);files.add(file.toString());
        }
        Path relocated=source.resolve("RelocatedProbe.java");
        Files.writeString(relocated,"public class RelocatedProbe {public static int value(){return new Late().value();} public static class Late {public int value(){return 7;}}}",StandardCharsets.UTF_8);
        files.add(relocated.toString());
        var args=new ArrayList<>(List.of("--release","17","-encoding","UTF-8","-d",classes.toString()));args.addAll(files);
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new)));
        var bytes=new ByteArrayOutputStream();
        try(var jar=new JarOutputStream(bytes);var entries=Files.walk(classes)) {
            for(var file:entries.filter(Files::isRegularFile).toList()) {
                jar.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\','/')));Files.copy(file,jar);jar.closeEntry();
            }
            if(encrypted)for(String name:List.of(LATE,"RelocatedProbe$Late")){jar.putNextEntry(new JarEntry("ENCRYPTED/"+name.replace('.','/')+".enc"));jar.write(new byte[]{1,2,3});jar.closeEntry();}
        }
        return bytes.toByteArray();
    }
    private ClassLoader loader(boolean memory,String id,byte[] jar,ClassLoader parent)throws Exception {
        if(memory)return new ByteArrayModuleClassLoader(id,jar,parent);
        Path file=directory.resolve(id+".jar");Files.write(file,jar);return new ModuleClassLoader(id,file.toUri().toURL(),parent);
    }
    private static void close(ClassLoader loader)throws Exception {
        if(loader instanceof ModuleClassLoader file)file.close();else ((ByteArrayModuleClassLoader)loader).close();
    }
    private static final class ClosingParent extends ClassLoader {
        volatile boolean closed;String failure="zip file closed";
        ClosingParent(){super(RetiringModuleClassLoaderTest.class.getClassLoader());}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(closed&&(name.startsWith("xuanmo.arcartxsuite.") || name.indexOf('.')<0))throw new IllegalStateException(failure);
            return super.loadClass(name,resolve);
        }
    }
}
