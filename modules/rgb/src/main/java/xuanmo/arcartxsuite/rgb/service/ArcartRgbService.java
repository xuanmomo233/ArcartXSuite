package xuanmo.arcartxsuite.rgb.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.OfflinePlayer;
import xuanmo.arcartxsuite.rgb.config.ArcartRgbEntry;
import xuanmo.arcartxsuite.rgb.config.ArcartRgbModuleConfiguration;
import xuanmo.arcartxsuite.rgb.config.ArcartRgbRenderOptions;

public final class ArcartRgbService {

    private static final int MAX_RENDER_TEXT_LENGTH = 1024;

    private final ArcartRgbModuleConfiguration configuration;
    private final Logger logger;
    private final PlaceholderResolver placeholderResolver;
    private final ThreadLocal<Set<String>> activeEntryIds = ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<Boolean> recursiveReferenceDetected = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public ArcartRgbService(ArcartRgbModuleConfiguration configuration, Logger logger) {
        this(configuration, logger, (player, text) -> text);
    }

    public ArcartRgbService(
        ArcartRgbModuleConfiguration configuration,
        Logger logger,
        PlaceholderResolver placeholderResolver
    ) {
        this.configuration = configuration;
        this.logger = logger;
        this.placeholderResolver = placeholderResolver;
    }

    public String render(String entryId) {
        return render(entryId, null);
    }

    public String render(String entryId, OfflinePlayer player) {
        String normalizedEntryId = normalizeEntryId(entryId);
        if (normalizedEntryId.isEmpty()) {
            return "";
        }

        ArcartRgbEntry entry = configuration.entry(entryId);
        if (entry == null || !entry.active()) {
            return "";
        }

        Set<String> activeIds = activeEntryIds.get();
        boolean rootRender = activeIds.isEmpty();
        if (rootRender) {
            recursiveReferenceDetected.set(Boolean.FALSE);
        }
        if (!activeIds.add(normalizedEntryId)) {
            recursiveReferenceDetected.set(Boolean.TRUE);
            if (configuration.debug() && logger != null) {
                logger.warning("ArcartRGB 检测到递归 PAPI 引用，已中止渲染: " + normalizedEntryId);
            }
            return "";
        }

        try {
            String text = resolvePlaceholders(player, entry.text());
            if (text.length() > MAX_RENDER_TEXT_LENGTH) {
                text = text.substring(0, MAX_RENDER_TEXT_LENGTH);
            }
            if (Boolean.TRUE.equals(recursiveReferenceDetected.get())) {
                return "";
            }
            return ArcartRgbRenderer.renderTextAtTime(
                text,
                entry.shineEnabled(),
                entry.gradientColors(),
                System.currentTimeMillis(),
                ArcartRgbRenderOptions.fromEntry(entry)
            );
        } finally {
            activeIds.remove(normalizedEntryId);
            if (rootRender) {
                activeEntryIds.remove();
                recursiveReferenceDetected.remove();
            }
        }
    }

    public int entryCount() {
        return configuration.entries().size();
    }

    public int activeEntryCount() {
        return configuration.enabledEntryCount();
    }

    public List<String> entryIds() {
        return new ArrayList<>(configuration.entries().keySet());
    }

    public void shutdown() {
        // 当前模块为纯计算型服务，无需额外资源释放。
        activeEntryIds.remove();
        recursiveReferenceDetected.remove();
    }

    private String resolvePlaceholders(OfflinePlayer player, String text) {
        if (player == null || text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return placeholderResolver.resolve(player, text);
    }

    private static String normalizeEntryId(String entryId) {
        return entryId == null ? "" : entryId.trim().toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    public interface PlaceholderResolver {
        String resolve(OfflinePlayer player, String text);
    }
}
