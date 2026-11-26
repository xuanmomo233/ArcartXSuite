package xuanmo.arcartxsuite.exploration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess;
import xuanmo.arcartxsuite.api.capability.ConversationPlayable;
import xuanmo.arcartxsuite.api.capability.QuestGpsNavigable;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.message.MessageProvider;

class DiscoveriesTest {
    private final ItemSourceRegistry sources = mock(ItemSourceRegistry.class);
    private final ItemRewardDispatcher rewards = mock(ItemRewardDispatcher.class);
    private final QuestGpsNavigable quests = mock(QuestGpsNavigable.class);
    private final ConversationPlayable dialogue = mock(ConversationPlayable.class);
    private final AbyssAdmissionAccess abyss = mock(AbyssAdmissionAccess.class);
    private final ExplorationChests chests = mock(ExplorationChests.class);
    private final Discoveries discoveries = new Discoveries(sources, rewards, () -> quests, () -> abyss,
        () -> dialogue, mock(MessageProvider.class), chests);
    private final Player player = mock(Player.class);
    private final ExplorationConfig.Region area = mock(ExplorationConfig.Region.class);

    private ExplorationConfig.Outcome outcome(ExplorationConfig.Kind kind, String target) {
        return new ExplorationConfig.Outcome(kind, 1, List.of(), "", target, null, null, 1.0);
    }

    @Test void rejectedPreflightNeverRequestsEntry() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(abyss.preflight(any())).thenReturn(new AbyssAdmissionAccess.Preflight(null, 0,
            List.of(new AbyssAdmissionAccess.Denial(null, "REQUIREMENT", "等级不足"))));
        assertFalse(discoveries.resolve(player, area, outcome(ExplorationConfig.Kind.ABYSS, "example")));
        verify(abyss, never()).requestEntry(any(), any());
    }

    @Test void admissionUsesSameSoloIntentAndTicket() {
        UUID actor = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(actor);
        when(abyss.preflight(any())).thenReturn(new AbyssAdmissionAccess.Preflight(token, 0, List.of()));
        when(abyss.requestEntry(any(), eq(token))).thenReturn(new AbyssAdmissionAccess.Entry(
            AbyssAdmissionAccess.Phase.PREPARING, null, "", Map.of()));
        assertTrue(discoveries.resolve(player, area, outcome(ExplorationConfig.Kind.ABYSS, "example")));
        var submitted = ArgumentCaptor.forClass(AbyssAdmissionAccess.Intent.class);
        verify(abyss).preflight(submitted.capture());
        var intent = submitted.getValue();
        assertEquals(List.of(actor), intent.members());
        assertEquals(actor, intent.actor());
        assertEquals("example", intent.dungeon());
        verify(abyss).requestEntry(intent, token);
        assertTrue(discoveries.hasPendingDiscovery(actor));
        when(player.isOnline()).thenReturn(true);
        when(abyss.entryStatus("exploration", intent.requestId())).thenReturn(new AbyssAdmissionAccess.Entry(
            AbyssAdmissionAccess.Phase.REJECTED, null, "MEMBER_BUSY", Map.of()));
        discoveries.refreshAdmissions();
        assertFalse(discoveries.hasPendingDiscovery(actor));
        verify(abyss, times(1)).requestEntry(any(), any());
    }

    @Test void questOpensOfferAndConversationReportsMissingNpc() {
        assertFalse(discoveries.resolve(player, area, outcome(ExplorationConfig.Kind.QUEST, "missing")));
        when(quests.tryOfferQuest(player, "letter", true)).thenReturn(true);
        assertTrue(discoveries.resolve(player, area, outcome(ExplorationConfig.Kind.QUEST, "letter")));
        verify(quests).tryOfferQuest(player, "letter", true);
        assertFalse(discoveries.resolve(player, area, outcome(ExplorationConfig.Kind.CONVERSATION, "herbalist")));
        verify(dialogue).openConversation(player, "herbalist");
    }

    @Test void gatherValidatesWholeBatchBeforeDelivering() {
        var berries = new ExplorationConfig.Loot("minecraft", "SWEET_BERRIES", 3);
        var bad = new ExplorationConfig.Loot("neige", "unknown", 1);
        when(sources.generateItem("minecraft", "SWEET_BERRIES", 3, null, player))
            .thenReturn(new ItemStack(Material.SWEET_BERRIES, 3));
        var outcome = new ExplorationConfig.Outcome(ExplorationConfig.Kind.GATHER, 1, List.of(berries, bad), "", null, null, null, 1.0);
        assertFalse(discoveries.resolve(player, area, outcome));
        verifyNoInteractions(rewards);
    }

    @Test void chestForwardsConfiguredModelScaleAndLootWithoutImmediateDelivery() {
        var loot = new ExplorationConfig.Loot("minecraft", "IRON_INGOT", 3);
        when(sources.generateItem("minecraft", "IRON_INGOT", 3, null, player))
            .thenReturn(new ItemStack(Material.IRON_INGOT, 3));
        var outcome = new ExplorationConfig.Outcome(ExplorationConfig.Kind.CHEST, 1, List.of(loot), "遗迹宝箱",
            null, null, "ruins_chest", 1.25);
        when(chests.spawn(eq(player), eq("遗迹宝箱"), eq("ruins_chest"), eq(1.25), anyList(), any()))
            .thenReturn(false, true);
        assertFalse(discoveries.resolve(player, area, outcome));
        assertTrue(discoveries.resolve(player, area, outcome));
        verify(chests, times(2)).spawn(eq(player), eq("遗迹宝箱"), eq("ruins_chest"), eq(1.25),
            argThat(items -> items.size() == 1 && items.get(0).getType() == Material.IRON_INGOT
                && items.get(0).getAmount() == 3), any());
        verifyNoInteractions(rewards);
    }

    @Test void unclaimedModelBlocksAnotherDiscoveryForItsOwner() {
        UUID owner = UUID.randomUUID();
        when(chests.pending(owner)).thenReturn(true);
        assertTrue(discoveries.hasPendingDiscovery(owner));
        assertFalse(discoveries.hasPendingDiscovery(UUID.randomUUID()));
    }
}
