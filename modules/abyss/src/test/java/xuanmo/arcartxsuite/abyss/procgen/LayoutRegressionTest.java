package xuanmo.arcartxsuite.abyss.procgen;
import java.util.*;
import java.lang.reflect.Method;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import xuanmo.arcartxsuite.abyss.template.*;
class LayoutRegressionTest {
    ProcGenRoomAsset asset(String name,ProcGenRoomAsset.RoomKind kind,List<ProcGenRoomAsset.DoorSpec> doors) {
        @SuppressWarnings("unchecked") List<ProcGenRoomAsset.RoomMob>[] waves=new List[]{List.of()};
        return new ProcGenRoomAsset(name,kind,10,false,null,waves,List.of(),List.of(),List.of(),
            doors,List.of(),Map.of(),List.of(),null,RegionTemplate.of(21,8,21,List.of("minecraft:air"),new byte[8][21*21*2]));
    }
    @Test void twoHundredSeedsHaveNoMainChainShortcuts() {
        var pools=new EnumMap<ProcGenRoomAsset.RoomKind,List<ProcGenRoomAsset>>(ProcGenRoomAsset.RoomKind.class);
        for(var kind:ProcGenRoomAsset.RoomKind.values())pools.put(kind,List.of(asset(kind.name(),kind,List.of())));
        YamlConfiguration yaml=new YamlConfiguration();yaml.set("counts.combat","6");
        var cfg=ProcGenConfig.load(yaml,"test");
        for(int seed=0;seed<200;seed++){
            var layout=LayoutGenerator.generate(pools,cfg,seed,64,0,0);assertNotNull(layout);
            int n=layout.rooms.size(),boss=layout.rooms.indexOf(layout.boss);
            assertEquals(n-1,layout.edges.size(),"seed="+seed);
            int[] dist=new int[n];Arrays.fill(dist,999);dist[0]=0;
            for(int j=0;j<n;j++)for(var edge:layout.edges){
                int a=layout.rooms.indexOf(edge.a),b=layout.rooms.indexOf(edge.b);
                dist[a]=Math.min(dist[a],dist[b]+1);dist[b]=Math.min(dist[b],dist[a]+1);
                assertTrue(edge.axis=='x'?edge.a.cellI<edge.b.cellI:edge.a.cellJ<edge.b.cellJ);
            }
            assertEquals(7,dist[boss],"seed="+seed);
        }
    }
    @Test void fullDirectionNamesWorkWithoutRotation()throws Exception {
        var a=asset("a",ProcGenRoomAsset.RoomKind.COMBAT,List.of(new ProcGenRoomAsset.DoorSpec("east",3,5,5)));
        var placed=new LayoutGenerator.PlacedRoom(a,0,0,0,0,64,0,21,8,21,true);
        Method resolve=ProcGenMaterializer.class.getDeclaredMethod("resolveDoor",LayoutGenerator.PlacedRoom.class,char.class,int.class,int.class,int.class);
        resolve.setAccessible(true);
        assertArrayEquals(new int[]{5,3,5},(int[])resolve.invoke(new ProcGenMaterializer(java.util.logging.Logger.getAnonymousLogger()),placed,'e',10,5,5));
    }
    @Test void offsetDoorCorridorRemainsConnectedThroughBends()throws Exception {
        Map<String,Material> blocks=new HashMap<>();Map<String,Block> mocks=new HashMap<>();
        World world=mock(World.class);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(call->{
            String key=call.getArgument(0)+","+call.getArgument(1)+","+call.getArgument(2);
            return mocks.computeIfAbsent(key,k->{
                Block block=mock(Block.class);
                doAnswer(change->{blocks.put(k,change.getArgument(0));return null;}).when(block).setType(any(Material.class),anyBoolean());
                return block;
            });
        });
        var cfg=ProcGenConfig.load(new YamlConfiguration(),"test");
        Method connect=ProcGenMaterializer.class.getDeclaredMethod("connectCorridor",World.class,int.class,int.class,int.class,int[].class,int[].class,char.class,ProcGenConfig.class);
        connect.setAccessible(true);
        connect.invoke(new ProcGenMaterializer(java.util.logging.Logger.getAnonymousLogger()),world,22,35,64,new int[]{5,3,5},new int[]{5,17,5},'x',cfg);
        Set<String> seen=new HashSet<>();ArrayDeque<int[]> queue=new ArrayDeque<>();queue.add(new int[]{22,3});
        while(!queue.isEmpty()){
            int[] p=queue.remove();String key=p[0]+",65,"+p[1];
            if(!seen.add(key)||blocks.get(key)!=Material.AIR)continue;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                int x=p[0]+d[0],z=p[1]+d[1];String next=x+",65,"+z;
                if(!seen.contains(next)&&blocks.get(next)==Material.AIR)queue.add(new int[]{x,z});
            }
        }
        assertTrue(seen.contains("35,65,17"),"the far door must be reachable");
    }
}
