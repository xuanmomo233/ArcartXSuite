package xuanmo.arcartxsuite.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.security.protection.ProtectedClassLoader;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLClassLoader;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeBootstrapTest {
    @TempDir Path temp;

    @Test void hashAddressedLocalFilesRejectTampering() throws Exception {
        byte[] content = {1, 2, 3};
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        var artifact = new RuntimeBootstrap.Artifact("test", hash, content.length, List.of("org/sqlite/"));
        Path file = temp.resolve(hash + ".jar");
        Files.write(file, content);
        assertEquals(file, RuntimeBootstrap.resolveArtifact(artifact, temp.resolve("cache"), temp));
        Files.write(file, new byte[] {1, 2, 4});
        assertThrows(java.io.IOException.class, () -> RuntimeBootstrap.resolveArtifact(artifact, temp.resolve("cache"), temp));
        assertFalse(Files.exists(temp.resolve("cache"))); // Local development never falls back to the network.
    }

    @Test void catalogRejectsDuplicateOwnershipTraversalAndExcessiveSizes() {
        String catalog = "format=1\nartifact.count=1\nartifact.0.id=sqlite\nartifact.0.sha256=" + "a".repeat(64)
                + "\nartifact.0.size=123\nartifact.0.prefixes=org/sqlite/\n";
        assertDoesNotThrow(() -> RuntimeBootstrap.parse(catalog.getBytes()));
        for (String broken : List.of(catalog + "format=1\n", catalog.replace("org/sqlite/", "xuanmo/"),
                catalog.replace("size=123", "size=999999999"), catalog.replace("org/sqlite/", "../"))) {
            assertThrows(java.io.IOException.class, () -> RuntimeBootstrap.parse(broken.getBytes()));
        }
    }

    @Test void downloadRejectsBadMirrorAndReusesOnlyVerifiedCache() throws Exception {
        byte[] content = {3, 1, 4};
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        var artifact = new RuntimeBootstrap.Artifact("test", hash, content.length, List.of("org/sqlite/"));
        AtomicInteger requests = new AtomicInteger();
        Path result = RuntimeBootstrap.resolveArtifact(artifact, temp, null, url -> {
            assertTrue(url.startsWith("https://"));
            return connection(url, 200, requests.getAndIncrement() == 0 ? new byte[] {9, 9, 9} : content);
        });
        assertEquals(2, requests.get());
        assertArrayEquals(content, Files.readAllBytes(result));
        assertEquals(result, RuntimeBootstrap.resolveArtifact(artifact, temp, null, url -> { fail("Verified cache should not request network"); return null; }));
        Files.write(result, new byte[] {9, 9, 9});
        assertThrows(java.io.IOException.class, () -> RuntimeBootstrap.resolveArtifact(artifact, temp, null,
                url -> connection(url, 302, content))); // Redirect bodies never become executable cache entries.
        try (var files = Files.list(temp)) { assertEquals(1, files.count()); }
        assertFalse(RuntimeBootstrap.valid(result, artifact));
    }

    private static HttpURLConnection connection(String url, int status, byte[] bytes) throws java.io.IOException {
        return new HttpURLConnection(URI.create(url).toURL()) {
            public void disconnect() {}
            public boolean usingProxy() { return false; }
            public void connect() {}
            public int getResponseCode() { assertFalse(getInstanceFollowRedirects()); return status; }
            public long getContentLengthLong() { return bytes.length; }
            public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(bytes); }
        };
    }

    @Test void aSlowColdDownloadDoesNotBlockClassOrResourceLookup() throws Exception {
        byte[] content;
        try (var bytes = new java.io.ByteArrayOutputStream()) {
            try (var jar = new JarOutputStream(bytes)) {
                jar.putNextEntry(new JarEntry("org/sqlite/test.txt"));
                jar.write(42);
                jar.closeEntry();
            }
            content = bytes.toByteArray();
        }
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        var artifact = new RuntimeBootstrap.Artifact("sqlite", hash, content.length, List.of("org/sqlite/"));
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var requests = new AtomicInteger();
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var runtime = new RuntimeBootstrap.Loader(getClass().getClassLoader(), List.of(artifact), temp, null, url -> {
            requests.incrementAndGet();
            started.countDown();
            try {
                if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("fetch timed out");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException(failure);
            }
            return connection(url, 200, content);
        })) {
            var prepared = runtime.prefetch(executor).toCompletableFuture();
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
                assertThrows(ClassNotFoundException.class, () -> runtime.loadClass("org.sqlite.JDBC"));
                assertThrows(IllegalStateException.class, () -> runtime.runtimeResource("org/sqlite/test.txt"));
            });
            assertFalse(prepared.isDone());
            release.countDown();
            prepared.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(0, runtime.getURLs().length);
            try (var stream = runtime.getResourceAsStream("org/sqlite/test.txt")) {
                assertNotNull(stream);
                assertEquals(42, stream.read());
            }
            assertEquals(1, requests.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test void domainSeparatedSignatureRejectsModifiedCatalog() throws Exception {
        KeyPair key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(key.getPrivate());
        signature.update(RuntimeBootstrap.catalogDigest(new byte[] {1, 2}));
        byte[] signed = signature.sign();
        signature.initVerify(key.getPublic());
        signature.update(RuntimeBootstrap.catalogDigest(new byte[] {1, 2}));
        assertTrue(signature.verify(signed));
        signature.initVerify(key.getPublic());
        signature.update(RuntimeBootstrap.catalogDigest(new byte[] {1, 3}));
        assertFalse(signature.verify(signed));
    }

    @Test void unsignedEncryptedDeliveryCannotUseDevelopmentOverride() throws Exception {
        Path fixture = temp.resolve("unsigned.jar");
        Path main = Path.of(System.getProperty("axs.runtime.test.jar"));
        byte[] descriptor;
        try (JarFile jar = new JarFile(main.toFile())) {
            descriptor = jar.getInputStream(jar.getJarEntry(RuntimeBootstrap.CATALOG)).readAllBytes();
        }
        try (var out = new JarOutputStream(Files.newOutputStream(fixture))) {
            out.putNextEntry(new JarEntry(RuntimeBootstrap.CATALOG));
            out.write(descriptor);
            out.closeEntry();
        }
        System.setProperty("axs.runtime.directory", temp.toString());
        try (JarFile jar = new JarFile(fixture.toFile());
             var isolated = new URLClassLoader(new java.net.URL[] {main.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> boot = isolated.loadClass(RuntimeBootstrap.class.getName());
            var open = boot.getMethod("open", JarFile.class, ClassLoader.class, boolean.class);
            var failure = assertThrows(InvocationTargetException.class, () -> open.invoke(null, jar, isolated, true));
            assertEquals("Runtime catalog signature rejected", failure.getCause().getMessage());
        } finally { System.clearProperty("axs.runtime.directory"); }
    }

    @Test void pinnedBootstrapAcceptsOnlyItsOwnCatalogEvenInLocalDevelopment() throws Exception {
        Path main = Path.of(System.getProperty("axs.runtime.test.jar"));
        System.setProperty("axs.runtime.directory", System.getProperty("axs.runtime.test.libraries"));
        try (var isolated = new URLClassLoader(new java.net.URL[] {main.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
             JarFile source = new JarFile(main.toFile())) {
            var open = isolated.loadClass(RuntimeBootstrap.class.getName()).getMethod("open", JarFile.class, ClassLoader.class, boolean.class);
            byte[] descriptor = source.getInputStream(source.getJarEntry(RuntimeBootstrap.CATALOG)).readAllBytes();
            for (boolean modified : List.of(false, true)) {
                Path fixture = temp.resolve(modified ? "modified.jar" : "matching.jar");
                try (var out = new JarOutputStream(Files.newOutputStream(fixture))) {
                    out.putNextEntry(new JarEntry(RuntimeBootstrap.CATALOG));
                    out.write(descriptor);
                    if (modified) out.write('\n'); // Semantically identical properties still have different signed bytes.
                    out.closeEntry();
                }
                try (JarFile jar = new JarFile(fixture.toFile())) {
                    if (modified) {
                        var failure = assertThrows(InvocationTargetException.class, () -> open.invoke(null, jar, isolated, false));
                        assertEquals("Runtime catalog does not belong to this host", failure.getCause().getMessage());
                    } else {
                        try (var loader = (java.io.Closeable) open.invoke(null, jar, isolated, false)) { assertNotNull(loader); }
                    }
                }
            }
        } finally { System.clearProperty("axs.runtime.directory"); }
    }

    @Test void realSplitLibrariesKeepIdentityResourcesAndLazyLoading() throws Exception {
        Path main = Path.of(System.getProperty("axs.runtime.test.jar"));
        Path libraries = Path.of(System.getProperty("axs.runtime.test.libraries"));
        try (JarFile jar = new JarFile(main.toFile())) {
            var artifacts = RuntimeBootstrap.parse(jar.getInputStream(jar.getJarEntry(RuntimeBootstrap.CATALOG)).readAllBytes());
            assertEquals(BuildEdition.CROSS_SERVER_INCLUDED ? 9 : 6, artifacts.size());
            assertNull(jar.getJarEntry("com/mysql/cj/jdbc/Driver.class"));
            assertNotNull(jar.getJarEntry("com/google/gson/Gson.class"));
            assertNotNull(jar.getJarEntry("org/tukaani/xz/XZInputStream.class"));
            ProtectedClassLoader pcl = new ProtectedClassLoader(getClass().getClassLoader(), jar, Map.of(), null, null);
            try (var runtime = new RuntimeBootstrap.Loader(pcl, artifacts, temp.resolve("cache"), libraries)) {
                pcl.attachRuntime(runtime);
                assertEquals(0, runtime.getURLs().length);
                Class<?> config = Class.forName("com.zaxxer.hikari.HikariConfig", true, pcl);
                assertSame(runtime, config.getClassLoader());
                assertEquals(1, runtime.getURLs().length);
                Class<?> gson = Class.forName("com.google.gson.Gson", true, pcl);
                assertSame(pcl, gson.getClassLoader());
                assertNotSame(com.google.gson.Gson.class, gson);
                // Static JDBC initialization and a real JNI-backed query catch class/resource loader mismatches.
                Class<?> sqlite = Class.forName("org.sqlite.JDBC", true, pcl);
                java.sql.Driver driver = (java.sql.Driver) sqlite.getConstructor().newInstance();
                try (var connection = driver.connect("jdbc:sqlite::memory:", new Properties());
                     var query = connection.createStatement(); var rows = query.executeQuery("SELECT 42")) {
                    assertTrue(rows.next()); assertEquals(42, rows.getInt(1));
                }
                assertSame(runtime, sqlite.getClassLoader());
                assertNotNull(pcl.getResource("org/sqlite/JDBC.class"));
                assertEquals(2, runtime.getURLs().length); // MySQL/Redis/crypto not downloaded by JDBC discovery.
                Object poolConfig = config.getConstructor().newInstance();
                config.getMethod("setJdbcUrl", String.class).invoke(poolConfig, "jdbc:sqlite::memory:");
                config.getMethod("setDriverClassName", String.class).invoke(poolConfig, "org.sqlite.JDBC");
                config.getMethod("setMaximumPoolSize", int.class).invoke(poolConfig, 1);
                Class<?> dataSourceType = Class.forName("com.zaxxer.hikari.HikariDataSource", true, pcl);
                Object pool = dataSourceType.getConstructor(config).newInstance(poolConfig);
                try (var connection = ((javax.sql.DataSource) pool).getConnection();
                     var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT 43")) {
                    assertTrue(rows.next()); assertEquals(43, rows.getInt(1));
                } finally { ((AutoCloseable) pool).close(); }
                assertSame(runtime, Class.forName("com.mysql.cj.jdbc.Driver", true, pcl).getClassLoader());
                if (BuildEdition.CROSS_SERVER_INCLUDED) {
                    assertSame(runtime, Class.forName("redis.clients.jedis.Jedis", false, pcl).getClassLoader());
                    Class<?> mapperClass = Class.forName("redis.clients.jedis.json.DefaultGsonObjectMapper", true, pcl);
                    assertSame(gson, mapperClass.getDeclaredField("gson").getType());
                    Object mapper = mapperClass.getConstructor().newInstance();
                    assertEquals("{\"value\":42}", mapperClass.getMethod("toJson", Object.class).invoke(mapper, Map.of("value", 42)));
                    assertSame(runtime, Class.forName("org.apache.commons.pool2.impl.GenericObjectPool", true, pcl).getClassLoader());
                    assertSame(runtime, Class.forName("org.json.JSONObject", true, pcl).getClassLoader());
                } else {
                    assertThrows(ClassNotFoundException.class, () -> Class.forName("redis.clients.jedis.Jedis", false, pcl));
                }
                Class<?> pinyin = Class.forName("net.sourceforge.pinyin4j.PinyinHelper", true, pcl);
                assertSame(runtime, pinyin.getClassLoader());
                assertTrue(Arrays.asList((String[]) pinyin.getMethod("toHanyuPinyinStringArray", char.class).invoke(null, '中')).contains("zhong1"));
                assertSame(runtime, Class.forName("org.bouncycastle.crypto.generators.Argon2BytesGenerator", true, pcl).getClassLoader());
                assertSame(runtime, Class.forName("org.java_websocket.client.WebSocketClient", false, pcl).getClassLoader());
            }
        }
    }
}
