package xuanmo.arcartxsuite.account;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.account.AccountType;
import xuanmo.arcartxsuite.api.account.AccountTypeService;

/**
 * 宿主统一账号识别服务实现。
 * <p>
 * 合并了原 loginview / qqbot 各自实现的判定逻辑，并修复了「微软正版未关联 LittleSkin 时
 * UUID 为 v3 被误判为离线」的问题。判定规则见 {@link AccountTypeService}。
 * <p>
 * 同时作为 {@link Listener} 在 {@link AsyncPlayerPreLoginEvent}（异步线程）阶段预热缓存，
 * 使玩家进服时账号类型已就绪，避免主线程阻塞网络请求。
 */
public final class AccountTypeServiceImpl implements AccountTypeService, Listener {

    private static final String AUTHLIB_AGENT_CLASS = "moe.yushi.authlibinjector.AuthlibInjector";
    private static final String MOJANG_PROFILE_API = "https://api.mojang.com/users/profiles/minecraft/";

    private final Logger logger;
    private final boolean enableMojangLookup;
    private final int mojangTimeoutMs;
    private final boolean debug;
    private final boolean authlibInjectorLoaded;

    /** UUID -> 已确定（含网络查询）的账号类型缓存 */
    private final ConcurrentMap<UUID, AccountType> accountTypeCache = new ConcurrentHashMap<>();
    /** 玩家名(小写) -> Mojang 官方 UUID（空串表示已确认不存在；网络失败不缓存） */
    private final ConcurrentMap<String, String> officialUuidCache = new ConcurrentHashMap<>();

    public AccountTypeServiceImpl(Logger logger, boolean enableMojangLookup, int mojangTimeoutMs, boolean debug) {
        this.logger = logger;
        this.enableMojangLookup = enableMojangLookup;
        this.mojangTimeoutMs = Math.max(1000, mojangTimeoutMs);
        this.debug = debug;
        this.authlibInjectorLoaded = detectAuthlibInjector();
    }

    @Override
    public @NotNull AccountType resolve(@Nullable UUID uuid, @Nullable String name) {
        if (uuid == null) {
            return AccountType.OFFLINE;
        }
        AccountType cached = accountTypeCache.get(uuid);
        if (cached != null) {
            return cached;
        }
        // 非阻塞：仅使用本地已有信息判定，不发起网络请求，结果不写主缓存
        return classify(uuid, name, false);
    }

    @Override
    public @NotNull AccountType resolveBlocking(@Nullable UUID uuid, @Nullable String name) {
        if (uuid == null) {
            return AccountType.OFFLINE;
        }
        AccountType cached = accountTypeCache.get(uuid);
        if (cached != null) {
            return cached;
        }
        AccountType type = classify(uuid, name, true);
        accountTypeCache.put(uuid, type);
        return type;
    }

    @Override
    public @NotNull AccountType cached(@Nullable UUID uuid) {
        if (uuid == null) {
            return AccountType.OFFLINE;
        }
        return accountTypeCache.getOrDefault(uuid, AccountType.OFFLINE);
    }

    @Override
    public boolean isAuthlibInjectorLoaded() {
        return authlibInjectorLoaded;
    }

    @Override
    public void invalidate(@Nullable UUID uuid) {
        if (uuid != null) {
            accountTypeCache.remove(uuid);
        }
    }

    @Override
    public void clearCache() {
        accountTypeCache.clear();
        officialUuidCache.clear();
    }

    /**
     * 在 PreLogin（异步线程）阶段预热账号类型缓存。
     * 使用 LOWEST 优先级尽早执行，让后续白名单门控等监听器能拿到已就绪的结果。
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        AccountType type = resolveBlocking(event.getUniqueId(), event.getName());
        if (debug) {
            UUID uuid = event.getUniqueId();
            logger.info("[AccountType] PreLogin " + event.getName()
                + " uuid=" + uuid + " v" + (uuid == null ? "?" : uuid.version())
                + " -> " + type.id());
        }
    }

    // ─── 核心分类逻辑 ──────────────────────────────────────────

    private AccountType classify(UUID uuid, String name, boolean allowNetwork) {
        if (uuid == null) {
            return AccountType.OFFLINE;
        }

        // 微软正版与 LittleSkin 都可能为 v4 UUID，不能仅凭版本号区分。
        // 必须通过 Mojang API 比较当前 UUID 与官方 UUID。
        // 关键事实：
        //   - 微软正版玩家的 UUID = Mojang 官方 UUID（无论是否通过 authlib-injector 登录）
        //   - LittleSkin 玩家的 UUID 由 yggdrasil 认证服务器分配，通常与官方 UUID 不同
        if (enableMojangLookup && allowNetwork) {
            String officialUuid = lookupOfficialUuid(name, true);
            if (!officialUuid.isBlank()) {
                if (uuidEqualsIgnoreDashes(uuid, officialUuid)) {
                    // UUID 与 Mojang 官方完全一致 = 微软正版
                    return AccountType.MICROSOFT;
                }
                // 玩家名在 Mojang 存在但 UUID 不同 = LittleSkin（名字恰好与正版相同）
                return AccountType.LITTLESKIN;
            }
        }

        // 玩家名不在 Mojang（或未启用 Mojang 查询）
        if (uuid.version() == 4) {
            // v4 且不在 Mojang：LittleSkin（authlib 环境）或某些特殊情况
            return AccountType.LITTLESKIN;
        }
        // v3 且不在 Mojang：离线
        return AccountType.OFFLINE;
    }

    private static boolean uuidEqualsIgnoreDashes(UUID uuid, String other) {
        String a = uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
        String b = other.replace("-", "").toLowerCase(Locale.ROOT);
        return a.equals(b);
    }

    /**
     * 查询玩家名对应的 Mojang 官方 UUID。
     * <p>
     * 返回空串表示「已确认该名字不是正版」；网络失败时返回空串但<strong>不写缓存</strong>，
     * 以便下次重试，避免临时网络故障污染判定结果。
     */
    private String lookupOfficialUuid(String name, boolean allowNetwork) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "";
        }
        String cached = officialUuidCache.get(normalized);
        if (cached != null) {
            return cached;
        }
        if (!allowNetwork) {
            return "";
        }
        try {
            String resolved = queryOfficialUuid(name);
            officialUuidCache.put(normalized, resolved);
            return resolved;
        } catch (IOException exception) {
            if (debug) {
                logger.warning("[AccountType] Mojang API 查询失败 (" + name + "): " + exception.getMessage()
                    + " —— 本次不缓存，下次重试");
            }
            return "";
        }
    }

    /**
     * 直接请求 Mojang Profile API。
     *
     * @return 官方 UUID（存在）或空串（HTTP 204/404 确认不存在）
     * @throws IOException 网络异常或非预期 HTTP 状态（如 429 限流），调用方据此跳过缓存
     */
    private String queryOfficialUuid(String name) throws IOException {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
        URL url = new URL(MOJANG_PROFILE_API + encoded);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(mojangTimeoutMs);
        conn.setReadTimeout(mojangTimeoutMs);
        conn.setRequestProperty("User-Agent", "ArcartXSuite-AccountType");
        try {
            int code = conn.getResponseCode();
            if (code == 200) {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return extractJsonString(response.toString(), "id");
                }
            }
            if (code == 204 || code == 404) {
                // Mojang 明确返回「无此玩家」
                return "";
            }
            throw new IOException("非预期 HTTP 状态: " + code);
        } finally {
            conn.disconnect();
        }
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(pattern);
        if (keyIndex < 0) {
            return "";
        }
        int colonIndex = json.indexOf(':', keyIndex + pattern.length());
        if (colonIndex < 0) {
            return "";
        }
        int startQuote = json.indexOf('"', colonIndex + 1);
        if (startQuote < 0) {
            return "";
        }
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) {
            return "";
        }
        return json.substring(startQuote + 1, endQuote).trim();
    }

    private static boolean detectAuthlibInjector() {
        // 优先检查 JVM 启动参数：-javaagent:...authlib-injector... 是 authlib-injector
        // 实际注入 JVM 的方式，比 Class.forName 更可靠（避免被其他插件 shade 的库误报）。
        try {
            for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (arg.contains("-javaagent") && arg.toLowerCase(Locale.ROOT).contains("authlib-injector")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        // fallback: 类路径中存在 authlib-injector 代理类（某些自定义加载方式）
        try {
            Class.forName(AUTHLIB_AGENT_CLASS);
            return true;
        } catch (ClassNotFoundException ignored) {
            // ignore
        }
        return false;
    }
}
