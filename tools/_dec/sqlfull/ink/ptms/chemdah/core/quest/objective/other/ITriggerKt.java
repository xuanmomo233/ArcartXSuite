/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.objective.other;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.objective.Objective;
import ink.ptms.chemdah.core.quest.objective.other.ITrigger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000\u0014\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u001a\u0010\u0010\u0000\u001a\b\u0012\u0004\u0012\u00020\u00020\u0001*\u00020\u0003\u001a\u0010\u0010\u0000\u001a\b\u0012\u0004\u0012\u00020\u00020\u0001*\u00020\u0004\u00a8\u0006\u0005"}, d2={"getAvailableTriggers", "", "", "Link/ptms/chemdah/core/PlayerProfile;", "Link/ptms/chemdah/core/quest/Quest;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nITrigger.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ITrigger.kt\nink/ptms/chemdah/core/quest/objective/other/ITriggerKt\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,46:1\n1360#2:47\n1446#2,5:48\n766#2:53\n857#2,2:54\n1360#2:56\n1446#2,5:57\n*S KotlinDebug\n*F\n+ 1 ITrigger.kt\nink/ptms/chemdah/core/quest/objective/other/ITriggerKt\n*L\n41#1:47\n41#1:48,5\n45#1:53\n45#1:54,2\n45#1:56\n45#1:57,5\n*E\n"})
public final class ITriggerKt {
    /*
     * WARNING - void declaration
     */
    @NotNull
    public static final List<String> getAvailableTriggers(@NotNull PlayerProfile $this$getAvailableTriggers) {
        void $this$flatMapTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)$this$getAvailableTriggers, (String)"<this>");
        Iterable $this$flatMap$iv = $this$getAvailableTriggers.getQuests(true);
        boolean $i$f$flatMap = false;
        Iterable iterable = $this$flatMap$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$flatMapTo = false;
        for (Object element$iv$iv : $this$flatMapTo$iv$iv) {
            Quest quest2 = (Quest)element$iv$iv;
            boolean bl = false;
            Iterable list$iv$iv = ITriggerKt.getAvailableTriggers(quest2);
            CollectionsKt.addAll((Collection)destination$iv$iv, (Iterable)list$iv$iv);
        }
        return CollectionsKt.toList((Iterable)CollectionsKt.toSet((Iterable)((List)destination$iv$iv)));
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public static final List<String> getAvailableTriggers(@NotNull Quest $this$getAvailableTriggers) {
        void $this$flatMapTo$iv$iv;
        Task it;
        Iterable $this$filterTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)$this$getAvailableTriggers, (String)"<this>");
        Iterable $this$filter$iv = $this$getAvailableTriggers.getTasks();
        boolean $i$f$filter = false;
        Iterable iterable = $this$filter$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            it = (Task)element$iv$iv;
            boolean bl = false;
            if (!(it.getObjective() instanceof ITrigger)) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        Iterable $this$flatMap$iv = (List)destination$iv$iv;
        boolean $i$f$flatMap = false;
        $this$filterTo$iv$iv = $this$flatMap$iv;
        destination$iv$iv = new ArrayList();
        boolean $i$f$flatMapTo = false;
        for (Object element$iv$iv : $this$flatMapTo$iv$iv) {
            it = (Task)element$iv$iv;
            boolean bl = false;
            Objective<? extends Object> objective2 = it.getObjective();
            Intrinsics.checkNotNull(objective2, (String)"null cannot be cast to non-null type ink.ptms.chemdah.core.quest.objective.other.ITrigger");
            Iterable list$iv$iv = ((ITrigger)objective2).getValues(it);
            CollectionsKt.addAll((Collection)destination$iv$iv, (Iterable)list$iv$iv);
        }
        return CollectionsKt.toList((Iterable)CollectionsKt.toSet((Iterable)((List)destination$iv$iv)));
    }
}

