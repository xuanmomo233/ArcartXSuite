package xuanmo.arcartxsuite.conversation.service;

import java.util.List;
import java.util.Set;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ChemdahNpcAccessTest {
    @Test
    void readsWorldRestrictionsWithoutDependingOnTheGetterReturnDescriptor() {
        assertEquals(List.of("town"), ChemdahNpcAccess.worlds(new ListBinding(List.of("town"))));
        assertEquals(List.of("island"), ChemdahNpcAccess.worlds(new SetBinding(Set.of("island"))));
        assertEquals(List.of("town", "island"), ChemdahNpcAccess.worlds(new ArrayBinding(new String[] {"town", "island"})));
        assertEquals(List.of(), ChemdahNpcAccess.worlds(new ListBinding(List.of())));
    }

    @Test
    void treatsOnlyTheVerifiedLegacyShapeAsUnrestricted() {
        assertEquals(List.of(), ChemdahNpcAccess.worlds(new LegacyBinding("adyeshach", "guard")));
        assertNull(ChemdahNpcAccess.worlds(new UnreadableWorldBinding("adyeshach", "guard", List.of("town"))));
        assertNull(ChemdahNpcAccess.worlds(new InvalidBinding()));
        assertNull(ChemdahNpcAccess.worlds(new MixedWorldBinding()));
    }

    @Test
    void delegatesToThePlayerAwareMatcherWithoutTryingTheGlobalOverload() {
        Player allowed = mock(Player.class);
        Player denied = mock(Player.class);
        var matching = new PlayerMatcher(allowed);

        assertTrue(ChemdahNpcAccess.hasPlayerMatcher(matching));
        assertTrue(ChemdahNpcAccess.matchesPlayer(matching, "adyeshach", "guard", allowed));
        assertFalse(ChemdahNpcAccess.matchesPlayer(matching, "adyeshach", "guard", denied));
        assertFalse(ChemdahNpcAccess.matchesPlayer(matching, "adyeshach", "trader", allowed));
        assertEquals(0, matching.globalCalls);
    }

    @Test
    void missingOrBrokenPlayerMatcherNeverGrantsAGlobalMatch() {
        Player player = mock(Player.class);
        var legacy = new GlobalMatcher();
        assertFalse(ChemdahNpcAccess.hasPlayerMatcher(legacy));
        assertFalse(ChemdahNpcAccess.matchesPlayer(legacy, "adyeshach", "guard", player));
        assertEquals(0, legacy.globalCalls);

        var broken = new BrokenMatcher();
        assertTrue(ChemdahNpcAccess.hasPlayerMatcher(broken));
        assertFalse(ChemdahNpcAccess.matchesPlayer(broken, "adyeshach", "guard", player));
        assertEquals(0, broken.globalCalls);
    }

    public record ListBinding(List<String> worlds) {
        public List<String> getWorlds() {
            return worlds;
        }
    }

    public record SetBinding(Set<String> worlds) {
        public Set<String> getWorlds() {
            return worlds;
        }
    }

    public record ArrayBinding(String[] worlds) {
        public String[] getWorlds() {
            return worlds;
        }
    }

    public static final class LegacyBinding {
        private final String namespace;
        private final String value;

        LegacyBinding(String namespace, String value) {
            this.namespace = namespace;
            this.value = value;
        }
    }

    public static final class UnreadableWorldBinding {
        private final String namespace;
        private final String value;
        private final List<String> worlds;

        UnreadableWorldBinding(String namespace, String value, List<String> worlds) {
            this.namespace = namespace;
            this.value = value;
            this.worlds = worlds;
        }
    }

    public static final class InvalidBinding {
        public String getWorlds() {
            return "town";
        }
    }

    public static final class MixedWorldBinding {
        public List<?> getWorlds() {
            return List.of("town", 42);
        }
    }

    public static class GlobalMatcher {
        int globalCalls;

        public boolean isNPC(String namespace, String npcId) {
            globalCalls++;
            return true;
        }
    }

    public static final class PlayerMatcher extends GlobalMatcher {
        private final Player allowed;

        PlayerMatcher(Player allowed) {
            this.allowed = allowed;
        }

        public boolean isNPC(String namespace, String npcId, Player player) {
            return namespace.equals("adyeshach") && npcId.equals("guard") && player == allowed;
        }
    }

    public static final class BrokenMatcher extends GlobalMatcher {
        public boolean isNPC(String namespace, String npcId, Player player) {
            throw new NoSuchMethodError("Runtime Chemdah binding lookup unavailable");
        }
    }
}
