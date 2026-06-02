import java.io.File
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * 为 Jar 计算所有 .class 文件的 SHA-256 组合摘要，
 * 并嵌入到 META-INF/axs-integrity.bin 中。
 *
 * 运行时由 JarIntegrityVerifier 重新计算并校验。
 */
abstract class EmbedIntegrityTask : DefaultTask() {

    @get:InputFile
    abstract val inputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    init {
        group = "obfuscation"
        description = "Embed integrity digest into JAR"
    }

    @TaskAction
    fun embed() {
        val input = inputJar.get().asFile
        val output = outputJar.get().asFile

        // 1. 计算 .class 组合摘要
        val digest = computeClassDigest(input)

        // 2. 复制 jar 并嵌入摘要文件
        JarFile(input).use { jar ->
            JarOutputStream(output.outputStream().buffered()).use { out ->
                // 先写入 integrity 文件
                out.putNextEntry(ZipEntry("META-INF/axs-integrity.bin"))
                out.write(digest)
                out.closeEntry()

                // 复制其余条目
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name == "META-INF/axs-integrity.bin") continue
                    out.putNextEntry(ZipEntry(entry.name))
                    jar.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
    }

    private fun computeClassDigest(jarFile: File): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        JarFile(jarFile).use { jar ->
            jar.stream()
                .filter { it.name.endsWith(".class") }
                .sorted(Comparator.comparing(JarEntry::getName))
                .forEach { entry ->
                    md.update(entry.name.toByteArray())
                    jar.getInputStream(entry).use { input ->
                        md.update(input.readAllBytes())
                    }
                }
        }
        return md.digest()
    }
}
