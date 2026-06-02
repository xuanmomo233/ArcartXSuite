import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Step 3：ClassFinal VMP 外壳加密。
 *
 * 对已混淆的 Jar 执行方法体抽空：将指定包下的 class 方法体替换为
 * 加密 stub，运行时通过 ClassFinal agent 解密执行。
 *
 * 这一步让反编译者即使绕过 ProGuard 名称混淆，也只能看到空方法体。
 *
 * ClassFinal 开源版本：https://github.com/core-lib/classfinal
 *
 * 构建流水线：编译 → 混淆 → Native 下沉 → **ClassFinal VMP** → 完整性嵌入。
 */
abstract class ClassFinalTask : DefaultTask() {

    @get:InputFile
    abstract val inputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    /** ClassFinal 加密密码（运行时通过 -javaagent 参数传入） */
    @get:Input
    abstract val password: Property<String>

    /** 需要加密的包名列表（如 xuanmo.arcartxsuite.license） */
    @get:Input
    abstract val packages: ListProperty<String>

    /** 排除不加密的类名模式 */
    @get:Input
    @get:Optional
    abstract val excludes: ListProperty<String>

    /** ClassFinal jar 路径（tools/classfinal/classfinal-fatjar.jar） */
    @get:InputFile
    @get:Optional
    abstract val classFinalJar: RegularFileProperty

    init {
        group = "protection"
        description = "Step 3: ClassFinal VMP method body encryption"
        password.convention("AXS-DEFAULT-CHANGE-ME")
        excludes.convention(listOf())
    }

    @TaskAction
    fun execute() {
        val cfJar = resolveClassFinalJar()
        logger.lifecycle("[AXS-Protect] Step 3 (ClassFinal): ${inputJar.get().asFile.name}")
        logger.lifecycle("  加密包: ${packages.get().joinToString(", ")}")

        val args = mutableListOf<String>()
        args.addAll(listOf("-jar", inputJar.get().asFile.absolutePath))
        args.addAll(listOf("-o", outputJar.get().asFile.absolutePath))
        args.addAll(listOf("-pwd", password.get()))
        args.addAll(listOf("-pkg", packages.get().joinToString(",")))

        if (excludes.get().isNotEmpty()) {
            args.addAll(listOf("-exclude", excludes.get().joinToString(",")))
        }

        // ClassFinal 通过命令行调用
        project.javaexec {
            classpath = project.files(cfJar)
            mainClass.set("net.roseboy.classfinal.ClassFinal")
            this.args = args
            jvmArgs = listOf("-Xmx512m")
        }

        logger.lifecycle("[AXS-Protect] Step 3 完成: ${outputJar.get().asFile.name}")
    }

    private fun resolveClassFinalJar(): java.io.File {
        if (classFinalJar.isPresent) {
            val f = classFinalJar.get().asFile
            if (f.isFile) return f
        }
        val local = project.rootProject.file("tools/classfinal/classfinal-fatjar.jar")
        if (local.isFile) return local
        throw IllegalStateException(
            "ClassFinal jar 未找到。请将 classfinal-fatjar.jar 放到 tools/classfinal/ 目录下\n" +
            "下载地址: https://github.com/core-lib/classfinal/releases"
        )
    }
}
