/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.scenes;

import kotlin.Metadata;
import kotlin1822.jvm.internal.DefaultConstructorMarker;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0005\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u000e\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B!\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0011\u001a\u00020\u0007H\u00c6\u0003J'\u0010\u0012\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0007H\u00c6\u0001J\u0013\u0010\u0013\u001a\u00020\u00072\b\u0010\u0014\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0015\u001a\u00020\u0016H\u00d6\u0001J\t\u0010\u0017\u001a\u00020\u0018H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u0019"}, d2={"Link/ptms/chemdah/module/scenes/ScenesBlockData;", "", "material", "Lorg/bukkit/Material;", "data", "", "falling", "", "(Lorg/bukkit/Material;BZ)V", "getData", "()B", "getFalling", "()Z", "getMaterial", "()Lorg/bukkit/Material;", "component1", "component2", "component3", "copy", "equals", "other", "hashCode", "", "toString", "", "Chemdah"})
public final class ScenesBlockData {
    @NotNull
    private final Material material;
    private final byte data;
    private final boolean falling;

    public ScenesBlockData(@NotNull Material material, byte data2, boolean falling) {
        Intrinsics.checkNotNullParameter((Object)material, (String)"material");
        this.material = material;
        this.data = data2;
        this.falling = falling;
    }

    public /* synthetic */ ScenesBlockData(Material material, byte by, boolean bl, int n, DefaultConstructorMarker defaultConstructorMarker) {
        if ((n & 2) != 0) {
            by = 0;
        }
        if ((n & 4) != 0) {
            bl = false;
        }
        this(material, by, bl);
    }

    @NotNull
    public final Material getMaterial() {
        return this.material;
    }

    public final byte getData() {
        return this.data;
    }

    public final boolean getFalling() {
        return this.falling;
    }

    @NotNull
    public final Material component1() {
        return this.material;
    }

    public final byte component2() {
        return this.data;
    }

    public final boolean component3() {
        return this.falling;
    }

    @NotNull
    public final ScenesBlockData copy(@NotNull Material material, byte data2, boolean falling) {
        Intrinsics.checkNotNullParameter((Object)material, (String)"material");
        return new ScenesBlockData(material, data2, falling);
    }

    public static /* synthetic */ ScenesBlockData copy$default(ScenesBlockData scenesBlockData, Material material, byte by, boolean bl, int n, Object object) {
        if ((n & 1) != 0) {
            material = scenesBlockData.material;
        }
        if ((n & 2) != 0) {
            by = scenesBlockData.data;
        }
        if ((n & 4) != 0) {
            bl = scenesBlockData.falling;
        }
        return scenesBlockData.copy(material, by, bl);
    }

    @NotNull
    public String toString() {
        return "ScenesBlockData(material=" + this.material + ", data=" + this.data + ", falling=" + this.falling + ')';
    }

    public int hashCode() {
        int result = this.material.hashCode();
        result = result * 31 + Byte.hashCode(this.data);
        int n = this.falling ? 1 : 0;
        if (n != 0) {
            n = 1;
        }
        result = result * 31 + n;
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScenesBlockData)) {
            return false;
        }
        ScenesBlockData scenesBlockData = (ScenesBlockData)other;
        if (this.material != scenesBlockData.material) {
            return false;
        }
        if (this.data != scenesBlockData.data) {
            return false;
        }
        return this.falling == scenesBlockData.falling;
    }
}

