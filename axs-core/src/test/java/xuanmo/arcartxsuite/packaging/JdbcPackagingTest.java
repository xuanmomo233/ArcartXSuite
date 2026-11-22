package xuanmo.arcartxsuite.packaging;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class JdbcPackagingTest {
    @Test
    void jdbcDriverAndConnectionModesWorkWithoutProtobuf() throws Exception {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.google.protobuf.Message"));
        var driver = new com.mysql.cj.jdbc.Driver();
        for (String url : new String[]{"jdbc:mysql://127.0.0.1/test",
                "jdbc:mysql://host1,host2/test", "jdbc:mysql:loadbalance://host1,host2/test",
                "jdbc:mysql:replication://host1,host2/test"}) {
            assertTrue(driver.acceptsURL(url), url);
        }
        Set<String> properties = Arrays.stream(driver.getPropertyInfo("jdbc:mysql://127.0.0.1/test", new Properties()))
                .map(property -> property.name).collect(Collectors.toSet());
        assertTrue(properties.containsAll(Set.of("sslMode", "useServerPrepStmts", "rewriteBatchedStatements")));
    }

    @Test
    void jdbcCoreRetainsLinkableMethodSignatures() throws Exception {
        Path jar = Path.of(com.mysql.cj.jdbc.Driver.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (var original = new ZipFile(jar.toFile())) {
            var classes = original.stream().filter(entry -> entry.getName().startsWith("com/mysql/cj/jdbc/")
                    // Optional c3p0 adapters require c3p0, which Suite does not use or ship.
                    && !entry.getName().startsWith("com/mysql/cj/jdbc/integration/")
                    && entry.getName().endsWith(".class")).toList();
            assertFalse(classes.isEmpty());
            for (var entry : classes) {
                String name = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');
                assertDoesNotThrow(() -> {
                    Class<?> type = Class.forName(name, false, getClass().getClassLoader());
                    type.getDeclaredConstructors();
                    type.getDeclaredMethods();
                    type.getDeclaredFields();
                }, name);
            }
        }
    }
}
