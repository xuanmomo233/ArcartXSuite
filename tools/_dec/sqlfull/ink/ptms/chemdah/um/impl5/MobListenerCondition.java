/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.um.impl5;

import ink.ptms.chemdah.taboolib.common.platform.Ghost;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.um.event.MobConditionLoadEvent;
import ink.ptms.chemdah.um.impl5.UtilsKt;
import ink.ptms.chemdah.um.skill.condition.BaseCondition;
import ink.ptms.chemdah.um.skill.condition.CasterCondition;
import ink.ptms.chemdah.um.skill.condition.EntityComparisonCondition;
import ink.ptms.chemdah.um.skill.condition.EntityCondition;
import ink.ptms.chemdah.um.skill.condition.EntityLocationDistanceCondition;
import ink.ptms.chemdah.um.skill.condition.LocationCondition;
import ink.ptms.chemdah.um.skill.condition.SkillMetaComparisonCondition;
import ink.ptms.chemdah.um.skill.condition.SkillMetadataCondition;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.conditions.ICasterCondition;
import io.lumine.mythic.api.skills.conditions.IEntityComparisonCondition;
import io.lumine.mythic.api.skills.conditions.IEntityCondition;
import io.lumine.mythic.api.skills.conditions.IEntityLocationComparisonCondition;
import io.lumine.mythic.api.skills.conditions.ILocationCondition;
import io.lumine.mythic.api.skills.conditions.ISkillCondition;
import io.lumine.mythic.api.skills.conditions.ISkillMetaComparisonCondition;
import io.lumine.mythic.api.skills.conditions.ISkillMetaCondition;
import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import io.lumine.mythic.core.skills.SkillCondition;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\b\b\u00c0\u0002\u0018\u00002\u00020\u0001:\u0007\u0007\b\t\n\u000b\f\rB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/um/impl5/MobListenerCondition;", "", "()V", "onMythicConditionLoad", "", "event", "Lio/lumine/mythic/bukkit/events/MythicConditionLoadEvent;", "Caster", "Entity", "EntityComparison", "EntityLocation", "Location", "SkillMeta", "SkillMetaComparison", "implementation-v5"})
public final class MobListenerCondition {
    @NotNull
    public static final MobListenerCondition INSTANCE = new MobListenerCondition();

    private MobListenerCondition() {
    }

    @Ghost
    @SubscribeEvent
    public final void onMythicConditionLoad(@NotNull MythicConditionLoadEvent event) {
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
            event.register((ISkillCondition)new Entity((EntityCondition)registerCondition, config));
        } else if (baseCondition2 instanceof CasterCondition) {
            event.register((ISkillCondition)new Caster((CasterCondition)registerCondition, config));
        } else if (baseCondition2 instanceof LocationCondition) {
            event.register((ISkillCondition)new Location((LocationCondition)registerCondition, config));
        } else if (baseCondition2 instanceof SkillMetadataCondition) {
            event.register((ISkillCondition)new SkillMeta((SkillMetadataCondition)registerCondition, config));
        } else if (baseCondition2 instanceof EntityComparisonCondition) {
            event.register((ISkillCondition)new EntityComparison((EntityComparisonCondition)registerCondition, config));
        } else if (baseCondition2 instanceof EntityLocationDistanceCondition) {
            event.register((ISkillCondition)new EntityLocation((EntityLocationDistanceCondition)registerCondition, config));
        } else if (baseCondition2 instanceof SkillMetaComparisonCondition) {
            event.register((ISkillCondition)new SkillMetaComparison((SkillMetaComparisonCondition)registerCondition, config));
        }
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u00012\u00020\u0002B\u0015\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u0012\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\rH\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/um/impl5/MobListenerCondition$Caster;", "Lio/lumine/mythic/core/skills/SkillCondition;", "Lio/lumine/mythic/api/skills/conditions/ICasterCondition;", "condition", "Link/ptms/chemdah/um/skill/condition/CasterCondition;", "config", "Lio/lumine/mythic/api/config/MythicLineConfig;", "(Link/ptms/chemdah/um/skill/condition/CasterCondition;Lio/lumine/mythic/api/config/MythicLineConfig;)V", "getCondition", "()Link/ptms/chemdah/um/skill/condition/CasterCondition;", "check", "", "p0", "Lio/lumine/mythic/api/skills/SkillCaster;", "implementation-v5"})
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

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u00012\u00020\u0002B\u0015\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u0012\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\rH\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/um/impl5/MobListenerCondition$Entity;", "Lio/lumine/mythic/core/skills/SkillCondition;", "Lio/lumine/mythic/api/skills/conditions/IEntityCondition;", "condition", "Link/ptms/chemdah/um/skill/condition/EntityCondition;", "config", "Lio/lumine/mythic/api/config/MythicLineConfig;", "(Link/ptms/chemdah/um/skill/condition/EntityCondition;Lio/lumine/mythic/api/config/MythicLineConfig;)V", "getCondition", "()Link/ptms/chemdah/um/skill/condition/EntityCondition;", "check", "", "p0", "Lio/lumine/mythic/api/adapters/AbstractEntity;", "implementation-v5"})
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

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u00012\u00020\u0002B\u0015\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u001c\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\r2\b\u0010\u000e\u001a\u0004\u0018\u00010\rH\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\u000f"}, d2={"Link/ptms/chemdah/um/impl5/MobListenerCondition$EntityComparison;", "Lio/lumine/mythic/core/skills/SkillCondition;", "Lio/lumine/mythic/api/skills/conditions/IEntityComparisonCondition;", "condition", "Link/ptms/chemdah/um/skill/condition/EntityComparisonCondition;", "config", "Lio/lumine/mythic/api/config/MythicLineConfig;", "(Link/ptms/chemdah/um/skill/condition/EntityComparisonCondition;Lio/lumine/mythic/api/config/MythicLineConfig;)V", "getCondition", "()Link/ptms/chemdah/um/skill/condition/EntityComparisonCondition;", "check", "", "p0", "Lio/lumine/mythic/api/adapters/AbstractEntity;", "p1", "implementation-v5"})
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

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u00012\u00020\u0002B\u0015\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u001c\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\r2\b\u0010\u000e\u001a\u0004\u0018\u00010\u000fH\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/um/impl5/MobListenerCondition$EntityLocation;", "Lio/lumine/mythic/core/skills/SkillCondition;", "Lio/lumine/mythic/api/skills/conditions/IEntityLocationComparisonCondition;", "condition", "Link/ptms/chemdah/um/skill/condition/EntityLocationDistanceCondition;", "config", "Lio/lumine/mythic/api/config/MythicLineConfig;", "(Link/ptms/chemdah/um/skill/condition/EntityLocationDistanceCondition;Lio/lumine/mythic/api/config/MythicLineConfig;)V", "getCondition", "()Link/ptms/chemdah/um/skill/condition/EntityLocationDistanceCondition;", "check", "", "p0", "Lio/lumine/mythic/api/adapters/AbstractEntity;", "p1", "Lio/lumine/mythic/api/adapters/AbstractLocation;", "implementation-v5"})
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

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u00012\u00020\u0002B\u0015\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u0012\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\rH\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/um/impl5/MobListenerCondition$Location;", "Lio/lumine/mythic/core/skills/SkillCondition;", "Lio/lumine/mythic/api/skills/conditions/ILocationCondition;", "condition", "Link/ptms/chemdah/um/skill/condition/LocationCondition;", "config", "Lio/lumine/mythic/api/config/MythicLineConfig;", "(Link/ptms/chemdah/um/skill/condition/LocationCondition;Lio/lumine/mythic/api/config/MythicLineConfig;)V", "getCondition", "()Link/ptms/chemdah/um/skill/condition/LocationCondition;", "check", "", "p0", "Lio/lumine/mythic/api/adapters/AbstractLocation;", "implementation-v5"})
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

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u00012\u00020\u0002B\u0015\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u0012\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\rH\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\u000e"}, d2={"Link/ptms/chemdah/um/impl5/MobListenerCondition$SkillMeta;", "Lio/lumine/mythic/core/skills/SkillCondition;", "Lio/lumine/mythic/api/skills/conditions/ISkillMetaCondition;", "condition", "Link/ptms/chemdah/um/skill/condition/SkillMetadataCondition;", "config", "Lio/lumine/mythic/api/config/MythicLineConfig;", "(Link/ptms/chemdah/um/skill/condition/SkillMetadataCondition;Lio/lumine/mythic/api/config/MythicLineConfig;)V", "getCondition", "()Link/ptms/chemdah/um/skill/condition/SkillMetadataCondition;", "check", "", "p0", "Lio/lumine/mythic/api/skills/SkillMetadata;", "implementation-v5"})
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

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u00012\u00020\u0002B\u0015\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u001c\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\r2\b\u0010\u000e\u001a\u0004\u0018\u00010\u000fH\u0016R\u0011\u0010\u0003\u001a\u00020\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/um/impl5/MobListenerCondition$SkillMetaComparison;", "Lio/lumine/mythic/core/skills/SkillCondition;", "Lio/lumine/mythic/api/skills/conditions/ISkillMetaComparisonCondition;", "condition", "Link/ptms/chemdah/um/skill/condition/SkillMetaComparisonCondition;", "config", "Lio/lumine/mythic/api/config/MythicLineConfig;", "(Link/ptms/chemdah/um/skill/condition/SkillMetaComparisonCondition;Lio/lumine/mythic/api/config/MythicLineConfig;)V", "getCondition", "()Link/ptms/chemdah/um/skill/condition/SkillMetaComparisonCondition;", "check", "", "p0", "Lio/lumine/mythic/api/skills/SkillMetadata;", "p1", "Lio/lumine/mythic/api/adapters/AbstractEntity;", "implementation-v5"})
    public static final class SkillMetaComparison
    extends SkillCondition
    implements ISkillMetaComparisonCondition {
        @NotNull
        private final SkillMetaComparisonCondition condition;

        public SkillMetaComparison(@NotNull SkillMetaComparisonCondition condition, @NotNull MythicLineConfig config) {
            Intrinsics.checkNotNullParameter((Object)condition, (String)"condition");
            Intrinsics.checkNotNullParameter((Object)config, (String)"config");
            super(config.getLine());
            this.condition = condition;
        }

        @NotNull
        public final SkillMetaComparisonCondition getCondition() {
            return this.condition;
        }

        public boolean check(@Nullable SkillMetadata p0, @Nullable AbstractEntity p1) {
            SkillMetadata skillMetadata = p0;
            AbstractEntity abstractEntity = p1;
            return this.condition.check(skillMetadata != null ? UtilsKt.toUniversal(skillMetadata) : null, (org.bukkit.entity.Entity)(abstractEntity != null ? abstractEntity.getBukkitEntity() : null));
        }
    }
}

