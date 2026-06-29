/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.realms;

import ink.ptms.chemdah.module.scenes.ScenesState;
import ink.ptms.chemdah.taboolib.common.util.Vector;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.navigation.BoundingBox;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0007\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u000f\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\f\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/module/realms/Realms;", "", "root", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "(Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;)V", "area", "Link/ptms/chemdah/taboolib/module/navigation/BoundingBox;", "getArea", "()Link/ptms/chemdah/taboolib/module/navigation/BoundingBox;", "id", "", "getId", "()Ljava/lang/String;", "getRoot", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "world", "getWorld", "Chemdah"})
public final class Realms {
    @NotNull
    private final ConfigurationSection root;
    @NotNull
    private final String id;
    @NotNull
    private final String world;
    @NotNull
    private final BoundingBox area;

    /*
     * WARNING - void declaration
     */
    public Realms(@NotNull ConfigurationSection root2) {
        void $this$area_u24lambda_u240;
        Intrinsics.checkNotNullParameter((Object)root2, (String)"root");
        this.root = root2;
        this.id = this.root.getName().toString();
        String string = this.root.getString("in");
        if (string == null) {
            string = "world";
        }
        this.world = string;
        String string2 = this.root.getString("area", "");
        Intrinsics.checkNotNull((Object)string2);
        String string3 = string2;
        Realms realms2 = this;
        boolean bl = false;
        String[] stringArray = new String[]{"~"};
        Vector min2 = ScenesState.Companion.toVector$default(ScenesState.Companion, ((Object)StringsKt.trim((CharSequence)((String)StringsKt.split$default((CharSequence)((CharSequence)$this$area_u24lambda_u240), (String[])stringArray, (boolean)false, (int)0, (int)6, null).get(0)))).toString(), null, 1, null);
        String[] stringArray2 = new String[]{"~"};
        Vector max2 = ScenesState.Companion.toVector$default(ScenesState.Companion, ((Object)StringsKt.trim((CharSequence)String.valueOf(CollectionsKt.getOrNull((List)StringsKt.split$default((CharSequence)((CharSequence)$this$area_u24lambda_u240), (String[])stringArray2, (boolean)false, (int)0, (int)6, null), (int)1)))).toString(), null, 1, null);
        realms2.area = new BoundingBox(Double.min(min2.getX(), max2.getX()), Double.min(min2.getY(), max2.getY()), Double.min(min2.getZ(), max2.getZ()), Double.max(min2.getX(), max2.getX()), Double.max(min2.getY(), max2.getY()), Double.max(min2.getZ(), max2.getZ()));
    }

    @NotNull
    public final ConfigurationSection getRoot() {
        return this.root;
    }

    @NotNull
    public final String getId() {
        return this.id;
    }

    @NotNull
    public final String getWorld() {
        return this.world;
    }

    @NotNull
    public final BoundingBox getArea() {
        return this.area;
    }
}

