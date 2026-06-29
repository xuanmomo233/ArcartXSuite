/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.addon.data;

import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.addon.data.Plan;
import java.util.ArrayList;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0007\u0010\b\"\u0004\b\t\u0010\nR\u001a\u0010\u0004\u001a\u00020\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\f\"\u0004\b\r\u0010\u000eR!\u0010\u000f\u001a\u0012\u0012\u0004\u0012\u00020\u00110\u0010j\b\u0012\u0004\u0012\u00020\u0011`\u0012\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014\u00a8\u0006\u0015"}, d2={"Link/ptms/chemdah/core/quest/addon/data/PlanGroup;", "", "groupId", "", "plan", "Link/ptms/chemdah/core/quest/addon/data/Plan;", "(Ljava/lang/String;Link/ptms/chemdah/core/quest/addon/data/Plan;)V", "getGroupId", "()Ljava/lang/String;", "setGroupId", "(Ljava/lang/String;)V", "getPlan", "()Link/ptms/chemdah/core/quest/addon/data/Plan;", "setPlan", "(Link/ptms/chemdah/core/quest/addon/data/Plan;)V", "quests", "Ljava/util/ArrayList;", "Link/ptms/chemdah/core/quest/Template;", "Lkotlin1822/collections/ArrayList;", "getQuests", "()Ljava/util/ArrayList;", "Chemdah"})
public final class PlanGroup {
    @NotNull
    private String groupId;
    @NotNull
    private Plan plan;
    @NotNull
    private final ArrayList<Template> quests;

    public PlanGroup(@NotNull String groupId, @NotNull Plan plan) {
        Intrinsics.checkNotNullParameter((Object)groupId, (String)"groupId");
        Intrinsics.checkNotNullParameter((Object)plan, (String)"plan");
        this.groupId = groupId;
        this.plan = plan;
        this.quests = new ArrayList();
    }

    @NotNull
    public final String getGroupId() {
        return this.groupId;
    }

    public final void setGroupId(@NotNull String string) {
        Intrinsics.checkNotNullParameter((Object)string, (String)"<set-?>");
        this.groupId = string;
    }

    @NotNull
    public final Plan getPlan() {
        return this.plan;
    }

    public final void setPlan(@NotNull Plan plan) {
        Intrinsics.checkNotNullParameter((Object)plan, (String)"<set-?>");
        this.plan = plan;
    }

    @NotNull
    public final ArrayList<Template> getQuests() {
        return this.quests;
    }
}

