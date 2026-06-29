/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.um.skill.condition;

import ink.ptms.chemdah.um.skill.condition.BaseCondition;
import kotlin.Metadata;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\bf\u0018\u00002\u00020\u0001J\u001c\u0010\u0002\u001a\u00020\u00032\b\u0010\u0004\u001a\u0004\u0018\u00010\u00052\b\u0010\u0006\u001a\u0004\u0018\u00010\u0005H&\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\u0007\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/um/skill/condition/EntityComparisonCondition;", "Link/ptms/chemdah/um/skill/condition/BaseCondition;", "check", "", "caster", "Lorg/bukkit/entity/Entity;", "entity", "common"})
public interface EntityComparisonCondition
extends BaseCondition {
    public boolean check(@Nullable Entity var1, @Nullable Entity var2);
}

