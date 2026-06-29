/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  io.lumine.xikage.mythicmobs.MythicMobs
 *  io.lumine.xikage.mythicmobs.mobs.ActiveMob
 *  io.lumine.xikage.mythicmobs.mobs.MythicMob
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.LivingEntity
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.um.impl4;

import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.um.MobType;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.mobs.ActiveMob;
import io.lumine.xikage.mythicmobs.mobs.MythicMob;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000R\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0010\u0006\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0000\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J \u0010%\u001a\u00020&2\u0006\u0010'\u001a\u00020\u000e2\u0006\u0010(\u001a\u00020)2\u0006\u0010*\u001a\u00020\u001aH\u0016J \u0010+\u001a\u00020&2\u0006\u0010'\u001a\u00020\u000e2\u0006\u0010(\u001a\u00020)2\u0006\u0010*\u001a\u00020\u001aH\u0016R\u0014\u0010\u0005\u001a\u00020\u00068VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0007\u0010\bR\u0014\u0010\t\u001a\u00020\n8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000b\u0010\fR\u0014\u0010\r\u001a\u00020\u000e8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000f\u0010\u0010R\u0014\u0010\u0011\u001a\u00020\u00128VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0013\u0010\u0014R\u0014\u0010\u0015\u001a\u00020\n8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0016\u0010\fR\u0014\u0010\u0017\u001a\u00020\n8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0018\u0010\fR\u0014\u0010\u0019\u001a\u00020\u001a8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u001b\u0010\u001cR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001eR\u0014\u0010\u001f\u001a\u00020\n8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b \u0010\fR\u0014\u0010!\u001a\u00020\"8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b#\u0010$\u00a8\u0006,"}, d2={"Link/ptms/chemdah/um/impl4/Mob;", "Link/ptms/chemdah/um/Mob;", "source", "Lio/lumine/xikage/mythicmobs/mobs/ActiveMob;", "(Lio/lumine/xikage/mythicmobs/mobs/ActiveMob;)V", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "getConfig", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "displayName", "", "getDisplayName", "()Ljava/lang/String;", "entity", "Lorg/bukkit/entity/Entity;", "getEntity", "()Lorg/bukkit/entity/Entity;", "entityType", "Lorg/bukkit/entity/EntityType;", "getEntityType", "()Lorg/bukkit/entity/EntityType;", "faction", "getFaction", "id", "getId", "level", "", "getLevel", "()D", "getSource", "()Lio/lumine/xikage/mythicmobs/mobs/ActiveMob;", "stance", "getStance", "type", "Link/ptms/chemdah/um/MobType;", "getType", "()Link/ptms/chemdah/um/MobType;", "addThreat", "", "mob", "target", "Lorg/bukkit/entity/LivingEntity;", "amount", "reduceThreat", "implementation-v4"})
public final class Mob
implements ink.ptms.chemdah.um.Mob {
    @NotNull
    private final ActiveMob source;

    public Mob(@NotNull ActiveMob source) {
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        this.source = source;
    }

    @NotNull
    public final ActiveMob getSource() {
        return this.source;
    }

    @Override
    @NotNull
    public String getId() {
        String string = this.source.getType().getInternalName();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getInternalName(...)");
        return string;
    }

    @Override
    @NotNull
    public String getDisplayName() {
        String string = this.source.getDisplayName();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getDisplayName(...)");
        return string;
    }

    @Override
    @NotNull
    public MobType getType() {
        MythicMob mythicMob = this.source.getType();
        Intrinsics.checkNotNullExpressionValue((Object)mythicMob, (String)"getType(...)");
        return new ink.ptms.chemdah.um.impl4.MobType(mythicMob);
    }

    @Override
    @NotNull
    public Entity getEntity() {
        Entity entity = this.source.getEntity().getBukkitEntity();
        Intrinsics.checkNotNullExpressionValue((Object)entity, (String)"getBukkitEntity(...)");
        return entity;
    }

    @Override
    @NotNull
    public EntityType getEntityType() {
        EntityType entityType = this.getEntity().getType();
        Intrinsics.checkNotNullExpressionValue((Object)entityType, (String)"getType(...)");
        return entityType;
    }

    @Override
    public double getLevel() {
        return this.source.getLevel();
    }

    @Override
    @NotNull
    public String getStance() {
        String string = this.source.getStance();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getStance(...)");
        return string;
    }

    @Override
    @NotNull
    public String getFaction() {
        String string = this.source.getFaction();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getFaction(...)");
        return string;
    }

    @Override
    @NotNull
    public ConfigurationSection getConfig() {
        return this.getType().getConfig();
    }

    @Override
    public void addThreat(@NotNull Entity mob, @NotNull LivingEntity target, double amount) {
        Intrinsics.checkNotNullParameter((Object)mob, (String)"mob");
        Intrinsics.checkNotNullParameter((Object)target, (String)"target");
        MythicMobs.inst().getAPIHelper().addThreat(mob, target, amount);
    }

    @Override
    public void reduceThreat(@NotNull Entity mob, @NotNull LivingEntity target, double amount) {
        Intrinsics.checkNotNullParameter((Object)mob, (String)"mob");
        Intrinsics.checkNotNullParameter((Object)target, (String)"target");
        MythicMobs.inst().getAPIHelper().reduceThreat(mob, target, amount);
    }
}

