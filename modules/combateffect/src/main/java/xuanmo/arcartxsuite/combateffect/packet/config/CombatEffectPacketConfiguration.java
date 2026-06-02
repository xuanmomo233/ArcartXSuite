package xuanmo.arcartxsuite.combateffect.packet.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

public final class CombatEffectPacketConfiguration {

    private final List<PacketDefinition> packetDefinitions;
    private final boolean entityCombatEnabled;
    private final boolean includePlayers;
    private final boolean includeNonPlayerLiving;
    private final Set<String> blacklistedMythicMobIds;
    private final Set<EntityType> blacklistedEntityTypes;
    private final boolean debug;

    private CombatEffectPacketConfiguration(
        List<PacketDefinition> packetDefinitions,
        boolean entityCombatEnabled,
        boolean includePlayers,
        boolean includeNonPlayerLiving,
        Set<String> blacklistedMythicMobIds,
        Set<EntityType> blacklistedEntityTypes,
        boolean debug
    ) {
        this.packetDefinitions = List.copyOf(packetDefinitions);
        this.entityCombatEnabled = entityCombatEnabled;
        this.includePlayers = includePlayers;
        this.includeNonPlayerLiving = includeNonPlayerLiving;
        this.blacklistedMythicMobIds = Set.copyOf(blacklistedMythicMobIds);
        this.blacklistedEntityTypes = Set.copyOf(blacklistedEntityTypes);
        this.debug = debug;
    }

    public static CombatEffectPacketConfiguration load(ConfigurationSection section, Logger logger) {
        return load(section, logger, null);
    }

    public static CombatEffectPacketConfiguration load(ConfigurationSection section, Logger logger, File packetsDirectory) {
        if (section == null) {
            return new CombatEffectPacketConfiguration(
                List.of(), true, true, true, Set.of(), Set.of(), false
            );
        }

        boolean entityCombatEnabled = section.getBoolean("settings.entity-combat.enabled", true);
        boolean includePlayers = section.getBoolean("settings.entity-combat.include-players", true);
        boolean includeNonPlayerLiving = section.getBoolean("settings.entity-combat.include-non-player-living", true);
        boolean debug = section.getBoolean("settings.debug", false);

        Set<String> blacklistedMm = new HashSet<>();
        for (String id : section.getStringList("settings.blacklist.mythic-mob-ids")) {
            if (id != null && !id.isBlank()) {
                blacklistedMm.add(id.trim());
            }
        }
        Set<EntityType> blacklistedEntities = new HashSet<>();
        for (String name : section.getStringList("settings.blacklist.entity-types")) {
            try {
                blacklistedEntities.add(EntityType.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                if (logger != null) {
                    logger.warning("CombatEffect 未知 EntityType 黑名单: " + name);
                }
            }
        }

        Map<String, PacketDefinition> defMap = new LinkedHashMap<>();
        if (packetsDirectory != null && packetsDirectory.isDirectory()) {
            loadPacketsFromDirectory(packetsDirectory, defMap);
        }
        List<PacketDefinition> definitions = new ArrayList<>(defMap.values());

        return new CombatEffectPacketConfiguration(
            definitions, entityCombatEnabled, includePlayers, includeNonPlayerLiving,
            blacklistedMm, blacklistedEntities, debug
        );
    }

    public List<PacketDefinition> packetDefinitions() { return packetDefinitions; }
    public boolean entityCombatEnabled() { return entityCombatEnabled; }
    public boolean includePlayers() { return includePlayers; }
    public boolean includeNonPlayerLiving() { return includeNonPlayerLiving; }
    public Set<String> blacklistedMythicMobIds() { return blacklistedMythicMobIds; }
    public Set<EntityType> blacklistedEntityTypes() { return blacklistedEntityTypes; }
    public boolean debug() { return debug; }

    public int enabledPacketCount() {
        return (int) packetDefinitions.stream().filter(PacketDefinition::enabled).count();
    }

    public PacketDefinition findPacketById(String packetId) {
        if (packetId == null) return null;
        for (PacketDefinition definition : packetDefinitions) {
            if (packetId.equals(definition.id())) {
                return definition;
            }
        }
        return null;
    }

    private static void loadPacketsFromDirectory(
        File directory,
        Map<String, PacketDefinition> target
    ) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String key : yaml.getKeys(false)) {
                ConfigurationSection def = yaml.getConfigurationSection(key);
                if (def == null) continue;
                PacketDefinition pd = parsePacketDefinition(key, def);
                if (pd != null) target.put(key, pd);
            }
        }
    }

    private static PacketDefinition parsePacketDefinition(String key, ConfigurationSection def) {
        boolean enabled = def.getBoolean("enabled", true);
        List<PacketTrigger> triggers = new ArrayList<>();
        // 支持 trigger (单值) 和 triggers (列表) 两种写法
        String singleTrigger = def.getString("trigger");
        if (singleTrigger != null && !singleTrigger.isBlank()) {
            PacketTrigger pt = PacketTrigger.fromConfig(singleTrigger);
            if (pt != null) triggers.add(pt);
        }
        for (String t : def.getStringList("triggers")) {
            PacketTrigger pt = PacketTrigger.fromConfig(t);
            if (pt != null && !triggers.contains(pt)) triggers.add(pt);
        }
        List<PacketRecipient> recipients = new ArrayList<>();
        for (String r : def.getStringList("recipients")) {
            PacketRecipient pr = PacketRecipient.fromConfig(r);
            if (pr != null) recipients.add(pr);
        }
        if (recipients.isEmpty()) recipients.add(PacketRecipient.ATTACKER);
        String uiId = def.getString("ui-id", "");
        String handler = def.getString("packet-handler", "");
        Map<String, Object> template = new LinkedHashMap<>();
        Object packValue = def.get("pack");
        if (packValue instanceof String s) {
            template.put("pack", s);
        } else if (packValue instanceof List<?> list) {
            template.put("pack", list);
        } else {
            ConfigurationSection packSection = def.getConfigurationSection("pack");
            if (packSection != null) {
                for (String pk : packSection.getKeys(false)) {
                    template.put(pk, packSection.get(pk));
                }
            }
        }
        // conditions 节（combo / keybind / state / controller 共用）
        ConfigurationSection conditions = def.getConfigurationSection("conditions");
        int comboMin = 0;
        int comboMax = Integer.MAX_VALUE;
        boolean comboRepeat = false;
        String keyName = null;
        String keyAction = "press";
        String keyType = "client";
        String stateId = null;
        String stateAction = "enter";
        String controllerId = null;
        if (conditions != null) {
            comboMin = conditions.getInt("combo-min", 0);
            comboMax = conditions.getInt("combo-max", Integer.MAX_VALUE);
            comboRepeat = conditions.getBoolean("combo-repeat", false);
            keyName = conditions.getString("key-name");
            keyAction = conditions.getString("key-action", "press");
            keyType = conditions.getString("key-type", "client");
            stateId = conditions.getString("state-id");
            stateAction = conditions.getString("state-action", "enter");
            controllerId = conditions.getString("controller-id");
        }

        // 冷却时间（毫秒），0 表示无冷却
        long cooldownMs = Math.max(0L, def.getLong("cooldown", 0L));

        return new PacketDefinition(key, enabled, triggers, recipients, uiId, handler, template,
            comboMin, comboMax, comboRepeat, cooldownMs,
            keyName, keyAction, keyType,
            stateId, stateAction, controllerId);
    }

    public boolean shouldProcessTarget(boolean isPlayer, String mythicMobId, EntityType entityType) {
        if (!entityCombatEnabled) return false;
        if (isPlayer && !includePlayers) return false;
        if (!isPlayer && !includeNonPlayerLiving) return false;
        if (mythicMobId != null && !mythicMobId.isBlank() && blacklistedMythicMobIds.contains(mythicMobId)) return false;
        if (entityType != null && blacklistedEntityTypes.contains(entityType)) return false;
        return true;
    }
}
