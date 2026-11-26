package xuanmo.arcartxsuite.exploration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess;
import xuanmo.arcartxsuite.api.capability.ConversationPlayable;
import xuanmo.arcartxsuite.api.capability.QuestGpsNavigable;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.message.MessageProvider;

final class Discoveries {
    private final ItemSourceRegistry sources;
    private final ItemRewardDispatcher rewards;
    private final Supplier<QuestGpsNavigable> quests;
    private final Supplier<AbyssAdmissionAccess> abyss;
    private final Supplier<ConversationPlayable> conversations;
    private final MessageProvider messages;
    private final ExplorationChests chests;
    private final Map<UUID, Admission> admissions = new HashMap<>();

    private record Admission(Player player, UUID request, long deadline) {}

    Discoveries(ItemSourceRegistry sources, ItemRewardDispatcher rewards, Supplier<QuestGpsNavigable> quests,
                Supplier<AbyssAdmissionAccess> abyss, Supplier<ConversationPlayable> conversations, MessageProvider messages,
                ExplorationChests chests) {
        this.chests = chests;
        this.sources = sources;
        this.rewards = rewards;
        this.quests = quests;
        this.abyss = abyss;
        this.conversations = conversations;
        this.messages = messages;
    }

    boolean resolve(Player player, ExplorationConfig.Region region, ExplorationConfig.Outcome outcome) {
        return switch (outcome.kind()) {
            case CONVERSATION -> {
                var dialogue = conversations.get();
                if (dialogue != null && dialogue.openConversation(player, outcome.target())) yield true;
                yield unavailable(player, "Conversation/" + outcome.target());
            }
            case QUEST -> {
                QuestGpsNavigable gps = quests.get();
                if (gps == null) yield unavailable(player, "QuestGPS");
                if (gps.tryOfferQuest(player, outcome.target(), true)) yield true;
                yield unavailable(player, "QuestGPS/" + outcome.target());
            }
            case ABYSS -> enterDungeon(player, outcome);
            case CHEST, GATHER -> loot(player, region, outcome);
        };
    }

    private boolean enterDungeon(Player player, ExplorationConfig.Outcome outcome) {
        AbyssAdmissionAccess access = abyss.get();
        if (access == null) return unavailable(player, "Abyss");
        UUID actor = player.getUniqueId();
        var intent = new AbyssAdmissionAccess.Intent("exploration", UUID.randomUUID(), actor, List.of(actor),
            outcome.target(), outcome.plan(), null, null);
        var preflight = access.preflight(intent);
        if (!preflight.allowed()) {
            String reason = preflight.denials().isEmpty() ? "Abyss" : preflight.denials().get(0).message();
            player.sendMessage(messages.get("abyss-denied", reason));
            return false;
        }
        var entry = access.requestEntry(intent, preflight.token());
        return switch (entry.phase()) {
            case PREPARING, RESERVING_COST, COMMITTING_COST, TELEPORTING -> {
                admissions.put(actor, new Admission(player, intent.requestId(), System.currentTimeMillis() + 60_000));
                player.sendMessage(messages.get("abyss-pending"));
                yield true;
            }
            case STARTED -> true;
            default -> {
                player.sendMessage(messages.get("abyss-denied", entry.reason()));
                yield false;
            }
        };
    }

    boolean hasPendingDiscovery(UUID player) {
        return admissions.containsKey(player) || chests.pending(player);
    }

    // Only observe submitted requests. Abyss owns the journal, costs and teleport.
    void refreshAdmissions() {
        if (admissions.isEmpty()) return;
        AbyssAdmissionAccess access = abyss.get();
        admissions.values().removeIf(pending -> {
            Player player = pending.player();
            if (!player.isOnline()) return true;
            if (access == null || System.currentTimeMillis() >= pending.deadline()) {
                player.sendMessage(messages.get("abyss-unknown"));
                return true;
            }
            var entry = access.entryStatus("exploration", pending.request());
            if (entry == null) return false;
            return switch (entry.phase()) {
                case PREPARING, RESERVING_COST, COMMITTING_COST, TELEPORTING, COMPENSATING_COST -> false;
                case STARTED -> true;
                case REVIEW_REQUIRED -> {
                    player.sendMessage(messages.get("abyss-unknown"));
                    yield true;
                }
                default -> {
                    player.sendMessage(messages.get("abyss-denied", entry.reason()));
                    yield true;
                }
            };
        });
    }

    private boolean loot(Player player, ExplorationConfig.Region region, ExplorationConfig.Outcome outcome) {
        List<ItemStack> items = new ArrayList<>();
        for (var loot : outcome.items()) {
            ItemStack stack = sources.generateItem(loot.source(), loot.id(), loot.amount(), null, player);
            if (stack == null || stack.getType().isAir() || loot.amount() > stack.getMaxStackSize()) {
                return unavailable(player, loot.source() + "/" + loot.id());
            }
            stack.setAmount(loot.amount());
            items.add(stack);
        }
        var options = ItemRewardDispatcher.DispatchOptions.builder("exploration").sourceDetail(region.id()).build();
        if (outcome.kind() == ExplorationConfig.Kind.GATHER) {
            boolean delivered = rewards.dispatchItems(player, items, options).success();
            player.sendMessage(messages.get(delivered ? "gathered" : "delivery-failed"));
            // A batch may already have delivered part of its items.
            return true;
        }
        if (!chests.spawn(player, outcome.title(), outcome.model(), outcome.scale(), items, options)) {
            return unavailable(player, outcome.title());
        }
        player.sendMessage(messages.get("chest"));
        return true;
    }

    private boolean unavailable(Player player, String detail) {
        player.sendMessage(messages.get("unavailable", detail));
        return false;
    }

    void shutdown() {
        admissions.clear();
    }
}
