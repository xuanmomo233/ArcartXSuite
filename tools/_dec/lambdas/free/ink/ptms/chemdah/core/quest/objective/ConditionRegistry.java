/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common5.CoerceExtensionsKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Location
 *  org.bukkit.block.Block
 *  org.bukkit.entity.Entity
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.util.Vector
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.quest.objective;

import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.quest.objective.ConditionPattern;
import ink.ptms.chemdah.taboolib.common5.CoerceExtensionsKt;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010%\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0006\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\t\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010\u00062\u0006\u0010\n\u001a\u00020\u0005J\u0018\u0010\u000b\u001a\f\u0012\u0006\u0012\u0004\u0018\u00010\u0001\u0018\u00010\u00062\u0006\u0010\n\u001a\u00020\u0005R!\u0010\u0003\u001a\u0012\u0012\u0004\u0012\u00020\u0005\u0012\b\u0012\u0006\u0012\u0002\b\u00030\u00060\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/core/quest/objective/ConditionRegistry;", "", "()V", "patterns", "", "", "Link/ptms/chemdah/core/quest/objective/ConditionPattern;", "getPatterns", "()Ljava/util/Map;", "getPattern", "name", "getPatternOrNull", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nConditionRegistry.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ConditionRegistry.kt\nink/ptms/chemdah/core/quest/objective/ConditionRegistry\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,38:1\n1747#2,3:39\n1747#2,3:42\n1747#2,3:45\n1747#2,3:48\n*S KotlinDebug\n*F\n+ 1 ConditionRegistry.kt\nink/ptms/chemdah/core/quest/objective/ConditionRegistry\n*L\n18#1:39,3\n26#1:42,3\n27#1:45,3\n28#1:48,3\n*E\n"})
public final class ConditionRegistry {
    @NotNull
    public static final ConditionRegistry INSTANCE = new ConditionRegistry();
    @NotNull
    private static final Map<String, ConditionPattern<?>> patterns = new LinkedHashMap();

    private ConditionRegistry() {
    }

    @NotNull
    public final Map<String, ConditionPattern<?>> getPatterns() {
        return patterns;
    }

    @NotNull
    public final ConditionPattern<Object> getPattern(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        ConditionPattern<Object> conditionPattern = patterns.get(name);
        ConditionPattern<Object> conditionPattern2 = conditionPattern instanceof ConditionPattern ? conditionPattern : null;
        if (conditionPattern2 == null) {
            throw new IllegalStateException(("Unknown condition pattern " + name).toString());
        }
        return conditionPattern2;
    }

    @Nullable
    public final ConditionPattern<Object> getPatternOrNull(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        ConditionPattern<Object> conditionPattern = patterns.get(name);
        return conditionPattern instanceof ConditionPattern ? conditionPattern : null;
    }

    private static final boolean _init_$lambda$0(Data p, Number it) {
        Intrinsics.checkNotNullParameter((Object)p, (String)"p");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return p.toConditionNumber().check(it);
    }

    private static final boolean _init_$lambda$2(Data p, String it) {
        boolean bl;
        block3: {
            Intrinsics.checkNotNullParameter((Object)p, (String)"p");
            Intrinsics.checkNotNullParameter((Object)it, (String)"it");
            Iterable $this$any$iv = p.asList();
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    String line = (String)element$iv;
                    boolean bl2 = false;
                    if (!CoerceExtensionsKt.eqic((String)line, (String)it)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final boolean _init_$lambda$3(Data p, boolean it) {
        Intrinsics.checkNotNullParameter((Object)p, (String)"p");
        return p.toBoolean() == it;
    }

    private static final boolean _init_$lambda$4(Data p, Location it) {
        Intrinsics.checkNotNullParameter((Object)p, (String)"p");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return p.toPosition().inside(it);
    }

    private static final boolean _init_$lambda$5(Data p, Vector it) {
        Intrinsics.checkNotNullParameter((Object)p, (String)"p");
        Intrinsics.checkNotNullParameter((Object)it, (String)"it");
        return p.toVector().inside(it);
    }

    private static final boolean _init_$lambda$6(Data p, ItemStack it) {
        Intrinsics.checkNotNullParameter((Object)p, (String)"p");
        ItemStack itemStack = it;
        if (itemStack == null) {
            return false;
        }
        return p.toInferItem().isItem(itemStack);
    }

    private static final boolean _init_$lambda$7(Data p, Block it) {
        Intrinsics.checkNotNullParameter((Object)p, (String)"p");
        Block block = it;
        if (block == null) {
            return false;
        }
        return p.toInferBlock().isBlock(block);
    }

    private static final boolean _init_$lambda$8(Data p, Entity it) {
        Intrinsics.checkNotNullParameter((Object)p, (String)"p");
        Entity entity = it;
        if (entity == null) {
            return false;
        }
        return p.toInferEntity().isEntity(entity);
    }

    private static final boolean _init_$lambda$10(Data p, List it) {
        boolean bl;
        block3: {
            Intrinsics.checkNotNullParameter((Object)p, (String)"p");
            Intrinsics.checkNotNullParameter((Object)it, (String)"it");
            Iterable $this$any$iv = it;
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    boolean bl2;
                    ItemStack item2 = (ItemStack)element$iv;
                    boolean bl3 = false;
                    ItemStack itemStack = item2;
                    if (!(itemStack == null ? (bl2 = false) : p.toInferItem().isItem(itemStack))) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final boolean _init_$lambda$12(Data p, List it) {
        boolean bl;
        block3: {
            Intrinsics.checkNotNullParameter((Object)p, (String)"p");
            Intrinsics.checkNotNullParameter((Object)it, (String)"it");
            Iterable $this$any$iv = it;
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    Block block = (Block)element$iv;
                    boolean bl2 = false;
                    if (!p.toInferBlock().isBlock(block)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    private static final boolean _init_$lambda$14(Data p, List it) {
        boolean bl;
        block3: {
            Intrinsics.checkNotNullParameter((Object)p, (String)"p");
            Intrinsics.checkNotNullParameter((Object)it, (String)"it");
            Iterable $this$any$iv = it;
            boolean $i$f$any = false;
            if ($this$any$iv instanceof Collection && ((Collection)$this$any$iv).isEmpty()) {
                bl = false;
            } else {
                for (Object element$iv : $this$any$iv) {
                    Entity entity = (Entity)element$iv;
                    boolean bl2 = false;
                    if (!p.toInferEntity().isEntity(entity)) continue;
                    bl = true;
                    break block3;
                }
                bl = false;
            }
        }
        return bl;
    }

    static {
        patterns.put("Number", ConditionRegistry::_init_$lambda$0);
        patterns.put("String", ConditionRegistry::_init_$lambda$2);
        patterns.put("Boolean", ConditionRegistry::_init_$lambda$3);
        patterns.put("Location", ConditionRegistry::_init_$lambda$4);
        patterns.put("Vector", ConditionRegistry::_init_$lambda$5);
        patterns.put("ItemStack", ConditionRegistry::_init_$lambda$6);
        patterns.put("Block", ConditionRegistry::_init_$lambda$7);
        patterns.put("Entity", ConditionRegistry::_init_$lambda$8);
        patterns.put("List<ItemStack>", ConditionRegistry::_init_$lambda$10);
        patterns.put("List<Block>", ConditionRegistry::_init_$lambda$12);
        patterns.put("List<Entity>", ConditionRegistry::_init_$lambda$14);
    }
}

