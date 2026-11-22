package xuanmo.arcartxsuite.essentials.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.essentials.config.EssentialsFeatures.Tpa;

final class TpaRequestsTest {
    private static AuthSnapshot admission(UUID id) {
        return new AuthSnapshot(id, UUID.randomUUID(), 1, id, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "server", Set.of(), "");
    }

    @Test
    void keepsSendersSeparateAndCancelsOnlySelectedTarget() {
        var requests = new TpaRequests(new Tpa(true, 0, 2, 2));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID otherTarget = UUID.randomUUID();
        assertEquals(TpaRequests.SendResult.SENT, requests.send(admission(first), admission(target), false, 0, 100));
        assertEquals(TpaRequests.SendResult.SENT, requests.send(admission(second), admission(target), true, 0, 100));
        assertEquals(TpaRequests.SendResult.SENT, requests.send(admission(first), admission(otherTarget), false, 0, 100));
        assertEquals(TpaRequests.SendResult.INCOMING_FULL, requests.send(admission(UUID.randomUUID()), admission(target), false, 0, 100));
        assertEquals(TpaRequests.SendResult.OUTGOING_FULL, requests.send(admission(first), admission(UUID.randomUUID()), false, 0, 100));
        assertEquals(1, requests.cancelOutgoing(first, target, 10).size());
        assertEquals(List.of(second), requests.received(target, 10).stream().map(TpaRequests.Request::sender).toList());
        assertEquals(List.of(first), requests.received(otherTarget, 10).stream().map(TpaRequests.Request::sender).toList());
    }

    @Test
    void cooldownAndExpiryUseTheNewRequest() {
        var requests = new TpaRequests(new Tpa(true, 1, 1, 1));
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        var senderAdmission = admission(sender);
        var targetAdmission = admission(target);
        assertEquals(TpaRequests.SendResult.SENT, requests.send(senderAdmission, targetAdmission, false, 0, 2000));
        assertEquals(TpaRequests.SendResult.COOLDOWN, requests.send(senderAdmission, targetAdmission, true, 500, 2000));
        assertEquals(TpaRequests.SendResult.SENT, requests.send(senderAdmission, targetAdmission, true, 1000, 2000));
        var expired = new ArrayList<TpaRequests.Request>();
        requests.expire(2000, expired::add);
        assertTrue(expired.isEmpty());
        assertTrue(requests.received(target, 2000).get(0).here());
        requests.expire(3000, expired::add);
        assertEquals(1, expired.size());
        assertTrue(requests.received(target, 3000).isEmpty());
    }
}
