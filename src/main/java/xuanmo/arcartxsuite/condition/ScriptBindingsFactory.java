package xuanmo.arcartxsuite.condition;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;

/**
 * 脚本绑定工厂（包私有），为 Aria/JS 脚本构造变量绑定。
 * <p>
 * 默认注入 {@code player}（封装为 {@link AriaPlayer}），
 * 可选注入 {@code Bukkit} 类供脚本访问服务端 API。
 */
final class ScriptBindingsFactory {

    private final PlaceholderResolverAPI placeholderResolver;

    ScriptBindingsFactory(PlaceholderResolverAPI placeholderResolver) {
        this.placeholderResolver = placeholderResolver;
    }

    @NotNull
    Map<String, Object> create(Player player, boolean includeBukkit) {
        Map<String, Object> bindings = new HashMap<>();
        bindings.put(
            "player",
            new AriaPlayer(player, placeholderResolver)
        );
        if (includeBukkit) {
            bindings.put("Bukkit", Bukkit.class);
        }
        return bindings;
    }
}
