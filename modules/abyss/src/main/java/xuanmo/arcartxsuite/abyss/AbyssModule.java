package xuanmo.arcartxsuite.abyss;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.capability.AbyssQueryableImpl;
import xuanmo.arcartxsuite.abyss.command.AbyssPlayerCommand;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;
import xuanmo.arcartxsuite.abyss.placeholder.AbyssPlaceholderExpansion;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.capability.AbyssQueryable;
import xuanmo.arcartxsuite.api.capability.DatabaseMigratable;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable;

/**
 * Abyss 地牢副本模块。
 * <p>
 * 多实例地牢：模板世界复制→独立实例→波次怪物→脚本驱动→奖励/排行，
 * 深度接入 ArcartXSuite 宿主（脚本双系统/统一物品源/奖励发放/货币/存储/调度/UI）。
 */
public final class AbyssModule extends AbstractAXSModule implements ModuleCommandHandler {

    private AbyssModuleConfiguration configuration;
    private AbyssService service;
    private AbyssPlayerCommand playerCommand;

    @Override
    public xuanmo.arcartxsuite.api.ModuleDescriptor descriptor() {
        return xuanmo.arcartxsuite.api.ModuleDescriptor.builder("abyss")
            .name("Abyss")
            .version("1.0.0")
            .mainClass(getClass().getName())
            .externalSoftDepends(List.of("MythicMobs", "MythicBukkit",
                "FastAsyncWorldEdit", "WorldEdit", "Adyeshach", "PlaceholderAPI"))
            .build();
    }

    @Override
    protected @NotNull xuanmo.arcartxsuite.api.config.ModuleConfig configSpec() {
        return xuanmo.arcartxsuite.api.config.ModuleConfig.builder()
            .configFileName("ArcartXAbyss.yml")
            .messagesFileName("messages.yml")
            // 已移除的队伍掉落/仓库字段：显式声明废弃路径，同步时自动清理
            .syncPolicy(xuanmo.arcartxsuite.api.config.SyncPolicy.builder()
                .obsoletePath("party.storage-pages")
                .obsoletePath("party.exp-share-mode")
                .obsoletePath("party.loot-mode")
                .build())
            .build();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXAbyss.yml 配置缺失");
        }
        configuration = AbyssModuleConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile));
    }

    @Override
    protected void startService() throws Exception {
        service = new AbyssService(
            this, plugin, configuration,
            packetBridge, itemStackBridge, itemSourceRegistry,
            itemRewardDispatcher, pendingRewardService, itemMatcher,
            vanillaItemNameBridge, currencyManager, attributeBridge,
            ariaBridge, scriptConditionEvaluator, placeholderResolver,
            storageManager, scheduler, crossServer, logger, messages());
        service.setEventBusProvider(() -> getCapability(EventBusCapability.class));
        service.setLinks(new xuanmo.arcartxsuite.abyss.links.AbyssLinks(
            this::getCapability, attributeBridge, logger));
        service.start(dataFolder);
        playerCommand = new AbyssPlayerCommand(service);
        registerAbyssUis();

        // 公开能力
        registerCapability(AbyssQueryable.class, new AbyssQueryableImpl(service));
        registerCapability(xuanmo.arcartxsuite.api.capability.AbyssPartyAccess.class,
            new xuanmo.arcartxsuite.abyss.capability.AbyssPartyAccessImpl(service));
        registerCapability(PlayerDataPurgeable.class, new PlayerDataPurgeable() {
            @Override public @NotNull String moduleId() { return "abyss"; }
            @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                try {
                    return service.playerData() != null
                        ? repository().deletePlayerData(playerUuid) : -1;
                } catch (Exception e) {
                    return -1;
                }
            }
            @Override public int purgeAllPlayerData() {
                try {
                    return service.playerData() != null
                        ? repository().deleteAllPlayerData() : -1;
                } catch (Exception e) {
                    return -1;
                }
            }
        });
        registerCapability(DatabaseMigratable.class, new DatabaseMigratable() {
            @Override public @NotNull String moduleId() { return "abyss"; }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor targetDescriptor,
                boolean overwriteTarget) {
                return repository().migrateData(targetDescriptor, overwriteTarget);
            }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                return repository().getDescriptor();
            }
        });
    }

    /**
     * 将 uiSpec() 中声明的 7 个 UI 文件注册到 ArcartX 引擎。
     * 配置 id 统一规范化为 AXS: 前缀（与 UiService 的 runtimeId 一致）。
     */
    private void registerAbyssUis() {
        AbyssModuleConfiguration.UiSection ui = configuration.ui();
        if (ui == null) return;
        registerModuleUi("ui/abyss_browser.yml", axsUiId(ui.browserId()), ui.registerOnEnable());
        registerModuleUi("ui/abyss_confirm.yml", axsUiId(ui.confirmId()), ui.registerOnEnable());
        registerModuleUi("ui/abyss_lobby.yml", axsUiId(ui.lobbyId()), ui.registerOnEnable());
        registerModuleUi("ui/abyss_party.yml", axsUiId(ui.partyId()), ui.registerOnEnable());
        registerModuleUi("ui/abyss_chest.yml", axsUiId(ui.chestId()), ui.registerOnEnable());
        registerModuleUi("ui/abyss_stats.yml", axsUiId(ui.statsId()), ui.registerOnEnable());
        registerModuleUi("ui/abyss_leaderboard.yml", axsUiId(ui.leaderboardId()), ui.registerOnEnable());
        registerModuleUi("ui/abyss_hud.yml", axsUiId(ui.hudId()), ui.registerOnEnable());
    }

    /** 规范化 UI id：缺省补 AXS: 前缀，已带前缀则原样返回。 */
    private static String axsUiId(String id) {
        if (id == null || id.isBlank()) return null;
        String v = id.trim();
        return v.startsWith("AXS:") ? v : "AXS:" + v;
    }

    private xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository repository() {
        return service != null ? service.repository() : null;
    }

    /**
     * 导出 jar 内资源到数据目录（示例地牢用，封装基类 protected exportResource）。
     */
    public void exportModuleResource(String resourcePath, File target, boolean overwrite) {
        exportResource(resourcePath, target, overwrite);
    }

    @Override
    protected void stopService() {
        playerCommand = null;
        if (service != null) {
            service.shutdown();
            service = null;
        }
    }

    // ═══ 生命周期钩子 ═══

    @Override
    protected @NotNull List<Listener> createListeners() {
        return service != null ? service.moduleListeners() : List.of();
    }

    /**
     * 玩家命令绑定（在 startService 之前注册，延迟引用 playerCommand，
     * 服务未启动时给玩家友好提示而非命令不存在）。
     */
    @Override
    protected @NotNull Map<String, org.bukkit.command.TabExecutor> commandBindings() {
        org.bukkit.command.TabExecutor abyss = new org.bukkit.command.TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender sender,
                                     @NotNull org.bukkit.command.Command command,
                                     @NotNull String label, @NotNull String[] args) {
                if (playerCommand == null) {
                    sender.sendMessage("§c[abyss] 模块未就绪");
                    return true;
                }
                return playerCommand.onCommand(sender, command, label, args);
            }
            @Override
            public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                        @NotNull org.bukkit.command.Command command,
                                                        @NotNull String label, @NotNull String[] args) {
                return playerCommand != null
                    ? playerCommand.onTabComplete(sender, command, label, args) : List.of();
            }
        };
        org.bukkit.command.TabExecutor party = new org.bukkit.command.TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender sender,
                                     @NotNull org.bukkit.command.Command command,
                                     @NotNull String label, @NotNull String[] args) {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("该命令仅玩家可执行");
                    return true;
                }
                if (playerCommand == null) {
                    sender.sendMessage("§c[abyss] 模块未就绪");
                    return true;
                }
                return playerCommand.onPartyCommand(p, args, label);
            }
            @Override
            public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                        @NotNull org.bukkit.command.Command command,
                                                        @NotNull String label, @NotNull String[] args) {
                return playerCommand != null
                    ? playerCommand.partyTabComplete(sender, args) : List.of();
            }
        };
        return Map.of("abyss", abyss, "party", party);
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new AbyssPlaceholderExpansion(plugin, () -> service);
    }

    @Override
    protected @NotNull xuanmo.arcartxsuite.api.PacketHandlerSpec createPacketHandlerSpec() {
        return xuanmo.arcartxsuite.api.PacketHandlerSpec.of(
            (player, packetId, data) ->
                service != null && service.handleClientPacket(player, packetId, data));
    }

    @Override
    protected @NotNull List<String> publishedTopics() {
        return List.of(
            "axs.abyss.enter",
            "axs.abyss.start",
            "axs.abyss.complete",
            "axs.abyss.fail",
            "axs.abyss.leave",
            "axs.abyss.player_death",
            "axs.abyss.player_enter",
            "axs.abyss.player_complete",
            "axs.abyss.player_fail",
            "axs.abyss.mob_kill",
            "axs.abyss.mob_group_start",
            "axs.abyss.mob_group_complete",
            "axs.abyss.party_created",
            "axs.abyss.party_member_join",
            "axs.abyss.party_member_leave",
            "axs.abyss.party_disbanded",
            "axs.abyss.party_leader_change",
            "axs.abyss.room_enter",
            "axs.abyss.room_clear",
            "axs.abyss.floor_enter",
            "axs.abyss.floor_clear"
        );
    }

    @Override
    protected @NotNull xuanmo.arcartxsuite.api.ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new java.util.LinkedHashMap<>();
        mappings.put("arcartx/ui/abyss_browser.yml", "ui/abyss_browser.yml");
        mappings.put("arcartx/ui/abyss_confirm.yml", "ui/abyss_confirm.yml");
        mappings.put("arcartx/ui/abyss_lobby.yml", "ui/abyss_lobby.yml");
        mappings.put("arcartx/ui/abyss_party.yml", "ui/abyss_party.yml");
        mappings.put("arcartx/ui/abyss_chest.yml", "ui/abyss_chest.yml");
        mappings.put("arcartx/ui/abyss_stats.yml", "ui/abyss_stats.yml");
        mappings.put("arcartx/ui/abyss_leaderboard.yml", "ui/abyss_leaderboard.yml");
        mappings.put("arcartx/ui/abyss_hud.yml", "ui/abyss_hud.yml");
        boolean overwrite = configuration != null
            && configuration.ui() != null && configuration.ui().overwriteUiFiles();
        return new xuanmo.arcartxsuite.api.ModuleUiSpec(mappings, overwrite);
    }

    // ═══ ModuleCommandHandler（/axs abyss）═══

    @Override public String commandId() { return "abyss"; }

    @Override public List<String> actions() {
        return service != null && service.adminCommand() != null
            ? service.adminCommand().actions() : List.of("help", "status");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label,
                             @NotNull String[] args) {
        return service != null && service.adminCommand() != null
            && service.adminCommand().onCommand(sender, label, args);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull String[] args) {
        return service != null && service.adminCommand() != null
            ? service.adminCommand().onTabComplete(sender, args) : null;
    }
}
