/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestChecker;
import ink.ptms.chemdah.core.quest.QuestLoader;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.module.lang.LangKt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J\b\u0010\u0007\u001a\u00020\u0004H\u0016J*\u0010\b\u001a\f\u0012\b\u0012\u0006\u0012\u0002\b\u00030\n0\t2\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eH\u0016\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/core/quest/QuestChecker;", "", "()V", "check", "", "player", "Lorg/bukkit/entity/Player;", "checkAll", "checkTask", "", "Ljava/util/concurrent/CompletableFuture;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nQuestChecker.kt\nKotlin\n*S Kotlin\n*F\n+ 1 QuestChecker.kt\nink/ptms/chemdah/core/quest/QuestChecker\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 ArraysJVM.kt\nkotlin/collections/ArraysKt__ArraysJVMKt\n*L\n1#1,70:1\n766#2:71\n857#2,2:72\n1855#2,2:74\n1855#2:76\n1856#2:79\n1549#2:80\n1620#2,3:81\n37#3,2:77\n*S KotlinDebug\n*F\n+ 1 QuestChecker.kt\nink/ptms/chemdah/core/quest/QuestChecker\n*L\n26#1:71\n26#1:72,2\n26#1:74,2\n35#1:76\n35#1:79\n61#1:80\n61#1:81,3\n49#1:77,2\n*E\n"})
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
            Player player = (Player)element$iv;
            boolean bl = false;
            Intrinsics.checkNotNullExpressionValue((Object)player, (String)"player");
            this.check(player);
        }
    }

    public void check(@NotNull Player player) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        PlayerProfile profile = ChemdahAPI.INSTANCE.getChemdahProfile(player);
        Iterable $this$forEach$iv = PlayerProfile.getQuests$default(profile, false, 1, null);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Object[] objectArray;
            Quest quest2 = (Quest)element$iv;
            boolean bl = false;
            if (quest2.getCheckLocked()) {
                ProxyCommandSender proxyCommandSender = AdapterKt.console();
                objectArray = new Object[2];
                Intrinsics.checkNotNullExpressionValue((Object)player.getName(), (String)"player.name");
                objectArray[1] = quest2.getId();
                LangKt.sendLang((ProxyCommandSender)proxyCommandSender, (String)"console-quest-locked", (Object[])objectArray);
                continue;
            }
            quest2.setCheckLocked(true);
            if (quest2.isTimeout()) {
                quest2.failQuestFuture().thenAccept(arg_0 -> QuestChecker.check$lambda$4$lambda$2((Function1)new Function1<Boolean, Unit>(quest2){
                    final /* synthetic */ Quest $quest;
                    {
                        this.$quest = $quest;
                        super(1);
                    }

                    public final void invoke(Boolean it) {
                        this.$quest.setCheckLocked(false);
                    }
                }, arg_0));
                continue;
            }
            Collection $this$toTypedArray$iv = this.checkTask(player, profile, quest2);
            boolean $i$f$toTypedArray = false;
            Collection thisCollection$iv = $this$toTypedArray$iv;
            objectArray = thisCollection$iv.toArray(new CompletableFuture[0]);
            CompletableFuture.allOf((CompletableFuture[])Arrays.copyOf(objectArray, objectArray.length)).thenAccept(arg_0 -> QuestChecker.check$lambda$4$lambda$3((Function1)new Function1<Void, Unit>(quest2){
                final /* synthetic */ Quest $quest;
                {
                    this.$quest = $quest;
                    super(1);
                }

                public final void invoke(Void it) {
                    this.$quest.checkCompleteFuture().thenAccept(arg_0 -> check.1.2.invoke$lambda$0((Function1)new Function1<Boolean, Unit>(this.$quest){
                        final /* synthetic */ Quest $quest;
                        {
                            this.$quest = $quest;
                            super(1);
                        }

                        public final void invoke(Boolean it) {
                            this.$quest.setCheckLocked(false);
                        }
                    }, arg_0));
                }

                private static final void invoke$lambda$0(Function1 $tmp0, Object p0) {
                    Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
                    $tmp0.invoke(p0);
                }
            }, arg_0));
        }
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public List<CompletableFuture<?>> checkTask(@NotNull Player player, @NotNull PlayerProfile profile, @NotNull Quest quest2) {
        void $this$mapTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter((Object)quest2, (String)"quest");
        Iterable $this$map$iv = quest2.getTasks();
        boolean $i$f$map = false;
        Iterable iterable = $this$map$iv;
        Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            void task;
            Task task2 = (Task)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl = false;
            collection.add(task.getObjective().isTickable() ? QuestLoader.INSTANCE.handleTask(profile, (Task)task, quest2, UnitsKt.getEMPTY_EVENT()) : task.getObjective().checkComplete(profile, (Task)task, quest2));
        }
        return (List)destination$iv$iv;
    }

    private static final void check$lambda$4$lambda$2(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final void check$lambda$4$lambda$3(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }
}

