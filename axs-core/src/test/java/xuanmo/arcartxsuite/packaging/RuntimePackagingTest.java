package xuanmo.arcartxsuite.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.util.LibraryLoaderUtil;
import xuanmo.arcartxsuite.auth.engine.Argon2Passwords;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class RuntimePackagingTest {
    @TempDir Path temporary;

    @Test
    void retainsEveryDesktopSqlitePlatformByteForByte() throws Exception {
        var metadata = new java.util.Properties();
        try (var input = getClass().getClassLoader().getResourceAsStream("META-INF/axs-runtime.properties")) {
            assertNotNull(input);
            metadata.load(input);
        }
        String target = metadata.getProperty("target");
        try (var original = new ZipFile(System.getProperty("axs.packaging.originalSqlite"))) {
            var libraries = original.stream().filter(e -> !e.isDirectory()
                    && e.getName().startsWith("org/sqlite/native/")).toList();
            assertFalse(libraries.isEmpty());
            for (var entry : libraries) {
                String name = entry.getName();
                URL packed = getClass().getClassLoader().getResource(name);
                boolean supported = switch (target) {
                    case "windows-x86_64" -> name.startsWith("org/sqlite/native/Windows/x86_64/");
                    case "linux-x86_64" -> name.startsWith("org/sqlite/native/Linux/x86_64/")
                            || name.startsWith("org/sqlite/native/Linux-Musl/x86_64/");
                    case "universal" -> name.startsWith("org/sqlite/native/Windows/")
                            || name.startsWith("org/sqlite/native/Linux/")
                            || name.startsWith("org/sqlite/native/Linux-Musl/");
                    default -> throw new AssertionError("Unknown runtime target: " + target);
                };
                if (!supported) {
                    assertNull(packed, name);
                    continue;
                }
                assertNotNull(packed, name);
                try (var expected = original.getInputStream(entry); var actual = packed.openStream()) {
                    assertArrayEquals(expected.readAllBytes(), actual.readAllBytes(), name);
                }
                int slash = name.lastIndexOf('/');
                assertTrue(LibraryLoaderUtil.hasNativeLib("/" + name.substring(0, slash),
                        name.substring(slash + 1)), name);
            }
            assertNull(getClass().getClassLoader().getResource("org/sqlite/native/unsupported/missing.so"));
            assertNotNull(getClass().getClassLoader().getResource(
                    "META-INF/maven/org.xerial/sqlite-jdbc/pom.properties"));
        }
    }

    @Test
    void sqliteTransactionsUnicodeAndBlobsStillWork() throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + temporary.resolve("数据库.db"))) {
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("CREATE TABLE sample (id INTEGER PRIMARY KEY, name TEXT, value BLOB)");
            }
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("INSERT INTO sample(name,value) VALUES (?,?)")) {
                insert.setString(1, "中文玩家😀");
                insert.setBytes(2, new byte[]{0, 1, -1, 0});
                insert.executeUpdate();
                connection.commit();
                insert.setString(1, "回滚");
                insert.executeUpdate();
                connection.rollback();
            }
            try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT * FROM sample")) {
                assertTrue(rows.next());
                assertEquals("中文玩家😀", rows.getString("name"));
                assertArrayEquals(new byte[]{0, 1, -1, 0}, rows.getBytes("value"));
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void compactCryptoAndOriginalCryptoVerifyEachOthersPasswordHashes() throws Exception {
        URL engine = Argon2Passwords.class.getProtectionDomain().getCodeSource().getLocation();
        URL bc = Path.of(System.getProperty("axs.packaging.originalCrypto")).toUri().toURL();
        try (var original = new URLClassLoader(new URL[]{engine, bc}, ClassLoader.getPlatformClassLoader())) {
            Class<?> type = original.loadClass(Argon2Passwords.class.getName());
            Object reference = type.getConstructor().newInstance();
            var hash = type.getMethod("hash", char[].class);
            var verify = type.getMethod("verify", char[].class, String.class);
            var compact = new Argon2Passwords();
            for (String password : Arrays.asList("", "secret", "中文密码😀\u0000", "密码".repeat(128))) {
                String oldHash = (String) hash.invoke(reference, (Object) password.toCharArray());
                assertTrue(compact.verify(password.toCharArray(), oldHash));
                String newHash = compact.hash(password.toCharArray());
                assertEquals(true, verify.invoke(reference, password.toCharArray(), newHash));
                assertFalse(compact.verify((password + "x").toCharArray(), oldHash));
            }
        }
    }
}
