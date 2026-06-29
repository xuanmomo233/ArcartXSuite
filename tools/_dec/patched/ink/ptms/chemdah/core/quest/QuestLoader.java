/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.event.InternalEvent
 *  ink.ptms.chemdah.taboolib.common.event.InternalEventBus
 *  ink.ptms.chemdah.taboolib.common.io.FileKt
 *  ink.ptms.chemdah.taboolib.common.io.ProjectScannerKt
 *  ink.ptms.chemdah.taboolib.common.platform.Awake
 *  ink.ptms.chemdah.taboolib.common.platform.event.EventPriority
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.ListenerKt
 *  ink.ptms.chemdah.taboolib.common.util.LocaleKt
 *  ink.ptms.chemdah.taboolib.common.util.SyncExecutorKt
 *  ink.ptms.chemdah.taboolib.common5.FileWatcher
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.library.reflex.ClassAnnotation
 *  ink.ptms.chemdah.taboolib.library.reflex.ReflexClass
 *  ink.ptms.chemdah.taboolib.module.configuration.ConfigNode
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration$Companion
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion
 *  ink.ptms.chemdah.taboolib.platform.bukkit.Parallel
 *  kotlin.Metadata
 *  kotlin1822.TuplesKt
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.io.FilesKt
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.functions.Function2
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.ranges.RangesKt
 *  kotlin1822.sequences.Sequence
 *  kotlin1822.sequences.SequencesKt
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Event
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.ObjectiveEvents;
import ink.ptms.chemdah.api.event.collect.PluginReloadEvent;
import ink.ptms.chemdah.api.event.collect.TemplateEvents;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.AgentType;
import ink.ptms.chemdah.core.quest.Id;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestDumper;
import ink.ptms.chemdah.core.quest.QuestLoader;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.TemplateGroup;
import ink.ptms.chemdah.core.quest.addon.Addon;
import ink.ptms.chemdah.core.quest.meta.Meta;
import ink.ptms.chemdah.core.quest.objective.Dependency;
import ink.ptms.chemdah.core.quest.objective.Objective;
import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.event.InternalEvent;
import ink.ptms.chemdah.taboolib.common.event.InternalEventBus;
import ink.ptms.chemdah.taboolib.common.io.FileKt;
import ink.ptms.chemdah.taboolib.common.io.ProjectScannerKt;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.platform.function.ListenerKt;
import ink.ptms.chemdah.taboolib.common.util.LocaleKt;
import ink.ptms.chemdah.taboolib.common.util.SyncExecutorKt;
import ink.ptms.chemdah.taboolib.common5.FileWatcher;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.library.reflex.ClassAnnotation;
import ink.ptms.chemdah.taboolib.library.reflex.ReflexClass;
import ink.ptms.chemdah.taboolib.module.configuration.ConfigNode;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion;
import ink.ptms.chemdah.taboolib.platform.bukkit.Parallel;
import ink.ptms.chemdah.util.Couple;
import ink.ptms.chemdah.util.FuturesKt;
import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kotlin.Metadata;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.io.FilesKt;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.functions.Function2;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.RangesKt;
import kotlin1822.sequences.Sequence;
import kotlin1822.sequences.SequencesKt;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u008a\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0011\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J;\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u001c0\u001b\"\b\b\u0000\u0010\u001d*\u00020\u00012\u0006\u0010\u001e\u001a\u00020\u001f2\u0006\u0010 \u001a\u00020!2\u0006\u0010\"\u001a\u00020#2\u0006\u0010$\u001a\u0002H\u001d\u00a2\u0006\u0002\u0010%J\b\u0010&\u001a\u00020'H\u0007J\u0006\u0010(\u001a\u00020'J\b\u0010)\u001a\u00020'H\u0007J\u0006\u0010*\u001a\u00020'J\u0014\u0010*\u001a\b\u0012\u0004\u0012\u00020,0+2\u0006\u0010-\u001a\u00020.J\u001e\u0010/\u001a\b\u0012\u0004\u0012\u00020,0+2\u0006\u00100\u001a\u00020.2\u0006\u00101\u001a\u00020.H\u0002J\u0006\u00102\u001a\u00020'J\b\u00103\u001a\u00020'H\u0007JO\u00104\u001a\u00020'\"\u0004\b\u0000\u001052\f\u0010$\u001a\b\u0012\u0004\u0012\u0002H5062\u0006\u00107\u001a\u0002082\u0006\u00109\u001a\u00020\u00112!\u0010:\u001a\u001d\u0012\u0013\u0012\u00110\u0001\u00a2\u0006\f\b<\u0012\b\b=\u0012\u0004\b\b($\u0012\u0004\u0012\u00020'0;H\u0002J\u0006\u0010>\u001a\u00020'J\b\u0010?\u001a\u00020'H\u0003J/\u0010@\u001a\u00020'\"\b\b\u0000\u0010\u001d*\u00020\u0001*\b\u0012\u0004\u0012\u0002H\u001d0A2\u0006\u0010B\u001a\u00020C2\u0006\u0010$\u001a\u0002H\u001d\u00a2\u0006\u0002\u0010DJ\u001a\u0010E\u001a\u00020'\"\b\b\u0000\u0010\u001d*\u00020\u0001*\b\u0012\u0004\u0012\u0002H\u001d0AR\"\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0086\u000e\u00a2\u0006\u0010\n\u0002\u0010\n\u001a\u0004\b\u0006\u0010\u0007\"\u0004\b\b\u0010\tR\u001e\u0010\r\u001a\u00020\f2\u0006\u0010\u000b\u001a\u00020\f@BX\u0086.\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u001e\u0010\u0010\u001a\u00020\u00118\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\u0012\"\u0004\b\u0013\u0010\u0014R\u001a\u0010\u0015\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0016\u0010\u0017\"\u0004\b\u0018\u0010\u0019\u00a8\u0006F"}, d2={"Link/ptms/chemdah/core/quest/QuestLoader;", "", "()V", "allowExtension", "", "", "getAllowExtension", "()[Ljava/lang/String;", "setAllowExtension", "([Ljava/lang/String;)V", "[Ljava/lang/String;", "<set-?>", "Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "groupConf", "getGroupConf", "()Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "isDisableFileWatcher", "", "()Z", "setDisableFileWatcher", "(Z)V", "optionKey", "getOptionKey", "()Ljava/lang/String;", "setOptionKey", "(Ljava/lang/String;)V", "handleTask", "Ljava/util/concurrent/CompletableFuture;", "Ljava/lang/Void;", "T", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "event", "(Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Task;Link/ptms/chemdah/core/quest/Quest;Ljava/lang/Object;)Ljava/util/concurrent/CompletableFuture;", "init", "", "loadAll", "loadGroup", "loadTemplate", "", "Link/ptms/chemdah/core/quest/Template;", "file", "Ljava/io/File;", "loadTemplateFromFile", "rootFile", "configFile", "loadTemplateGroup", "registerComponents", "registerEvent", "E", "Ljava/lang/Class;", "priority", "Link/ptms/chemdah/taboolib/common/platform/event/EventPriority;", "ignoreCancelled", "func", "Lkotlin1822/Function1;", "Lkotlin1822/ParameterName;", "name", "resetObjectiveUsageState", "watch", "handleEvent", "Link/ptms/chemdah/core/quest/objective/Objective;", "player", "Lorg/bukkit/entity/Player;", "(Link/ptms/chemdah/core/quest/objective/Objective;Lorg/bukkit/entity/Player;Ljava/lang/Object;)V", "register", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nQuestLoader.kt\nKotlin\n*S Kotlin\n*F\n+ 1 QuestLoader.kt\nink/ptms/chemdah/core/quest/QuestLoader\n+ 2 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 4 Maps.kt\nkotlin/collections/MapsKt__MapsKt\n+ 5 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,418:1\n215#2,2:419\n215#2,2:421\n215#2,2:423\n215#2,2:454\n1549#3:425\n1620#3,3:426\n1774#3,4:429\n766#3:433\n857#3,2:434\n1549#3:436\n1620#3,3:437\n1477#3:440\n1502#3,3:441\n1505#3,3:451\n766#3:456\n857#3,2:457\n1603#3,9:459\n1855#3:468\n1856#3:470\n1612#3:471\n1271#3,2:472\n1285#3,4:474\n361#4,7:444\n1#5:469\n*S KotlinDebug\n*F\n+ 1 QuestLoader.kt\nink/ptms/chemdah/core/quest/QuestLoader\n*L\n98#1:419,2\n242#1:421,2\n243#1:423,2\n319#1:454,2\n302#1:425\n302#1:426,3\n310#1:429,4\n317#1:433\n317#1:434,2\n317#1:436\n317#1:437,3\n319#1:440\n319#1:441,3\n319#1:451,3\n358#1:456\n358#1:457,2\n359#1:459,9\n359#1:468\n359#1:470\n359#1:471\n396#1:472,2\n396#1:474,4\n319#1:444,7\n359#1:469\n*E\n"})
public final class QuestLoader {
    @NotNull
    public static final QuestLoader INSTANCE = new QuestLoader();
    private static Configuration groupConf;
    @NotNull
    private static String optionKey;
    @NotNull
    private static String[] allowExtension;
    @ConfigNode(value="default-quest.disable-file-watcher")
    private static boolean isDisableFileWatcher;

    private QuestLoader() {
    }

    @NotNull
    public final Configuration getGroupConf() {
        Configuration configuration = groupConf;
        if (configuration != null) {
            return configuration;
        }
        Intrinsics.throwUninitializedPropertyAccessException((String)"groupConf");
        return null;
    }

    @NotNull
    public final String getOptionKey() {
        return optionKey;
    }

    public final void setOptionKey(@NotNull String string) {
        Intrinsics.checkNotNullParameter((Object)string, (String)"<set-?>");
        optionKey = string;
    }

    @NotNull
    public final String[] getAllowExtension() {
        return allowExtension;
    }

    public final void setAllowExtension(@NotNull String[] stringArray) {
        Intrinsics.checkNotNullParameter((Object)stringArray, (String)"<set-?>");
        allowExtension = stringArray;
    }

    public final boolean isDisableFileWatcher() {
        return isDisableFileWatcher;
    }

    public final void setDisableFileWatcher(boolean bl) {
        isDisableFileWatcher = bl;
    }

    @Awake(value=LifeCycle.LOAD)
    public final void loadGroup() {
        for (String ext : allowExtension) {
            File file = new File(IOKt.getDataFolder(), "core/group." + ext);
            if (!file.exists()) continue;
            groupConf = Configuration.Companion.loadFromFile$default((Configuration.Companion)Configuration.Companion, (File)file, null, (boolean)false, (int)6, null);
            return;
        }
        IOKt.releaseResourceFile$default((String)"core/group.yml", (boolean)false, null, (int)6, null);
        this.loadGroup();
    }

    @Parallel(id="chemdah_quest_components_init", runOn=LifeCycle.ENABLE)
    public final void registerComponents() {
        Map $this$forEach$iv = ProjectScannerKt.getRunningClassMapInJar();
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry element$iv;
            Map.Entry it = element$iv = iterator.next();
            boolean bl = false;
            ReflexClass reflex = (ReflexClass)it.getValue();
            if (reflex.hasAnnotation(Dependency.class) && reflex.isSingleton()) {
                String dependPlugin;
                ClassAnnotation dependency = reflex.getAnnotation(Dependency.class);
                String string = (String)dependency.property("plugin");
                if (string == null) {
                    string = "minecraft";
                }
                if (!Intrinsics.areEqual((Object)(dependPlugin = string), (Object)"minecraft") && Bukkit.getPluginManager().getPlugin(dependPlugin) == null || MinecraftVersion.INSTANCE.getMajorLegacy() < ((Number)dependency.property("version", (Object)10700)).intValue()) continue;
                try {
                    Objective objective2;
                    Object object = reflex.getInstance();
                    Objective objective3 = objective2 = object instanceof Objective ? (Objective)object : null;
                    if (objective3 != null) {
                        INSTANCE.register(objective3);
                    }
                }
                catch (Throwable throwable) {}
                continue;
            }
            if (!reflex.hasAnnotation(Id.class)) continue;
            Object object = reflex.getAnnotation(Id.class).property("id");
            Intrinsics.checkNotNull((Object)object);
            String id2 = (String)object;
            Class instance = reflex.toClass();
            if (Meta.class.isAssignableFrom(instance)) {
                Map map = ChemdahAPI.INSTANCE.getQuestMeta();
                Intrinsics.checkNotNull((Object)instance, (String)"null cannot be cast to non-null type java.lang.Class<out ink.ptms.chemdah.core.quest.meta.Meta<*>>");
                map.put(id2, instance);
                continue;
            }
            if (!Addon.class.isAssignableFrom(instance)) continue;
            Map map = ChemdahAPI.INSTANCE.getQuestAddon();
            Intrinsics.checkNotNull((Object)instance, (String)"null cannot be cast to non-null type java.lang.Class<out ink.ptms.chemdah.core.quest.addon.Addon>");
            map.put(id2, instance);
        }
        QuestDumper.INSTANCE.dumpComponentsAPI();
    }

    public final <T> void register(@NotNull Objective<T> $this$register) {
        Intrinsics.checkNotNullParameter($this$register, (String)"<this>");
        ((Map)ChemdahAPI.INSTANCE.getQuestObjective()).put($this$register.getName(), $this$register);
        if ($this$register.isListener()) {
            this.registerEvent($this$register.getEvent(), EventPriority.values()[$this$register.getPriority().ordinal()], $this$register.getIgnoreCancelled(), (Function1<Object, Unit>)((Function1)new Function1<Object, Unit>($this$register){
                final /* synthetic */ Objective<T> $this_register;
                {
                    this.$this_register = $receiver;
                    super(1);
                }

                public final void invoke(@NotNull Object e) {
                    Intrinsics.checkNotNullParameter((Object)e, (String)"e");
                    ChemdahAPI.INSTANCE.getEventFactory().callObjectiveCall(this.$this_register, e);
                    if (this.$this_register.getUsing()) {
                        Player player2 = this.$this_register.getPlayerHandler$Chemdah().apply(e);
                        if (player2 == null) {
                            return;
                        }
                        Player player3 = player2;
                        if (ChemdahAPI.INSTANCE.isChemdahProfileLoaded(player3)) {
                            QuestLoader.INSTANCE.handleEvent(this.$this_register, player3, e);
                        }
                    }
                }
            }));
        }
    }

    public final <T> void handleEvent(@NotNull Objective<T> $this$handleEvent, @NotNull Player player2, @NotNull T event) {
        Intrinsics.checkNotNullParameter($this$handleEvent, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter(event, (String)"event");
        if (ChemdahAPI.INSTANCE.isChemdahProfileLoaded(player2)) {
            PlayerProfile playerProfile2;
            PlayerProfile profile = playerProfile2 = ChemdahAPI.INSTANCE.getChemdahProfile(player2);
            boolean bl = false;
            profile.tasksByObjective(profile, $this$handleEvent, arg_0 -> QuestLoader.handleEvent$lambda$3$lambda$2(event, arg_0));
        }
    }

    @NotNull
    public final <T> CompletableFuture<Void> handleTask(@NotNull PlayerProfile profile, @NotNull Task task, @NotNull Quest quest2, @NotNull T event) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        Intrinsics.checkNotNullParameter(event, (String)"event");
        Objective<? extends Object> objective2 = task.getObjective();
        Intrinsics.checkNotNull(objective2, (String)"null cannot be cast to non-null type ink.ptms.chemdah.core.quest.objective.Objective<T of ink.ptms.chemdah.core.quest.QuestLoader.handleTask>");
        Objective<? extends Object> objective3 = objective2;
        if (profile.isQuestCompleted(quest2.getTemplate()) || objective3.hasCompletedSignature(profile, task) || task.isClosed(profile)) {
            CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
            return completableFuture;
        }
        if (new ObjectiveEvents.Continue.Pre(objective3, task, quest2, profile).call()) {
            CompletableFuture<Void> future = new CompletableFuture<Void>();
            FuturesKt.applyWithError(objective3.checkCondition(profile, task, quest2, event), (Function1)new Function1<Boolean, Unit>(objective3, profile, task, quest2, event, future){
                final /* synthetic */ Objective<T> $objective;
                final /* synthetic */ PlayerProfile $profile;
                final /* synthetic */ Task $task;
                final /* synthetic */ Quest $quest;
                final /* synthetic */ T $event;
                final /* synthetic */ CompletableFuture<Void> $future;
                {
                    this.$objective = $objective;
                    this.$profile = $profile;
                    this.$task = $task;
                    this.$quest = $quest;
                    this.$event = $event;
                    this.$future = $future;
                    super(1);
                }

                public final void invoke(boolean cond) {
                    if (cond) {
                        this.$objective.onContinue(this.$profile, this.$task, this.$quest, this.$event);
                        this.$task.agent(this.$quest.getProfile(), AgentType.TASK_CONTINUED, "self", null, this.$event);
                        new ObjectiveEvents.Continue.Post(this.$objective, this.$task, this.$quest, this.$profile).call();
                        try {
                            FuturesKt.acceptWithError(this.$objective.checkComplete(this.$profile, this.$task, this.$quest), (Function0<Unit>)((Function0)new Function0<Unit>(this.$future){
                                final /* synthetic */ CompletableFuture<Void> $future;
                                {
                                    this.$future = $future;
                                    super(0);
                                }

                                public final void invoke() {
                                    this.$future.complete(null);
                                }
                            }));
                        }
                        catch (Exception e) {
                            this.$future.complete(null);
                        }
                    } else {
                        this.$future.complete(null);
                    }
                }
            });
            return future;
        }
        CompletableFuture<Object> completableFuture = CompletableFuture.completedFuture(null);
        Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(null)");
        return completableFuture;
    }

    public final void resetObjectiveUsageState() {
        Map.Entry element$iv;
        Map $this$forEach$iv = ChemdahAPI.INSTANCE.getQuestObjective();
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry it = element$iv = iterator.next();
            boolean bl = false;
            ((Objective)it.getValue()).setUsing(false);
        }
        $this$forEach$iv = ChemdahAPI.INSTANCE.getQuestTemplate();
        $i$f$forEach = false;
        iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry t = element$iv = iterator.next();
            boolean bl = false;
            Map $this$forEach$iv2 = ((Template)t.getValue()).getTaskMap();
            boolean $i$f$forEach2 = false;
            Iterator iterator2 = $this$forEach$iv2.entrySet().iterator();
            while (iterator2.hasNext()) {
                Map.Entry element$iv2;
                Map.Entry it = element$iv2 = iterator2.next();
                boolean bl2 = false;
                ((Task)it.getValue()).getObjective().setUsing(true);
            }
        }
    }

    @Parallel(id="chemdah_quest_watch", runOn=LifeCycle.ACTIVE)
    private final void watch() {
        if (isDisableFileWatcher) {
            return;
        }
        FileWatcher.INSTANCE.addSimpleListener(new File(IOKt.getDataFolder(), "core/quest"), QuestLoader::watch$lambda$7);
    }

    @Parallel(id="chemdah_quest_init", dependOn={"chemdah_quest_components_init"}, runOn=LifeCycle.ACTIVE)
    public final void init() {
        SyncExecutorKt.runSync((Function0)init.1.INSTANCE);
    }

    public final void loadAll() {
        this.loadTemplate();
        this.loadTemplateGroup();
        new PluginReloadEvent.Quest(false).call();
    }

    /*
     * WARNING - void declaration
     */
    public final void loadTemplate() {
        void $this$forEach$iv;
        void $this$groupByTo$iv$iv;
        void $this$mapTo$iv$iv;
        void $this$map$iv;
        boolean bl;
        Objective it;
        void $this$filterTo$iv$iv;
        Iterable $this$filter$iv;
        int n;
        Object[] $this$count$iv;
        void $this$mapTo$iv$iv2;
        Object[] $this$map$iv2;
        File file = new File(IOKt.getDataFolder(), "core/quest");
        if (FileKt.notfound((File)file)) {
            IOKt.releaseResourceFile$default((String)"core/quest/example.yml", (boolean)false, null, (int)6, null);
        }
        long start = System.currentTimeMillis();
        List<Template> templates = this.loadTemplate(file);
        ChemdahAPI.INSTANCE.getQuestTemplate().clear();
        Iterable iterable = templates;
        Object[] objectArray = ChemdahAPI.INSTANCE.getQuestTemplate();
        boolean $i$f$map232 = false;
        void var7_8 = $this$map$iv2;
        Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv2, (int)10));
        boolean $i$f$mapTo232 = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv2) {
            void it2;
            Template template = (Template)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl2 = false;
            collection.add(TuplesKt.to((Object)it2.getId(), (Object)it2));
        }
        MapsKt.putAll((Map)objectArray, (Iterable)((List)destination$iv$iv));
        this.resetObjectiveUsageState();
        $this$map$iv2 = new Object[]{LocaleKt.t((String)("\n                \u5df2\u52a0\u8f7d " + ChemdahAPI.INSTANCE.getQuestTemplate().size() + " \u4e2a\u4efb\u52a1\u6a21\u677f, \u8017\u65f6: " + (System.currentTimeMillis() - start) + "ms\n                " + ChemdahAPI.INSTANCE.getQuestTemplate().size() + " templates loaded, " + (System.currentTimeMillis() - start) + "ms\n            "))};
        IOKt.info((Object[])$this$map$iv2);
        StringBuilder stringBuilder = new StringBuilder();
        Collection<Objective<?>> collection = ChemdahAPI.INSTANCE.getQuestObjective().values();
        Intrinsics.checkNotNullExpressionValue(collection, (String)"ChemdahAPI.questObjective.values");
        Iterable $i$f$map232 = collection;
        objectArray = stringBuilder;
        boolean $i$f$count232 = false;
        if (((Collection)$this$count$iv).isEmpty()) {
            n = 0;
        } else {
            int count$iv = 0;
            for (Object entry : $this$count$iv) {
                Objective it3 = (Objective)entry;
                boolean bl3 = false;
                if (!it3.getUsing() || ++count$iv >= 0) continue;
                CollectionsKt.throwCountOverflow();
            }
            n = count$iv;
        }
        int n2 = n;
        String active = objectArray.append(n2).append('/').append(ChemdahAPI.INSTANCE.getQuestObjective().size()).toString();
        $this$count$iv = new Object[]{LocaleKt.t((String)("\n                \u603b\u5171\u6fc0\u6d3b " + active + " \u4e2a\u4efb\u52a1\u4e8b\u4ef6:\n                " + active + " events activated:\n            "))};
        IOKt.info((Object[])$this$count$iv);
        $this$count$iv = new Object[1];
        Collection<Objective<?>> collection2 = ChemdahAPI.INSTANCE.getQuestObjective().values();
        Intrinsics.checkNotNullExpressionValue(collection2, (String)"ChemdahAPI.questObjective.values");
        Iterable $i$f$count232 = collection2;
        n2 = 0;
        objectArray = $this$count$iv;
        boolean $i$f$filter = false;
        void $i$f$mapTo232 = $this$filter$iv;
        Collection collection3 = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            it = (Objective)element$iv$iv;
            bl = false;
            if (!it.getUsing()) continue;
            collection3.add(element$iv$iv);
        }
        Collection<String> collection4 = (List)collection3;
        $this$filter$iv = collection4;
        boolean $i$f$map332 = false;
        $this$filterTo$iv$iv = $this$map$iv;
        Collection collection5 = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            it = (Objective)item$iv$iv;
            collection4 = collection5;
            bl = false;
            collection4.add(it.getName());
        }
        collection4 = (List)collection5;
        objectArray[n2] = collection4;
        IOKt.info((Object[])$this$count$iv);
        Object $this$groupBy$iv = templates;
        boolean $i$f$groupBy = false;
        Iterable $i$f$map332 = $this$groupBy$iv;
        Map destination$iv$iv3 = new LinkedHashMap();
        boolean bl4 = false;
        for (Object element$iv$iv : $this$groupByTo$iv$iv) {
            Object object;
            Template it4 = (Template)element$iv$iv;
            boolean bl5 = false;
            String key$iv$iv = it4.getId();
            Map $this$getOrPut$iv$iv$iv = destination$iv$iv3;
            boolean $i$f$getOrPut = false;
            Object value$iv$iv$iv = $this$getOrPut$iv$iv$iv.get(key$iv$iv);
            if (value$iv$iv$iv == null) {
                boolean bl2 = false;
                List answer$iv$iv$iv = new ArrayList();
                $this$getOrPut$iv$iv$iv.put(key$iv$iv, answer$iv$iv$iv);
                object = answer$iv$iv$iv;
            } else {
                object = value$iv$iv$iv;
            }
            List list$iv$iv = (List)object;
            list$iv$iv.add(element$iv$iv);
        }
        $this$groupBy$iv = destination$iv$iv3;
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry element$iv;
            Map.Entry entry = element$iv = iterator.next();
            boolean bl6 = false;
            String id2 = (String)entry.getKey();
            List c = (List)entry.getValue();
            if (c.size() <= 1) continue;
            Object[] objectArray2 = new Object[]{LocaleKt.t((String)("\n                        \u6709 " + c.size() + " \u4e2a\u4efb\u52a1\u6a21\u677f\u4f7f\u7528\u4e86\u91cd\u590d\u7684 ID: " + id2 + "\n                        There are " + c.size() + " templates using duplicate id: " + id2 + "\n                    "))};
            IOKt.warning((Object[])objectArray2);
        }
    }

    @NotNull
    public final List<Template> loadTemplate(@NotNull File file) {
        Intrinsics.checkNotNullParameter((Object)file, (String)"file");
        List<Template> list2 = SequencesKt.toList((Sequence)SequencesKt.filter((Sequence)((Sequence)FilesKt.walk$default((File)file, null, (int)1, null)), (Function1)loadTemplate.6.INSTANCE)).parallelStream().flatMap(arg_0 -> QuestLoader.loadTemplate$lambda$14((Function1)new Function1<File, Stream<? extends Template>>(file){
            final /* synthetic */ File $file;
            {
                this.$file = $file;
                super(1);
            }

            public final Stream<? extends Template> invoke(File it) {
                Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                return QuestLoader.access$loadTemplateFromFile(QuestLoader.INSTANCE, this.$file, it).stream();
            }
        }, arg_0)).collect(Collectors.toList());
        Intrinsics.checkNotNullExpressionValue(list2, (String)"file: File): List<Templa\u2026lect(Collectors.toList())");
        return list2;
    }

    /*
     * WARNING - void declaration
     */
    private final List<Template> loadTemplateFromFile(File rootFile, File configFile) {
        List list2;
        try {
            void $this$mapNotNullTo$iv$iv;
            void $this$mapNotNull$iv;
            void $this$filterTo$iv$iv;
            Iterable $this$filter$iv;
            Configuration conf = Configuration.Companion.loadFromFile$default((Configuration.Companion)Configuration.Companion, (File)configFile, null, (boolean)false, (int)2, null);
            ConfigurationSection fileOption = conf.getConfigurationSection(optionKey);
            Iterable iterable = conf.getKeys(false);
            boolean $i$f$filter = false;
            void var7_9 = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                String key = (String)element$iv$iv;
                boolean bl = false;
                if (!(!Intrinsics.areEqual((Object)key, (Object)optionKey) && conf.isConfigurationSection(key))) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            $this$filter$iv = (List)destination$iv$iv;
            boolean $i$f$mapNotNull = false;
            $this$filterTo$iv$iv = $this$mapNotNull$iv;
            destination$iv$iv = new ArrayList();
            boolean $i$f$mapNotNullTo = false;
            void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
            boolean $i$f$forEach = false;
            Iterator iterator = $this$forEach$iv$iv$iv.iterator();
            while (iterator.hasNext()) {
                Template template;
                ConfigurationSection section;
                Object element$iv$iv$iv;
                Object element$iv$iv = element$iv$iv$iv = iterator.next();
                boolean bl = false;
                String key = (String)element$iv$iv;
                boolean bl2 = false;
                Intrinsics.checkNotNull((Object)conf.getConfigurationSection(key));
                if (new TemplateEvents.Load(rootFile, key, section).call()) {
                    Template template2;
                    try {
                        template2 = ChemdahAPI.INSTANCE.getCoreConfigDeserializer().template(configFile, key, section, fileOption);
                    }
                    catch (Exception e) {
                        Object[] objectArray = new Object[]{LocaleKt.t((String)("\n                                    \u4efb\u52a1\u6a21\u677f " + key + " \u52a0\u8f7d\u5931\u8d25\uff1a" + e.getMessage() + "\n                                    Template " + key + " load failed: " + e.getMessage() + "\n                                "))};
                        IOKt.warning((Object[])objectArray);
                        e.printStackTrace();
                        template2 = null;
                    }
                    template = template2;
                } else {
                    template = null;
                }
                if (template == null) continue;
                Template it$iv$iv = template;
                boolean bl3 = false;
                destination$iv$iv.add(it$iv$iv);
            }
            list2 = (List)destination$iv$iv;
        }
        catch (Exception e) {
            Object[] objectArray = new Object[]{LocaleKt.t((String)("\n                    \u914d\u7f6e\u6587\u4ef6 " + configFile.getName() + " \u52a0\u8f7d\u5931\u8d25\uff1a" + e.getMessage() + "\n                    Config file " + configFile.getName() + " load failed: " + e.getMessage() + "\n                "))};
            IOKt.warning((Object[])objectArray);
            e.printStackTrace();
            list2 = CollectionsKt.emptyList();
        }
        return list2;
    }

    /*
     * WARNING - void declaration
     */
    public final void loadTemplateGroup() {
        Map map;
        ChemdahAPI.INSTANCE.getQuestTemplateGroup().clear();
        Map map2 = ChemdahAPI.INSTANCE.getQuestTemplateGroup();
        Object object = this.getGroupConf().getConfigurationSection("group");
        if (object != null && (object = object.getKeys(false)) != null) {
            Iterable $this$associateWith$iv = (Iterable)object;
            boolean $i$f$associateWith = false;
            LinkedHashMap result$iv = new LinkedHashMap(RangesKt.coerceAtLeast((int)MapsKt.mapCapacity((int)CollectionsKt.collectionSizeOrDefault((Iterable)$this$associateWith$iv, (int)10)), (int)16));
            Iterable $this$associateWithTo$iv$iv = $this$associateWith$iv;
            boolean $i$f$associateWithTo = false;
            for (Object element$iv$iv : $this$associateWithTo$iv$iv) {
                void group2;
                String string = (String)element$iv$iv;
                Object t = element$iv$iv;
                Map map3 = result$iv;
                boolean bl = false;
                TemplateGroup templateGroup = ChemdahAPI.INSTANCE.getCoreConfigDeserializer().templateGroup((String)group2, INSTANCE.getGroupConf().getStringList("group." + (String)group2));
                map3.put(t, templateGroup);
            }
            map = result$iv;
        } else {
            map = MapsKt.emptyMap();
        }
        Map map4 = map;
        map2.putAll(map4);
    }

    private final <E> void registerEvent(Class<E> event, EventPriority priority, boolean ignoreCancelled, Function1<Object, Unit> func) {
        if (Event.class.isAssignableFrom(event)) {
            Intrinsics.checkNotNull(event, (String)"null cannot be cast to non-null type java.lang.Class<out org.bukkit.event.Event>");
            ListenerKt.registerBukkitListener(event, (EventPriority)priority, (boolean)ignoreCancelled, (Function2)new Function2<Closeable, ?, Unit>(func){
                final /* synthetic */ Function1<Object, Unit> $func;
                {
                    this.$func = $func;
                    super(2);
                }

                public final void invoke(@NotNull Closeable $this$registerBukkitListener, @NotNull Event it) {
                    Intrinsics.checkNotNullParameter((Object)$this$registerBukkitListener, (String)"$this$registerBukkitListener");
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    this.$func.invoke((Object)it);
                }
            });
        } else if (InternalEvent.class.isAssignableFrom(event)) {
            Intrinsics.checkNotNull(event, (String)"null cannot be cast to non-null type java.lang.Class<out taboolib.common.event.InternalEvent>");
            InternalEventBus.Companion.listen(event, priority.ordinal(), ignoreCancelled, new Function1<?, Unit>(func){
                final /* synthetic */ Function1<Object, Unit> $func;
                {
                    this.$func = $func;
                    super(1);
                }

                public final void invoke(@NotNull InternalEvent it) {
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    this.$func.invoke((Object)it);
                }
            });
        } else {
            throw new IllegalStateException(("Unsupported event type: " + event).toString());
        }
    }

    private static final void handleEvent$lambda$3$lambda$2$lambda$1(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final void handleEvent$lambda$3$lambda$2(Object $event, Couple couple) {
        Intrinsics.checkNotNullParameter((Object)$event, (String)"$event");
        Intrinsics.checkNotNullParameter((Object)couple, (String)"<name for destructuring parameter 0>");
        Quest quest2 = (Quest)couple.component1();
        Task task = (Task)couple.component2();
        INSTANCE.handleTask(quest2.getProfile(), task, quest2, $event).thenAccept(arg_0 -> QuestLoader.handleEvent$lambda$3$lambda$2$lambda$1((Function1)new Function1<Void, Unit>(quest2){
            final /* synthetic */ Quest $quest;
            {
                this.$quest = $quest;
                super(1);
            }

            public final void invoke(Void it) {
                this.$quest.checkCompleteFuture();
            }
        }, arg_0));
    }

    private static final void watch$lambda$7(File it) {
        Object[] objectArray = new Object[]{LocaleKt.t((String)"\n                    \u4efb\u52a1\u6587\u4ef6\u53d1\u751f\u53d8\u52a8\uff0c\u6b63\u5728\u91cd\u8f7d...\n                    Quest file changed, reloading...\n                ")};
        IOKt.info((Object[])objectArray);
        INSTANCE.loadAll();
    }

    private static final Stream loadTemplate$lambda$14(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        return (Stream)$tmp0.invoke(p0);
    }

    public static final /* synthetic */ List access$loadTemplateFromFile(QuestLoader $this, File rootFile, File configFile) {
        return $this.loadTemplateFromFile(rootFile, configFile);
    }

    static {
        optionKey = "__option__";
        String[] stringArray = new String[]{"yaml", "yml", "json", "toml"};
        allowExtension = stringArray;
        isDisableFileWatcher = true;
    }
}

