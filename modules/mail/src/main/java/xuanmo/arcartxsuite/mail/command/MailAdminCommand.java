package xuanmo.arcartxsuite.mail.command;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.mail.model.MailCdkDefinition;
import xuanmo.arcartxsuite.mail.model.MailOperationResult;
import xuanmo.arcartxsuite.mail.model.MailPresetDefinition;
import xuanmo.arcartxsuite.mail.service.MailService;

public final class MailAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "reload", "open", "preset", "cdk", "admin");
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smhd])$", Pattern.CASE_INSENSITIVE);

    private final Supplier<MailService> serviceProvider;
    private final MessageProvider messages;

    public MailAdminCommand(Supplier<MailService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override public String commandId() { return "mail"; }
    @Override public List<String> actions() { return ACTIONS; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "reload" -> sender.sendMessage(fullMsg("common.reload-hint", label));
            case "open" -> handleOpen(sender, args);
            case "preset" -> handlePreset(sender, args);
            case "cdk" -> handleCdk(sender, args);
            case "admin" -> handleAdmin(sender);
            default -> sender.sendMessage(fullMsg("common.unknown", label));
        }
        return true;
    }

    private void handleAdmin(CommandSender sender) {
        MailService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(fullMsg("common.only-player"));
            return;
        }
        svc.openAdminUi(player);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        MailService svc = serviceProvider.get();
        if (args.length == 3) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "preset" -> filter(List.of("list", "send", "info", "delete", "reload"), args[2]);
                case "cdk" -> filter(List.of("create", "info", "list", "delete"), args[2]);
                case "open" -> null; // player names
                default -> null;
            };
        }
        if (args.length == 4 && "preset".equalsIgnoreCase(args[1]) && svc != null) {
            String sub = args[2].toLowerCase(Locale.ROOT);
            if ("send".equals(sub) || "info".equals(sub) || "delete".equals(sub)) {
                return filter(svc.presetIds(), args[3]);
            }
        }
        if (args.length == 5 && "preset".equalsIgnoreCase(args[1]) && "send".equalsIgnoreCase(args[2])) {
            return null; // 目标玩家
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " mail";
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(fullMsg("help.status", cmd));
        sender.sendMessage(fullMsg("help.open", cmd));
        sender.sendMessage(fullMsg("help.preset.list", cmd));
        sender.sendMessage(fullMsg("help.preset.send", cmd));
        sender.sendMessage(fullMsg("help.preset.info", cmd));
        sender.sendMessage(fullMsg("help.preset.delete", cmd));
        sender.sendMessage(fullMsg("help.preset.reload", cmd));
        sender.sendMessage(fullMsg("help.admin", cmd));
        sender.sendMessage(fullMsg("help.cdk.create", cmd));
        sender.sendMessage(fullMsg("help.cdk.info", cmd));
        sender.sendMessage(fullMsg("help.cdk.list", cmd));
        sender.sendMessage(fullMsg("help.cdk.delete", cmd));
    }

    private void sendStatus(CommandSender sender) {
        MailService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        sender.sendMessage(fullMsg("status.title"));
        sender.sendMessage(fullMsg("status.preset", svc.presetCount()));
        sender.sendMessage(fullMsg("status.cross-server",
            svc.crossServerActive() ? messages.get("status.cross-server-enabled") : messages.get("status.cross-server-disabled")));
        sender.sendMessage(fullMsg("status.compose", svc.composeSessionCount()));
    }

    private void handleOpen(CommandSender sender, String[] args) {
        MailService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.open.usage")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { sender.sendMessage(fullMsg("open.not-online", args[2])); return; }
        MailOperationResult result = svc.openInbox(target);
        sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void handlePreset(CommandSender sender, String[] args) {
        MailService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.preset.usage"));
            return;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "list" -> handlePresetList(sender, svc);
            case "send" -> {
                if (args.length < 5) {
                    sender.sendMessage(fullMsg("admin.preset.send-usage"));
                    return;
                }
                MailOperationResult result = svc.dispatchPreset(args[3], args[4], sender.getName());
                sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
            }
            case "info" -> {
                if (args.length < 4) {
                    sender.sendMessage(fullMsg("admin.preset.info-usage"));
                    return;
                }
                handlePresetInfo(sender, svc, args[3]);
            }
            case "delete" -> {
                if (args.length < 4) {
                    sender.sendMessage(fullMsg("admin.preset.delete-usage"));
                    return;
                }
                MailOperationResult result = svc.deletePreset(args[3]);
                sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
            }
            case "reload" -> {
                MailOperationResult result = svc.reloadPresets();
                sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
            }
            default -> sender.sendMessage(fullMsg("admin.preset.sub-list"));
        }
    }

    private void handlePresetList(CommandSender sender, MailService svc) {
        List<MailPresetDefinition> definitions = svc.getPresetDefinitions();
        if (definitions.isEmpty()) {
            sender.sendMessage(fullMsg("admin.preset.empty"));
            return;
        }
        sender.sendMessage(fullMsg("admin.preset.list-title", definitions.size()));
        for (MailPresetDefinition def : definitions) {
            String check = def.enabled() ? messages.get("admin.preset.enabled-marker") : messages.get("admin.preset.disabled-marker");
            int attCount = def.attachments() != null ? def.attachments().size() : 0;
            int cmdCount = def.claimCommands() != null ? def.claimCommands().size() : 0;
            sender.sendMessage(fullMsg("admin.preset.item-format", check, def.id(), ChatColor.translateAlternateColorCodes('&', def.displayName()), attCount, cmdCount));
        }
    }

    private void handlePresetInfo(CommandSender sender, MailService svc, String presetId) {
        MailPresetDefinition def = svc.getPreset(presetId);
        if (def == null) {
            sender.sendMessage(fullMsg("admin.preset.not-found", presetId));
            return;
        }
        sender.sendMessage(fullMsg("admin.preset.info-title", def.id()));
        sender.sendMessage(fullMsg("admin.preset.info-enabled", def.enabled()));
        sender.sendMessage(fullMsg("admin.preset.info-display", ChatColor.translateAlternateColorCodes('&', def.displayName())));
        sender.sendMessage(fullMsg("admin.preset.info-subject", def.subject()));
        String displayBody = def.body().length() > 50 ? def.body().substring(0, 50) + "..." : def.body();
        sender.sendMessage(fullMsg("admin.preset.info-body", displayBody));
        sender.sendMessage(fullMsg("admin.preset.info-expires", def.expiresAfter() != null ? def.expiresAfter().toDays() + " 天" : messages.get("admin.preset.info-expires-default")));
        sender.sendMessage(fullMsg("admin.preset.info-attachments", def.attachments() != null ? def.attachments().size() : 0));
        sender.sendMessage(fullMsg("admin.preset.info-commands", def.claimCommands() != null ? def.claimCommands().size() : 0));
        sender.sendMessage(fullMsg("admin.preset.info-conditions", def.claimConditions() != null ? def.claimConditions().size() : 0));
        sender.sendMessage(fullMsg("admin.preset.info-cdks", def.cdks() != null ? def.cdks().size() : 0));
    }

    private void handleCdk(CommandSender sender, String[] args) {
        MailService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.cdk.usage")); return; }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "create" -> handleCdkCreate(sender, svc, args);
            case "info" -> handleCdkInfo(sender, svc, args);
            case "list" -> handleCdkList(sender, svc, args);
            case "delete" -> handleCdkDelete(sender, svc, args);
            default -> sender.sendMessage(fullMsg("admin.cdk.sub-list"));
        }
    }

    private void handleCdkCreate(CommandSender sender, MailService svc, String[] args) {
        if (args.length < 7) {
            sender.sendMessage(fullMsg("admin.cdk.create-usage"));
            return;
        }
        int maxClaims;
        try { maxClaims = Integer.parseInt(args[5]); } catch (NumberFormatException e) {
            sender.sendMessage(fullMsg("admin.cdk.invalid-claims", args[5])); return;
        }
        Instant expiresAt = parseTtl(args[6]);
        MailOperationResult result = svc.createCdk(args[3], args[4], maxClaims, expiresAt, sender.getName());
        sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void handleCdkInfo(CommandSender sender, MailService svc, String[] args) {
        if (args.length < 4) { sender.sendMessage(fullMsg("admin.cdk.info-usage")); return; }
        Optional<MailCdkDefinition> opt = svc.loadCdk(args[3]);
        if (opt.isEmpty()) { sender.sendMessage(fullMsg("admin.cdk.not-found", args[3])); return; }
        MailCdkDefinition cdk = opt.get();
        sender.sendMessage(fullMsg("admin.cdk.info-title", cdk.code()));
        sender.sendMessage(fullMsg("admin.cdk.info-preset", cdk.presetId()));
        sender.sendMessage(fullMsg("admin.cdk.info-claims", cdk.claimedCount(), cdk.maxClaims()));
        sender.sendMessage(fullMsg("admin.cdk.info-expires", cdk.expiresAt() == null ? messages.get("admin.cdk.info-expires-never") : cdk.expiresAt().toString()));
        sender.sendMessage(fullMsg("admin.cdk.info-enabled", cdk.enabled()));
    }

    private void handleCdkList(CommandSender sender, MailService svc, String[] args) {
        int page = args.length >= 4 ? parsePageNumber(args[3]) : 1;
        List<MailCdkDefinition> cdks = svc.listCdks(page, 10);
        if (cdks.isEmpty()) { sender.sendMessage(fullMsg("admin.cdk.list-empty", page)); return; }
        sender.sendMessage(fullMsg("admin.cdk.list-title", page));
        for (MailCdkDefinition c : cdks) {
            String stateStr = c.enabled() ? messages.get("admin.cdk.enabled") : messages.get("admin.cdk.disabled");
            sender.sendMessage(fullMsg("admin.cdk.list-item", c.code(), c.presetId(), c.claimedCount(), c.maxClaims(), stateStr));
        }
    }

    private void handleCdkDelete(CommandSender sender, MailService svc, String[] args) {
        if (args.length < 4) { sender.sendMessage(fullMsg("admin.cdk.delete-usage")); return; }
        MailOperationResult result = svc.deleteCdk(args[3]);
        sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private Instant parseTtl(String input) {
        if ("permanent".equalsIgnoreCase(input)) return null;
        Matcher m = DURATION_PATTERN.matcher(input.toLowerCase(Locale.ROOT));
        if (!m.matches()) return Instant.now().plus(Duration.ofDays(7));
        long val = Long.parseLong(m.group(1));
        return Instant.now().plus(switch (m.group(2)) {
            case "s" -> Duration.ofSeconds(val);
            case "m" -> Duration.ofMinutes(val);
            case "h" -> Duration.ofHours(val);
            case "d" -> Duration.ofDays(val);
            default -> Duration.ofDays(7);
        });
    }

    private static int parsePageNumber(String raw) {
        try { return Math.max(1, Integer.parseInt(raw)); } catch (NumberFormatException e) { return 1; }
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(n)) r.add(c);
        return r;
    }
}
