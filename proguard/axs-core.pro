# ═══════════════════════════════════════════════════════════════════
# ArcartX-Suite Core - ProGuard Rules
# ═══════════════════════════════════════════════════════════════════

# ─── 混合认证代理（独立进程入口，必须保留类名/包名/成员） ──────
# 该类由 start-mixed-auth 脚本以 `java -cp ArcartX-Suite.jar
# xuanmo.arcartxsuite.auth.minecraft.MixedYggdrasilGateway <port>` 独立启动，
# 插件内不再直接 new 它，故必须 keep，否则会被混淆删除或 repackage 而无法定位。
-keep class xuanmo.arcartxsuite.auth.minecraft.** { *; }

# 中心传输 DTO 由 Gson 按记录字段名序列化，必须保持协议字段。
-keep class xuanmo.arcartxsuite.auth.protocol.AuthWire$* { *; }
-keepattributes Record

# ─── JAR 保护引导类（方案 ②：必须保留原始类名，且含其全部内部类） ──
# encrypt-jar.py 以全限定名识别引导类（保持明文、不加密），ProtectionInit 运行时
# 也按原名捕获/复用它们；若被 ProGuard 重命名或加密，则 ClassLoader 注入链断裂。
# 这 6 个类本就是必须明文的引导脚手架，保留类名不降低整体保护强度。
-keep class xuanmo.arcartxsuite.ArcartXSuitePlugin { *; }
-keep class xuanmo.arcartxsuite.ArcartXSuitePlugin$* { *; }
-keep class xuanmo.arcartxsuite.security.NativeBridge { *; }
-keep class xuanmo.arcartxsuite.security.NativeBridge$* { *; }
-keep class xuanmo.arcartxsuite.security.protection.ProtectionInit { *; }
-keep class xuanmo.arcartxsuite.security.protection.ProtectionInit$* { *; }
-keep class xuanmo.arcartxsuite.security.protection.ProtectedClassLoader { *; }
-keep class xuanmo.arcartxsuite.security.protection.ProtectedClassLoader$* { *; }
-keep class xuanmo.arcartxsuite.security.protection.JvmAntiDebug { *; }
-keep class xuanmo.arcartxsuite.security.protection.JvmAntiDebug$* { *; }
-keep class xuanmo.arcartxsuite.security.protection.ProtectionEnvironment { *; }
-keep class xuanmo.arcartxsuite.security.protection.ProtectionEnvironment$* { *; }
-keep class xuanmo.arcartxsuite.util.HashUtils { *; }
-keep class xuanmo.arcartxsuite.util.HashUtils$* { *; }

# ─── Suite-core 契约接口（明文、版本化，宿主薄壳与业务核心唯一耦合点） ──
# 宿主薄壳直接引用本接口，必须保留全限定名与成员（且 encrypt-jar 保持明文）。
-keep interface xuanmo.arcartxsuite.bootstrap.SuiteCore { *; }
-keep class xuanmo.arcartxsuite.bootstrap.HostShutdown { *; }
-keep class xuanmo.arcartxsuite.bootstrap.HostShutdown$* { *; }
-keep class xuanmo.arcartxsuite.bootstrap.RuntimeBootstrap { *; }
-keep class xuanmo.arcartxsuite.bootstrap.RuntimeBootstrap$* { *; }
-keep interface xuanmo.arcartxsuite.bootstrap.RuntimeBootstrap$* { *; }
-keep interface xuanmo.arcartxsuite.bootstrap.HostShutdown$* { *; }

# ─── 业务核心实现类：保留类名 + 无参构造器供宿主反射加载 ───────────
# 宿主以 Class.forName("xuanmo.arcartxsuite.SuiteCoreImpl") 反射实例化，故类名与
# 无参构造器不可被重命名/裁剪；类体其余成员仍正常混淆，随后由 encrypt-jar 逐类加密。
-keep class xuanmo.arcartxsuite.SuiteCoreImpl {
    public <init>();
}

# ─── Aria 脚本玩家门面（JavaObjectMirror 按方法名反射调用） ──────
# global.player.* 脚本 API（getLevel/hasPermission/papi/command 等）由 ArcartX
# Aria 的 JavaObjectMirror 通过反射按方法名解析；方法名被混淆后脚本调用一律
# 返回 none，形如 "none >= 30" 的条件直接报「none 类型不支持比较运算」。
-keep class xuanmo.arcartxsuite.condition.AriaPlayer { *; }
-keep class xuanmo.arcartxsuite.condition.AriaPlayer$* { *; }

# ─── bridge 包：不再保留任何实现类名 ─────────────────────────────
# 所有核心 bridge 均通过 xuanmo.arcartxsuite.api.bridge.* 接口暴露，并由宿主实现。
# 模块不直接引用 xuanmo.arcartxsuite.bridge.* 下的任何实现类，核心 bridge 可完全混淆。

# ─── 跨版本兼容：PotionAccessor 反射加载链 ──────────────────────
# PotionAccessorFactory 通过 Class.forName 硬编码类名反射加载 PotionAccessorModern，
# 故接口、Modern 实现、Factory 三者的全限定名必须保留，否则混淆后 Class.forName 失败。
-keep class xuanmo.arcartxsuite.bridge.potion.PotionAccessor { *; }
-keep class xuanmo.arcartxsuite.bridge.potion.PotionAccessorModern { *; }
-keep class xuanmo.arcartxsuite.bridge.potion.PotionAccessorFactory { *; }
-keep class xuanmo.arcartxsuite.bridge.potion.PotionAccessorLegacy { *; }


# ─── Shadow 打包的第三方库不混淆 ─────────────────────────────
-keep class com.zaxxer.hikari.** { *; }
-keep class redis.clients.** { *; }
-keep class com.mysql.** { *; }
-keep class org.sqlite.** { *; }
-keep class org.tukaani.xz.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class at.favre.lib.** { *; }
-keep class net.sourceforge.pinyin4j.** { *; }
-keep class com.google.gson.** { *; }
-keep class org.java_websocket.** { *; }
-keep class org.apache.commons.pool2.** { *; }

# ─── Bukkit YAML 反序列化 ────────────────────────────────────
-keepclassmembers class * {
    public static ** deserialize(java.util.Map);
    public java.util.Map serialize();
}

# ─── 保留资源文件名/内容（不重命名 yml 等资源）─────────
-adaptresourcefilenames !**.yml,!**.yaml
-adaptresourcefilecontents !**.yml,!**.yaml

# ─── 混淆字典（使用难以辨认的名称） ──────────────────────────
-obfuscationdictionary dictionary.txt
-classobfuscationdictionary dictionary.txt
-packageobfuscationdictionary dictionary.txt

# ─── 重定向包名（所有未 keep 的类扁平化到 internal） ─────────
-repackageclasses 'xuanmo.arcartxsuite.internal'

# ─── 允许更激进的重命名 ──────────────────────────────────────
-allowaccessmodification

# ─── 调试支持：输出混淆映射 + 保留行号表 ──────────────────────
# printmapping 生成原始类名 ↔ 混淆名映射文件，用于还原栈追踪。
# SourceFile + LineNumberTable 让栈追踪显示源文件名和行号，便于定位。
# 注意：生产环境可考虑移除 LineNumberTable（略微增加逆向难度），
#       但 printmapping 务必保留并归档，否则 StackOverflowError 等无法排查。
# -printmapping 由 ObfuscateJarTask 动态注入（输出到 outputJar 同目录），
# 不在此声明：ProGuard 会把此处的相对路径相对于 proguard/ 解析，
# 而 proguard/ 下无 build/ 目录，导致 FileNotFoundException。
-keepattributes SourceFile,LineNumberTable
