/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.util.CollectionKt
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  kotlin.Metadata
 *  kotlin1822.Result
 *  kotlin1822.ResultKt
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.functions.Function2
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.QuestEvents;
import ink.ptms.chemdah.core.DataContainerEventFactory;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.SimpleDataContainer;
import ink.ptms.chemdah.core.quest.AcceptResult;
import ink.ptms.chemdah.core.quest.AgentType;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.AddonControl;
import ink.ptms.chemdah.core.quest.addon.data.ControlResult;
import ink.ptms.chemdah.core.quest.addon.data.ControlTrigger;
import ink.ptms.chemdah.core.quest.meta.Meta;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.util.FuturesKt;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Metadata;
import kotlin1822.Result;
import kotlin1822.ResultKt;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.functions.Function2;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000h\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0010 \n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0010$\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0016\u0018\u0000 52\u00020\u0001:\u00015B'\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\b\u0010\b\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\u0002\u0010\tJ\u0016\u0010#\u001a\b\u0012\u0004\u0012\u00020%0$2\u0006\u0010&\u001a\u00020'H\u0016J\u001a\u0010(\u001a\u00020)2\u0006\u0010*\u001a\u00020\u00032\b\b\u0002\u0010+\u001a\u00020\u0003H\u0016J\u0016\u0010,\u001a\b\u0012\u0004\u0012\u00020%0$2\u0006\u0010&\u001a\u00020'H\u0016J\u0018\u0010-\u001a\u0012\u0012\u0004\u0012\u00020\u0003\u0012\b\u0012\u0006\u0012\u0002\b\u00030/0.H\u0016J\u0018\u00100\u001a\u0002012\u0006\u00102\u001a\u00020\u00032\u0006\u0010&\u001a\u00020'H\u0016J\u0010\u00103\u001a\u00020\u00182\u0006\u00104\u001a\u00020\u0003H\u0002R\u0013\u0010\n\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0013\u0010\b\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R \u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00030\u0012X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0013\u0010\u0014\"\u0004\b\u0015\u0010\u0016R\u0011\u0010\u0017\u001a\u00020\u0018\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001aR\u0011\u0010\u001b\u001a\u00020\u0018\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u001aR-\u0010\u001d\u001a\u001e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u001f0\u001ej\u000e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u001f` \u00a2\u0006\b\n\u0000\u001a\u0004\b!\u0010\"\u00a8\u00066"}, d2={"Link/ptms/chemdah/core/quest/Template;", "Link/ptms/chemdah/core/quest/QuestContainer;", "id", "", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "file", "Ljava/io/File;", "fileOption", "(Ljava/lang/String;Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;Ljava/io/File;Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "dataIsolation", "getDataIsolation", "()Ljava/lang/String;", "getFile", "()Ljava/io/File;", "getFileOption", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "metaImport", "", "getMetaImport", "()Ljava/util/List;", "setMetaImport", "(Ljava/util/List;)V", "recordQuestCompleted", "", "getRecordQuestCompleted", "()Z", "recordTaskCompleted", "getRecordTaskCompleted", "taskMap", "Ljava/util/HashMap;", "Link/ptms/chemdah/core/quest/Task;", "Lkotlin1822/collections/HashMap;", "getTaskMap", "()Ljava/util/HashMap;", "acceptTo", "Ljava/util/concurrent/CompletableFuture;", "Link/ptms/chemdah/core/quest/AcceptResult;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "addTask", "", "taskId", "taskNode", "checkAccept", "metaAll", "", "Link/ptms/chemdah/core/quest/meta/Meta;", "newQuest", "Link/ptms/chemdah/core/quest/Quest;", "questId", "resolveRecordOption", "key", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nTemplate.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Template.kt\nink/ptms/chemdah/core/quest/Template\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,213:1\n766#2:214\n857#2,2:215\n1855#2,2:217\n1855#2,2:219\n1855#2:221\n1856#2:223\n1#3:222\n*S KotlinDebug\n*F\n+ 1 Template.kt\nink/ptms/chemdah/core/quest/Template\n*L\n56#1:214\n56#1:215,2\n56#1:217,2\n57#1:219,2\n158#1:221\n158#1:223\n*E\n"})
public class Template
extends QuestContainer {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final File file;
    @Nullable
    private final ConfigurationSection fileOption;
    @Nullable
    private final String dataIsolation;
    private final boolean recordQuestCompleted;
    private final boolean recordTaskCompleted;
    @NotNull
    private final HashMap<String, Task> taskMap;
    @NotNull
    private List<String> metaImport;
    private static final long LOCK_TTL_MS = 30000L;
    @NotNull
    private static final ConcurrentHashMap<String, Long> acceptLocks = new ConcurrentHashMap();

    /*
     * WARNING - void declaration
     */
    public Template(@NotNull String id2, @NotNull ConfigurationSection config, @NotNull File file, @Nullable ConfigurationSection fileOption) {
        void $this$forEach$iv;
        Iterator $this$filterTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        Intrinsics.checkNotNullParameter((Object)file, (String)"file");
        super(id2, config);
        this.file = file;
        ConfigurationSection configurationSection = this.fileOption = fileOption;
        this.dataIsolation = config.getString("data-isolation", configurationSection != null ? configurationSection.getString("data-isolation") : null);
        ConfigurationSection configurationSection2 = this.fileOption;
        this.recordQuestCompleted = config.getBoolean("record-quest-completed", configurationSection2 != null ? configurationSection2.getBoolean("record-quest-completed", true) : true);
        this.recordTaskCompleted = this.resolveRecordOption("record-task-completed");
        this.taskMap = new LinkedHashMap();
        Object object = config.get("meta.import");
        if (object == null || (object = CollectionKt.asList((Object)object)) == null) {
            object = CollectionsKt.emptyList();
        }
        this.metaImport = object;
        Iterable $this$filter$iv = config.getKeys(false);
        boolean $i$f$filter = false;
        Iterable iterable = $this$filter$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        Iterator iterator = $this$filterTo$iv$iv.iterator();
        while (iterator.hasNext()) {
            Object element$iv$iv = iterator.next();
            String it = (String)element$iv$iv;
            boolean bl = false;
            if (!StringsKt.startsWith$default((String)it, (String)"task:", (boolean)false, (int)2, null)) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        $this$filter$iv = (List)destination$iv$iv;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            String it = (String)element$iv;
            boolean bl = false;
            String string = it.substring(5);
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).substring(startIndex)");
            this.addTask(string, it);
        }
        Object object2 = config.getConfigurationSection("task");
        if (object2 != null && (object2 = object2.getKeys(false)) != null) {
            Iterable $this$forEach$iv2 = (Iterable)object2;
            boolean $i$f$forEach2 = false;
            for (Object element$iv : $this$forEach$iv2) {
                String node = (String)element$iv;
                boolean bl = false;
                Template.addTask$default(this, node, null, 2, null);
            }
        }
        this.metaAll();
    }

    @NotNull
    public final File getFile() {
        return this.file;
    }

    @Nullable
    public final ConfigurationSection getFileOption() {
        return this.fileOption;
    }

    @Nullable
    public final String getDataIsolation() {
        return this.dataIsolation;
    }

    public final boolean getRecordQuestCompleted() {
        return this.recordQuestCompleted;
    }

    public final boolean getRecordTaskCompleted() {
        return this.recordTaskCompleted;
    }

    private final boolean resolveRecordOption(String key) {
        ConfigurationSection configurationSection = this.fileOption;
        boolean fileDefault = configurationSection != null ? configurationSection.getBoolean(key, this.fileOption.getBoolean("record-completed", false)) : false;
        return this.getConfig().getBoolean(key, this.getConfig().getBoolean("record-completed", fileDefault));
    }

    @NotNull
    public final HashMap<String, Task> getTaskMap() {
        return this.taskMap;
    }

    @NotNull
    public final List<String> getMetaImport() {
        return this.metaImport;
    }

    public final void setMetaImport(@NotNull List<String> list2) {
        Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
        this.metaImport = list2;
    }

    public void addTask(@NotNull String taskId, @NotNull String taskNode) {
        Intrinsics.checkNotNullParameter((Object)taskId, (String)"taskId");
        Intrinsics.checkNotNullParameter((Object)taskNode, (String)"taskNode");
        Map map = this.taskMap;
        ConfigurationSection configurationSection = this.getConfig().getConfigurationSection(taskNode);
        if (configurationSection == null) {
            throw new IllegalStateException(("Task " + taskId + " not found").toString());
        }
        Task task = new Task(taskId, configurationSection, this);
        map.put(taskId, task);
    }

    public static /* synthetic */ void addTask$default(Template template, String string, String string2, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: addTask");
        }
        if ((n & 2) != 0) {
            string2 = "task." + string;
        }
        template.addTask(string, string2);
    }

    @NotNull
    public Quest newQuest(@NotNull String questId2, @NotNull PlayerProfile profile) {
        Intrinsics.checkNotNullParameter((Object)questId2, (String)"questId");
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        return new Quest(questId2, profile, new SimpleDataContainer(DataContainerEventFactory.Companion.getEMPTY()));
    }

    @NotNull
    public CompletableFuture<AcceptResult> acceptTo(@NotNull PlayerProfile profile) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        if (!Companion.tryLock(profile, this.getId())) {
            CompletableFuture<AcceptResult> completableFuture = CompletableFuture.completedFuture(new AcceptResult(AcceptResult.Type.ALREADY_EXISTS));
            Intrinsics.checkNotNullExpressionValue(completableFuture, (String)"completedFuture(AcceptRe\u2026ult.Type.ALREADY_EXISTS))");
            return completableFuture;
        }
        CompletionStage completionStage = this.checkAccept(profile).thenApply(arg_0 -> Template.acceptTo$lambda$3((Function1)new Function1<AcceptResult, AcceptResult>(profile, this){
            final /* synthetic */ PlayerProfile $profile;
            final /* synthetic */ Template this$0;
            {
                this.$profile = $profile;
                this.this$0 = $receiver;
                super(1);
            }

            /*
             * WARNING - void declaration
             */
            public final AcceptResult invoke(AcceptResult result) {
                if (result.getType() == AcceptResult.Type.SUCCESSFUL) {
                    void $this$forEach$iv;
                    this.$profile.getPersistentDataContainer().remove("quest.complete." + this.this$0.getId());
                    Map map = this.this$0.getTaskMap();
                    PlayerProfile playerProfile2 = this.$profile;
                    Template template = this.this$0;
                    boolean $i$f$forEach = false;
                    Iterator<Map.Entry<K, V>> iterator = $this$forEach$iv.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<K, V> element$iv;
                        Map.Entry<K, V> entry = element$iv = iterator.next();
                        boolean bl = false;
                        Task task = (Task)entry.getValue();
                        playerProfile2.getPersistentDataContainer().remove("quest.complete." + template.getId() + '.' + task.getId());
                    }
                    AddonControl.Companion.control(this.this$0).signature(this.$profile, ControlTrigger.ACCEPT);
                    Quest quest2 = this.this$0.newQuest(this.this$0.getId(), this.$profile);
                    PlayerProfile.registerQuest$default(this.$profile, quest2, false, 2, null);
                    QuestContainer.agent$default(this.this$0, this.$profile, AgentType.QUEST_ACCEPTED, null, null, 12, null);
                    new QuestEvents.Accept.Post(quest2, this.$profile).call();
                } else {
                    QuestContainer.agent$default(this.this$0, this.$profile, AgentType.QUEST_ACCEPT_CANCELLED, null, result.getReason(), 4, null);
                }
                return result;
            }
        }, arg_0));
        Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"open fun acceptTo(profil\u2026       // endregion\n    }");
        CompletionStage completionStage2 = FuturesKt.except(completionStage, acceptTo.2.INSTANCE).whenComplete((arg_0, arg_1) -> Template.acceptTo$lambda$4((Function2)new Function2<AcceptResult, Throwable, Unit>(profile, this){
            final /* synthetic */ PlayerProfile $profile;
            final /* synthetic */ Template this$0;
            {
                this.$profile = $profile;
                this.this$0 = $receiver;
                super(2);
            }

            public final void invoke(AcceptResult acceptResult, Throwable throwable) {
                Template.Companion.unlock(this.$profile, this.this$0.getId());
            }
        }, arg_0, arg_1));
        Intrinsics.checkNotNullExpressionValue((Object)completionStage2, (String)"open fun acceptTo(profil\u2026       // endregion\n    }");
        return completionStage2;
    }

    @NotNull
    public CompletableFuture<AcceptResult> checkAccept(@NotNull PlayerProfile profile) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        CompletableFuture<AcceptResult> future = new CompletableFuture<AcceptResult>();
        if (profile.getQuestById(this.getId(), false) != null) {
            future.complete(new AcceptResult(AcceptResult.Type.ALREADY_EXISTS));
            return future;
        }
        QuestEvents.Accept.Pre pre = new QuestEvents.Accept.Pre(this, profile);
        if (!pre.call()) {
            future.complete(new AcceptResult(AcceptResult.Type.CANCELLED_BY_EVENT, pre.getReason()));
            return future;
        }
        CompletionStage completionStage = AddonControl.Companion.control(this).check(profile).thenApply(arg_0 -> Template.checkAccept$lambda$5((Function1)new Function1<ControlResult, Object>(this, profile, future){
            final /* synthetic */ Template this$0;
            final /* synthetic */ PlayerProfile $profile;
            final /* synthetic */ CompletableFuture<AcceptResult> $future;
            {
                this.this$0 = $receiver;
                this.$profile = $profile;
                this.$future = $future;
                super(1);
            }

            public final Object invoke(ControlResult result) {
                return result.getPass() ? QuestContainer.agent$default(this.this$0, this.$profile, AgentType.QUEST_ACCEPT, null, null, 12, null).thenAccept(arg_0 -> checkAccept.1.invoke$lambda$0((Function1)new Function1<Boolean, Unit>(this.$future){
                    final /* synthetic */ CompletableFuture<AcceptResult> $future;
                    {
                        this.$future = $future;
                        super(1);
                    }

                    public final void invoke(Boolean a) {
                        Intrinsics.checkNotNullExpressionValue((Object)a, (String)"a");
                        if (a.booleanValue()) {
                            this.$future.complete(new AcceptResult(AcceptResult.Type.SUCCESSFUL));
                        } else {
                            this.$future.complete(new AcceptResult(AcceptResult.Type.CANCELLED_BY_AGENT));
                        }
                    }
                }, arg_0)) : Boolean.valueOf(this.$future.complete(new AcceptResult(AcceptResult.Type.CANCELLED_BY_CONTROL, result.getReason())));
            }

            private static final void invoke$lambda$0(Function1 $tmp0, Object p0) {
                Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
                $tmp0.invoke(p0);
            }
        }, arg_0));
        Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"open fun checkAccept(pro\u2026       // endregion\n    }");
        FuturesKt.except(completionStage, (Function1)new Function1<Throwable, Object>(future){
            final /* synthetic */ CompletableFuture<AcceptResult> $future;
            {
                this.$future = $future;
                super(1);
            }

            public final Object invoke(@NotNull Throwable it) {
                Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                return this.$future.complete(new AcceptResult(AcceptResult.Type.FAILED, it.getMessage()));
            }
        });
        return future;
    }

    @NotNull
    public Map<String, Meta<?>> metaAll() {
        HashMap map = new HashMap();
        if (!((Collection)this.metaImport).isEmpty()) {
            Iterable $this$forEach$iv = this.metaImport;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Object object;
                String clone = (String)element$iv;
                boolean bl = false;
                if (ChemdahAPI.INSTANCE.getQuestTemplate(clone) == null) continue;
                try {
                    Template quest2;
                    boolean bl2 = false;
                    map.putAll(quest2.metaAll());
                    object = Result.constructor-impl((Object)Unit.INSTANCE);
                }
                catch (Throwable throwable) {
                    object = Result.constructor-impl((Object)ResultKt.createFailure((Throwable)throwable));
                }
            }
        }
        map.putAll((Map)this.getMetaMap());
        return map;
    }

    private static final AcceptResult acceptTo$lambda$3(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        return (AcceptResult)$tmp0.invoke(p0);
    }

    private static final void acceptTo$lambda$4(Function2 $tmp0, Object p0, Object p1) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0, p1);
    }

    private static final Object checkAccept$lambda$5(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        return $tmp0.invoke(p0);
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000@\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000bJ\u0016\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u0007J\u0016\u0010\u0011\u001a\u00020\t2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u0007J\n\u0010\u0012\u001a\u00020\u0013*\u00020\u0014R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0005\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00040\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0015"}, d2={"Link/ptms/chemdah/core/quest/Template$Companion;", "", "()V", "LOCK_TTL_MS", "", "acceptLocks", "Ljava/util/concurrent/ConcurrentHashMap;", "", "releaseLocksForPlayer", "", "uniqueId", "Ljava/util/UUID;", "tryLock", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "questId", "unlock", "toTemplate", "Link/ptms/chemdah/core/quest/Template;", "Link/ptms/chemdah/core/quest/QuestContainer;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        public final boolean tryLock(@NotNull PlayerProfile profile, @NotNull String questId2) {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Intrinsics.checkNotNullParameter((Object)questId2, (String)"questId");
            String key = profile.getUniqueId() + ':' + questId2;
            long now = System.currentTimeMillis();
            Long l = acceptLocks.putIfAbsent(key, now);
            if (l == null) {
                return true;
            }
            long existingTime = ((Number)l).longValue();
            if (now - existingTime > 30000L) {
                acceptLocks.remove(key, existingTime);
                return acceptLocks.putIfAbsent(key, now) == null;
            }
            return false;
        }

        public final void unlock(@NotNull PlayerProfile profile, @NotNull String questId2) {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Intrinsics.checkNotNullParameter((Object)questId2, (String)"questId");
            String key = profile.getUniqueId() + ':' + questId2;
            acceptLocks.remove(key);
        }

        public final void releaseLocksForPlayer(@NotNull UUID uniqueId) {
            Intrinsics.checkNotNullParameter((Object)uniqueId, (String)"uniqueId");
            String prefix = "" + uniqueId + ':';
            ((ConcurrentHashMap.KeySetView)acceptLocks.keySet()).removeIf(arg_0 -> Companion.releaseLocksForPlayer$lambda$0((Function1)new Function1<String, Boolean>(prefix){
                final /* synthetic */ String $prefix;
                {
                    this.$prefix = $prefix;
                    super(1);
                }

                @NotNull
                public final Boolean invoke(String it) {
                    Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
                    return StringsKt.startsWith$default((String)it, (String)this.$prefix, (boolean)false, (int)2, null);
                }
            }, arg_0));
        }

        @NotNull
        public final Template toTemplate(@NotNull QuestContainer $this$toTemplate) {
            Intrinsics.checkNotNullParameter((Object)$this$toTemplate, (String)"<this>");
            return $this$toTemplate instanceof Task ? ((Task)$this$toTemplate).getTemplate() : (Template)$this$toTemplate;
        }

        private static final boolean releaseLocksForPlayer$lambda$0(Function1 $tmp0, Object p0) {
            Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
            return (Boolean)$tmp0.invoke(p0);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

