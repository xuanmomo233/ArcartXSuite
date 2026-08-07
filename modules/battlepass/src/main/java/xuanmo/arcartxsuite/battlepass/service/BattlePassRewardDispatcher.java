package xuanmo.arcartxsuite.battlepass.service;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward;

/**
 * 战令奖励发放器，负责根据奖励类型执行实际的奖励发放逻辑。
 * <p>
 * 当前支持 command 类型（以控制台身份执行命令），item 和 currency 类型尚未实现。
 * 命令执行在主线程同步调度，支持 {@code {player}} 占位符替换。
 */
public final class BattlePassRewardDispatcher {

    private final JavaPlugin plugin;
    private final Logger logger;

    /**
     * 构造奖励发放器。
     *
     * @param plugin 插件实例
     * @param logger 日志记录器
     */
    public BattlePassRewardDispatcher(JavaPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    /**
     * 向在线玩家发放指定奖励。
     *
     * @param player 目标玩家
     * @param reward 要发放的奖励
     */
    public void dispatch(Player player, BattlePassReward reward) {
        if (player == null || !player.isOnline()) return;
        String type = reward.type();
        Map<String, String> data = reward.data();
        switch (type.toLowerCase()) {
            case "command" -> dispatchCommand(player, data.getOrDefault("data", ""));
            case "item" -> logger.warning("物品奖励尚未实现: " + reward.rewardId());
            case "currency" -> logger.warning("货币奖励尚未实现: " + reward.rewardId());
            default -> logger.warning("未知奖励类型 [" + type + "]: " + reward.rewardId());
        }
    }

    private void dispatchCommand(Player player, String command) {
        if (command == null || command.isEmpty()) return;
        String formatted = command.replace("{player}", player.getName());
        AxsScheduler.runTask(plugin, () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatted);
            } catch (Exception e) {
                logger.warning("执行战令奖励命令失败: " + formatted + " | " + e.getMessage());
            }
        });
    }
}
