package xuanmo.arcartxsuite.chat.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChatChannelRelayPolicyTest {
    private ChatChannelDefinition channel(ChatChannelMode mode, double range, String receivePermission) {
        return new ChatChannelDefinition("test", true, "test", mode, "", receivePermission,
            false, range, "", "", "", "", "");
    }
    @Test void onlyUnrestrictedServerWidePublicChannelsCanBeRelayed() {
        assertTrue(channel(ChatChannelMode.NORMAL, 0, "").permitsExternalRelay());
        assertTrue(channel(ChatChannelMode.GLOBAL, 0, "").permitsExternalRelay());
        assertFalse(channel(ChatChannelMode.PRIVATE, 0, "").permitsExternalRelay());
        assertFalse(channel(ChatChannelMode.STAFF, 0, "").permitsExternalRelay());
        assertFalse(channel(ChatChannelMode.NORMAL, 100, "").permitsExternalRelay());
        assertFalse(channel(ChatChannelMode.NORMAL, 0, "staff.read").permitsExternalRelay());
    }

    @Test void crossServerRejectsNearbyAndPartyAndDistinguishesReceiverPolicies() {
        var normal = crossServerChannel(ChatChannelMode.NORMAL, 0, "");
        var nearby = crossServerChannel(ChatChannelMode.NORMAL, 30, "");
        var party = crossServerChannel(ChatChannelMode.PARTY, 0, "");
        var restricted = crossServerChannel(ChatChannelMode.NORMAL, 0, "staff.read");
        assertTrue(normal.permitsCrossServer());
        assertFalse(nearby.permitsCrossServer());
        assertFalse(party.permitsCrossServer());
        assertFalse(party.permitsExternalRelay());
        assertNotEquals(normal.audienceKey(), restricted.audienceKey());
        assertThrows(IllegalArgumentException.class, () -> ChatChannelMode.parse("staf"));
    }

    private ChatChannelDefinition crossServerChannel(ChatChannelMode mode, double range, String receivePermission) {
        return new ChatChannelDefinition("test", true, "test", mode, "", receivePermission,
            true, range, "", "", "", "", "");
    }
}
