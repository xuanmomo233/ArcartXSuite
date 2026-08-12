package xuanmo.arcartxsuite.tooltip;

import java.io.File;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.capability.TooltipDataCapable;
import xuanmo.arcartxsuite.tooltip.cache.TooltipDataCache;
import xuanmo.arcartxsuite.tooltip.command.TooltipAdminCommand;
import xuanmo.arcartxsuite.tooltip.listener.TooltipPlayerListener;
import xuanmo.arcartxsuite.tooltip.packet.TooltipPacketHandler;
import xuanmo.arcartxsuite.tooltip.service.TooltipDataCapability;
import xuanmo.arcartxsuite.tooltip.service.TooltipJsonParser;

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

    private TooltipDataCache cache;
    private TooltipJsonParser parser;
    private TooltipDataCapability capability;
    private TooltipAdminCommand adminCommand;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("tooltip")
            .name("Tooltip")
            .version("1.0.0")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return null; // 本模块无需配置文件
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        // 无配置文件，无需处理
    }

    @Override
    protected void startService() throws Exception {
        cache = new TooltipDataCache();
        parser = new TooltipJsonParser();
        capability = new TooltipDataCapability(cache);
        adminCommand = new TooltipAdminCommand(cache, messages());

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
    }

    @Override
    protected @NotNull List<Listener> createListeners() {
        return List.of(new TooltipPlayerListener(cache));
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return new TooltipPacketHandler(cache, parser);
    }

    @Override
    protected int packetHandlerPriority() {
        // 高优先级（数值小先执行），tooltip 数据需要尽快缓存
        return 50;
    }

    @Override
    protected @Nullable String packetOwnershipPacketId() {
        return TooltipPacketHandler.PACKET_ID;
    }

    @Override
    protected @Nullable String packetGuardModuleKey() {
        return "tooltip";
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
