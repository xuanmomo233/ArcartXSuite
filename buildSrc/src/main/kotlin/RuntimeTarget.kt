import org.gradle.api.DefaultTask
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.Serializable

/** Distribution policy; runtime assets never determine which business classes are compiled. */
enum class RuntimeTarget(val id: String, val os: String, val arch: String) {
    UNIVERSAL("universal", "windows,linux", "any"),
    WINDOWS_X86_64("windows-x86_64", "windows", "x86_64"),
    LINUX_X86_64("linux-x86_64", "linux", "x86_64");

    val fileSuffix: String get() = if (this == UNIVERSAL) "" else "-$id"

    val nativeFiles: Set<String> get() = when (this) {
        UNIVERSAL -> setOf("axs-native.dll", "axs-native.enc", "libaxs-native.so", "libaxs-native.enc")
        WINDOWS_X86_64 -> setOf("axs-native.dll", "axs-native.enc")
        LINUX_X86_64 -> setOf("libaxs-native.so", "libaxs-native.enc")
    }

    fun keeps(path: String): Boolean = when {
        path.startsWith("org/sqlite/native/") -> when (this) {
            UNIVERSAL -> listOf("Windows/", "Linux/", "Linux-Musl/").any {
                path.startsWith("org/sqlite/native/$it")
            }
            WINDOWS_X86_64 -> path.startsWith("org/sqlite/native/Windows/x86_64/")
            LINUX_X86_64 -> listOf("Linux/x86_64/", "Linux-Musl/x86_64/").any {
                path.startsWith("org/sqlite/native/$it")
            }
        }
        path.startsWith("native/") -> path.removePrefix("native/") in nativeFiles
        else -> true
    }

    companion object {
        @JvmStatic fun parse(value: String): RuntimeTarget = values().firstOrNull { it.id == value }
            ?: throw IllegalArgumentException("未知 axs.runtimeTarget=$value；可选值: ${values().joinToString { it.id }}")
    }
}

/** A serializable CopySpec predicate also works when Gradle reuses its configuration cache. */
class ExcludeOtherRuntimeAssets(private val target: RuntimeTarget) : Spec<FileTreeElement>, Serializable {
    override fun isSatisfiedBy(element: FileTreeElement): Boolean = !element.isDirectory && !target.keeps(element.path)
}

abstract class GenerateRuntimeTargetTask : DefaultTask() {
    @get:Input abstract val targetId: Property<String>
    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction fun generate() {
        val target = RuntimeTarget.parse(targetId.get())
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText("format=1\ntarget=${target.id}\nos=${target.os}\narch=${target.arch}\n", Charsets.UTF_8)
    }
}
