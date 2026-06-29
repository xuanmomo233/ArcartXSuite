/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.um.skill;

import ink.ptms.chemdah.um.Skill;
import ink.ptms.chemdah.um.skill.SkillCaster;
import java.util.Map;
import java.util.Set;
import kotlin.Metadata;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000R\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\"\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\b\n\n\u0002\u0010\u0007\n\u0002\b\f\n\u0002\u0010\u0002\n\u0002\b\u0004\bf\u0018\u00002\u00020\u0001J\b\u00103\u001a\u00020\u0000H&J\b\u00104\u001a\u00020\u0000H&J\u0018\u00105\u001a\u0002062\u0006\u00107\u001a\u00020\u001e2\u0006\u00108\u001a\u00020\u0001H&J\u0018\u00109\u001a\u0002062\u0006\u00107\u001a\u00020\u001e2\u0006\u00108\u001a\u00020\u001eH&R\u0018\u0010\u0002\u001a\u00020\u0003X\u00a6\u000e\u00a2\u0006\f\u001a\u0004\b\u0004\u0010\u0005\"\u0004\b\u0006\u0010\u0007R\u0012\u0010\b\u001a\u00020\tX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\n\u0010\u000bR\u001e\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u000e0\rX\u00a6\u000e\u00a2\u0006\f\u001a\u0004\b\u000f\u0010\u0010\"\u0004\b\u0011\u0010\u0012R\u0018\u0010\u0013\u001a\u00020\u0014X\u00a6\u000e\u00a2\u0006\f\u001a\u0004\b\u0013\u0010\u0015\"\u0004\b\u0016\u0010\u0017R\u001e\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\u00190\rX\u00a6\u000e\u00a2\u0006\f\u001a\u0004\b\u001a\u0010\u0010\"\u0004\b\u001b\u0010\u0012R\u001e\u0010\u001c\u001a\u000e\u0012\u0004\u0012\u00020\u001e\u0012\u0004\u0012\u00020\u00010\u001dX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u001f\u0010 R\u0018\u0010!\u001a\u00020\u0019X\u00a6\u000e\u00a2\u0006\f\u001a\u0004\b\"\u0010#\"\u0004\b$\u0010%R\u001e\u0010&\u001a\u000e\u0012\u0004\u0012\u00020\u001e\u0012\u0004\u0012\u00020\u001e0\u001dX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b'\u0010 R\u0018\u0010(\u001a\u00020)X\u00a6\u000e\u00a2\u0006\f\u001a\u0004\b*\u0010+\"\u0004\b,\u0010-R\u0018\u0010.\u001a\u00020\u000eX\u00a6\u000e\u00a2\u0006\f\u001a\u0004\b/\u00100\"\u0004\b1\u00102\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006:\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/um/skill/SkillMeta;", "", "caster", "Link/ptms/chemdah/um/skill/SkillCaster;", "getCaster", "()Link/ptms/chemdah/um/skill/SkillCaster;", "setCaster", "(Link/ptms/chemdah/um/skill/SkillCaster;)V", "cause", "Link/ptms/chemdah/um/Skill$Trigger;", "getCause", "()Link/ptms/chemdah/um/Skill$Trigger;", "entityTargets", "", "Lorg/bukkit/entity/Entity;", "getEntityTargets", "()Ljava/util/Set;", "setEntityTargets", "(Ljava/util/Set;)V", "isAsync", "", "()Z", "setAsync", "(Z)V", "locationTargets", "Lorg/bukkit/Location;", "getLocationTargets", "setLocationTargets", "metadata", "", "", "getMetadata", "()Ljava/util/Map;", "origin", "getOrigin", "()Lorg/bukkit/Location;", "setOrigin", "(Lorg/bukkit/Location;)V", "parameters", "getParameters", "power", "", "getPower", "()F", "setPower", "(F)V", "trigger", "getTrigger", "()Lorg/bukkit/entity/Entity;", "setTrigger", "(Lorg/bukkit/entity/Entity;)V", "clone", "deepClone", "setMetadata", "", "key", "value", "setParameter", "common"})
public interface SkillMeta {
    @NotNull
    public SkillCaster getCaster();

    public void setCaster(@NotNull SkillCaster var1);

    @NotNull
    public Entity getTrigger();

    public void setTrigger(@NotNull Entity var1);

    @NotNull
    public Location getOrigin();

    public void setOrigin(@NotNull Location var1);

    @NotNull
    public Skill.Trigger getCause();

    public float getPower();

    public void setPower(float var1);

    public boolean isAsync();

    public void setAsync(boolean var1);

    @NotNull
    public Set<Entity> getEntityTargets();

    public void setEntityTargets(@NotNull Set<? extends Entity> var1);

    @NotNull
    public Set<Location> getLocationTargets();

    public void setLocationTargets(@NotNull Set<? extends Location> var1);

    @NotNull
    public Map<String, Object> getMetadata();

    @NotNull
    public Map<String, String> getParameters();

    public void setMetadata(@NotNull String var1, @NotNull Object var2);

    public void setParameter(@NotNull String var1, @NotNull String var2);

    @NotNull
    public SkillMeta clone();

    @NotNull
    public SkillMeta deepClone();
}

