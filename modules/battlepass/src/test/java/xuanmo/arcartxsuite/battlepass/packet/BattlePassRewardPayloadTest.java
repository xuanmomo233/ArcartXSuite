package xuanmo.arcartxsuite.battlepass.packet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.battlepass.config.BattlePassModuleConfiguration;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward.RewardTier;
import xuanmo.arcartxsuite.battlepass.service.BattlePassService;

class BattlePassRewardPayloadTest {
    @Test
    void keepsTheLevelSlotsAndPlayerStateWhileReusingOnlyStaticIcons() throws Exception {
        var stone = IconDefinition.load("STONE");
        var external = new IconDefinition("", 1, "", List.of(), 0, "overture", "personal",
            "", "", "", "", "", Map.of(), "", false, "", "");
        var configuration = mock(BattlePassModuleConfiguration.class);
        when(configuration.rewards()).thenReturn(List.of(
            reward(10, RewardTier.DELUXE, external), reward(2, RewardTier.PREMIUM, stone), reward(2, RewardTier.FREE, stone)));
        when(configuration.seasonRewards()).thenReturn(List.of());
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        var service = mock(BattlePassService.class);
        when(service.configuration()).thenReturn(configuration);
        var early = new BattlePassPlayerProgress(id, "season").withLevel(2);
        var bridge = mock(PacketBridgeAPI.class);
        var items = mock(ItemBridgeAPI.class);
        when(items.itemToPreviewJson(any(ItemStack.class))).thenReturn(Optional.of("preview"));
        var sources = mock(ItemSourceRegistry.class);
        when(sources.generateOvertureItem("personal", player, 1)).thenReturn(mock(ItemStack.class));
        var handler = new BattlePassPacketHandler(null, bridge, null, service, "main", "tasks", "rewards",
            "settlements", sources, items, null);
        var build = BattlePassPacketHandler.class.getDeclaredMethod("buildRewardsData", Player.class, BattlePassPlayerProgress.class);
        build.setAccessible(true);
        Map<?, ?> first;
        Map<?, ?> second;
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getItemFactory).thenReturn(mock(ItemFactory.class));
            first = (Map<?, ?>) build.invoke(handler, player, early);
            second = (Map<?, ?>) build.invoke(handler, player, early.withLevel(10));
        }

        var levels = (Map<?, ?>) first.get("levels");
        assertEquals(Map.of("level", "2", "reached", "true", "freeEntryKey", "0", "premiumEntryKey", "1"), levels.get("1"));
        assertEquals(Map.of("level", "10", "reached", "false", "deluxeEntryKey", "2"), levels.get("2"));
        assertEquals("true", ((Map<?, ?>) ((Map<?, ?>) second.get("levels")).get("2")).get("reached"));
        assertEquals("preview", first.get("iconJson0"));
        assertEquals("preview", first.get("iconJson1"));
        assertEquals("preview", second.get("iconJson2"));
        verify(sources, times(2)).generateOvertureItem("personal", player, 1);
        verify(items, times(3)).itemToPreviewJson(any(ItemStack.class));
    }

    private static BattlePassReward reward(int level, RewardTier tier, IconDefinition icon) {
        return new BattlePassReward(level + "_" + tier, level, tier, "item", icon, null,
            List.of(), null, 0.0, null, null, null, null);
    }
}
