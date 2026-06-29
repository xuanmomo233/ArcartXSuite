/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.util.LocaleKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.ranges.RangesKt
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.taboolib.common.util.LocaleKt;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.RangesKt;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000J\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0006\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0006\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J \u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00142\b\b\u0002\u0010\u0015\u001a\u00020\u0014J \u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00162\b\b\u0002\u0010\u0015\u001a\u00020\u0016J\u0006\u0010\u0017\u001a\u00020\u0010J\u0013\u0010\u0018\u001a\u0004\u0018\u00010\u00192\u0006\u0010\u0011\u001a\u00020\u0012H\u0086\u0002J\u0019\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u001a\u001a\u00020\u0001H\u0086\u0002J\u000e\u0010\u001b\u001a\u00020\u001c2\u0006\u0010\u0011\u001a\u00020\u0012J\u000e\u0010\u001d\u001a\u00020\u001c2\u0006\u0010\u0011\u001a\u00020\u0012J\u0016\u0010\u001e\u001a\u00020\u001c2\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014J\u0016\u0010\u001e\u001a\u00020\u001c2\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0016J\u000e\u0010\u001f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u0012J\u001b\u0010 \u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u00122\b\u0010\u0013\u001a\u0004\u0018\u00010\u0001H\u0086\u0002J\u001f\u0010!\u001a\u00020\u00102\b\u0010\t\u001a\u0004\u0018\u00010\nH\u0002\u0082\u0002\n\n\b\b\u0000\u001a\u0004\b\u0003\u0010\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0013\u0010\t\u001a\u0004\u0018\u00010\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\""}, d2={"Link/ptms/chemdah/core/quest/QuestDataOperator;", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "task", "Link/ptms/chemdah/core/quest/Task;", "(Link/ptms/chemdah/core/PlayerProfile;Link/ptms/chemdah/core/quest/Task;)V", "getProfile", "()Link/ptms/chemdah/core/PlayerProfile;", "quest", "Link/ptms/chemdah/core/quest/Quest;", "getQuest", "()Link/ptms/chemdah/core/quest/Quest;", "getTask", "()Link/ptms/chemdah/core/quest/Task;", "add", "", "node", "", "value", "", "max", "", "clear", "get", "Link/ptms/chemdah/core/Data;", "def", "isEmpty", "", "isTrue", "more", "remove", "set", "validation", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nQuestDataOperator.kt\nKotlin\n*S Kotlin\n*F\n+ 1 QuestDataOperator.kt\nink/ptms/chemdah/core/quest/QuestDataOperator\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,131:1\n1#2:132\n*E\n"})
public final class QuestDataOperator {
    @NotNull
    private final PlayerProfile profile;
    @NotNull
    private final Task task;
    @Nullable
    private final Quest quest;

    public QuestDataOperator(@NotNull PlayerProfile profile, @NotNull Task task) {
        Object v0;
        block1: {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Intrinsics.checkNotNullParameter((Object)task, (String)"task");
            this.profile = profile;
            this.task = task;
            Iterable iterable = PlayerProfile.getQuests$default(this.profile, false, 1, null);
            QuestDataOperator questDataOperator = this;
            Iterable iterable2 = iterable;
            for (Object t : iterable2) {
                Quest it = (Quest)t;
                boolean bl = false;
                if (!Intrinsics.areEqual((Object)it.getId(), (Object)this.task.getTemplate().getId())) continue;
                v0 = t;
                break block1;
            }
            v0 = null;
        }
        questDataOperator.quest = v0;
    }

    @NotNull
    public final PlayerProfile getProfile() {
        return this.profile;
    }

    @NotNull
    public final Task getTask() {
        return this.task;
    }

    @Nullable
    public final Quest getQuest() {
        return this.quest;
    }

    private final void validation(Quest quest2) {
        if (quest2 == null) {
            throw new IllegalStateException(LocaleKt.t((String)("\n                    \u4efb\u52a1\u6570\u636e\u63a7\u5236\u5668\u521d\u59cb\u5316\u5931\u8d25\uff0c\u6ca1\u6709\u627e\u5230\u73a9\u5bb6 " + this.profile.getPlayer().getName() + " \u7684\u4efb\u52a1 " + this.task.getTemplate().getId() + "\n                    Quest data operator init failed, no quest found for player " + this.profile.getPlayer().getName() + " with id " + this.task.getTemplate().getId() + "\n                ")).toString());
        }
    }

    public final void remove(@NotNull String node) {
        Intrinsics.checkNotNullParameter((Object)node, (String)"node");
        this.validation(this.quest);
        this.quest.getPersistentDataContainer().remove(this.task.getId() + '.' + node);
    }

    public final boolean isEmpty(@NotNull String node) {
        Intrinsics.checkNotNullParameter((Object)node, (String)"node");
        this.validation(this.quest);
        return this.quest.getPersistentDataContainer().get(this.task.getId() + '.' + node) == null;
    }

    public final boolean more(@NotNull String node, int value2) {
        Intrinsics.checkNotNullParameter((Object)node, (String)"node");
        this.validation(this.quest);
        Data data2 = this.quest.getPersistentDataContainer().get(this.task.getId() + '.' + node);
        return (data2 != null ? data2.toInt() : 0) >= value2;
    }

    public final boolean more(@NotNull String node, double value2) {
        Intrinsics.checkNotNullParameter((Object)node, (String)"node");
        this.validation(this.quest);
        Data data2 = this.quest.getPersistentDataContainer().get(this.task.getId() + '.' + node);
        return (data2 != null ? data2.toDouble() : 0.0) >= value2;
    }

    public final boolean isTrue(@NotNull String node) {
        Intrinsics.checkNotNullParameter((Object)node, (String)"node");
        this.validation(this.quest);
        Data data2 = this.quest.getPersistentDataContainer().get(this.task.getId() + '.' + node);
        return data2 != null ? data2.toBoolean() : false;
    }

    @Nullable
    public final Data get(@NotNull String node) {
        Intrinsics.checkNotNullParameter((Object)node, (String)"node");
        this.validation(this.quest);
        return this.quest.getPersistentDataContainer().get(this.task.getId() + '.' + node);
    }

    @NotNull
    public final Data get(@NotNull String node, @NotNull Object def) {
        Intrinsics.checkNotNullParameter((Object)node, (String)"node");
        Intrinsics.checkNotNullParameter((Object)def, (String)"def");
        this.validation(this.quest);
        Data data2 = this.quest.getPersistentDataContainer().get(this.task.getId() + '.' + node);
        if (data2 == null) {
            data2 = Data.Companion.unsafe(def);
        }
        return data2;
    }

    public final void set(@NotNull String node, @Nullable Object value2) {
        Intrinsics.checkNotNullParameter((Object)node, (String)"node");
        this.validation(this.quest);
        if (value2 != null) {
            this.quest.getPersistentDataContainer().set(this.task.getId() + '.' + node, value2);
        } else {
            this.quest.getPersistentDataContainer().remove(this.task.getId() + '.' + node);
        }
    }

    public final void add(@NotNull String node, int value2, int max2) {
        Intrinsics.checkNotNullParameter((Object)node, (String)"node");
        this.validation(this.quest);
        this.quest.getPersistentDataContainer().set(this.task.getId() + '.' + node, RangesKt.coerceAtMost((int)(this.quest.getPersistentDataContainer().get(this.task.getId() + '.' + node, 0).toInt() + value2), (int)max2));
    }

    public static /* synthetic */ void add$default(QuestDataOperator questDataOperator, String string, int n, int n2, int n3, Object object) {
        if ((n3 & 4) != 0) {
            n2 = Integer.MAX_VALUE;
        }
        questDataOperator.add(string, n, n2);
    }

    public final void add(@NotNull String node, double value2, double max2) {
        Intrinsics.checkNotNullParameter((Object)node, (String)"node");
        this.validation(this.quest);
        this.quest.getPersistentDataContainer().set(this.task.getId() + '.' + node, RangesKt.coerceAtMost((double)(this.quest.getPersistentDataContainer().get(this.task.getId() + '.' + node, 0.0).toDouble() + value2), (double)max2));
    }

    public static /* synthetic */ void add$default(QuestDataOperator questDataOperator, String string, double d, double d2, int n, Object object) {
        if ((n & 4) != 0) {
            d2 = Double.MAX_VALUE;
        }
        questDataOperator.add(string, d, d2);
    }

    public final void clear() {
        this.validation(this.quest);
        this.quest.getPersistentDataContainer().removeIf(arg_0 -> QuestDataOperator.clear$lambda$2(this, arg_0));
    }

    private static final Boolean clear$lambda$2(QuestDataOperator this$0, Map.Entry it) {
        Intrinsics.checkNotNullParameter((Object)this$0, (String)"this$0");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return StringsKt.startsWith$default((String)((String)it.getKey()), (String)(this$0.task.getId() + '.'), (boolean)false, (int)2, null);
    }
}

