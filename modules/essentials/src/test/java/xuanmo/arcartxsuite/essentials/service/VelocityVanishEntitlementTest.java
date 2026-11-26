package xuanmo.arcartxsuite.essentials.service;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.crossserver.ProxyTabVisibility;
import static org.junit.jupiter.api.Assertions.*;

class VelocityVanishEntitlementTest {
    @Test void freeBackendStripsProxyMarkerWithoutApplyingOrRetainingTheHandoff() throws Exception {
        var permitted = new AtomicBoolean(false);
        var handoff = handoff(permitted);
        var event = new ForwardedLoginEvent();
        assertTrue(handoff.capture(event));
        assertEquals(Set.of("textures"), event.profile.properties);
        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, event.getLoginResult());

        permitted.set(true);
        assertNull(handoff.take(event.getUniqueId()));
    }

    @Test void paidHandoffWorksOnceAndRevocationDiscardsPendingVanish() throws Exception {
        var permitted = new AtomicBoolean(true);
        var handoff = handoff(permitted);
        var carried = new ForwardedLoginEvent();
        assertTrue(handoff.capture(carried));
        assertEquals(Set.of("textures"), carried.profile.properties);
        assertEquals(carried.getAddress(), handoff.take(carried.getUniqueId()));
        assertNull(handoff.take(carried.getUniqueId()));

        var pending = new ForwardedLoginEvent();
        assertTrue(handoff.capture(pending));
        permitted.set(false);
        assertNull(handoff.take(pending.getUniqueId()));
        permitted.set(true);
        assertNull(handoff.take(pending.getUniqueId()));
    }

    private static VelocityVanishProfile handoff(AtomicBoolean permitted) throws Exception {
        return new VelocityVanishProfile(ForwardedLoginEvent.class.getMethod("profile"),
            ForwardedProfile.class.getMethod("removeProperty", String.class), permitted::get);
    }

    static final class ForwardedLoginEvent extends AsyncPlayerPreLoginEvent {
        private final ForwardedProfile profile = new ForwardedProfile();

        ForwardedLoginEvent() throws Exception {
            super("Alex", InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 1}), UUID.randomUUID());
        }

        public ForwardedProfile profile() {
            return profile;
        }
    }

    static final class ForwardedProfile {
        private final Set<String> properties = new HashSet<>(Set.of("textures", ProxyTabVisibility.VANISH_PROFILE_PROPERTY));

        public boolean removeProperty(String name) {
            return properties.remove(name);
        }
    }
}
