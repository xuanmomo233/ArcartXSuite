package xuanmo.arcartxsuite.rgb;

import java.io.File;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.rgb.config.ArcartRgbModuleConfiguration;
import xuanmo.arcartxsuite.rgb.placeholder.ArcartRgbPlaceholderExpansion;
import xuanmo.arcartxsuite.rgb.service.ArcartRgbService;

/**
 * ArcartRGB 独立模块。
 * <p>
 * 提供渐变色 + 扫光文本渲染，通过 PlaceholderAPI 对外暴露。
 */
public final class RgbModule extends AbstractAXSModule {

    private ArcartRgbModuleConfiguration configuration;
    private ArcartRgbService service;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("rgb")
            .name("RGB")
            .version("1.0.2-beta")
            .mainClass(getClass().getName())
            .externalDepends(List.of("PlaceholderAPI"))
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXRGB.yml";
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        // entries 为渐变色条目列表，用户自由增删
        return SyncPolicy.builder()
            .dynamicSection("entries")
            .build();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXRGB.yml 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        String entriesDirRelative = yaml.getString("entries-directory", "entries");
        File entriesDirectory = new File(context.dataFolder(), entriesDirRelative);
        if (!entriesDirectory.exists()) {
            entriesDirectory.mkdirs();
        }
        File defaultEntries = new File(entriesDirectory, "default.yml");
        if (!defaultEntries.exists()) {
            context.exportResource("entries/default.yml", defaultEntries, false);
        }
        configuration = ArcartRgbModuleConfiguration.load(yaml, context.logger(), entriesDirectory);
    }

    @Override
    protected void startService() throws Exception {
        service = new ArcartRgbService(configuration, context.logger());

        context.logger().fine(
            "ArcartRGB 模块已载入，条目数: "
                + service.activeEntryCount() + "/" + service.entryCount()
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
    protected @Nullable Object createPlaceholderExpansion() {
        if (service == null) {
            return null;
        }
        return new ArcartRgbPlaceholderExpansion(context.plugin(), service);
    }

    public ArcartRgbService getService() {
        return service;
    }

    public ArcartRgbModuleConfiguration getConfiguration() {
        return configuration;
    }
}

