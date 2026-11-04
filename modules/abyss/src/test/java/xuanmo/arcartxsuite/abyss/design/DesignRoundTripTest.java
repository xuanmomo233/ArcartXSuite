package xuanmo.arcartxsuite.abyss.design;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import xuanmo.arcartxsuite.abyss.template.*;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;

class DesignRoundTripTest {
    @TempDir Path dir;
    final DesignManager manager=new DesignManager(Logger.getAnonymousLogger(),(p,m)->{});
    AbyssTemplate template;
    DesignSession session;
    void write(String file,String text)throws Exception { Files.writeString(dir.resolve(file),text,StandardCharsets.UTF_8); }
    YamlConfiguration read(String file)throws Exception {return DesignWriter.load(dir.resolve(file));}
    void open(boolean floorMode)throws Exception {
        var components=AbyssTemplate.class.getRecordComponents();
        Class<?>[] types=Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] values=new Object[components.length];
        for(int i=0;i<components.length;i++){
            values[i]=types[i]==int.class?0:null;
            switch(components[i].getName()){
                case "id" -> values[i]="test";
                case "mode" -> values[i]=floorMode?AbyssTemplate.Mode.FLOORS:AbyssTemplate.Mode.CLASSIC;
                case "floors" -> values[i]=floorMode?FloorConfig.load(read("floors.yml"),read("rooms.yml")):FloorConfig.EMPTY;
                case "dir" -> values[i]=dir.toFile();
            }
        }
        template=AbyssTemplate.class.getDeclaredConstructor(types).newInstance(values);
        session=new DesignSession(UUID.randomUUID(),new AbyssInstance(UUID.randomUUID(),template,null));
        Method load=DesignManager.class.getDeclaredMethod("loadObjects",AbyssTemplate.class);
        load.setAccessible(true);
        @SuppressWarnings("unchecked") List<DesignObject> objects=(List<DesignObject>)load.invoke(manager,template);
        session.putAll(objects);
        DesignWriter.captureBaseline(template,session);
    }
    DesignWriter.Result save(){return DesignWriter.save(template,session,Logger.getAnonymousLogger());}
    void roomFixture()throws Exception {
        write("floors.yml","floors:\n- id: f1\n  rooms: [room_a]\n");
        write("rooms.yml","rooms:\n  room_a:\n    origin: '100,0,200'\n    region: ['0,60,0','20,80,20']\n    spawn: '1,64,1'\n    groups:\n      wave_1:\n        monsters: [{mob-id: 'vanilla:zombie', location: '2,64,2', amount: 1}]\n");
        open(true);
    }
    @Test void legacyScriptAppendPreservesExistingLines()throws Exception {
        write("monsters.yml","wave_1:\n  on_end: [old_action]\n");open(false);
        manager.lineAdd(session,"group:wave_1","on-end","new_action");
        assertTrue(save().ok());
        assertEquals(List.of("old_action","new_action"),MonsterConfig.load(read("monsters.yml")).groups().get("wave_1").onEnd());
    }
    @Test void clearingLegacyScriptDoesNotResurrectAlias()throws Exception {
        write("monsters.yml","wave_1:\n  on_end: [old_action]\n");open(false);
        manager.lineDel(session,"group:wave_1","on-end",0);
        assertTrue(save().ok());assertTrue(MonsterConfig.load(read("monsters.yml")).groups().get("wave_1").onEnd().isEmpty());
    }
    @Test void roomRenameMovesChildrenAndFloorReferences()throws Exception {
        roomFixture();
        assertTrue(manager.rename(session,"room:room_a","room_b"));
        manager.setField(session,"group:room_a.wave_1","delay-seconds","2");
        assertTrue(save().ok());
        assertFalse(read("rooms.yml").contains("rooms.room_a"));
        assertEquals(2,read("rooms.yml").getInt("rooms.room_b.groups.wave_1.delay-seconds"));
        assertEquals(List.of("room_b"),FloorConfig.load(read("floors.yml"),read("rooms.yml")).floors().get(0).roomRefs());
        assertNotNull(read("rooms.yml").get("rooms.room_b.groups.wave_1"));
    }
    @Test void groupRenameMigratesDependenciesAndScripts()throws Exception {
        write("monsters.yml","wave_1: {}\nwave_2:\n  trigger-after: [wave_1]\n  on-end: ['spawn_group wave_1']\n");open(false);
        assertTrue(manager.rename(session,"group:wave_1","first"));
        assertTrue(save().ok());
        assertEquals(List.of("first"),read("monsters.yml").getStringList("wave_2.trigger-after"));
        assertEquals(List.of("spawn_group first"),read("monsters.yml").getStringList("wave_2.on-end"));
    }
    @Test void referencedDeletionIsRejectedAndRetainsDraft()throws Exception {
        write("monsters.yml","wave_1: {}\nwave_2:\n  trigger-after: [wave_1]\n");open(false);
        byte[] before=Files.readAllBytes(dir.resolve("monsters.yml"));
        manager.delete(session,"group:wave_1");
        assertFalse(save().ok());assertTrue(session.hasDirty());
        assertArrayEquals(before,Files.readAllBytes(dir.resolve("monsters.yml")));
    }
    @Test void roomCoordinatesAreStoredRelativeAndSpawnAboveClickedFloor()throws Exception {
        roomFixture();
        Method mark=DesignManager.class.getDeclaredMethod("landPoint",DesignSession.class,org.bukkit.entity.Player.class,
            DesignSession.PendingMark.class,org.bukkit.Location.class);
        mark.setAccessible(true);
        mark.invoke(manager,session,null,new DesignSession.PendingMark(DesignSession.PendingMark.Kind.POINT,
            "group:room_a.wave_1","monsters",DesignObject.Kind.ROOM_GROUP),new org.bukkit.Location(null,105,63,205));
        assertTrue(save().ok());
        var spawns=FloorConfig.load(read("floors.yml"),read("rooms.yml")).rooms().get("room_a").groups().get("wave_1").monsters();
        assertEquals("5,64,5",spawns.get(1).location());assertEquals("vanilla:zombie",spawns.get(1).mobId());
    }
    @Test void nextIdDoesNotCollideAfterDeletion()throws Exception {
        write("npcs.yml","point_1: {}\npoint_3: {}\n");open(false);
        assertEquals("point_2",session.nextId(DesignObject.Kind.NPC,"point"));
        manager.delete(session,"npc:point_1");
        assertEquals("point_2",session.nextId(DesignObject.Kind.NPC,"point"));
    }
    @Test void duplicatePersistedIdsAreRejected()throws Exception {
        write("npcs.yml","one: {name: original}\n");open(false);
        session.put(new DesignObject("npc:other",DesignObject.Kind.NPC,"one","npcs.yml","","",
            null,null,"misc:objects",new LinkedHashMap<>(Map.of("name","replacement")),true));
        assertFalse(save().ok());assertEquals("original",read("npcs.yml").getString("one.name"));
    }
    @Test void structuredMonsterRowsKeepTypesAndCanBeEdited()throws Exception {
        write("monsters.yml","wave_1:\n  monsters: [{mob-id: 'vanilla:zombie', location: '0,65,0', amount: 1}]\n");open(false);
        manager.lineSet(session,"group:wave_1","monsters",0,
            "{\"mob-id\":\"vanilla:skeleton\",\"location\":\"1,65,1\",\"amount\":3,\"level\":2}");
        assertTrue(save().ok());
        assertEquals(3,MonsterConfig.load(read("monsters.yml")).groups().get("wave_1").monsters().get(0).amount());
        assertTrue(manager.lines(session.get("group:wave_1"),"monsters").get(0).contains("skeleton"));
    }
    @Test void rewardTypeActuallyEnablesRandomPool()throws Exception {
        write("rewards.yml","rewards:\n  base: {money: 1}\n  pack:\n    type: fixed\n    pool: [{reward: base, weight: 10}]\n");open(false);
        manager.setField(session,"reward:pack","type","random");
        assertTrue(save().ok());assertTrue(RewardConfig.load(read("rewards.yml")).rewards().get("pack").random());
    }
    @Test void externalChangesPreventLostUpdates()throws Exception {
        write("npcs.yml","one: {name: original}\n");open(false);
        manager.setField(session,"npc:one","name","draft");
        write("npcs.yml","one: {name: external}\n");
        assertFalse(save().ok());assertTrue(session.hasDirty());
        assertEquals("external",read("npcs.yml").getString("one.name"));
        assertTrue(Files.isDirectory(dir.resolve(".design-drafts")));
    }
    @Test void invalidDocumentRetainsDraftAndOriginalBytes()throws Exception {
        write("scripts.yml","start: [original]\n");open(false);
        manager.lineSet(session,"document:scripts.yml","content",0,"start: [broken");
        assertFalse(save().ok());assertEquals(List.of("original"),read("scripts.yml").getStringList("start"));
        assertTrue(session.hasDirty());
    }
    @Test void documentAndObjectCannotOverwriteEachOther()throws Exception {
        write("npcs.yml","one: {name: original}\n");open(false);
        manager.setField(session,"npc:one","name","object");
        manager.lineSet(session,"document:npcs.yml","content",0,"one: {name: document}");
        assertFalse(save().ok());assertEquals("original",read("npcs.yml").getString("one.name"));
    }
    @Test void documentAndDeletionCannotOverwriteEachOther()throws Exception {
        write("npcs.yml","one: {name: original}\n");open(false);
        manager.delete(session,"npc:one");
        manager.lineSet(session,"document:npcs.yml","content",0,"one: {name: document}");
        assertFalse(save().ok());
        assertEquals("original",read("npcs.yml").getString("one.name"));
    }
    @Test void documentEditorCanCreateFloorAndRoomTogether()throws Exception {
        open(false);
        manager.lineAdd(session,"document:floors.yml","content","floors: [{id: f1, rooms: [new_room]}]");
        manager.lineAdd(session,"document:rooms.yml","content","rooms: {new_room: {origin: '0,0,0', region: ['0,60,0','10,70,10']}}");
        manager.lineAdd(session,"document:stages.yml","content","stages: {intro: {on-enter: ['message @all hello']}}");
        assertTrue(save().ok());
        assertTrue(FloorConfig.load(read("floors.yml"),read("rooms.yml")).rooms().containsKey("new_room"));
        assertTrue(read("stages.yml").contains("stages.intro"));
    }
    @Test void floorFormAddsAndReordersRoutesWithoutWritingEditorFields() throws Exception {
        roomFixture();
        var values = new LinkedHashMap<String, Object>();
        DesignManager.seedDefaults(DesignObject.Kind.FLOOR, values);
        values.put("rooms", List.of("room_a"));
        values.put("design-order", 0);
        session.put(new DesignObject("floor:first", DesignObject.Kind.FLOOR, "first",
            "floors.yml", "floors", "楼层路线", null, null, "misc:layout", values, true));
        assertTrue(save().ok());
        var floors = FloorConfig.load(read("floors.yml"), read("rooms.yml")).floors();
        assertEquals(List.of("first", "f1"), floors.stream().map(FloorConfig.Floor::id).toList());
        assertFalse(read("floors.yml").getMapList("floors").get(0).containsKey("design-order"));
    }

    @Test void settlementFormKeepsFailureOverridesAndUnrelatedConfiguration() throws Exception {
        write("config.yml", "name: test\nsettlement:\n  failure: {exit-mode: IMMEDIATE}\n");
        open(false);
        manager.setField(session, "settlement:settlement", "exit-mode", "MANUAL");
        manager.setField(session, "settlement:settlement", "manual-timeout-seconds", "45");
        assertTrue(save().ok());
        var yaml = read("config.yml");
        var settlement = SettlementConfig.load(yaml.getConfigurationSection("settlement"));
        assertEquals(SettlementConfig.ExitMode.MANUAL, settlement.complete().mode());
        assertEquals(45, settlement.complete().manualTimeoutSeconds());
        assertEquals(SettlementConfig.ExitMode.IMMEDIATE, settlement.failure().mode());
        assertEquals("test", yaml.getString("name"));
    }

    @Test void successfulSaveBacksUpOriginalBytes()throws Exception {
        write("npcs.yml","# original comment\none: {name: original}\n");open(false);
        byte[] before=Files.readAllBytes(dir.resolve("npcs.yml"));
        manager.setField(session,"npc:one","name","updated");assertTrue(save().ok());
        try(var stream=Files.walk(dir.resolve(".design-backups"))){
            Path backup=stream.filter(p->p.getFileName().toString().equals("npcs.yml")).findFirst().orElseThrow();
            assertArrayEquals(before,Files.readAllBytes(backup));
        }
    }
    @Test void failedSecondReplacementRollsBackAlreadyWrittenFile()throws Exception {
        write("a.yml","old-a");write("b.yml","old-b");
        Map<Path,byte[]> writes=new LinkedHashMap<>();
        writes.put(dir.resolve("a.yml"),"new-a".getBytes(StandardCharsets.UTF_8));
        writes.put(dir.resolve("b.yml"),"new-b".getBytes(StandardCharsets.UTF_8));
        AtomicInteger calls=new AtomicInteger();
        assertThrows(Exception.class,()->DesignWriter.commit(writes,Map.of(),dir,(from,to)->{
            if(calls.incrementAndGet()==2)throw new java.io.IOException("simulated disk failure");
            Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);
        }));
        assertEquals("old-a",Files.readString(dir.resolve("a.yml")));assertEquals("old-b",Files.readString(dir.resolve("b.yml")));
    }
    @Test void snapshotPublicationSelectsLocalWorldAndSkipsTerrainRegeneration()throws Exception {
        write("config.yml","world: {template: void}\n");open(false);
        session.worldSnapshot(true);assertTrue(save().ok());
        assertEquals("world",read("config.yml").getString("world.template"));
        assertTrue(read("config.yml").getBoolean("world.design-snapshot"));
    }
    @Test void invalidNumbersNeverBecomeYamlStrings()throws Exception {
        write("rewards.yml","rewards: {pack: {money: 1}}\n");open(false);
        assertThrows(IllegalArgumentException.class,()->manager.setField(session,"reward:pack","money","NaN"));
        assertThrows(IllegalArgumentException.class,()->manager.setField(session,"reward:pack","money","oops"));
        assertNull(DesignCoords.point("NaN,64,0"));assertNull(DesignCoords.point("Infinity,64,0"));
        assertArrayEquals(new int[]{-1,64,-2},DesignCoords.point("-0.5,64,-1.5"));
    }
    @Test void chineseIdsRemainEditableAndMarkersDoNotCollide()throws Exception {
        write("rewards.yml","rewards: {深渊加赏: {money: 1}}\n");open(false);
        assertTrue(manager.rename(session,"reward:深渊加赏","黄金礼包"));assertTrue(save().ok());
        assertTrue(read("rewards.yml").contains("rewards.黄金礼包"));
        Method marker=DesignManager.class.getDeclaredMethod("markerId",String.class);marker.setAccessible(true);
        assertNotEquals(marker.invoke(null,"npc:甲乙"),marker.invoke(null,"npc:丙丁"));
    }
    @Test void renamesCannotConsumeAnotherUnsavedOriginalAddress()throws Exception {
        write("npcs.yml","a: {}\nb: {}\n");open(false);
        assertTrue(manager.rename(session,"npc:b","c"));
        assertFalse(manager.rename(session,"npc:a","b"));
        assertTrue(save().ok());assertNotNull(read("npcs.yml").get("a"));assertNotNull(read("npcs.yml").get("c"));
    }
    @Test void globalRenameDoesNotRewriteRoomLocalDependencies()throws Exception {
        roomFixture();
        var rooms=read("rooms.yml");rooms.set("rooms.room_a.groups.wave_2.trigger-after",List.of("wave_1"));
        rooms.save(dir.resolve("rooms.yml").toFile());
        write("monsters.yml","wave_1: {}\n");open(true);
        assertTrue(manager.rename(session,"group:wave_1","global_first"));assertTrue(save().ok());
        assertEquals(List.of("wave_1"),read("rooms.yml").getStringList("rooms.room_a.groups.wave_2.trigger-after"));
    }
}
