/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin1822.enums.EnumEntries
 *  kotlin1822.enums.EnumEntriesKt
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.um.skill;

import kotlin.Metadata;
import kotlin1822.enums.EnumEntries;
import kotlin1822.enums.EnumEntriesKt;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\n\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006j\u0002\b\u0007j\u0002\b\bj\u0002\b\tj\u0002\b\n\u00a8\u0006\u000b"}, d2={"Link/ptms/chemdah/um/skill/SkillResult;", "", "(Ljava/lang/String;I)V", "SUCCESS", "ERROR", "REQUIRES_PREMIUM", "INVALID_VERSION", "INVALID_TARGET", "INVALID_CONFIG", "MISSING_COMPATIBILITY", "CONDITION_FAILED", "common"})
public final class SkillResult
extends Enum<SkillResult> {
    public static final /* enum */ SkillResult SUCCESS = new SkillResult();
    public static final /* enum */ SkillResult ERROR = new SkillResult();
    public static final /* enum */ SkillResult REQUIRES_PREMIUM = new SkillResult();
    public static final /* enum */ SkillResult INVALID_VERSION = new SkillResult();
    public static final /* enum */ SkillResult INVALID_TARGET = new SkillResult();
    public static final /* enum */ SkillResult INVALID_CONFIG = new SkillResult();
    public static final /* enum */ SkillResult MISSING_COMPATIBILITY = new SkillResult();
    public static final /* enum */ SkillResult CONDITION_FAILED = new SkillResult();
    private static final /* synthetic */ SkillResult[] $VALUES;
    private static final /* synthetic */ EnumEntries $ENTRIES;

    public static SkillResult[] values() {
        return (SkillResult[])$VALUES.clone();
    }

    public static SkillResult valueOf(String value2) {
        return Enum.valueOf(SkillResult.class, value2);
    }

    @NotNull
    public static EnumEntries<SkillResult> getEntries() {
        return $ENTRIES;
    }

    static {
        $VALUES = skillResultArray = new SkillResult[]{SkillResult.SUCCESS, SkillResult.ERROR, SkillResult.REQUIRES_PREMIUM, SkillResult.INVALID_VERSION, SkillResult.INVALID_TARGET, SkillResult.INVALID_CONFIG, SkillResult.MISSING_COMPATIBILITY, SkillResult.CONDITION_FAILED};
        $ENTRIES = EnumEntriesKt.enumEntries((Enum[])$VALUES);
    }
}

