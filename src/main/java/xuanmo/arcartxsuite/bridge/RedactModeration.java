package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.bridge.ModerationRules.Span;

/** Optional binding to Redact's public Bukkit service. */
final class RedactModeration {
    private final ServicesManager services;
    private final Class<?> serviceType;
    private final Method checkLocal;
    private final Method violation;
    private final Method truncated;
    private final Method hits;
    private final Method start;
    private final Method end;
    private final Logger logger;
    private final AtomicBoolean unavailableLogged = new AtomicBoolean();

    static RedactModeration connect(JavaPlugin host, Logger logger) {
        Plugin plugin = host.getServer().getPluginManager().getPlugin("Redact");
        if (plugin == null || !plugin.isEnabled()) {
            logger.warning("moderation.redact 已开启，但 Redact 未加载，继续使用 Suite 词库和正则。");
            return null;
        }
        try {
            return new RedactModeration(host.getServer().getServicesManager(), plugin.getClass().getClassLoader(), logger);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logger.warning("无法绑定 Redact 本地审核 API，继续使用 Suite 词库和正则：" + e);
            return null;
        }
    }

    private RedactModeration(ServicesManager services, ClassLoader loader, Logger logger)
        throws ReflectiveOperationException {
        this.services = services;
        this.logger = logger;
        serviceType = Class.forName("priv.seventeen.artist.redact.api.RedactService", false, loader);
        checkLocal = serviceType.getMethod("checkLocal", String.class);
        Class<?> resultType = checkLocal.getReturnType();
        violation = resultType.getMethod("isViolation");
        truncated = resultType.getMethod("isTruncated");
        hits = resultType.getMethod("getHits");
        Class<?> hitType = Class.forName("priv.seventeen.artist.redact.api.ModerationHit", false, loader);
        start = hitType.getMethod("getStartInclusive");
        end = hitType.getMethod("getEndExclusive");
    }

    List<Span> find(String text) {
        try {
            Object service = services.load(serviceType);
            if (service == null) {
                logUnavailable("服务尚未注册");
                return List.of();
            }
            List<Span> result = matchedSpans(checkLocal.invoke(service, text), text.length());
            unavailableLogged.set(false);
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logUnavailable(e.toString());
            return List.of();
        }
    }

    private List<Span> matchedSpans(Object result, int length) throws ReflectiveOperationException {
        if (!(boolean) violation.invoke(result)) return List.of();
        List<?> reported = (List<?>) hits.invoke(result);
        // When only part of the result is reported, cover the original text so no hit is left visible.
        if ((boolean) truncated.invoke(result) || reported.isEmpty()) return List.of(new Span(0, length));
        List<Span> spans = new ArrayList<>(reported.size());
        for (Object hit : reported) {
            int from = (int) start.invoke(hit);
            int to = (int) end.invoke(hit);
            if (from < 0 || to <= from || to > length) return List.of(new Span(0, length));
            spans.add(new Span(from, to));
        }
        return spans;
    }

    private void logUnavailable(String detail) {
        if (unavailableLogged.compareAndSet(false, true)) {
            logger.warning("Redact 本地审核暂不可用，继续使用 Suite 词库和正则：" + detail);
        }
    }
}
