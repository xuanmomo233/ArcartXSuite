package xuanmo.arcartxsuite.conversation.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI;
import xuanmo.arcartxsuite.conversation.service.ConversationService;

/**
 * /axs conversation adyeshach setModel       <name> <modelID> <scale>
 * /axs conversation adyeshach setAnimation    <name> <state> <animName>
 * /axs conversation adyeshach playAnimation   <name> <animation> <speed> [transitionTime] [keepTime]
 */
public final class ConversationAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "adyeshach");
    private static final List<String> ADYESHACH_ACTIONS = List.of("setModel", "setAnimation", "playAnimation");

    private final Supplier<ConversationService> serviceProvider;
    private final Supplier<AdyeshachNpcBridgeAPI> npcBridgeProvider;
    private final MessageProvider messages;

    public ConversationAdminCommand(
        Supplier<ConversationService> serviceProvider,
        Supplier<AdyeshachNpcBridgeAPI> npcBridgeProvider,
        MessageProvider messages
    ) {
        this.serviceProvider = serviceProvider;
        this.npcBridgeProvider = npcBridgeProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override
    public String commandId() {
        return "conversation";
    }

    @Override
    public List<String> actions() {
        return ACTIONS;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("arcartxsuite.admin")) {
            sender.sendMessage(fullMsg("common.no-permission"));
            return true;
        }

        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "adyeshach" -> handleAdyeshach(sender, label, args);
            default -> sender.sendMessage(fullMsg("common.unknown", label));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            return filter(ACTIONS, args[1]);
        }
        if (args.length == 3 && "adyeshach".equalsIgnoreCase(args[1])) {
            return filter(ADYESHACH_ACTIONS, args[2]);
        }
        return List.of();
    }

    // ─── adyeshach 子命令 ────────────────────────────────────

    private void handleAdyeshach(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sendAdyeshachHelp(sender, label);
            return;
        }
        String sub = args[2].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "setmodel" -> handleSetModel(sender, args);
            case "setanimation" -> handleSetAnimation(sender, args);
            case "playanimation" -> handlePlayAnimation(sender, args);
            default -> sendAdyeshachHelp(sender, label);
        }
    }

    /**
     * /axs conversation adyeshach setModel <name> <modelID> <scale>
     */
    private void handleSetModel(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(fullMsg("adyeshach.setmodel.usage"));
            return;
        }
        String npcName = args[3];
        String modelId = args[4];
        double scale;
        try {
            scale = Double.parseDouble(args[5]);
        } catch (NumberFormatException e) {
            sender.sendMessage(fullMsg("adyeshach.setmodel.invalid-scale", args[5]));
            return;
        }

        AdyeshachNpcBridgeAPI bridge = npcBridgeProvider.get();
        if (bridge == null || !bridge.isAvailable()) {
            sender.sendMessage(fullMsg("adyeshach.setmodel.bridge-unavailable"));
            return;
        }

        Optional<Object> npc = bridge.findByName(npcName);
        if (npc.isEmpty()) {
            sender.sendMessage(fullMsg("adyeshach.setmodel.not-found", npcName));
            return;
        }

        boolean ok = bridge.applyModel(npc.get(), modelId, scale);
        if (ok) {
            sender.sendMessage(fullMsg("adyeshach.setmodel.success", npcName, modelId, scale));
        } else {
            sender.sendMessage(fullMsg("adyeshach.setmodel.failed"));
        }
    }

    /**
     * /axs conversation adyeshach setAnimation <name> <state> <animName>
     */
    private void handleSetAnimation(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(fullMsg("adyeshach.setanimation.usage"));
            return;
        }
        String npcName = args[3];
        String state = args[4];
        String animName = args[5];

        AdyeshachNpcBridgeAPI bridge = npcBridgeProvider.get();
        if (bridge == null || !bridge.isAvailable()) {
            sender.sendMessage(fullMsg("adyeshach.setmodel.bridge-unavailable"));
            return;
        }

        Optional<Object> npc = bridge.findByName(npcName);
        if (npc.isEmpty()) {
            sender.sendMessage(fullMsg("adyeshach.setmodel.not-found", npcName));
            return;
        }

        boolean ok = bridge.applyDefaultState(npc.get(), state, animName);
        if (ok) {
            sender.sendMessage(fullMsg("adyeshach.setanimation.success", npcName, state, animName));
        } else {
            sender.sendMessage(fullMsg("adyeshach.setanimation.failed"));
        }
    }

    /**
     * /axs conversation adyeshach playAnimation <name> <animation> <speed> [transitionTime] [keepTime]
     * transitionTime 默认 5 ms，keepTime 默认 -1（播放完整）
     */
    private void handlePlayAnimation(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(fullMsg("adyeshach.playanimation.usage"));
            return;
        }
        String npcName = args[3];
        String animation = args[4];
        double speed;
        try {
            speed = Double.parseDouble(args[5]);
            if (speed <= 0.0D) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(fullMsg("adyeshach.playanimation.invalid-speed", args[5]));
            return;
        }
        int transitionTime = 5;
        if (args.length >= 7) {
            try {
                transitionTime = Integer.parseInt(args[6]);
            } catch (NumberFormatException e) {
                sender.sendMessage(fullMsg("adyeshach.playanimation.invalid-transition", args[6]));
                return;
            }
        }
        long keepTime = -1L;
        if (args.length >= 8) {
            try {
                keepTime = Long.parseLong(args[7]);
            } catch (NumberFormatException e) {
                sender.sendMessage(fullMsg("adyeshach.playanimation.invalid-keep", args[7]));
                return;
            }
        }

        AdyeshachNpcBridgeAPI bridge = npcBridgeProvider.get();
        if (bridge == null || !bridge.isAvailable()) {
            sender.sendMessage(fullMsg("adyeshach.setmodel.bridge-unavailable"));
            return;
        }

        Optional<Object> npc = bridge.findByName(npcName);
        if (npc.isEmpty()) {
            sender.sendMessage(fullMsg("adyeshach.setmodel.not-found", npcName));
            return;
        }

        boolean ok = bridge.applyAnimation(npc.get(), animation, speed, transitionTime, keepTime);
        if (ok) {
            sender.sendMessage(fullMsg("adyeshach.playanimation.success", npcName, animation, speed, transitionTime, keepTime));
        } else {
            sender.sendMessage(fullMsg("adyeshach.playanimation.failed"));
        }
    }

    // ─── help / status ───────────────────────────────

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " conversation";
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(fullMsg("help.status", cmd));
        sender.sendMessage(fullMsg("help.setmodel", cmd));
        sender.sendMessage(fullMsg("help.setanimation", cmd));
        sender.sendMessage(fullMsg("help.playanimation", cmd));
    }

    private void sendAdyeshachHelp(CommandSender sender, String label) {
        sender.sendMessage(fullMsg("adyeshach.help.title"));
        sender.sendMessage(fullMsg("adyeshach.help.setmodel"));
        sender.sendMessage(fullMsg("adyeshach.help.setanimation"));
        sender.sendMessage(fullMsg("adyeshach.help.playanimation"));
    }

    private void sendStatus(CommandSender sender) {
        ConversationService svc = serviceProvider.get();
        if (svc == null) {
            sender.sendMessage(fullMsg("common.service-down"));
            return;
        }
        sender.sendMessage(fullMsg("status.title"));
        sender.sendMessage(fullMsg("status.interaction", flag(svc.interactionReady())));
        sender.sendMessage(fullMsg("status.npc-bridge", flag(svc.npcBridgeReady())));
        sender.sendMessage(fullMsg("status.key-event", flag(svc.keyPressEventReady())));
        sender.sendMessage(fullMsg("status.selector-ui", flag(svc.selectorUiReady())));
        sender.sendMessage(fullMsg("status.active-count", svc.activeConversationCount()));
        if (!svc.interactionReady()) {
            sender.sendMessage(fullMsg("status.disabled-reason", svc.interactionDisabledReason()));
        }
    }

    private static String flag(boolean value) {
        return value ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
    }

    private static List<String> filter(List<String> candidates, String input) {
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
