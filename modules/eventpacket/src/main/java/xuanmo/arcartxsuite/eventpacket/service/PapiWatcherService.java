package xuanmo.arcartxsuite.eventpacket.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.bridge.ArcartXPacketBridge;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketContext;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketRule;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketTrigger;
import xuanmo.arcartxsuite.eventpacket.config.PluginConfiguration;
import xuanmo.arcartxsuite.eventpacket.storage.EventPacketRepository;

public final class PapiWatcherService {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    private final JavaPlugin plugin;
    private final EventPacketDispatchService dispatchService;
    private final PluginConfiguration configuration;
    private final ArcartXPacketBridge packetBridge;
    private final EventPacketRepository repository;
    private final PlaceholderResolverAPI placeholderResolver;
    private final Map<String, Map<UUID, String>> lastValues = new HashMap<>();
    private final Map<String, Integer> mobKillCounts = new ConcurrentHashMap<>();

    private BukkitTask task;

    public PapiWatcherService(
        JavaPlugin plugin,
        EventPacketDispatchService dispatchService,
        PluginConfiguration configuration,
        ArcartXPacketBridge packetBridge,
        EventPacketRepository repository,
        PlaceholderResolverAPI placeholderResolver
    ) {
        this.plugin = plugin;
        this.dispatchService = dispatchService;
        this.configuration = configuration;
        this.packetBridge = packetBridge;
        this.repository = repository;
        this.placeholderResolver = placeholderResolver;
    }

    public void start() {
        shutdown();
        task = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::tick,
            configuration.refreshIntervalTicks(),
            configuration.refreshIntervalTicks()
        );
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        lastValues.clear();
        mobKillCounts.clear();
    }

    public boolean isRunning() {
        return task != null;
    }

    public void clearPlayer(UUID playerId) {
        for (Map<UUID, String> state : lastValues.values()) {
            state.remove(playerId);
        }
    }

    public void recordMobKill(Player player, String worldName, String entityType, String mythicMobId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        boolean changed = false;
        for (EventPacketRule rule : configuration.rules()) {
            if (!rule.enabled() || rule.trigger() != EventPacketTrigger.MOB_KILL_COUNT) {
                continue;
            }
            if (!matchesRuleFilters(rule, worldName, entityType, mythicMobId)) {
                continue;
            }
            String cacheKey = player.getUniqueId() + "|" + rule.id();
            int cachedCount = mobKillCounts.getOrDefault(cacheKey, -1);
            if (cachedCount < 0) {
                try {
                    cachedCount = repository.getKillCount(player.getUniqueId(), rule.id());
                } catch (SQLException exception) {
                    plugin.getLogger().warning("EventPacket 读取击杀进度失败: " + exception.getMessage());
                    cachedCount = 0;
                }
            }
            int newCount = cachedCount + 1;
            mobKillCounts.put(cacheKey, newCount);

            try {
                repository.setKillCount(player.getUniqueId(), rule.id(), newCount);
            } catch (SQLException exception) {
                plugin.getLogger().warning("EventPacket 保存击杀进度失败: " + exception.getMessage());
            }

            if (newCount < rule.requiredCount()) {
                continue;
            }

            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("rule_id", rule.id());
            variables.put("kill_count", String.valueOf(newCount));
            variables.put("required_count", String.valueOf(rule.requiredCount()));
            variables.put("mob_world", nullToEmpty(worldName));
            variables.put("mob_entity_type", nullToEmpty(entityType));
            variables.put("mythic_mob_id", nullToEmpty(mythicMobId));
            dispatchService.dispatchRule(
                rule,
                EventPacketTrigger.MOB_KILL_COUNT,
                player,
                EventPacketContext.fromVariables(EventPacketTrigger.MOB_KILL_COUNT, player, variables)
            );
            if (rule.repeatable()) {
                mobKillCounts.put(cacheKey, 0);
                try {
                    repository.setKillCount(player.getUniqueId(), rule.id(), 0);
                } catch (SQLException exception) {
                    plugin.getLogger().warning("EventPacket 重置击杀进度失败: " + exception.getMessage());
                }
            }
        }
    }

    private void tick() {
        if (configuration.rules().isEmpty()) {
            return;
        }
        boolean papiAvailable = org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        Set<UUID> onlinePlayerIds = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlinePlayerIds.add(player.getUniqueId());
        }

        for (EventPacketRule rule : configuration.rules()) {
            if (!rule.enabled() || !rule.papiTrigger() || rule.placeholder().isBlank()) {
                continue;
            }
            if (!papiAvailable) {
                continue;
            }

            Map<UUID, String> state = lastValues.computeIfAbsent(rule.id(), ignored -> new HashMap<>());
            state.keySet().removeIf(uuid -> !onlinePlayerIds.contains(uuid));

            for (Player player : Bukkit.getOnlinePlayers()) {
                String currentValue = resolvePlaceholder(player, rule.placeholder());
                UUID playerId = player.getUniqueId();
                String previousValue = state.put(playerId, currentValue);
                if (previousValue == null || Objects.equals(previousValue, currentValue)) {
                    continue;
                }
                if (rule.requireNonEmpty() && (currentValue == null || currentValue.isBlank())) {
                    continue;
                }

                BigDecimal oldNumber = parseNumericValue(previousValue);
                BigDecimal newNumber = parseNumericValue(currentValue);
                if (oldNumber == null || newNumber == null) {
                    if (configuration.debug()) {
                        plugin.getLogger().info(
                            "EventPacket 规则[" + rule.id() + "] 的 placeholder 无法解析为数字: old="
                                + previousValue
                                + " | new="
                                + currentValue
                        );
                    }
                    continue;
                }

                int compareResult = newNumber.compareTo(oldNumber);
                boolean shouldDispatch = switch (rule.trigger()) {
                    case PAPI_INCREASE -> compareResult > 0;
                    case PAPI_DECREASE -> compareResult < 0;
                    case PAPI_THRESHOLD -> rule.threshold() != null
                        && oldNumber.compareTo(rule.threshold()) < 0
                        && newNumber.compareTo(rule.threshold()) >= 0;
                    default -> false;
                };
                if (shouldDispatch) {
                    dispatchService.dispatchRule(
                        rule,
                        rule.trigger(),
                        player,
                        EventPacketContext.fromPapiChange(
                            rule.trigger(),
                            player,
                            rule.placeholder(),
                            previousValue,
                            currentValue,
                            oldNumber,
                            newNumber
                        )
                    );
                }
            }
        }
    }

    private boolean matchesRuleFilters(EventPacketRule rule, String worldName, String entityType, String mythicMobId) {
        if (!rule.worlds().isEmpty() && !rule.worlds().contains(nullToEmpty(worldName).toLowerCase())) {
            return false;
        }
        if (!rule.entityTypes().isEmpty() && !rule.entityTypes().contains(normalizeEntityType(entityType))) {
            return false;
        }
        return rule.mythicMobIds().isEmpty() || rule.mythicMobIds().contains(nullToEmpty(mythicMobId).toLowerCase());
    }


    private static BigDecimal parseNumericValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim().replace(",", "");
        Matcher matcher = NUMBER_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }


    private String resolvePlaceholder(Player player, String placeholder) {
        if (placeholderResolver == null) {
            return placeholder;
        }
        String result = placeholderResolver.applyPlaceholders(player, placeholder);
        return result == null ? "" : result;
    }

    private static String normalizeEntityType(String value) {
        return nullToEmpty(value).trim().replace('-', '_').replace(' ', '_').toLowerCase();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
