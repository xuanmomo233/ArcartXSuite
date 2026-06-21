import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.*
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/**
 * Step 1.5：ASM 字符串加密。
 *
 * 遍历 JAR 中所有 .class 文件，将 LDC String 常量替换为
 * 加密后的 Base64 字符串 + 运行时解密调用。
 *
 * 加密算法：XOR（密钥按类名 hashCode 派生），轻量但足以消除明文。
 *
 * 构建流水线：编译 → ProGuard → **字符串加密** → ClassFinal → 完整性嵌入。
 */
abstract class StringEncryptTask : DefaultTask() {

    @get:InputFile
    abstract val inputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    /** 最小字符串长度阈值，短于此长度的字符串不加密 */
    @get:Input
    @get:Optional
    abstract val minLength: Property<Int>

    init {
        group = "protection"
        description = "Step 1.5: ASM string constant encryption"
        minLength.convention(3)
    }

    @TaskAction
    fun execute() {
        val input = inputJar.get().asFile
        val output = outputJar.get().asFile
        val threshold = minLength.get()

        logger.lifecycle("[AXS-Protect] Step 1.5 (StringEncrypt): ${input.name}")

        output.parentFile.mkdirs()
        var encryptedCount = 0
        var classCount = 0

        JarFile(input).use { jar ->
            JarOutputStream(output.outputStream().buffered()).use { out ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()

                    if (entry.name.endsWith(".class") && shouldProcess(entry.name)) {
                        val original = jar.getInputStream(entry).use { it.readAllBytes() }
                        val result = encryptStrings(original, entry.name, threshold)
                        if (result.modifiedCount > 0) {
                            encryptedCount += result.modifiedCount
                            classCount++
                        }
                        out.putNextEntry(ZipEntry(entry.name))
                        out.write(result.bytecode)
                        out.closeEntry()
                    } else {
                        out.putNextEntry(ZipEntry(entry.name))
                        jar.getInputStream(entry).use { it.copyTo(out) }
                        out.closeEntry()
                    }
                }
            }
        }

        logger.lifecycle("[AXS-Protect] Step 1.5 完成: $classCount 个类中 $encryptedCount 个字符串已加密")
    }

    /**
     * 判断是否应该处理此 class 文件。
     * 跳过第三方库和 API 包。
     */
    private fun shouldProcess(entryName: String): Boolean {
        // 只处理 xuanmo 包下的类
        if (!entryName.startsWith("xuanmo/")) return false
        // 跳过 API 包（保持可读）
        if (entryName.startsWith("xuanmo/arcartxsuite/api/")) return false
        // 跳过 NativeBridge（JNI 桥接层）
        if (entryName.contains("NativeBridge")) return false
        // 跳过 MixedYggdrasilProxy（独立进程入口）
        if (entryName.contains("MixedYggdrasilProxy")) return false
        return true
    }

    /**
     * 判断字符串是否应该加密。
     */
    private fun shouldEncrypt(value: String, threshold: Int): Boolean {
        if (value.length < threshold) return false
        // 跳过纯路径分隔符、空白、单个标点
        if (value.all { it == '/' || it == '.' || it == '\\' || it == ' ' }) return false
        // 跳过纯数字
        if (value.all { it.isDigit() || it == '.' || it == '-' }) return false
        // 跳过 Java 内部类名模式（如 "java/lang/String"）
        // 不跳过 — 这些也应该加密以增加逆向难度
        return true
    }

    /**
     * 对单个 class 文件执行字符串加密。
     */
    private fun encryptStrings(bytecode: ByteArray, entryName: String, threshold: Int): EncryptResult {
        val className = entryName.removeSuffix(".class")
        val xorKey = deriveKey(className)

        // 前置检查：如果类中已有 $s 解密方法，说明已被加密过，跳过
        // 这通常发生在某个类被意外打包进多个 JAR 且多次经过字符串加密
        val checker = DecryptorExistenceChecker()
        ClassReader(bytecode).accept(checker, ClassReader.SKIP_FRAMES)
        if (checker.hasDecryptor) {
            logger.debug("[AXS-Protect] 跳过已加密类: $entryName")
            return EncryptResult(bytecode, 0)
        }

        val collector = StringCollectorVisitor(threshold)

        // 第一遍：收集需要加密的字符串
        val reader1 = ClassReader(bytecode)
        reader1.accept(collector, ClassReader.SKIP_FRAMES)

        if (collector.strings.isEmpty()) {
            return EncryptResult(bytecode, 0)
        }

        // 第二遍：替换字符串 + 注入解密方法
        val reader2 = ClassReader(bytecode)
        val writer = ClassWriter(reader2, ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        val encryptor = StringEncryptorVisitor(writer, className, xorKey, collector.strings, threshold, this)
        try {
            reader2.accept(encryptor, ClassReader.SKIP_FRAMES)
        } catch (e: Exception) {
            // 如果 COMPUTE_FRAMES 失败（缺少依赖类），跳过此类
            logger.debug("[AXS-Protect] 跳过字符串加密 (frame 计算失败): $entryName - ${e.message}")
            return EncryptResult(bytecode, 0)
        }

        return EncryptResult(writer.toByteArray(), encryptor.modifiedCount)
    }

    /**
     * 按类名派生 XOR 密钥（每个类不同）。
     */
    private fun deriveKey(className: String): ByteArray {
        val hash = className.hashCode()
        return byteArrayOf(
            (hash ushr 24).toByte(),
            (hash ushr 16).toByte(),
            (hash ushr 8).toByte(),
            hash.toByte(),
            ((hash * 31) ushr 24).toByte(),
            ((hash * 31) ushr 16).toByte(),
            ((hash * 31) ushr 8).toByte(),
            ((hash * 31)).toByte()
        )
    }

    data class EncryptResult(val bytecode: ByteArray, val modifiedCount: Int)

    // ─── 前置检查：类中是否已有 $s 解密方法 ─────────────────────

    private class DecryptorExistenceChecker : ClassVisitor(Opcodes.ASM9) {
        var hasDecryptor = false

        override fun visitMethod(
            access: Int, name: String?, descriptor: String?,
            signature: String?, exceptions: Array<out String>?
        ): MethodVisitor? {
            if (name == "\$s" && descriptor == "(Ljava/lang/String;)Ljava/lang/String;") {
                hasDecryptor = true
            }
            return null
        }
    }

    // ─── 第一遍：收集字符串 ──────────────────────────────────

    private inner class StringCollectorVisitor(private val threshold: Int) : ClassVisitor(Opcodes.ASM9) {
        val strings = mutableSetOf<String>()

        override fun visitMethod(
            access: Int, name: String?, descriptor: String?,
            signature: String?, exceptions: Array<out String>?
        ): MethodVisitor {
            return object : MethodVisitor(Opcodes.ASM9) {
                override fun visitLdcInsn(value: Any?) {
                    if (value is String && shouldEncrypt(value, threshold)) {
                        strings.add(value)
                    }
                }
            }
        }
    }

    // ─── 第二遍：替换字符串 + 注入解密方法 ──────────────────────

    private class StringEncryptorVisitor(
        cv: ClassWriter,
        private val className: String,
        private val xorKey: ByteArray,
        private val targetStrings: Set<String>,
        private val threshold: Int,
        private val task: StringEncryptTask
    ) : ClassVisitor(Opcodes.ASM9, cv) {

        var modifiedCount = 0
        private var decryptorInjected = false

        override fun visitMethod(
            access: Int, name: String?, descriptor: String?,
            signature: String?, exceptions: Array<out String>?
        ): MethodVisitor {
            val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
            return object : MethodVisitor(Opcodes.ASM9, mv) {
                override fun visitLdcInsn(value: Any?) {
                    if (value is String && value in targetStrings) {
                        val encrypted = xorEncrypt(value.toByteArray(Charsets.UTF_8), xorKey)
                        val encoded = Base64.getEncoder().encodeToString(encrypted)
                        mv.visitLdcInsn(encoded)
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            className,
                            "\$s",
                            "(Ljava/lang/String;)Ljava/lang/String;",
                            false
                        )
                        modifiedCount++
                    } else {
                        super.visitLdcInsn(value)
                    }
                }
            }
        }

        override fun visitEnd() {
            if (modifiedCount > 0 && !decryptorInjected) {
                injectDecryptMethod()
                decryptorInjected = true
            }
            super.visitEnd()
        }

        /**
         * 注入 private static String $s(String encoded) 方法。
         *
         * 等价 Java 代码：
         * ```
         * private static String $s(String e) {
         *     byte[] d = java.util.Base64.getDecoder().decode(e);
         *     byte[] k = { key[0], key[1], ... key[7] };
         *     for (int i = 0; i < d.length; i++) d[i] ^= k[i % k.length];
         *     return new String(d, java.nio.charset.StandardCharsets.UTF_8);
         * }
         * ```
         */
        private fun injectDecryptMethod() {
            val mv = cv.visitMethod(
                Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC,
                "\$s",
                "(Ljava/lang/String;)Ljava/lang/String;",
                null,
                null
            )
            mv.visitCode()

            // byte[] d = Base64.getDecoder().decode(e);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Base64", "getDecoder",
                "()Ljava/util/Base64\$Decoder;", false)
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Base64\$Decoder", "decode",
                "(Ljava/lang/String;)[B", false)
            mv.visitVarInsn(Opcodes.ASTORE, 1) // d

            // byte[] k = { ... };
            mv.visitIntInsn(Opcodes.BIPUSH, xorKey.size)
            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE)
            for (i in xorKey.indices) {
                mv.visitInsn(Opcodes.DUP)
                mv.visitIntInsn(Opcodes.BIPUSH, i)
                mv.visitIntInsn(Opcodes.BIPUSH, xorKey[i].toInt())
                mv.visitInsn(Opcodes.BASTORE)
            }
            mv.visitVarInsn(Opcodes.ASTORE, 2) // k

            // for (int i = 0; i < d.length; i++) d[i] ^= k[i % k.length];
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitVarInsn(Opcodes.ISTORE, 3) // i = 0

            val loopStart = Label()
            val loopEnd = Label()

            mv.visitLabel(loopStart)
            mv.visitVarInsn(Opcodes.ILOAD, 3)
            mv.visitVarInsn(Opcodes.ALOAD, 1)
            mv.visitInsn(Opcodes.ARRAYLENGTH)
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd)

            // d[i] ^= k[i % k.length]
            mv.visitVarInsn(Opcodes.ALOAD, 1) // d
            mv.visitVarInsn(Opcodes.ILOAD, 3) // i
            mv.visitInsn(Opcodes.DUP2) // d, i, d, i
            mv.visitInsn(Opcodes.BALOAD) // d, i, d[i]
            mv.visitVarInsn(Opcodes.ALOAD, 2) // k
            mv.visitVarInsn(Opcodes.ILOAD, 3) // i
            mv.visitVarInsn(Opcodes.ALOAD, 2) // k
            mv.visitInsn(Opcodes.ARRAYLENGTH)
            mv.visitInsn(Opcodes.IREM) // i % k.length
            mv.visitInsn(Opcodes.BALOAD) // k[i % k.length]
            mv.visitInsn(Opcodes.IXOR)
            mv.visitInsn(Opcodes.I2B)
            mv.visitInsn(Opcodes.BASTORE)

            // i++
            mv.visitIincInsn(3, 1)
            mv.visitJumpInsn(Opcodes.GOTO, loopStart)

            mv.visitLabel(loopEnd)

            // return new String(d, StandardCharsets.UTF_8);
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/String")
            mv.visitInsn(Opcodes.DUP)
            mv.visitVarInsn(Opcodes.ALOAD, 1)
            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/nio/charset/StandardCharsets",
                "UTF_8", "Ljava/nio/charset/Charset;")
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>",
                "([BLjava/nio/charset/Charset;)V", false)
            mv.visitInsn(Opcodes.ARETURN)

            mv.visitMaxs(6, 4)
            mv.visitEnd()
        }
    }

    companion object {
        fun xorEncrypt(data: ByteArray, key: ByteArray): ByteArray {
            val result = data.copyOf()
            for (i in result.indices) {
                result[i] = (result[i].toInt() xor key[i % key.size].toInt()).toByte()
            }
            return result
        }
    }
}
