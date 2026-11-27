package xuanmo.arcartxsuite.tab;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.configuration.ConfigurationSection;

record TabListSettings(Presentation presentation, List<String> includeGroups, List<String> excludeGroups,
                       String sortPlaceholder, boolean numeric, boolean descending,
                       List<Group> groups, String ungroupedLabel,
                       Set<String> pinnedNames, List<String> pinnedGroups) {

    enum Presentation { AUTO, HUD }

    record Group(String id, String label, List<String> permissionGroups) {}

    static TabListSettings load(ConfigurationSection config) {
        var presentation = switch (config.getString("player-list.presentation", "auto").strip().toLowerCase(Locale.ROOT)) {
            case "auto" -> Presentation.AUTO;
            case "hud" -> Presentation.HUD;
            default -> throw new IllegalArgumentException("player-list.presentation 必须为 auto 或 hud");
        };
        String order = config.getString("player-list.sort.order", "asc").strip().toLowerCase(Locale.ROOT);
        if (!order.equals("asc") && !order.equals("desc")) {
            throw new IllegalArgumentException("player-list.sort.order 必须为 asc 或 desc");
        }

        List<Group> groups = new ArrayList<>();
        ConfigurationSection configured = config.getConfigurationSection("player-list.groups");
        if (config.contains("player-list.groups") && configured == null) {
            throw new IllegalArgumentException("player-list.groups 必须是配置节");
        }
        if (configured != null) {
            for (String id : configured.getKeys(false)) {
                var section = configured.getConfigurationSection(id);
                if (section == null) {
                    throw new IllegalArgumentException("player-list.groups." + id + " 必须是配置节");
                }
                List<String> members = permissionGroups(section, "permission-groups");
                if (members.isEmpty()) {
                    throw new IllegalArgumentException("player-list.groups." + id + ".permission-groups 不能为空");
                }
                groups.add(new Group(id, section.getString("label", id), members));
            }
        }

        return new TabListSettings(presentation, permissionGroups(config, "player-list.include-groups"),
            permissionGroups(config, "player-list.exclude-groups"), config.getString("player-list.sort.placeholder", "").strip(),
            config.getBoolean("player-list.sort.numeric", true), order.equals("desc"), List.copyOf(groups),
            config.getString("player-list.ungrouped-label", "其他玩家"),
            Set.copyOf(config.getStringList("player-list.pinned.names").stream().map(String::strip)
                .filter(name -> !name.isEmpty()).map(name -> name.toLowerCase(Locale.ROOT)).toList()),
            permissionGroups(config, "player-list.pinned.permission-groups"));
    }

    boolean permitsNativeOrder() {
        return presentation == Presentation.AUTO && includeGroups.isEmpty() && excludeGroups.isEmpty() && groups.isEmpty();
    }

    List<String> permissionInputs() {
        var names = new TreeSet<String>();
        names.addAll(includeGroups);
        names.addAll(excludeGroups);
        names.addAll(pinnedGroups);
        groups.forEach(group -> names.addAll(group.permissionGroups()));
        return List.copyOf(names);
    }

    String inputsKey(List<String> permissionInputs) {
        try {
            byte[] fingerprint = MessageDigest.getInstance("SHA-256").digest(
                (String.join(",", permissionInputs) + "\n" + sortPlaceholder).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(fingerprint);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static List<String> permissionGroups(ConfigurationSection config, String path) {
        if (config.contains(path) && !config.isList(path)) {
            throw new IllegalArgumentException(path + " 必须是权限组列表");
        }
        List<String> groups = config.getStringList(path).stream()
            .map(String::strip).map(group -> group.toLowerCase(Locale.ROOT)).distinct().toList();
        for (String group : groups) {
            if (!group.matches("[a-z0-9_.-]{1,64}")) {
                throw new IllegalArgumentException(path + " 包含无效的权限组 " + group);
            }
        }
        return groups;
    }
}
