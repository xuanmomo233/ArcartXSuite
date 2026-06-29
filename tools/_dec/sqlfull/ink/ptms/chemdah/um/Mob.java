/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.um;

import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.um.MobType;
import kotlin.Metadata;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000J\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0010\u0006\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\bf\u0018\u00002\u00020\u0001J \u0010 \u001a\u00020!2\u0006\u0010\"\u001a\u00020\u000b2\u0006\u0010#\u001a\u00020$2\u0006\u0010%\u001a\u00020\u0017H&J \u0010&\u001a\u00020!2\u0006\u0010\"\u001a\u00020\u000b2\u0006\u0010#\u001a\u00020$2\u0006\u0010%\u001a\u00020\u0017H&R\u0012\u0010\u0002\u001a\u00020\u0003X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0004\u0010\u0005R\u0012\u0010\u0006\u001a\u00020\u0007X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\b\u0010\tR\u0012\u0010\n\u001a\u00020\u000bX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\f\u0010\rR\u0012\u0010\u000e\u001a\u00020\u000fX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0010\u0010\u0011R\u0012\u0010\u0012\u001a\u00020\u0007X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0013\u0010\tR\u0012\u0010\u0014\u001a\u00020\u0007X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0015\u0010\tR\u0012\u0010\u0016\u001a\u00020\u0017X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0018\u0010\u0019R\u0012\u0010\u001a\u001a\u00020\u0007X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u001b\u0010\tR\u0012\u0010\u001c\u001a\u00020\u001dX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u001e\u0010\u001f\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006'\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/um/Mob;", "", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "getConfig", "()Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "displayName", "", "getDisplayName", "()Ljava/lang/String;", "entity", "Lorg/bukkit/entity/Entity;", "getEntity", "()Lorg/bukkit/entity/Entity;", "entityType", "Lorg/bukkit/entity/EntityType;", "getEntityType", "()Lorg/bukkit/entity/EntityType;", "faction", "getFaction", "id", "getId", "level", "", "getLevel", "()D", "stance", "getStance", "type", "Link/ptms/chemdah/um/MobType;", "getType", "()Link/ptms/chemdah/um/MobType;", "addThreat", "", "mob", "target", "Lorg/bukkit/entity/LivingEntity;", "amount", "reduceThreat", "common"})
public interface Mob {
    @NotNull
    public String getId();

    @NotNull
    public String getDisplayName();

    @NotNull
    public MobType getType();

    @NotNull
    public Entity getEntity();

    @NotNull
    public EntityType getEntityType();

    public double getLevel();

    @NotNull
    public String getStance();

    @NotNull
    public String getFaction();

    @NotNull
    public ConfigurationSection getConfig();

    public void addThreat(@NotNull Entity var1, @NotNull LivingEntity var2, double var3);

    public void reduceThreat(@NotNull Entity var1, @NotNull LivingEntity var2, double var3);
}

