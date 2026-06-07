package xuanmo.arcartxsuite.mail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.command.CommandSender;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.bridge.ArcartXPacketBridge;
import xuanmo.arcartxsuite.mail.command.MailAdminCommand;
import xuanmo.arcartxsuite.mail.command.MailPlayerCommand;
import xuanmo.arcartxsuite.mail.config.MailModuleConfiguration;
import xuanmo.arcartxsuite.mail.placeholder.MailPlaceholderExpansion;
import xuanmo.arcartxsuite.mail.service.MailService;
import xuanmo.arcartxsuite.mail.storage.JdbcMailRepository;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;

public final class MailModule extends AbstractAXSModule implements ModuleCommandHandler {

    private MailAdminCommand adminCommand;

    private MailModuleConfiguration configuration;
    private MailService service;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("mail")
            .name("Mail")
            .version("1.0.2-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXMail.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        // currencies、presets 包含用户自定义货币与邮件模板预设
        return SyncPolicy.builder()
            .dynamicSection("currencies")
            .dynamicSection("presets")
            .build();
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            // storage.mode 必须是 sqlite 或 mysql
            ValidationRule.required("storage.mode", ValueType.STRING)
                .withEnum(Set.of("sqlite", "mysql")),
            // pool-size 范围 1-100
            ValidationRule.required("storage.pool-size", ValueType.INT)
                .withRange(1, 100),
            // 玩家发送附件上限至少为 1
            ValidationRule.of("player-send.max-attachments", ValueType.INT)
                .withRange(1, 64),
            // 保留天数必须为正
            ValidationRule.of("retention.completed-days", ValueType.INT)
                .withRange(1, null)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("arcartx/ui/mail_inbox.yml", "ui/mail_inbox.yml");
        mappings.put("arcartx/ui/mail_compose.yml", "ui/mail_compose.yml");
        mappings.put("arcartx/ui/mail_logs.yml", "ui/mail_logs.yml");
        mappings.put("arcartx/ui/mail_admin.yml", "ui/mail_admin.yml");
        return mappings;
    }

    @Override
    protected boolean overwriteUiFiles() {
        return configuration != null && configuration.ui().overwriteUiFiles();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXMail.yml 配置文件缺失");
        }
        configuration = MailModuleConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile), context.logger());
    }

    @Override
    protected void startService() throws Exception {
        ArcartXPacketBridge packetBridge = (ArcartXPacketBridge) context.packetBridge();
        PacketGuardAPI packetGuard = context.packetGuard();

        MailService.UiResourceExporter uiExporter = (resourcePath, relativeUiPath, overwrite) -> {
            try {
                return context.exportUiResource(resourcePath, relativeUiPath, overwrite, moduleClassLoader());
            } catch (IOException ex) {
                throw ex;
            }
        };
        MailService.BundledResourceWriter presetWriter = (resourcePath, target) -> {
            try (java.io.InputStream input = context.openProtectedResource(resourcePath, moduleClassLoader())) {
                if (input == null) {
                    throw new IOException("Bundled resource not found: " + resourcePath);
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) {
                    Files.createDirectories(parent.toPath());
                }
                try (java.io.OutputStream output = new java.io.FileOutputStream(target)) {
                    input.transferTo(output);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        };

        // 一次性迁移老的 plugins/ArcartXSuite/mail/ 目录到 data/mail/mail/
        // （configuration.presetsDirectory() 默认 "mail/presets"，整体迁移 "mail" 一次到位）
        context.migrateLegacyDirectory("mail");

        JdbcMailRepository mailRepo = new JdbcMailRepository(
            context.migrateLegacyDataFile(configuration.storage().sqliteFileName()),
            configuration.storage(), context.logger());
        service = new MailService(
            context.plugin(), context.dataFolder(), configuration,
            mailRepo,
            packetBridge, packetGuard, uiExporter, presetWriter, null,
            context.currencyManager(), context.crossServer()
        );
        service.start();
        adminCommand = new MailAdminCommand(() -> service, messages());

        // 注册 MailDispatchable capability，供其他模块调用
        context.registerCapability(MailDispatchable.class, (presetId, playerName, source) -> {
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null && player.isOnline()) {
                return service.dispatchPresetToPlayer(presetId, player, source).success();
            }
            return service.dispatchPreset(presetId, playerName, source).success();
        });

        ensureMailNotifyCardDefaults();

        context.registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "mail"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    try { return mailRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { context.logger().warning("Mail purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    try { return mailRepo.deleteAllPlayerData(); }
                    catch (Exception e) { context.logger().warning("Mail purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        context.registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "mail"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    return mailRepo.migrateData(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return mailRepo.getDescriptor();
                }
            });

        context.logger().fine(
            "Mail 模块已载入，预设=" + service.presetCount()
                + " | 存储=" + configuration.storage().dialect().configKey()
                + " | InboxUI=" + service.inboxUiId()
                + " | 跨服=" + (service.crossServerActive() ? "ON" : "OFF")
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
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        MailPlayerCommand cmd = new MailPlayerCommand(() -> service, messages());
        return Map.of("mail", (TabExecutor) cmd);
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new MailPlaceholderExpansion(context.plugin(), () -> service);
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) ->
            service != null && service.handleClientPacket(player, packetId, data);
    }

    public MailService getService() {
        return service;
    }

    public MailModuleConfiguration getConfiguration() {
        return configuration;
    }

    private void ensureMailNotifyCardDefaults() {
        org.bukkit.plugin.Plugin arcartX = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return;
        }
        File chatCardDir = new File(arcartX.getDataFolder(), "chat_card");
        if (!chatCardDir.exists()) {
            chatCardDir.mkdirs();
        }
        File target = new File(chatCardDir, "axs_mail_notify.yml");
        if (target.exists()) {
            return;
        }
        context.exportResource("mail/card/axs_mail_notify.yml", target, false);
        if (target.exists()) {
            context.logger().info("已导出邮件通知卡片模板到 ArcartX/chat_card/axs_mail_notify.yml");
        }
    }

    @Override public String commandId() { return "mail"; }
    @Override public List<String> actions() { return adminCommand != null ? adminCommand.actions() : List.of("help", "status", "reload"); }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onCommand(sender, label, args) : false;
    }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}
