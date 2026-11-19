package xuanmo.arcartxsuite.battlepass.condition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.bridge.ChronosAccess;

class PlayerStateConditionTest {
    @Test
    void chronosConditionUsesCurrentProbeAndFailsClosedWhenUnavailable() {
        Player player = mock(Player.class);
        ChronosAccess access = mock(ChronosAccess.class);
        when(access.stateId(player)).thenReturn("attack", "idle");
        var condition = new PlayerStateCondition("chronos", "attack");
        try (var chronos = mockStatic(ChronosAccess.class)) {
            chronos.when(ChronosAccess::probe)
                .thenReturn(new ChronosAccess.Probe(ChronosAccess.Status.READY, "3.1.1", access));
            assertTrue(condition.test(player, Map.of()));
            assertFalse(condition.test(player, Map.of()));
            chronos.when(ChronosAccess::probe)
                .thenReturn(new ChronosAccess.Probe(ChronosAccess.Status.DISABLED, "3.1.1", null));
            assertFalse(condition.test(player, Map.of()));
        }
    }
}
