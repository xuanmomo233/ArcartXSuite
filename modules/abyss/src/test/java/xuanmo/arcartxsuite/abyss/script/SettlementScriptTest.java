package xuanmo.arcartxsuite.abyss.script;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;
import xuanmo.arcartxsuite.abyss.instance.*;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.abyss.template.*;
import xuanmo.arcartxsuite.api.scheduler.*;

class SettlementScriptTest {
    AbyssService service;AbyssInstance instance;ScriptRunner runner;
    List<Runnable> tasks=new ArrayList<>();AtomicInteger marks=new AtomicInteger();
    Field serverField;Object previousServer;
    @BeforeEach void setup()throws Exception {
        Server server=mock(Server.class);when(server.isPrimaryThread()).thenReturn(true);
        serverField=Bukkit.class.getDeclaredField("server");serverField.setAccessible(true);previousServer=serverField.get(null);serverField.set(null,server);
        SchedulerAPI scheduler=mock(SchedulerAPI.class);
        when(scheduler.scheduleOnce(any(),any(Runnable.class),anyLong())).thenAnswer(call->{
            tasks.add(call.getArgument(1));return mock(ScheduledTask.class);
        });
        Constructor<?> ctor=AbyssService.class.getDeclaredConstructors()[0];
        Object[] arguments=new Object[ctor.getParameterCount()];
        for(int i=0;i<arguments.length;i++){
            Class<?> type=ctor.getParameterTypes()[i];
            if(type==Logger.class)arguments[i]=Logger.getAnonymousLogger();
            if(type==AbyssModuleConfiguration.class)arguments[i]=AbyssModuleConfiguration.load(new YamlConfiguration());
            if(type==SchedulerAPI.class)arguments[i]=scheduler;
        }
        service=(AbyssService)ctor.newInstance(arguments);
        var yaml=new YamlConfiguration();
        yaml.set("complete",List.of("mark complete","wait 1t","run_script bonus"));
        yaml.set("bonus",List.of("mark bonus"));yaml.set("fail",List.of("mark fail"));yaml.set("exit",List.of("mark exit"));
        var cs=AbyssTemplate.class.getRecordComponents();
        Class<?>[] types=Arrays.stream(cs).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] values=new Object[cs.length];
        for(int i=0;i<cs.length;i++){
            values[i]=types[i]==int.class?0:null;
            if(cs[i].getName().equals("id") || cs[i].getName().equals("name"))values[i]="test";
            if(cs[i].getName().equals("scripts"))values[i]=ScriptConfig.load(yaml);
        }
        var template=AbyssTemplate.class.getDeclaredConstructor(types).newInstance(values);
        instance=new AbyssInstance(UUID.randomUUID(),template,null);
        runner=new ScriptRunner(null,scheduler,()->service,Logger.getAnonymousLogger());
        runner.actions().register("mark",(context,args,session)->marks.incrementAndGet());
        Field field=AbyssService.class.getDeclaredField("scriptRunner");field.setAccessible(true);field.set(service,runner);
    }
    @AfterEach void restore()throws Exception {if(serverField!=null)serverField.set(null,previousServer);}
    @Test void completionRewardsAndWaitedCustomScriptRunAfterTerminalTransition() {
        instance.transitionTo(AbyssStatus.PREPARING,AbyssStatus.COMPLETED);
        service.runLifecycleScript(instance,"complete",null);
        assertEquals(1,marks.get());assertEquals(1,tasks.size());
        tasks.get(0).run();assertEquals(2,marks.get());
    }
    @Test void failureAndExitLifecycleScriptsAreNotSilentlyDropped() {
        instance.transitionTo(AbyssStatus.PREPARING,AbyssStatus.FAILED);
        service.runLifecycleScript(instance,"fail",null);service.runLifecycleScript(instance,"exit",null);
        assertEquals(2,marks.get());
    }
    @Test void normalGameplayWaitDoesNotContinueAfterCompletion() {
        instance.transitionTo(AbyssStatus.PREPARING,AbyssStatus.ACTIVE);
        service.runScript(instance,List.of("mark before","wait 1t","mark after"),ScriptContext.of(instance,null));
        instance.transitionTo(AbyssStatus.ACTIVE,AbyssStatus.COMPLETED);
        tasks.get(0).run();assertEquals(1,marks.get());
        assertNull(service.runScript(instance,List.of("mark blocked"),ScriptContext.of(instance,null)));
    }
    @Test void designInstancesCannotBeRecordedAsGameplayCompletions() {
        instance.designMode(true);service.completeDungeon(instance);
        assertEquals(AbyssStatus.PREPARING,instance.getStatus());assertEquals(0,marks.get());
    }
}
