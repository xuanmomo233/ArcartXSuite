package xuanmo.arcartxsuite.tab;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import net.luckperms.api.LuckPermsProvider;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;

/** Captures rule inputs on the player's backend, then applies one set of rules to each visible list. */
final class TabPlayerList {
    static final int MAX_ROWS = 80;
    private static final int MAX_SORT_BYTES = 128;
    private static final long SNAPSHOT_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final Comparator<Row> BY_NAME = Comparator.comparing(
        (Row row) -> row.player().name(), String.CASE_INSENSITIVE_ORDER).thenComparing(row -> row.player().uuid());

    record Details(String inputsKey, List<Long> permissionMask, String sortValue, String displayName) {
        Details(String inputsKey, List<Long> permissionMask, String sortValue) {
            this(inputsKey, permissionMask, sortValue, null);
        }
    }
    record RemotePlayer(UUID uuid, String name, int latency, Details details) {}
    private record PlayerRow(UUID uuid, String name, Player local, int remoteLatency) {
        String displayName() {
            String displayName = local == null ? null : local.getPlayerListName();
            return displayName == null ? name : displayName;
        }

        boolean spectator() {
            return local != null && local.getGameMode() == GameMode.SPECTATOR;
        }

        int latency() {
            return local == null ? remoteLatency : local.getPing();
        }
    }
    private record Row(PlayerRow player, String groupId, boolean pinned, String text, Double number, String formattedName) {}
    private record LocalDetails(Player player, Details details) {}
    private record Snapshot(long createdAt, Map<UUID, LocalDetails> players) {}

    private final TabListSettings settings;
    private final PlaceholderResolverAPI placeholders;
    private final TabDisplayFormat displayFormat;
    private final List<String> permissionInputs;
    private final Map<String, Integer> permissionIndices = new HashMap<>();
    private final String inputsKey;
    private final Comparator<Row> order;
    private Snapshot snapshot;

    TabPlayerList(TabListSettings settings, PlaceholderResolverAPI placeholders) {
        this(settings, placeholders, null);
    }

    TabPlayerList(TabListSettings settings, PlaceholderResolverAPI placeholders, TabDisplayFormat displayFormat) {
        this.settings = settings;
        this.placeholders = placeholders;
        this.displayFormat = displayFormat;
        permissionInputs = settings.permissionInputs();
        for (int i = 0; i < permissionInputs.size(); i++) permissionIndices.put(permissionInputs.get(i), i);
        inputsKey = settings.inputsKey(permissionInputs);
        Comparator<Double> numberOrder = settings.descending() ? Comparator.reverseOrder() : Comparator.naturalOrder();
        Comparator<String> textOrder = settings.descending() ? String.CASE_INSENSITIVE_ORDER.reversed() : String.CASE_INSENSITIVE_ORDER;
        Comparator<Row> sort = settings.numeric()
            ? Comparator.comparing(Row::number, Comparator.nullsLast(numberOrder))
            : Comparator.comparing(Row::text, Comparator.nullsLast(textOrder));
        order = Comparator.comparing(Row::pinned).reversed().thenComparing(sort).thenComparing(BY_NAME);
    }

    List<Map<String, Object>> rowsFor(Player viewer, Collection<? extends Player> onlinePlayers,
                                     List<RemotePlayer> remotePlayers, long now) {
        List<Row> visible = new ArrayList<>();
        addLocalRows(visible, onlinePlayers, viewer::canSee, now);
        if (!remotePlayers.isEmpty()) {
            var localIds = onlinePlayers.stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
            for (var remote : remotePlayers) {
                if (!localIds.contains(remote.uuid())) {
                    add(visible, new PlayerRow(remote.uuid(), remote.name(), null, remote.latency()), remote.details());
                }
            }
        }
        visible.sort(order);

        List<Map<String, Object>> rows = new ArrayList<>();
        if (settings.groups().isEmpty()) {
            append(rows, visible, "", "");
            return rows;
        }
        append(rows, visible.stream().filter(Row::pinned).toList(), "header:pinned", "置顶");
        for (var group : settings.groups()) {
            append(rows, visible.stream().filter(row -> !row.pinned() && group.id().equals(row.groupId())).toList(),
                "header:group:" + group.id(), group.label());
        }
        append(rows, visible.stream().filter(row -> !row.pinned() && row.groupId() == null).toList(),
            "header:ungrouped", settings.ungroupedLabel());
        return rows;
    }

    List<Player> orderedLocalPlayers(Collection<? extends Player> onlinePlayers, long now) {
        List<Row> ordered = new ArrayList<>();
        addLocalRows(ordered, onlinePlayers, player -> true, now);
        ordered.sort(order);
        return ordered.stream().map(row -> row.player().local()).toList();
    }

    static boolean admissionAllowsNativeList(Player viewer, Collection<? extends Player> onlinePlayers) {
        // The original list can still contain a visible player whom LoginView has not admitted.
        return onlinePlayers.stream().noneMatch(player -> player.isOnline() && !eligible(player) && viewer.canSee(player));
    }

    private void addLocalRows(List<Row> rows, Collection<? extends Player> onlinePlayers, Predicate<Player> visible, long now) {
        Map<UUID, LocalDetails> localDetails = snapshot(onlinePlayers, now);
        for (Player player : onlinePlayers) {
            if (!player.isOnline() || !eligible(player) || !visible.test(player)) continue;
            var local = localDetails.get(player.getUniqueId());
            if (local != null && local.player() == player) {
                add(rows, new PlayerRow(player.getUniqueId(), player.getName(), player, 0), local.details());
            }
        }
    }

    Details detailsFor(Player player, Collection<? extends Player> onlinePlayers, long now) {
        var local = snapshot(onlinePlayers, now).get(player.getUniqueId());
        return local != null && local.player() == player ? local.details() : null;
    }

    int permissionInputCount() {
        return permissionInputs.size();
    }

    private Map<UUID, LocalDetails> snapshot(Collection<? extends Player> players, long now) {
        if (snapshot != null && now - snapshot.createdAt() < SNAPSHOT_NANOS) return snapshot.players();
        Map<UUID, LocalDetails> details = new HashMap<>();
        Map<Player, String> names = displayFormat == null ? Map.of() : displayFormat.namesFor(players, now);
        boolean resolveSort = !settings.sortPlaceholder().isEmpty() && placeholders != null && placeholders.available();
        for (Player player : players) {
            if (!player.isOnline() || !eligible(player)) continue;
            BitSet mask = new BitSet(permissionInputs.size());
            String primary = permissionInputs.isEmpty() ? null : primaryGroup(player);
            for (int i = 0; i < permissionInputs.size(); i++) {
                String group = permissionInputs.get(i);
                String permission = "group." + group;
                if (group.equals(primary) || (player.isPermissionSet(permission) && player.hasPermission(permission))) mask.set(i);
            }
            details.put(player.getUniqueId(), new LocalDetails(player,
                new Details(inputsKey, LongStream.of(mask.toLongArray()).boxed().toList(),
                    resolveSort ? sortValue(player) : null, names.get(player))));
        }
        snapshot = new Snapshot(now, details);
        return details;
    }

    private void add(List<Row> rows, PlayerRow player, Details details) {
        if (details == null || !inputsKey.equals(details.inputsKey())) return;
        BitSet mask = BitSet.valueOf(details.permissionMask().stream().mapToLong(Long::longValue).toArray());
        if ((!settings.includeGroups().isEmpty() && !matches(mask, settings.includeGroups()))
                || matches(mask, settings.excludeGroups())) return;

        String groupId = null;
        for (var group : settings.groups()) {
            if (matches(mask, group.permissionGroups())) {
                groupId = group.id();
                break;
            }
        }
        boolean pinned = settings.pinnedNames().contains(player.name().toLowerCase(Locale.ROOT))
            || matches(mask, settings.pinnedGroups());
        String text = details.sortValue();
        rows.add(new Row(player, groupId, pinned, text, settings.numeric() ? parseNumber(text) : null, details.displayName()));
    }

    static boolean eligible(Player player) {
        var stage = AuthAccess.snapshot(player.getUniqueId()).stage();
        return stage == AuthSnapshot.Stage.ADMITTED || stage == AuthSnapshot.Stage.ABSENT;
    }

    private boolean matches(BitSet mask, List<String> groups) {
        for (String group : groups) {
            if (mask.get(permissionIndices.get(group))) return true;
        }
        return false;
    }

    private static String primaryGroup(Player player) {
        try {
            var provider = Bukkit.getServicesManager().load(Permission.class);
            if (provider != null) {
                String group = provider.getPrimaryGroup(player);
                if (group != null && !group.isBlank()) return group.strip().toLowerCase(Locale.ROOT);
            }
        } catch (RuntimeException | LinkageError unavailable) {
            // Vault is optional.
        }
        try {
            var user = LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                String group = user.getPrimaryGroup();
                if (group != null && !group.isBlank()) return group.strip().toLowerCase(Locale.ROOT);
            }
        } catch (RuntimeException | LinkageError unavailable) {
            // Explicit Bukkit group permissions work without LuckPerms.
        }
        return null;
    }

    private String sortValue(Player player) {
        String value = ChatColor.stripColor(placeholders.applyPlaceholders(player, settings.sortPlaceholder()));
        if (value == null || value.isBlank() || value.equals(settings.sortPlaceholder())) return null;
        value = value.strip();
        return value.getBytes(StandardCharsets.UTF_8).length <= MAX_SORT_BYTES ? value : null;
    }

    private static Double parseNumber(String text) {
        if (text == null) return null;
        try {
            double value = Double.parseDouble(text.replace(",", ""));
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException failure) {
            return null;
        }
    }

    private static void append(List<Map<String, Object>> rows, List<Row> entries, String key, String label) {
        if (entries.isEmpty() || rows.size() >= MAX_ROWS) return;
        if (!label.isBlank()) {
            if (rows.size() + 1 == MAX_ROWS) return;
            rows.add(Map.of("uuid", key, "displayName", label, "header", true));
        }
        for (Row entry : entries) {
            if (rows.size() >= MAX_ROWS) break;
            var player = entry.player();
            String displayName = entry.formattedName() == null ? player.displayName() : entry.formattedName();
            rows.add(Map.of("uuid", player.uuid().toString(), "name", player.name(), "displayName", displayName,
                "spectator", player.spectator(), "latency", player.latency(), "header", false));
        }
    }
}
