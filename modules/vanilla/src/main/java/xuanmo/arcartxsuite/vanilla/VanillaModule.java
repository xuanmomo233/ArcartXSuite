package xuanmo.arcartxsuite.vanilla;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.event.ModuleLifecycleEvent;
import xuanmo.arcartxsuite.api.capability.WarehousePanelAccess;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.vanilla.config.VanillaConfiguration;
import xuanmo.arcartxsuite.vanilla.listener.InventoryOpenListener;
import xuanmo.arcartxsuite.vanilla.service.VanillaService;
import xuanmo.arcartxsuite.vanilla.task.FurnaceStateTask;
import xuanmo.arcartxsuite.vanilla.recipe.WorkshopType;
import xuanmo.arcartxsuite.vanilla.recipe.RecipeEditor;
import xuanmo.arcartxsuite.vanilla.recipe.VanillaRecipeRegistry;
import xuanmo.arcartxsuite.api.inventory.ItemPresentation;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.api.capability.VanillaBackpackAccess;
import xuanmo.arcartxsuite.api.capability.VanillaRecipeAccess;
import xuanmo.arcartxsuite.api.capability.WarehouseAccess;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.vanilla.backpack.command.ExtraBackpackAdminCommand;
import xuanmo.arcartxsuite.vanilla.backpack.config.ExtraBackpackConfiguration;
import xuanmo.arcartxsuite.vanilla.backpack.placeholder.ExtraBackpackPlaceholderExpansion;
import xuanmo.arcartxsuite.vanilla.backpack.service.ExtraBackpackService;

/**
 * Vanilla UI 模块：基于 ArcartX UI 系统替换原版工作台与熔炉界面。
 * <p>
 * 提供配方书、中文搜索、一键填充材料、熔炉状态实时推送等功能。
 */
public final class VanillaModule extends AbstractAXSModule implements Listener,xuanmo.arcartxsuite.api.ModuleCommandHandler {

    private VanillaConfiguration configuration;
    private VanillaService service;
    private FurnaceStateTask furnaceTask;
    private InventoryOpenListener inventoryListener;
    private ExtraBackpackConfiguration backpackConfiguration;
    private ExtraBackpackService backpack;
    private Runnable unregisterMmoItemsAccessories;
    private ExtraBackpackAdminCommand backpackCommand;
    private VanillaRecipeRegistry recipes;
    private RecipeEditor recipeEditor;
    private boolean recipesEnabled;
    private static final Pattern LAYOUT_BOOTSTRAP = Pattern.compile(
        "(?m)^([\\t ]*)var\\.whLayoutEnabled = (?:true|false)[\\t ]*$");
    private final Map<String, File> layoutUiFiles = new LinkedHashMap<>();
    private BukkitTask layoutRefreshTask;
    private boolean stopping;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("vanilla")
            .name("VanillaUI")
            .version("3.3.7")
            .mainClass(getClass().getName())
            .softDepends(List.of("warehouse"))
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        List<ValidationRule> rules = new ArrayList<>();
        rules.add(ValidationRule.of("ui.overwrite-ui-files", ValueType.BOOLEAN));
        rules.add(ValidationRule.of("backpack.enabled", ValueType.BOOLEAN));
        rules.add(ValidationRule.of("backpack.mmoitems-attribute-slots", ValueType.STRING_LIST));
        rules.add(ValidationRule.of("backpack.presentation.enabled", ValueType.BOOLEAN));
        rules.add(ValidationRule.of("backpack.presentation.transition-ms", ValueType.INT).withRange(0, 3000));
        rules.add(ValidationRule.of("backpack.presentation.blend-ms", ValueType.INT).withRange(0, 2000));
        rules.add(ValidationRule.of("backpack.presentation.open.animation", ValueType.STRING));
        rules.add(ValidationRule.of("backpack.presentation.open.duration-ms", ValueType.INT).withRange(50, 60000));
        rules.add(ValidationRule.of("backpack.presentation.idle.animation", ValueType.STRING));
        rules.add(ValidationRule.of("backpack.presentation.random.min-interval-ms", ValueType.INT).withRange(250, 120000));
        rules.add(ValidationRule.of("backpack.presentation.random.max-interval-ms", ValueType.INT).withRange(250, 120000));
        rules.add(ValidationRule.of("recipes.enabled", ValueType.BOOLEAN));
        for (WorkshopType type : WorkshopType.values()) {
            rules.add(ValidationRule.of("ui." + type.configKey() + "-ui-id", ValueType.STRING));
            rules.add(ValidationRule.of(type.configKey() + ".enabled", ValueType.BOOLEAN));
            if (type.processing()||type.equipment()||type.artisan()||type.utility()) rules.add(ValidationRule.of(type.configKey() + ".push-interval-ticks", ValueType.INT).withRange(1, 100));
        }
        return ModuleConfig.builder()
            .configFileName("ArcartXVanilla.yml")
            .messagesFileName("message.yml")
            .syncPolicy(SyncPolicy.builder().obsoletePath("backpack.categories")
                .dynamicSection("backpack.presentation.random.actions").build())
            .currentVersion(3)
            .validations(rules)
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        for (WorkshopType type : WorkshopType.values())
            if (configuration == null || configuration.enabled(type)) mappings.put(type.resource(), type.file());
        return new ModuleUiSpec(mappings, configuration.ui().overwriteUiFiles());
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        var editor = recipeEditor;
        var current = service;
        var packetIds = new java.util.ArrayList<String>();
        if (current != null) packetIds.addAll(current.packetIds());
        if (editor != null) packetIds.addAll(editor.packetIds());
        return new PacketHandlerSpec((player, packetId, data) -> {
            if (editor != null && editor.ownsPacket(packetId)) {
                editor.handle(player, data);
                return true;
            }
            return current != null && current.handleClientPacket(player, packetId, data);
        }, 0, packetIds, "vanilla");
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXVanilla.yml 配置文件缺失");
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        configuration = VanillaConfiguration.load(yaml);
        recipesEnabled = yaml.getBoolean("recipes.enabled", true);
        var backpackSection = yaml.getConfigurationSection("backpack");
        backpackConfiguration = backpackSection == null ? null : ExtraBackpackConfiguration.load(backpackSection);
    }

    @Override
    protected void startService() throws Exception {
        stopping = false;
        PacketBridgeAPI noticeBridge=xuanmo.arcartxsuite.api.message.UiNoticeBridge.wrap(packetBridge,messages(),dataFolder,"message.yml");
        service = new VanillaService(plugin,noticeBridge,itemStackBridge,vanillaItemNameBridge,configuration,logger);
        service.setWarehousePanel(() -> getCapability(WarehousePanelAccess.class));
        service.materialPlanner().setProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess.class));

        Map<WorkshopType, String> cookingUis = new java.util.EnumMap<>(WorkshopType.class);
        for (WorkshopType type : WorkshopType.values()) {
            if (!configuration.enabled(type)) continue;
            UiBinding binding = registerPreparedUi(type.resource(), type.file(), configuration.uiId(type));
            service.setRuntimeUiId(type, binding.runtimeUiId());
            if (type.processing()) cookingUis.put(type, binding.runtimeUiId());
            logger.info("Vanilla " + type.title() + " UI 已注册: " + binding.runtimeUiId());
        }
        if (!cookingUis.isEmpty()) {
            furnaceTask = new FurnaceStateTask(plugin, packetBridge, cookingUis, configuration);
            furnaceTask.start();
            inventoryListener = new InventoryOpenListener(furnaceTask);
        }
        startBackpack();
        if (recipesEnabled) startRecipes(noticeBridge);
        logger.info("VanillaUI 模块已启用 | 原版工位及容器界面=" + layoutUiFiles.size());
    }

    private void startRecipes(PacketBridgeAPI noticeBridge) {
        recipes = new VanillaRecipeRegistry(dataFolder, itemSourceRegistry, service.materialPlanner());
        try {
            recipes.reload();
        } catch (Exception exception) {
            logger.warning("自定义配方未发布，修正 recipes.yml 后执行 /axs vanilla recipes reload。"
                + exception.getMessage());
        }
        UiBinding editor = registerModuleUi("arcartx/ui/vanilla_recipe_editor.yml", "ui/vanilla/vanilla_recipe_editor.yml",
            RecipeEditor.UI, true);
        recipeEditor = new RecipeEditor(plugin, recipes, noticeBridge, new ItemPresentation(itemStackBridge, vanillaItemNameBridge), itemStackBridge,
            editor.runtimeUiId());
        recipeEditor.registerExtraEvents();
        service.setCustomRecipes(recipes, recipeEditor);
        registerCapability(VanillaRecipeAccess.class, recipes);
    }

    @Override
    protected void stopService() {
        stopping = true;
        if (layoutRefreshTask != null) {
            layoutRefreshTask.cancel();
            layoutRefreshTask = null;
        }
        layoutUiFiles.clear();
        if (recipeEditor != null) {
            recipeEditor.close();
            recipeEditor = null;
        }
        if (recipes != null) {
            recipes.close();
            recipes = null;
        }
        backpackCommand = null;
        if (unregisterMmoItemsAccessories != null) {
            unregisterMmoItemsAccessories.run();
            unregisterMmoItemsAccessories = null;
        }
        if (backpack != null) {
            backpack.shutdown();
            backpack = null;
        }
        if (furnaceTask != null) {
            furnaceTask.stop();
            furnaceTask = null;
        }
        if (service != null) {
            service.shutdown();
            service = null;
        }
        inventoryListener = null;
        configuration = null;
    }

    @Override
    protected @NotNull List<Listener> createListeners() {
        List<Listener> listeners = new ArrayList<>();
        listeners.add(this);
        listeners.add(service);
        listeners.add(service.equipment());
        listeners.add(service.artisan());
        listeners.add(service.utility());
        if (recipes != null) listeners.add(recipes);
        if (recipeEditor != null) listeners.add(recipeEditor);
        listeners.add(new xuanmo.arcartxsuite.vanilla.listener.MaterialTransferInventoryGuard(service.materialPlanner()));
        if (inventoryListener != null) {
            listeners.add(inventoryListener);
        }
        return listeners;
    }

    private UiBinding registerPreparedUi(String resource, String relativePath, String uiId) throws IOException {
        File uiFile = exportUiResource(resource, relativePath,
            configuration.ui().overwriteUiFiles(), moduleClassLoader());
        prepareWarehouseLayout(uiFile);
        layoutUiFiles.put(uiId, uiFile);
        return registerPreparedFile(uiId, uiFile);
    }

    private boolean prepareWarehouseLayout(File uiFile) throws IOException {
        String source = Files.readString(uiFile.toPath(), StandardCharsets.UTF_8);
        Matcher marker = LAYOUT_BOOTSTRAP.matcher(source);
        if (!marker.find()) {
            logger.warning("Vanilla UI 缺少 whLayoutEnabled 初始化标记，保留自定义文件: " + uiFile.getName());
            return false;
        }
        String prepared = marker.replaceFirst(Matcher.quoteReplacement(marker.group(1)
            + "var.whLayoutEnabled = " + (getCapability(WarehousePanelAccess.class) != null)));
        if (source.equals(prepared)) return false;
        Files.writeString(uiFile.toPath(), prepared, StandardCharsets.UTF_8);
        return true;
    }

    private UiBinding registerPreparedFile(String uiId, File uiFile) {
        if (packetBridge == null || !packetBridge.isAvailable()) {
            return new UiBinding(PacketBridgeAPI.normalizeUiId(uiId, uiFile), null);
        }
        var registration = packetBridge.registerOrReloadUi(uiId, uiFile);
        if (!registration.success()) {
            throw new IllegalStateException("Vanilla UI 注册失败: " + registration.message());
        }
        recordExternalUi(registration.runtimeUiId(), registration.registeredUiId());
        return new UiBinding(registration.runtimeUiId(), registration.registeredUiId());
    }

    @EventHandler
    public void onWarehouseLifecycle(ModuleLifecycleEvent event) {
        if (stopping || !"warehouse".equals(event.moduleId())) return;
        if (event.phase() == ModuleLifecycleEvent.Phase.DISABLING || event.phase() == ModuleLifecycleEvent.Phase.DISABLED) {
            if (service != null) service.materialPlanner().cancelPending();
        }
        switch (event.phase()) {
            case ENABLED, DISABLED, RELOADED, ENABLE_FAILED -> { }
            default -> { return; }
        }
        if (layoutRefreshTask != null) layoutRefreshTask.cancel();
        layoutRefreshTask = Bukkit.getScheduler().runTask(plugin, () -> {
            layoutRefreshTask = null;
            if (stopping) return;
            for (var entry : layoutUiFiles.entrySet()) {
                try {
                    if (prepareWarehouseLayout(entry.getValue())) {
                        registerPreparedFile(entry.getKey(), entry.getValue());
                    }
                } catch (Exception exception) {
                    logger.warning("更新 Vanilla 仓库布局失败: " + exception.getMessage());
                }
            }
        });
    }

    private void startBackpack() throws Exception {
        if (backpackConfiguration == null || !backpackConfiguration.enabled()) {
            logger.info("Vanilla 随身背包未启用（backpack.enabled 未开启）");
            return;
        }
        var messages = messages();
        backpack = new ExtraBackpackService(plugin, logger, packetBridge, clientBridge,
            (resource, path, overwrite) -> exportUiResource(resource, path, overwrite, moduleClassLoader()),
            backpackConfiguration, messages, vanillaItemNameBridge, currencyManager, rondoBridge, configuration.sortMode());
        backpack.setEventBusProvider(() -> getCapability(EventBusCapability.class));
        backpack.setWarehouseProvider(() -> getCapability(WarehouseAccess.class));
        backpack.setPanelProvider(() -> getCapability(WarehousePanelAccess.class));
        backpack.start();
        var accessorySlots = backpackConfiguration.mmoItemsAttributeSlots();
        if (!accessorySlots.isEmpty()) {
            unregisterMmoItemsAccessories = propBridge == null ? null : propBridge.registerMmoItemsAccessories(accessorySlots);
            if (unregisterMmoItemsAccessories == null) {
                logger.warning("MMOItems 饰品槽未接入，请确认 ArcartX、MMOItems 和 MythicLib 的接口可用。");
            }
        }
        backpackCommand = new ExtraBackpackAdminCommand(() -> backpack, messages);
        for (String uiId : backpack.registeredUiIds()) recordExternalUi(uiId, uiId);
        registerCapability(VanillaBackpackAccess.class, backpack);
        registerClientPacketHandler(PacketHandlerSpec.of((player, packetId, data) -> {
            var current = backpack;
            if (current == null || !"AXS_VANILLA_BACKPACK".equalsIgnoreCase(packetId)) return false;
            return current.handleClientPacket(player, packetId, data, getCapability(SecondaryPasswordAccess.class));
        }, -1, "AXS_VANILLA_BACKPACK", "vanilla_backpack"));
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return backpack != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
            ? new ExtraBackpackPlaceholderExpansion(plugin, () -> backpack) : null;
    }

    @Override
    protected List<String> publishedTopics() {
        return List.of(ExtraBackpackService.TOPIC_DEPOSITED, ExtraBackpackService.TOPIC_WITHDRAWN,
            ExtraBackpackService.TOPIC_UPGRADED, ExtraBackpackService.TOPIC_DESTROYED);
    }

    @Override public String commandId(){return "vanilla";}
    @Override public List<String> actions(){return List.of("help", "status", "backpack", "recipes");}
    @Override
    public boolean allowsWithoutSuiteAdmin(org.bukkit.command.CommandSender sender, String[] args) {
        return args.length > 1 && (
            "backpack".equalsIgnoreCase(args[1]) && (args.length == 2 || sender.hasPermission("arcartxsuite.vanilla.backpack.admin"))
            || "recipes".equalsIgnoreCase(args[1]) && sender.hasPermission(RecipeEditor.ADMIN));
    }
    @Override public boolean onCommand(org.bukkit.command.CommandSender sender,String label,String[] args){
        try{
            if(args.length>1&&"status".equalsIgnoreCase(args[1])){
                String message="VanillaUI "+(service==null?"未启用":"已启用，界面 "+layoutUiFiles.size());
                if(sender instanceof org.bukkit.entity.Player player&&service!=null&&service.notice(player,message))return true;
                sender.sendMessage(message);return true;
            }
            if (args.length > 1 && "backpack".equalsIgnoreCase(args[1])) {
                if (backpackCommand == null) {
                    sender.sendMessage("随身背包未启用");
                    return true;
                }
                if (args.length == 2 && sender instanceof org.bukkit.entity.Player player) {
                    backpack.open(player);
                    return true;
                }
                return backpackCommand.onCommand(sender, label, java.util.Arrays.copyOfRange(args, 1, args.length));
            }
            if (args.length > 1 && "recipes".equalsIgnoreCase(args[1])) {
                if (!sender.hasPermission(RecipeEditor.ADMIN)) sender.sendMessage("§c没有配方管理权限");
                else if (recipeEditor == null) sender.sendMessage("§c自定义配方未启用");
                else if (args.length == 3 && "reload".equalsIgnoreCase(args[2])) {
                    recipes.reload();
                    sender.sendMessage("§a自定义配方已更新");
                } else if (sender instanceof org.bukkit.entity.Player player && recipeEditor.open(player)) {
                    return true;
                } else sender.sendMessage("请放下光标上的物品后打开配方编辑器，或使用 /axs vanilla recipes reload");
                return true;
            }
            sender.sendMessage("/axs vanilla status | /axs vanilla backpack | /axs vanilla recipes");
            return true;
        }catch(Exception e){
            if(sender instanceof org.bukkit.entity.Player player&&service!=null&&service.notice(player,e.getMessage()))return true;
            sender.sendMessage("§c"+e.getMessage());return true;
        }
    }
    @Override public List<String> onTabComplete(org.bukkit.command.CommandSender sender,String[] args){
        if (args.length == 3 && "recipes".equalsIgnoreCase(args[1]) && sender.hasPermission(RecipeEditor.ADMIN)) {
            return "reload".startsWith(args[2].toLowerCase(java.util.Locale.ROOT)) ? List.of("reload") : List.of();
        }
        if (args.length >= 3 && "backpack".equalsIgnoreCase(args[1]) && backpackCommand != null) {
            return backpackCommand.onTabComplete(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        return null;
    }

}
