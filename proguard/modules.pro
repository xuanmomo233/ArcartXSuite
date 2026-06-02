# ═══════════════════════════════════════════════════════════════════
# ArcartXSuite Modules - ProGuard Rules (所有模块共享)
# ═══════════════════════════════════════════════════════════════════

# ─── 模块入口类由 ObfuscateJarTask 动态注入，无需在此声明 ─────

# ─── HikariCP 连接池（部分模块 shade） ────────────────────────
-keep class com.zaxxer.hikari.** { *; }

# ─── Bukkit 配置序列化 ───────────────────────────────────────
-keepclassmembers class * {
    public static ** deserialize(java.util.Map);
    public java.util.Map serialize();
}

# ─── 保留 record 类（Java 17） ───────────────────────────────
-keepclassmembers class * extends java.lang.Record {
    <fields>;
    public <methods>;
}

# ─── 混淆字典 ────────────────────────────────────────────────
-obfuscationdictionary dictionary.txt
-classobfuscationdictionary dictionary.txt
-packageobfuscationdictionary dictionary.txt

# ─── 扁平化包名（混淆类名但不改变方法可见性）───────────────
-repackageclasses ''
