package xuanmo.arcartxsuite.abyss.service;
import java.util.*;
import java.lang.reflect.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;
import xuanmo.arcartxsuite.abyss.party.*;
class PartyEntryTest {
    AbyssService service;PartyManager parties;Party party;
    Player leader,member;
    @BeforeEach void setup()throws Exception {
        Constructor<?> constructor=AbyssService.class.getDeclaredConstructors()[0];
        Object[] args=new Object[constructor.getParameterCount()];
        for(int i=0;i<args.length;i++)if(constructor.getParameterTypes()[i]==java.util.logging.Logger.class)
            args[i]=java.util.logging.Logger.getAnonymousLogger();
        service=(AbyssService)constructor.newInstance(args);
        parties=new PartyManager(new AbyssModuleConfiguration.PartySection(4,60,true),s->{},null);
        Field f=AbyssService.class.getDeclaredField("partyManager");f.setAccessible(true);f.set(service,parties);
        leader=mock(Player.class);member=mock(Player.class);
        when(leader.getUniqueId()).thenReturn(UUID.randomUUID());when(member.getUniqueId()).thenReturn(UUID.randomUUID());
        when(leader.isOnline()).thenReturn(true);when(member.isOnline()).thenReturn(true);
        party=parties.create(leader);party.addMember(member.getUniqueId());
        Field table=PartyManager.class.getDeclaredField("playerParty");table.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID,Party> memberships=(Map<UUID,Party>)table.get(parties);
        memberships.put(member.getUniqueId(),party);
    }
    @Test void memberCannotStartWholePartyFromBrowser() {
        var result=service.enterFromPlayer(member,"dungeon",null);
        assertFalse(result.ok());assertEquals("error.not-party-leader",result.reason());
    }
    @Test void leaderMustWaitForReadiness() {
        var result=service.enterFromPlayer(leader,"dungeon",null);
        assertFalse(result.ok());assertEquals("party.not-all-ready",result.reason());
    }
    @Test void offlineMembersCannotBeSilentlyExcluded()throws Exception {
        party.toggleReady(member.getUniqueId());
        Server server=mock(Server.class);when(server.getPlayer(leader.getUniqueId())).thenReturn(leader);
        Field field=Bukkit.class.getDeclaredField("server");field.setAccessible(true);Object old=field.get(null);
        try {
            field.set(null,server);
            var result=service.enterFromPlayer(leader,"dungeon",null);
            assertFalse(result.ok());assertEquals("队伍成员未全部在线",result.reason());
        } finally {field.set(null,old);}
    }
}
