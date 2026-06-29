/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.selector;

import ink.ptms.chemdah.api.event.InferEntityHookEvent;
import ink.ptms.chemdah.core.quest.selector.DataMatchHandler;
import ink.ptms.chemdah.core.quest.selector.InferEntity;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/core/quest/selector/InferEntityParser;", "", "()V", "parse", "Link/ptms/chemdah/core/quest/selector/InferEntity$Entity;", "source", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nInferEntityParser.kt\nKotlin\n*S Kotlin\n*F\n+ 1 InferEntityParser.kt\nink/ptms/chemdah/core/quest/selector/InferEntityParser\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,18:1\n1#2:19\n*E\n"})
public class InferEntityParser {
    @NotNull
    public InferEntity.Entity parse(@NotNull String source) {
        Class<InferEntity.Entity> clazz;
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        DataMatchHandler.Matcher matcher2 = DataMatchHandler.parseMatcher$default(DataMatchHandler.INSTANCE, source, false, 2, null);
        switch (matcher2.getNamespace()) {
            case "minecraft": 
            case "mc": {
                clazz = InferEntity.Entity.class;
                break;
            }
            case "citizen": 
            case "citizens": {
                clazz = InferEntity.CitizensEntity.class;
                break;
            }
            case "mm": 
            case "mythicmob": 
            case "mythicmobs": {
                clazz = InferEntity.MythicMobsEntity.class;
                break;
            }
            default: {
                String namespace;
                InferEntityHookEvent inferEntityHookEvent;
                InferEntityHookEvent $this$parse_u24lambda_u240 = inferEntityHookEvent = new InferEntityHookEvent(namespace, InferEntity.Entity.class);
                boolean bl = false;
                $this$parse_u24lambda_u240.call();
                clazz = inferEntityHookEvent.getItemClass();
            }
        }
        Class<InferEntity.Entity> entity = clazz;
        Object[] objectArray = new Object[]{matcher2.getKey(), matcher2.getFlags(), matcher2.getDataMatch()};
        return (InferEntity.Entity)Reflex.Companion.invokeConstructor(entity, objectArray);
    }
}

