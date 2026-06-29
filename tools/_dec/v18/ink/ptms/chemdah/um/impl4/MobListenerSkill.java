/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.Ghost
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  io.lumine.xikage.mythicmobs.adapters.AbstractEntity
 *  io.lumine.xikage.mythicmobs.adapters.AbstractLocation
 *  io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMechanicLoadEvent
 *  io.lumine.xikage.mythicmobs.io.MythicLineConfig
 *  io.lumine.xikage.mythicmobs.skills.INoTargetSkill
 *  io.lumine.xikage.mythicmobs.skills.ITargetedEntitySkill
 *  io.lumine.xikage.mythicmobs.skills.ITargetedLocationSkill
 *  io.lumine.xikage.mythicmobs.skills.SkillMechanic
 *  io.lumine.xikage.mythicmobs.skills.SkillMetadata
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.entity.Entity
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.um.impl4;

import ink.ptms.chemdah.taboolib.common.platform.Ghost;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.um.event.MobSkillLoadEvent;
import ink.ptms.chemdah.um.impl4.UtilsKt;
import ink.ptms.chemdah.um.skill.SkillMeta;
import ink.ptms.chemdah.um.skill.SkillResult;
import ink.ptms.chemdah.um.skill.type.BaseSkill;
import ink.ptms.chemdah.um.skill.type.EntityTargetSkill;
import ink.ptms.chemdah.um.skill.type.LocationTargetSkill;
import ink.ptms.chemdah.um.skill.type.NoTargetSkill;
import io.lumine.xikage.mythicmobs.adapters.AbstractEntity;
import io.lumine.xikage.mythicmobs.adapters.AbstractLocation;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.xikage.mythicmobs.io.MythicLineConfig;
import io.lumine.xikage.mythicmobs.skills.INoTargetSkill;
import io.lumine.xikage.mythicmobs.skills.ITargetedEntitySkill;
import io.lumine.xikage.mythicmobs.skills.ITargetedLocationSkill;
import io.lumine.xikage.mythicmobs.skills.SkillMechanic;
import io.lumine.xikage.mythicmobs.skills.SkillMetadata;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c0\u0002\u0018\u00002\u00020\u0001:\u0001\u0007B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007\u00a8\u0006\b"}, d2={"Link/ptms/chemdah/um/impl4/MobListenerSkill;", "", "()V", "onDropLoadEvent", "", "event", "Lio/lumine/xikage/mythicmobs/api/bukkit/events/MythicMechanicLoadEvent;", "ProxySkill", "implementation-v4"})
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
        String string2 = event.getMechanicName();
        Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"getMechanicName(...)");
        MythicLineConfig mythicLineConfig2 = event.getConfig();
        Intrinsics.checkNotNullExpressionValue((Object)mythicLineConfig2, (String)"getConfig(...)");
        event.register((SkillMechanic)new ProxySkill(registerSkill, string2, mythicLineConfig2));
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000F\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u00012\u00020\u00022\u00020\u00032\u00020\u0004B\u001d\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\u0006\u0010\u0007\u001a\u00020\b\u0012\u0006\u0010\t\u001a\u00020\n\u00a2\u0006\u0002\u0010\u000bJ\u0010\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u0011H\u0016J\u0018\u0010\u0012\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0013\u001a\u00020\u0014H\u0016J\u0018\u0010\u0015\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0016\u001a\u00020\u0017H\u0016R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\r\u00a8\u0006\u0018"}, d2={"Link/ptms/chemdah/um/impl4/MobListenerSkill$ProxySkill;", "Lio/lumine/xikage/mythicmobs/skills/SkillMechanic;", "Lio/lumine/xikage/mythicmobs/skills/ITargetedEntitySkill;", "Lio/lumine/xikage/mythicmobs/skills/ITargetedLocationSkill;", "Lio/lumine/xikage/mythicmobs/skills/INoTargetSkill;", "skill", "Link/ptms/chemdah/um/skill/type/BaseSkill;", "name", "", "mlc", "Lio/lumine/xikage/mythicmobs/io/MythicLineConfig;", "(Link/ptms/chemdah/um/skill/type/BaseSkill;Ljava/lang/String;Lio/lumine/xikage/mythicmobs/io/MythicLineConfig;)V", "getSkill", "()Link/ptms/chemdah/um/skill/type/BaseSkill;", "cast", "", "metadata", "Lio/lumine/xikage/mythicmobs/skills/SkillMetadata;", "castAtEntity", "entity", "Lio/lumine/xikage/mythicmobs/adapters/AbstractEntity;", "castAtLocation", "location", "Lio/lumine/xikage/mythicmobs/adapters/AbstractLocation;", "implementation-v4"})
    public static final class ProxySkill
    extends SkillMechanic
    implements ITargetedEntitySkill,
    ITargetedLocationSkill,
    INoTargetSkill {
        @NotNull
        private final BaseSkill skill;

        public ProxySkill(@NotNull BaseSkill skill, @NotNull String name, @NotNull MythicLineConfig mlc) {
            Intrinsics.checkNotNullParameter((Object)skill, (String)"skill");
            Intrinsics.checkNotNullParameter((Object)name, (String)"name");
            Intrinsics.checkNotNullParameter((Object)mlc, (String)"mlc");
            super(name, mlc);
            this.skill = skill;
        }

        @NotNull
        public final BaseSkill getSkill() {
            return this.skill;
        }

        public boolean castAtEntity(@NotNull SkillMetadata metadata, @NotNull AbstractEntity entity) {
            Intrinsics.checkNotNullParameter((Object)metadata, (String)"metadata");
            Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
            if (this.skill instanceof EntityTargetSkill) {
                EntityTargetSkill entityTargetSkill = (EntityTargetSkill)this.skill;
                SkillMeta skillMeta = UtilsKt.toUniversal(metadata);
                Entity entity2 = entity.getBukkitEntity();
                Intrinsics.checkNotNullExpressionValue((Object)entity2, (String)"getBukkitEntity(...)");
                return entityTargetSkill.cast(skillMeta, entity2) == SkillResult.SUCCESS;
            }
            Object[] objectArray = new Object[]{this.skill + " is not ITargetedEntitySkill"};
            IOKt.warning((Object[])objectArray);
            return false;
        }

        public boolean castAtLocation(@NotNull SkillMetadata metadata, @NotNull AbstractLocation location) {
            Intrinsics.checkNotNullParameter((Object)metadata, (String)"metadata");
            Intrinsics.checkNotNullParameter((Object)location, (String)"location");
            if (this.skill instanceof LocationTargetSkill) {
                return ((LocationTargetSkill)this.skill).cast(UtilsKt.toUniversal(metadata), UtilsKt.toBukkit(location)) == SkillResult.SUCCESS;
            }
            Object[] objectArray = new Object[]{this.skill + " is not ITargetedLocationSkill"};
            IOKt.warning((Object[])objectArray);
            return false;
        }

        public boolean cast(@NotNull SkillMetadata metadata) {
            Intrinsics.checkNotNullParameter((Object)metadata, (String)"metadata");
            if (this.skill instanceof NoTargetSkill) {
                return ((NoTargetSkill)this.skill).cast(UtilsKt.toUniversal(metadata)) == SkillResult.SUCCESS;
            }
            Object[] objectArray = new Object[]{this.skill + " is not INoTargetSkill"};
            IOKt.warning((Object[])objectArray);
            return false;
        }
    }
}

