/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.Ghost
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  io.lumine.xikage.mythicmobs.adapters.AbstractEntity
 *  io.lumine.xikage.mythicmobs.adapters.AbstractLocation
 *  io.lumine.xikage.mythicmobs.api.bukkit.events.MythicConditionLoadEvent
 *  io.lumine.xikage.mythicmobs.io.MythicLineConfig
 *  io.lumine.xikage.mythicmobs.skills.SkillCaster
 *  io.lumine.xikage.mythicmobs.skills.SkillCondition
 *  io.lumine.xikage.mythicmobs.skills.SkillMetadata
 *  io.lumine.xikage.mythicmobs.skills.conditions.ICasterCondition
 *  io.lumine.xikage.mythicmobs.skills.conditions.IEntityComparisonCondition
 *  io.lumine.xikage.mythicmobs.skills.conditions.IEntityCondition
 *  io.lumine.xikage.mythicmobs.skills.conditions.IEntityLocationComparisonCondition
 *  io.lumine.xikage.mythicmobs.skills.conditions.ILocationCondition
 *  io.lumine.xikage.mythicmobs.skills.conditions.ISkillMetaCondition
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.um.impl4;

import ink.ptms.chemdah.taboolib.common.platform.Ghost;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.um.event.MobConditionLoadEvent;
import ink.ptms.chemdah.um.impl4.UtilsKt;
import ink.ptms.chemdah.um.skill.condition.BaseCondition;
import ink.ptms.chemdah.um.skill.condition.CasterCondition;
import ink.ptms.chemdah.um.skill.condition.EntityComparisonCondition;
import ink.ptms.chemdah.um.skill.condition.EntityCondition;
import ink.ptms.chemdah.um.skill.condition.EntityLocationDistanceCondition;
import ink.ptms.chemdah.um.skill.condition.LocationCondition;
import ink.ptms.chemdah.um.skill.condition.SkillMetaComparisonCondition;
import ink.ptms.chemdah.um.skill.condition.SkillMetadataCondition;
import io.lumine.xikage.mythicmobs.adapters.AbstractEntity;
import io.lumine.xikage.mythicmobs.adapters.AbstractLocation;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicConditionLoadEvent;
import io.lumine.xikage.mythicmobs.io.MythicLineConfig;
import io.lumine.xikage.mythicmobs.skills.SkillCaster;
import io.lumine.xikage.mythicmobs.skills.SkillCondition;
import io.lumine.xikage.mythicmobs.skills.SkillMetadata;
import io.lumine.xikage.mythicmobs.skills.conditions.ICasterCondition;
import io.lumine.xikage.mythicmobs.skills.conditions.IEntityComparisonCondition;
import io.lumine.xikage.mythicmobs.skills.conditions.IEntityCondition;
import io.lumine.xikage.mythicmobs.skills.conditions.IEntityLocationComparisonCondition;
import io.lumine.xikage.mythicmobs.skills.conditions.ILocationCondition;
import io.lumine.xikage.mythicmobs.skills.conditions.ISkillMetaCondition;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0007\b\u00c0\u0002\u0018\u00002\u00020\u0001:\u0006\u0007\b\t\n\u000b\fB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/um/impl4/MobListenerCondition;", "", "()V", "onSkillConditionEvent", "", "event", "Lio/lumine/xikage/mythicmobs/api/bukkit/events/MythicConditionLoadEvent;", "Caster", "Entity", "EntityComparison", "EntityLocation", "Location", "SkillMeta", "implementation-v4"})
public final class MobListenerCondition {
    @NotNull
    public static final MobListenerCondition INSTANCE = new MobListenerCondition();

    private MobListenerCondition() {
    }

    @Ghost
    @SubscribeEvent
    public final void onSkillConditionEvent(@NotNull MythicConditionLoadEvent event) {
        Intrinsics.checkNotNullParameter((Object)event, (String)"event");
        MythicLineConfig config = event.getConfig();
        String string = config.getKey();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getKey(...)");
        Intrinsics.checkNotNull((Object)config);
        MobConditionLoadEvent e = new MobConditionLoadEvent(string, UtilsKt.toUniversal(config)).fire();
        BaseCondition baseCondition = e.getSkillCondition();
        if (baseCondition == null) {
            return;
        }
        BaseCondition registerCondition = baseCondition;
        if (!BaseCondition.Companion.isSubclass(registerCondition)) {
            throw new IllegalStateException(("Unsupported skill: " + registerCondition).toString());
        }
        BaseCondition baseCondition2 = registerCondition;
        if (baseCondition2 instanceof EntityCondition) {
            event.register((SkillCondition)new Entity((EntityCondition)registerCondition, config));
        } else if (baseCondition2 instanceof CasterCondition) {
            event.register((SkillCondition)new Caster((CasterCondition)registerCondition, config));
        } else if (baseCondition2 instanceof LocationCondition) {
            event.register((SkillCondition)new Location((LocationCondition)registerCondition, config));
        } else if (baseCondition2 instanceof SkillMetadataCondition) {
            event.register((SkillCondition)new SkillMeta((SkillMetadataCondition)registerCondition, config));
        } else if (baseCondition2 instanceof EntityComparisonCondition) {
            event.register((SkillCondition)new EntityComparison((EntityComparisonCondition)registerCondition, config));
        } else if (baseCondition2 instanceof EntityLocationDistanceCondition) {
            event.register((SkillCondition)new EntityLocation((EntityLocationDistanceCondition)registerCondition, config));
        } else if (baseCondition2 instanceof SkillMetaComparisonCondition) {
            throw new NullPointerException("\u5f53\u524dMM\u7248\u672c\u4e0d\u652f\u6301\u8fd9\u4e2a\u6761\u4ef6");
        }
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u00012\u00020\u0002B\u0015\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u0012\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\rH\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/um/impl4/MobListenerCondition$Caster;", "Lio/lumine/xikage/mythicmobs/skills/SkillCondition;", "Lio/lumine/xikage/mythicmobs/skills/conditions/ICasterCondition;", "condition", "Link/ptms/chemdah/um/skill/condition/CasterCondition;", "config", "Lio/lumine/xikage/mythicmobs/io/MythicLineConfig;", "(Link/ptms/chemdah/um/skill/condition/CasterCondition;Lio/lumine/xikage/mythicmobs/io/MythicLineConfig;)V", "getCondition", "()Link/ptms/chemdah/um/skill/condition/CasterCondition;", "check", "", "p0", "Lio/lumine/xikage/mythicmobs/skills/SkillCaster;", "implementation-v4"})
    public static final class Caster
    extends SkillCondition
    implements ICasterCondition {
        @NotNull
        private final CasterCondition condition;

        public Caster(@NotNull CasterCondition condition, @NotNull MythicLineConfig config) {
            Intrinsics.checkNotNullParameter((Object)condition, (String)"condition");
            Intrinsics.checkNotNullParameter((Object)config, (String)"config");
            super(config.getLine());
            this.condition = condition;
        }

        @NotNull
        public final CasterCondition getCondition() {
            return this.condition;
        }

        public boolean check(@Nullable SkillCaster p0) {
            SkillCaster skillCaster = p0;
            return this.condition.check(skillCaster != null ? UtilsKt.toUniversal(skillCaster) : null);
        }
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u00012\u00020\u0002B\u0015\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u0012\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\rH\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/um/impl4/MobListenerCondition$Entity;", "Lio/lumine/xikage/mythicmobs/skills/SkillCondition;", "Lio/lumine/xikage/mythicmobs/skills/conditions/IEntityCondition;", "condition", "Link/ptms/chemdah/um/skill/condition/EntityCondition;", "config", "Lio/lumine/xikage/mythicmobs/io/MythicLineConfig;", "(Link/ptms/chemdah/um/skill/condition/EntityCondition;Lio/lumine/xikage/mythicmobs/io/MythicLineConfig;)V", "getCondition", "()Link/ptms/chemdah/um/skill/condition/EntityCondition;", "check", "", "p0", "Lio/lumine/xikage/mythicmobs/adapters/AbstractEntity;", "implementation-v4"})
    public static final class Entity
    extends SkillCondition
    implements IEntityCondition {
        @NotNull
        private final EntityCondition condition;

        public Entity(@NotNull EntityCondition condition, @NotNull MythicLineConfig config) {
            Intrinsics.checkNotNullParameter((Object)condition, (String)"condition");
            Intrinsics.checkNotNullParameter((Object)config, (String)"config");
            super(config.getLine());
            this.condition = condition;
        }

        @NotNull
        public final EntityCondition getCondition() {
            return this.condition;
        }

        public boolean check(@Nullable AbstractEntity p0) {
            AbstractEntity abstractEntity = p0;
            return this.condition.check((org.bukkit.entity.Entity)(abstractEntity != null ? abstractEntity.getBukkitEntity() : null));
        }
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u00012\u00020\u0002B\u0015\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u001c\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\r2\b\u0010\u000e\u001a\u0004\u0018\u00010\rH\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/um/impl4/MobListenerCondition$EntityComparison;", "Lio/lumine/xikage/mythicmobs/skills/SkillCondition;", "Lio/lumine/xikage/mythicmobs/skills/conditions/IEntityComparisonCondition;", "condition", "Link/ptms/chemdah/um/skill/condition/EntityComparisonCondition;", "config", "Lio/lumine/xikage/mythicmobs/io/MythicLineConfig;", "(Link/ptms/chemdah/um/skill/condition/EntityComparisonCondition;Lio/lumine/xikage/mythicmobs/io/MythicLineConfig;)V", "getCondition", "()Link/ptms/chemdah/um/skill/condition/EntityComparisonCondition;", "check", "", "p0", "Lio/lumine/xikage/mythicmobs/adapters/AbstractEntity;", "p1", "implementation-v4"})
    public static final class EntityComparison
    extends SkillCondition
    implements IEntityComparisonCondition {
        @NotNull
        private final EntityComparisonCondition condition;

        public EntityComparison(@NotNull EntityComparisonCondition condition, @NotNull MythicLineConfig config) {
            Intrinsics.checkNotNullParameter((Object)condition, (String)"condition");
            Intrinsics.checkNotNullParameter((Object)config, (String)"config");
            super(config.getLine());
            this.condition = condition;
        }

        @NotNull
        public final EntityComparisonCondition getCondition() {
            return this.condition;
        }

        public boolean check(@Nullable AbstractEntity p0, @Nullable AbstractEntity p1) {
            AbstractEntity abstractEntity = p0;
            AbstractEntity abstractEntity2 = p1;
            return this.condition.check((org.bukkit.entity.Entity)(abstractEntity != null ? abstractEntity.getBukkitEntity() : null), (org.bukkit.entity.Entity)(abstractEntity2 != null ? abstractEntity2.getBukkitEntity() : null));
        }
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u00012\u00020\u0002B\u0015\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u001c\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\r2\b\u0010\u000e\u001a\u0004\u0018\u00010\u000fH\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/um/impl4/MobListenerCondition$EntityLocation;", "Lio/lumine/xikage/mythicmobs/skills/SkillCondition;", "Lio/lumine/xikage/mythicmobs/skills/conditions/IEntityLocationComparisonCondition;", "condition", "Link/ptms/chemdah/um/skill/condition/EntityLocationDistanceCondition;", "config", "Lio/lumine/xikage/mythicmobs/io/MythicLineConfig;", "(Link/ptms/chemdah/um/skill/condition/EntityLocationDistanceCondition;Lio/lumine/xikage/mythicmobs/io/MythicLineConfig;)V", "getCondition", "()Link/ptms/chemdah/um/skill/condition/EntityLocationDistanceCondition;", "check", "", "p0", "Lio/lumine/xikage/mythicmobs/adapters/AbstractEntity;", "p1", "Lio/lumine/xikage/mythicmobs/adapters/AbstractLocation;", "implementation-v4"})
    public static final class EntityLocation
    extends SkillCondition
    implements IEntityLocationComparisonCondition {
        @NotNull
        private final EntityLocationDistanceCondition condition;

        public EntityLocation(@NotNull EntityLocationDistanceCondition condition, @NotNull MythicLineConfig config) {
            Intrinsics.checkNotNullParameter((Object)condition, (String)"condition");
            Intrinsics.checkNotNullParameter((Object)config, (String)"config");
            super(config.getLine());
            this.condition = condition;
        }

        @NotNull
        public final EntityLocationDistanceCondition getCondition() {
            return this.condition;
        }

        public boolean check(@Nullable AbstractEntity p0, @Nullable AbstractLocation p1) {
            AbstractEntity abstractEntity = p0;
            AbstractLocation abstractLocation = p1;
            return this.condition.check((org.bukkit.entity.Entity)(abstractEntity != null ? abstractEntity.getBukkitEntity() : null), (org.bukkit.Location)(abstractLocation != null ? UtilsKt.toBukkit(abstractLocation) : null));
        }
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u00012\u00020\u0002B\u0015\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u0012\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\rH\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/um/impl4/MobListenerCondition$Location;", "Lio/lumine/xikage/mythicmobs/skills/SkillCondition;", "Lio/lumine/xikage/mythicmobs/skills/conditions/ILocationCondition;", "condition", "Link/ptms/chemdah/um/skill/condition/LocationCondition;", "config", "Lio/lumine/xikage/mythicmobs/io/MythicLineConfig;", "(Link/ptms/chemdah/um/skill/condition/LocationCondition;Lio/lumine/xikage/mythicmobs/io/MythicLineConfig;)V", "getCondition", "()Link/ptms/chemdah/um/skill/condition/LocationCondition;", "check", "", "p0", "Lio/lumine/xikage/mythicmobs/adapters/AbstractLocation;", "implementation-v4"})
    public static final class Location
    extends SkillCondition
    implements ILocationCondition {
        @NotNull
        private final LocationCondition condition;

        public Location(@NotNull LocationCondition condition, @NotNull MythicLineConfig config) {
            Intrinsics.checkNotNullParameter((Object)condition, (String)"condition");
            Intrinsics.checkNotNullParameter((Object)config, (String)"config");
            super(config.getLine());
            this.condition = condition;
        }

        @NotNull
        public final LocationCondition getCondition() {
            return this.condition;
        }

        public boolean check(@Nullable AbstractLocation p0) {
            AbstractLocation abstractLocation = p0;
            return this.condition.check((org.bukkit.Location)(abstractLocation != null ? UtilsKt.toBukkit(abstractLocation) : null));
        }
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u00012\u00020\u0002B\u0015\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u0012\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\rH\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/um/impl4/MobListenerCondition$SkillMeta;", "Lio/lumine/xikage/mythicmobs/skills/SkillCondition;", "Lio/lumine/xikage/mythicmobs/skills/conditions/ISkillMetaCondition;", "condition", "Link/ptms/chemdah/um/skill/condition/SkillMetadataCondition;", "config", "Lio/lumine/xikage/mythicmobs/io/MythicLineConfig;", "(Link/ptms/chemdah/um/skill/condition/SkillMetadataCondition;Lio/lumine/xikage/mythicmobs/io/MythicLineConfig;)V", "getCondition", "()Link/ptms/chemdah/um/skill/condition/SkillMetadataCondition;", "check", "", "p0", "Lio/lumine/xikage/mythicmobs/skills/SkillMetadata;", "implementation-v4"})
    public static final class SkillMeta
    extends SkillCondition
    implements ISkillMetaCondition {
        @NotNull
        private final SkillMetadataCondition condition;

        public SkillMeta(@NotNull SkillMetadataCondition condition, @NotNull MythicLineConfig config) {
            Intrinsics.checkNotNullParameter((Object)condition, (String)"condition");
            Intrinsics.checkNotNullParameter((Object)config, (String)"config");
            super(config.getLine());
            this.condition = condition;
        }

        @NotNull
        public final SkillMetadataCondition getCondition() {
            return this.condition;
        }

        public boolean check(@Nullable SkillMetadata p0) {
            SkillMetadata skillMetadata = p0;
            return this.condition.check(skillMetadata != null ? UtilsKt.toUniversal(skillMetadata) : null);
        }
    }
}

