/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest.selector;

import ink.ptms.chemdah.core.bukkit.NMS;
import ink.ptms.chemdah.core.quest.selector.DataMatch;
import ink.ptms.chemdah.core.quest.selector.DataMatchHandler;
import ink.ptms.chemdah.core.quest.selector.Flags;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\u0018\u0000 \u000f2\u00020\u0001:\u0002\u000e\u000fB\u0013\u0012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\u0002\u0010\u0005J\u000e\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000bJ\b\u0010\f\u001a\u00020\rH\u0016R\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/quest/selector/InferBlock;", "", "mats", "", "Link/ptms/chemdah/core/quest/selector/InferBlock$Block;", "(Ljava/util/List;)V", "getMats", "()Ljava/util/List;", "isBlock", "", "block", "Lorg/bukkit/block/Block;", "toString", "", "Block", "Companion", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nInferBlock.kt\nKotlin\n*S Kotlin\n*F\n+ 1 InferBlock.kt\nink/ptms/chemdah/core/quest/selector/InferBlock\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,57:1\n1747#2,3:58\n*S KotlinDebug\n*F\n+ 1 InferBlock.kt\nink/ptms/chemdah/core/quest/selector/InferBlock\n*L\n25#1:58,3\n*E\n"})
public final class InferBlock {
    @NotNull
    public static final Companion Companion = new Companion(null);
    @NotNull
    private final List<Block> mats;

    public InferBlock(@NotNull List<? extends Block> mats) {
        Intrinsics.checkNotNullParameter(mats, (String)"mats");
        this.mats = mats;
    }

    @NotNull
    public final List<Block> getMats() {
        return this.mats;
    }

    public final boolean isBlock(@NotNull org.bukkit.block.Block block) {
        boolean bl;
        block3: {
            Intrinsics.checkNotNullParameter((Object)block, (String)"block");
            String string = block.getType().name().toLowerCase(Locale.ROOT);
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toLowerCase(Locale.ROOT)");
            String type = string;
            Map<String, Object> data2 = NMS.Companion.getINSTANCE().getBlocKData(block);
            Iterable $this$any$iv = this.mats;
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    Block mat = (Block)element$iv;
                    boolean bl2 = false;
                    if (!(mat.matchFlags(type) && mat.matchBlockData(data2))) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    @NotNull
    public String toString() {
        return "InferBlock(mats=" + this.mats + ')';
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00008\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010$\n\u0002\b\u0003\b\u0016\u0018\u00002\u00020\u0001B)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\b0\u0005\u00a2\u0006\u0002\u0010\tJ\u000e\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u0017J\u001a\u0010\u0018\u001a\u00020\u00152\u0012\u0010\u0019\u001a\u000e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u00010\u001aJ\u000e\u0010\u001b\u001a\u00020\u00152\u0006\u0010\u001c\u001a\u00020\u0003R \u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\b0\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\rR \u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000e\u0010\u000b\"\u0004\b\u000f\u0010\rR\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\u0011\"\u0004\b\u0012\u0010\u0013\u00a8\u0006\u001d"}, d2={"Link/ptms/chemdah/core/quest/selector/InferBlock$Block;", "", "material", "", "flags", "", "Link/ptms/chemdah/core/quest/selector/Flags;", "data", "Link/ptms/chemdah/core/quest/selector/DataMatch;", "(Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V", "getData", "()Ljava/util/List;", "setData", "(Ljava/util/List;)V", "getFlags", "setFlags", "getMaterial", "()Ljava/lang/String;", "setMaterial", "(Ljava/lang/String;)V", "match", "", "block", "Lorg/bukkit/block/Block;", "matchBlockData", "map", "", "matchFlags", "type", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nInferBlock.kt\nKotlin\n*S Kotlin\n*F\n+ 1 InferBlock.kt\nink/ptms/chemdah/core/quest/selector/InferBlock$Block\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,57:1\n1747#2,3:58\n1726#2,3:61\n*S KotlinDebug\n*F\n+ 1 InferBlock.kt\nink/ptms/chemdah/core/quest/selector/InferBlock$Block\n*L\n39#1:58,3\n43#1:61,3\n*E\n"})
    public static class Block {
        @NotNull
        private String material;
        @NotNull
        private List<? extends Flags> flags;
        @NotNull
        private List<DataMatch> data;

        public Block(@NotNull String material, @NotNull List<? extends Flags> flags, @NotNull List<DataMatch> data2) {
            Intrinsics.checkNotNullParameter((Object)material, (String)"material");
            Intrinsics.checkNotNullParameter(flags, (String)"flags");
            Intrinsics.checkNotNullParameter(data2, (String)"data");
            this.material = material;
            this.flags = flags;
            this.data = data2;
        }

        @NotNull
        public final String getMaterial() {
            return this.material;
        }

        public final void setMaterial(@NotNull String string) {
            Intrinsics.checkNotNullParameter((Object)string, (String)"<set-?>");
            this.material = string;
        }

        @NotNull
        public final List<Flags> getFlags() {
            return this.flags;
        }

        public final void setFlags(@NotNull List<? extends Flags> list2) {
            Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
            this.flags = list2;
        }

        @NotNull
        public final List<DataMatch> getData() {
            return this.data;
        }

        public final void setData(@NotNull List<DataMatch> list2) {
            Intrinsics.checkNotNullParameter(list2, (String)"<set-?>");
            this.data = list2;
        }

        public final boolean match(@NotNull org.bukkit.block.Block block) {
            Intrinsics.checkNotNullParameter((Object)block, (String)"block");
            return new InferBlock(CollectionsKt.listOf((Object)this)).isBlock(block);
        }

        public final boolean matchFlags(@NotNull String type) {
            boolean bl;
            block3: {
                Intrinsics.checkNotNullParameter((Object)type, (String)"type");
                Iterable $this$any$iv = this.flags;
                boolean $i$f$any = false;
                if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                    bl = false;
                } else {
                    for (Object element$iv : $this$any$iv) {
                        Flags it = (Flags)((Object)element$iv);
                        boolean bl2 = false;
                        if (!((Boolean)it.getMatch().invoke((Object)type, (Object)this.material)).booleanValue()) continue;
                        bl = true;
                        break block3;
                    }
                    bl = false;
                }
            }
            return bl;
        }

        public final boolean matchBlockData(@NotNull Map<String, ? extends Object> map) {
            boolean bl;
            block4: {
                Intrinsics.checkNotNullParameter(map, (String)"map");
                Iterable $this$all$iv = this.data;
                boolean $i$f$all = false;
                if ($this$all$iv instanceof Collection && ((Collection)$this$all$iv).isEmpty()) {
                    bl = true;
                } else {
                    for (Object element$iv : $this$all$iv) {
                        DataMatch it = (DataMatch)element$iv;
                        boolean bl2 = false;
                        Object object = map.get(it.getKey());
                        if (object == null) {
                            return false;
                        }
                        if (DataMatch.check$default(it, object, null, 2, null)) continue;
                        bl = false;
                        break block4;
                    }
                    bl = true;
                }
            }
            return bl;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\n\u0010\u0003\u001a\u00020\u0004*\u00020\u0005J\u0010\u0010\u0003\u001a\u00020\u0006*\b\u0012\u0004\u0012\u00020\u00050\u0007\u00a8\u0006\b"}, d2={"Link/ptms/chemdah/core/quest/selector/InferBlock$Companion;", "", "()V", "toInferBlock", "Link/ptms/chemdah/core/quest/selector/InferBlock$Block;", "", "Link/ptms/chemdah/core/quest/selector/InferBlock;", "", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nInferBlock.kt\nKotlin\n*S Kotlin\n*F\n+ 1 InferBlock.kt\nink/ptms/chemdah/core/quest/selector/InferBlock$Companion\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,57:1\n1549#2:58\n1620#2,3:59\n*S KotlinDebug\n*F\n+ 1 InferBlock.kt\nink/ptms/chemdah/core/quest/selector/InferBlock$Companion\n*L\n54#1:58\n54#1:59,3\n*E\n"})
    public static final class Companion {
        private Companion() {
        }

        @NotNull
        public final Block toInferBlock(@NotNull String $this$toInferBlock) {
            Intrinsics.checkNotNullParameter((Object)$this$toInferBlock, (String)"<this>");
            return DataMatchHandler.INSTANCE.getBlockParser().parse($this$toInferBlock);
        }

        /*
         * WARNING - void declaration
         */
        @NotNull
        public final InferBlock toInferBlock(@NotNull List<String> $this$toInferBlock) {
            void $this$mapTo$iv$iv;
            Intrinsics.checkNotNullParameter($this$toInferBlock, (String)"<this>");
            Iterable $this$map$iv = $this$toInferBlock;
            boolean $i$f$map = false;
            Iterable iterable = $this$map$iv;
            Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
            boolean $i$f$mapTo = false;
            for (Object item$iv$iv : $this$mapTo$iv$iv) {
                void it;
                String string = (String)item$iv$iv;
                Collection collection = destination$iv$iv;
                boolean bl = false;
                collection.add(Companion.toInferBlock((String)it));
            }
            List list2 = (List)destination$iv$iv;
            return new InferBlock(list2);
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}

