package xuanmo.arcartxsuite.crossserver;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FreeCrossServerServiceTest {
    @Test void configuredProxyAndRedisStillReturnOnlyInactiveAcknowledgements() {
        var plugin = mock(JavaPlugin.class);
        var configuration = new YamlConfiguration();
        configuration.set("cross-server.node-id", "local-name");
        configuration.set("cross-server.proxy.enabled", true);
        configuration.set("cross-server.redis.enabled", true);
        when(plugin.getConfig()).thenReturn(configuration);
        var service = new CrossServerService(plugin, () -> { throw new AssertionError("Free transport consulted a seat"); });
        service.start(CompletableFuture.completedFuture(null));

        var config = CrossServerChannelConfig.enabledDefault();
        var channels = List.of(service.openChannel("map", config, ignored -> fail("Unexpected delivery")),
            service.openChannelAdmitting("eventpacket", config, ignored -> fail("Unexpected admission")),
            service.openChannelCompleting("market", config, ignored -> fail("Unexpected receipt")));
        for (var channel : channels) {
            assertFalse(channel.isActive());
            assertFalse(channel.requiresSharedStateCatchUp());
            channel.publish("event");
            assertFalse(channel.publishDurably("event").toCompletableFuture().join());
            assertFalse(channel.publishWithReceipt("event").toCompletableFuture().join());
            channel.close();
        }
        assertEquals("local-name", service.nodeId());
        assertFalse(service.isRedisAvailable());
        service.shutdown().toCompletableFuture().join();
        verify(plugin, never()).getServer();
    }
}
