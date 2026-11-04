package xuanmo.arcartxsuite.abyss.design;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.script.ScriptContext;
import xuanmo.arcartxsuite.abyss.script.ScriptRunner;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.abyss.stage.StageManager;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplateLoader;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DraftTrialTest {
    @Test void exampleDraftRunsThroughRoomActionBranchAndSettlement() throws Exception {
        rehearse("example", 90);
    }

    @Test void procgenDraftRunsThroughObjectActionBranchAndSettlement() throws Exception {
        rehearse("procgen", 60);
    }

    private void rehearse(String id, int seconds) throws Exception {
        var root = Path.of("src/main/resources");
        var directory = root.resolve("abysses").resolve(id);
        var config = directory.resolve("config.yml");
        byte[] before = Files.readAllBytes(config);
        var logger = Logger.getAnonymousLogger();
        var loader = new AbyssTemplateLoader(root.toFile(), logger);
        var template = loader.load(id, directory.toFile());
        assertNotNull(template);
        var design = new DesignSession(UUID.randomUUID(), new AbyssInstance(UUID.randomUUID(), template, null));
        var manager = new DesignManager(logger, (player, message) -> { });
        var load = DesignManager.class.getDeclaredMethod("loadObjects", template.getClass());
        load.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<DesignObject> objects = (List<DesignObject>) load.invoke(manager, template);
        design.putAll(objects);
        addStage(design, "first");
        addStage(design, "last");
        manager.setField(design, "stage:first", "design-entry", "true");
        manager.setField(design, "stage:first", "design-condition", "变量成立");
        manager.setField(design, "stage:first", "design-condition-ref", "draft_event");
        manager.setField(design, "stage:first", "design-branch-target", "last");
        manager.cycleNext(design, "stage:first", "design-append-branch");
        String event = id.equals("example") ? "on-clear" : "click-script";
        var kind = id.equals("example") ? DesignObject.Kind.ROOM : DesignObject.Kind.NPC;
        String key = objects.stream().filter(object -> object.kind() == kind).findFirst().orElseThrow().key();
        manager.setField(design, key, "design-action-hook", event);
        manager.setField(design, key, "design-action", "设置变量");
        manager.setField(design, key, "design-action-arg", "draft_event");
        manager.setField(design, key, "design-action-value", "yes");
        manager.cycleNext(design, key, "design-append-action");
        manager.lineAdd(design, key, event, "console prohibited");
        manager.lineAdd(design, key, event, "js: prohibited");
        var object = design.get(key);
        var draft = loader.loadPrepared(id, directory.toFile(), DesignWriter.prepare(template, design));
        assertNotNull(draft);
        assertTrue(template.stages().isEmpty());
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        Server server = mock(Server.class);
        when(server.isPrimaryThread()).thenReturn(true);
        serverField.set(null, server);
        try {
            var scheduler = mock(SchedulerAPI.class);
            var scheduled = new ArrayDeque<Runnable>();
            when(scheduler.scheduleOnce(isNull(), any(Runnable.class), anyLong())).thenAnswer(call -> {
                scheduled.add(call.getArgument(1));
                return mock(ScheduledTask.class);
            });
            var service = mock(AbyssService.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
            var stages = new StageManager(service, null, scheduler, logger);
            var runner = new ScriptRunner(null, scheduler, () -> service, logger);
            when(service.stages()).thenReturn(stages);
            stages.setScriptLauncher((instance, lines, trigger, vars) ->
                runner.run("stage", lines, ScriptContext.withVars(instance, trigger, vars), instance));
            try (var trial = new DraftTrial(draft, stages, runner, Map.of(new DraftTrial.Hook(key, event),
                    new DraftTrial.Event(object.id(), kind, object.strList(event))), line -> { })) {
                trial.start();
                while (!scheduled.isEmpty()) scheduled.remove().run();
                trial.event(key, event);
                trial.mapCleared();
                var trace = trial.traceSnapshot();
                assertTrue(trace.contains("分支 → last"), trace::toString);
                assertTrue(trace.contains("进入阶段 last"), trace::toString);
                assertTrue(trace.contains("通关结算 COUNTDOWN " + seconds + " 秒"), trace::toString);
                assertTrue(trace.contains("记录动作 grant_reward"), trace::toString);
                assertFalse(trace.stream().anyMatch(line -> line.contains("prohibited")), trace::toString);
                verify(service, never()).completeDungeon(any());
                verify(service, never()).rewards();
            }
            assertArrayEquals(before, Files.readAllBytes(config));
        } finally {
            serverField.set(null, previous);
        }
    }

    private void addStage(DesignSession design, String id) {
        var values = new LinkedHashMap<String, Object>();
        DesignManager.seedDefaults(DesignObject.Kind.STAGE, values);
        design.put(new DesignObject("stage:" + id, DesignObject.Kind.STAGE, id, "stages.yml",
            "", "", null, null, "misc:stages", values, true));
    }
}
