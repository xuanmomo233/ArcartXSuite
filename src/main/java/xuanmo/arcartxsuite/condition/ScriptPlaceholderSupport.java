package xuanmo.arcartxsuite.condition;

import java.util.regex.Pattern;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;

final class ScriptPlaceholderSupport {

    private static final Pattern RETURN_PATTERN =
        Pattern.compile("\\breturn\\b");

    private final PlaceholderResolverAPI placeholderResolver;

    ScriptPlaceholderSupport(PlaceholderResolverAPI placeholderResolver) {
        this.placeholderResolver = placeholderResolver;
    }

    @NotNull
    String applyPlaceholders(
        @Nullable Player player,
        @NotNull String input
    ) {
        if (input == null) {
            return "";
        }
        if (player == null) {
            return input;
        }
        String withPlayer = input.replace("{player}", player.getName());
        if (placeholderResolver == null) {
            return withPlayer;
        }
        String result = placeholderResolver.applyPlaceholders(player, withPlayer);
        return result == null ? withPlayer : result;
    }

    @NotNull
    String resolvePlaceholder(
        Player player,
        @Nullable String placeholder
    ) {
        if (placeholder == null) {
            return "";
        }
        if (placeholder.isBlank()) {
            return placeholder;
        }
        if (placeholderResolver == null) {
            return placeholder;
        }
        String result = placeholderResolver.applyPlaceholders(player, placeholder);
        return result == null ? "" : result;
    }

    static String ensureReturn(String script) {
        if (script == null || script.isBlank()) {
            return script;
        }
        if (RETURN_PATTERN.matcher(script).find()) {
            return script;
        }
        return "return " + script;
    }
}
