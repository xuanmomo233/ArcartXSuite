/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.util;

import ink.ptms.chemdah.taboolib.common.platform.ProxyParticle;
import ink.ptms.chemdah.taboolib.common5.Demand;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import ink.ptms.chemdah.taboolib.library.xseries.XBlock;
import ink.ptms.chemdah.taboolib.library.xseries.XMaterial;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion;
import ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent;
import ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt;
import ink.ptms.chemdah.util.NumberKt;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.collections.ArraysKt;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.io.NoSuchFileException;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000L\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\u001a!\u0010\u0000\u001a\u0004\u0018\u0001H\u0001\"\u0004\b\u0000\u0010\u00012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u0002H\u00010\u0003\u00a2\u0006\u0002\u0010\u0004\u001a\n\u0010\u0005\u001a\u00020\u0006*\u00020\u0007\u001a\n\u0010\b\u001a\u00020\t*\u00020\t\u001a\u0012\u0010\n\u001a\u00020\u0006*\u00020\u000b2\u0006\u0010\f\u001a\u00020\r\u001a.\u0010\u000e\u001a\u000e\u0012\u0004\u0012\u0002H\u0010\u0012\u0004\u0012\u0002H\u00110\u000f\"\u0004\b\u0000\u0010\u0011\"\u0004\b\u0001\u0010\u0010*\u000e\u0012\u0004\u0012\u0002H\u0011\u0012\u0004\u0012\u0002H\u00100\u000f\u001a\u0012\u0010\u0012\u001a\u00020\u0013*\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u0016\u001a\n\u0010\u0017\u001a\u00020\u0018*\u00020\u0019\u001a\f\u0010\u001a\u001a\u0004\u0018\u00010\u0018*\u00020\u0019\u00a8\u0006\u001b"}, d2={"safely", "T", "func", "Lkotlin1822/Function0;", "(Lkotlin1822/jvm/functions/Function0;)Ljava/lang/Object;", "callIfFailed", "", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "hidden", "Lorg/bukkit/potion/PotionEffect;", "isBlock", "Link/ptms/chemdah/taboolib/library/xseries/XMaterial;", "block", "Lorg/bukkit/block/Block;", "reversed", "Lkotlin1822/Pair;", "V", "K", "setIcon", "", "Lorg/bukkit/inventory/ItemStack;", "value", "", "toBukkit", "Lorg/bukkit/Particle;", "Link/ptms/chemdah/taboolib/common/platform/ProxyParticle;", "toBukkitOrNull", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nUtils.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Utils.kt\nink/ptms/chemdah/util/UtilsKt\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,95:1\n1#2:96\n*E\n"})
public final class UtilsKt {
    public static final boolean isBlock(@NotNull XMaterial $this$isBlock, @NotNull Block block) {
        Intrinsics.checkNotNullParameter((Object)$this$isBlock, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)block, (String)"block");
        return XBlock.isSimilar((Block)block, (XMaterial)$this$isBlock) && (MinecraftVersion.INSTANCE.getMajorLegacy() >= 11300 || block.getData() == $this$isBlock.getData());
    }

    public static final void setIcon(@NotNull ItemStack $this$setIcon, @NotNull String value2) {
        block1: {
            String it;
            Intrinsics.checkNotNullParameter((Object)$this$setIcon, (String)"<this>");
            Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
            Demand demand = Demand.Companion.toDemand(value2);
            Material material = XMaterial.matchXMaterial((String)demand.getNamespace()).orElse(XMaterial.STONE).parseMaterial();
            Intrinsics.checkNotNull((Object)material);
            $this$setIcon.setType(material);
            Object[] objectArray = new String[]{"d", "data"};
            String string = Demand.get$default((Demand)demand, (List)CollectionsKt.listOf((Object[])objectArray), null, (int)2, null);
            if (string != null) {
                it = string;
                boolean bl = false;
                $this$setIcon.setDurability((short)NumberKt.asInt$default(it, 0, 1, null));
            }
            objectArray = new String[]{"c", "custom_data_model"};
            String string2 = Demand.get$default((Demand)demand, (List)CollectionsKt.listOf((Object[])objectArray), null, (int)2, null);
            if (string2 == null) break block1;
            it = string2;
            boolean bl = false;
            ItemModifierKt.modifyMeta((ItemStack)$this$setIcon, (Function1)((Function1)new Function1<ItemMeta, Unit>(it){
                final /* synthetic */ String $it;
                {
                    this.$it = $it;
                    super(1);
                }

                public final void invoke(@NotNull ItemMeta $this$modifyMeta) {
                    Intrinsics.checkNotNullParameter((Object)$this$modifyMeta, (String)"$this$modifyMeta");
                    $this$modifyMeta.setCustomModelData(Integer.valueOf(NumberKt.asInt$default(this.$it, 0, 1, null)));
                }
            }));
        }
    }

    @NotNull
    public static final PotionEffect hidden(@NotNull PotionEffect $this$hidden) {
        Intrinsics.checkNotNullParameter((Object)$this$hidden, (String)"<this>");
        if (MinecraftVersion.INSTANCE.getMajorLegacy() >= 11300) {
            try {
                Reflex.Companion.setProperty$default((Reflex.Companion)Reflex.Companion, (Object)$this$hidden, (String)"icon", (Object)false, (boolean)false, (boolean)false, (boolean)false, null, (int)60, null);
                Reflex.Companion.setProperty$default((Reflex.Companion)Reflex.Companion, (Object)$this$hidden, (String)"particles", (Object)false, (boolean)false, (boolean)false, (boolean)false, null, (int)60, null);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        return $this$hidden;
    }

    public static final boolean callIfFailed(@NotNull BukkitProxyEvent $this$callIfFailed) {
        Intrinsics.checkNotNullParameter((Object)$this$callIfFailed, (String)"<this>");
        return !$this$callIfFailed.call();
    }

    @NotNull
    public static final Particle toBukkit(@NotNull ProxyParticle $this$toBukkit) {
        Intrinsics.checkNotNullParameter((Object)$this$toBukkit, (String)"<this>");
        Particle particle = UtilsKt.toBukkitOrNull($this$toBukkit);
        if (particle == null) {
            throw new IllegalStateException(("Unsupported: " + $this$toBukkit.name()).toString());
        }
        return particle;
    }

    @Nullable
    public static final Particle toBukkitOrNull(@NotNull ProxyParticle $this$toBukkitOrNull) {
        Particle particle;
        block1: {
            Intrinsics.checkNotNullParameter((Object)$this$toBukkitOrNull, (String)"<this>");
            Particle[] particleArray = Particle.values();
            int n = particleArray.length;
            for (int i = 0; i < n; ++i) {
                Particle particle2;
                Particle it = particle2 = particleArray[i];
                boolean bl = false;
                if (!(Intrinsics.areEqual((Object)it.name(), (Object)$this$toBukkitOrNull.name()) || ArraysKt.contains((Object[])$this$toBukkitOrNull.getAliases(), (Object)it.name()))) continue;
                particle = particle2;
                break block1;
            }
            particle = null;
        }
        return particle;
    }

    @Nullable
    public static final <T> T safely(@NotNull Function0<? extends T> func) {
        Intrinsics.checkNotNullParameter(func, (String)"func");
        try {
            return (T)func.invoke();
        }
        catch (NoSuchFieldError noSuchFieldError) {
        }
        catch (NoSuchFileException noSuchFileException) {
        }
        catch (NoSuchMethodError noSuchMethodError) {
        }
        catch (NoSuchMethodException noSuchMethodException) {
        }
        catch (NoClassDefFoundError noClassDefFoundError) {
            // empty catch block
        }
        return null;
    }

    @NotNull
    public static final <K, V> Pair<V, K> reversed(@NotNull Pair<? extends K, ? extends V> $this$reversed) {
        Intrinsics.checkNotNullParameter($this$reversed, (String)"<this>");
        return TuplesKt.to((Object)$this$reversed.getSecond(), (Object)$this$reversed.getFirst());
    }
}

