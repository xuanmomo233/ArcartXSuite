# ═══════════════════════════════════════════════════════════════════
# ArcartXSuite Core - ProGuard Rules
# ═══════════════════════════════════════════════════════════════════

# ─── 字符串加密候选（后续可用 Allatori 替代此段） ─────────────
# 目前 ProGuard 不支持字符串加密，此处标记需要保护的包
# 待升级到 Allatori 时启用 string encryption 对以下包

# ─── 内部实现混淆（license 包内部细节） ───────────────────────
-keepclassmembers class xuanmo.arcartxsuite.license.LicenseVerifier {
    # 保留 public static 方法（被 ResourceKeyManager 引用）
    public static *** base64UrlDecode(java.lang.String);
}

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

# ─── 重定向包名 ──────────────────────────────────────────────
-repackageclasses 'xuanmo.arcartxsuite.internal'
