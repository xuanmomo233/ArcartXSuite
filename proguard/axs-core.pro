# ═══════════════════════════════════════════════════════════════════
# ArcartX-Suite Core - ProGuard Rules
# ═══════════════════════════════════════════════════════════════════

# ─── 混合认证代理（独立进程入口，必须保留类名/包名/成员） ──────
# 该类由 start-mixed-auth 脚本以 `java -cp ArcartX-Suite.jar
# xuanmo.arcartxsuite.auth.MixedYggdrasilProxy <port>` 独立启动，
# 插件内不再直接 new 它，故必须 keep，否则会被混淆删除或 repackage 而无法定位。
-keep class xuanmo.arcartxsuite.auth.MixedYggdrasilProxy { *; }
-keep class xuanmo.arcartxsuite.auth.MixedYggdrasilProxy$* { *; }

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
