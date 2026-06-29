/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function2
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestChecker;
import ink.ptms.chemdah.core.quest.QuestLoader;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function2;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J\b\u0010\u0007\u001a\u00020\u0004H\u0016J*\u0010\b\u001a\f\u0012\b\u0012\u0006\u0012\u0002\b\u00030\n0\t2\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eH\u0016\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/core/quest/QuestChecker;", "", "()V", "check", "", "player", "Lorg/bukkit/entity/Player;", "checkAll", "checkTask", "", "Ljava/util/concurrent/CompletableFuture;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nQuestChecker.kt\nKotlin\n*S Kotlin\n*F\n+ 1 QuestChecker.kt\nink/ptms/chemdah/core/quest/QuestChecker\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 ArraysJVM.kt\nkotlin/collections/ArraysKt__ArraysJVMKt\n*L\n1#1,85:1\n766#2:86\n857#2,2:87\n1855#2,2:89\n766#2:91\n857#2,2:92\n1855#2:94\n1856#2:97\n766#2:98\n857#2,2:99\n1549#2:101\n1620#2,3:102\n37#3,2:95\n*S KotlinDebug\n*F\n+ 1 QuestChecker.kt\nink/ptms/chemdah/core/quest/QuestChecker\n*L\n28#1:86\n28#1:87,2\n29#1:89,2\n45#1:91\n45#1:92,2\n46#1:94\n46#1:97\n76#1:98\n76#1:99,2\n76#1:101\n76#1:102,3\n61#1:95,2\n*E\n"})
public class QuestChecker {
    /*
     * WARNING - void declaration
     */
    public void checkAll() {
        void $this$filterTo$iv$iv;
        Collection collection = Bukkit.getOnlinePlayers();
        Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"getOnlinePlayers()");
        Iterable $this$filter$iv = collection;
        boolean $i$f$filter = false;
        Iterable iterable = $this$filter$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            Player it = (Player)element$iv$iv;
            boolean bl = false;
            Intrinsics.checkNotNullExpressionValue((Object)it, (String)"it");
            if (!ChemdahAPI.INSTANCE.isChemdahProfileLoaded(it)) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        Iterable $this$forEach$iv = (List)destination$iv$iv;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Player player2 = (Player)element$iv;
            boolean bl = false;
            try {
                Intrinsics.checkNotNullExpressionValue((Object)player2, (String)"player");
                this.check(player2);
            }
            catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    /*
     * WARNING - void declaration
     */
    public void check(@NotNull Player player2) {
        void $this$filterTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        PlayerProfile profile = ChemdahAPI.INSTANCE.getChemdahProfile(player2);
        Iterable $this$filter$iv = PlayerProfile.getQuests$default(profile, false, 1, null);
        boolean $i$f$filter = false;
        Iterable iterable = $this$filter$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            Quest it = (Quest)element$iv$iv;
            boolean bl = false;
            if (!(!it.isFreeze() && !profile.isQuestCompleted(it.getTemplate()))) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        Iterable $this$forEach$iv = (List)destination$iv$iv;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Quest quest2 = (Quest)element$iv;
            boolean bl = false;
            try {
                if (quest2.getCheckLocked()) continue;
                quest2.setCheckLocked(true);
                if (quest2.isTimeout()) {
                    quest2.failQuestFuture().whenComplete((arg_0, arg_1) -> QuestChecker.check$lambda$5$lambda$3((Function2)new Function2<Boolean, Throwable, Unit>(quest2){
                        final /* synthetic */ Quest $quest;
                        {
                            this.$quest = $quest;
                            super(2);
                        }

                        public final void invoke(Boolean bl, Throwable throwable) {
                            this.$quest.setCheckLocked(false);
                        }
                    }, arg_0, arg_1));
                    continue;
                }
                Collection $this$toTypedArray$iv = this.checkTask(player2, profile, quest2);
                boolean $i$f$toTypedArray = false;
                Collection thisCollection$iv = $this$toTypedArray$iv;
                CompletableFuture[] completableFutureArray = thisCollection$iv.toArray(new CompletableFuture[0]);
                CompletableFuture.allOf(Arrays.copyOf(completableFutureArray, completableFutureArray.length)).whenComplete((arg_0, arg_1) -> QuestChecker.check$lambda$5$lambda$4((Function2)new Function2<Void, Throwable, Unit>(quest2){
                    final /* synthetic */ Quest $quest;
                    {
                        this.$quest = $quest;
                        super(2);
                    }

                    public final void invoke(Void void_, Throwable throwable) {
                        this.$quest.checkCompleteFuture().whenComplete((arg_0, arg_1) -> check.2.2.invoke$lambda$0((Function2)new Function2<Boolean, Throwable, Unit>(this.$quest){
                            final /* synthetic */ Quest $quest;
                            {
                                this.$quest = $quest;
                                super(2);
                            }

                            public final void invoke(Boolean bl, Throwable throwable) {
                                this.$quest.setCheckLocked(false);
                            }
                        }, arg_0, arg_1));
                    }

                    private static final void invoke$lambda$0(Function2 $tmp0, Object p0, Object p1) {
                        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
                        $tmp0.invoke(p0, p1);
                    }
                }, arg_0, arg_1));
            }
            catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public List<CompletableFuture<?>> checkTask(@NotNull Player player2, @NotNull PlayerProfile profile, @NotNull Quest quest2) {
        void $this$mapTo$iv$iv;
        void $this$map$iv;
        Task it;
        void $this$filterTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)player2, (String)"player");
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        Iterable $this$filter$iv = quest2.getTasks();
        boolean $i$f$filter = false;
        Iterable iterable = $this$filter$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            it = (Task)element$iv$iv;
            boolean bl = false;
            if (!(!it.isClosed(profile))) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        $this$filter$iv = (List)destination$iv$iv;
        boolean $i$f$map = false;
        $this$filterTo$iv$iv = $this$map$iv;
        destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            void task;
            it = (Task)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl = false;
            collection.add(task.getObjective().isTickable() ? QuestLoader.INSTANCE.handleTask(profile, (Task)task, quest2, UnitsKt.getEMPTY_EVENT()) : task.getObjective().checkComplete(profile, (Task)task, quest2));
        }
        return (List)destination$iv$iv;
    }

    private static final void check$lambda$5$lambda$3(Function2 $tmp0, Object p0, Object p1) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0, p1);
    }

    private static final void check$lambda$5$lambda$4(Function2 $tmp0, Object p0, Object p1) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0, p1);
    }
}

