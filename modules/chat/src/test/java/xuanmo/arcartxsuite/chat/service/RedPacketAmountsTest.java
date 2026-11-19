package xuanmo.arcartxsuite.chat.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.chat.model.RedPacketType;

class RedPacketAmountsTest {
    @Test
    void amountMustHaveEnoughRepresentableUnitsForEveryShare() {
        var minimum = new BigDecimal("0.01");
        assertTrue(RedPacketService.splitAmounts(RedPacketType.NORMAL, BigDecimal.ONE, 3, 0, minimum).isEmpty());
        assertTrue(RedPacketService.splitAmounts(RedPacketType.LUCKY, BigDecimal.ONE, 3, 0, minimum).isEmpty());
        assertTrue(RedPacketService.splitAmounts(RedPacketType.NORMAL, new BigDecimal("1.001"), 2, 2, minimum).isEmpty());
        for (RedPacketType type : new RedPacketType[]{RedPacketType.NORMAL, RedPacketType.LUCKY}) {
            var shares = RedPacketService.splitAmounts(type, new BigDecimal("3.01"), 3, 2, new BigDecimal("1.001"));
            assertTrue(shares.isEmpty());
            shares = RedPacketService.splitAmounts(type, new BigDecimal("3.09"), 3, 2, new BigDecimal("1.001"));
            assertEquals(3, shares.size());
            assertTrue(shares.stream().allMatch(amount -> amount.compareTo(new BigDecimal("1.01")) >= 0));
            assertEquals(new BigDecimal("3.09"), shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }
}
