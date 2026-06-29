/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin.jvm.JvmStatic
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.um.skill.condition;

import ink.ptms.chemdah.um.skill.condition.CasterCondition;
import ink.ptms.chemdah.um.skill.condition.EntityComparisonCondition;
import ink.ptms.chemdah.um.skill.condition.EntityCondition;
import ink.ptms.chemdah.um.skill.condition.EntityLocationDistanceCondition;
import ink.ptms.chemdah.um.skill.condition.LocationCondition;
import ink.ptms.chemdah.um.skill.condition.SkillMetaComparisonCondition;
import ink.ptms.chemdah.um.skill.condition.SkillMetadataCondition;
import kotlin.Metadata;
import kotlin.jvm.JvmStatic;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\bf\u0018\u0000 \u00022\u00020\u0001:\u0001\u0002\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\u0003\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/um/skill/condition/BaseCondition;", "", "Companion", "common"})
public interface BaseCondition {
    @NotNull
    public static final Companion Companion = ink.ptms.chemdah.um.skill.condition.BaseCondition$Companion.$$INSTANCE;

    @JvmStatic
    public static boolean isSubclass(@NotNull BaseCondition subclass) {
        return Companion.isSubclass(subclass);
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007\u00a8\u0006\u0007"}, d2={"Link/ptms/chemdah/um/skill/condition/BaseCondition$Companion;", "", "()V", "isSubclass", "", "subclass", "Link/ptms/chemdah/um/skill/condition/BaseCondition;", "common"})
    public static final class Companion {
        static final /* synthetic */ Companion $$INSTANCE;

        private Companion() {
        }

        @JvmStatic
        public final boolean isSubclass(@NotNull BaseCondition subclass) {
            Intrinsics.checkNotNullParameter((Object)subclass, (String)"subclass");
            BaseCondition baseCondition = subclass;
            return (((((baseCondition instanceof CasterCondition ? true : baseCondition instanceof EntityCondition) ? true : baseCondition instanceof LocationCondition) ? true : baseCondition instanceof SkillMetadataCondition) ? true : baseCondition instanceof EntityComparisonCondition) ? true : baseCondition instanceof SkillMetaComparisonCondition) ? true : baseCondition instanceof EntityLocationDistanceCondition;
        }

        static {
            $$INSTANCE = new Companion();
        }
    }
}

