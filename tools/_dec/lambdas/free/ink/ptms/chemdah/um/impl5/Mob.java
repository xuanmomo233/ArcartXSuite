/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  io.lumine.mythic.api.mobs.MythicMob
 *  io.lumine.mythic.bukkit.MythicBukkit
 *  io.lumine.mythic.core.mobs.ActiveMob
 *  kotlin.Metadata
 *  kotlin1822.Lazy
 *  kotlin1822.LazyKt
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.LivingEntity
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.um.impl5;

import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.um.MobType;
import ink.ptms.chemdah.um.impl5.Mob;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.LazyKt;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000Z\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0010\u0006\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0000\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J \u0010+\u001a\u00020,2\u0006\u0010-\u001a\u00020\u00142\u0006\u0010.\u001a\u00020/2\u0006\u00100\u001a\u00020 H\u0016J \u00101\u001a\u00020,2\u0006\u0010-\u001a\u00020\u00142\u0006\u0010.\u001a\u00020/2\u0006\u00100\u001a\u00020 H\u0016R\u001b\u0010\u0005\u001a\u00020\u00068FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\t\u0010\n\u001a\u0004\b\u0007\u0010\bR\u0014\u0010\u000b\u001a\u00020\f8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\r\u0010\u000eR\u0014\u0010\u000f\u001a\u00020\u00108VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0011\u0010\u0012R\u0014\u0010\u0013\u001a\u00020\u00148VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0015\u0010\u0016R\u0014\u0010\u0017\u001a\u00020\u00188VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0019\u0010\u001aR\u0014\u0010\u001b\u001a\u00020\u00108VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u001c\u0010\u0012R\u0014\u0010\u001d\u001a\u00020\u00108VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u001e\u0010\u0012R\u0014\u0010\u001f\u001a\u00020 8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b!\u0010\"R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b#\u0010$R\u0014\u0010%\u001a\u00020\u00108VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b&\u0010\u0012R\u0014\u0010'\u001a\u00020(8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b)\u0010*\u00a8\u00062"}, d2={"Link/ptms/chemdah/um/impl5/Mob;", "Link/ptms/chemdah/um/Mob;", "source", "Lio/lumine/mythic/core/mobs/ActiveMob;", "(Lio/lumine/mythic/core/mobs/ActiveMob;)V", "api", "Lio/lumine/mythic/bukkit/MythicBukkit;", "getApi", "()Lio/lumine/mythic/bukkit/MythicBukkit;", "api$delegate", "Lkotlin1822/Lazy;", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "getConfig", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "displayName", "", "getDisplayName", "()Ljava/lang/String;", "entity", "Lorg/bukkit/entity/Entity;", "getEntity", "()Lorg/bukkit/entity/Entity;", "entityType", "Lorg/bukkit/entity/EntityType;", "getEntityType", "()Lorg/bukkit/entity/EntityType;", "faction", "getFaction", "id", "getId", "level", "", "getLevel", "()D", "getSource", "()Lio/lumine/mythic/core/mobs/ActiveMob;", "stance", "getStance", "type", "Link/ptms/chemdah/um/MobType;", "getType", "()Link/ptms/chemdah/um/MobType;", "addThreat", "", "mob", "target", "Lorg/bukkit/entity/LivingEntity;", "amount", "reduceThreat", "implementation-v5"})
public final class Mob
implements ink.ptms.chemdah.um.Mob {
    @NotNull
    private final ActiveMob source;
    @NotNull
    private final Lazy api$delegate;

    public Mob(@NotNull ActiveMob source) {
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        this.source = source;
        this.api$delegate = LazyKt.lazy((Function0)api.2.INSTANCE);
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
        return new ink.ptms.chemdah.um.impl5.MobType(mythicMob);
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

    @NotNull
    public final MythicBukkit getApi() {
        Lazy lazy = this.api$delegate;
        return (MythicBukkit)lazy.getValue();
    }

    @Override
    public void addThreat(@NotNull Entity mob, @NotNull LivingEntity target, double amount) {
        Intrinsics.checkNotNullParameter((Object)mob, (String)"mob");
        Intrinsics.checkNotNullParameter((Object)target, (String)"target");
        this.getApi().getAPIHelper().addThreat(mob, target, amount);
    }

    @Override
    public void reduceThreat(@NotNull Entity mob, @NotNull LivingEntity target, double amount) {
        Intrinsics.checkNotNullParameter((Object)mob, (String)"mob");
        Intrinsics.checkNotNullParameter((Object)target, (String)"target");
        this.getApi().getAPIHelper().reduceThreat(mob, target, amount);
    }
}

