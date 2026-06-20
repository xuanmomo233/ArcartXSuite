import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
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
 * 构建流水线：编译 → 混淆 → 字符串加密 → **ClassFinal VMP** → 完整性嵌入。
 */
abstract class ClassFinalTask : DefaultTask() {

    @get:InputFile
    abstract val inputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    /** ClassFinal 加密密码（运行时通过 -javaagent 参数传入） */
    @get:Input
    abstract val password: Property<String>

    /** 需要加密的包名列表 */
    @get:Input
    abstract val packages: ListProperty<String>

    /** 排除不加密的类名模式 */
    @get:Input
    @get:Optional
    abstract val excludes: ListProperty<String>

    /** ClassFinal jar 路径（tools/classfinal/classfinal-fatjar.jar） */
    @get:InputFile
    abstract val classFinalJar: RegularFileProperty

    init {
        group = "protection"
        description = "Step 3: ClassFinal VMP method body encryption"
        password.convention("AXS-DEFAULT-CHANGE-ME")
        excludes.convention(listOf())
    }

    @TaskAction
    fun execute() {
        val cfJar = classFinalJar.get().asFile
        if (!cfJar.isFile) {
            throw IllegalStateException(
                "ClassFinal jar 未找到: ${cfJar.absolutePath}\n" +
                "请将 classfinal-fatjar.jar 放到 tools/classfinal/ 目录下"
            )
        }

        logger.lifecycle("[AXS-Protect] Step 3 (ClassFinal): ${inputJar.get().asFile.name}")
        logger.lifecycle("  加密包: ${packages.get().joinToString(", ")}")

        val input = inputJar.get().asFile
        val output = outputJar.get().asFile
        val expectedEncrypted = java.io.File(input.parentFile, "${input.nameWithoutExtension}-encrypted.jar")

        val args = mutableListOf<String>()
        // classfinal 1.2.1 参数格式：-file <input> -packages <pkgs> -pwd <pwd> -Y
        args.addAll(listOf("-file", input.absolutePath))
        args.addAll(listOf("-packages", packages.get().joinToString(",")))
        args.addAll(listOf("-pwd", password.get()))
        args.add("-Y") // 自动确认，避免交互式阻塞

        if (excludes.get().isNotEmpty()) {
            args.addAll(listOf("-exclude", excludes.get().joinToString(",")))
        }

        // 使用 ProcessBuilder 避免 project.javaexec（Gradle 配置缓存不兼容）
        val javaExe = resolveJavaExecutable()
        val cmd = listOf(javaExe, "-Xmx512m", "-cp", cfJar.absolutePath) +
                listOf("net.roseboy.classfinal.Main") + args

        logger.lifecycle("  执行: ${cmd.joinToString(" ")}")
        val process = ProcessBuilder(cmd).inheritIO().start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("ClassFinal 执行失败，exit code: $exitCode")
        }

        // classfinal 输出固定为 "{input}-encrypted.jar"，需要重命名到指定 output
        if (expectedEncrypted.isFile) {
            expectedEncrypted.copyTo(output, overwrite = true)
            expectedEncrypted.delete()
            logger.lifecycle("[AXS-Protect] Step 3 完成: ${output.name}")
        } else {
            throw GradleException(
                "ClassFinal 未生成预期输出文件: ${expectedEncrypted.absolutePath}"
            )
        }
    }

    private fun resolveJavaExecutable(): String {
        val javaHome = System.getProperty("java.home")
        val winExe = java.io.File(javaHome, "bin/java.exe")
        if (winExe.exists()) return winExe.absolutePath
        val unixExe = java.io.File(javaHome, "bin/java")
        if (unixExe.exists()) return unixExe.absolutePath
        return "java"
    }
}
