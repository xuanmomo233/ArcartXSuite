package xuanmo.arcartxsuite.prop.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PropPlayerHandle;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;
import xuanmo.arcartxsuite.prop.config.PropDefinition;
import xuanmo.arcartxsuite.prop.config.PropKeyBindingDefinition;
import xuanmo.arcartxsuite.prop.config.PropKeyMappingConfiguration;
import xuanmo.arcartxsuite.prop.config.PropLanguageConfiguration;
import xuanmo.arcartxsuite.prop.config.PropModuleConfiguration;
import xuanmo.arcartxsuite.module.AxsLog;

public final class PropService implements Listener {

    private final JavaPlugin plugin;
    private final PropModuleConfiguration configuration;
    private final PropBridgeAPI bridge;
    private final PropKeyMappingConfiguration keyMappingConfiguration;
    private final PropLanguageConfiguration languageConfiguration;
    private final Map<String, PropDefinition> definitionsByNormalizedId;
    private final PropAttributePlusService attributePlusService;
    private final PropMythicLibService mythicLibService;
    private final PropSymphonyService symphonyService;
    private Supplier<EventBusCapability> eventBusProvider;
    private final Set<String> registeredBindingIds = new LinkedHashSet<>();

    public PropService(
        JavaPlugin plugin,
        PropModuleConfiguration configuration,
        PropBridgeAPI bridge,
        PropKeyMappingConfiguration keyMappingConfiguration,
        PropLanguageConfiguration languageConfiguration,
        Map<String, PropDefinition> definitionsByNormalizedId,
        xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.bridge = bridge;
        this.keyMappingConfiguration = keyMappingConfiguration;
        this.languageConfiguration = languageConfiguration;
        this.definitionsByNormalizedId = definitionsByNormalizedId;
        this.attributePlusService = new PropAttributePlusService(plugin, attributeBridge.attributePlus());
        this.mythicLibService = new PropMythicLibService(plugin, configuration.mythicLib(), attributeBridge.mythicLib());
        this.symphonyService = new PropSymphonyService(plugin, attributeBridge.symphony());
    }

    public void setEventBusProvider(Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    public void start() {
        mythicLibService.start();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerBindings();
        syncOnlinePlayers();
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        unregisterBindings();
        mythicLibService.shutdown();
        symphonyService.shutdown();
    }

    public int propCount() {
        return definitionsByNormalizedId.size();
    }

    public int registeredKeyCount() {
        return registeredBindingIds.size();
    }

    public String keyCategory() {
        return keyMappingConfiguration.category();
    }

    public boolean attributePlusHooked() {
        return attributePlusService.hooked();
    }

    public boolean mythicLibHooked() {
        return mythicLibService.hooked();
    }

    public boolean symphonyHooked() {
        return symphonyService.hooked();
    }

    public List<String> propIds() {
        List<String> propIds = new ArrayList<>();
        for (PropDefinition definition : definitionsByNormalizedId.values()) {
            propIds.add(definition.id());
        }
        return List.copyOf(propIds);
    }

    public PropOperationResult applyPropToMainHand(Player player, String propId) {
        if (player == null) {
            return PropOperationResult.failure("只有玩家可以使用该命令。");
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!isRealItem(mainHand)) {
            return PropOperationResult.failure("请先手持一个物品。");
        }

        PropDefinition definition = resolveDefinition(propId);
        if (definition == null) {
            return PropOperationResult.failure("道具 ID 不存在: " + propId);
        }

        ItemStack boundItem = bindItem(mainHand, definition.id(), definition.coolDownGroup());
        player.getInventory().setItemInMainHand(boundItem);
        return PropOperationResult.success("已将手中物品设置为道具 " + definition.id() + "。");
    }

    public void handleClientInitialized(Player player) {
        scheduleSlotSync(player, 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        scheduleSlotSync(event.getPlayer(), 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        mythicLibService.clear(player);
        symphonyService.clear(player);
        attributePlusService.clear(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ResolvedBoundProp resolved = resolveBoundProp(mainHand);
        if (resolved == null) {
            return;
        }

        event.setCancelled(true);

        if (resolved.changed()) {
            player.getInventory().setItemInMainHand(resolved.itemStack());
        }

        PropPlayerHandle handle = bridge.resolvePlayerHandle(player).orElse(null);
        if (handle == null) {
            return;
        }

        handleUse(
            player,
            resolved,
            handle.getTagCooldown(resolved.definition().coolDownGroup()),
            PropUseMode.HAND,
            null,
            handle
        );
    }

    private void registerBindings() {
        unregisterBindings();
        for (PropKeyBindingDefinition definition : keyMappingConfiguration.bindings().values()) {
            boolean success = bridge.registerClientKeyBind(
                definition.bindingId(),
                keyMappingConfiguration.category(),
                definition.defaultKey(),
                player -> Bukkit.getScheduler().runTask(plugin, () -> handleKeyPress(player, definition))
            );
            if (success) {
                registeredBindingIds.add(definition.bindingId());
                if (configuration.debug()) {
                    AxsLog.logger().info(
                        "ArcartXProp 注册按键 -> id="
                            + definition.bindingId()
                            + " | key="
                            + definition.defaultKey()
                            + " | slot="
                            + definition.slotId()
                    );
                }
            }
        }
    }

    private void unregisterBindings() {
        for (String bindingId : registeredBindingIds) {
            bridge.unregisterClientKeyBind(bindingId);
        }
        registeredBindingIds.clear();
    }

    private void handleKeyPress(Player player, PropKeyBindingDefinition binding) {
        if (player == null || !player.isOnline()) {
            return;
        }

        PropPlayerHandle handle = bridge.resolvePlayerHandle(player).orElse(null);
        if (handle == null) {
            return;
        }

        ItemStack slotItem = handle.getSlotItemStack(binding.slotId());
        ResolvedBoundProp resolved = resolveBoundProp(slotItem);
        if (resolved == null) {
            return;
        }

        if (resolved.changed()) {
            handle.setSlotItemStack(binding.slotId(), resolved.itemStack());
        }

        handleUse(
            player,
            resolved,
            handle.getTagCooldown(resolved.definition().coolDownGroup()),
            PropUseMode.KEY,
            binding.slotId(),
            handle
        );
    }

    private void handleUse(
        Player player,
        ResolvedBoundProp resolved,
        long cooldown,
        PropUseMode useMode,
        String slotId,
        PropPlayerHandle handle
    ) {
        PropDefinition definition = resolved.definition();
        ItemStack itemStack = resolved.itemStack();
        String itemName = resolveDisplayName(itemStack);

        if (cooldown > 0L) {
            player.sendMessage(render(languageConfiguration.coolDown(), itemName, cooldown));
            if (useMode == PropUseMode.KEY) {
                handle.syncSlotCacheToClient();
            }
            return;
        }
        if (useMode == PropUseMode.HAND && !definition.hand()) {
            player.sendMessage(render(languageConfiguration.noHand(), itemName, 0L));
            return;
        }
        if (useMode == PropUseMode.KEY && !definition.key()) {
            player.sendMessage(render(languageConfiguration.noKey(), itemName, 0L));
            handle.syncSlotCacheToClient();
            return;
        }
        if (!definition.permission().isBlank() && !player.hasPermission(definition.permission())) {
            player.sendMessage(render(languageConfiguration.noPermission(), itemName, 0L));
            if (useMode == PropUseMode.KEY) {
                handle.syncSlotCacheToClient();
            }
            return;
        }
        if (!definition.conditions().isEmpty()) {
            ScriptCondition failedCondition = ScriptConditionServices.evaluator().firstFailed(player, definition.conditions());
            if (failedCondition != null) {
                player.sendMessage(renderCondition(languageConfiguration.conditionNotMet(), itemName, failedCondition.raw()));
                if (useMode == PropUseMode.KEY) {
                    handle.syncSlotCacheToClient();
                }
                return;
            }
        }

        handle.setTagCooldown(definition.coolDownGroup(), definition.coolDownTimeSeconds() * 1000L);

        if (useMode == PropUseMode.KEY) {
            if (definition.remove()) {
                handle.setSlotItemStack(slotId, consumeOne(itemStack));
            } else if (resolved.changed()) {
                handle.setSlotItemStack(slotId, itemStack);
            }
            handle.syncSlotCacheToClient();
        } else if (useMode == PropUseMode.HAND) {
            if (definition.remove()) {
                player.getInventory().setItemInMainHand(consumeOne(itemStack));
            } else if (resolved.changed()) {
                player.getInventory().setItemInMainHand(itemStack);
            }
        }

        executeEffects(player, definition);
        publishPropUsedEvent(player, definition.id());

        if (configuration.debug()) {
            AxsLog.logger().info(
                "ArcartXProp 使用成功 -> player="
                    + player.getName()
                    + " | prop="
                    + definition.id()
                    + " | mode="
                    + useMode.name().toLowerCase(Locale.ROOT)
                    + " | cooldownGroup="
                    + definition.coolDownGroup()
                    + " | slot="
                    + (slotId == null ? "hand" : slotId)
            );
        }
    }

    private ResolvedBoundProp resolveBoundProp(ItemStack itemStack) {
        if (!isRealItem(itemStack)) {
            return null;
        }

        String propId = bridge.getPersistentPropId(itemStack);
        if (propId.isBlank()) {
            return null;
        }

        PropDefinition definition = resolveDefinition(propId);
        if (definition == null) {
            return null;
        }

        ItemStack working = itemStack;
        boolean changed = false;
        String currentCooldownTag = bridge.getCooldownTag(working);
        if (!Objects.equals(currentCooldownTag, definition.coolDownGroup())) {
            bridge.setCooldownTag(working, definition.coolDownGroup());
            changed = true;
        }

        return new ResolvedBoundProp(definition, working, changed);
    }

    private ItemStack bindItem(ItemStack itemStack, String propId, String coolDownGroup) {
        ItemStack working = bridge.writePropId(itemStack, propId);
        if (working == null) {
            working = itemStack;
        }
        bridge.setCooldownTag(working, coolDownGroup);
        return working;
    }

    private void publishPropUsedEvent(Player player, String propId) {
        if (eventBusProvider == null) return;
        EventBusCapability eventBus = eventBusProvider.get();
        if (eventBus == null) return;
        Map<String, String> payload = new java.util.HashMap<>();
        payload.put("prop_id", propId);
        eventBus.publish("axs.prop.prop_used", player, payload);
    }

    private void executeEffects(Player player, PropDefinition definition) {
        List<String> attributeLines = new ArrayList<>();
        List<PropMythicLibEffect> mythicLibEffects = new ArrayList<>();
        List<PropSymphonyEffect> symphonyEffects = new ArrayList<>();
        for (String effectLine : definition.effects()) {
            String[] split = effectLine.split("\\|", 2);
            if (split.length != 2) {
                continue;
            }

            String type = split[0].trim().toLowerCase(Locale.ROOT);
            String payload = split[1].trim();
            switch (type) {
                case "cmd" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replacePlayerToken(payload, player));
                case "msg" -> player.sendMessage(ChatColor.translateAlternateColorCodes('&', replacePlayerToken(payload, player)));
                case "food" -> player.setFoodLevel(Math.min(20, player.getFoodLevel() + parseInt(payload, 0)));
                case "health" -> applyHealthDelta(player, parseDouble(payload, 0.0D));
                case "exp" -> player.giveExp(parseInt(payload, 0));
                case "healthpercent" -> applyHealthPercent(player, parseDouble(payload, 0.0D));
                case "potion" -> applyPotion(player, definition.durationSeconds(), payload);
                case "ap" -> attributeLines.add(payload);
                case "ml", "mythiclib" -> PropMythicLibEffectParser.parse(type + "|" + payload)
                    .ifPresentOrElse(
                        mythicLibEffects::add,
                        () -> AxsLog.logger().warning("Prop MythicLib 效果格式无效，已跳过: " + effectLine)
                    );
                case "sy", "symphony" -> parseSymphonyEffect(payload)
                    .ifPresentOrElse(
                        symphonyEffects::add,
                        () -> AxsLog.logger().warning("Prop Symphony 效果格式无效，已跳过: " + effectLine)
                    );
                default -> {
                    if (configuration.debug()) {
                        AxsLog.logger().warning("ArcartXProp 检测到未知效果类型: " + effectLine);
                    }
                }
            }
        }

        if (!attributeLines.isEmpty() && !attributePlusService.apply(player, definition.displayName(), attributeLines, definition.durationSeconds()) && configuration.debug()) {
            AxsLog.logger().info("ArcartXProp AP 效果已跳过 -> hooked=" + attributePlusService.hooked());
        }
        if (!mythicLibEffects.isEmpty()
            && !mythicLibService.apply(player, definition.id(), mythicLibEffects, definition.durationSeconds())
            && configuration.debug()
        ) {
            AxsLog.logger().info("ArcartXProp MythicLib 效果已跳过 -> hooked=" + mythicLibService.hooked());
        }
        if (!symphonyEffects.isEmpty()
            && !symphonyService.apply(player, definition.id(), symphonyEffects, definition.durationSeconds())
            && configuration.debug()
        ) {
            AxsLog.logger().info("ArcartXProp Symphony 效果已跳过 -> hooked=" + symphonyService.hooked());
        }
    }

    private static java.util.Optional<PropSymphonyEffect> parseSymphonyEffect(String payload) {
        if (payload == null || payload.isBlank()) {
            return java.util.Optional.empty();
        }
        boolean percent = false;
        String working = payload.trim();
        if (working.endsWith("%")) {
            percent = true;
            working = working.substring(0, working.length() - 1).trim();
        }
        int separatorIndex = working.indexOf(':');
        if (separatorIndex <= 0 || separatorIndex >= working.length() - 1) {
            return java.util.Optional.empty();
        }
        String attributeId = working.substring(0, separatorIndex).trim();
        if (attributeId.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            double value = Double.parseDouble(working.substring(separatorIndex + 1).trim());
            return java.util.Optional.of(new PropSymphonyEffect(attributeId, value, percent));
        } catch (NumberFormatException exception) {
            return java.util.Optional.empty();
        }
    }

    private void syncOnlinePlayers() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            scheduleSlotSync(online, 1L);
        }
    }

    private void scheduleSlotSync(Player player, long delayTicks) {
        if (player == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> synchronizeConfiguredSlots(player), Math.max(0L, delayTicks));
    }

    private void synchronizeConfiguredSlots(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        PropPlayerHandle handle = bridge.resolvePlayerHandle(player).orElse(null);
        if (handle == null) {
            return;
        }

        Set<String> slotIds = new LinkedHashSet<>();
        for (PropKeyBindingDefinition definition : keyMappingConfiguration.bindings().values()) {
            slotIds.add(definition.slotId());
        }
        for (String slotId : slotIds) {
            ItemStack slotItem = handle.getSlotItemStack(slotId);
            ResolvedBoundProp resolved = resolveBoundProp(slotItem);
            if (resolved != null && resolved.changed()) {
                handle.setSlotItemStack(slotId, resolved.itemStack());
            }
        }
        handle.syncSlotCacheToClient();
    }

    private PropDefinition resolveDefinition(String propId) {
        if (propId == null) {
            return null;
        }
        return definitionsByNormalizedId.get(propId.trim().toLowerCase(Locale.ROOT));
    }

    private String render(String template, String itemName, long cooldownMillis) {
        String rendered = template
            .replace("{NAME}", itemName == null ? "" : itemName)
            .replace("{TIME}", String.format(Locale.US, "%.2f", Math.max(0L, cooldownMillis) / 1000.0D));
        return ChatColor.translateAlternateColorCodes('&', rendered);
    }

    private static boolean isRealItem(ItemStack itemStack) {
        return itemStack != null && itemStack.getType() != Material.AIR;
    }

    private static ItemStack consumeOne(ItemStack itemStack) {
        if (!isRealItem(itemStack)) {
            return new ItemStack(Material.AIR);
        }
        if (itemStack.getAmount() <= 1) {
            return new ItemStack(Material.AIR);
        }
        ItemStack next = itemStack.clone();
        next.setAmount(next.getAmount() - 1);
        return next;
    }

    private static String replacePlayerToken(String text, Player player) {
        return (text == null ? "" : text).replace("{player}", player.getName());
    }

    private static void applyHealthDelta(Player player, double delta) {
        if (player == null) {
            return;
        }
        Attribute attribute = Attribute.GENERIC_MAX_HEALTH;
        var maxHealthAttribute = player.getAttribute(attribute);
        double maxHealth = maxHealthAttribute == null ? player.getHealth() : maxHealthAttribute.getValue();
        player.setHealth(Math.min(maxHealth, Math.max(0.0D, player.getHealth() + delta)));
    }

    private static void applyHealthPercent(Player player, double percent) {
        if (player == null) {
            return;
        }
        var maxHealthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttribute == null) {
            return;
        }
        double maxHealth = maxHealthAttribute.getValue();
        double delta = maxHealth * percent / 100.0D;
        player.setHealth(Math.min(maxHealth, Math.max(0.0D, player.getHealth() + delta)));
    }

    private static void applyPotion(Player player, int durationSeconds, String payload) {
        String[] split = payload.split(":", 2);
        if (split.length != 2) {
            return;
        }
        PotionEffectType type = PotionEffectType.getByName(split[0].trim().toUpperCase(Locale.ROOT));
        if (type == null) {
            return;
        }
        int amplifier = parseInt(split[1], 0);
        player.addPotionEffect(new PotionEffect(type, Math.max(0, durationSeconds) * 20, amplifier));
    }

    private static int parseInt(String rawValue, int defaultValue) {
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static double parseDouble(String rawValue, double defaultValue) {
        try {
            return Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private String renderCondition(String template, String itemName, String conditionRaw) {
        String rendered = template
            .replace("{NAME}", itemName == null ? "" : itemName)
            .replace("{CONDITION}", conditionRaw == null ? "" : conditionRaw);
        return ChatColor.translateAlternateColorCodes('&', rendered);
    }

    private static String resolveDisplayName(ItemStack itemStack) {
        if (itemStack == null) {
            return "§c未知道具";
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null && itemMeta.hasDisplayName()) {
            String displayName = itemMeta.getDisplayName();
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
        }

        String[] parts = itemStack.getType().name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.length() == 0 ? itemStack.getType().name() : builder.toString();
    }

    public record PropOperationResult(boolean success, String message) {

        public static PropOperationResult success(String message) {
            return new PropOperationResult(true, message);
        }

        public static PropOperationResult failure(String message) {
            return new PropOperationResult(false, message);
        }
    }

    private record ResolvedBoundProp(PropDefinition definition, ItemStack itemStack, boolean changed) {
    }

    private enum PropUseMode {
        HAND,
        KEY
    }
}
