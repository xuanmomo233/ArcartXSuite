package xuanmo.arcartxsuite.afkreward.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.afkreward.service.AfkRewardService;

public final class AfkRewardAdminCommand implements ModuleCommandHandler {

    private final Supplier<AfkRewardService> serviceSupplier;
    private final MessageProvider messages;

    public AfkRewardAdminCommand(Supplier<AfkRewardService> serviceSupplier, MessageProvider messages) {
        this.serviceSupplier = serviceSupplier;
        this.messages = messages;
    }

    private String msg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override
    public String commandId() {
        return "afkreward";
    }

    @Override
    public List<String> actions() {
        return List.of("help", "status", "reload");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg("admin.unknown", label));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);

        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "reload" -> sender.sendMessage(msg("admin.reload-hint", label));
            default -> sender.sendMessage(msg("admin.unknown", label));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            return filter(actions(), args[1]);
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(msg("admin.help.title"));
        sender.sendMessage(msg("admin.help.status", label));
        sender.sendMessage(msg("admin.help.reload", label));
    }

    private void sendStatus(CommandSender sender) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) {
            sender.sendMessage(msg("common.service-down"));
            return;
        }
        sender.sendMessage(msg("admin.status.title"));
        sender.sendMessage(msg("admin.status.areas", service.areas().size()));
        sender.sendMessage(msg("admin.status.types", service.types().size()));
        int online = 0;
        for (String area : service.areas().keySet()) {
            online += service.getPlayersInArea(area);
        }
        sender.sendMessage(msg("admin.status.online", online));
    }

    private static List<String> filter(List<String> list, String input) {
        String norm = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase(Locale.ROOT).startsWith(norm)) result.add(s);
        }
        return result;
    }
}
