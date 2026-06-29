/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.util.OptionalKt
 *  ink.ptms.chemdah.taboolib.common.util.SyncExecutorKt
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.common5.Demand
 *  ink.ptms.chemdah.taboolib.common5.Quat
 *  ink.ptms.chemdah.taboolib.library.xseries.XItemStackKt
 *  ink.ptms.chemdah.taboolib.library.xseries.particles.XParticle
 *  ink.ptms.chemdah.taboolib.module.chat.UtilKt
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Color
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.Particle
 *  org.bukkit.Particle$DustOptions
 *  org.bukkit.Particle$DustTransition
 *  org.bukkit.World
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.material.MaterialData
 *  org.bukkit.util.Vector
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.util;

import ink.ptms.chemdah.taboolib.common.util.OptionalKt;
import ink.ptms.chemdah.taboolib.common.util.SyncExecutorKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.common5.Demand;
import ink.ptms.chemdah.taboolib.common5.Quat;
import ink.ptms.chemdah.taboolib.library.xseries.XItemStackKt;
import ink.ptms.chemdah.taboolib.library.xseries.particles.XParticle;
import ink.ptms.chemdah.taboolib.module.chat.UtilKt;
import ink.ptms.chemdah.util.UtilsKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000n\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0007\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0006\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\r\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0016\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001a\u00105\u001a\u0002062\u0006\u00107\u001a\u0002082\n\b\u0002\u00109\u001a\u0004\u0018\u00010:J\u0018\u0010;\u001a\u0002062\u0006\u0010<\u001a\u00020=2\u0006\u00109\u001a\u00020:H\u0002J\b\u0010>\u001a\u00020\u0003H\u0016R\u0013\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\r\u001a\u00020\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0011\u001a\u00020\u0012\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0015\u001a\u00020\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u0011\u0010\u0019\u001a\u00020\u0012\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u0014R\u0011\u0010\u001b\u001a\u00020\u001c\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001eR\u0011\u0010\u001f\u001a\u00020 \u00a2\u0006\b\n\u0000\u001a\u0004\b!\u0010\"R\u0011\u0010#\u001a\u00020 \u00a2\u0006\b\n\u0000\u001a\u0004\b$\u0010\"R\u0011\u0010%\u001a\u00020 \u00a2\u0006\b\n\u0000\u001a\u0004\b&\u0010\"R\u0011\u0010'\u001a\u00020(\u00a2\u0006\b\n\u0000\u001a\u0004\b)\u0010*R\u0011\u0010+\u001a\u00020 \u00a2\u0006\b\n\u0000\u001a\u0004\b,\u0010\"R\u0011\u0010-\u001a\u00020 \u00a2\u0006\b\n\u0000\u001a\u0004\b.\u0010\"R\u0011\u0010/\u001a\u00020 \u00a2\u0006\b\n\u0000\u001a\u0004\b0\u0010\"R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b1\u00102R\u0011\u00103\u001a\u00020 \u00a2\u0006\b\n\u0000\u001a\u0004\b4\u0010\"\u00a8\u0006?"}, d2={"Link/ptms/chemdah/util/Effect;", "", "source", "", "(Ljava/lang/String;)V", "blockData", "Lorg/bukkit/block/data/BlockData;", "getBlockData", "()Lorg/bukkit/block/data/BlockData;", "count", "", "getCount", "()I", "demand", "Link/ptms/chemdah/taboolib/common5/Demand;", "getDemand", "()Link/ptms/chemdah/taboolib/common5/Demand;", "dustColor", "Lorg/bukkit/Color;", "getDustColor", "()Lorg/bukkit/Color;", "dustSize", "", "getDustSize", "()F", "dustToColor", "getDustToColor", "materialData", "Lorg/bukkit/material/MaterialData;", "getMaterialData", "()Lorg/bukkit/material/MaterialData;", "offsetX", "", "getOffsetX", "()D", "offsetY", "getOffsetY", "offsetZ", "getOffsetZ", "particle", "Lorg/bukkit/Particle;", "getParticle", "()Lorg/bukkit/Particle;", "posX", "getPosX", "posY", "getPosY", "posZ", "getPosZ", "getSource", "()Ljava/lang/String;", "speed", "getSpeed", "run", "", "location", "Lorg/bukkit/Location;", "player", "Lorg/bukkit/entity/Player;", "sendParticle", "quat", "Link/ptms/chemdah/taboolib/common5/Quat;", "toString", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nEffect.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Effect.kt\nink/ptms/chemdah/util/Effect\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,74:1\n800#2,11:75\n1855#2,2:86\n*S KotlinDebug\n*F\n+ 1 Effect.kt\nink/ptms/chemdah/util/Effect\n*L\n67#1:75,11\n67#1:86,2\n*E\n"})
public class Effect {
    @NotNull
    private final String source;
    @NotNull
    private final Demand demand;
    @NotNull
    private final Particle particle;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final double posX;
    private final double posY;
    private final double posZ;
    private final double speed;
    private final int count;
    @Nullable
    private final BlockData blockData;
    @NotNull
    private final MaterialData materialData;
    @NotNull
    private final Color dustColor;
    @NotNull
    private final Color dustToColor;
    private final float dustSize;

    public Effect(@NotNull String source) {
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        this.source = source;
        this.demand = new Demand(this.source);
        Optional optional = XParticle.of((String)this.demand.getNamespace());
        Intrinsics.checkNotNullExpressionValue((Object)optional, (String)"of(demand.namespace)");
        XParticle xParticle = (XParticle)OptionalKt.orNull((Optional)optional);
        if (xParticle == null || (xParticle = xParticle.get()) == null) {
            Particle particle = XParticle.FLAME.get();
            xParticle = particle;
            Intrinsics.checkNotNull((Object)particle);
        }
        Intrinsics.checkNotNullExpressionValue((Object)xParticle, (String)"XParticle.of(demand.name\u2026: XParticle.FLAME.get()!!");
        this.particle = xParticle;
        String string = this.demand.get(0, "0");
        Intrinsics.checkNotNull((Object)string);
        this.offsetX = Coerce.toDouble((Object)string);
        String string2 = this.demand.get(1, "0");
        Intrinsics.checkNotNull((Object)string2);
        this.offsetY = Coerce.toDouble((Object)string2);
        String string3 = this.demand.get(2, "0");
        Intrinsics.checkNotNull((Object)string3);
        this.offsetZ = Coerce.toDouble((Object)string3);
        this.posX = Coerce.toDouble((Object)Demand.get$default((Demand)this.demand, (String)"posX", null, (int)2, null));
        this.posY = Coerce.toDouble((Object)Demand.get$default((Demand)this.demand, (String)"posY", null, (int)2, null));
        this.posZ = Coerce.toDouble((Object)Demand.get$default((Demand)this.demand, (String)"posZ", null, (int)2, null));
        Object[] objectArray = new String[]{"speed", "s"};
        String string4 = this.demand.get(CollectionsKt.listOf((Object[])objectArray), "0");
        Intrinsics.checkNotNull((Object)string4);
        this.speed = Coerce.toDouble((Object)string4);
        objectArray = new String[]{"count", "c"};
        String string5 = this.demand.get(CollectionsKt.listOf((Object[])objectArray), "1");
        Intrinsics.checkNotNull((Object)string5);
        this.count = Coerce.toInteger((Object)string5);
        String string6 = Demand.get$default((Demand)this.demand, (String)"block", null, (int)2, null);
        this.blockData = string6 != null && (string6 = XItemStackKt.parseToMaterial((String)string6)) != null ? string6.createBlockData() : null;
        String string7 = Demand.get$default((Demand)this.demand, (String)"item", null, (int)2, null);
        this.materialData = new MaterialData((Material)(string7 != null ? XItemStackKt.parseToMaterial((String)string7) : null));
        String string8 = this.demand.get("color", "#FFFFFF");
        Intrinsics.checkNotNull((Object)string8);
        Color color = Color.fromRGB((int)UtilKt.parseToHexColor((String)StringsKt.replace$default((String)string8, (char)'~', (char)'-', (boolean)false, (int)4, null)));
        Intrinsics.checkNotNullExpressionValue((Object)color, (String)"fromRGB(demand.get(\"colo\u2026, '-').parseToHexColor())");
        this.dustColor = color;
        String string9 = this.demand.get("color-to", "#FFFFFF");
        Intrinsics.checkNotNull((Object)string9);
        Color color2 = Color.fromRGB((int)UtilKt.parseToHexColor((String)StringsKt.replace$default((String)string9, (char)'~', (char)'-', (boolean)false, (int)4, null)));
        Intrinsics.checkNotNullExpressionValue((Object)color2, (String)"fromRGB(demand.get(\"colo\u2026, '-').parseToHexColor())");
        this.dustToColor = color2;
        String string10 = this.demand.get("size", "1.0");
        Intrinsics.checkNotNull((Object)string10);
        this.dustSize = Coerce.toFloat((Object)string10);
    }

    @NotNull
    public final String getSource() {
        return this.source;
    }

    @NotNull
    public final Demand getDemand() {
        return this.demand;
    }

    @NotNull
    public final Particle getParticle() {
        return this.particle;
    }

    public final double getOffsetX() {
        return this.offsetX;
    }

    public final double getOffsetY() {
        return this.offsetY;
    }

    public final double getOffsetZ() {
        return this.offsetZ;
    }

    public final double getPosX() {
        return this.posX;
    }

    public final double getPosY() {
        return this.posY;
    }

    public final double getPosZ() {
        return this.posZ;
    }

    public final double getSpeed() {
        return this.speed;
    }

    public final int getCount() {
        return this.count;
    }

    @Nullable
    public final BlockData getBlockData() {
        return this.blockData;
    }

    @NotNull
    public final MaterialData getMaterialData() {
        return this.materialData;
    }

    @NotNull
    public final Color getDustColor() {
        return this.dustColor;
    }

    @NotNull
    public final Color getDustToColor() {
        return this.dustToColor;
    }

    public final float getDustSize() {
        return this.dustSize;
    }

    private final void sendParticle(Quat quat, Player player2) {
        Class clazz = this.particle.getDataType();
        UtilsKt.sendTo(player2, this.particle, quat, this.count, new Vector(this.offsetX, this.offsetY, this.offsetZ), this.speed, Intrinsics.areEqual((Object)clazz, Particle.DustOptions.class) ? new Particle.DustOptions(this.dustColor, this.dustSize) : (Intrinsics.areEqual((Object)clazz, Particle.DustTransition.class) ? new Particle.DustTransition(this.dustColor, this.dustToColor, this.dustSize) : (Intrinsics.areEqual((Object)clazz, BlockData.class) ? this.blockData : (Intrinsics.areEqual((Object)clazz, ItemStack.class) ? this.materialData.toItemStack(1) : (Intrinsics.areEqual((Object)clazz, MaterialData.class) ? this.materialData : null)))));
    }

    /*
     * WARNING - void declaration
     */
    public final void run(@NotNull Location location, @Nullable Player player2) {
        Intrinsics.checkNotNullParameter((Object)location, (String)"location");
        Location location2 = location.clone().add(this.posX, this.posY, this.posZ);
        Intrinsics.checkNotNullExpressionValue((Object)location2, (String)"location.clone().add(posX, posY, posZ)");
        Location pos = location2;
        Quat quat = null;
        quat = Quat.at((double)pos.getX(), (double)pos.getY(), (double)pos.getZ());
        quat = quat.rotate2D((double)location.getYaw(), location.getX(), location.getZ());
        if (player2 != null) {
            Quat quat2 = quat;
            Intrinsics.checkNotNullExpressionValue((Object)quat2, (String)"quat");
            this.sendParticle(quat2, player2);
        } else {
            void $this$forEach$iv;
            void $this$filterIsInstanceTo$iv$iv;
            Object object = SyncExecutorKt.runSync((Function0)((Function0)new Function0<Collection<Entity>>(location){
                final /* synthetic */ Location $location;
                {
                    this.$location = $location;
                    super(0);
                }

                @NotNull
                public final Collection<Entity> invoke() {
                    World world = this.$location.getWorld();
                    Intrinsics.checkNotNull((Object)world);
                    return world.getNearbyEntities(this.$location, 100.0, 100.0, 100.0);
                }
            }));
            Intrinsics.checkNotNullExpressionValue((Object)object, (String)"location: Location, play\u20260.0, 100.0)\n            }");
            Collection entities2 = (Collection)object;
            Iterable $this$filterIsInstance$iv = entities2;
            boolean $i$f$filterIsInstance = false;
            Iterable iterable = $this$filterIsInstance$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterIsInstanceTo = false;
            for (Object element$iv$iv : $this$filterIsInstanceTo$iv$iv) {
                if (!(element$iv$iv instanceof Player)) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            $this$filterIsInstance$iv = (List)destination$iv$iv;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Player it = (Player)element$iv;
                boolean bl = false;
                Quat quat3 = quat;
                Intrinsics.checkNotNullExpressionValue((Object)quat3, (String)"quat");
                this.sendParticle(quat3, it);
            }
        }
    }

    public static /* synthetic */ void run$default(Effect effect, Location location, Player player2, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: run");
        }
        if ((n & 2) != 0) {
            player2 = null;
        }
        effect.run(location, player2);
    }

    @NotNull
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Effect(source='").append(this.source).append("', demand=").append(this.demand).append(", particle=").append(this.particle).append(", offsetX=").append(this.offsetX).append(", offsetY=").append(this.offsetY).append(", offsetZ=").append(this.offsetZ).append(", posX=").append(this.posX).append(", posY=").append(this.posY).append(", posZ=").append(this.posZ).append(", speed=").append(this.speed).append(", count=").append(this.count).append(", blockData=");
        stringBuilder.append(this.blockData).append(", materialData=").append(this.materialData).append(", dustColor=").append(this.dustColor).append(", dustToColor=").append(this.dustToColor).append(", dustSize=").append(this.dustSize).append(')');
        return stringBuilder.toString();
    }
}

