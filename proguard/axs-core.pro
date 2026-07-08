# ═══════════════════════════════════════════════════════════════════
# ArcartX-Suite Core - ProGuard Rules
# ═══════════════════════════════════════════════════════════════════

# ─── 混合认证代理（独立进程入口，必须保留类名/包名/成员） ──────
# 该类由 start-mixed-auth 脚本以 `java -cp ArcartX-Suite.jar
# xuanmo.arcartxsuite.auth.MixedYggdrasilProxy <port>` 独立启动，
# 插件内不再直接 new 它，故必须 keep，否则会被混淆删除或 repackage 而无法定位。
-keep class xuanmo.arcartxsuite.auth.MixedYggdrasilProxy { *; }
-keep class xuanmo.arcartxsuite.auth.MixedYggdrasilProxy$* { *; }

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

# ─── Suite-core 契约接口（明文、版本化，宿主薄壳与业务核心唯一耦合点） ──
# 宿主薄壳直接引用本接口，必须保留全限定名与成员（且 encrypt-jar 保持明文）。
-keep interface xuanmo.arcartxsuite.bootstrap.SuiteCore { *; }

# ─── 业务核心实现类：保留类名 + 无参构造器供宿主反射加载 ───────────
# 宿主以 Class.forName("xuanmo.arcartxsuite.SuiteCoreImpl") 反射实例化，故类名与
# 无参构造器不可被重命名/裁剪；类体其余成员仍正常混淆，随后由 encrypt-jar 逐类加密。
-keep class xuanmo.arcartxsuite.SuiteCoreImpl {
    public <init>();
}

# ─── bridge 包：不再保留任何实现类名 ─────────────────────────────
# 所有核心 bridge 均通过 xuanmo.arcartxsuite.api.bridge.* 接口暴露，并由宿主实现。
# 模块不直接引用 xuanmo.arcartxsuite.bridge.* 下的任何实现类，核心 bridge 可完全混淆。


# ─── Shadow 打包的第三方库不混淆 ─────────────────────────────
-keep class com.zaxxer.hikari.** { *; }
-keep class redis.clients.** { *; }
-keep class com.mysql.** { *; }
-keep class org.sqlite.** { *; }
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

# ─── 保留资源相关（排除加密的 .axb/.axl 二进制资源）─────────
-adaptresourcefilenames !**.axb,!**.axl,!**.yml,!**.yaml
-adaptresourcefilecontents !**.axb,!**.axl,!**.yml,!**.yaml

# ─── 混淆字典（使用难以辨认的名称） ──────────────────────────
-obfuscationdictionary dictionary.txt
-classobfuscationdictionary dictionary.txt
-packageobfuscationdictionary dictionary.txt

# ─── 重定向包名（所有未 keep 的类扁平化到 internal） ─────────
-repackageclasses 'xuanmo.arcartxsuite.internal'

# ─── 允许更激进的重命名 ──────────────────────────────────────
-allowaccessmodification
