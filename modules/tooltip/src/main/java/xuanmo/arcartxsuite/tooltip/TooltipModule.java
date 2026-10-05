package xuanmo.arcartxsuite.tooltip;

import java.io.File;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientInitializedHandler;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.capability.TooltipDataCapable;
import xuanmo.arcartxsuite.tooltip.cache.TooltipDataCache;
import xuanmo.arcartxsuite.tooltip.command.TooltipAdminCommand;
import xuanmo.arcartxsuite.tooltip.listener.TooltipPlayerListener;
import xuanmo.arcartxsuite.tooltip.packet.TooltipPacketHandler;
import xuanmo.arcartxsuite.tooltip.service.GunListService;
import xuanmo.arcartxsuite.tooltip.service.TooltipDataCapability;
import xuanmo.arcartxsuite.tooltip.service.TooltipJsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tooltip 模块主类。
 * <p>
 * 接收客户端 Forge mod（ArcartX-Suite-Mod）采集的 tooltip 数据，
 * 缓存并提供给其他模块使用。
 * <p>
 * 核心功能：
 * <ul>
 *   <li>接收客户端 {@code AXS_TOOLTIP_DATA} 网络包，缓存 tooltip 文本行和结构化数据</li>
 *   <li>提供 {@link TooltipDataCapable} 能力，供 Chat 等模块查询</li>
 *   <li>支持将 tooltip 文本行注入物品 NBT Lore，用于聊天物品预览</li>
 * </ul>
 */
public final class TooltipModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final String GUN_LIST_UI_RESOURCE_PATH = "arcartx/ui/gun_list.yml";
    private static final String GUN_LIST_UI_FILE_PATH = "ui/gun_list.yml";

    private TooltipDataCache cache;
    private TooltipJsonParser parser;
    private TooltipDataCapability capability;
    private TooltipAdminCommand adminCommand;
    private GunListService gunListService;
    private GunListService.UiMode gunUiMode = GunListService.UiMode.AX;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("tooltip")
            .name("Tooltip")
            .version("1.4.4")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXTooltip.yml")
            .messagesFileName("messages.yml")
            .currentVersion(1)
            .build();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXTooltip.yml 配置文件缺失");
        }
        String mode = org.bukkit.configuration.file.YamlConfiguration
            .loadConfiguration(configFile)
            .getString("gun-list.mode", "ax");
        gunUiMode = "mod".equalsIgnoreCase(mode)
            ? GunListService.UiMode.MOD
            : GunListService.UiMode.AX;
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(GUN_LIST_UI_RESOURCE_PATH, GUN_LIST_UI_FILE_PATH);
        return new ModuleUiSpec(mappings, false);
    }

    @Override
    protected void startService() throws Exception {
        cache = new TooltipDataCache();
        parser = new TooltipJsonParser();
        capability = new TooltipDataCapability(cache);
        // 枪械浏览 UI：gun_list 为 ax 模式界面；模式通过 plugin message
        // 通道 axstooltip:gunui 下发（客户端初始化完成后推送，与 map 模块同机制）
        gunListService = new GunListService(plugin, packetBridge);
        gunListService.setMode(gunUiMode);
        gunListService.registerChannels();
        UiBinding gunListBinding = registerModuleUi(GUN_LIST_UI_FILE_PATH, null, true);
        gunListService.bindUi(gunListBinding.runtimeUiId());
        // reload 后给在线玩家补推模式（init 回调只在进服时触发）
        gunListService.pushModeToAll();

        adminCommand = new TooltipAdminCommand(cache, messages(), gunListService);

        // 注册跨模块能力
        registerCapability(TooltipDataCapable.class, capability);

        logger.info("Tooltip 模块已启动，等待客户端 mod 发送 tooltip 数据");
    }

    @Override
    protected void stopService() {
        if (cache != null) {
            cache.cleanup();
        }
        cache = null;
        parser = null;
        capability = null;
        adminCommand = null;
        if (gunListService != null) {
            gunListService.unregisterChannels();
        }
        gunListService = null;
    }

    @Override
    protected @NotNull List<Listener> createListeners() {
        return List.of(new TooltipPlayerListener(cache));
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        // 高优先级（数值小先执行），tooltip 数据需要尽快缓存
        return PacketHandlerSpec.of(
            new TooltipPacketHandler(cache, parser, gunListService),
            50,
            TooltipPacketHandler.PACKET_ID,
            "tooltip"
        );
    }

    @Override
    protected @Nullable ClientInitializedHandler createInitializedHandler() {
        // 客户端初始化完成后下发 UI 模式（HUD 此时才就绪可收包）
        return player -> {
            if (gunListService != null) {
                gunListService.pushMode(player);
            }
        };
    }

    // ─── ModuleCommandHandler ────────────────────────────────

    @Override
    public String commandId() {
        return "tooltip";
    }

    @Override
    public List<String> actions() {
        return adminCommand != null ? adminCommand.actions() : List.of("help", "status");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null && adminCommand.onCommand(sender, label, args);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}
