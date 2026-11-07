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
}
