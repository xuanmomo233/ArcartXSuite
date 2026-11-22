import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.TreeMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.objectweb.asm.*

/** Only public dependencies cross this boundary. Suite classes and native protection stay bundled. */
abstract class SplitRuntimeTask : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJar: RegularFileProperty
    @get:OutputFile abstract val outputJar: RegularFileProperty
    @get:OutputDirectory abstract val libraries: DirectoryProperty
    @get:Input val signed: Boolean get() = !System.getenv("AXS_RUNTIME_SIGNING_KEY_FILE").isNullOrBlank()

    init { outputs.upToDateWhen { System.getenv("AXS_RUNTIME_SIGNING_KEY_FILE").isNullOrBlank() } }

    @TaskAction fun split() {
        val groups = linkedMapOf(
            "mysql-8.4.0" to listOf("com/mysql/"),
            "sqlite-3.46.0.0" to listOf("org/sqlite/"),
            "jedis-5.2.0" to listOf("redis/clients/jedis/"),
            "pool2-2.12.0" to listOf("org/apache/commons/pool2/"),
            "hikari-5.1.0" to listOf("com/zaxxer/hikari/"),
            "pinyin4j-2.5.1" to listOf("net/sourceforge/pinyin4j/", "com/hp/hpl/", "pinyindb/"),
            "bcprov-suite-1.86" to listOf("org/bouncycastle/"),
            "websocket-1.5.7" to listOf("org/java_websocket/"),
            "json-20240303" to listOf("org/json/")
        )
        fun owner(path: String) = groups.entries.firstOrNull { (_, prefixes) -> prefixes.any(path::startsWith) }?.key
        val main = TreeMap<String, ByteArray>()
        val parts = groups.mapValues { TreeMap<String, ByteArray>() }
        val notices = TreeMap<String, ByteArray>()
        ZipFile(inputJar.get().asFile).use { zip ->
            zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                val name = entry.name
                val data = zip.getInputStream(entry).use { it.readBytes() }
                if (name.startsWith("META-INF/services/")) {
                    // A provider descriptor belongs to its provider, never to every runtime library.
                    data.toString(Charsets.UTF_8).lineSequence().map { it.substringBefore('#').trim() }
                        .filter { it.isNotEmpty() }.groupBy { owner(it.replace('.', '/') + ".class") }
                        .forEach { (group, providers) ->
                            (if (group == null) main else parts.getValue(group))[name] =
                                (providers.distinct().joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)
                        }
                } else {
                    (owner(name)?.let(parts::getValue) ?: main)[name] = data
                }
                if (name.startsWith("META-INF/") && Regex("(?i).*(license|notice|copying).*").matches(name)) notices[name] = data
            }
        }
        val outDir = libraries.get().asFile
        // Remove stale content-addressed artifacts from this task's own output directory only.
        outDir.mkdirs()
        outDir.listFiles()?.filter { it.isFile && it.name.matches(Regex("[a-f0-9]{64}\\.jar")) }?.forEach { it.delete() }
        val catalog = StringBuilder("format=1\n")
        val populated = groups.keys.filter { parts.getValue(it).keys.any { name -> name.endsWith(".class") } }
        catalog.append("artifact.count=${populated.size}\n")
        populated.forEachIndexed { index, id ->
            val content = parts.getValue(id)
            check(content.keys.none { it.startsWith("xuanmo/") || it.startsWith("ENCRYPTED/") || it.startsWith("native/") })
            notices.forEach { (name, data) -> content.putIfAbsent(name, data) }
            val bytes = java.io.ByteArrayOutputStream().also { writeZip(it, content) }.toByteArray()
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            java.io.File(outDir, "$hash.jar").writeBytes(bytes)
            catalog.append("artifact.$index.id=$id\nartifact.$index.sha256=$hash\nartifact.$index.size=${bytes.size}\n")
            catalog.append("artifact.$index.prefixes=${groups.getValue(id).joinToString(",")}\n")
        }
        val descriptor = catalog.toString().toByteArray(Charsets.US_ASCII)
        main["META-INF/axs-libraries.properties"] = descriptor
        // Bind this catalog to the exact host, not merely to any catalog ever signed with the same key.
        // Native BOOTSTRAP.MF verification protects this class (including the pinned digest) in production.
        val bootstrap = "xuanmo/arcartxsuite/bootstrap/RuntimeBootstrap.class"
        val descriptorHash = MessageDigest.getInstance("SHA-256").digest(descriptor).joinToString("") { "%02x".format(it) }
        main[bootstrap] = pinCatalog(main.getValue(bootstrap), descriptorHash)
        val signingPath = System.getenv("AXS_RUNTIME_SIGNING_KEY_FILE")
        if (!signingPath.isNullOrBlank()) {
            val pem = java.io.File(signingPath).readText(Charsets.US_ASCII)
                .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace(Regex("\\s"), "")
            val key = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)))
            val signer = Signature.getInstance("Ed25519")
            signer.initSign(key)
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update("AXS-RUNTIME-1\u0000".toByteArray(Charsets.US_ASCII))
            signer.update(digest.digest(descriptor))
            main["META-INF/axs-libraries.sig"] = signer.sign()
        } else {
            check(System.getenv("CI") != "true" || System.getenv("AXS_PRODUCTION") != "ON") { "Runtime signing key missing" }
            main["META-INF/axs-libraries.sig"] = byteArrayOf()
        }
        outputJar.get().asFile.apply { parentFile.mkdirs(); outputStream().use { writeZip(it, main) } }
        logger.lifecycle("Thin core: ${outputJar.get().asFile.length()} bytes; ${populated.size} downloadable public libraries")
    }

    private fun pinCatalog(bytes: ByteArray, hash: String): ByteArray {
        val reader = ClassReader(bytes)
        val writer = ClassWriter(0)
        var replacements = 0
        reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                val method = super.visitMethod(access, name, descriptor, signature, exceptions)
                if (name != "expectedCatalogHash" || descriptor != "()Ljava/lang/String;") return method
                return object : MethodVisitor(Opcodes.ASM9, method) {
                    override fun visitLdcInsn(value: Any) {
                        if (value == "AXS_RUNTIME_CATALOG_SHA256_PLACEHOLDER") {
                            replacements++
                            super.visitLdcInsn(hash)
                        } else super.visitLdcInsn(value)
                    }
                }
            }
        }, 0)
        check(replacements == 1) { "Runtime catalog pin must be injected exactly once" }
        return writer.toByteArray()
    }

    private fun writeZip(out: java.io.OutputStream, entries: Map<String, ByteArray>) {
        ZipOutputStream(out).use { zip ->
            zip.setLevel(9)
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
