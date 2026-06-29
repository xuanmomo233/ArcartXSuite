/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Deprecated
 *  kotlin.Metadata
 *  kotlin.ReplaceWith
 *  kotlin1822.Unit
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.ObjectiveEvents;
import ink.ptms.chemdah.api.event.collect.QuestEvents;
import ink.ptms.chemdah.core.DataContainer;
import ink.ptms.chemdah.core.DataContainerEventFactory;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.AgentType;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.AddonControl;
import ink.ptms.chemdah.core.quest.addon.AddonOptional;
import ink.ptms.chemdah.core.quest.addon.AddonRestart;
import ink.ptms.chemdah.core.quest.addon.AddonTimeout;
import ink.ptms.chemdah.core.quest.addon.data.ControlTrigger;
import ink.ptms.chemdah.util.FuturesKt;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kotlin.Deprecated;
import kotlin.Metadata;
import kotlin.ReplaceWith;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000X\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0011\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0010\u001e\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0016\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\b\u0010(\u001a\u00020)H\u0017J\u000e\u0010*\u001a\b\u0012\u0004\u0012\u00020\n0+H\u0016J\b\u0010,\u001a\u00020)H\u0016J\b\u0010-\u001a\u00020)H\u0017J\u000e\u0010.\u001a\b\u0012\u0004\u0012\u00020\n0+H\u0016J\b\u0010/\u001a\u00020)H\u0016J\u0012\u00100\u001a\u0004\u0018\u00010!2\u0006\u0010\u0002\u001a\u00020\u0003H\u0016J\u0016\u00101\u001a\u00020)2\f\u00102\u001a\b\u0012\u0004\u0012\u00020\n0+H\u0014J\u0010\u00103\u001a\u00020\n2\u0006\u00104\u001a\u000205H\u0016J\b\u00106\u001a\u00020)H\u0017J\u000e\u00107\u001a\b\u0012\u0004\u0012\u00020\n0+H\u0016R\u001a\u0010\t\u001a\u00020\nX\u0096\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\f\"\u0004\b\r\u0010\u000eR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0014\u0010\u0011\u001a\u00020\n8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0011\u0010\fR\u0014\u0010\u0012\u001a\u00020\n8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0012\u0010\fR\u0014\u0010\u0013\u001a\u00020\n8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0013\u0010\fR\u001a\u0010\u0014\u001a\u00020\nX\u0096\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0015\u0010\f\"\u0004\b\u0016\u0010\u000eR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001aR\u0014\u0010\u001b\u001a\u00020\u001c8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u001d\u0010\u001eR\u001a\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020!0 8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\"\u0010#R\u0014\u0010$\u001a\u00020%8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b&\u0010'\u00a8\u00068"}, d2={"Link/ptms/chemdah/core/quest/Quest;", "", "id", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "persistentDataContainer", "Link/ptms/chemdah/core/DataContainer;", "(Ljava/lang/String;Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/DataContainer;)V", "checkLocked", "", "getCheckLocked", "()Z", "setCheckLocked", "(Z)V", "getId", "()Ljava/lang/String;", "isCompleted", "isTimeout", "isValid", "newQuest", "getNewQuest", "setNewQuest", "getPersistentDataContainer", "()Link/ptms/chemdah/core/DataContainer;", "getProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "startTime", "", "getStartTime", "()J", "tasks", "", "Link/ptms/chemdah/core/quest/Task;", "getTasks", "()Ljava/util/Collection;", "template", "Link/ptms/chemdah/core/quest/Template;", "getTemplate", "()Link/ptms/chemdah/core/quest/Template;", "checkComplete", "", "checkCompleteFuture", "Ljava/util/concurrent/CompletableFuture;", "completeQuest", "failQuest", "failQuestFuture", "fakeCompleteQuest", "getTask", "handleComplete", "future", "isOwner", "player", "Lorg/bukkit/entity/Player;", "restartQuest", "restartQuestFuture", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nQuest.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Quest.kt\nink/ptms/chemdah/core/quest/Quest\n+ 2 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,257:1\n167#2,3:258\n288#3,2:261\n1855#3,2:263\n*S KotlinDebug\n*F\n+ 1 Quest.kt\nink/ptms/chemdah/core/quest/Quest\n*L\n48#1:258,3\n100#1:261,2\n109#1:263,2\n*E\n"})
public class Quest {
    @NotNull
    private final String id;
    @NotNull
    private final PlayerProfile profile;
    @NotNull
    private final DataContainer persistentDataContainer;
    private boolean newQuest;
    private boolean checkLocked;

    public Quest(@NotNull String id2, @NotNull PlayerProfile profile, @NotNull DataContainer persistentDataContainer) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)persistentDataContainer, (String)"persistentDataContainer");
        this.id = id2;
        this.profile = profile;
        this.persistentDataContainer = persistentDataContainer;
        this.persistentDataContainer.setEventFactory(DataContainerEventFactory.Companion.of(this.profile, this));
        if (this.persistentDataContainer.isEmpty()) {
            this.persistentDataContainer.set("start", System.currentTimeMillis());
            this.profile.getPersistentDataContainer().remove("quest.complete." + this.id);
        }
    }

    @NotNull
    public final String getId() {
        return this.id;
    }

    @NotNull
    public final PlayerProfile getProfile() {
        return this.profile;
    }

    @NotNull
    public final DataContainer getPersistentDataContainer() {
        return this.persistentDataContainer;
    }

    @NotNull
    public Template getTemplate() {
        Template template = ChemdahAPI.INSTANCE.getQuestTemplate(this.id);
        Intrinsics.checkNotNull((Object)template);
        return template;
    }

    public boolean isValid() {
        return ChemdahAPI.INSTANCE.getQuestTemplate(this.id) != null;
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public boolean isCompleted() {
        Map.Entry it;
        if (!this.isValid()) return false;
        Map $this$all$iv = this.getTemplate().getTaskMap();
        boolean $i$f$all = false;
        if ($this$all$iv.isEmpty()) {
            return true;
        }
        Iterator iterator = $this$all$iv.entrySet().iterator();
        do {
            Map.Entry element$iv;
            if (!iterator.hasNext()) return true;
            it = element$iv = iterator.next();
            boolean bl = false;
        } while (((Task)it.getValue()).getObjective().hasCompletedSignature(this.profile, (Task)it.getValue()));
        return false;
    }

    @NotNull
    public Collection<Task> getTasks() {
        Collection<Task> collection = this.getTemplate().getTaskMap().values();
        Intrinsics.checkNotNullExpressionValue(collection, (String)"template.taskMap.values");
        return collection;
    }

    public long getStartTime() {
        return this.persistentDataContainer.get("start", 0L).toLong();
    }

    public boolean isTimeout() {
        return AddonTimeout.Companion.isTimeout(this.getTemplate(), this.getStartTime());
    }

    public boolean getNewQuest() {
        return this.newQuest;
    }

    public void setNewQuest(boolean bl) {
        this.newQuest = bl;
    }

    public boolean getCheckLocked() {
        return this.checkLocked;
    }

    public void setCheckLocked(boolean bl) {
        this.checkLocked = bl;
    }

    public boolean isOwner(@NotNull Player player) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        return Intrinsics.areEqual((Object)this.profile.getUniqueId(), (Object)player.getUniqueId());
    }

    @Nullable
    public Task getTask(@NotNull String id2) {
        Object v0;
        block1: {
            Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
            Iterable $this$firstOrNull$iv = this.getTasks();
            boolean $i$f$firstOrNull = false;
            for (Object element$iv : $this$firstOrNull$iv) {
                Task it = (Task)element$iv;
                boolean bl = false;
                if (!Intrinsics.areEqual((Object)it.getId(), (Object)id2)) continue;
                v0 = element$iv;
                break block1;
            }
            v0 = null;
        }
        return v0;
    }

    public void completeQuest() {
        Iterable $this$forEach$iv = this.getTasks();
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Task it = (Task)element$iv;
            boolean bl = false;
            it.getObjective().setCompletedSignature(this.profile, it, true);
        }
        this.checkCompleteFuture();
    }

    public void fakeCompleteQuest() {
        this.handleComplete(new CompletableFuture<Boolean>());
    }

    @Deprecated(message="Use checkCompleteFuture", replaceWith=@ReplaceWith(expression="checkCompleteFuture()", imports={}))
    public void checkComplete() {
        this.checkCompleteFuture();
    }

    @NotNull
    public CompletableFuture<Boolean> checkCompleteFuture() {
        CompletableFuture<Boolean> completableFuture;
        if (this.profile.getPersistentDataContainer().containsKey("quest.complete." + this.id)) {
            CompletableFuture<Boolean> completableFuture2 = CompletableFuture.completedFuture(false);
            Intrinsics.checkNotNullExpressionValue(completableFuture2, (String)"completedFuture(false)");
            return completableFuture2;
        }
        CompletableFuture<Boolean> future = completableFuture = new CompletableFuture<Boolean>();
        boolean bl = false;
        AddonRestart.Companion.canRestart(this.getTemplate(), this.profile).thenAccept(arg_0 -> Quest.checkCompleteFuture$lambda$4$lambda$3((Function1)new Function1<Boolean, Unit>(this, future){
            final /* synthetic */ Quest this$0;
            final /* synthetic */ CompletableFuture<Boolean> $future;
            {
                this.this$0 = $receiver;
                this.$future = $future;
                super(1);
            }

            /*
             * WARNING - void declaration
             */
            public final void invoke(Boolean restart2) {
                Intrinsics.checkNotNullExpressionValue((Object)restart2, (String)"restart");
                if (restart2.booleanValue()) {
                    this.this$0.restartQuestFuture().thenAccept(arg_0 -> checkCompleteFuture.1.1.invoke$lambda$0((Function1)new Function1<Boolean, Unit>(this.$future){
                        final /* synthetic */ CompletableFuture<Boolean> $future;
                        {
                            this.$future = $future;
                            super(1);
                        }

                        public final void invoke(Boolean it) {
                            FuturesKt.failure(this.$future);
                        }
                    }, arg_0));
                } else {
                    boolean bl;
                    block7: {
                        void $this$all$iv;
                        Iterable iterable = this.this$0.getTasks();
                        Quest quest2 = this.this$0;
                        boolean $i$f$all = false;
                        if ($this$all$iv instanceof Collection && ((Collection)$this$all$iv).isEmpty()) {
                            bl = true;
                        } else {
                            for (T element$iv : $this$all$iv) {
                                Task it = (Task)element$iv;
                                boolean bl2 = false;
                                if (it.getObjective().hasCompletedSignature(quest2.getProfile(), it) || AddonOptional.Companion.isOptional(it)) continue;
                                bl = false;
                                break block7;
                            }
                            bl = true;
                        }
                    }
                    if (bl) {
                        this.this$0.handleComplete(this.$future);
                    } else {
                        FuturesKt.failure(this.$future);
                    }
                }
            }

            private static final void invoke$lambda$0(Function1 $tmp0, Object p0) {
                Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
                $tmp0.invoke(p0);
            }
        }, arg_0));
        return completableFuture;
    }

    protected void handleComplete(@NotNull CompletableFuture<Boolean> future) {
        Intrinsics.checkNotNullParameter(future, (String)"future");
        if (new QuestEvents.Complete.Pre(this, this.profile).call()) {
            CompletionStage completionStage = QuestContainer.agent$default(this.getTemplate(), this.profile, AgentType.QUEST_COMPLETE, null, null, 12, null).thenAccept(arg_0 -> Quest.handleComplete$lambda$5((Function1)new Function1<Boolean, Unit>(this, future){
                final /* synthetic */ Quest this$0;
                final /* synthetic */ CompletableFuture<Boolean> $future;
                {
                    this.this$0 = $receiver;
                    this.$future = $future;
                    super(1);
                }

                public final void invoke(Boolean result) {
                    Intrinsics.checkNotNullExpressionValue((Object)result, (String)"result");
                    if (result.booleanValue() && !this.this$0.getProfile().getPersistentDataContainer().containsKey("quest.complete." + this.this$0.getId())) {
                        if (this.this$0.getTemplate().getRecordCompleted()) {
                            this.this$0.getProfile().getPersistentDataContainer().set("quest.complete." + this.this$0.getId(), System.currentTimeMillis());
                        }
                        PlayerProfile.unregisterQuest$default(this.this$0.getProfile(), this.this$0, false, 2, null);
                        AddonControl.Companion.control(this.this$0.getTemplate()).signature(this.this$0.getProfile(), ControlTrigger.COMPLETE);
                        QuestContainer.agent$default(this.this$0.getTemplate(), this.this$0.getProfile(), AgentType.QUEST_COMPLETED, null, null, 12, null);
                        new QuestEvents.Complete.Post(this.this$0, this.this$0.getProfile()).call();
                    }
                    this.$future.complete(result);
                }
            }, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"protected open fun handl\u2026failure()\n        }\n    }");
            FuturesKt.exceptNull(completionStage, (Function1<? super Throwable, Unit>)((Function1)new Function1<Throwable, Unit>(future){
                final /* synthetic */ CompletableFuture<Boolean> $future;
                {
                    this.$future = $future;
                    super(1);
                }

                public final void invoke(@NotNull Throwable it) {
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    FuturesKt.failure(this.$future);
                }
            }));
        } else {
            FuturesKt.failure(future);
        }
    }

    @Deprecated(message="Use failQuestFuture", replaceWith=@ReplaceWith(expression="failQuestFuture()", imports={}))
    public void failQuest() {
        this.failQuestFuture();
    }

    @NotNull
    public CompletableFuture<Boolean> failQuestFuture() {
        CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();
        if (new QuestEvents.Fail.Pre(this, this.profile).call()) {
            CompletionStage completionStage = QuestContainer.agent$default(this.getTemplate(), this.profile, AgentType.QUEST_FAIL, null, null, 12, null).thenAccept(arg_0 -> Quest.failQuestFuture$lambda$6((Function1)new Function1<Boolean, Unit>(this, future){
                final /* synthetic */ Quest this$0;
                final /* synthetic */ CompletableFuture<Boolean> $future;
                {
                    this.this$0 = $receiver;
                    this.$future = $future;
                    super(1);
                }

                public final void invoke(Boolean result) {
                    Intrinsics.checkNotNullExpressionValue((Object)result, (String)"result");
                    if (result.booleanValue()) {
                        PlayerProfile.unregisterQuest$default(this.this$0.getProfile(), this.this$0, false, 2, null);
                        AddonControl.Companion.control(this.this$0.getTemplate()).signature(this.this$0.getProfile(), ControlTrigger.FAIL);
                        QuestContainer.agent$default(this.this$0.getTemplate(), this.this$0.getProfile(), AgentType.QUEST_FAILED, null, null, 12, null);
                        new QuestEvents.Fail.Post(this.this$0, this.this$0.getProfile()).call();
                    }
                    this.$future.complete(result);
                }
            }, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"open fun failQuestFuture\u2026      return future\n    }");
            FuturesKt.exceptNull(completionStage, (Function1<? super Throwable, Unit>)((Function1)new Function1<Throwable, Unit>(future){
                final /* synthetic */ CompletableFuture<Boolean> $future;
                {
                    this.$future = $future;
                    super(1);
                }

                public final void invoke(@NotNull Throwable it) {
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    FuturesKt.failure(this.$future);
                }
            }));
        } else {
            FuturesKt.failure(future);
        }
        return future;
    }

    @Deprecated(message="Use restartQuestFuture", replaceWith=@ReplaceWith(expression="restartQuestFuture()", imports={}))
    public void restartQuest() {
        this.restartQuestFuture();
    }

    @NotNull
    public CompletableFuture<Boolean> restartQuestFuture() {
        CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();
        if (new QuestEvents.Restart.Pre(this, this.profile).call()) {
            CompletionStage completionStage = QuestContainer.agent$default(this.getTemplate(), this.profile, AgentType.QUEST_RESTART, null, null, 12, null).thenAccept(arg_0 -> Quest.restartQuestFuture$lambda$7((Function1)new Function1<Boolean, Unit>(this, future){
                final /* synthetic */ Quest this$0;
                final /* synthetic */ CompletableFuture<Boolean> $future;
                {
                    this.this$0 = $receiver;
                    this.$future = $future;
                    super(1);
                }

                /*
                 * WARNING - void declaration
                 */
                public final void invoke(Boolean result) {
                    Intrinsics.checkNotNullExpressionValue((Object)result, (String)"result");
                    if (result.booleanValue()) {
                        void $this$forEach$iv;
                        Iterable iterable = this.this$0.getTasks();
                        Quest quest2 = this.this$0;
                        boolean $i$f$forEach = false;
                        for (T element$iv : $this$forEach$iv) {
                            Task task = (Task)element$iv;
                            boolean bl = false;
                            if (!new ObjectiveEvents.Restart.Pre(task.getObjective(), task, quest2, quest2.getProfile()).call()) continue;
                            task.getObjective().onReset(quest2.getProfile(), task, quest2);
                        }
                        this.this$0.getPersistentDataContainer().clear();
                        this.this$0.getPersistentDataContainer().set("start", System.currentTimeMillis());
                        QuestContainer.agent$default(this.this$0.getTemplate(), this.this$0.getProfile(), AgentType.QUEST_RESTARTED, null, null, 12, null);
                        new QuestEvents.Restart.Post(this.this$0, this.this$0.getProfile()).call();
                    }
                    this.$future.complete(result);
                }
            }, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)completionStage, (String)"open fun restartQuestFut\u2026      return future\n    }");
            FuturesKt.exceptNull(completionStage, (Function1<? super Throwable, Unit>)((Function1)new Function1<Throwable, Unit>(future){
                final /* synthetic */ CompletableFuture<Boolean> $future;
                {
                    this.$future = $future;
                    super(1);
                }

                public final void invoke(@NotNull Throwable it) {
                    Intrinsics.checkNotNullParameter((Object)it, (String)"it");
                    FuturesKt.failure(this.$future);
                }
            }));
        } else {
            FuturesKt.failure(future);
        }
        return future;
    }

    private static final void checkCompleteFuture$lambda$4$lambda$3(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final void handleComplete$lambda$5(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final void failQuestFuture$lambda$6(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final void restartQuestFuture$lambda$7(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }
}

