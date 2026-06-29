/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.ObjectiveEvents;
import ink.ptms.chemdah.api.event.collect.PluginReloadEvent;
import ink.ptms.chemdah.api.event.collect.TemplateEvents;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.database.Database;
import ink.ptms.chemdah.core.quest.AgentType;
import ink.ptms.chemdah.core.quest.Id;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestContainer;
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
import ink.ptms.chemdah.taboolib.common5.FileWatcher;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.library.reflex.ReflexClass;
import ink.ptms.chemdah.taboolib.module.configuration.ConfigNode;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion;
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

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u008a\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0011\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J;\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u001c0\u001b\"\b\b\u0000\u0010\u001d*\u00020\u00012\u0006\u0010\u001e\u001a\u00020\u001f2\u0006\u0010 \u001a\u00020!2\u0006\u0010\"\u001a\u00020#2\u0006\u0010$\u001a\u0002H\u001d\u00a2\u0006\u0002\u0010%J\b\u0010&\u001a\u00020'H\u0007J\b\u0010(\u001a\u00020'H\u0007J\u0006\u0010)\u001a\u00020'J\u0014\u0010)\u001a\b\u0012\u0004\u0012\u00020+0*2\u0006\u0010,\u001a\u00020-J\u0006\u0010.\u001a\u00020'J\b\u0010/\u001a\u00020'H\u0007JO\u00100\u001a\u00020'\"\u0004\b\u0000\u001012\f\u0010$\u001a\b\u0012\u0004\u0012\u0002H1022\u0006\u00103\u001a\u0002042\u0006\u00105\u001a\u00020\u00112!\u00106\u001a\u001d\u0012\u0013\u0012\u00110\u0001\u00a2\u0006\f\b8\u0012\b\b9\u0012\u0004\b\b($\u0012\u0004\u0012\u00020'07H\u0002J\u0006\u0010:\u001a\u00020'J\b\u0010;\u001a\u00020'H\u0003J/\u0010<\u001a\u00020'\"\b\b\u0000\u0010\u001d*\u00020\u0001*\b\u0012\u0004\u0012\u0002H\u001d0=2\u0006\u0010>\u001a\u00020?2\u0006\u0010$\u001a\u0002H\u001d\u00a2\u0006\u0002\u0010@J\u001a\u0010A\u001a\u00020'\"\b\b\u0000\u0010\u001d*\u00020\u0001*\b\u0012\u0004\u0012\u0002H\u001d0=R\"\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0086\u000e\u00a2\u0006\u0010\n\u0002\u0010\n\u001a\u0004\b\u0006\u0010\u0007\"\u0004\b\b\u0010\tR\u001e\u0010\r\u001a\u00020\f2\u0006\u0010\u000b\u001a\u00020\f@BX\u0086.\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u001e\u0010\u0010\u001a\u00020\u00118\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\u0012\"\u0004\b\u0013\u0010\u0014R\u001a\u0010\u0015\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0016\u0010\u0017\"\u0004\b\u0018\u0010\u0019\u00a8\u0006B"}, d2={"Link/ptms/chemdah/core/quest/QuestLoader;", "", "()V", "allowExtension", "", "", "getAllowExtension", "()[Ljava/lang/String;", "setAllowExtension", "([Ljava/lang/String;)V", "[Ljava/lang/String;", "<set-?>", "Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "groupConf", "getGroupConf", "()Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "isDisableFileWatcher", "", "()Z", "setDisableFileWatcher", "(Z)V", "optionKey", "getOptionKey", "()Ljava/lang/String;", "setOptionKey", "(Ljava/lang/String;)V", "handleTask", "Ljava/util/concurrent/CompletableFuture;", "Ljava/lang/Void;", "T", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "event", "(Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Task;Link/ptms/chemdah/core/quest/Quest;Ljava/lang/Object;)Ljava/util/concurrent/CompletableFuture;", "loadAll", "", "loadGroup", "loadTemplate", "", "Link/ptms/chemdah/core/quest/Template;", "file", "Ljava/io/File;", "loadTemplateGroup", "registerComponents", "registerEvent", "E", "Ljava/lang/Class;", "priority", "Link/ptms/chemdah/taboolib/common/platform/event/EventPriority;", "ignoreCancelled", "func", "Lkotlin1822/Function1;", "Lkotlin1822/ParameterName;", "name", "resetObjectiveUsageState", "watch", "handleEvent", "Link/ptms/chemdah/core/quest/objective/Objective;", "player", "Lorg/bukkit/entity/Player;", "(Link/ptms/chemdah/core/quest/objective/Objective;Lorg/bukkit/entity/Player;Ljava/lang/Object;)V", "register", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nQuestLoader.kt\nKotlin\n*S Kotlin\n*F\n+ 1 QuestLoader.kt\nink/ptms/chemdah/core/quest/QuestLoader\n+ 2 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 4 Maps.kt\nkotlin/collections/MapsKt__MapsKt\n*L\n1#1,356:1\n215#2,2:357\n215#2,2:359\n215#2,2:361\n215#2,2:381\n1549#3:363\n1620#3,3:364\n1477#3:367\n1502#3,3:368\n1505#3,3:378\n1271#3,2:383\n1285#3,4:385\n361#4,7:371\n*S KotlinDebug\n*F\n+ 1 QuestLoader.kt\nink/ptms/chemdah/core/quest/QuestLoader\n*L\n94#1:357,2\n225#1:359,2\n226#1:361,2\n281#1:381,2\n272#1:363\n272#1:364,3\n281#1:367\n281#1:368,3\n281#1:378,3\n335#1:383,2\n335#1:385,4\n281#1:371,7\n*E\n"})
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

    @Awake(value=LifeCycle.ENABLE)
    public final void registerComponents() {
        boolean checkDependency = !new File(IOKt.getDataFolder(), "api.json").exists();
        Map $this$forEach$iv = ProjectScannerKt.getRunningClassMapInJar();
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry element$iv;
            Map.Entry it = element$iv = iterator.next();
            boolean bl = false;
            ReflexClass reflex = (ReflexClass)it.getValue();
            if (reflex.hasAnnotation(Dependency.class) && reflex.isSingleton()) {
                Object dependency22;
                if (checkDependency && (!Intrinsics.areEqual((Object)(dependency22 = reflex.getAnnotation(Dependency.class)).property("plugin"), (Object)"minecraft") && Bukkit.getPluginManager().getPlugin(String.valueOf(dependency22.property("plugin"))) == null || MinecraftVersion.INSTANCE.getMajorLegacy() < ((Number)dependency22.property("version", (Object)10700)).intValue())) continue;
                try {
                    Object object = reflex.getInstance();
                    Object object2 = dependency22 = object instanceof Objective ? (Objective)object : null;
                    if (object2 != null) {
                        INSTANCE.register((Objective)object2);
                    }
                }
                catch (NoClassDefFoundError dependency22) {}
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
                        Player player = this.$this_register.getPlayerHandler$Chemdah().apply(e);
                        if (player == null) {
                            return;
                        }
                        Player player2 = player;
                        if (ChemdahAPI.INSTANCE.isChemdahProfileLoaded(player2)) {
                            QuestLoader.INSTANCE.handleEvent(this.$this_register, player2, e);
                        }
                    }
                }
            }));
        }
    }

    public final <T> void handleEvent(@NotNull Objective<T> $this$handleEvent, @NotNull Player player, @NotNull T event) {
        Intrinsics.checkNotNullParameter($this$handleEvent, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter(event, (String)"event");
        if (ChemdahAPI.INSTANCE.isChemdahProfileLoaded(player)) {
            PlayerProfile playerProfile;
            PlayerProfile profile = playerProfile = ChemdahAPI.INSTANCE.getChemdahProfile(player);
            boolean bl = false;
            profile.tasks($this$handleEvent, arg_0 -> QuestLoader.handleEvent$lambda$3$lambda$2(event, arg_0));
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
        if (objective3.hasCompletedSignature(profile, task)) {
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
                        QuestContainer.agent$default(this.$task, this.$quest.getProfile(), AgentType.TASK_CONTINUED, null, null, 12, null);
                        new ObjectiveEvents.Continue.Post(this.$objective, this.$task, this.$quest, this.$profile).call();
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

    @Awake(value=LifeCycle.ACTIVE)
    private final void watch() {
        if (isDisableFileWatcher) {
            return;
        }
        FileWatcher.INSTANCE.addSimpleListener(new File(IOKt.getDataFolder(), "core/quest"), QuestLoader::watch$lambda$7);
    }

    @Awake(value=LifeCycle.ACTIVE)
    public final void loadAll() {
        this.loadTemplate();
        this.loadTemplateGroup();
        new PluginReloadEvent.Quest().call();
        Database.Companion.setup();
    }

    /*
     * WARNING - void declaration
     */
    public final void loadTemplate() {
        void $this$groupByTo$iv$iv;
        Template it;
        Iterable $this$mapTo$iv$iv;
        Object[] $this$map$iv;
        File file = new File(IOKt.getDataFolder(), "core/quest");
        if (FileKt.notfound((File)file)) {
            IOKt.releaseResourceFile$default((String)"core/quest/example.yml", (boolean)false, null, (int)6, null);
        }
        List<Template> templates = this.loadTemplate(file);
        ChemdahAPI.INSTANCE.getQuestTemplate().clear();
        Iterable iterable = templates;
        Map map = ChemdahAPI.INSTANCE.getQuestTemplate();
        boolean $i$f$map = false;
        Iterator iterator = $this$map$iv;
        Object destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            Template template = (Template)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl = false;
            collection.add(TuplesKt.to((Object)it.getId(), (Object)it));
        }
        MapsKt.putAll((Map)map, (Iterable)((List)destination$iv$iv));
        this.resetObjectiveUsageState();
        $this$map$iv = new Object[]{LocaleKt.t((String)("\n                \u5df2\u52a0\u8f7d " + ChemdahAPI.INSTANCE.getQuestTemplate().size() + " \u4e2a\u4efb\u52a1\u6a21\u677f\u3002\n                " + ChemdahAPI.INSTANCE.getQuestTemplate().size() + " templates loaded.\n            "))};
        IOKt.info((Object[])$this$map$iv);
        Iterable $this$groupBy$iv = templates;
        boolean $i$f$groupBy = false;
        $this$mapTo$iv$iv = $this$groupBy$iv;
        destination$iv$iv = new LinkedHashMap();
        boolean $i$f$groupByTo = false;
        for (Object element$iv$iv : $this$groupByTo$iv$iv) {
            Object object;
            it = (Template)element$iv$iv;
            boolean bl = false;
            String key$iv$iv = it.getId();
            Object $this$getOrPut$iv$iv$iv = destination$iv$iv;
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
        Object $this$forEach$iv = destination$iv$iv;
        boolean $i$f$forEach = false;
        iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry element$iv;
            Map.Entry entry = element$iv = iterator.next();
            boolean bl = false;
            String id2 = (String)entry.getKey();
            List c = (List)entry.getValue();
            if (c.size() <= 1) continue;
            Object[] objectArray = new Object[]{LocaleKt.t((String)("\n                        \u6709 " + c.size() + " \u4e2a\u4efb\u52a1\u6a21\u677f\u4f7f\u7528\u4e86\u91cd\u590d\u7684 ID: " + id2 + "\n                        There are " + c.size() + " templates using duplicate id: " + id2 + "\n                    "))};
            IOKt.warning((Object[])objectArray);
        }
    }

    @NotNull
    public final List<Template> loadTemplate(@NotNull File file) {
        Intrinsics.checkNotNullParameter((Object)file, (String)"file");
        return SequencesKt.toList((Sequence)SequencesKt.flatMapIterable((Sequence)SequencesKt.filter((Sequence)((Sequence)FilesKt.walk$default((File)file, null, (int)1, null)), (Function1)loadTemplate.4.INSTANCE), (Function1)((Function1)new Function1<File, List<? extends Template>>(file){
            final /* synthetic */ File $file;
            {
                this.$file = $file;
                super(1);
            }

            /*
             * WARNING - void declaration
             */
            @NotNull
            public final List<Template> invoke(@NotNull File it) {
                void $this$mapNotNullTo$iv$iv;
                void $this$mapNotNull$iv;
                void $this$filterTo$iv$iv;
                Iterable $this$filter$iv;
                Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                Configuration conf = Configuration.Companion.loadFromFile$default((Configuration.Companion)Configuration.Companion, (File)it, null, (boolean)false, (int)2, null);
                ConfigurationSection fileOption = null;
                fileOption = conf.getConfigurationSection(QuestLoader.INSTANCE.getOptionKey());
                Iterable iterable = conf.getKeys(false);
                boolean $i$f$filter = false;
                void var6_7 = $this$filter$iv;
                Collection destination$iv$iv = new ArrayList<E>();
                boolean $i$f$filterTo = false;
                for (T element$iv$iv : $this$filterTo$iv$iv) {
                    String it2 = (String)element$iv$iv;
                    boolean bl = false;
                    if (!(!Intrinsics.areEqual((Object)it2, (Object)QuestLoader.INSTANCE.getOptionKey()) && conf.isConfigurationSection(it2))) continue;
                    destination$iv$iv.add(element$iv$iv);
                }
                $this$filter$iv = (List)destination$iv$iv;
                File file = this.$file;
                boolean $i$f$mapNotNull = false;
                destination$iv$iv = $this$mapNotNull$iv;
                Collection destination$iv$iv2 = new ArrayList<E>();
                boolean $i$f$mapNotNullTo = false;
                void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
                boolean $i$f$forEach = false;
                Iterator<T> iterator = $this$forEach$iv$iv$iv.iterator();
                while (iterator.hasNext()) {
                    Template template;
                    ConfigurationSection section;
                    T element$iv$iv$iv;
                    T element$iv$iv = element$iv$iv$iv = iterator.next();
                    boolean bl = false;
                    String key = (String)element$iv$iv;
                    boolean bl2 = false;
                    Intrinsics.checkNotNull((Object)conf.getConfigurationSection(key));
                    if (new TemplateEvents.Load(file, key, section).call()) {
                        Template template2;
                        try {
                            template2 = ChemdahAPI.INSTANCE.getCoreConfigDeserializer().template(it, key, section, fileOption);
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
                    destination$iv$iv2.add(it$iv$iv);
                }
                return (List)destination$iv$iv2;
            }
        })));
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

    static {
        optionKey = "__option__";
        String[] stringArray = new String[]{"yaml", "yml", "json", "toml"};
        allowExtension = stringArray;
    }
}

