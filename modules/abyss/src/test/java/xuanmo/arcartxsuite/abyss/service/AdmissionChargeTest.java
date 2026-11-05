package xuanmo.arcartxsuite.abyss.service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import xuanmo.arcartxsuite.abyss.reward.RewardResolver;
import xuanmo.arcartxsuite.abyss.stamina.StaminaManager;
import xuanmo.arcartxsuite.abyss.template.*;
import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;
import xuanmo.arcartxsuite.api.currency.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdmissionChargeTest {
    StaminaManager stamina;CooldownManager cooldown;RewardResolver rewards;
    CurrencyBridgeAPI currency;CurrencyBridgeAPI.CurrencyBridge money;RequirementChecker checker;AbyssTemplate template;
    @BeforeEach void setup() {
        stamina=mock(StaminaManager.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));when(stamina.ready(any())).thenReturn(true);when(stamina.getStamina(any())).thenReturn(100);
        when(stamina.consume(any(),anyInt())).thenReturn(true);
        cooldown=mock(CooldownManager.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));rewards=mock(RewardResolver.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        when(rewards.refundMoney(any(),anyDouble(),anyString(),anyString())).thenReturn(true);
        currency=mock(CurrencyBridgeAPI.class);money=mock(CurrencyBridgeAPI.CurrencyBridge.class);
        when(currency.bridge("money")).thenReturn(money);when(money.available()).thenReturn(true);
        when(money.withdraw(any(),any())).thenReturn(CurrencyTransactionResult.ok());
        checker=new RequirementChecker(mock(ScriptConditionEvaluator.class),currency,stamina,cooldown,rewards);
        template=mock(AbyssTemplate.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));when(template.id()).thenReturn("test");
        var yaml=new org.bukkit.configuration.file.YamlConfiguration();yaml.set("money-cost",5);yaml.set("stamina-cost",10);yaml.set("cooldown",30);
        when(template.requirements()).thenReturn(EntryRequirements.load(yaml));
    }
    Player player() {
        Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.isOnline()).thenReturn(true);
        var inventory=mock(PlayerInventory.class);when(player.getInventory()).thenReturn(inventory);
        var contents=new AtomicReference<>(new ItemStack[1]);when(inventory.getContents()).thenAnswer(call->contents.get());
        doAnswer(call->{contents.set(call.getArgument(0));return null;}).when(inventory).setContents(any(ItemStack[].class));
        return player;
    }
    @Test void secondMembersPaymentFailureRefundsEarlierMemberAndNeverStartsCooldown() {
        Player first=player(),second=player();when(money.withdraw(eq(second),any())).thenReturn(CurrencyTransactionResult.failure("denied"));
        var charge=checker.charge(template,List.of(first,second));assertFalse(charge.ok());
        assertEquals("NATIVE_COMPENSATED", charge.memberCosts().get(first.getUniqueId()));
        assertEquals("NO_NATIVE_CHARGE", charge.memberCosts().get(second.getUniqueId()));
        verify(rewards).refundMoney(eq(first),eq(5d),eq("money"),anyString());
        verify(stamina).add(first.getUniqueId(),10);verifyNoInteractions(cooldown);
    }
    @Test void teleportRollbackIsIdempotentAndCooldownOnlyStartsOnCommit() {
        Player player=player();var charge=checker.charge(template,List.of(player));assertTrue(charge.ok());
        verifyNoInteractions(cooldown);charge.rollback();charge.rollback();charge.commit();
        verify(rewards,times(1)).refundMoney(eq(player),eq(5d),eq("money"),anyString());verifyNoInteractions(cooldown);
        var success=checker.charge(template,List.of(player));success.commit();success.commit();
        verify(cooldown,times(1)).setCooldown(player.getUniqueId(),"test",30);
    }
    @Test void duplicateRequiredItemsAreAccountedTogetherBeforeAnyMoneyIsTaken() {
        var yaml=new org.bukkit.configuration.file.YamlConfiguration();yaml.set("money-cost",5);
        yaml.set("required-items",List.of("material:DIAMOND:1","material:DIAMOND:1"));
        when(template.requirements()).thenReturn(EntryRequirements.load(yaml));
        Player player=player();player.getInventory().setContents(new ItemStack[]{new ItemStack(Material.DIAMOND,1)});
        when(rewards.matches(anyString(),any())).thenReturn(true);
        assertFalse(checker.charge(template,List.of(player)).ok());
        verify(money,never()).withdraw(any(),any(BigDecimal.class));assertEquals(1,player.getInventory().getContents()[0].getAmount());
    }
    @Test void rollbackDoesNotOverwriteItemsChangedDuringTeleport() throws Exception {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("money-cost", 5);
        yaml.set("required-items", List.of("material:DIAMOND:1"));
        when(template.requirements()).thenReturn(EntryRequirements.load(yaml));
        Player player = player();
        when(rewards.matches(anyString(), any())).thenReturn(true);
        player.getInventory().setContents(new ItemStack[]{new ItemStack(Material.DIAMOND)});
        var serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        var server = mock(org.bukkit.Server.class);
        var factory = mock(ItemFactory.class);
        when(factory.equals(isNull(), isNull())).thenReturn(true);
        when(server.getItemFactory()).thenReturn(factory);
        try {
            serverField.set(null, server);
            var charge = checker.charge(template, List.of(player));
            assertTrue(charge.ok(), charge::reason);
            player.getInventory().setContents(new ItemStack[]{new ItemStack(Material.STONE)});
            assertThrows(IllegalStateException.class, charge::rollback);
            assertEquals("NATIVE_REVIEW_REQUIRED", charge.memberCosts().get(player.getUniqueId()));
            assertEquals(Material.STONE, player.getInventory().getContents()[0].getType());
            verify(rewards).refundMoney(eq(player), eq(5d), eq("money"), anyString());
            verifyNoInteractions(cooldown);
        } finally {
            serverField.set(null, previous);
        }
    }

    @Test void unloadedPlayerDataBlocksAllCharges() {
        Player player=player();when(stamina.ready(player.getUniqueId())).thenReturn(false);
        assertFalse(checker.charge(template,List.of(player)).ok());verify(money,never()).withdraw(any(),any());
    }
    @Test void leaderOnlyRequirementsUseTheActualLeaderEvenAfterLeadershipTransfer() {
        var yaml=new org.bukkit.configuration.file.YamlConfiguration();yaml.set("require-all-players",false);
        yaml.set("required-permissions",List.of("entry.gate"));when(template.requirements()).thenReturn(EntryRequirements.load(yaml));
        Player member=player(),leader=player();when(member.hasPermission("entry.gate")).thenReturn(true);
        var party=new xuanmo.arcartxsuite.abyss.party.Party(member.getUniqueId(),4);party.addMember(leader.getUniqueId());party.setLeader(leader.getUniqueId());
        assertFalse(checker.check(template,List.of(member,leader),party).ok());
        verify(leader).hasPermission("entry.gate");verify(member,never()).hasPermission(anyString());
        assertFalse(checker.check(template,List.of(member),party).ok());
    }
}
