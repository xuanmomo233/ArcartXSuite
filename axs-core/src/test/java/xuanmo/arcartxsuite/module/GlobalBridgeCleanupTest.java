package xuanmo.arcartxsuite.module;

import org.junit.jupiter.api.Test;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.bridge.ContentModeratorImpl;
import static org.mockito.Mockito.*;

class GlobalBridgeCleanupTest {
    @Test void detachingTheFactoryStopsItsModerationRefreshTask() throws Exception {
        var factory = new GlobalBridgeFactory(mock(JavaPlugin.class), null, null, mock(ModuleRegistry.class), mock(CapabilityRegistry.class));
        var moderation = mock(ContentModeratorImpl.class);
        var field = GlobalBridgeFactory.class.getDeclaredField("contentModerator"); field.setAccessible(true); field.set(factory, moderation);
        factory.detachGlobalListeners(); verify(moderation).shutdown();
    }
}
