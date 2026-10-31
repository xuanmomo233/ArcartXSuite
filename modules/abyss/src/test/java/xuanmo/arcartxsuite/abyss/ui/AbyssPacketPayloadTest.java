package xuanmo.arcartxsuite.abyss.ui;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;
import xuanmo.arcartxsuite.abyss.design.*;
import xuanmo.arcartxsuite.abyss.instance.*;
import xuanmo.arcartxsuite.abyss.party.PartyManager;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.abyss.stats.CombatStatisticsService;
import xuanmo.arcartxsuite.abyss.template.*;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.scheduler.*;

class AbyssPacketPayloadTest {
    @TempDir Path dir;
    AbyssService service;AbyssUiService ui;Player player;AbyssTemplate template;AbyssInstance instance;
    List<Runnable> deferred;List<Map<String,Object>> packets;PacketBridgeAPI bridge;
    Field serverField;Object previousServer;
    @BeforeEach void setup()throws Exception {
        var cfg=AbyssModuleConfiguration.load(new YamlConfiguration());
        player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);when(player.getName()).thenReturn("Member1234567890");
        Server server=mock(Server.class);when(server.getPlayer(player.getUniqueId())).thenReturn(player);
        serverField=Bukkit.class.getDeclaredField("server");serverField.setAccessible(true);previousServer=serverField.get(null);serverField.set(null,server);
        bridge=mock(PacketBridgeAPI.class);when(bridge.isAvailable()).thenReturn(true);
        when(bridge.openUiUnsafe(any(),anyString())).thenReturn(true);
        deferred=new ArrayList<>();packets=new ArrayList<>();
        when(bridge.sendPacket(any(),anyString(),anyString(),any())).thenAnswer(call->{
            @SuppressWarnings("unchecked") Map<String,Object> payload=(Map<String,Object>)call.getArgument(3);
            packets.add(payload);return true;
        });
        SchedulerAPI scheduler=mock(SchedulerAPI.class);
        when(scheduler.scheduleOnce(any(),any(Runnable.class),anyLong())).thenAnswer(call->{
            deferred.add(call.getArgument(1));return mock(ScheduledTask.class);
        });
        Constructor<?> constructor=AbyssService.class.getDeclaredConstructors()[0];
        Object[] args=new Object[constructor.getParameterCount()];
        for(int i=0;i<args.length;i++) {
            Class<?> type=constructor.getParameterTypes()[i];
            if(type==AbyssModuleConfiguration.class)args[i]=cfg;
            if(type==SchedulerAPI.class)args[i]=scheduler;
            if(type==PacketBridgeAPI.class)args[i]=bridge;
            if(type==java.util.logging.Logger.class)args[i]=java.util.logging.Logger.getAnonymousLogger();
        }
        service=(AbyssService)constructor.newInstance(args);
        set("instances",new AbyssInstanceManager());
        set("partyManager",new PartyManager(cfg.party(),s->{},null));
        set("stats",new CombatStatisticsService(cfg.statistics()));
        var cs=AbyssTemplate.class.getRecordComponents();Class<?>[] types=Arrays.stream(cs).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] values=new Object[cs.length];
        for(int i=0;i<cs.length;i++){
            values[i]=types[i]==int.class?0:null;
            switch(cs[i].getName()){
                case "id","name" -> values[i]="test";
                case "mode" -> values[i]=AbyssTemplate.Mode.CLASSIC;
                case "floors" -> values[i]=FloorConfig.EMPTY;
                case "dir" -> values[i]=dir.toFile();
            }
        }
        template=AbyssTemplate.class.getDeclaredConstructor(types).newInstance(values);
        instance=new AbyssInstance(UUID.randomUUID(),template,null);instance.addPlayer(player);
        ui=new AbyssUiService(service,bridge,null,java.util.logging.Logger.getAnonymousLogger());
    }
    @AfterEach void restore()throws Exception {if(serverField!=null)serverField.set(null,previousServer);}
    void set(String field,Object value)throws Exception{Field f=AbyssService.class.getDeclaredField(field);f.setAccessible(true);f.set(service,value);}
    Map<String,Object> flush(){var jobs=List.copyOf(deferred);deferred.clear();jobs.forEach(Runnable::run);return packets.get(packets.size()-1);}
    void assertClientFields(String name,Map<String,Object> payload)throws Exception {
        var yaml=new YamlConfiguration();yaml.load(Path.of("src/main/resources/arcartx/ui/abyss_"+name+".yml").toFile());
        String handler=yaml.getString("ui.packetHandler.init");
        var matcher=Pattern.compile("packet\\['([^']+)'\\]").matcher(handler);
        while(matcher.find())assertTrue(payload.containsKey(matcher.group(1)),name+" missing "+matcher.group(1));
    }
    @Test void emptyBrowserAndLobbySupplyAllClientFields()throws Exception {
        ui.openBrowser(player);assertClientFields("browser",flush());
        ui.openLobby(player);assertClientFields("lobby",flush());
    }
    @Test void oldWindowTokenCannotCreatePartyAfterNavigation() {
        ui.openBrowser(player);String oldToken=(String)flush().get("action_token");
        ui.openLobby(player);String fresh=(String)flush().get("action_token");assertNotEquals(oldToken,fresh);
        ui.handlePacket(player,"abyss",List.of("lobby_create",oldToken));assertNull(service.party().partyOf(player));
        ui.handlePacket(player,"abyss",List.of("lobby_create",fresh));assertNotNull(service.party().partyOf(player));
    }
    @Test void chestOnlyConsumesAnItemAfterHostPersistenceConfirmation()throws Exception {
        var rewards=mock(xuanmo.arcartxsuite.abyss.reward.RewardResolver.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));set("rewards",rewards);
        when(rewards.rollChestOptions(any(),anyInt())).thenReturn(List.of(new org.bukkit.inventory.ItemStack(Material.STONE,2),new org.bukkit.inventory.ItemStack(Material.STONE,3)));
        when(rewards.queueItems(any(),anyList(),anyString())).thenReturn(false,true);
        ui.open(player,new RewardConfig.ChestReward("test","test",1,List.of()),instance);
        String token=(String)flush().get("action_token");
        var field=AbyssUiService.class.getDeclaredField("pendingChests");field.setAccessible(true);
        var pending=(Map<?,?>)field.get(ui);Object chest=pending.get(player.getUniqueId());
        var items=chest.getClass().getDeclaredMethod("items");items.setAccessible(true);
        ui.handlePacket(player,"abyss",List.of("chest_claim_one","0",token));
        assertEquals(2,((List<?>)items.invoke(chest)).size());
        ui.handlePacket(player,"abyss",List.of("chest_claim_one","0",token));
        assertEquals(1,((List<?>)items.invoke(chest)).size());
        verify(rewards,times(2)).queueItems(eq(player),anyList(),anyString());
    }
    Player peer()throws Exception {
        Player peer=mock(Player.class);when(peer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(peer.getName()).thenReturn("Applicant");when(peer.isOnline()).thenReturn(true);
        Server server=(Server)serverField.get(null);when(server.getPlayer(peer.getUniqueId())).thenReturn(peer);
        return peer;
    }
    @Test void invitationRefreshesAnAlreadyOpenLobby()throws Exception {
        Player peer=peer();service.party().create(player);
        ui.openParty(player);String leaderToken=(String)flush().get("action_token");
        ui.openLobby(peer);flush();
        ui.handlePacket(player,"abyss",List.of("party_invite_uuid",peer.getUniqueId().toString(),leaderToken));
        verify(bridge).sendPacket(eq(peer),eq("AXS:abyss_lobby"),eq("lobby_list"),any());
        assertEquals(1,service.party().inviteEntries(peer.getUniqueId()).size());
    }
    @Test void applicationRefreshesLeaderAndApprovalOpensApplicantRoom()throws Exception {
        Player peer=peer();var party=service.party().create(player);party.listed(true);
        ui.openParty(player);String leaderToken=(String)flush().get("action_token");
        ui.openLobby(peer);String peerToken=(String)flush().get("action_token");
        ui.handlePacket(peer,"abyss",List.of("lobby_join",party.id().toString(),peerToken));
        verify(bridge).sendPacket(eq(player),eq("AXS:abyss_party"),eq("update"),
            argThat(value->value instanceof Map<?,?> map && Integer.valueOf(1).equals(map.get("applicantCount"))));
        ui.handlePacket(player,"abyss",List.of("party_apply_ok",peer.getUniqueId().toString(),leaderToken));
        assertSame(party,service.party().partyOf(peer));
        verify(bridge).openUiUnsafe(peer,"AXS:abyss_party");
        assertEquals(0,party.applications().size());
    }
    @Test void zeroKillPlayersAreIncludedInStatistics()throws Exception {
        ui.openStats(player,instance);var payload=flush();assertClientFields("stats",payload);
        assertEquals(1,payload.get("statCount"));
        @SuppressWarnings("unchecked") var rows=(Map<String,Map<String,Object>>)payload.get("statRows");
        assertEquals("Member1234567890",rows.get("0").get("name"));assertEquals("0",rows.get("0").get("killsText"));
    }
    @Test void allShopGoodsAreVisibleAndClientMetadataIsComplete()throws Exception {
        List<ShopConfig.Goods> goods=new ArrayList<>();
        for(int i=0;i<12;i++)goods.add(new ShopConfig.Goods("g"+i,"STONE","item"+i,List.of(),i,List.of()));
        ui.openShop(player,new ShopConfig.Shop("shop","商店","coins",goods),instance);
        var payload=flush();assertClientFields("shop",payload);assertEquals(12,payload.get("goodsCount"));
        assertEquals("0",payload.get("coinsText"));
        @SuppressWarnings("unchecked") var rows=(Map<String,Map<String,Object>>)payload.get("goods");
        assertEquals("11",rows.get("11").get("priceText"));
    }
    @Test void partyChatSendsEveryWrappedLineWithClientDisplayFields()throws Exception {
        var party=service.party().create(player);
        String message="队伍消息".repeat(12);party.appendFeed(player.getName(),message);
        ui.openParty(player);var payload=flush();assertClientFields("party",payload);
        @SuppressWarnings("unchecked") var rows=(Map<String,Map<String,Object>>)payload.get("feedRows");
        var lines=rows.values().stream().filter(r->message.equals(r.get("text")))
            .map(r->(String)r.get("display")).toList();
        assertTrue(lines.size()>1);
        String visible=ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',String.join("",lines)));
        assertTrue(visible.endsWith(message));
    }
    @Test void integralAriaPacketNumbersAreAcceptedWithoutTruncatingFractions()throws Exception {
        var parser=AbyssUiService.class.getDeclaredMethod("parseInt",String.class,int.class);parser.setAccessible(true);
        assertEquals(1,parser.invoke(null,"1.0",-9));assertEquals(-1,parser.invoke(null,"-1.0",-9));
        assertEquals(-9,parser.invoke(null,"1.5",-9));assertEquals(-9,parser.invoke(null,"NaN",-9));
        assertEquals(-9,parser.invoke(null,"2147483648",-9));
    }
    @Test void buffCandidatesAreNotTruncatedAndMultiPickMetadataIsSent()throws Exception {
        List<BuffConfig.Option> options=new ArrayList<>();
        for(int i=0;i<7;i++)options.add(new BuffConfig.Option("b"+i,"buff"+i,"","common",1,List.of(),List.of()));
        var pool=new BuffConfig.Pool("pool","祝福",2,7,"panel",20,"none",false,options);
        ui.openBuffOffer(player,instance,pool,options,20);
        var payload=flush();assertClientFields("buff",payload);
        assertEquals(7,payload.get("count"));assertEquals(2,payload.get("picksLeft"));
    }
    @Test void documentPagesSendBoundedRowsAndAbsoluteOffsets()throws Exception {
        Files.writeString(dir.resolve("scripts.yml"),"# line\n".repeat(35)+"start: []\n");
        var manager=new DesignManager(java.util.logging.Logger.getAnonymousLogger(),(p,m)->{});
        set("designManager",manager);var session=manager.open(player,instance);
        session.linePage("document:scripts.yml","content",1);
        ui.openDesignEdit(player,session,"document:scripts.yml");
        var payload=flush();assertClientFields("design_edit",payload);
        @SuppressWarnings("unchecked") var groups=(Map<String,Map<String,Object>>)payload.get("groupRows");
        var group=groups.get("0");
        assertEquals(12,group.get("lineOffset"));assertEquals(12,group.get("lineCount"));assertEquals(36,group.get("lineTotal"));
    }
    @Test void designInstancesDoNotConsumePlayerInstanceCapacity()throws Exception {
        var cs=AbyssTemplate.class.getRecordComponents();Object[] values=new Object[cs.length];
        Class<?>[] types=Arrays.stream(cs).map(RecordComponent::getType).toArray(Class<?>[]::new);
        for(int i=0;i<cs.length;i++)values[i]=cs[i].getName().equals("maxInstances")?1:cs[i].getAccessor().invoke(template);
        var limited=AbyssTemplate.class.getDeclaredConstructor(types).newInstance(values);
        var manager=new AbyssInstanceManager();manager.createInstance(limited,null).designMode(true);
        assertEquals(0,manager.getInstanceCount("test"));assertTrue(manager.hasAvailableSlot(limited));
        manager.createInstance(limited,null);assertEquals(1,manager.getInstanceCount("test"));
        assertFalse(manager.hasAvailableSlot(limited));
    }
}
