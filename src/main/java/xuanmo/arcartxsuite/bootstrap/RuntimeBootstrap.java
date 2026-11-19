package xuanmo.arcartxsuite.bootstrap;

import xuanmo.arcartxsuite.security.NativeBridge;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;
import java.util.logging.Logger;

/** JDK-only loader for public dependencies. No Suite bytecode or native protection is externalized. */
public final class RuntimeBootstrap {
    public static final String CATALOG = "META-INF/axs-libraries.properties";
    private static final String[] MIRRORS = {
        "https://cloud.021209.xyz/v1/runtime-libraries/",
        "https://flare.021209.xyz/v1/runtime-libraries/"
    };
    private static final Logger LOG = Logger.getLogger("AXS-Runtime");
    private RuntimeBootstrap() {}

    public static Loader open(JarFile jar, ClassLoader parent, boolean encrypted) throws Exception {
        var entry = jar.getJarEntry(CATALOG);
        if (entry == null) {
            if (encrypted) throw new IOException("Missing signed runtime catalog");
            return null; // Class directories / legacy fat development fixtures.
        }
        byte[] descriptor;
        try (var in = jar.getInputStream(entry)) { descriptor = bounded(in, 65536); }
        if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(descriptor)).equals(expectedCatalogHash())) {
            throw new IOException("Runtime catalog does not belong to this host");
        }
        byte[] signature = new byte[0];
        var signatureEntry = jar.getJarEntry("META-INF/axs-libraries.sig");
        if (signatureEntry != null) {
            try (var in = jar.getInputStream(signatureEntry)) { signature = bounded(in, 64); }
        }
        String local = System.getProperty("axs.runtime.directory", "");
        // Unsigned catalogs are strictly local development input; encrypted delivery never honors this switch.
        boolean development = !encrypted && signature.length == 0 && !local.isBlank();
        if (!development && (signature.length != 64 || !NativeBridge.isAvailable()
                || !NativeBridge.n7(catalogDigest(descriptor), signature))) {
            throw new IOException("Runtime catalog signature rejected");
        }
        Path jarPath = Path.of(jar.getName()).toAbsolutePath();
        Path cache = jarPath.getParent().resolve("ArcartX-Suite/runtime-libraries");
        return new Loader(parent, parse(descriptor), cache, development ? Path.of(local).toAbsolutePath() : null);
    }

    private static String expectedCatalogHash() { return "AXS_RUNTIME_CATALOG_SHA256_PLACEHOLDER"; }

    static byte[] catalogDigest(byte[] descriptor) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update("AXS-RUNTIME-1\0".getBytes(StandardCharsets.US_ASCII));
        return digest.digest(descriptor);
    }

    static List<Artifact> parse(byte[] bytes) throws IOException {
        Properties p = new Properties();
        // Reject duplicate fields instead of Properties silently accepting the last value.
        Set<String> keys = new HashSet<>();
        for (String line : new String(bytes, StandardCharsets.US_ASCII).split("\n")) {
            if (line.isBlank()) continue;
            int equals = line.indexOf('=');
            if (equals < 1 || !keys.add(line.substring(0, equals))) throw new IOException("Invalid runtime catalog");
        }
        p.load(new ByteArrayInputStream(bytes));
        if (!"1".equals(p.getProperty("format"))) throw new IOException("Unknown runtime format");
        try {
            int count = Integer.parseInt(p.getProperty("artifact.count", "0"));
            if (count < 1 || count > 32 || keys.size() != 2 + count * 4) throw new IOException("Invalid runtime count");
            List<Artifact> result = new ArrayList<>();
            Set<String> prefixes = new HashSet<>();
            Set<String> hashes = new HashSet<>();
            for (int i = 0; i < count; i++) {
                String base = "artifact." + i + ".";
                String id = p.getProperty(base + "id", "");
                String hash = p.getProperty(base + "sha256", "");
                long size = Long.parseLong(p.getProperty(base + "size", "0"));
                String[] owned = p.getProperty(base + "prefixes", "").split(",");
                if (!id.matches("[a-zA-Z0-9._-]{1,80}") || !hash.matches("[a-f0-9]{64}")
                        || !hashes.add(hash) || size < 1 || size > 16 * 1024 * 1024) throw new IOException("Invalid runtime artifact");
                for (String prefix : owned) {
                    if (!PUBLIC_PREFIXES.contains(prefix) || !prefixes.add(prefix)) throw new IOException("Invalid runtime ownership");
                }
                result.add(new Artifact(id, hash, size, List.of(owned)));
            }
            return List.copyOf(result);
        } catch (IllegalArgumentException e) { throw new IOException("Invalid runtime catalog", e); }
    }

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
        "com/mysql/", "org/sqlite/", "redis/clients/jedis/", "org/apache/commons/pool2/",
        "com/zaxxer/hikari/", "net/sourceforge/pinyin4j/", "com/hp/hpl/", "pinyindb/",
        "org/bouncycastle/", "org/java_websocket/", "org/json/"
    );

    record Artifact(String id, String hash, long size, List<String> prefixes) {
        boolean owns(String path) {
            // This predicate runs before normal parent delegation, including for cold JDK classes.
            // Streams/lambdas here can recursively ask this same loader for their own implementation.
            for (int i = 0; i < prefixes.size(); i++) {
                if (path.startsWith(prefixes.get(i))) return true;
            }
            return false;
        }
    }

    public static final class Loader extends URLClassLoader {
        static { registerAsParallelCapable(); }
        private final List<Artifact> artifacts;
        private final Path cache;
        private final Path development;
        private final Set<String> installed = new HashSet<>();
        private volatile boolean closed;

        Loader(ClassLoader parent, List<Artifact> artifacts, Path cache, Path development) {
            super(new URL[0], parent);
            this.artifacts = artifacts;
            this.cache = cache;
            this.development = development;
        }

        public boolean ownsClass(String name) { return owner(name.replace('.', '/') + ".class") != null; }
        public boolean ownsResource(String name) { return owner(name) != null; }
        private Artifact owner(String path) {
            for (int i = 0; i < artifacts.size(); i++) {
                Artifact artifact = artifacts.get(i);
                if (artifact.owns(path)) return artifact;
            }
            return null;
        }

        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // Fail closed for owned packages: never pick an unpinned copy from another plugin/server.
            if (!ownsClass(name)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    try { ensure(owner(name.replace('.', '/') + ".class")); }
                    catch (IOException e) { throw new ClassNotFoundException("Runtime unavailable: " + name, e); }
                    c = findClass(name);
                }
                if (resolve) resolveClass(c);
                return c;
            }
        }

        /** Only consult local URLs here. Parent is the protected loader and must not be recursed into. */
        public URL runtimeResource(String name) {
            Artifact owner = owner(name);
            if (owner != null) {
                try { ensure(owner); }
                catch (IOException e) { throw new IllegalStateException("Runtime resource unavailable: " + name, e); }
            }
            return findResource(name);
        }

        public Enumeration<URL> runtimeResources(String name) throws IOException {
            Artifact owner = owner(name);
            if (owner != null) ensure(owner);
            return findResources(name); // ServiceLoader sees providers of installed libraries only.
        }

        @Override public URL getResource(String name) {
            URL own = runtimeResource(name);
            return own != null || ownsResource(name) ? own : getParent().getResource(name);
        }

        @Override public Enumeration<URL> getResources(String name) throws IOException {
            // Parent merges main-JAR resources with these local resources.
            return getParent().getResources(name);
        }

        private synchronized void ensure(Artifact artifact) throws IOException {
            if (closed) throw new IOException("Runtime loader closed");
            if (installed.contains(artifact.hash)) return;
            Path file = resolveArtifact(artifact, cache, development);
            addURL(file.toUri().toURL());
            installed.add(artifact.hash);
            LOG.info("运行库已就绪: " + artifact.id);
        }

        @Override public synchronized void close() throws IOException {
            closed = true;
            // MySQL's cleanup thread retains its defining loader unless explicitly stopped.
            Class<?> mysqlCleanup = findLoadedClass("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread");
            if (mysqlCleanup != null) {
                Thread thread = Thread.currentThread();
                ClassLoader previous = thread.getContextClassLoader();
                try {
                    thread.setContextClassLoader(this);
                    mysqlCleanup.getMethod("checkedShutdown").invoke(null);
                } catch (ReflectiveOperationException e) { LOG.warning("MySQL runtime cleanup failed: " + e.getClass().getSimpleName()); }
                finally { thread.setContextClassLoader(previous); }
            }
            if (findLoadedClass("org.sqlite.JDBC") != null || findLoadedClass("com.mysql.cj.jdbc.Driver") != null) cleanupDrivers(this);
            super.close();
        }
    }

    private static void cleanupDrivers(ClassLoader owner) {
        // The captured bootstrap class's PluginClassLoader can see drivers through the installed parent hook.
        java.sql.DriverManager.getDrivers().asIterator().forEachRemaining(driver -> {
            if (driver.getClass().getClassLoader() == owner) {
                try { java.sql.DriverManager.deregisterDriver(driver); }
                catch (java.sql.SQLException e) { LOG.warning("JDBC runtime cleanup failed: " + e.getSQLState()); }
            }
        });
    }

    static Path resolveArtifact(Artifact artifact, Path cache, Path development) throws IOException {
        return resolveArtifact(artifact, cache, development,
                url -> (HttpURLConnection) URI.create(url).toURL().openConnection());
    }

    @FunctionalInterface interface Connections { HttpURLConnection open(String url) throws IOException; }

    static Path resolveArtifact(Artifact artifact, Path cache, Path development, Connections connections) throws IOException {
        if (development != null) {
            Path local = development.resolve(artifact.hash + ".jar");
            if (!valid(local, artifact)) throw new IOException("Local runtime hash mismatch: " + artifact.id);
            return local;
        }
        Files.createDirectories(cache);
        Path file = cache.resolve(artifact.hash + ".jar");
        if (valid(file, artifact)) return file;
        IOException failure = new IOException("Cannot download verified runtime " + artifact.id);
        for (String mirror : MIRRORS) {
            Path temp = Files.createTempFile(cache, ".download-", ".part");
            try {
                HttpURLConnection connection = connections.open(mirror + artifact.hash + ".jar");
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", "ArcartX-Suite-Runtime/1");
                try {
                    if (connection.getResponseCode() != 200) throw new IOException("Runtime HTTP " + connection.getResponseCode());
                    long contentLength = connection.getContentLengthLong();
                    if (contentLength != -1 && contentLength != artifact.size) throw new IOException("Runtime length mismatch");
                    try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(temp)) {
                        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(45);
                        byte[] buffer = new byte[16384];
                        long total = 0;
                        for (int n; (n = in.read(buffer)) != -1;) {
                            total += n;
                            if (total > artifact.size || System.nanoTime() > deadline) throw new IOException("Runtime download limit exceeded");
                            out.write(buffer, 0, n);
                        }
                    }
                } finally { connection.disconnect(); }
                if (!valid(temp, artifact)) throw new IOException("Runtime checksum mismatch");
                try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
                return file;
            } catch (IOException e) { failure.addSuppressed(e); }
            finally { Files.deleteIfExists(temp); }
        }
        throw failure;
    }

    static boolean valid(Path file, Artifact artifact) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != artifact.size) return false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[16384];
                for (int n; (n = in.read(buffer)) != -1;) digest.update(buffer, 0, n);
            }
            return MessageDigest.isEqual(digest.digest(), HexFormat.of().parseHex(artifact.hash));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }

    private static byte[] bounded(InputStream in, int maximum) throws IOException {
        byte[] data = in.readNBytes(maximum + 1);
        if (data.length > maximum) throw new IOException("Runtime metadata too large");
        return data;
    }
}
