package xuanmo.arcartxsuite.condition;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;

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
