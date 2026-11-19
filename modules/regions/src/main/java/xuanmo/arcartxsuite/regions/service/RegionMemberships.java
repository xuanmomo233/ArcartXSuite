package xuanmo.arcartxsuite.regions.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.regions.model.Region;

/** 区域快照的反向成员索引。玩家权限仍由 Bukkit 按当前权限组判定。 */
public final class RegionMemberships {
    private final Map<UUID, Map<Region, Boolean>> players = new HashMap<>();
    private final Map<String, Map<Region, Boolean>> groups = new HashMap<>();

    RegionMemberships(List<Region> regions) {
        for (Region region : regions) {
            region.members().forEach(id -> add(players, id, region, false));
            region.owners().forEach(id -> add(players, id, region, true));
            region.memberGroups().forEach(group -> add(groups, group, region, false));
            region.ownerGroups().forEach(group -> add(groups, group, region, true));
        }
    }

    public View view(Player player, View previous) {
        var currentGroups = new HashSet<String>();
        for (String group : groups.keySet()) {
            if (player.hasPermission("axs.regions.group." + group)) currentGroups.add(group);
        }
        if (previous != null && previous.index() == this && currentGroups.equals(previous.groups())) return previous;
        Map<Region, Boolean> matches = new HashMap<>(players.getOrDefault(player.getUniqueId(), Map.of()));
        for (String group : currentGroups) {
            groups.get(group).forEach((region, owner) -> matches.merge(region, owner, (left, right) -> left || right));
        }
        var memberships = matches.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(Region::key)))
            .map(entry -> new Membership(entry.getKey(), entry.getValue())).toList();
        return new View(this, Set.copyOf(currentGroups), memberships);
    }

    private static <K> void add(Map<K, Map<Region, Boolean>> index, K key, Region region, boolean owner) {
        index.computeIfAbsent(key, unused -> new LinkedHashMap<>()).put(region, owner);
    }

    public record View(RegionMemberships index, Set<String> groups, List<Membership> memberships) {}

    public record Membership(Region region, boolean owner) {}
}
