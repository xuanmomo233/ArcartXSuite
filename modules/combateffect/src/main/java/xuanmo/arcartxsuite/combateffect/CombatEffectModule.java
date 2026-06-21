package xuanmo.arcartxsuite.combateffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AXSModule;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleContext;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.capability.CombatEffectTriggerable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.config.ConfigSyncSpec;
import xuanmo.arcartxsuite.api.config.ModuleConfigSpec;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.combateffect.combo.ComboTrackerConfiguration;
import xuanmo.arcartxsuite.combateffect.combo.ComboTrackerService;
import xuanmo.arcartxsuite.combateffect.deathbuffer.DeathBufferConfiguration;
import xuanmo.arcartxsuite.combateffect.deathbuffer.DeathBufferService;
import xuanmo.arcartxsuite.combateffect.display.config.CombatDisplayConfiguration;
import xuanmo.arcartxsuite.combateffect.display.service.CombatDisplayService;
import xuanmo.arcartxsuite.combateffect.packet.CombatEffectPacketService;
import xuanmo.arcartxsuite.combateffect.packet.config.CombatEffectPacketConfiguration;
import xuanmo.arcartxsuite.combateffect.trigger.KeybindTriggerService;
import xuanmo.arcartxsuite.combateffect.trigger.StateTriggerService;

/**
 * CombatEffect 独立模块。
 * <p>
 * 监听击杀 / 攻击事件并向客户端发送自定义 UI 包；
 * 同时管理伤害 / 治疗显示子模块。
 */
public final class CombatEffectModule implements AXSModule, ModuleCommandHandler, CombatEffectTriggerable {

    private static final String CONFIG_FILE_NAME = "ArcartXCombatEffect.yml";
    private static final String MESSAGES_FILE_NAME = "messages.yml";

    private ModuleContext context;
    private MessageProvider messages;
    private CombatEffectPacketConfiguration packetConfiguration;
    private CombatEffectPacketService packetService;
    private CombatDisplayConfiguration displayConfiguration;
    private CombatDisplayService displayService;
    private DeathBufferConfiguration deathBufferConfiguration;
    private DeathBufferService deathBufferService;
    private ComboTrackerConfiguration comboTrackerConfiguration;
    private ComboTrackerService comboTrackerService;
    private KeybindTriggerService keybindTriggerService;
    private StateTriggerService stateTriggerService;
    private final List<String> registeredUiIds = new ArrayList<>();
    private boolean ready;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("combateffect")
            .name("CombatEffect")
            .version("1.0.2-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    public List<ModuleConfigSpec> configSpecs() {
        // kill-effect、combo-tracker、death-buffer、digis-display 包含用户自定义条目
        SyncPolicy policy = SyncPolicy.builder()
            .dynamicSection("kill-effect")
            .dynamicSection("combo-tracker")
            .dynamicSection("death-buffer")
            .dynamicSection("keybind-trigger")
            .dynamicSection("state-trigger")
            .dynamicSection("digis-display")
            .build();
        return List.of(new ModuleConfigSpec(
            "combateffect",
            new ConfigSyncSpec(CONFIG_FILE_NAME, "data/combateffect/config.yml", policy),
            1,
            "config-version",
            "migrations",
            List.of()
        ));
    }

    @Override
    public boolean onEnable(ModuleContext context) throws Exception {
        this.context = context;
        Logger logger = context.logger();

        File configFile = ensureConfigExists();
        initMessages();
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection killEffectSection = yaml.getConfigurationSection("kill-effect");
        String packetsDirRelative = killEffectSection != null
            ? killEffectSection.getString("packets-directory", "packets") : "packets";
        File packetsDirectory = new File(context.dataFolder(), packetsDirRelative);
        if (!packetsDirectory.exists()) {
            packetsDirectory.mkdirs();
        }
        File[] existingPackets = packetsDirectory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existingPackets == null || existingPackets.length == 0) {
            File defaultPackets = new File(packetsDirectory, "default.yml");
            if (!defaultPackets.exists()) {
                context.exportResource("packets/default.yml", defaultPackets, false);
            }
        }
        packetConfiguration = CombatEffectPacketConfiguration.load(killEffectSection, logger, packetsDirectory);
        displayConfiguration = CombatDisplayConfiguration.load(yaml.getConfigurationSection("digis-display"));

        PacketBridgeAPI packetBridge = context.packetBridge();
        ClientBridgeAPI clientBridge = context.clientBridge();
        JavaPlugin plugin = (JavaPlugin) context.plugin();

        // 导出 UI 资源到 ArcartX ui/ 目录
        exportUiResources(context);
        // 导出 damage_display 资源到 ArcartX damage_display/ 目录
        exportDamageDisplayResources(context);

        packetService = new CombatEffectPacketService(plugin, packetConfiguration, packetBridge, logger);
        packetService.setEventBusProvider(() -> context.getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        packetService.start();

        if (clientBridge != null && clientBridge.isAvailable()) {
            displayService = new CombatDisplayService(plugin, displayConfiguration, clientBridge, context.attributeBridge());
            displayService.start();
        }

        deathBufferConfiguration = DeathBufferConfiguration.load(yaml.getConfigurationSection("death-buffer"));
        if (deathBufferConfiguration.enabled()) {
            deathBufferService = new DeathBufferService(
                plugin, deathBufferConfiguration, packetConfiguration, packetBridge, logger
            );
            deathBufferService.start();
        }

        comboTrackerConfiguration = ComboTrackerConfiguration.load(yaml.getConfigurationSection("combo-tracker"));
        if (comboTrackerConfiguration.enabled()) {
            comboTrackerService = new ComboTrackerService(
                plugin, comboTrackerConfiguration, packetConfiguration, packetBridge, clientBridge, logger
            );
            comboTrackerService.start();
        }

        // 按键触发器
        boolean keybindEnabled = yaml.getBoolean("keybind-trigger.enabled", false);
        if (keybindEnabled) {
            keybindTriggerService = new KeybindTriggerService(plugin, packetConfiguration, packetBridge, logger);
            keybindTriggerService.start();
        }

        // 状态/控制器触发器
        boolean stateEnabled = yaml.getBoolean("state-trigger.enabled", false);
        if (stateEnabled) {
            stateTriggerService = new StateTriggerService(plugin, packetConfiguration, packetBridge, logger);
            stateTriggerService.start();
        }

        // 注册跨模块 capability
        context.registerCapability(CombatEffectTriggerable.class, this);

        logger.fine(
            "CombatEffect 模块已载入，已启用包: "
                + packetConfiguration.enabledPacketCount() + "/" + packetConfiguration.packetDefinitions().size()
                + (displayService != null ? " | 战斗显示: 已启用" : " | 战斗显示: 未启用")
                + (deathBufferService != null ? " | 死亡缓冲: 已启用" : " | 死亡缓冲: 未启用")
                + (comboTrackerService != null ? " | 连击追踪: 已启用" : " | 连击追踪: 未启用")
        );
        ready = true;
        return true;
    }

    @Override
    public void onDisable() {
        ready = false;
        if (stateTriggerService != null) {
            stateTriggerService.shutdown();
            stateTriggerService = null;
        }
        if (keybindTriggerService != null) {
            keybindTriggerService.shutdown();
            keybindTriggerService = null;
        }
        if (comboTrackerService != null) {
            comboTrackerService.shutdown();
            comboTrackerService = null;
        }
        if (deathBufferService != null) {
            deathBufferService.shutdown();
            deathBufferService = null;
        }
        if (displayService != null) {
            displayService.shutdown();
            displayService = null;
        }
        if (packetService != null) {
            packetService.shutdown();
            packetService = null;
        }
        // 注销已注册的 UI
        if (context != null) {
            for (String uiId : registeredUiIds) {
                context.unregisterUi(uiId);
            }
        }
        registeredUiIds.clear();
        comboTrackerConfiguration = null;
        deathBufferConfiguration = null;
        displayConfiguration = null;
        packetConfiguration = null;
    }

    @Override
    public void onReload() throws Exception {
        onDisable();
        if (context != null) {
            onEnable(context);
        }
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    public CombatEffectPacketConfiguration getPacketConfiguration() {
        return packetConfiguration;
    }

    public CombatDisplayConfiguration getDisplayConfiguration() {
        return displayConfiguration;
    }

    // ─── CombatEffectTriggerable ─────────────────────────────

    @Override
    public boolean triggerPacket(@NotNull String packetId, @NotNull Player recipient, @Nullable Map<String, String> variables) {
        if (packetService == null) return false;
        return packetService.triggerPacketById(packetId, recipient, variables);
    }

    @Override
    public boolean triggerDirect(@NotNull String uiId, @NotNull String packetHandler, @NotNull Player recipient, @Nullable Object payload) {
        if (packetService == null) return false;
        return packetService.triggerDirect(uiId, packetHandler, recipient, payload);
    }

    // ─── ModuleCommandHandler ────────────────────────────────

    private static final List<String> ACTIONS = List.of("help", "status", "reload", "send", "direct");

    @Override
    public String commandId() {
        return "combateffect";
    }

    @Override
    public List<String> actions() {
        return ACTIONS;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        String cmd = "/" + label + " combateffect";
        switch (action) {
            case "help" -> sendHelp(sender, cmd);
            case "status" -> sendStatus(sender);
            case "reload" -> sender.sendMessage(msg("common.reload-hint", label));
            case "send" -> handleSend(sender, args, cmd);
            case "direct" -> handleDirect(sender, args, cmd);
            default -> sender.sendMessage(msg("common.unknown", cmd));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            return filter(ACTIONS, args[1]);
        }
        if (args.length == 3) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if ("send".equals(action) && packetService != null) {
                return filter(packetService.packetIds(), args[2]);
            }
        }
        if (args.length == 4) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if ("send".equals(action) || "direct".equals(action)) {
                return null; // 返回 null 让 Bukkit 补全在线玩家名
            }
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String cmd) {
        sender.sendMessage(msg("help.title", descriptor().version()));
        sender.sendMessage(msg("help.status", cmd));
        sender.sendMessage(msg("help.reload", cmd));
        sender.sendMessage(msg("help.send", cmd));
        sender.sendMessage(msg("help.direct", cmd));
    }

    private String enabledLabel(boolean enabled) {
        return messages.get(enabled ? "common.enabled" : "common.disabled");
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(msg("status.title", descriptor().version()));
        sender.sendMessage(msg("status.packets",
            packetConfiguration != null
                ? packetConfiguration.enabledPacketCount() + "/" + packetConfiguration.packetDefinitions().size()
                : messages.get("status.packets-none")));
        sender.sendMessage(msg("status.display", enabledLabel(displayService != null)));
        sender.sendMessage(msg("status.death-buffer", enabledLabel(deathBufferService != null)));
        sender.sendMessage(msg("status.combo", enabledLabel(comboTrackerService != null)));
        sender.sendMessage(msg("status.keybind", enabledLabel(keybindTriggerService != null)));
        sender.sendMessage(msg("status.state", enabledLabel(stateTriggerService != null)));
    }

    // /axs combateffect send <packetId> <player> [k=v ...]
    private void handleSend(CommandSender sender, String[] args, String cmd) {
        if (args.length < 4) {
            sender.sendMessage(msg("send.usage", cmd));
            return;
        }
        String packetId = args[2];
        Player target = Bukkit.getPlayerExact(args[3]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(msg("send.player-offline", args[3]));
            return;
        }
        Map<String, String> variables = parseKeyValues(args, 4);
        boolean success = triggerPacket(packetId, target, variables.isEmpty() ? null : variables);
        sender.sendMessage(success
            ? msg("send.success", packetId, target.getName())
            : msg("send.fail"));
    }

    // /axs combateffect direct <uiId> <handler> <player> [k=v ...]
    private void handleDirect(CommandSender sender, String[] args, String cmd) {
        if (args.length < 5) {
            sender.sendMessage(msg("direct.usage", cmd));
            return;
        }
        String uiId = args[2];
        String handler = args[3];
        Player target = Bukkit.getPlayerExact(args[4]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(msg("direct.player-offline", args[4]));
            return;
        }
        Map<String, String> variables = parseKeyValues(args, 5);
        Object payload = variables.isEmpty() ? Map.of() : variables;
        boolean success = triggerDirect(uiId, handler, target, payload);
        sender.sendMessage(success
            ? msg("direct.success", uiId, handler, target.getName())
            : msg("direct.fail"));
    }

    private static Map<String, String> parseKeyValues(String[] args, int startIndex) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = startIndex; i < args.length; i++) {
            int eq = args[i].indexOf('=');
            if (eq > 0 && eq < args[i].length() - 1) {
                map.put(args[i].substring(0, eq), args[i].substring(eq + 1));
            }
        }
        return map;
    }

    private static List<String> filter(List<String> candidates, String input) {
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(candidate);
            }
        }
        return result;
    }

    // ─── UI 资源导出 ─────────────────────────────────────────

    private static final Map<String, String> UI_RESOURCE_MAPPINGS = new LinkedHashMap<>();
    static {
        UI_RESOURCE_MAPPINGS.put("arcartx/ui/combat_kill_effect.yml", "ui/combat_kill_effect.yml");
        UI_RESOURCE_MAPPINGS.put("arcartx/ui/combo_effect.yml", "ui/combo_effect.yml");
        UI_RESOURCE_MAPPINGS.put("arcartx/ui/death_buffer.yml", "ui/death_buffer.yml");
    }

    private static final Map<String, String> DAMAGE_DISPLAY_MAPPINGS = new LinkedHashMap<>();
    static {
        DAMAGE_DISPLAY_MAPPINGS.put("arcartx/damage_display/ArcartXSuite-damage.yml", "ArcartXSuite-damage.yml");
        DAMAGE_DISPLAY_MAPPINGS.put("arcartx/damage_display/ArcartXSuite-heal.yml", "ArcartXSuite-heal.yml");
    }

    private void exportUiResources(ModuleContext ctx) {
        ClassLoader loader = getClass().getClassLoader();
        for (Map.Entry<String, String> entry : UI_RESOURCE_MAPPINGS.entrySet()) {
            try {
                File uiFile = ctx.exportUiResource(entry.getKey(), entry.getValue(), false, loader);
                // 导出后必须向 ArcartX 注册，否则 sendPacket 找不到目标 UI
                String uiId = uiFile.getName().replace(".yml", "");
                UiBinding binding = ctx.prepareUiBinding("CombatEffect", uiId, true, uiFile);
                if (binding != null) {
                    if (binding.registeredUiId() != null) {
                        registeredUiIds.add(binding.registeredUiId());
                    }
                } else {
                    ctx.logger().warning("CombatEffect UI 注册失败: " + uiId);
                }
            } catch (IOException exception) {
                ctx.logger().warning("UI 资源导出失败: " + entry.getKey() + " | " + exception.getMessage());
            }
        }
    }

    private void exportDamageDisplayResources(ModuleContext ctx) {
        org.bukkit.plugin.Plugin arcartX = org.bukkit.Bukkit.getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            ctx.logger().fine("ArcartX 未安装，跳过 damage_display 资源导出");
            return;
        }
        File damageDisplayDir = new File(arcartX.getDataFolder(), "damage_display");
        if (!damageDisplayDir.exists()) {
            damageDisplayDir.mkdirs();
        }
        for (Map.Entry<String, String> entry : DAMAGE_DISPLAY_MAPPINGS.entrySet()) {
            File target = new File(damageDisplayDir, entry.getValue());
            if (target.exists()) {
                continue;
            }
            ctx.exportResource(entry.getKey(), target, false);
            ctx.logger().fine("已导出 damage_display 资源: " + entry.getValue());
        }
    }

    private void initMessages() {
        File messagesFile = new File(context.dataFolder(), MESSAGES_FILE_NAME);
        if (!messagesFile.exists()) {
            context.exportResource(MESSAGES_FILE_NAME, messagesFile, false);
        }
        messages = new MessageProvider(context.dataFolder(), MESSAGES_FILE_NAME, getClass().getClassLoader(), context.logger());
        messages.load();
    }

    private String msg(String key, Object... args) {
        return messages.get("prefix") + messages.get(key, args);
    }

    private File ensureConfigExists() {
        File moduleDataFolder = context.dataFolder();
        File newConfigFile = new File(moduleDataFolder, "config.yml");

        // 一次性迁移：plugins/ArcartXSuite/ArcartXCombatEffect.yml -> data/combateffect/config.yml
        File legacyFile = new File(context.pluginDataFolder(), CONFIG_FILE_NAME);
        if (legacyFile.isFile() && !newConfigFile.exists()) {
            try {
                java.nio.file.Files.createDirectories(moduleDataFolder.toPath());
                java.nio.file.Files.move(
                    legacyFile.toPath(),
                    newConfigFile.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                );
                context.logger().info(org.bukkit.ChatColor.GOLD + "→ 已归位配置文件: "
                    + org.bukkit.ChatColor.YELLOW + CONFIG_FILE_NAME
                    + org.bukkit.ChatColor.GRAY + "  ➜  "
                    + org.bukkit.ChatColor.AQUA + "data/combateffect/config.yml");
            } catch (java.io.IOException exception) {
                context.logger().warning("迁移配置文件失败: " + CONFIG_FILE_NAME
                    + " | " + exception.getMessage());
            }
        }

        if (!newConfigFile.exists()) {
            context.exportResource(CONFIG_FILE_NAME, newConfigFile, false);
        }
        return newConfigFile;
    }
}



