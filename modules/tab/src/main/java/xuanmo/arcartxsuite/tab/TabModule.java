package xuanmo.arcartxsuite.tab;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.capability.TabRefreshable;
import xuanmo.arcartxsuite.api.config.ModuleConfig;

public final class TabModule extends AbstractAXSModule {

    private enum Layout {
        SIMPLE("tab-simple"),
        AVATAR("tab-avatar");

        private final String fileName;

        Layout(String fileName) {
            this.fileName = fileName;
        }
    }

    private Layout layout;
    private boolean proxyRoster;
    private String registeredHudId;
    private TabProxyRoster visibility;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("tab")
            .name("Tab")
            .version("3.3.7")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder().configFileName("ArcartXTab.yml").build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        return ModuleUiSpec.of(Map.of(
            "arcartx/ui/tab-simple.yml", "ui/tab-simple.yml",
            "arcartx/ui/tab-avatar.yml", "ui/tab-avatar.yml"
        ));
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) {
        if (configFile == null) {
            throw new IllegalStateException("Tab 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        proxyRoster = yaml.getBoolean("settings.proxy-roster", false);
        String selected = yaml.getString("settings.layout", "simple").trim().toUpperCase(Locale.ROOT);
        try {
            layout = Layout.valueOf(selected);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("settings.layout 必须为 simple 或 avatar", failure);
        }
    }

    @Override
    protected void startService() {
        String path = "ui/" + layout.fileName + ".yml";
        String id = "AXS:" + layout.fileName;
        var registration = packetBridge.registerOrReloadUi(id, new File(pluginDataFolder, path));
        if (!registration.success()) {
            throw new IllegalStateException("Tab HUD 注册失败: " + registration.message());
        }
        registeredHudId = registration.runtimeUiId();
        recordUiBinding(path, new UiBinding(registeredHudId, registration.registeredUiId()));
        if (BuildEdition.CROSS_SERVER_INCLUDED && proxyRoster && advancedCrossServerOrchestrationAllowed()) {
            visibility = TabProxyRosterFactory.create(plugin, this::advancedCrossServerOrchestrationAllowed);
            registerListener(visibility);
            registerCapability(TabRefreshable.class, visibility);
            visibility.requestGlobalRefresh("enable");
        }
    }

    @Override
    protected void stopService() {
        if (visibility != null) {
            visibility.close();
            visibility = null;
        }
        if (registeredHudId != null) {
            for (var player : Bukkit.getOnlinePlayers()) {
                packetBridge.closeUi(player, registeredHudId);
            }
            registeredHudId = null;
        }
    }
}
