/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.core.quest.Template;
import java.util.Set;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\"\n\u0002\u0018\u0002\n\u0002\b\u000b\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u001b\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u00a2\u0006\u0002\u0010\u0007J\t\u0010\u000e\u001a\u00020\u0003H\u00c6\u0003J\u000f\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005H\u00c6\u0003J#\u0010\u0010\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\u000e\b\u0002\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005H\u00c6\u0001J\u0013\u0010\u0011\u001a\u00020\u00122\b\u0010\u0013\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0014\u001a\u00020\u0015H\u00d6\u0001J\t\u0010\u0016\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR \u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\r\u00a8\u0006\u0017"}, d2={"Link/ptms/chemdah/core/quest/TemplateGroup;", "", "id", "", "quests", "", "Link/ptms/chemdah/core/quest/Template;", "(Ljava/lang/String;Ljava/util/Set;)V", "getId", "()Ljava/lang/String;", "getQuests", "()Ljava/util/Set;", "setQuests", "(Ljava/util/Set;)V", "component1", "component2", "copy", "equals", "", "other", "hashCode", "", "toString", "Chemdah"})
public final class TemplateGroup {
    @NotNull
    private final String id;
    @NotNull
    private Set<? extends Template> quests;

    public TemplateGroup(@NotNull String id2, @NotNull Set<? extends Template> quests) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter(quests, (String)"quests");
        this.id = id2;
        this.quests = quests;
    }

    @NotNull
    public final String getId() {
        return this.id;
    }

    @NotNull
    public final Set<Template> getQuests() {
        return this.quests;
    }

    public final void setQuests(@NotNull Set<? extends Template> set2) {
        Intrinsics.checkNotNullParameter(set2, (String)"<set-?>");
        this.quests = set2;
    }

    @NotNull
    public final String component1() {
        return this.id;
    }

    @NotNull
    public final Set<Template> component2() {
        return this.quests;
    }

    @NotNull
    public final TemplateGroup copy(@NotNull String id2, @NotNull Set<? extends Template> quests) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter(quests, (String)"quests");
        return new TemplateGroup(id2, quests);
    }

    public static /* synthetic */ TemplateGroup copy$default(TemplateGroup templateGroup, String string, Set set2, int n, Object object) {
        if ((n & 1) != 0) {
            string = templateGroup.id;
        }
        if ((n & 2) != 0) {
            set2 = templateGroup.quests;
        }
        return templateGroup.copy(string, set2);
    }

    @NotNull
    public String toString() {
        return "TemplateGroup(id=" + this.id + ", quests=" + this.quests + ')';
    }

    public int hashCode() {
        int result = this.id.hashCode();
        result = result * 31 + ((Object)this.quests).hashCode();
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TemplateGroup)) {
            return false;
        }
        TemplateGroup templateGroup = (TemplateGroup)other;
        if (!Intrinsics.areEqual((Object)this.id, (Object)templateGroup.id)) {
            return false;
        }
        return Intrinsics.areEqual(this.quests, templateGroup.quests);
    }
}

