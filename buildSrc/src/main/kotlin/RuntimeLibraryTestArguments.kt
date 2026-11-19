import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.process.CommandLineArgumentProvider

abstract class RuntimeLibraryTestArguments : CommandLineArgumentProvider {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val jar: RegularFileProperty
    @get:InputDirectory @get:PathSensitive(PathSensitivity.NONE) abstract val libraries: DirectoryProperty
    override fun asArguments() = listOf(
        "-Daxs.runtime.test.jar=${jar.get().asFile.absolutePath}",
        "-Daxs.runtime.test.libraries=${libraries.get().asFile.absolutePath}"
    )
}
