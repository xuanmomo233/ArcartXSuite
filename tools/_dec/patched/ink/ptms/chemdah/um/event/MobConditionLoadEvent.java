/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.um.event;

import ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent;
import ink.ptms.chemdah.um.skill.SkillConfig;
import ink.ptms.chemdah.um.skill.condition.BaseCondition;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0006\u0010\u0011\u001a\u00020\u0000J\u000e\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\fR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u001c\u0010\u000b\u001a\u0004\u0018\u00010\fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u000e\"\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0015"}, d2={"Link/ptms/chemdah/um/event/MobConditionLoadEvent;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "name", "", "config", "Link/ptms/chemdah/um/skill/SkillConfig;", "(Ljava/lang/String;Link/ptms/chemdah/um/skill/SkillConfig;)V", "getConfig", "()Link/ptms/chemdah/um/skill/SkillConfig;", "getName", "()Ljava/lang/String;", "skillCondition", "Link/ptms/chemdah/um/skill/condition/BaseCondition;", "getSkillCondition", "()Link/ptms/chemdah/um/skill/condition/BaseCondition;", "setSkillCondition", "(Link/ptms/chemdah/um/skill/condition/BaseCondition;)V", "fire", "register", "", "condition", "common"})
public final class MobConditionLoadEvent
extends BukkitProxyEvent {
    @NotNull
    private final String name;
    @NotNull
    private final SkillConfig config;
    @Nullable
    private BaseCondition skillCondition;

    public MobConditionLoadEvent(@NotNull String name, @NotNull SkillConfig config) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        this.name = name;
        this.config = config;
    }

    @NotNull
    public final String getName() {
        return this.name;
    }

    @NotNull
    public final SkillConfig getConfig() {
        return this.config;
    }

    @Nullable
    public final BaseCondition getSkillCondition() {
        return this.skillCondition;
    }

    public final void setSkillCondition(@Nullable BaseCondition baseCondition) {
        this.skillCondition = baseCondition;
    }

    public final void register(@NotNull BaseCondition condition) {
        Intrinsics.checkNotNullParameter((Object)condition, (String)"condition");
        this.skillCondition = condition;
    }

    @NotNull
    public final MobConditionLoadEvent fire() {
        this.call();
        return this;
    }
}

