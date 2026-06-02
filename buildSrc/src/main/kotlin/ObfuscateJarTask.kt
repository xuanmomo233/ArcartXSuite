import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import proguard.Configuration
import proguard.ConfigurationParser
import proguard.ProGuard

/**
 * Step 1：ProGuard 字节码混淆。
 *
 * 擦除名称特征 + 死代码剔除 + 重打包到内部命名空间。
 * 构建流水线：编译 → **混淆** → Native 下沉 → ClassFinal VMP → 完整性嵌入。
 *
 * 使用 proguard-base 直接调用 [ProGuard] 核心 API，
 * 避免 proguard-gradle 的 Kotlin 版本依赖问题。
 */
abstract class ObfuscateJarTask : DefaultTask() {

    @get:InputFile
    abstract val inputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:InputFiles
    abstract val libraryJars: ConfigurableFileCollection

    /** 额外 .pro 规则文件 */
    @get:InputFiles
    @get:Optional
    abstract val configFiles: ConfigurableFileCollection

    @get:Input
    abstract val coreJar: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val moduleEntryClass: Property<String>

    init {
        group = "protection"
        description = "Step 1: ProGuard bytecode obfuscation"
        coreJar.convention(false)
    }

    @TaskAction
    fun execute() {
        logger.lifecycle("[AXS-Protect] Step 1 (ProGuard): ${inputJar.get().asFile.name}")

        val proFile = temporaryDir.resolve("generated-rules.pro")
        proFile.writeText(buildProGuardConfig())

        val configuration = Configuration()
        val parser = ConfigurationParser(proFile, System.getProperties())
        try {
            parser.parse(configuration)
        } finally {
            parser.close()
        }

        // 额外规则文件
        configFiles.files.forEach { extraFile ->
            val extraParser = ConfigurationParser(extraFile, System.getProperties())
            try {
                extraParser.parse(configuration)
            } finally {
                extraParser.close()
            }
        }

        ProGuard(configuration).execute()
        logger.lifecycle("[AXS-Protect] Step 1 完成: ${outputJar.get().asFile.name}")
    }

    private fun buildProGuardConfig(): String {
        val sb = StringBuilder()

        // 输入输出
        sb.appendLine("-injars ${quote(inputJar.get().asFile)}")
        sb.appendLine("-outjars ${quote(outputJar.get().asFile)}")

        // Library jars
        libraryJars.files.forEach { sb.appendLine("-libraryjars ${quote(it)}") }

        // JDK modules (Java 17)
        val javaHome = System.getProperty("java.home")
        listOf("java.base", "java.logging", "java.sql", "java.management",
               "java.naming", "java.desktop", "java.instrument").forEach { mod ->
            sb.appendLine("-libraryjars ${quote(java.io.File("$javaHome/jmods/$mod.jmod"))}(!**.jar,!module-info.class)")
        }

        // 通用规则
        sb.appendLine("-dontwarn")
        sb.appendLine("-dontoptimize")
        sb.appendLine("-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,Exceptions")

        // ── 公开 API 全保留 ──
        sb.appendLine("-keep class xuanmo.arcartxsuite.api.** { *; }")

        // ── Bukkit 反射 ──
        sb.appendLine("-keep class * extends org.bukkit.plugin.java.JavaPlugin { *; }")
        sb.appendLine("-keep class * extends xuanmo.arcartxsuite.api.AbstractAXSModule { *; }")
        sb.appendLine("-keep class * implements xuanmo.arcartxsuite.api.AXSModule { *; }")
        sb.appendLine("""
-keep class * implements org.bukkit.event.Listener {
    @org.bukkit.event.EventHandler <methods>;
}
        """.trimIndent())
        sb.appendLine("-keep class * implements org.bukkit.command.TabExecutor { *; }")
        sb.appendLine("-keep class * extends me.clip.placeholderapi.expansion.PlaceholderExpansion { *; }")
        sb.appendLine("-keep enum * { public static **[] values(); public static ** valueOf(java.lang.String); }")

        // ── JNI native 方法保留（Step 2 桥接层） ──
        sb.appendLine("-keepclasseswithmembers class * { native <methods>; }")

        // ── Record 类 ──
        sb.appendLine("""
-keepclassmembers class * extends java.lang.Record {
    <fields>;
    public <methods>;
}
        """.trimIndent())

        if (coreJar.get()) {
            sb.appendLine("-keep class xuanmo.arcartxsuite.bridge.** { public *; }")
            sb.appendLine("-keep class xuanmo.arcartxsuite.license.LicenseService { public *; }")
            sb.appendLine("-keep class xuanmo.arcartxsuite.license.LicenseDecision { *; }")
            sb.appendLine("-keep class xuanmo.arcartxsuite.license.LicenseDiagnostics { *; }")
            sb.appendLine("-keep class xuanmo.arcartxsuite.license.EncryptedResourceLoader { public *; }")
            sb.appendLine("-keep class xuanmo.arcartxsuite.module.ModuleRegistry { public *; }")
            sb.appendLine("-keep class xuanmo.arcartxsuite.module.ModuleClassLoader { *; }")
            sb.appendLine("-keep class xuanmo.arcartxsuite.security.** { public *; }")
            sb.appendLine("-keep class xuanmo.arcartxsuite.config.** { public *; }")
            sb.appendLine("-keep class xuanmo.arcartxsuite.command.** { *; }")
            // ↑ currency/combat/item/mythiclib/util 已迁入 axs-api，由 api.** keep 规则覆盖
        } else if (moduleEntryClass.isPresent) {
            sb.appendLine("-keep class ${moduleEntryClass.get()} { *; }")
        }

        return sb.toString()
    }

    private fun quote(file: java.io.File): String {
        val path = file.absolutePath
        return if (path.contains(' ')) "'$path'" else path
    }
}
