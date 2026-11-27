package xuanmo.arcartxsuite.tab;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;

/** Computes one formatted name per online participant for both original list and HUD. */
final class TabDisplayFormat {
    static final int MAX_LENGTH = 512;
    private static final long SNAPSHOT_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final Pattern PLACEHOLDER = Pattern.compile("%[A-Za-z0-9.-]+_[^%\\s<>]+%");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}+");

    private final String format;
    private final PlaceholderResolverAPI placeholders;
    private long sampledAt;
    private Map<Player, String> sampledNames;

    TabDisplayFormat(String format, PlaceholderResolverAPI placeholders) {
        this.format = format;
        this.placeholders = placeholders;
    }

    Map<Player, String> namesFor(Collection<? extends Player> players, long now) {
        if (sampledNames != null && now - sampledAt < SNAPSHOT_NANOS) return sampledNames;
        boolean usePlaceholders = placeholders != null && placeholders.available();
        var names = new HashMap<Player, String>();
        for (Player player : players) {
            if (player.isOnline() && TabPlayerList.eligible(player)) {
                names.put(player, name(player, usePlaceholders));
            }
        }
        sampledAt = now;
        sampledNames = Map.copyOf(names);
        return sampledNames;
    }

    private String name(Player player, boolean usePlaceholders) {
        String accountName = player.getName();
        String template = format.replace("%player_name%", accountName);
        String value = usePlaceholders ? placeholders.applyPlaceholders(player, template) : template;
        if (value == null || value.length() > MAX_LENGTH) return accountName;
        // A missing expansion should not leave its raw token visible. ArcartX icon syntax is preserved.
        value = PLACEHOLDER.matcher(value).replaceAll("");
        value = ChatColor.translateAlternateColorCodes('&', CONTROL.matcher(value).replaceAll(" ").strip());
        return ChatColor.stripColor(value).isBlank() ? accountName : value;
    }
}
