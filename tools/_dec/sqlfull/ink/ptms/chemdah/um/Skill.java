/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.um;

import ink.ptms.chemdah.um.Skill;
import java.util.Map;
import java.util.Set;
import kotlin.Metadata;
import kotlin1822.collections.MapsKt;
import kotlin1822.collections.SetsKt;
import kotlin1822.jvm.functions.Function1;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000X\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\"\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0003\bf\u0018\u00002\u00020\u0001:\u0002\u001f Jv\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u000b2\u000e\b\u0002\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000b0\u000e2\u000e\b\u0002\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00100\u000e2\b\b\u0002\u0010\u0011\u001a\u00020\u00122\u0014\b\u0002\u0010\u0013\u001a\u000e\u0012\u0004\u0012\u00020\u0015\u0012\u0004\u0012\u00020\u00010\u00142\u0014\b\u0002\u0010\u0016\u001a\u000e\u0012\u0004\u0012\u00020\u000b\u0012\u0004\u0012\u00020\u00070\u0017H&J\u0010\u0010\u0018\u001a\u00020\u00122\u0006\u0010\u0019\u001a\u00020\u000bH&J\u0010\u0010\u001a\u001a\u00020\u00072\u0006\u0010\u0019\u001a\u00020\u000bH&J\u0018\u0010\u001b\u001a\u00020\u001c2\u0006\u0010\u0019\u001a\u00020\u000b2\u0006\u0010\u001d\u001a\u00020\u001eH&R\u0012\u0010\u0002\u001a\u00020\u0003X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0004\u0010\u0005\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006!\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/um/Skill;", "", "delay", "", "getDelay", "()I", "execute", "", "trigger", "Link/ptms/chemdah/um/Skill$Trigger;", "entity", "Lorg/bukkit/entity/Entity;", "target", "et", "", "lt", "Lorg/bukkit/Location;", "power", "", "parameters", "", "", "targetFilter", "Lkotlin1822/Function1;", "getCooldown", "caster", "onCooldown", "setCooldown", "", "time", "", "ActiveCaster", "Trigger", "common"})
public interface Skill {
    public int getDelay();

    public boolean execute(@NotNull Trigger var1, @NotNull Entity var2, @NotNull Entity var3, @NotNull Set<? extends Entity> var4, @NotNull Set<? extends Location> var5, float var6, @NotNull Map<String, ? extends Object> var7, @NotNull Function1<? super Entity, Boolean> var8);

    public static /* synthetic */ boolean execute$default(Skill skill, Trigger trigger2, Entity entity, Entity entity2, Set set2, Set set3, float f, Map map, Function1 function1, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: execute");
        }
        if ((n & 8) != 0) {
            set2 = SetsKt.emptySet();
        }
        if ((n & 0x10) != 0) {
            set3 = SetsKt.emptySet();
        }
        if ((n & 0x20) != 0) {
            f = 0.0f;
        }
        if ((n & 0x40) != 0) {
            map = MapsKt.emptyMap();
        }
        if ((n & 0x80) != 0) {
            function1 = execute.1.INSTANCE;
        }
        return skill.execute(trigger2, entity, entity2, set2, set3, f, map, (Function1<? super Entity, Boolean>)function1);
    }

    public boolean onCooldown(@NotNull Entity var1);

    public float getCooldown(@NotNull Entity var1);

    public void setCooldown(@NotNull Entity var1, double var2);

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\b\u0003\bf\u0018\u00002\u00020\u0001R\u001e\u0010\u0002\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00010\u0003X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\u0007\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/um/Skill$ActiveCaster;", "", "parameters", "", "", "getParameters", "()Ljava/util/Map;", "common"})
    public static interface ActiveCaster {
        @NotNull
        public Map<String, Object> getParameters();
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\bf\u0018\u00002\u00020\u0001R\u0012\u0010\u0002\u001a\u00020\u0003X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0004\u0010\u0005\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\u0006\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/um/Skill$Trigger;", "", "name", "", "getName", "()Ljava/lang/String;", "common"})
    public static interface Trigger {
        @NotNull
        public String getName();
    }
}

