/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.quest.Template;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\f\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/core/quest/QuestDataIsolation;", "", "()V", "getKeys", "", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nQuestDataIsolation.kt\nKotlin\n*S Kotlin\n*F\n+ 1 QuestDataIsolation.kt\nink/ptms/chemdah/core/quest/QuestDataIsolation\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,21:1\n1603#2,9:22\n1855#2:31\n1856#2:33\n1612#2:34\n1#3:32\n*S KotlinDebug\n*F\n+ 1 QuestDataIsolation.kt\nink/ptms/chemdah/core/quest/QuestDataIsolation\n*L\n19#1:22,9\n19#1:31\n19#1:33\n19#1:34\n19#1:32\n*E\n"})
public final class QuestDataIsolation {
    @NotNull
    public static final QuestDataIsolation INSTANCE = new QuestDataIsolation();

    private QuestDataIsolation() {
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public final List<String> getKeys() {
        void $this$mapNotNullTo$iv$iv;
        Collection<Template> collection = ChemdahAPI.INSTANCE.getQuestTemplate().values();
        Intrinsics.checkNotNullExpressionValue(collection, (String)"ChemdahAPI.questTemplate.values");
        Iterable $this$mapNotNull$iv = collection;
        boolean $i$f$mapNotNull = false;
        Iterable iterable = $this$mapNotNull$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$mapNotNullTo = false;
        void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv$iv$iv.iterator();
        while (iterator.hasNext()) {
            String it$iv$iv;
            Object element$iv$iv$iv;
            Object element$iv$iv = element$iv$iv$iv = iterator.next();
            boolean bl = false;
            Template it = (Template)element$iv$iv;
            boolean bl2 = false;
            if (it.getDataIsolation() == null) continue;
            boolean bl3 = false;
            destination$iv$iv.add(it$iv$iv);
        }
        return CollectionsKt.plus((Collection)CollectionsKt.distinct((Iterable)((List)destination$iv$iv)), (Object)"~");
    }
}

