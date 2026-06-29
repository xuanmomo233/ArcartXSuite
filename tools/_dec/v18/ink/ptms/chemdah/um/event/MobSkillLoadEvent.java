/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.um.event;

import ink.ptms.chemdah.taboolib.platform.type.BukkitProxyEvent;
import ink.ptms.chemdah.um.skill.SkillConfig;
import ink.ptms.chemdah.um.skill.type.BaseSkill;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u00006\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0011\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0006\u0010\u0011\u001a\u00020\u0000J\u001f\u0010\u0012\u001a\u00020\u00132\u0012\u0010\u0014\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00030\u0015\"\u00020\u0003\u00a2\u0006\u0002\u0010\u0016J\u000e\u0010\u0017\u001a\u00020\u00182\u0006\u0010\u0019\u001a\u00020\nR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u001c\u0010\t\u001a\u0004\u0018\u00010\nX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\f\"\u0004\b\r\u0010\u000eR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u001a"}, d2={"Link/ptms/chemdah/um/event/MobSkillLoadEvent;", "Link/ptms/chemdah/taboolib/platform/type/BukkitProxyEvent;", "skillName", "", "config", "Link/ptms/chemdah/um/skill/SkillConfig;", "(Ljava/lang/String;Link/ptms/chemdah/um/skill/SkillConfig;)V", "getConfig", "()Link/ptms/chemdah/um/skill/SkillConfig;", "registerSkill", "Link/ptms/chemdah/um/skill/type/BaseSkill;", "getRegisterSkill", "()Link/ptms/chemdah/um/skill/type/BaseSkill;", "setRegisterSkill", "(Link/ptms/chemdah/um/skill/type/BaseSkill;)V", "getSkillName", "()Ljava/lang/String;", "fire", "nameIs", "", "name", "", "([Ljava/lang/String;)Z", "register", "", "skill", "common"})
@SourceDebugExtension(value={"SMAP\nMobSkillLoadEvent.kt\nKotlin\n*S Kotlin\n*F\n+ 1 MobSkillLoadEvent.kt\nink/ptms/um/event/MobSkillLoadEvent\n+ 2 _Arrays.kt\nkotlin/collections/ArraysKt___ArraysKt\n*L\n1#1,46:1\n12474#2,2:47\n*S KotlinDebug\n*F\n+ 1 MobSkillLoadEvent.kt\nink/ptms/um/event/MobSkillLoadEvent\n*L\n23#1:47,2\n*E\n"})
public final class MobSkillLoadEvent
extends BukkitProxyEvent {
    @NotNull
    private final String skillName;
    @NotNull
    private final SkillConfig config;
    @Nullable
    private BaseSkill registerSkill;

    public MobSkillLoadEvent(@NotNull String skillName, @NotNull SkillConfig config) {
        Intrinsics.checkNotNullParameter((Object)skillName, (String)"skillName");
        Intrinsics.checkNotNullParameter((Object)config, (String)"config");
        this.skillName = skillName;
        this.config = config;
    }

    @NotNull
    public final String getSkillName() {
        return this.skillName;
    }

    @NotNull
    public final SkillConfig getConfig() {
        return this.config;
    }

    @Nullable
    public final BaseSkill getRegisterSkill() {
        return this.registerSkill;
    }

    public final void setRegisterSkill(@Nullable BaseSkill baseSkill) {
        this.registerSkill = baseSkill;
    }

    public final void register(@NotNull BaseSkill skill) {
        Intrinsics.checkNotNullParameter((Object)skill, (String)"skill");
        this.registerSkill = skill;
    }

    public final boolean nameIs(String ... name) {
        boolean bl;
        block1: {
            Intrinsics.checkNotNullParameter((Object)name, (String)"name");
            String[] $this$any$iv = name;
            boolean $i$f$any = false;
            int n = $this$any$iv.length;
            for (int i = 0; i < n; ++i) {
                String element$iv;
                String it = element$iv = $this$any$iv[i];
                boolean bl2 = false;
                if (!StringsKt.equals((String)it, (String)this.skillName, (boolean)true)) continue;
                bl = true;
                break block1;
            }
            bl = false;
        }
        return bl;
    }

    @NotNull
    public final MobSkillLoadEvent fire() {
        this.call();
        return this;
    }
}

