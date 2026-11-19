package xuanmo.arcartxsuite.combateffect.display.service;

import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry;
import xuanmo.arcartxsuite.api.attribute.AttributeDamageEvent;
import xuanmo.arcartxsuite.api.attribute.AttributeDamageListener;
import xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI;
import xuanmo.arcartxsuite.api.event.CombatDamageEvent;
import xuanmo.arcartxsuite.combateffect.display.config.CombatDisplayConfiguration;

class ConfirmedDamageDisplayTest {
    @Test
    void symphonyStyleAndFallbackRenderEachSettledHitOnceAndKeepDodgeFeedback() {
        var client = mock(ClientBridgeAPI.class);
        var attributes = mock(AttributeBridgeRegistry.class);
        var attacker = mock(Player.class);
        var target = mock(LivingEntity.class);
        when(attacker.isOnline()).thenReturn(true);
        when(client.isAvailable()).thenReturn(true);
        when(attributes.symphonyDamageAvailable()).thenReturn(true);
        var yaml = new YamlConfiguration();
        yaml.set("damage-display.source.mode", "symphony");
        yaml.set("damage-display.symphony.enabled", true);
        yaml.set("damage-display.symphony.config-id", "symphony");
        yaml.set("damage-display.dodged.enabled", true);
        yaml.set("damage-display.dodged.config-id", "dodge");
        yaml.set("damage-display.original.config-id", "standard");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            var display = new CombatDisplayService(mock(JavaPlugin.class), Logger.getAnonymousLogger(),
                CombatDisplayConfiguration.load(yaml), client, attributes);
            display.start();
            var listener = ArgumentCaptor.forClass(AttributeDamageListener.class);
            verify(attributes).registerDamageListener(listener.capture());
            listener.getValue().onAttributeDamage(new AttributeDamageEvent(attacker, target, 7,
                AttributeDamageEvent.Source.SYMPHONY));
            display.handleDamage(new CombatDamageEvent(attacker, target, 7, CombatDamageEvent.Source.SYMPHONY,
                UUID.randomUUID(), null));
            listener.getValue().onAttributeDamage(new AttributeDamageEvent(attacker, target, 0,
                AttributeDamageEvent.Source.SYMPHONY, false, true, null));
            display.handleDamage(new CombatDamageEvent(attacker, target, 3, CombatDamageEvent.Source.TACZ, null, null));
            verify(client).sendDamageDisplay(attacker, "symphony", 7, target);
            verify(client).sendDamageDisplay(attacker, "dodge", 0, target);
            verify(client).sendDamageDisplay(attacker, "standard", 3, target);
            verify(client, times(3)).sendDamageDisplay(eq(attacker), anyString(), anyDouble(), eq(target));
            display.shutdown();
        }
    }
}
