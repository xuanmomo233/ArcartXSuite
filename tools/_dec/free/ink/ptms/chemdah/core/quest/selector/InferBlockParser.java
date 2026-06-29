/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest.selector;

import ink.ptms.chemdah.api.event.InferBlockHookEvent;
import ink.ptms.chemdah.core.quest.selector.DataMatchHandler;
import ink.ptms.chemdah.core.quest.selector.InferBlock;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/core/quest/selector/InferBlockParser;", "", "()V", "parse", "Link/ptms/chemdah/core/quest/selector/InferBlock$Block;", "source", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nInferBlockParser.kt\nKotlin\n*S Kotlin\n*F\n+ 1 InferBlockParser.kt\nink/ptms/chemdah/core/quest/selector/InferBlockParser\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,18:1\n1#2:19\n*E\n"})
public class InferBlockParser {
    @NotNull
    public InferBlock.Block parse(@NotNull String source) {
        Class<InferBlock.Block> clazz;
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        DataMatchHandler.Matcher matcher2 = DataMatchHandler.INSTANCE.parseMatcher(source, MinecraftVersion.INSTANCE.getMajorLegacy() >= 11300);
        Object[] objectArray = matcher2.getNamespace();
        if (Intrinsics.areEqual((Object)objectArray, (Object)"mc") ? true : Intrinsics.areEqual((Object)objectArray, (Object)"minecraft")) {
            clazz = InferBlock.Block.class;
        } else {
            InferBlockHookEvent inferBlockHookEvent;
            InferBlockHookEvent $this$parse_u24lambda_u240 = inferBlockHookEvent = new InferBlockHookEvent(matcher2.getNamespace(), InferBlock.Block.class);
            boolean bl = false;
            $this$parse_u24lambda_u240.call();
            clazz = inferBlockHookEvent.getBlockClass();
        }
        Class<InferBlock.Block> block = clazz;
        objectArray = new Object[]{matcher2.getKey(), matcher2.getFlags(), matcher2.getDataMatch()};
        return (InferBlock.Block)Reflex.Companion.invokeConstructor(block, objectArray);
    }
}

