/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.api;

import ink.ptms.chemdah.api.event.plugin.CollectEvent;
import ink.ptms.chemdah.api.event.plugin.ObjectiveCallEvent;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.objective.Objective;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000L\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0003\b\u0016\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u001c\u0010\f\u001a\u00020\r2\n\u0010\u000e\u001a\u0006\u0012\u0002\b\u00030\u000f2\u0006\u0010\u0010\u001a\u00020\u0001H\u0016J$\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00130\u00122\u0006\u0010\u0014\u001a\u00020\u00152\f\u0010\u0016\u001a\b\u0012\u0004\u0012\u00020\u00130\u0012H\u0016J\u0010\u0010\u0017\u001a\u00020\u00182\u0006\u0010\u0019\u001a\u00020\u0005H\u0016J\u0010\u0010\u001a\u001a\u00020\u00182\u0006\u0010\u0019\u001a\u00020\nH\u0016R$\u0010\u0003\u001a\u0012\u0012\u0004\u0012\u00020\u00050\u0004j\b\u0012\u0004\u0012\u00020\u0005`\u0006X\u0084\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR$\u0010\t\u001a\u0012\u0012\u0004\u0012\u00020\n0\u0004j\b\u0012\u0004\u0012\u00020\n`\u0006X\u0084\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\b\u00a8\u0006\u001b"}, d2={"Link/ptms/chemdah/api/ChemdahEventFactory;", "", "()V", "objectiveCallCallback", "Ljava/util/ArrayList;", "Link/ptms/chemdah/api/event/plugin/ObjectiveCallEvent;", "Lkotlin1822/collections/ArrayList;", "getObjectiveCallCallback", "()Ljava/util/ArrayList;", "questCollectCallback", "Link/ptms/chemdah/api/event/plugin/CollectEvent;", "getQuestCollectCallback", "callObjectiveCall", "", "objective", "Link/ptms/chemdah/core/quest/objective/Objective;", "event", "callQuestCollect", "", "Link/ptms/chemdah/core/quest/Quest;", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "quests", "prepareObjectiveCall", "", "consumer", "prepareQuestCollect", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nChemdahEventFactory.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ChemdahEventFactory.kt\nink/ptms/chemdah/api/ChemdahEventFactory\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,47:1\n1855#2,2:48\n1855#2,2:50\n*S KotlinDebug\n*F\n+ 1 ChemdahEventFactory.kt\nink/ptms/chemdah/api/ChemdahEventFactory\n*L\n39#1:48,2\n44#1:50,2\n*E\n"})
public class ChemdahEventFactory {
    @NotNull
    private final ArrayList<CollectEvent> questCollectCallback = new ArrayList();
    @NotNull
    private final ArrayList<ObjectiveCallEvent> objectiveCallCallback = new ArrayList();

    @NotNull
    protected final ArrayList<CollectEvent> getQuestCollectCallback() {
        return this.questCollectCallback;
    }

    @NotNull
    protected final ArrayList<ObjectiveCallEvent> getObjectiveCallCallback() {
        return this.objectiveCallCallback;
    }

    public void prepareObjectiveCall(@NotNull ObjectiveCallEvent consumer) {
        Intrinsics.checkNotNullParameter((Object)consumer, (String)"consumer");
        this.objectiveCallCallback.add(consumer);
    }

    public void prepareQuestCollect(@NotNull CollectEvent consumer) {
        Intrinsics.checkNotNullParameter((Object)consumer, (String)"consumer");
        this.questCollectCallback.add(consumer);
    }

    @NotNull
    public List<Quest> callQuestCollect(@NotNull PlayerProfile profile, @NotNull List<? extends Quest> quests) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Intrinsics.checkNotNullParameter(quests, (String)"quests");
        List list2 = CollectionsKt.toMutableList((Collection)quests);
        Iterable $this$forEach$iv = this.questCollectCallback;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            CollectEvent it = (CollectEvent)element$iv;
            boolean bl = false;
            it.invoke(profile, list2);
        }
        return list2;
    }

    public boolean callObjectiveCall(@NotNull Objective<?> objective2, @NotNull Object event) {
        Intrinsics.checkNotNullParameter(objective2, (String)"objective");
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        Iterable $this$forEach$iv = this.objectiveCallCallback;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            ObjectiveCallEvent it = (ObjectiveCallEvent)element$iv;
            boolean bl = false;
            it.invoke(objective2, event);
        }
        return true;
    }
}

