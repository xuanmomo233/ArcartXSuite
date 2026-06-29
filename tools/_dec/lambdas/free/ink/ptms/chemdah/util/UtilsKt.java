/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common5.Demand
 *  ink.ptms.chemdah.taboolib.common5.Quat
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex$Companion
 *  ink.ptms.chemdah.taboolib.library.xseries.XBlock
 *  ink.ptms.chemdah.taboolib.library.xseries.XMaterial
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion
 *  ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent
 *  ink.ptms.chemdah.taboolib.platform.util.ItemModifierKt
 *  kotlin.Metadata
 *  kotlin1822.Pair
 *  kotlin1822.TuplesKt
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.io.NoSuchFileException
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.Particle
 *  org.bukkit.block.Block
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.util.Vector
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.util;

import ink.ptms.chemdah.taboolib.common5.Demand;
import ink.ptms.chemdah.taboolib.common5.Quat;
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
import kotlin1822.collections.CollectionsKt;
import kotlin1822.io.NoSuchFileException;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=2, xi=48, d1={"\u0000p\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0006\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\u001a!\u0010\u0000\u001a\u0004\u0018\u0001H\u0001\"\u0004\b\u0000\u0010\u00012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u0002H\u00010\u0003\u00a2\u0006\u0002\u0010\u0004\u001a\n\u0010\u0005\u001a\u00020\u0006*\u00020\u0007\u001a\n\u0010\b\u001a\u00020\t*\u00020\t\u001a\u0012\u0010\n\u001a\u00020\u0006*\u00020\u000b2\u0006\u0010\f\u001a\u00020\r\u001a.\u0010\u000e\u001a\u000e\u0012\u0004\u0012\u0002H\u0010\u0012\u0004\u0012\u0002H\u00110\u000f\"\u0004\b\u0000\u0010\u0011\"\u0004\b\u0001\u0010\u0010*\u000e\u0012\u0004\u0012\u0002H\u0011\u0012\u0004\u0012\u0002H\u00100\u000f\u001aD\u0010\u0012\u001a\u00020\u0013*\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u00182\b\b\u0002\u0010\u0019\u001a\u00020\u001a2\b\b\u0002\u0010\u001b\u001a\u00020\u001c2\b\b\u0002\u0010\u001d\u001a\u00020\u001e2\n\b\u0002\u0010\u001f\u001a\u0004\u0018\u00010 \u001aD\u0010\u0012\u001a\u00020\u0013*\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u00162\u0006\u0010!\u001a\u00020\"2\b\b\u0002\u0010\u001b\u001a\u00020\u001c2\b\b\u0002\u0010\u0019\u001a\u00020\u001a2\b\b\u0002\u0010\u001d\u001a\u00020\u001e2\n\b\u0002\u0010\u001f\u001a\u0004\u0018\u00010 \u001a\u0012\u0010#\u001a\u00020\u0013*\u00020$2\u0006\u0010%\u001a\u00020&\u00a8\u0006'"}, d2={"safely", "T", "func", "Lkotlin1822/Function0;", "(Lkotlin1822/jvm/functions/Function0;)Ljava/lang/Object;", "callIfFailed", "", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "hidden", "Lorg/bukkit/potion/PotionEffect;", "isBlock", "Link/ptms/chemdah/taboolib/library/xseries/XMaterial;", "block", "Lorg/bukkit/block/Block;", "reversed", "Lkotlin1822/Pair;", "V", "K", "sendTo", "", "Lorg/bukkit/entity/Player;", "particle", "Lorg/bukkit/Particle;", "location", "Lorg/bukkit/Location;", "offset", "Lorg/bukkit/util/Vector;", "count", "", "speed", "", "any", "", "quat", "Link/ptms/chemdah/taboolib/common5/Quat;", "setIcon", "Lorg/bukkit/inventory/ItemStack;", "value", "", "Chemdah"})
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

    public static final void sendTo(@NotNull Player $this$sendTo, @NotNull Particle particle, @NotNull Quat quat, int count2, @NotNull Vector offset, double speed, @Nullable Object any) {
        Intrinsics.checkNotNullParameter((Object)$this$sendTo, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)particle, (String)"particle");
        Intrinsics.checkNotNullParameter((Object)quat, (String)"quat");
        Intrinsics.checkNotNullParameter((Object)offset, (String)"offset");
        $this$sendTo.spawnParticle(particle, new Location($this$sendTo.getWorld(), quat.x(), quat.y(), quat.z()), count2, offset.getX(), offset.getY(), offset.getZ(), speed, any);
    }

    public static /* synthetic */ void sendTo$default(Player player2, Particle particle, Quat quat, int n, Vector vector, double d, Object object, int n2, Object object2) {
        if ((n2 & 4) != 0) {
            n = 1;
        }
        if ((n2 & 8) != 0) {
            vector = new Vector();
        }
        if ((n2 & 0x10) != 0) {
            d = 0.0;
        }
        if ((n2 & 0x20) != 0) {
            object = null;
        }
        UtilsKt.sendTo(player2, particle, quat, n, vector, d, object);
    }

    public static final void sendTo(@NotNull Player $this$sendTo, @NotNull Particle particle, @NotNull Location location, @NotNull Vector offset, int count2, double speed, @Nullable Object any) {
        Intrinsics.checkNotNullParameter((Object)$this$sendTo, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)particle, (String)"particle");
        Intrinsics.checkNotNullParameter((Object)location, (String)"location");
        Intrinsics.checkNotNullParameter((Object)offset, (String)"offset");
        $this$sendTo.spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count2, offset.getX(), offset.getY(), offset.getZ(), speed, any);
    }

    public static /* synthetic */ void sendTo$default(Player player2, Particle particle, Location location, Vector vector, int n, double d, Object object, int n2, Object object2) {
        if ((n2 & 4) != 0) {
            vector = new Vector();
        }
        if ((n2 & 8) != 0) {
            n = 1;
        }
        if ((n2 & 0x10) != 0) {
            d = 0.0;
        }
        if ((n2 & 0x20) != 0) {
            object = null;
        }
        UtilsKt.sendTo(player2, particle, location, vector, n, d, object);
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

