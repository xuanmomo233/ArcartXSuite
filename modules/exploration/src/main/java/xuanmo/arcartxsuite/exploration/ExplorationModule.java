package xuanmo.arcartxsuite.exploration;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientInitializedHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess;
import xuanmo.arcartxsuite.api.capability.ConversationPlayable;
import xuanmo.arcartxsuite.api.capability.QuestGpsNavigable;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.config.SyncPolicy;

public final class ExplorationModule extends AbstractAXSModule {
    private static final String KEY = "AXS_EXPLORE";
    private static final String RESOURCE = "arcartx/ui/exploration_hud.yml";
    private static final String FILE = "ui/exploration/exploration_hud.yml";
    private static final String CHEST_RESOURCE = "arcartx/ui/exploration_chest.yml";
    private static final String CHEST_FILE = "ui/exploration/exploration_chest.yml";
    private static final String MODEL = "resource/model/exploration_chest.bbmodel";
    private ExplorationConfig config;
    private ExplorationService service;
    private Discoveries discoveries;
    private ExplorationChests chests;
    private boolean registeredKey;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("exploration").name("Exploration").version("3.3.7")
            .mainClass(getClass().getName()).build();
    }

    @Override
    protected ModuleConfig configSpec() {
        return ModuleConfig.builder().configFileName("ArcartXExploration.yml").messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.builder().opaqueSection("regions").build()).currentVersion(1).build();
    }

    @Override
    protected ModuleUiSpec uiSpec() {
        return new ModuleUiSpec(Map.of(RESOURCE, FILE, CHEST_RESOURCE, CHEST_FILE), false);
    }

    @Override
    protected PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of((player, packetId, data) -> {
            if (chests != null) chests.claim(player, data);
            return true;
        }, 0, "AXS_EXPLORATION", "exploration");
    }

    @Override
    protected void loadConfiguration(File configFile) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.load(Objects.requireNonNull(configFile));
        config = ExplorationConfig.load(yaml);
    }

    @Override
    protected void startService() {
        var hud = registerModuleUi(RESOURCE, FILE, "AXS:exploration_hud", true);
        if (hud.registeredUiId() == null) throw new IllegalStateException("探索 HUD 注册失败");
        var chestUi = registerModuleUi(CHEST_RESOURCE, CHEST_FILE, "AXS:exploration_chest", true);
        if (chestUi.registeredUiId() == null) throw new IllegalStateException("探索宝箱 UI 注册失败");
        exportResource("arcartx/" + MODEL, new File(dataFolder, "client/ArcartX/" + MODEL), false);
        var ady = plugin.getServer().getPluginManager().isPluginEnabled("Adyeshach") ? createAdyeshachNpcBridge() : null;
        chests = new ExplorationChests(new ChestModels(ady, plugin), packetBridge, chestUi.runtimeUiId(),
            itemStackBridge, vanillaItemNameBridge, itemRewardDispatcher, messages(), player -> AuthAccess.admitted(player.getUniqueId()));
        discoveries = new Discoveries(itemSourceRegistry, itemRewardDispatcher, () -> getCapability(QuestGpsNavigable.class),
            () -> getCapability(AbyssAdmissionAccess.class), () -> getCapability(ConversationPlayable.class), messages(), chests);
        service = new ExplorationService(config, packetBridge, hud.runtimeUiId(), discoveries, messages(),
            player -> AuthAccess.admitted(player.getUniqueId()), logger);
        registeredKey = propBridge != null && propBridge.registerClientKeyBind(KEY, "ArcartXSuite", config.defaultKey(), service::press);
        if (!registeredKey) throw new IllegalStateException("探索客户端按键注册失败");
        if (!config.worlds().isEmpty()) {
            scheduler.scheduleAtFixedRate(plugin, () -> service.tick(plugin.getServer().getOnlinePlayers()), 1, 1);
        }
    }

    @Override
    protected List<Listener> createListeners() {
        return List.of(service, chests);
    }

    @Override
    protected ClientInitializedHandler createInitializedHandler() {
        return player -> {
            if (service != null) service.hide(player);
        };
    }

    @Override
    protected void stopService() {
        if (registeredKey) propBridge.unregisterClientKeyBind(KEY);
        registeredKey = false;
        if (service != null) service.shutdown();
        if (discoveries != null) discoveries.shutdown();
        if (chests != null) chests.shutdown();
        chests = null;
        service = null;
        discoveries = null;
    }
}
