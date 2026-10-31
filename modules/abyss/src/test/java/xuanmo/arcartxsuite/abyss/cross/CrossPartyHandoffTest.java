package xuanmo.arcartxsuite.abyss.cross;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstanceManager;
import xuanmo.arcartxsuite.abyss.party.PartyManager;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;
import xuanmo.arcartxsuite.api.crossserver.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrossPartyHandoffTest {
    Field serverField;Object previous;Server server;Player leader,member;
    AbyssService service;PartyManager parties;CrossDungeonManager manager;AbyssTemplate template;
    @BeforeEach void setup()throws Exception {
        server=mock(Server.class);serverField=Bukkit.class.getDeclaredField("server");serverField.setAccessible(true);previous=serverField.get(null);serverField.set(null,server);
        leader=player("Leader");member=player("Member");
        service=mock(AbyssService.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));parties=new PartyManager(new AbyssModuleConfiguration.PartySection(4,60,true),s->{},null);
        when(service.party()).thenReturn(parties);var instances=new AbyssInstanceManager();when(service.instances()).thenReturn(instances);
        template=mock(AbyssTemplate.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));when(template.id()).thenReturn("test");when(template.crossServer()).thenReturn("host");
        when(template.plans()).thenReturn(Map.of("route_a",new AbyssTemplate.PlanEntry("route_a","route",List.of())));
        instances.registerTemplate(template);
        when(service.enterDungeon(anyList(),eq("test"),eq("route_a"))).thenReturn(AbyssService.EnterResult.success());
        var api=mock(CrossServerAPI.class);when(api.nodeId()).thenReturn("host");
        manager=new CrossDungeonManager(service,api,null,Logger.getAnonymousLogger());
    }
    Player player(String name) {
        var p=mock(Player.class);when(p.getName()).thenReturn(name);when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.isOnline()).thenReturn(true);
        when(server.getPlayer(p.getUniqueId())).thenReturn(p);return p;
    }
    @AfterEach void restore()throws Exception {serverField.set(null,previous);}
    Map<String,String> context() {
        Map<String,String> context=new HashMap<>();context.put("req","request");context.put("from","source");context.put("_sender","source");
        context.put("dungeon","test");context.put("version","2");context.put("leader",leader.getUniqueId().toString());
        context.put("players",member.getUniqueId()+","+leader.getUniqueId());context.put("party","true");context.put("mode","guild");
        context.put("capacity","8");context.put("plan","route_a");return context;
    }
    void request(Map<String,String> context)throws Exception {
        var method=CrossDungeonManager.class.getDeclaredMethod("onReqEnter",Map.class);method.setAccessible(true);method.invoke(manager,context);
    }
    @Test void allArrivalsRebuildThePartyWithOriginalLeaderModeAndPlan()throws Exception {
        request(context());manager.onPlayerJoin(member);verify(service,never()).enterDungeon(anyList(),anyString(),any());
        manager.onPlayerJoin(leader);
        ArgumentCaptor<List<Player>> players=ArgumentCaptor.forClass(List.class);
        verify(service).enterDungeon(players.capture(),eq("test"),eq("route_a"));assertEquals(leader,players.getValue().get(0));
        var party=parties.partyOf(leader);assertSame(party,parties.partyOf(member));assertEquals("guild",party.mode());
        assertEquals(leader.getUniqueId(),party.leader());assertTrue(party.allReady());assertEquals(8,party.maxSize());
    }
    @Test void timeoutNeverStartsAnIncompleteParty()throws Exception {
        request(context());manager.onPlayerJoin(member);
        var expire=CrossDungeonManager.class.getDeclaredMethod("expirePendingIn",String.class);expire.setAccessible(true);expire.invoke(manager,"request");
        verify(service,never()).enterDungeon(anyList(),anyString(),any());assertNull(parties.partyOf(member));
    }
    @Test void unexpectedSenderCannotReservePlayers()throws Exception {
        var context=context();context.put("_sender","wrong-node");request(context);manager.onPlayerJoin(member);manager.onPlayerJoin(leader);
        verify(service,never()).enterDungeon(anyList(),anyString(),any());
    }
}
