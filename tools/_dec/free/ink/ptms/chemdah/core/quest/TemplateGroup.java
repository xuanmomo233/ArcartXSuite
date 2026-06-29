/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.AcceptResult;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Template;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000D\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\"\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u000b\n\u0002\u0010 \n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u001b\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u00a2\u0006\u0002\u0010\u0007J\u0018\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\b\b\u0002\u0010\u0012\u001a\u00020\u0013J\u000e\u0010\u0014\u001a\u00020\u00132\u0006\u0010\u0010\u001a\u00020\u0011J\u000e\u0010\u0015\u001a\u00020\u00132\u0006\u0010\u0010\u001a\u00020\u0011J\u000e\u0010\u0016\u001a\u00020\u00132\u0006\u0010\u0010\u001a\u00020\u0011J\u0018\u0010\u0017\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\b\b\u0002\u0010\u0012\u001a\u00020\u0013J\u000e\u0010\u0018\u001a\u00020\u00132\u0006\u0010\u0010\u001a\u00020\u0011J\t\u0010\u0019\u001a\u00020\u0003H\u00c6\u0003J\u000f\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005H\u00c6\u0003J#\u0010\u001b\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\u000e\b\u0002\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005H\u00c6\u0001J\u0013\u0010\u001c\u001a\u00020\u00132\b\u0010\u001d\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\"\u0010\u001e\u001a\b\u0012\u0004\u0012\u00020\u00030\u001f2\u0014\b\u0002\u0010 \u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00130!J\t\u0010\"\u001a\u00020#H\u00d6\u0001J\t\u0010$\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR \u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\r\u00a8\u0006%"}, d2={"Link/ptms/chemdah/core/quest/TemplateGroup;", "", "id", "", "quests", "", "Link/ptms/chemdah/core/quest/Template;", "(Ljava/lang/String;Ljava/util/Set;)V", "getId", "()Ljava/lang/String;", "getQuests", "()Ljava/util/Set;", "setQuests", "(Ljava/util/Set;)V", "acceptTo", "", "profile", "Link/ptms/chemdah/core/PlayerProfile;", "skipCheck", "", "accepted", "checkAccept", "checkComplete", "complete", "completed", "component1", "component2", "copy", "equals", "other", "getQuestsId", "", "filter", "Ljava/util/function/Function;", "hashCode", "", "toString", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nTemplateGroup.kt\nKotlin\n*S Kotlin\n*F\n+ 1 TemplateGroup.kt\nink/ptms/chemdah/core/quest/TemplateGroup\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,81:1\n1726#2,3:82\n1726#2,3:85\n1855#2,2:88\n1726#2,3:90\n1726#2,3:93\n1855#2,2:96\n766#2:98\n857#2,2:99\n1549#2:101\n1620#2,3:102\n*S KotlinDebug\n*F\n+ 1 TemplateGroup.kt\nink/ptms/chemdah/core/quest/TemplateGroup\n*L\n21#1:82,3\n30#1:85,3\n41#1:88,2\n50#1:90,3\n59#1:93,3\n70#1:96,2\n79#1:98\n79#1:99,2\n79#1:101\n79#1:102,3\n*E\n"})
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

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public final boolean checkComplete(@NotNull PlayerProfile profile) {
        boolean bl;
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        Iterable $this$all$iv = this.quests;
        boolean $i$f$all = false;
        if ($this$all$iv instanceof Collection && ((Collection)$this$all$iv).isEmpty()) {
            return true;
        }
        Iterator iterator = $this$all$iv.iterator();
        do {
            if (!iterator.hasNext()) return true;
            Object element$iv = iterator.next();
            Template it = (Template)element$iv;
            boolean bl2 = false;
            if (PlayerProfile.getQuestById$default(profile, it.getId(), false, 2, null) == null) return false;
            Quest quest2 = PlayerProfile.getQuestById$default(profile, it.getId(), false, 2, null);
            Intrinsics.checkNotNull((Object)quest2);
            Boolean bl3 = quest2.checkCompleteFuture().get();
            Intrinsics.checkNotNullExpressionValue((Object)bl3, (String)"profile.getQuestById(it.\u2026eckCompleteFuture().get()");
            if (bl3 == false) return false;
            bl = true;
        } while (bl);
        return false;
    }

    public final boolean completed(@NotNull PlayerProfile profile) {
        boolean bl;
        block3: {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Iterable $this$all$iv = this.quests;
            boolean $i$f$all = false;
            if ($this$all$iv instanceof Collection && ((Collection)$this$all$iv).isEmpty()) {
                bl = true;
            } else {
                for (Object element$iv : $this$all$iv) {
                    Template it = (Template)element$iv;
                    boolean bl2 = false;
                    if (profile.isQuestCompleted(it)) continue;
                    bl = false;
                    break block3;
                }
                bl = true;
            }
        }
        return bl;
    }

    public final void complete(@NotNull PlayerProfile profile, boolean skipCheck) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        if (!this.checkComplete(profile) && !skipCheck) {
            return;
        }
        Iterable $this$forEach$iv = this.quests;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Template it = (Template)element$iv;
            boolean bl = false;
            Quest quest2 = PlayerProfile.getQuestById$default(profile, it.getId(), false, 2, null);
            if (quest2 == null) continue;
            quest2.completeQuest();
        }
    }

    public static /* synthetic */ void complete$default(TemplateGroup templateGroup, PlayerProfile playerProfile2, boolean bl, int n, Object object) {
        if ((n & 2) != 0) {
            bl = false;
        }
        templateGroup.complete(playerProfile2, bl);
    }

    public final boolean checkAccept(@NotNull PlayerProfile profile) {
        boolean bl;
        block3: {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Iterable $this$all$iv = this.quests;
            boolean $i$f$all = false;
            if ($this$all$iv instanceof Collection && ((Collection)$this$all$iv).isEmpty()) {
                bl = true;
            } else {
                for (Object element$iv : $this$all$iv) {
                    Template it = (Template)element$iv;
                    boolean bl2 = false;
                    if (it.checkAccept(profile).get().getType() == AcceptResult.Type.SUCCESSFUL) continue;
                    bl = false;
                    break block3;
                }
                bl = true;
            }
        }
        return bl;
    }

    public final boolean accepted(@NotNull PlayerProfile profile) {
        boolean bl;
        block3: {
            Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
            Iterable $this$all$iv = this.quests;
            boolean $i$f$all = false;
            if ($this$all$iv instanceof Collection && ((Collection)$this$all$iv).isEmpty()) {
                bl = true;
            } else {
                for (Object element$iv : $this$all$iv) {
                    Template it = (Template)element$iv;
                    boolean bl2 = false;
                    if (PlayerProfile.getQuestById$default(profile, it.getId(), false, 2, null) != null) continue;
                    bl = false;
                    break block3;
                }
                bl = true;
            }
        }
        return bl;
    }

    public final void acceptTo(@NotNull PlayerProfile profile, boolean skipCheck) {
        Intrinsics.checkNotNullParameter((Object)profile, (String)"profile");
        if (!this.checkAccept(profile) && !skipCheck) {
            return;
        }
        Iterable $this$forEach$iv = this.quests;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Template it = (Template)element$iv;
            boolean bl = false;
            it.acceptTo(profile);
        }
    }

    public static /* synthetic */ void acceptTo$default(TemplateGroup templateGroup, PlayerProfile playerProfile2, boolean bl, int n, Object object) {
        if ((n & 2) != 0) {
            bl = false;
        }
        templateGroup.acceptTo(playerProfile2, bl);
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public final List<String> getQuestsId(@NotNull Function<Template, Boolean> filter) {
        void $this$mapTo$iv$iv;
        Template it;
        Iterable $this$filterTo$iv$iv;
        Intrinsics.checkNotNullParameter(filter, (String)"filter");
        Iterable $this$filter$iv = this.quests;
        boolean $i$f$filter = false;
        Iterable iterable = $this$filter$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            it = (Template)element$iv$iv;
            boolean bl = false;
            Boolean bl2 = filter.apply(it);
            Intrinsics.checkNotNullExpressionValue((Object)bl2, (String)"filter.apply(it)");
            if (!bl2.booleanValue()) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        Iterable $this$map$iv = (List)destination$iv$iv;
        boolean $i$f$map = false;
        $this$filterTo$iv$iv = $this$map$iv;
        destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            it = (Template)item$iv$iv;
            Collection collection = destination$iv$iv;
            boolean bl = false;
            collection.add(it.getId());
        }
        return (List)destination$iv$iv;
    }

    public static /* synthetic */ List getQuestsId$default(TemplateGroup templateGroup, Function function, int n, Object object) {
        if ((n & 1) != 0) {
            function = TemplateGroup::getQuestsId$lambda$6;
        }
        return templateGroup.getQuestsId(function);
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

    private static final Boolean getQuestsId$lambda$6(Template template) {
        Intrinsics.checkNotNullParameter((Object)template, (String)"<anonymous parameter 0>");
        return true;
    }
}

