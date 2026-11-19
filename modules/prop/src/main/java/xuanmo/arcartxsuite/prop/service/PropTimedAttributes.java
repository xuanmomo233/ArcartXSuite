package xuanmo.arcartxsuite.prop.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry;
import xuanmo.arcartxsuite.api.attribute.AttributePlusBridge;
import xuanmo.arcartxsuite.api.attribute.CraneAttributeBridge;
import xuanmo.arcartxsuite.api.attribute.MythicLibBridge;
import xuanmo.arcartxsuite.api.attribute.SymphonyBridge;
import xuanmo.arcartxsuite.api.attribute.SymphonyBridge.ModifierOperation;
import xuanmo.arcartxsuite.api.modattr.ModAttributeModifier;
import xuanmo.arcartxsuite.api.modattr.ModAttributeService;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.script.AriaBridge;
import xuanmo.arcartxsuite.prop.config.PropAmount;
import xuanmo.arcartxsuite.prop.config.PropEffects;
import xuanmo.arcartxsuite.prop.config.PropEffects.AttributeGroup;
import xuanmo.arcartxsuite.prop.config.PropEffects.Provider;
import xuanmo.arcartxsuite.prop.config.PropMythicLibConfiguration;

/** Prop 的属性组可以各自到期。重复使用会刷新同一道具的全部属性组。 */
final class PropTimedAttributes {
    @FunctionalInterface
    interface ExpiryScheduler {
        BukkitTask schedule(Runnable expire, long delayTicks);
    }

    private final AttributePlusBridge attributePlus;
    private final MythicLibBridge mythicLib;
    private final SymphonyBridge symphony;
    private final CraneAttributeBridge craneAttribute;
    private final ModAttributeService modAttributes;
    private final AriaBridge aria;
    private final PlaceholderResolverAPI placeholders;
    private final PropMythicLibConfiguration mythicLibConfiguration;
    private final ExpiryScheduler scheduler;
    private final Logger logger;
    private final Map<UUID, Map<String, ActiveUse>> active = new HashMap<>();

    PropTimedAttributes(AttributeBridgeRegistry bridges, ModAttributeService modAttributes,
                        AriaBridge aria, PlaceholderResolverAPI placeholders,
                        PropMythicLibConfiguration mythicLibConfiguration, ExpiryScheduler scheduler, Logger logger) {
        this.attributePlus = bridges.attributePlus();
        this.mythicLib = bridges.mythicLib();
        this.symphony = bridges.symphony();
        this.craneAttribute = bridges.craneAttribute();
        this.modAttributes = modAttributes;
        this.aria = aria;
        this.placeholders = placeholders;
        this.mythicLibConfiguration = mythicLibConfiguration;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    Map<String, Boolean> providers() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (Provider provider : Provider.values()) result.put(provider.displayName(), available(provider));
        return result;
    }

    String unavailableProvider(PropEffects effects) {
        for (AttributeGroup group : effects.attributes()) {
            if (!available(group.provider())) return group.provider().displayName();
        }
        return null;
    }

    private boolean available(Provider provider) {
        return switch (provider) {
            case ATTRIBUTE_PLUS -> attributePlus.available();
            case CRANE_ATTRIBUTE -> craneAttribute.available();
            case MYTHIC_LIB -> mythicLib.available();
            case SYMPHONY -> symphony.available();
            case MOD -> modAttributes.available();
        };
    }

    String unsupportedAttribute(Player player, PropEffects effects) {
        for (AttributeGroup group : effects.attributes()) {
            for (var modifier : group.modifiers()) {
                boolean supported = switch (group.provider()) {
                    case MOD -> modAttributes.supportsAttribute(player, modifier.id());
                    case MYTHIC_LIB -> mythicLib.isRegisteredStat(modifier.id());
                    default -> true;
                };
                if (!supported) return modifier.id();
            }
        }
        return null;
    }

    List<PreparedGroup> prepare(Player player, PropEffects effects) {
        if (effects.attributes().isEmpty()) return List.of();
        PropAmount.Inputs inputs = new PropAmount.Inputs(player, symphony, placeholders);
        List<PreparedGroup> groups = new ArrayList<>(effects.attributes().size());
        for (AttributeGroup group : effects.attributes()) {
            Map<String, ModAttributeModifier> modifiers = new LinkedHashMap<>();
            for (var modifier : group.modifiers()) {
                double amount = modifier.amount().resolve(inputs, aria);
                modifiers.put(modifier.id(), new ModAttributeModifier(modifier.id(), amount, modifier.operation()));
            }
            groups.add(new PreparedGroup(group, modifiers));
        }
        return groups;
    }

    void apply(Player player, String propId, List<PreparedGroup> groups) {
        if (groups.isEmpty()) return;
        UUID playerId = player.getUniqueId();
        clear(playerId, propId);
        Map<Integer, List<Runnable>> cleanupByDuration = new LinkedHashMap<>();
        for (PreparedGroup prepared : groups) {
            AttributeGroup group = prepared.group();
            String identity = propId + "\0" + group.provider().key() + "\0" + group.id();
            String key = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
            List<Runnable> cleanup = cleanupByDuration.computeIfAbsent(group.durationSeconds(), ignored -> new ArrayList<>());
            applyGroup(player, prepared, key, cleanup);
        }
        cleanupByDuration.values().removeIf(List::isEmpty);
        if (cleanupByDuration.isEmpty()) return;

        var use = new ActiveUse(new HashMap<>());
        active.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(propId, use);
        cleanupByDuration.forEach((duration, cleanup) -> {
            BukkitTask task = scheduler.schedule(() -> expire(playerId, propId, use, duration), duration * 20L);
            use.expiries().put(duration, new Expiry(task, cleanup));
        });
    }

    void clear(Player player) {
        Map<String, ActiveUse> uses = active.remove(player.getUniqueId());
        if (uses != null) uses.values().forEach(this::close);
    }

    void shutdown() {
        active.values().forEach(uses -> uses.values().forEach(this::close));
        active.clear();
    }

    private void clear(UUID playerId, String propId) {
        Map<String, ActiveUse> uses = active.get(playerId);
        if (uses == null) return;
        ActiveUse old = uses.remove(propId);
        if (uses.isEmpty()) active.remove(playerId);
        if (old != null) close(old);
    }

    private void expire(UUID playerId, String propId, ActiveUse use, int duration) {
        Map<String, ActiveUse> uses = active.get(playerId);
        if (uses == null || uses.get(propId) != use) return;
        Expiry current = use.expiries().remove(duration);
        if (use.expiries().isEmpty()) {
            uses.remove(propId);
            if (uses.isEmpty()) active.remove(playerId);
        }
        if (current != null) close(current);
    }

    private void close(ActiveUse use) {
        use.expiries().values().forEach(this::close);
    }

    private void close(Expiry expiry) {
        expiry.task().cancel();
        for (Runnable removeSource : expiry.cleanup()) {
            try {
                removeSource.run();
            } catch (RuntimeException | LinkageError exception) {
                logger.warning("Prop 清理临时属性失败: " + exception);
            }
        }
    }

    private void applyGroup(Player player, PreparedGroup prepared, String key, List<Runnable> cleanup) {
        AttributeGroup group = prepared.group();
        String source = "AXS_PROP_" + key;
        switch (group.provider()) {
            case ATTRIBUTE_PLUS -> applyAttributePlus(player, source, group, cleanup);
            case CRANE_ATTRIBUTE -> applyCraneAttribute(player, source, group, cleanup);
            case MYTHIC_LIB -> applyMythicLib(player, key, prepared, cleanup);
            case SYMPHONY -> applySymphony(player, source, prepared, cleanup);
            case MOD -> {
                modAttributes.setModifiers(player, source, prepared.modifiers());
                cleanup.add(() -> modAttributes.clearSource(player, source));
            }
        }
    }

    private void applyAttributePlus(Player player, String source, AttributeGroup group, List<Runnable> cleanup) {
        Object data = attributePlus.getAttrData(player);
        Object attributes = attributePlus.getAttributeSource(group.lines());
        if (data == null || attributes == null) return;
        attributePlus.addSource(data, source, attributes);
        attributePlus.updateAttribute(player, data);
        cleanup.add(() -> {
            attributePlus.removeSource(data, source);
            attributePlus.updateAttribute(player, data);
        });
    }

    private void applyCraneAttribute(Player player, String source, AttributeGroup group, List<Runnable> cleanup) {
        Object data = craneAttribute.getAttrData(player);
        if (data == null) return;
        if (craneAttribute.supportsStaticSource()) {
            craneAttribute.addStaticSource(data, source, group.lines());
        } else {
            craneAttribute.addSource(data, source, group.lines());
        }
        craneAttribute.updateAttribute(player);
        cleanup.add(() -> {
            craneAttribute.removeSource(data, source);
            craneAttribute.updateAttribute(player);
        });
    }

    private void applyMythicLib(Player player, String key, PreparedGroup prepared, List<Runnable> cleanup) {
        Object data = mythicLib.getPlayerData(player);
        if (data == null) return;
        for (var modifier : prepared.modifiers().values()) {
            String id = modifier.attributeId();
            Object handle = mythicLib.registerTemporaryModifier(data, mythicLibConfiguration.modifierName(key, id),
                id, modifier.amount(), prepared.group().durationSeconds() * 1000L);
            if (handle != null) cleanup.add(() -> mythicLib.closeTemporaryModifier(handle));
        }
    }

    private void applySymphony(Player player, String source, PreparedGroup prepared, List<Runnable> cleanup) {
        List<String> sources = new ArrayList<>();
        for (var modifier : prepared.modifiers().values()) {
            String key = source + "_" + sources.size();
            ModifierOperation operation = switch (modifier.operation()) {
                case ADDITION -> ModifierOperation.ADD;
                case MULTIPLY_BASE -> ModifierOperation.MULTIPLY_BASE;
                case MULTIPLY_TOTAL -> ModifierOperation.MULTIPLY_TOTAL;
            };
            symphony.setAttribute(player, modifier.attributeId(), operation, modifier.amount(), key);
            sources.add(key);
        }
        symphony.recalculate(player);
        cleanup.add(() -> {
            for (String key : sources) symphony.removeAttribute(player, key);
            symphony.recalculate(player);
        });
    }

    record PreparedGroup(AttributeGroup group, Map<String, ModAttributeModifier> modifiers) {}
    private record ActiveUse(Map<Integer, Expiry> expiries) {}
    private record Expiry(BukkitTask task, List<Runnable> cleanup) {}
}
