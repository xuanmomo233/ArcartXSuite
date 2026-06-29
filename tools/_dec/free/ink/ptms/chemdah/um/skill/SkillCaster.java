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

import kotlin.Metadata;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0010\u0006\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0007\n\u0002\b\u0003\bf\u0018\u00002\u00020\u0001R\u0012\u0010\u0002\u001a\u00020\u0003X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0004\u0010\u0005R\u0018\u0010\u0006\u001a\u00020\u0007X\u00a6\u000e\u00a2\u0006\f\u001a\u0004\b\b\u0010\t\"\u0004\b\n\u0010\u000bR\u0012\u0010\f\u001a\u00020\rX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000e\u0010\u000fR\u0012\u0010\u0010\u001a\u00020\u0011X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0012\u0010\u0013R\u0012\u0010\u0014\u001a\u00020\u0015X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0016\u0010\u0017\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\u0018\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/um/skill/SkillCaster;", "", "entity", "Lorg/bukkit/entity/Entity;", "getEntity", "()Lorg/bukkit/entity/Entity;", "globalCooldown", "", "getGlobalCooldown", "()I", "setGlobalCooldown", "(I)V", "level", "", "getLevel", "()D", "location", "Lorg/bukkit/Location;", "getLocation", "()Lorg/bukkit/Location;", "power", "", "getPower", "()F", "common"})
public interface SkillCaster {
    @NotNull
    public Entity getEntity();

    @NotNull
    public Location getLocation();

    public double getLevel();

    public float getPower();

    public int getGlobalCooldown();

    public void setGlobalCooldown(int var1);
}

