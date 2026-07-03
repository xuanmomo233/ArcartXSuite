package xuanmo.arcartxsuite.combateffect;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.capability.CombatEffectTriggerable;
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
public final class CombatEffectModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final String CONFIG_FILE_NAME = "ArcartXCombatEffect.yml";
    private static final String MESSAGES_FILE_NAME = "messages.yml";

    private FileConfiguration rawConfiguration;
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

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("combateffect")
            .name("CombatEffect")
            .version("1.0.2-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return CONFIG_FILE_NAME;
    }

    @Override
    protected String messagesFileName() {
        return MESSAGES_FILE_NAME;
    }

    @Override
    protected int currentConfigVersion() {
        return 1;
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        return SyncPolicy.builder()
            .dynamicSection("kill-effect")
            .dynamicSection("combo-tracker")
            .dynamicSection("death-buffer")
            .dynamicSection("keybind-trigger")
            .dynamicSection("state-trigger")
            .dynamicSection("digis-display")
            .build();
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("arcartx/ui/combat_kill_effect.yml", "ui/combat_kill_effect.yml");
        mappings.put("arcartx/ui/combo_effect.yml", "ui/combo_effect.yml");
        mappings.put("arcartx/ui/death_buffer.yml", "ui/death_buffer.yml");
        return mappings;
    }

    @Override
    protected boolean overwriteUiFiles() {
        return false;
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXCombatEffect.yml 配置文件缺失");
        }
        rawConfiguration = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection killEffectSection = rawConfiguration.getConfigurationSection("kill-effect");
        String packetsDirRelative = killEffectSection != null
            ? killEffectSection.getString("packets-directory", "packets") : "packets";
        File packetsDirectory = new File(dataFolder, packetsDirRelative);
        if (!packetsDirectory.exists()) {
            packetsDirectory.mkdirs();
        }
        File[] existingPackets = packetsDirectory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existingPackets == null || existingPackets.length == 0) {
            File defaultPackets = new File(packetsDirectory, "default.yml");
            if (!defaultPackets.exists()) {
                exportResource("packets/default.yml", defaultPackets, false);
            }
        }
        packetConfiguration = CombatEffectPacketConfiguration.load(killEffectSection, logger, packetsDirectory);
        displayConfiguration = CombatDisplayConfiguration.load(rawConfiguration.getConfigurationSection("digis-display"));
        deathBufferConfiguration = DeathBufferConfiguration.load(rawConfiguration.getConfigurationSection("death-buffer"));
        comboTrackerConfiguration = ComboTrackerConfiguration.load(rawConfiguration.getConfigurationSection("combo-tracker"));
    }

    @Override
    protected void startService() throws Exception {

        // 注册 UI（由基类根据 uiResourceMappings() 自动导出并注册）
        registerModuleUi("ui/combat_kill_effect.yml", "combat_kill_effect", true);
        registerModuleUi("ui/combo_effect.yml", "combo_effect", true);
        registerModuleUi("ui/death_buffer.yml", "death_buffer", true);

        // 导出 damage_display 资源到 ArcartX damage_display/ 目录
        exportDamageDisplayResources();

        packetService = new CombatEffectPacketService(plugin, packetConfiguration, packetBridge, logger);
        packetService.setEventBusProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        packetService.start();

        if (clientBridge != null && clientBridge.isAvailable()) {
            displayService = new CombatDisplayService(plugin, logger, displayConfiguration, clientBridge, attributeBridge);
            displayService.start();
        }

        if (deathBufferConfiguration.enabled()) {
            deathBufferService = new DeathBufferService(
                plugin, deathBufferConfiguration, packetConfiguration, packetBridge, logger
            );
            deathBufferService.start();
        }

        if (comboTrackerConfiguration.enabled()) {
            comboTrackerService = new ComboTrackerService(
                plugin, comboTrackerConfiguration, packetConfiguration, packetBridge, clientBridge, logger
            );
            comboTrackerService.start();
        }

        // 按键触发器
        boolean keybindEnabled = rawConfiguration != null
            ? rawConfiguration.getBoolean("keybind-trigger.enabled", false)
            : false;
        if (keybindEnabled) {
            keybindTriggerService = new KeybindTriggerService(plugin, packetConfiguration, packetBridge, logger);
            keybindTriggerService.start();
        }

        // 状态/控制器触发器
        boolean stateEnabled = rawConfiguration != null
            ? rawConfiguration.getBoolean("state-trigger.enabled", false)
            : false;
        if (stateEnabled) {
            stateTriggerService = new StateTriggerService(plugin, packetConfiguration, packetBridge, logger);
            stateTriggerService.start();
        }

        // 注册跨模块 capability
        registerCapability(CombatEffectTriggerable.class, new CombatEffectTriggerable() {
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
        });

        logger.fine(
            "CombatEffect 模块已启动，已启用包: "
                + packetConfiguration.enabledPacketCount() + "/" + packetConfiguration.packetDefinitions().size()
                + (displayService != null ? " | 战斗显示: 已启用" : " | 战斗显示: 未启用")
                + (deathBufferService != null ? " | 死亡缓冲: 已启用" : " | 死亡缓冲: 未启用")
                + (comboTrackerService != null ? " | 连击追踪: 已启用" : " | 连击追踪: 未启用")
        );
    }

    @Override
    protected void stopService() {
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
        rawConfiguration = null;
        comboTrackerConfiguration = null;
        deathBufferConfiguration = null;
        displayConfiguration = null;
        packetConfiguration = null;
    }

    public CombatEffectPacketConfiguration getPacketConfiguration() {
        return packetConfiguration;
    }

    public CombatDisplayConfiguration getDisplayConfiguration() {
        return displayConfiguration;
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
        var mp = messages();
        if (mp == null) return enabled ? "已启用" : "已禁用";
        return mp.get(enabled ? "common.enabled" : "common.disabled");
    }

    private void sendStatus(CommandSender sender) {
        var mp = messages();
        sender.sendMessage(msg("status.title", descriptor().version()));
        sender.sendMessage(msg("status.packets",
            packetConfiguration != null
                ? packetConfiguration.enabledPacketCount() + "/" + packetConfiguration.packetDefinitions().size()
                : (mp != null ? mp.get("status.packets-none") : "N/A")));
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
        boolean success = packetService != null && packetService.triggerPacketById(packetId, target, variables.isEmpty() ? null : variables);
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
        boolean success = packetService != null && packetService.triggerDirect(uiId, handler, target, payload);
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

    // ─── damage_display 资源导出（特殊路径，不走标准 UI 注册） ───

    private static final Map<String, String> DAMAGE_DISPLAY_MAPPINGS = new LinkedHashMap<>();
    static {
        DAMAGE_DISPLAY_MAPPINGS.put("arcartx/damage_display/ArcartXSuite-damage.yml", "ArcartXSuite-damage.yml");
        DAMAGE_DISPLAY_MAPPINGS.put("arcartx/damage_display/ArcartXSuite-heal.yml", "ArcartXSuite-heal.yml");
    }

    private void exportDamageDisplayResources() {
        org.bukkit.plugin.Plugin arcartX = org.bukkit.Bukkit.getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            logger.fine("ArcartX 未安装，跳过 damage_display 资源导出");
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
            exportResource(entry.getKey(), target, false);
            logger.fine("已导出 damage_display 资源: " + entry.getValue());
        }
    }

    private String msg(String key, Object... args) {
        var mp = messages();
        if (mp == null) return key;
        return mp.get("prefix") + mp.get(key, args);
    }
}






