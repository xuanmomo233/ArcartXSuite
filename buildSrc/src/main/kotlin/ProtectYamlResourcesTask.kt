import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * 将 YAML 资源加密为 .axb 文件。
 *
 * 可在 axs-core 和各模块子项目中复用。
 */
@DisableCachingByDefault(because = "输出文件包含随机 IV，任务结果不适合作为构建缓存条目。")
abstract class ProtectYamlResourcesTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val licenseBoundModuleId: Property<String>

    @TaskAction
    fun generate() {
        val root = sourceDir.asFile.get().toPath()
        outputDir.asFile.get().deleteRecursively()
        val protectedRoot = outputDir.file("arcartx/internal/protected").get().asFile
        val licenseRoot = outputDir.file("arcartx/internal/license").get().asFile
        protectedRoot.mkdirs()

        sourceDir.asFileTree.matching {
            include("**/*.yml")
            include("**/*.yaml")
            exclude("plugin.yml")
        }.files.sortedBy { it.path }.forEach { source ->
            val resourcePath = root.relativize(source.toPath()).toString().replace('\\', '/')
            val moduleId = licenseBoundModuleId.orNull
            val licenseBound = !moduleId.isNullOrBlank() && resourcePath != "module.yml"
            if (!licenseBound) {
                val encryptedOutput = File(protectedRoot, "${encodeResourcePath(resourcePath)}.axb")
                encryptedOutput.parentFile.mkdirs()
                encryptedOutput.writeBytes(encrypt(source.readBytes(), deriveResourceKey()))
            } else {
                val paidModuleId = moduleId ?: error("licenseBoundModuleId is required")
                val encryptedOutput = File(File(licenseRoot, paidModuleId), "${encodeResourcePath(resourcePath)}.axl")
                encryptedOutput.parentFile.mkdirs()
                encryptedOutput.writeBytes(encryptLicenseBound(source.readBytes(), paidModuleId))
            }
        }
    }

    private fun encrypt(bytes: ByteArray, key: ByteArray): ByteArray {
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, iv)
        )

        val payload = cipher.doFinal(gzip(bytes))
        return ByteArrayOutputStream().use { output ->
            output.write(byteArrayOf(0x41.toByte(), 0x58.toByte(), 0x52.toByte(), 0x31.toByte()))
            output.write(iv)
            output.write(payload)
            output.toByteArray()
        }
    }

    private fun encryptLicenseBound(bytes: ByteArray, moduleId: String): ByteArray {
        val payload = encrypt(bytes, deriveModuleResourceKey(moduleId))
        payload[2] = 0x4C.toByte()
        return payload
    }

    private fun encodeResourcePath(resourcePath: String): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(resourcePath.toByteArray(Charsets.UTF_8))
    }

    private fun decodeResourceSeed(): ByteArray {
        return ByteArray(RESOURCE_SEED_LEFT.size) { index ->
            val offset = (index * 29 + 17) and 0xFF
            (RESOURCE_SEED_LEFT[index] xor RESOURCE_SEED_RIGHT[index] xor offset).toByte()
        }
    }

    private fun deriveResourceKey(): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(decodeResourceSeed())
        digest.update(RESOURCE_DIGEST_SALT)
        return digest.digest()
    }

    private fun deriveModuleResourceKey(moduleId: String): ByteArray {
        return MessageDigest.getInstance("SHA-256")
            .digest("AXS-MODULE-RESOURCE-v1|$moduleId".toByteArray(Charsets.UTF_8))
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            gzip.write(bytes)
        }
        return output.toByteArray()
    }

    private companion object {
        val RESOURCE_SEED_LEFT = intArrayOf(
            175, 119, 140, 14, 90, 244, 58, 154,
            22, 234, 29, 141, 21, 229, 61, 74,
            181, 98, 76, 164, 206, 128, 205, 200
        )

        val RESOURCE_SEED_RIGHT = intArrayOf(
            142, 91, 87, 25, 42, 3, 205, 96,
            67, 146, 110, 46, 153, 21, 165, 229,
            114, 152, 36, 68, 209, 105, 189, 51
        )

        val RESOURCE_DIGEST_SALT = byteArrayOf(19, 51, 87, 123, 9, 44, 62, 108)
    }
}
