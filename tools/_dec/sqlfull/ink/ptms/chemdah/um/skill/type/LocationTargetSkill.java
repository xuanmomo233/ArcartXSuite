/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.um.skill.type;

import ink.ptms.chemdah.um.skill.SkillMeta;
import ink.ptms.chemdah.um.skill.SkillResult;
import ink.ptms.chemdah.um.skill.type.BaseSkill;
import kotlin.Metadata;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\bf\u0018\u00002\u00020\u0001J\u0018\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u0007H&\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\b\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/um/skill/type/LocationTargetSkill;", "Link/ptms/chemdah/um/skill/type/BaseSkill;", "cast", "Link/ptms/chemdah/um/skill/SkillResult;", "meta", "Link/ptms/chemdah/um/skill/SkillMeta;", "location", "Lorg/bukkit/Location;", "common"})
public interface LocationTargetSkill
extends BaseSkill {
    @NotNull
    public SkillResult cast(@NotNull SkillMeta var1, @NotNull Location var2);
}

