/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon;

import ink.ptms.chemdah.api.event.collect.QuestEvents;
import ink.ptms.chemdah.core.quest.Id;
import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.addon.Addon;
import ink.ptms.chemdah.core.quest.objective.other.IPlayerData;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Id(id="reset-data-on-accepted")
@Option(type=Option.Type.BOOLEAN)
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0007\u0018\u0000 \t2\u00020\u0001:\u0001\tB\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\b\u00a8\u0006\n"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonResetDataOnAccepted;", "Link/ptms/chemdah/core/quest/addon/Addon;", "value", "", "task", "Link/ptms/chemdah/core/quest/Task;", "(ZLink/ptms/chemdah/core/quest/Task;)V", "getValue", "()Z", "Companion", "Chemdah"})
public final class AddonResetDataOnAccepted
extends Addon {
    @NotNull
    public static final Companion Companion = new Companion(null);
    private final boolean value;

    public AddonResetDataOnAccepted(boolean value2, @NotNull Task task) {
        Intrinsics.checkNotNullParameter((Object)task, (String)"task");
        super(value2, task);
        this.value = value2;
    }

    public final boolean getValue() {
        return this.value;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0003J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0007H\u0003J\n\u0010\b\u001a\u00020\t*\u00020\n\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/core/quest/addon/AddonResetDataOnAccepted$Companion;", "", "()V", "onAccepted", "", "e", "Link/ptms/chemdah/api/event/collect/QuestEvents$Accept$Post;", "Link/ptms/chemdah/api/event/collect/QuestEvents$Restart$Post;", "isResetDataOnAccepted", "", "Link/ptms/chemdah/core/quest/Task;", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nAddonResetDataOnAccepted.kt\nKotlin\n*S Kotlin\n*F\n+ 1 AddonResetDataOnAccepted.kt\nink/ptms/chemdah/core/quest/addon/AddonResetDataOnAccepted$Companion\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,78:1\n1855#2,2:79\n1855#2,2:81\n*S KotlinDebug\n*F\n+ 1 AddonResetDataOnAccepted.kt\nink/ptms/chemdah/core/quest/addon/AddonResetDataOnAccepted$Companion\n*L\n61#1:79,2\n71#1:81,2\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        public final boolean isResetDataOnAccepted(@NotNull Task $this$isResetDataOnAccepted) {
            Intrinsics.checkNotNullParameter((Object)$this$isResetDataOnAccepted, (String)"<this>");
            AddonResetDataOnAccepted addonResetDataOnAccepted = (AddonResetDataOnAccepted)$this$isResetDataOnAccepted.addon("reset-data-on-accepted");
            return addonResetDataOnAccepted != null ? addonResetDataOnAccepted.getValue() : false;
        }

        @SubscribeEvent
        private final void onAccepted(QuestEvents.Accept.Post e) {
            Iterable $this$forEach$iv = e.getQuest().getTasks();
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Task task = (Task)element$iv;
                boolean bl = false;
                if (!(task.getObjective() instanceof IPlayerData) || !Companion.isResetDataOnAccepted(task)) continue;
                e.getPlayerProfile().getPersistentDataContainer().remove(String.valueOf(task.getGoal().get("key")));
            }
        }

        @SubscribeEvent
        private final void onAccepted(QuestEvents.Restart.Post e) {
            Iterable $this$forEach$iv = e.getQuest().getTasks();
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Task task = (Task)element$iv;
                boolean bl = false;
                if (!(task.getObjective() instanceof IPlayerData) || !Companion.isResetDataOnAccepted(task)) continue;
                e.getPlayerProfile().getPersistentDataContainer().remove(String.valueOf(task.getGoal().get("key")));
            }
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

