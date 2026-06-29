/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.adyeshach.core.entity.EntityInstance
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptContext
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.kether;

import ink.ptms.adyeshach.core.entity.EntityInstance;
import ink.ptms.chemdah.module.kether.ActionWizard;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\f\u0012\b\b\u0001\u0012\u0004\u0018\u00010\u00010\u0004H\u0007J\u0014\u0010\u0005\u001a\f\u0012\u0006\u0012\u0004\u0018\u00010\u0007\u0018\u00010\u0006*\u00020\b\u00a8\u0006\t"}, d2={"Link/ptms/chemdah/module/kether/ActionWizard;", "", "()V", "parser", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "getEntities", "", "Link/ptms/adyeshach/core/entity/EntityInstance;", "Link/ptms/chemdah/taboolib/module/kether/ScriptContext;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nActionWizard.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ActionWizard.kt\nink/ptms/chemdah/module/kether/ActionWizard\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,69:1\n1549#2:70\n1620#2,3:71\n*S KotlinDebug\n*F\n+ 1 ActionWizard.kt\nink/ptms/chemdah/module/kether/ActionWizard\n*L\n67#1:70\n67#1:71,3\n*E\n"})
public final class ActionWizard {
    @NotNull
    public static final ActionWizard INSTANCE = new ActionWizard();

    private ActionWizard() {
    }

    @KetherParser(value={"wizard"}, shared=true)
    @NotNull
    public final ScriptActionParser<? extends Object> parser() {
        return KetherHelperKt.scriptParser((Function1)parser.1.INSTANCE);
    }

    /*
     * WARNING - void declaration
     */
    @Nullable
    public final List<EntityInstance> getEntities(@NotNull ScriptContext $this$getEntities) {
        List list2;
        Intrinsics.checkNotNullParameter((Object)$this$getEntities, (String)"<this>");
        List list3 = (List)ScriptContext.get$default((ScriptContext)$this$getEntities, (String)"@entities", null, (int)2, null);
        if (list3 != null) {
            void $this$mapTo$iv$iv;
            Iterable $this$map$iv = list3;
            boolean $i$f$map = false;
            Iterable iterable = $this$map$iv;
            Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
            boolean $i$f$mapTo = false;
            Iterator iterator = $this$mapTo$iv$iv.iterator();
            while (iterator.hasNext()) {
                void it;
                Object item$iv$iv;
                Object t = item$iv$iv = iterator.next();
                Collection collection = destination$iv$iv;
                boolean bl = false;
                collection.add(it instanceof EntityInstance ? (EntityInstance)it : null);
            }
            list2 = (List)destination$iv$iv;
        } else {
            list2 = null;
        }
        return list2;
    }
}

