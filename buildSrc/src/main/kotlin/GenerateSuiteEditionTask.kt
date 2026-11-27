import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateSuiteEditionTask : DefaultTask() {
    @get:Input
    abstract val edition: Property<String>

    @get:OutputFile
    abstract val editionFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val output = editionFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText("${edition.get()}\n")
    }
}
