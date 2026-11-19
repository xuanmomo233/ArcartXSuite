package xuanmo.arcartxsuite.lottery.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.lottery.LotteryService;

class LotteryPreviewIconsTest {
    private final IconResolver resolver = mock(IconResolver.class);
    private final LotteryPreviewIcons icons = new LotteryPreviewIcons(resolver, mock(LotteryService.class));
    private final Player alice = mock(Player.class);
    private final Player bob = mock(Player.class);

    @Test
    void reusesVanillaPreviewAfterSuccessfulResolution() {
        IconDefinition icon = IconDefinition.load("DIAMOND");
        when(resolver.resolveItemJson(alice, icon)).thenReturn("");
        when(resolver.resolveItemJson(bob, icon)).thenReturn("diamond");

        assertEquals("", icons.resolve(alice, icon));
        assertEquals("diamond", icons.resolve(bob, icon));
        assertEquals("diamond", icons.resolve(alice, icon));
        verify(resolver).resolveItemJson(alice, icon);
        verify(resolver).resolveItemJson(bob, icon);
    }

    @Test
    void resolvesExternalPreviewWithTheCurrentPlayerEachTime() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("source", "overture");
        config.set("id", "personal-item");
        IconDefinition icon = IconDefinition.load(config);
        when(resolver.resolveItemJson(alice, icon)).thenReturn("alice-original", "alice-changed");
        when(resolver.resolveItemJson(bob, icon)).thenReturn("bob");

        assertEquals("alice-original", icons.resolve(alice, icon));
        assertEquals("bob", icons.resolve(bob, icon));
        assertEquals("alice-changed", icons.resolve(alice, icon));
    }
}
