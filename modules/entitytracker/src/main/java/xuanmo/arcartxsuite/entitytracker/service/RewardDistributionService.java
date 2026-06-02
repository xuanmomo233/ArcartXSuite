package xuanmo.arcartxsuite.entitytracker.service;

import xuanmo.arcartxsuite.entitytracker.dao.RankingRewardConfigDao;
import xuanmo.arcartxsuite.entitytracker.dao.RankingRewardRecordDao;
import xuanmo.arcartxsuite.entitytracker.dao.PlayerDkpDao;
import xuanmo.arcartxsuite.entitytracker.entity.RankingRewardConfig;
import xuanmo.arcartxsuite.entitytracker.entity.RankingRewardRecord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 奖励发放服务
 */
public class RewardDistributionService {
    private final Logger logger;
    private final RankingRewardConfigDao configDao;
    private final RankingRewardRecordDao recordDao;
    private final PlayerDkpDao dkpDao;
    private final ScheduledExecutorService executorService;
    private final JavaPlugin plugin;

    // 变量替换模式
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{(\\w+)\\}");

    public RewardDistributionService(DataSource dataSource, JavaPlugin plugin) {
        this.logger = plugin.getLogger();
        this.configDao = new RankingRewardConfigDao(dataSource, plugin);
        this.recordDao = new RankingRewardRecordDao(dataSource, plugin);
        this.dkpDao = new PlayerDkpDao(dataSource, plugin);
        this.executorService = Executors.newScheduledThreadPool(3);
        this.plugin = plugin;
    }

    /**
     * 异步发放奖励
     */
    public CompletableFuture<Void> distributeRewardAsync(Integer recordId) {
        return CompletableFuture.runAsync(() -> {
            try {
                distributeReward(recordId);
            } catch (Exception e) {
                logger.severe("异步发放奖励失败: ID=" + recordId + ", 错误: " + e.getMessage());
                handleDistributionFailure(recordId, e);
            }
        }, executorService);
    }

    /**
     * 发放奖励
     */
    private void distributeReward(Integer recordId) throws SQLException {
        Optional<RankingRewardRecord> recordOpt = recordDao.findById(recordId);
        if (recordOpt.isEmpty()) {
            logger.warning("奖励记录不存在: ID=" + recordId);
            return;
        }

        RankingRewardRecord record = recordOpt.get();
        Optional<RankingRewardConfig> configOpt = configDao.findById(record.getRewardConfigId());
        if (configOpt.isEmpty()) {
            logger.warning("奖励配置不存在: ID=" + record.getRewardConfigId());
            return;
        }

        RankingRewardConfig config = configOpt.get();

        try {
            // 发放物品奖励
            if (config.getRewardItems() != null && !config.getRewardItems().isEmpty()) {
                distributeItemRewards(record, config.getRewardItems());
            }

            // 发放金钱奖励
            if (config.getRewardMoney() != null && config.getRewardMoney() > 0) {
                distributeMoneyReward(record, config.getRewardMoney());
            }

            // 发放DKP奖励
            if (config.getRewardDkp() != null && config.getRewardDkp() > 0) {
                distributeDkpReward(record, config.getRewardDkp());
            }

            // 执行命令奖励
            if (config.getRewardCommands() != null && !config.getRewardCommands().isEmpty()) {
                executeRewardCommands(record, config.getRewardCommands());
            }

            // 更新记录状态为成功
            record.setStatus("success");
            record.setIssuedTime(LocalDateTime.now());
            recordDao.update(record);

            logger.info("奖励发放成功: 玩家=" + record.getPlayerName() + ", 配置=" + config.getRewardName() + ", 奖励=" + formatRewardSummary(config));

        } catch (Exception e) {
            // 发放失败处理
            record.setStatus("failed");
            record.setFailureReason(e.getMessage());
            recordDao.update(record);
            
            throw e;
        }
    }

    /**
     * 发放物品奖励
     */
    private void distributeItemRewards(RankingRewardRecord record, String rewardItemsJson) {
        try {
            // 解析JSON格式的物品配置
            List<ItemStack> items = parseRewardItems(rewardItemsJson);
            
            Player player = Bukkit.getPlayer(java.util.UUID.fromString(record.getPlayerUuid()));
            if (player != null && player.isOnline()) {
                // 玩家在线，直接给予物品
                for (ItemStack item : items) {
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(item);
                    } else {
                        // 背包满了，掉落到地面
                        player.getWorld().dropItem(player.getLocation(), item);
                    }
                }
                player.sendMessage("§a您获得了排行榜奖励物品！");
            } else {
                // 玩家离线，存储到数据库（需要实现离线物品存储）
                storeOfflineItems(record.getPlayerUuid(), items);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("发放物品奖励失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发放金钱奖励
     */
    private void distributeMoneyReward(RankingRewardRecord record, Integer amount) {
        try {
            // 这里需要集成经济插件API
            // 示例代码，需要根据实际使用的经济插件调整
            Player player = Bukkit.getPlayer(java.util.UUID.fromString(record.getPlayerUuid()));
            if (player != null && player.isOnline()) {
                // 假设使用Vault经济系统
                if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
                    // economy.depositPlayer(player, amount);
                    player.sendMessage("§a您获得了 " + amount + " 金币的排行榜奖励！");
                } else {
                    logger.warning("未找到经济插件，无法发放金钱奖励");
                }
            } else {
                // 离线玩家金钱处理
                storeOfflineMoney(record.getPlayerUuid(), amount);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("发放金钱奖励失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发放DKP奖励
     */
    private void distributeDkpReward(RankingRewardRecord record, Integer dkpPoints) {
        try {
            dkpDao.addPoints(record.getPlayerUuid(), record.getPlayerName(), dkpPoints, 
                           "排行榜奖励: " + record.getRankingType() + " 第" + record.getRank() + "名");
            
            Player player = Bukkit.getPlayer(java.util.UUID.fromString(record.getPlayerUuid()));
            if (player != null && player.isOnline()) {
                player.sendMessage("§a您获得了 " + dkpPoints + " DKP积分的排行榜奖励！");
            }
            
        } catch (Exception e) {
            throw new RuntimeException("发放DKP奖励失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行奖励命令
     */
    private void executeRewardCommands(RankingRewardRecord record, String rewardCommandsJson) {
        try {
            List<String> commands = parseRewardCommands(rewardCommandsJson);
            
            for (String command : commands) {
                // 替换变量
                String processedCommand = replaceVariables(command, record);
                
                // 执行命令
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("执行奖励命令失败: " + e.getMessage(), e);
        }
    }

    /**
     * 替换命令中的变量
     */
    private String replaceVariables(String command, RankingRewardRecord record) {
        Matcher matcher = VARIABLE_PATTERN.matcher(command);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String variable = matcher.group(1);
            String value = getVariableValue(variable, record);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }

    /**
     * 获取变量值
     */
    private String getVariableValue(String variable, RankingRewardRecord record) {
        return switch (variable) {
            case "player" -> record.getPlayerName();
            case "player_uuid" -> record.getPlayerUuid();
            case "rank" -> String.valueOf(record.getRank());
            case "score" -> String.valueOf(record.getScore());
            case "period" -> formatPeriod(record.getPeriodStart(), record.getPeriodEnd());
            case "ranking_type" -> getRankingTypeDisplayName(record.getRankingType());
            case "boss_id" -> record.getBossId() != null ? record.getBossId() : "";
            default -> "{" + variable + "}";
        };
    }

    /**
     * 格式化时间周期
     */
    private String formatPeriod(LocalDateTime start, LocalDateTime end) {
        return start.getMonthValue() + "月" + start.getDayOfMonth() + "日-" + 
               end.getMonthValue() + "月" + end.getDayOfMonth() + "日";
    }

    /**
     * 获取排行类型显示名称
     */
    private String getRankingTypeDisplayName(String rankingType) {
        return switch (rankingType) {
            case "best_damage" -> "最高伤害";
            case "boss_damage" -> "Boss伤害";
            case "kills" -> "击杀数";
            case "participate" -> "参与数";
            default -> rankingType;
        };
    }

    /**
     * 解析物品奖励JSON
     * JSON格式: {"items":[{"item_id":"diamond_sword","item_name":"xxx","amount":1,"enchantments":["sharpness:5"],"lore":["line1"]}]}
     */
    private List<ItemStack> parseRewardItems(String json) {
        List<ItemStack> items = new java.util.ArrayList<>();
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            com.google.gson.JsonArray itemsArray = root.getAsJsonArray("items");
            if (itemsArray == null) {
                return items;
            }
            
            for (com.google.gson.JsonElement element : itemsArray) {
                com.google.gson.JsonObject itemObj = element.getAsJsonObject();
                String itemId = itemObj.get("item_id").getAsString();
                int amount = itemObj.has("amount") ? itemObj.get("amount").getAsInt() : 1;
                
                // 解析Material
                org.bukkit.Material material = org.bukkit.Material.matchMaterial(itemId.toUpperCase());
                if (material == null) {
                    logger.warning("未知物品ID: " + itemId);
                    continue;
                }
                
                ItemStack stack = new ItemStack(material, amount);
                org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
                if (meta == null) continue;
                
                // 设置显示名称
                if (itemObj.has("item_name")) {
                    meta.setDisplayName(itemObj.get("item_name").getAsString()
                        .replace('&', '\u00a7'));
                }
                
                // 设置Lore
                if (itemObj.has("lore")) {
                    com.google.gson.JsonArray loreArray = itemObj.getAsJsonArray("lore");
                    List<String> lore = new java.util.ArrayList<>();
                    for (com.google.gson.JsonElement loreElement : loreArray) {
                        lore.add(loreElement.getAsString().replace('&', '\u00a7'));
                    }
                    meta.setLore(lore);
                }
                
                // 设置附魔
                if (itemObj.has("enchantments")) {
                    com.google.gson.JsonArray enchArray = itemObj.getAsJsonArray("enchantments");
                    for (com.google.gson.JsonElement enchElement : enchArray) {
                        String enchStr = enchElement.getAsString();
                        String[] parts = enchStr.split(":");
                        if (parts.length == 2) {
                            org.bukkit.enchantments.Enchantment ench = 
                                org.bukkit.enchantments.Enchantment.getByKey(
                                    org.bukkit.NamespacedKey.minecraft(parts[0].toLowerCase()));
                            if (ench != null) {
                                meta.addEnchant(ench, Integer.parseInt(parts[1]), true);
                            }
                        }
                    }
                }
                
                stack.setItemMeta(meta);
                items.add(stack);
            }
        } catch (Exception e) {
            logger.severe("解析物品奖励JSON失败: " + e.getMessage());
        }
        return items;
    }

    /**
     * 解析命令奖励JSON
     * JSON格式: ["command1 {player}", "command2 {player}"]
     */
    private List<String> parseRewardCommands(String json) {
        List<String> commands = new java.util.ArrayList<>();
        try {
            com.google.gson.JsonArray array = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
            for (com.google.gson.JsonElement element : array) {
                commands.add(element.getAsString());
            }
        } catch (Exception e) {
            logger.severe("解析命令奖励JSON失败: " + e.getMessage());
        }
        return commands;
    }

    /**
     * 存储离线玩家物品
     */
    private void storeOfflineItems(String playerUuid, List<ItemStack> items) {
        try {
            // 将物品序列化为JSON存储
            com.google.gson.JsonObject data = new com.google.gson.JsonObject();
            com.google.gson.JsonArray itemsArray = new com.google.gson.JsonArray();
            for (ItemStack item : items) {
                com.google.gson.JsonObject itemObj = new com.google.gson.JsonObject();
                itemObj.addProperty("material", item.getType().name());
                itemObj.addProperty("amount", item.getAmount());
                if (item.hasItemMeta()) {
                    org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                    if (meta.hasDisplayName()) {
                        itemObj.addProperty("display_name", meta.getDisplayName());
                    }
                    if (meta.hasLore()) {
                        com.google.gson.JsonArray loreArr = new com.google.gson.JsonArray();
                        for (String line : meta.getLore()) {
                            loreArr.add(line);
                        }
                        itemObj.add("lore", loreArr);
                    }
                }
                itemsArray.add(itemObj);
            }
            data.add("items", itemsArray);
            
            storeOfflineReward(playerUuid, "items", data.toString());
            logger.info("存储离线玩家物品: UUID=" + playerUuid + ", 数量=" + items.size());
        } catch (Exception e) {
            logger.severe("存储离线物品失败: " + e.getMessage());
        }
    }

    /**
     * 存储离线玩家金钱
     */
    private void storeOfflineMoney(String playerUuid, Integer amount) {
        try {
            com.google.gson.JsonObject data = new com.google.gson.JsonObject();
            data.addProperty("amount", amount);
            
            storeOfflineReward(playerUuid, "money", data.toString());
            logger.info("存储离线玩家金钱: UUID=" + playerUuid + ", 金额=" + amount);
        } catch (Exception e) {
            logger.severe("存储离线金钱失败: " + e.getMessage());
        }
    }

    /**
     * 存储离线奖励到数据库
     */
    private void storeOfflineReward(String playerUuid, String rewardType, String rewardData) {
        String sql = """
            INSERT INTO offline_reward_storage (player_uuid, reward_type, reward_data, created_time)
            VALUES (?, ?, ?, ?)
            """;
        try (java.sql.Connection conn = configDao.getDataSource().getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setString(2, rewardType);
            stmt.setString(3, rewardData);
            stmt.setTimestamp(4, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();
        } catch (java.sql.SQLException e) {
            logger.severe("存储离线奖励失败: " + e.getMessage());
        }
    }

    /**
     * 格式化奖励摘要
     */
    private String formatRewardSummary(RankingRewardConfig config) {
        StringBuilder summary = new StringBuilder();
        
        if (config.getRewardMoney() != null && config.getRewardMoney() > 0) {
            summary.append("金钱:").append(config.getRewardMoney()).append(" ");
        }
        
        if (config.getRewardDkp() != null && config.getRewardDkp() > 0) {
            summary.append("DKP:").append(config.getRewardDkp()).append(" ");
        }
        
        if (config.getRewardItems() != null && !config.getRewardItems().isEmpty()) {
            summary.append("物品奖励 ");
        }
        
        if (config.getRewardCommands() != null && !config.getRewardCommands().isEmpty()) {
            summary.append("命令奖励 ");
        }
        
        return summary.toString().trim();
    }

    /**
     * 处理发放失败
     */
    private void handleDistributionFailure(Integer recordId, Exception e) {
        try {
            RankingRewardRecord record = recordDao.findById(recordId).orElse(null);
            if (record != null) {
                record.setStatus("failed");
                record.setFailureReason(e.getMessage());
                recordDao.update(record);
                
                // 检查是否需要重试
                if (record.getRetryCount() < 3) {
                    // 安排重试
                    scheduleRetry(record);
                } else {
                    logger.severe("奖励发放重试次数已达上限: ID=" + recordId);
                }
            }
        } catch (SQLException ex) {
            logger.severe("处理发放失败时发生错误: " + ex.getMessage());
        }
    }

    /**
     * 安排重试
     */
    private void scheduleRetry(RankingRewardRecord record) {
        int retryDelay = (record.getRetryCount() + 1) * 30; // 递增延迟
        
        executorService.schedule(() -> {
            try {
                record.setRetryCount(record.getRetryCount() + 1);
                record.setStatus("pending");
                record.setFailureReason(null);
                recordDao.update(record);
                
                distributeRewardAsync(record.getId());
                logger.info("重试奖励发放: ID=" + record.getId() + ", 重试次数=" + record.getRetryCount());
                
            } catch (SQLException e) {
                logger.severe("安排重试时发生错误: " + e.getMessage());
            }
        }, retryDelay, TimeUnit.MINUTES);
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
