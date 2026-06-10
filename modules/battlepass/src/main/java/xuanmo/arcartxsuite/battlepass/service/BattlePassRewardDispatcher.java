package xuanmo.arcartxsuite.battlepass.service;

import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward;

public final class BattlePassRewardDispatcher {

    private final JavaPlugin plugin;
    private final Logger logger;

    public BattlePassRewardDispatcher(JavaPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

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
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatted);
            } catch (Exception e) {
                logger.warning("执行战令奖励命令失败: " + formatted + " | " + e.getMessage());
            }
        });
    }
}
