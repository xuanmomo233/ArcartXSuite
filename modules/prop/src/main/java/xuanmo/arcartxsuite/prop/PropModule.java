package xuanmo.arcartxsuite.prop;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientInitializedHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.prop.command.PropAdminCommand;
import xuanmo.arcartxsuite.bridge.ArcartXPropBridge;
import xuanmo.arcartxsuite.prop.config.PropDefinition;
import xuanmo.arcartxsuite.prop.config.PropDefinitionLoader;
import xuanmo.arcartxsuite.prop.config.PropKeyMappingConfiguration;
import xuanmo.arcartxsuite.prop.config.PropLanguageConfiguration;
import xuanmo.arcartxsuite.prop.config.PropModuleConfiguration;
import xuanmo.arcartxsuite.prop.service.PropService;

public final class PropModule extends AbstractAXSModule implements ModuleCommandHandler {

    private PropAdminCommand adminCommand;

    private static final String PROP_DATA_DIRECTORY = "prop";
    private static final String PROP_DEFINITIONS_DIRECTORY = "prop/props";

    private PropModuleConfiguration configuration;
    private PropService service;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("prop")
            .name("Prop")
            .version("1.0.2-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXProp.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXProp.yml 配置文件缺失");
        }
        configuration = PropModuleConfiguration.load(YamlConfiguration.loadConfiguration(configFile));
    }

    @Override
    protected void startService() throws Exception {
        ArcartXPropBridge propBridge = (ArcartXPropBridge) context.propBridge();
        if (propBridge == null || !propBridge.isAvailable()) {
            throw new IllegalStateException("Prop 模块需要 ArcartX API 桥接，当前不可用");
        }

        ensureDefaults();
        File dataDir = new File(context.dataFolder(), PROP_DATA_DIRECTORY);
        PropKeyMappingConfiguration keyMapping = PropKeyMappingConfiguration.load(
            YamlConfiguration.loadConfiguration(new File(dataDir, "key.yml")));
        PropLanguageConfiguration language = PropLanguageConfiguration.load(
            YamlConfiguration.loadConfiguration(new File(dataDir, "language.yml")));
        Map<String, PropDefinition> definitions = PropDefinitionLoader.load(
            new File(dataDir, "props"), context.logger());

        service = new PropService(context.plugin(), configuration, propBridge, keyMapping, language, definitions, context.attributeBridge());
        service.setEventBusProvider(() -> context.getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        service.start();
        adminCommand = new PropAdminCommand(() -> service, messages());

        context.logger().fine(
            "Prop 模块已载入，props=" + service.propCount()
                + " | keys=" + service.registeredKeyCount()
                + " | category=" + service.keyCategory()
                + " | mythiclib=" + service.mythicLibHooked()
                + " | debug=" + configuration.debug()
        );
    }

    @Override
    protected void stopService() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        configuration = null;
    }

    @Override
    protected @Nullable ClientInitializedHandler createInitializedHandler() {
        return player -> {
            if (service != null) {
                service.handleClientInitialized(player);
            }
        };
    }

    public PropService getService() {
        return service;
    }

    public PropModuleConfiguration getConfiguration() {
        return configuration;
    }

    private void ensureDefaults() throws IOException {
        // 一次性迁移老路径 plugins/ArcartXSuite/prop/ -> data/prop/prop/
        // （PROP_DATA_DIRECTORY 历史值就叫 "prop"，因此最终路径是 data/prop/prop/）
        context.migrateLegacyDirectory(PROP_DATA_DIRECTORY);

        File dataDir = new File(context.dataFolder(), PROP_DATA_DIRECTORY);
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new IOException("无法创建 Prop 数据目录: " + dataDir.getAbsolutePath());
        }
        File propsDir = new File(context.dataFolder(), PROP_DEFINITIONS_DIRECTORY);
        if (!propsDir.exists() && !propsDir.mkdirs()) {
            throw new IOException("无法创建 Prop 道具目录: " + propsDir.getAbsolutePath());
        }
        exportIfMissing("prop/key.yml");
        exportIfMissing("prop/language.yml");
        // 若道具目录已有用户自定义内容，不再重复导出默认示例
        File[] existing = propsDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existing == null || existing.length == 0) {
            exportIfMissing("prop/props/道具示例.yml");
        }
    }

    private void exportIfMissing(String relativePath) {
        File target = new File(context.dataFolder(), relativePath);
        if (target.exists()) {
            return;
        }
        context.exportResource(relativePath, target, false);
    }

    @Override public String commandId() { return "prop"; }
    @Override public List<String> actions() { return adminCommand != null ? adminCommand.actions() : List.of("help", "status", "reload"); }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onCommand(sender, label, args) : false;
    }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}
