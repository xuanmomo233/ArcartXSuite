import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.CommandLineArgumentProvider
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import proguard.Configuration
import proguard.ConfigurationParser
import proguard.ProGuard
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Only shrink the crypto dependency; Suite and other third-party code are untouched. */
abstract class CompactCryptoJarTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJar: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val consumerClasses: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun compact() {
        val roots = sortedSetOf<String>()
        val remapper = object : Remapper() {
            override fun map(internalName: String): String {
                if (internalName.startsWith("org/bouncycastle/")) roots.add(internalName.replace('/', '.'))
                return internalName
            }

            override fun mapValue(value: Any?): Any? {
                // Literal reflective class names also count as entry points.
                if (value is String && value.startsWith("org.bouncycastle.")) roots.add(value)
                return super.mapValue(value)
            }
        }
        consumerClasses.files.forEach { directory ->
            if (directory.isDirectory) directory.walkTopDown().filter { it.extension == "class" }.forEach {
                ClassReader(it.readBytes()).accept(ClassRemapper(ClassWriter(0), remapper), ClassReader.SKIP_DEBUG)
            }
        }
        check(roots.isNotEmpty()) { "No crypto entry points found; refusing to produce an empty dependency" }
        val output = outputJar.get().asFile
        output.parentFile.mkdirs()
        val shrunk = temporaryDir.resolve("bcprov-shrunk.jar")
        val configText = buildString {
            // Baseline classes work on every supported JVM; provider services and signatures
            // belong to the original complete provider, not this private Argon2 dependency.
            appendLine("-injars '${inputJar.get().asFile.absolutePath}'(!META-INF/**,!module-info.class)")
            appendLine("-outjars '${shrunk.absolutePath}'")
            val javaHome = System.getProperty("java.home")
            appendLine("-libraryjars '$javaHome/jmods/java.base.jmod'(!**.jar,!module-info.class)")
            appendLine("-dontwarn")
            appendLine("-dontnote")
            // Gradle tracks consumer bytecode; ProGuard only sees its own input JARs.
            // Recompute when a new Suite call introduces another crypto entry point.
            appendLine("-forceprocessing")
            appendLine("-dontoptimize")
            appendLine("-dontobfuscate")
            appendLine("-keepattributes Signature,InnerClasses,EnclosingMethod")
            roots.forEach { appendLine("-keep class $it { *; }") }
        }
        val config = Configuration()
        ConfigurationParser(configText, name, null, System.getProperties()).use { it.parse(config) }
        ProGuard(config).execute()
        ZipOutputStream(output.outputStream().buffered()).use { target ->
            fun copy(source: ZipFile, entry: ZipEntry) {
                target.putNextEntry(ZipEntry(entry.name).apply { time = 0 })
                source.getInputStream(entry).use { it.copyTo(target) }
                target.closeEntry()
            }
            ZipFile(shrunk).use { source ->
                source.entries().asSequence().filterNot { it.isDirectory }.sortedBy { it.name }
                    .forEach { copy(source, it) }
            }
            ZipFile(inputJar.get().asFile).use { source ->
                copy(source, checkNotNull(source.getEntry("META-INF/LICENSE.md")))
            }
        }
        logger.lifecycle("[AXS-Package] Crypto: {} entry points, {} -> {} bytes",
            roots.size, inputJar.get().asFile.length(), output.length())
    }
}

/** Reference upstream artifacts for interoperability tests without execution-time project access. */
abstract class PackagingReferenceArguments : CommandLineArgumentProvider {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sqliteJar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val cryptoJar: RegularFileProperty

    override fun asArguments(): Iterable<String> = listOf(
        "-Daxs.packaging.originalSqlite=${sqliteJar.get().asFile.absolutePath}",
        "-Daxs.packaging.originalCrypto=${cryptoJar.get().asFile.absolutePath}"
    )
}
