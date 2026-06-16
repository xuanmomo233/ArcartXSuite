import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * 将模块 Jar 加密为 .axb 文件，用于上传到 AXS Cloud Platform。
 *
 * 加密流程：GZIP 压缩 → AES-256-GCM 加密。
 * 输出格式：IV (12 bytes) + ciphertext + auth tag (16 bytes)。
 *
 * 模块作者需在 Gradle 中配置 moduleKey 和 moduleIv（Base64），
 * 或在任务执行时自动生成随机密钥并输出到控制台。
 */
@DisableCachingByDefault(because = "输出包含随机 IV，不适合缓存。")
abstract class EncryptModuleAxbTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputAxb: RegularFileProperty

    /** 模块加密密钥，32 bytes，Base64。未设置则自动生成。 */
    @get:Input
    @get:Optional
    abstract val moduleKey: Property<String>

    /** 模块 IV，12 bytes，Base64。未设置则自动生成。 */
    @get:Input
    @get:Optional
    abstract val moduleIv: Property<String>

    @TaskAction
    fun encrypt() {
        val jarFile = inputJar.asFile.get()
        if (!jarFile.exists()) {
            throw IllegalArgumentException("输入 Jar 不存在: ${jarFile.absolutePath}")
        }

        val key: ByteArray
        val iv: ByteArray

        val keyB64: String
        val ivB64: String
        if (moduleKey.isPresent && moduleIv.isPresent) {
            keyB64 = moduleKey.get()
            ivB64 = moduleIv.get()
            key = Base64.getDecoder().decode(keyB64)
            iv = Base64.getDecoder().decode(ivB64)
            require(key.size == 32) { "moduleKey 必须是 32 bytes (Base64)" }
            require(iv.size == 12) { "moduleIv 必须是 12 bytes (Base64)" }
        } else {
            key = ByteArray(32).also { SecureRandom().nextBytes(it) }
            iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            keyB64 = Base64.getEncoder().encodeToString(key)
            ivB64 = Base64.getEncoder().encodeToString(iv)
            logger.lifecycle("[EncryptModuleAxb] 自动生成密钥（请保存到平台）：")
            logger.lifecycle("  moduleKey = $keyB64")
            logger.lifecycle("  moduleIv  = $ivB64")
        }

        val plain = jarFile.readBytes()
        val encrypted = encrypt(gzip(plain), key, iv)

        val outFile = outputAxb.asFile.get()
        outFile.parentFile.mkdirs()
        outFile.writeBytes(encrypted)

        // 写入 metadata JSON，CI 上传时读取 key/iv
        val metaFile = File(outFile.parentFile, outFile.name.replace(".axb", ".meta.json"))
        metaFile.writeText(
            """{"moduleKey":"$keyB64","moduleIv":"$ivB64","size":${encrypted.size}}"""
        )

        logger.lifecycle("[EncryptModuleAxb] 已生成: ${outFile.absolutePath} (${encrypted.size} bytes)")
    }

    private fun encrypt(bytes: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, iv)
        )
        val payload = cipher.doFinal(bytes)
        return ByteArrayOutputStream().use { output ->
            output.write(iv)
            output.write(payload)
            output.toByteArray()
        }
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            gzip.write(bytes)
        }
        return output.toByteArray()
    }
}
