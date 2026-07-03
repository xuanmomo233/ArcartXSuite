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
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI;
import xuanmo.arcartxsuite.prop.config.PropDefinition;
import xuanmo.arcartxsuite.prop.config.PropDefinitionLoader;
import xuanmo.arcartxsuite.prop.config.PropKeyMappingConfiguration;
import xuanmo.arcartxsuite.prop.config.PropLanguageConfiguration;
import xuanmo.arcartxsuite.prop.config.PropModuleConfiguration;
import xuanmo.arcartxsuite.prop.service.PropService;

public final class PropModule extends AbstractAXSModule implements ModuleCommandHandler {

    private PropAdminCommand adminCommand;

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
        if (propBridge == null || !propBridge.isAvailable()) {
            throw new IllegalStateException("Prop 模块需要 ArcartX API 桥接，当前不可用");
        }

        ensureDefaults();
        PropKeyMappingConfiguration keyMapping = PropKeyMappingConfiguration.load(
            YamlConfiguration.loadConfiguration(new File(dataFolder, "key.yml")));
        PropLanguageConfiguration language = PropLanguageConfiguration.load(
            YamlConfiguration.loadConfiguration(new File(dataFolder, "language.yml")));
        Map<String, PropDefinition> definitions = PropDefinitionLoader.load(
            new File(dataFolder, "props"), logger);

        service = new PropService(plugin, logger, configuration, propBridge, keyMapping, language, definitions, attributeBridge);
        service.setEventBusProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        service.start();
        adminCommand = new PropAdminCommand(() -> service, messages());

        logger.fine(
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
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IOException("无法创建 Prop 数据目录: " + dataFolder.getAbsolutePath());
        }
        File propsDir = new File(dataFolder, "props");
        if (!propsDir.exists() && !propsDir.mkdirs()) {
            throw new IOException("无法创建 Prop 道具目录: " + propsDir.getAbsolutePath());
        }
        exportIfMissing("prop/key.yml", new File(dataFolder, "key.yml"));
        exportIfMissing("prop/language.yml", new File(dataFolder, "language.yml"));
        // 若道具目录已有用户自定义内容，不再重复导出默认示例
        File[] existing = propsDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existing == null || existing.length == 0) {
            exportIfMissing("prop/props/道具示例.yml", new File(dataFolder, "props/道具示例.yml"));
        }
    }

    private void exportIfMissing(String resourcePath, File target) {
        if (target.exists()) {
            return;
        }
        exportResource(resourcePath, target, false);
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


