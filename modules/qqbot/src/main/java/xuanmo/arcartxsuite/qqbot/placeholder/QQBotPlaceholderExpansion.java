package xuanmo.arcartxsuite.qqbot.placeholder;

import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.qqbot.service.QQBotService;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.QQBotBinding;

/**
 * QQBot 模块的 PlaceholderAPI 占位符扩展。
 * <p>
 * 关联模块：{@code qqbot}。向 PlaceholderAPI 注册 {@code %axsqqbot_*%} 系列占位符，
 * 提供连接状态、玩家绑定 QQ 号、是否已绑定、绑定名称、群数量等查询能力。
 * 通过 {@link Supplier} 延时获取 {@link QQBotService} 以适应服务未就绪场景。
 */
public final class QQBotPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<QQBotService> serviceProvider;

    /**
     * 构造占位符扩展。
     *
     * @param plugin          宿主插件实例
     * @param serviceProvider QQBotService 延时提供者
     */
    public QQBotPlaceholderExpansion(JavaPlugin plugin, Supplier<QQBotService> serviceProvider) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
    }

    /**
     * 返回占位符标识符。
     *
     * @return 固定值 {@code "axsqqbot"}
     */
    @Override
    public @NotNull String getIdentifier() {
        return "axsqqbot";
    }

    /**
     * 返回占位符作者，取插件描述中的首位作者。
     *
     * @return 作者名
     */
    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty()
            ? "ArcartXSuite" : plugin.getDescription().getAuthors().get(0);
    }

    /**
     * 返回占位符版本号。
     *
     * @return 固定值 {@code "1.0.0"}
     */
    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    /**
     * 返回是否在插件重载后持久保留此扩展。
     *
     * @return 固定返回 {@code true}
     */
    @Override
    public boolean persist() {
        return true;
    }

    /**
     * 处理占位符请求。
     * <p>
     * 支持的占位符：{@code connected}、{@code connected_display}、
     * {@code bound_qq}、{@code is_bound}、{@code bound_name}、{@code group_count}。
     *
     * @param player 触发占位符的玩家，部分占位符需要
     * @param params 占位符参数（标识符部分）
     * @return 占位符替换值，未知占位符返回 {@code null}
     */
    @Override
    @Nullable
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        QQBotService service = serviceProvider.get();
        if (service == null) return "";

        switch (params.toLowerCase()) {
            case "connected" -> {
                return service.isConnected() ? "true" : "false";
            }
            case "connected_display" -> {
                return service.isConnected() ? "已连接" : "未连接";
            }
            case "bound_qq" -> {
                if (player == null) return "";
                QQBotBinding binding = service.bindService().findByPlayer(player.getUniqueId());
                return binding != null ? String.valueOf(binding.qqId()) : "";
            }
            case "is_bound" -> {
                if (player == null) return "false";
                QQBotBinding binding = service.bindService().findByPlayer(player.getUniqueId());
                return binding != null ? "true" : "false";
            }
            case "bound_name" -> {
                if (player == null) return "";
                QQBotBinding binding = service.bindService().findByPlayer(player.getUniqueId());
                return binding != null ? binding.playerName() : "";
            }
            case "group_count" -> {
                return String.valueOf(service.configuration().groups().size());
            }
            default -> {
                return null;
            }
        }
    }
}
