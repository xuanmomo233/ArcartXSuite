/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.Ghost
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  io.lumine.mythic.api.adapters.AbstractEntity
 *  io.lumine.mythic.api.adapters.AbstractLocation
 *  io.lumine.mythic.api.config.MythicLineConfig
 *  io.lumine.mythic.api.skills.INoTargetSkill
 *  io.lumine.mythic.api.skills.ISkillMechanic
 *  io.lumine.mythic.api.skills.ITargetedEntitySkill
 *  io.lumine.mythic.api.skills.ITargetedLocationSkill
 *  io.lumine.mythic.api.skills.SkillMetadata
 *  io.lumine.mythic.api.skills.SkillResult
 *  io.lumine.mythic.bukkit.MythicBukkit
 *  io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent
 *  io.lumine.mythic.core.skills.SkillExecutor
 *  io.lumine.mythic.core.skills.SkillMechanic
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Entity
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.um.impl5;

import ink.ptms.chemdah.taboolib.common.platform.Ghost;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.um.event.MobSkillLoadEvent;
import ink.ptms.chemdah.um.impl5.UtilsKt;
import ink.ptms.chemdah.um.skill.SkillMeta;
import ink.ptms.chemdah.um.skill.type.BaseSkill;
import ink.ptms.chemdah.um.skill.type.EntityTargetSkill;
import ink.ptms.chemdah.um.skill.type.LocationTargetSkill;
import ink.ptms.chemdah.um.skill.type.NoTargetSkill;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.INoTargetSkill;
import io.lumine.mythic.api.skills.ISkillMechanic;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.ITargetedLocationSkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.skills.SkillExecutor;
import io.lumine.mythic.core.skills.SkillMechanic;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c0\u0002\u0018\u00002\u00020\u0001:\u0001\u0007B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007\u00a8\u0006\b"}, d2={"Link/ptms/chemdah/um/impl5/MobListenerSkill;", "", "()V", "onDropLoadEvent", "", "event", "Lio/lumine/mythic/bukkit/events/MythicMechanicLoadEvent;", "ProxySkill", "implementation-v5"})
public final class MobListenerSkill {
    @NotNull
    public static final MobListenerSkill INSTANCE = new MobListenerSkill();

    private MobListenerSkill() {
    }

    @Ghost
    @SubscribeEvent
    public final void onDropLoadEvent(@NotNull MythicMechanicLoadEvent event) {
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        String string = event.getMechanicName();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getMechanicName(...)");
        MythicLineConfig mythicLineConfig = event.getConfig();
        Intrinsics.checkNotNullExpressionValue((Object)mythicLineConfig, (String)"getConfig(...)");
        MobSkillLoadEvent e = new MobSkillLoadEvent(string, UtilsKt.toUniversal(mythicLineConfig)).fire();
        BaseSkill baseSkill = e.getRegisterSkill();
        if (baseSkill == null) {
            return;
        }
        BaseSkill registerSkill = baseSkill;
        if (!(registerSkill instanceof EntityTargetSkill || registerSkill instanceof LocationTargetSkill || registerSkill instanceof NoTargetSkill)) {
            throw new IllegalStateException(("Unsupported skill: " + registerSkill).toString());
        }
        SkillExecutor skillExecutor = MythicBukkit.inst().getSkillManager();
        Intrinsics.checkNotNullExpressionValue((Object)skillExecutor, (String)"getSkillManager(...)");
        String string2 = event.getMechanicName();
        Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"getMechanicName(...)");
        MythicLineConfig mythicLineConfig2 = event.getConfig();
        Intrinsics.checkNotNullExpressionValue((Object)mythicLineConfig2, (String)"getConfig(...)");
        event.register((ISkillMechanic)new ProxySkill(registerSkill, skillExecutor, string2, mythicLineConfig2));
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000R\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u00012\u00020\u00022\u00020\u00032\u00020\u0004B%\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\u0006\u0010\u0007\u001a\u00020\b\u0012\u0006\u0010\t\u001a\u00020\n\u0012\u0006\u0010\u000b\u001a\u00020\f\u00a2\u0006\u0002\u0010\rJ\u0010\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0013H\u0016J\u0018\u0010\u0014\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0015\u001a\u00020\u0016H\u0016J\u0018\u0010\u0017\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0018\u001a\u00020\u0019H\u0016J\f\u0010\u001a\u001a\u00020\u0011*\u00020\u001bH\u0002R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u001c"}, d2={"Link/ptms/chemdah/um/impl5/MobListenerSkill$ProxySkill;", "Lio/lumine/mythic/core/skills/SkillMechanic;", "Lio/lumine/mythic/api/skills/ITargetedEntitySkill;", "Lio/lumine/mythic/api/skills/ITargetedLocationSkill;", "Lio/lumine/mythic/api/skills/INoTargetSkill;", "skill", "Link/ptms/chemdah/um/skill/type/BaseSkill;", "manager", "Lio/lumine/mythic/core/skills/SkillExecutor;", "name", "", "mlc", "Lio/lumine/mythic/api/config/MythicLineConfig;", "(Link/ptms/chemdah/um/skill/type/BaseSkill;Lio/lumine/mythic/core/skills/SkillExecutor;Ljava/lang/String;Lio/lumine/mythic/api/config/MythicLineConfig;)V", "getSkill", "()Link/ptms/chemdah/um/skill/type/BaseSkill;", "cast", "Lio/lumine/mythic/api/skills/SkillResult;", "metadata", "Lio/lumine/mythic/api/skills/SkillMetadata;", "castAtEntity", "entity", "Lio/lumine/mythic/api/adapters/AbstractEntity;", "castAtLocation", "location", "Lio/lumine/mythic/api/adapters/AbstractLocation;", "toMythic", "Link/ptms/chemdah/um/skill/SkillResult;", "implementation-v5"})
    public static final class ProxySkill
    extends SkillMechanic
    implements ITargetedEntitySkill,
    ITargetedLocationSkill,
    INoTargetSkill {
        @NotNull
        private final BaseSkill skill;

        public ProxySkill(@NotNull BaseSkill skill, @NotNull SkillExecutor manager, @NotNull String name, @NotNull MythicLineConfig mlc) {
            Intrinsics.checkNotNullParameter((Object)skill, (String)"skill");
            Intrinsics.checkNotNullParameter((Object)manager, (String)"manager");
            Intrinsics.checkNotNullParameter((Object)name, (String)"name");
            Intrinsics.checkNotNullParameter((Object)mlc, (String)"mlc");
            super(manager, name, mlc);
            this.skill = skill;
        }

        @NotNull
        public final BaseSkill getSkill() {
            return this.skill;
        }

        @NotNull
        public SkillResult castAtEntity(@NotNull SkillMetadata metadata, @NotNull AbstractEntity entity) {
            Intrinsics.checkNotNullParameter((Object)metadata, (String)"metadata");
            Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
            if (this.skill instanceof EntityTargetSkill) {
                EntityTargetSkill entityTargetSkill = (EntityTargetSkill)this.skill;
                SkillMeta skillMeta = UtilsKt.toUniversal(metadata);
                Entity entity2 = entity.getBukkitEntity();
                Intrinsics.checkNotNullExpressionValue((Object)entity2, (String)"getBukkitEntity(...)");
                return this.toMythic(entityTargetSkill.cast(skillMeta, entity2));
            }
            Object[] objectArray = new Object[]{this.skill + " is not ITargetedEntitySkill"};
            IOKt.warning((Object[])objectArray);
            return SkillResult.ERROR;
        }

        @NotNull
        public SkillResult castAtLocation(@NotNull SkillMetadata metadata, @NotNull AbstractLocation location) {
            Intrinsics.checkNotNullParameter((Object)metadata, (String)"metadata");
            Intrinsics.checkNotNullParameter((Object)location, (String)"location");
            if (this.skill instanceof LocationTargetSkill) {
                return this.toMythic(((LocationTargetSkill)this.skill).cast(UtilsKt.toUniversal(metadata), UtilsKt.toBukkit(location)));
            }
            Object[] objectArray = new Object[]{this.skill + " is not ITargetedLocationSkill"};
            IOKt.warning((Object[])objectArray);
            return SkillResult.ERROR;
        }

        @NotNull
        public SkillResult cast(@NotNull SkillMetadata metadata) {
            Intrinsics.checkNotNullParameter((Object)metadata, (String)"metadata");
            if (this.skill instanceof NoTargetSkill) {
                return this.toMythic(((NoTargetSkill)this.skill).cast(UtilsKt.toUniversal(metadata)));
            }
            Object[] objectArray = new Object[]{this.skill + " is not INoTargetSkill"};
            IOKt.warning((Object[])objectArray);
            return SkillResult.ERROR;
        }

        private final SkillResult toMythic(ink.ptms.chemdah.um.skill.SkillResult $this$toMythic) {
            SkillResult skillResult;
            try {
                skillResult = SkillResult.values()[$this$toMythic.ordinal()];
            }
            catch (Throwable _) {
                skillResult = SkillResult.ERROR;
            }
            return skillResult;
        }
    }
}

