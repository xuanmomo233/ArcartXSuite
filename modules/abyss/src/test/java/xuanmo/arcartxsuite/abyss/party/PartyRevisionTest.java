package xuanmo.arcartxsuite.abyss.party;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartyRevisionTest {
    @Test void targetMembershipAndLeadershipChangesInvalidateReadiness() {
        UUID leader=UUID.randomUUID(),member=UUID.randomUUID();Party party=new Party(leader,4);party.addMember(member);
        party.toggleReady(member);assertTrue(party.allReady());long revision=party.revision();
        party.dungeonId("other");assertFalse(party.allReady());assertTrue(party.revision()>revision);
        party.toggleReady(member);party.addMember(UUID.randomUUID());assertFalse(party.isReady(member));
        party.setLeader(member);assertFalse(party.isReady(leader));
    }
    @Test void programmaticChangesNotifyObserversAndCosmeticChangesKeepReadiness() {
        UUID leader=UUID.randomUUID(),member=UUID.randomUUID();Party party=new Party(leader,4);party.addMember(member);party.toggleReady(member);
        var notifications=new AtomicInteger();party.onChange(notifications::incrementAndGet);
        party.name("renamed");assertTrue(party.allReady());assertEquals(1,notifications.get());
        party.removeMember(member);assertEquals(2,notifications.get());assertTrue(party.observers().contains(member));
    }
}
