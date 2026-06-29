/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.util.CollectionKt
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.Regex
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.meta;

import ink.ptms.chemdah.core.quest.Id;
import ink.ptms.chemdah.core.quest.MetaInfo;
import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.ParamInfo;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.meta.Meta;
import ink.ptms.chemdah.taboolib.common.util.CollectionKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.Regex;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Id(id="type")
@Option(type=Option.Type.ANY)
@MetaInfo(name="\u4efb\u52a1\u7c7b\u578b\u7ec4\u4ef6", description={"\u7528\u4e8e\u7ed9\u4efb\u52a1\u5206\u7c7b\u6807\u8bb0", "\u652f\u6301\u591a\u79cd\u8f93\u5165\u683c\u5f0f", "\u4e3b\u8981\u7528\u4e8e\u4efb\u52a1\u7b5b\u9009\u548c\u5206\u7ec4"}, alias={"\u7c7b\u578b", "\u5206\u7c7b", "\u6807\u7b7e"}, params={@ParamInfo(name="type", type="any", required=false, description="\u4efb\u52a1\u7c7b\u578b\u6807\u8bb0\uff0c\u652f\u6301\uff1a\u5355\u4e2a\u5b57\u7b26\u4e32 | \u9017\u53f7/\u5206\u53f7\u5206\u9694\u7684\u5b57\u7b26\u4e32 | \u5b57\u7b26\u4e32\u5217\u8868")})
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0006\b\u0007\u0018\u0000 \u000e2\n\u0012\u0006\u0012\u0004\u0018\u00010\u00020\u0001:\u0001\u000eB\u0017\u0012\b\u0010\u0003\u001a\u0004\u0018\u00010\u0002\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006R \u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\r\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/core/quest/meta/MetaType;", "Link/ptms/chemdah/core/quest/meta/Meta;", "", "source", "questContainer", "Link/ptms/chemdah/core/quest/QuestContainer;", "(Ljava/lang/Object;Link/ptms/chemdah/core/quest/QuestContainer;)V", "type", "", "", "getType", "()Ljava/util/List;", "setType", "(Ljava/util/List;)V", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nMetaType.kt\nKotlin\n*S Kotlin\n*F\n+ 1 MetaType.kt\nink/ptms/chemdah/core/quest/meta/MetaType\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,62:1\n1360#2:63\n1446#2,5:64\n1549#2:69\n1620#2,3:70\n*S KotlinDebug\n*F\n+ 1 MetaType.kt\nink/ptms/chemdah/core/quest/meta/MetaType\n*L\n55#1:63\n55#1:64,5\n55#1:69\n55#1:70,3\n*E\n"})
public final class MetaType
extends Meta<Object> {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private List<String> type;

    /*
     * WARNING - void declaration
     */
    public MetaType(@Nullable Object source, @NotNull QuestContainer questContainer) {
        List list2;
        Intrinsics.checkNotNullParameter((Object)questContainer, (String)"questContainer");
        super(source, questContainer);
        MetaType metaType = this;
        Object object = source;
        if (object != null && (object = CollectionKt.asList((Object)object)) != null) {
            void $this$mapTo$iv$iv;
            void $this$map$iv;
            void $this$flatMapTo$iv$iv;
            void $this$flatMap$iv;
            Iterable iterable = (Iterable)object;
            MetaType metaType2 = metaType;
            boolean $i$f$flatMap22 = false;
            void var5_7 = $this$flatMap$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$flatMapTo = false;
            for (Object element$iv$iv : $this$flatMapTo$iv$iv) {
                String it = (String)element$iv$iv;
                boolean bl = false;
                CharSequence charSequence = it;
                Regex regex = new Regex("[,;]");
                int n = 0;
                Iterable list$iv$iv = regex.split(charSequence, n);
                CollectionsKt.addAll((Collection)destination$iv$iv, (Iterable)list$iv$iv);
            }
            Iterable $i$f$flatMap22 = (List)destination$iv$iv;
            boolean $i$f$map = false;
            destination$iv$iv = $this$map$iv;
            Collection destination$iv$iv2 = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
            boolean $i$f$mapTo = false;
            for (Object item$iv$iv : $this$mapTo$iv$iv) {
                void it;
                String bl = (String)item$iv$iv;
                Collection collection = destination$iv$iv2;
                boolean bl2 = false;
                collection.add(((Object)StringsKt.trim((CharSequence)((CharSequence)it))).toString());
            }
            list2 = (List)destination$iv$iv2;
            metaType = metaType2;
        } else {
            list2 = CollectionsKt.emptyList();
        }
        metaType.type = list2;
    }

    @NotNull
    public final List<String> getType() {
        return this.type;
    }

    public final void setType(@NotNull List<String> list2) {
        Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
        this.type = list2;
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004*\u00020\u0006\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/core/quest/meta/MetaType$Companion;", "", "()V", "type", "", "", "Link/ptms/chemdah/core/quest/Template;", "Chemdah"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final List<String> type(@NotNull Template $this$type) {
            Intrinsics.checkNotNullParameter((Object)$this$type, (String)"<this>");
            Object object = (MetaType)$this$type.meta("type");
            if (object == null || (object = ((MetaType)object).getType()) == null) {
                object = CollectionsKt.emptyList();
            }
            return object;
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

