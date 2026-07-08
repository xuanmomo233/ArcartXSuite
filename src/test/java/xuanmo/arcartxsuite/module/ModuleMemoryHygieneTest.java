package xuanmo.arcartxsuite.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;

final class ModuleMemoryHygieneTest {

    @Test
    void classLoaderScrubsJarBytesAfterIndexingAndStillLoadsClasses() throws Exception {
        byte[] jarBytes = jarWithClassBytes(FixtureClass.class);
        byte[] moduleSeed = new byte[32];
        Arrays.fill(moduleSeed, (byte) 7);

        ByteArrayModuleClassLoader loader = new ByteArrayModuleClassLoader(
            "fixture",
            jarBytes,
            moduleSeed,
            ModuleMemoryHygieneTest.class.getClassLoader()
        );

        assertZeroed(internalBytes(loader, "jarBytes"));

        Class<?> loaded = loader.loadClass("xuanmo.arcartxsuite.module.FixtureClass");
        assertEquals("xuanmo.arcartxsuite.module.FixtureClass", loaded.getName());

        loader.close();

        assertZeroed(internalBytes(loader, "moduleSeed"));
    }

    private static byte[] jarWithClassBytes(Class<?> type) throws IOException {
        String entryName = type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(type.getSimpleName() + ".class");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JarOutputStream jar = new JarOutputStream(out)) {
            if (in == null) {
                throw new IOException("Missing class bytes for " + type.getName());
            }
            jar.putNextEntry(new JarEntry(entryName));
            in.transferTo(jar);
            jar.closeEntry();
            jar.finish();
            return out.toByteArray();
        }
    }

    private static byte[] internalBytes(ByteArrayModuleClassLoader loader, String fieldName) throws Exception {
        Field field = ByteArrayModuleClassLoader.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (byte[]) field.get(loader);
    }

    private static void assertZeroed(byte[] data) {
        assertTrue(data != null);
        for (byte value : data) {
            assertEquals(0, value);
        }
    }
}
