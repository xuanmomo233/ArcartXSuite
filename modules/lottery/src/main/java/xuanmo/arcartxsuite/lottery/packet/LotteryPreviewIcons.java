package xuanmo.arcartxsuite.lottery.packet;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.lottery.LotteryService;
import xuanmo.arcartxsuite.lottery.model.PoolItem;

/** 主线程复用当前配置内的静态预览 JSON，模块重载时随处理器销毁。 */
final class LotteryPreviewIcons {
    private final IconResolver resolver;
    private final LotteryService service;
    private final Map<IconDefinition, String> icons = new IdentityHashMap<>();
    private final Map<PoolItem, String> items = new IdentityHashMap<>();

    LotteryPreviewIcons(IconResolver resolver, LotteryService service) {
        this.resolver = resolver;
        this.service = service;
    }

    String resolve(Player player, IconDefinition icon) {
        if (icon == null) return "";
        String cached = icons.get(icon);
        if (cached != null) return cached;
        String json = resolver.resolveItemJson(player, icon);
        if (!json.isEmpty() && isStatic(icon)) icons.put(icon, json);
        return json;
    }

    String resolve(Player player, PoolItem item) {
        if (item.icon() != null) return resolve(player, item.icon());
        String cached = items.get(item);
        if (cached != null) return cached;
        String json = resolver.itemToPreviewJson(service.generateItem(item, player));
        if (!json.isEmpty() && vanillaSource(item.source())) items.put(item, json);
        return json;
    }

    private static boolean isStatic(IconDefinition icon) {
        if (hasText(icon.json())) return true;
        if (hasText(icon.source()) && hasText(icon.sourceId())) return vanillaSource(icon.source().trim());
        return !hasText(icon.mmoType()) || !hasText(icon.mmoId());
    }

    private static boolean vanillaSource(String source) {
        return switch (source.toLowerCase(Locale.ROOT)) {
            case "minecraft", "plain", "vanilla" -> true;
            default -> false;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
