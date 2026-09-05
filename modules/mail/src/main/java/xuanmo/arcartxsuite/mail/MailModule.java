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
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.mail.MailAttachmentSpec;
import xuanmo.arcartxsuite.api.capability.mail.MailPresetRequest;
import xuanmo.arcartxsuite.api.capability.mail.MailSendRequest;
import xuanmo.arcartxsuite.api.capability.mail.MailSendResult;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.mail.command.MailAdminCommand;
import xuanmo.arcartxsuite.mail.command.MailPlayerCommand;
import xuanmo.arcartxsuite.mail.config.MailModuleConfiguration;
import xuanmo.arcartxsuite.mail.placeholder.MailPlaceholderExpansion;
import xuanmo.arcartxsuite.mail.service.MailService;
import xuanmo.arcartxsuite.mail.storage.JdbcMailRepository;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.mail.AxsMailApi;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.mail.service.AxsMailServiceAdapter;

/**
 * 邮件模块主类，继承 {@link AbstractAXSModule} 并实现 {@link ModuleCommandHandler}。
 * <p>
 * 关联模块：mail。负责配置加载、服务启动/停止、UI 资源导出、命令绑定、
 * PAPI 占位符注册，以及 {@link xuanmo.arcartxsuite.api.capability.MailDispatchable}、
 * {@link xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable}、
 * {@link xuanmo.arcartxsuite.api.capability.DatabaseMigratable} 能力注册。
 */
public final class MailModule extends AbstractAXSModule implements ModuleCommandHandler {

    private MailAdminCommand adminCommand;

    private MailModuleConfiguration configuration;
    private MailService service;
    private JdbcMailRepository repository;
    private AxsMailServiceAdapter mailApiAdapter;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("mail")
            .name("Mail")
            .version("1.4.4")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXMail.yml")
            .messagesFileName("messages.yml")
            // currencies、presets 包含用户自定义货币与邮件模板预设
            .syncPolicy(SyncPolicy.builder().dynamicSection("presets").build())
            .currentVersion(2)
            .validations(List.of(
                // storage.shared 必须是布尔值
                ValidationRule.required("storage.shared", ValueType.BOOLEAN),
                // pool-size 范围 1-100（仅在 shared=false 时需要）
                ValidationRule.of("storage.pool-size", ValueType.INT)
                    .withRange(1, 100),
                // 玩家发送附件上限至少为 1
                ValidationRule.of("player-send.max-attachments", ValueType.INT)
                    .withRange(1, 64),
                // 管理员预设附件上限至少为 1
                ValidationRule.of("presets.admin-max-attachments", ValueType.INT)
                    .withRange(1, 64),
                // 保留天数必须为正
                ValidationRule.of("retention.completed-days", ValueType.INT)
                    .withRange(1, null)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("arcartx/ui/mail_inbox.yml", "ui/mail_inbox.yml");
        mappings.put("arcartx/ui/mail_compose.yml", "ui/mail_compose.yml");
        mappings.put("arcartx/ui/mail_logs.yml", "ui/mail_logs.yml");
        mappings.put("arcartx/ui/mail_admin.yml", "ui/mail_admin.yml");
        return new ModuleUiSpec(mappings, configuration != null && configuration.ui().overwriteUiFiles());
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXMail.yml 配置文件缺失");
        }
        configuration = MailModuleConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile), logger);
    }

    @Override
    protected void startService() throws Exception {

        MailService.UiResourceExporter uiExporter = (resourcePath, relativeUiPath, overwrite) -> {
            try {
                return exportUiResource(resourcePath, relativeUiPath, overwrite, moduleClassLoader());
            } catch (IOException ex) {
                throw ex;
            }
        };
        MailService.BundledResourceWriter presetWriter = (resourcePath, target) -> {
            try (java.io.InputStream input = openResource(resourcePath, moduleClassLoader())) {
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

        // 统一通过 StorageManager 解析数据源（共享或自建），连接池生命周期由本体管控
        xuanmo.arcartxsuite.api.storage.StorageDescriptor mailDesc;
        javax.sql.DataSource mailDs;
        if (configuration.storage().hasOverride()) {
            // 自建模式：模块配置了独立 storage 节
            mailDesc = configuration.storage().toDescriptor();
            mailDs = storageManager.resolveModuleDataSource("mail", null, dataFolder, mailDesc);
        } else {
            // 共享模式：使用本体统一数据源
            mailDesc = storageManager.getDescriptor().withTablePrefix("");
            mailDs = storageManager.resolveModuleDataSource("mail",
                configuration.storage().sqliteFileName(), dataFolder, null);
        }
        JdbcMailRepository mailRepo = new JdbcMailRepository(mailDs, mailDesc, dataFolder, logger);
        repository = mailRepo;
        service = new MailService(
            plugin, logger, dataFolder, configuration,
            mailRepo,
            packetBridge, packetGuard, uiExporter, presetWriter, null,
            currencyManager, crossServer, messages(),
            () -> getCapability(SecondaryPasswordAccess.class),
            itemSourceRegistry,
            vanillaItemNameBridge,
            new xuanmo.arcartxsuite.api.icon.IconResolver(itemStackBridge, itemSourceRegistry)
        );
        service.start();
        // 将 Service 手动注册的 UI 纳入基类生命周期管理（onDisable 时兜底注销）
        for (String uiId : service.registeredUiIds()) {
            recordExternalUi(uiId, uiId);
        }
        adminCommand = new MailAdminCommand(() -> service, messages());

        // 注册 MailDispatchable capability，供其他模块调用
        registerCapability(MailDispatchable.class, new MailDispatchable() {
            @Override
            public MailSendResult sendMail(@NotNull MailSendRequest request) {
                try {
                    java.util.List<xuanmo.arcartxsuite.mail.model.MailAttachment> attachments = new java.util.ArrayList<>();
                    int sortOrder = 0;
                    for (MailAttachmentSpec spec : request.attachments()) {
                        if (spec.type() == MailAttachmentSpec.Type.ITEM) {
                            org.bukkit.inventory.ItemStack item = spec.itemStack();
                            if (item == null) continue;
                            String itemData = xuanmo.arcartxsuite.mail.util.MailItemSerializer.serialize(item);
                            attachments.add(new xuanmo.arcartxsuite.mail.model.MailAttachment(
                                0L, sortOrder++, xuanmo.arcartxsuite.mail.model.MailAttachmentType.ITEM,
                                itemData, "", 0.0D, ""));
                        } else if (spec.type() == MailAttachmentSpec.Type.CURRENCY) {
                            String currencyId = spec.currencyId();
                            if (currencyId == null || currencyId.isBlank()) continue;
                            double amount = spec.amount();
                            if (amount <= 0) continue;
                            xuanmo.arcartxsuite.mail.model.MailAttachmentType type =
                                "money".equals(currencyId)
                                    ? xuanmo.arcartxsuite.mail.model.MailAttachmentType.VAULT
                                    : xuanmo.arcartxsuite.mail.model.MailAttachmentType.CURRENCY;
                            attachments.add(new xuanmo.arcartxsuite.mail.model.MailAttachment(
                                0L, sortOrder++, type, "", currencyId, amount, ""));
                        }
                    }
                    String logContent = buildLogContent(request.sourceModule(), request.sourceDetail());
                    java.time.Instant expiresAt = request.expiresAt() != null
                        ? request.expiresAt()
                        : java.time.Instant.now().plus(java.time.Duration.ofDays(30));
                    String senderName = request.senderName() != null && !request.senderName().isBlank()
                        ? request.senderName() : "系统";
                    var result = service.sendSystemMail(
                        request.playerUuid(),
                        logContent,
                        request.subject(),
                        request.body(),
                        attachments,
                        java.util.List.of(),
                        expiresAt,
                        senderName
                    );
                    return result.success()
                        ? MailSendResult.ok()
                        : MailSendResult.failure(result.message());
                } catch (IOException e) {
                    logger.warning("sendMail 物品序列化失败: " + e.getMessage());
                    return MailSendResult.failure("物品序列化失败: " + e.getMessage());
                } catch (Exception e) {
                    logger.warning("sendMail 发送失败: " + e.getMessage());
                    return MailSendResult.failure("发送失败: " + e.getMessage());
                }
            }

            @Override
            public MailSendResult dispatchPreset(@NotNull MailPresetRequest request) {
                try {
                    String actorName = buildLogContent(request.sourceModule(), request.sourceDetail());
                    Player player = Bukkit.getPlayerExact(request.playerName());
                    var result = (player != null && player.isOnline())
                        ? service.dispatchPresetToPlayer(request.presetId(), player, actorName)
                        : service.dispatchPreset(request.presetId(), request.playerName(), actorName);
                    return result.success()
                        ? MailSendResult.ok()
                        : MailSendResult.failure(result.message());
                } catch (Exception e) {
                    logger.warning("dispatchPreset 发送失败: " + e.getMessage());
                    return MailSendResult.failure("发送失败: " + e.getMessage());
                }
            }

            private String buildLogContent(@NotNull String sourceModule, @Nullable String sourceDetail) {
                return sourceDetail != null && !sourceDetail.isBlank()
                    ? sourceModule + ":" + sourceDetail
                    : sourceModule;
            }
        });

        ensureMailNotifyCardDefaults();

        registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "mail"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    try { return mailRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { logger.warning("Mail purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    try { return mailRepo.deleteAllPlayerData(); }
                    catch (Exception e) { logger.warning("Mail purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
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

        // 注册公开 API（ServicesManager + 静态入口），供外部插件调用
        mailApiAdapter = new AxsMailServiceAdapter(plugin, service, logger);
        org.bukkit.plugin.RegisteredServiceProvider<AxsMailService> existingRegistration =
            plugin.getServer().getServicesManager().getRegistration(AxsMailService.class);
        if (existingRegistration != null) {
            plugin.getServer().getServicesManager().unregister(AxsMailService.class, existingRegistration.getProvider());
        }
        plugin.getServer().getServicesManager().register(AxsMailService.class, mailApiAdapter, plugin, org.bukkit.plugin.ServicePriority.Normal);
        AxsMailApi.setService(mailApiAdapter);
        logger.info("Mail 公开 API 已注册（AxsMailApi / ServicesManager）");

        logger.fine(
            "Mail 模块已载入，预设=" + service.presetCount()
                + " | 存储=" + configuration.storage().mode()
                + " | InboxUI=" + service.inboxUiId()
                + " | 跨服=" + (service.crossServerActive() ? "ON" : "OFF")
        );
    }

    @Override
    protected void stopService() {
        // 注销公开 API
        if (mailApiAdapter != null) {
            plugin.getServer().getServicesManager().unregister(AxsMailService.class, mailApiAdapter);
            AxsMailApi.setService(null);
            mailApiAdapter = null;
        }
        if (service != null) {
            service.shutdown();
            service = null;
        }
        if (repository != null) {
            storageManager.closeModuleDataSource("mail");
            repository = null;
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
        return new MailPlaceholderExpansion(plugin, () -> service);
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of(
            (player, packetId, data) -> service != null && service.handleClientPacket(player, packetId, data),
            0,
            "AXS_MAIL",
            "mail"
        );
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
        exportResource("mail/card/axs_mail_notify.yml", target, false);
        if (target.exists()) {
            logger.info("已导出邮件通知卡片模板到 ArcartX/chat_card/axs_mail_notify.yml");
        }
    }

    @Override public String commandId() { return "mail"; }
    @Override public List<String> actions() { return adminCommand != null ? adminCommand.actions() : List.of("help", "status"); }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onCommand(sender, label, args) : false;
    }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}

