/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.lumine.mythic.api.adapters.AbstractLocation
 *  io.lumine.mythic.api.skills.SkillCaster
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.um.impl5;

import ink.ptms.chemdah.um.impl5.UtilsKt;
import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.api.skills.SkillCaster;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0006\n\u0002\u0010\u0006\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0007\n\u0002\b\u0003\b\u0000\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u0014\u0010\u0005\u001a\u00020\u00068VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0007\u0010\bR$\u0010\u000b\u001a\u00020\n2\u0006\u0010\t\u001a\u00020\n8V@VX\u0096\u000e\u00a2\u0006\f\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000fR\u0014\u0010\u0010\u001a\u00020\u00118VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0012\u0010\u0013R\u0014\u0010\u0014\u001a\u00020\u00158VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0016\u0010\u0017R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0019R\u0014\u0010\u001a\u001a\u00020\u001b8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u001c\u0010\u001d\u00a8\u0006\u001e"}, d2={"Link/ptms/chemdah/um/impl5/SkillCasterProxy5;", "Link/ptms/chemdah/um/skill/SkillCaster;", "origin", "Lio/lumine/mythic/api/skills/SkillCaster;", "(Lio/lumine/mythic/api/skills/SkillCaster;)V", "entity", "Lorg/bukkit/entity/Entity;", "getEntity", "()Lorg/bukkit/entity/Entity;", "value", "", "globalCooldown", "getGlobalCooldown", "()I", "setGlobalCooldown", "(I)V", "level", "", "getLevel", "()D", "location", "Lorg/bukkit/Location;", "getLocation", "()Lorg/bukkit/Location;", "getOrigin", "()Lio/lumine/mythic/api/skills/SkillCaster;", "power", "", "getPower", "()F", "implementation-v5"})
public final class SkillCasterProxy5
implements ink.ptms.chemdah.um.skill.SkillCaster {
    @NotNull
    private final SkillCaster origin;

    public SkillCasterProxy5(@NotNull SkillCaster origin) {
        Intrinsics.checkNotNullParameter((Object)origin, (String)"origin");
        this.origin = origin;
    }

    @NotNull
    public final SkillCaster getOrigin() {
        return this.origin;
    }

    @Override
    @NotNull
    public Entity getEntity() {
        Entity entity = this.origin.getEntity().getBukkitEntity();
        Intrinsics.checkNotNullExpressionValue((Object)entity, (String)"getBukkitEntity(...)");
        return entity;
    }

    @Override
    @NotNull
    public Location getLocation() {
        AbstractLocation abstractLocation = this.origin.getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)abstractLocation, (String)"getLocation(...)");
        return UtilsKt.toBukkit(abstractLocation);
    }

    @Override
    public double getLevel() {
        return this.origin.getLevel();
    }

    @Override
    public float getPower() {
        return this.origin.getPower();
    }

    @Override
    public int getGlobalCooldown() {
        return this.origin.getGlobalCooldown();
    }

    @Override
    public void setGlobalCooldown(int value2) {
        this.origin.setGlobalCooldown(value2);
    }
}

