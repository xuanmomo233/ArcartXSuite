/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.base.Enums
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyParticle
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyParticle$BlockData
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyParticle$Data
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyParticle$DustData
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyParticle$ItemData
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.common.util.Location
 *  ink.ptms.chemdah.taboolib.common.util.Vector
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.common5.Demand
 *  ink.ptms.chemdah.taboolib.common5.Quat
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.DefaultConstructorMarker
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.util;

import com.google.common.base.Enums;
import ink.ptms.chemdah.taboolib.common.platform.ProxyParticle;
import ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.util.Location;
import ink.ptms.chemdah.taboolib.common.util.Vector;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.common5.Demand;
import ink.ptms.chemdah.taboolib.common5.Quat;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000N\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0006\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\r\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0016\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010)\u001a\u00020*2\u0006\u0010+\u001a\u00020,J\u0016\u0010)\u001a\u00020*2\u0006\u0010+\u001a\u00020,2\u0006\u0010-\u001a\u00020.J\b\u0010/\u001a\u00020\u0003H\u0016R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u001c\u0010\t\u001a\u0004\u0018\u00010\nX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\f\"\u0004\b\r\u0010\u000eR\u0011\u0010\u000f\u001a\u00020\u0010\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0013\u001a\u00020\u0014\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u0011\u0010\u0017\u001a\u00020\u0014\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0016R\u0011\u0010\u0019\u001a\u00020\u0014\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u0016R\u0011\u0010\u001b\u001a\u00020\u001c\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001eR\u0011\u0010\u001f\u001a\u00020\u0014\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010\u0016R\u0011\u0010!\u001a\u00020\u0014\u00a2\u0006\b\n\u0000\u001a\u0004\b\"\u0010\u0016R\u0011\u0010#\u001a\u00020\u0014\u00a2\u0006\b\n\u0000\u001a\u0004\b$\u0010\u0016R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b%\u0010&R\u0011\u0010'\u001a\u00020\u0014\u00a2\u0006\b\n\u0000\u001a\u0004\b(\u0010\u0016\u00a8\u00060"}, d2={"Link/ptms/chemdah/util/Effect;", "", "source", "", "(Ljava/lang/String;)V", "count", "", "getCount", "()I", "data", "Link/ptms/chemdah/taboolib/common/platform/ProxyParticle$Data;", "getData", "()Link/ptms/chemdah/taboolib/common/platform/ProxyParticle$Data;", "setData", "(Link/ptms/chemdah/taboolib/common/platform/ProxyParticle$Data;)V", "demand", "Link/ptms/chemdah/taboolib/common5/Demand;", "getDemand", "()Link/ptms/chemdah/taboolib/common5/Demand;", "offsetX", "", "getOffsetX", "()D", "offsetY", "getOffsetY", "offsetZ", "getOffsetZ", "particle", "Link/ptms/chemdah/taboolib/common/platform/ProxyParticle;", "getParticle", "()Link/ptms/chemdah/taboolib/common/platform/ProxyParticle;", "posX", "getPosX", "posY", "getPosY", "posZ", "getPosZ", "getSource", "()Ljava/lang/String;", "speed", "getSpeed", "run", "", "location", "Lorg/bukkit/Location;", "player", "Lorg/bukkit/entity/Player;", "toString", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nEffect.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Effect.kt\nink/ptms/chemdah/util/Effect\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,81:1\n1#2:82\n800#3,11:83\n1855#3,2:94\n*S KotlinDebug\n*F\n+ 1 Effect.kt\nink/ptms/chemdah/util/Effect\n*L\n52#1:83,11\n52#1:94,2\n*E\n"})
public class Effect {
    @NotNull
    private final String source;
    @NotNull
    private final Demand demand;
    @NotNull
    private final ProxyParticle particle;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final double posX;
    private final double posY;
    private final double posZ;
    private final double speed;
    private final int count;
    @Nullable
    private ProxyParticle.Data data;

    public Effect(@NotNull String source) {
        block12: {
            Object object;
            Object object2;
            int n;
            Object object3;
            Object object4;
            Object object5;
            int n2;
            Object object6;
            String it;
            Intrinsics.checkNotNullParameter((Object)source, (String)"source");
            this.source = source;
            this.demand = new Demand(this.source);
            String string = this.demand.getNamespace().toUpperCase(Locale.ROOT);
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).toUpperCase(Locale.ROOT)");
            Object object7 = Enums.getIfPresent(ProxyParticle.class, (String)string).or((Object)ProxyParticle.FLAME);
            Intrinsics.checkNotNull((Object)object7);
            this.particle = (ProxyParticle)object7;
            String string2 = this.demand.get(0, "0");
            Intrinsics.checkNotNull((Object)string2);
            this.offsetX = Coerce.toDouble((Object)string2);
            String string3 = this.demand.get(1, "0");
            Intrinsics.checkNotNull((Object)string3);
            this.offsetY = Coerce.toDouble((Object)string3);
            String string4 = this.demand.get(2, "0");
            Intrinsics.checkNotNull((Object)string4);
            this.offsetZ = Coerce.toDouble((Object)string4);
            this.posX = Coerce.toDouble((Object)Demand.get$default((Demand)this.demand, (String)"posX", null, (int)2, null));
            this.posY = Coerce.toDouble((Object)Demand.get$default((Demand)this.demand, (String)"posY", null, (int)2, null));
            this.posZ = Coerce.toDouble((Object)Demand.get$default((Demand)this.demand, (String)"posZ", null, (int)2, null));
            Object[] objectArray = new String[]{"speed", "s"};
            String string5 = this.demand.get(CollectionsKt.listOf((Object[])objectArray), "0");
            Intrinsics.checkNotNull((Object)string5);
            this.speed = Coerce.toDouble((Object)string5);
            objectArray = new String[]{"count", "c"};
            String string6 = this.demand.get(CollectionsKt.listOf((Object[])objectArray), "1");
            Intrinsics.checkNotNull((Object)string6);
            this.count = Coerce.toInteger((Object)string6);
            String string7 = Demand.get$default((Demand)this.demand, (String)"block", null, (int)2, null);
            if (string7 != null) {
                it = string7;
                boolean bl = false;
                this.data = (ProxyParticle.Data)new ProxyParticle.BlockData(it, 0, 2, null);
            }
            String string8 = Demand.get$default((Demand)this.demand, (String)"item", null, (int)2, null);
            if (string8 != null) {
                Object object8;
                it = string8;
                boolean bl = false;
                Effect effect = this;
                object6 = new String[]{":"};
                String string9 = (String)StringsKt.split$default((CharSequence)it, (String[])object6, (boolean)false, (int)0, (int)6, null).get(0);
                object6 = new String[]{":"};
                int n3 = 1;
                if (n3 <= CollectionsKt.getLastIndex((List)(object6 = StringsKt.split$default((CharSequence)it, (String[])object6, (boolean)false, (int)0, (int)6, null)))) {
                    object8 = object6.get(n3);
                } else {
                    n2 = n3;
                    String string10 = string9;
                    Effect effect2 = effect;
                    boolean bl2 = false;
                    object5 = "0";
                    effect = effect2;
                    string9 = string10;
                    object8 = object5;
                }
                DefaultConstructorMarker defaultConstructorMarker = null;
                int n4 = 28;
                int n5 = 0;
                List list2 = null;
                String string11 = null;
                int n6 = Coerce.toInteger(object8);
                String string12 = string9;
                effect.data = (ProxyParticle.Data)new ProxyParticle.ItemData(string12, n6, string11, list2, n5, n4, defaultConstructorMarker);
            }
            String string13 = Demand.get$default((Demand)this.demand, (String)"color", null, (int)2, null);
            if (string13 == null) break block12;
            it = string13;
            boolean bl = false;
            object6 = new String[]{"~"};
            CharSequence charSequence = (CharSequence)StringsKt.split$default((CharSequence)it, (String[])object6, (boolean)false, (int)0, (int)6, null).get(0);
            object6 = new String[]{"-"};
            List color = StringsKt.split$default((CharSequence)charSequence, (String[])object6, (boolean)false, (int)0, (int)6, null);
            Object object9 = this;
            n2 = 0;
            object6 = color;
            if (n2 <= CollectionsKt.getLastIndex((List)object6)) {
                object4 = object6.get(n2);
            } else {
                int n7 = n2;
                object5 = object9;
                boolean bl3 = false;
                object4 = "0";
                object9 = object5;
            }
            int n8 = Coerce.toInteger(object4);
            object6 = color;
            n2 = 1;
            if (n2 <= CollectionsKt.getLastIndex((List)object6)) {
                object3 = object6.get(n2);
            } else {
                int it2 = n2;
                n = n8;
                object5 = object9;
                boolean bl4 = false;
                String string14 = "1";
                object9 = object5;
                n8 = n;
                object3 = string14;
            }
            int n9 = Coerce.toInteger(object3);
            object6 = color;
            n2 = 2;
            if (n2 <= CollectionsKt.getLastIndex((List)object6)) {
                object2 = object6.get(n2);
            } else {
                int it3 = n2;
                int n10 = n9;
                n = n8;
                object5 = object9;
                boolean bl5 = false;
                String string15 = "2";
                object9 = object5;
                n8 = n;
                n9 = n10;
                object2 = string15;
            }
            int n11 = Coerce.toInteger(object2);
            int n12 = n9;
            int n13 = n8;
            Color color2 = new Color(n13, n12, n11);
            object6 = new String[]{"~"};
            object6 = StringsKt.split$default((CharSequence)it, (String[])object6, (boolean)false, (int)0, (int)6, null);
            n2 = 1;
            if (n2 <= CollectionsKt.getLastIndex((List)object6)) {
                object = object6.get(n2);
            } else {
                int it4 = n2;
                Color color3 = color2;
                object5 = object9;
                boolean bl6 = false;
                String string16 = "0";
                object9 = object5;
                color2 = color3;
                object = string16;
            }
            float f = Coerce.toFloat(object);
            Color color4 = color2;
            ((Effect)object9).data = (ProxyParticle.Data)new ProxyParticle.DustData(color4, f);
        }
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
    public final ProxyParticle getParticle() {
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
    public final ProxyParticle.Data getData() {
        return this.data;
    }

    public final void setData(@Nullable ProxyParticle.Data data2) {
        this.data = data2;
    }

    /*
     * WARNING - void declaration
     */
    public final void run(@NotNull org.bukkit.Location location) {
        void $this$forEach$iv;
        void $this$filterIsInstanceTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)location, (String)"location");
        org.bukkit.Location location2 = location.clone().add(this.posX, this.posY, this.posZ);
        Intrinsics.checkNotNullExpressionValue((Object)location2, (String)"location.clone().add(posX, posY, posZ)");
        org.bukkit.Location pos = location2;
        Quat quat = null;
        quat = Quat.at((double)pos.getX(), (double)pos.getY(), (double)pos.getZ());
        quat = quat.rotate2D((double)location.getYaw(), location.getX(), location.getZ());
        World world = location.getWorld();
        Intrinsics.checkNotNull((Object)world);
        Collection collection = world.getNearbyEntities(location, 100.0, 100.0, 100.0);
        Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"location.world!!.getNear\u2026ion, 100.0, 100.0, 100.0)");
        Iterable $this$filterIsInstance$iv = collection;
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
            ProxyPlayer proxyPlayer = AdapterKt.adaptPlayer((Object)it);
            World world2 = location.getWorld();
            Intrinsics.checkNotNull((Object)world2);
            this.particle.sendTo(proxyPlayer, new Location(world2.getName(), quat.x(), quat.y(), quat.z()), new Vector(this.offsetX, this.offsetY, this.offsetZ), this.count, this.speed, this.data);
        }
    }

    public final void run(@NotNull org.bukkit.Location location, @NotNull Player player) {
        Intrinsics.checkNotNullParameter((Object)location, (String)"location");
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        org.bukkit.Location location2 = location.clone().add(this.posX, this.posY, this.posZ);
        Intrinsics.checkNotNullExpressionValue((Object)location2, (String)"location.clone().add(posX, posY, posZ)");
        org.bukkit.Location pos = location2;
        Quat quat = Quat.at((double)pos.getX(), (double)pos.getY(), (double)pos.getZ());
        quat = quat.rotate2D((double)location.getYaw(), location.getX(), location.getZ());
        ProxyPlayer proxyPlayer = AdapterKt.adaptPlayer((Object)player);
        World world = location.getWorld();
        Intrinsics.checkNotNull((Object)world);
        this.particle.sendTo(proxyPlayer, new Location(world.getName(), quat.x(), quat.y(), quat.z()), new Vector(this.offsetX, this.offsetY, this.offsetZ), this.count, this.speed, this.data);
    }

    @NotNull
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Effect(source='").append(this.source).append("', demand=").append(this.demand).append(", particle=").append(this.particle).append(", offsetX=").append(this.offsetX).append(", offsetY=").append(this.offsetY).append(", offsetZ=").append(this.offsetZ).append(", posX=").append(this.posX).append(", posY=").append(this.posY).append(", posZ=").append(this.posZ).append(", speed=").append(this.speed).append(", count=").append(this.count).append(", data=");
        stringBuilder.append(this.data).append(')');
        return stringBuilder.toString();
    }
}

