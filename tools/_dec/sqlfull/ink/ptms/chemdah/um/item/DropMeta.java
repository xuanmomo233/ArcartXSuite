/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.um.item;

import ink.ptms.chemdah.um.skill.SkillCaster;
import kotlin.Metadata;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\bf\u0018\u00002\u00020\u0001J\b\u0010\u0016\u001a\u00020\u0017H&R\u0018\u0010\u0002\u001a\u00020\u0003X\u00a6\u000e\u00a2\u0006\f\u001a\u0004\b\u0004\u0010\u0005\"\u0004\b\u0006\u0010\u0007R\u0014\u0010\b\u001a\u0004\u0018\u00010\tX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\n\u0010\u000bR\u0014\u0010\f\u001a\u0004\u0018\u00010\rX\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000e\u0010\u000fR\u0018\u0010\u0010\u001a\u00020\u0011X\u00a6\u000e\u00a2\u0006\f\u001a\u0004\b\u0012\u0010\u0013\"\u0004\b\u0014\u0010\u0015\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\u0018\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/um/item/DropMeta;", "", "amount", "", "getAmount", "()F", "setAmount", "(F)V", "cause", "Lorg/bukkit/entity/Entity;", "getCause", "()Lorg/bukkit/entity/Entity;", "dropper", "Link/ptms/chemdah/um/skill/SkillCaster;", "getDropper", "()Link/ptms/chemdah/um/skill/SkillCaster;", "generations", "", "getGenerations", "()I", "setGenerations", "(I)V", "tick", "", "common"})
public interface DropMeta {
    @Nullable
    public SkillCaster getDropper();

    @Nullable
    public Entity getCause();

    public float getAmount();

    public void setAmount(float var1);

    public int getGenerations();

    public void setGenerations(int var1);

    public void tick();
}

