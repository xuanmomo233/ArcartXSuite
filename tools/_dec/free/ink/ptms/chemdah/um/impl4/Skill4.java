/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.lumine.xikage.mythicmobs.MythicMobs
 *  io.lumine.xikage.mythicmobs.adapters.AbstractEntity
 *  io.lumine.xikage.mythicmobs.adapters.AbstractLocation
 *  io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitAdapter
 *  io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitPlayer
 *  io.lumine.xikage.mythicmobs.mobs.GenericCaster
 *  io.lumine.xikage.mythicmobs.skills.SkillCaster
 *  io.lumine.xikage.mythicmobs.skills.SkillMechanic
 *  io.lumine.xikage.mythicmobs.skills.SkillMetadata
 *  io.lumine.xikage.mythicmobs.skills.SkillTrigger
 *  io.lumine.xikage.mythicmobs.skills.mechanics.DelaySkill
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.um.impl4;

import ink.ptms.chemdah.um.Skill;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.adapters.AbstractEntity;
import io.lumine.xikage.mythicmobs.adapters.AbstractLocation;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitAdapter;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitPlayer;
import io.lumine.xikage.mythicmobs.mobs.GenericCaster;
import io.lumine.xikage.mythicmobs.skills.SkillCaster;
import io.lumine.xikage.mythicmobs.skills.SkillMechanic;
import io.lumine.xikage.mythicmobs.skills.SkillMetadata;
import io.lumine.xikage.mythicmobs.skills.SkillTrigger;
import io.lumine.xikage.mythicmobs.skills.mechanics.DelaySkill;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000d\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\"\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0004\b\u0000\u0018\u00002\u00020\u0001:\u0003%&'B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004Jl\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u00102\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00100\u00132\f\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00150\u00132\u0006\u0010\u0016\u001a\u00020\u00172\u0012\u0010\u0018\u001a\u000e\u0012\u0004\u0012\u00020\u001a\u0012\u0004\u0012\u00020\u001b0\u00192\u0012\u0010\u001c\u001a\u000e\u0012\u0004\u0012\u00020\u0010\u0012\u0004\u0012\u00020\f0\u001dH\u0016J\u0010\u0010\u001e\u001a\u00020\u00172\u0006\u0010\u001f\u001a\u00020\u0010H\u0016J\u0010\u0010 \u001a\u00020\f2\u0006\u0010\u001f\u001a\u00020\u0010H\u0016J\u0018\u0010!\u001a\u00020\"2\u0006\u0010\u001f\u001a\u00020\u00102\u0006\u0010#\u001a\u00020$H\u0016R\u0014\u0010\u0005\u001a\u00020\u0006X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006("}, d2={"Link/ptms/chemdah/um/impl4/Skill4;", "Link/ptms/chemdah/um/Skill;", "source", "Lio/lumine/xikage/mythicmobs/skills/SkillMechanic;", "(Lio/lumine/xikage/mythicmobs/skills/SkillMechanic;)V", "delay", "", "getDelay", "()I", "getSource", "()Lio/lumine/xikage/mythicmobs/skills/SkillMechanic;", "execute", "", "trigger", "Link/ptms/chemdah/um/Skill$Trigger;", "entity", "Lorg/bukkit/entity/Entity;", "target", "et", "", "lt", "Lorg/bukkit/Location;", "power", "", "parameters", "", "", "", "targetFilter", "Lkotlin1822/Function1;", "getCooldown", "caster", "onCooldown", "setCooldown", "", "time", "", "CasterImpl", "MetadataImpl", "Trigger", "implementation-v4"})
@SourceDebugExtension(value={"SMAP\nSkill4.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Skill4.kt\nink/ptms/um/impl4/Skill4\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,103:1\n1549#2:104\n1620#2,3:105\n1549#2:108\n1620#2,3:109\n*S KotlinDebug\n*F\n+ 1 Skill4.kt\nink/ptms/um/impl4/Skill4\n*L\n42#1:104\n42#1:105,3\n43#1:108\n43#1:109,3\n*E\n"})
public final class Skill4
implements Skill {
    @NotNull
    private final SkillMechanic source;
    private final int delay;

    public Skill4(@NotNull SkillMechanic source) {
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        SkillMechanic skillMechanic = this.source = source;
        DelaySkill delaySkill = skillMechanic instanceof DelaySkill ? (DelaySkill)skillMechanic : null;
        this.delay = delaySkill != null ? delaySkill.getTicks() : -1;
    }

    @NotNull
    public final SkillMechanic getSource() {
        return this.source;
    }

    @Override
    public int getDelay() {
        return this.delay;
    }

    @Override
    public boolean execute(@NotNull Skill.Trigger trigger2, @NotNull Entity entity, @NotNull Entity target, @NotNull Set<? extends Entity> et, @NotNull Set<? extends Location> lt, float power, @NotNull Map<String, ? extends Object> parameters, @NotNull Function1<? super Entity, Boolean> targetFilter) {
        Collection<AbstractLocation> collection;
        Location it;
        Collection<AbstractEntity> collection2;
        Iterable $this$mapTo$iv$iv;
        Iterable $this$map$iv;
        Intrinsics.checkNotNullParameter((Object)trigger2, (String)"trigger");
        Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
        Intrinsics.checkNotNullParameter((Object)target, (String)"target");
        Intrinsics.checkNotNullParameter(et, (String)"et");
        Intrinsics.checkNotNullParameter(lt, (String)"lt");
        Intrinsics.checkNotNullParameter(parameters, (String)"parameters");
        Intrinsics.checkNotNullParameter(targetFilter, (String)"targetFilter");
        AbstractEntity caster = entity instanceof Player ? (AbstractEntity)new BukkitPlayer((Player)entity) : BukkitAdapter.adapt((Entity)entity);
        MythicMobs.inst().getSkillManager().runSecondPass();
        SkillTrigger skillTrigger = ((Trigger)trigger2).getSource();
        SkillCaster skillCaster = (SkillCaster)new CasterImpl(caster, parameters);
        AbstractEntity abstractEntity = BukkitAdapter.adapt((Entity)target);
        Intrinsics.checkNotNullExpressionValue((Object)abstractEntity, (String)"adapt(...)");
        AbstractLocation abstractLocation = BukkitAdapter.adapt((Location)entity.getLocation());
        Intrinsics.checkNotNullExpressionValue((Object)abstractLocation, (String)"adapt(...)");
        Iterable iterable = et;
        AbstractLocation abstractLocation2 = abstractLocation;
        AbstractEntity abstractEntity2 = abstractEntity;
        SkillCaster skillCaster2 = skillCaster;
        SkillTrigger skillTrigger2 = skillTrigger;
        SkillMechanic skillMechanic = this.source;
        boolean $i$f$map = false;
        void var12_17 = $this$map$iv;
        Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            Entity entity2 = (Entity)item$iv$iv;
            collection2 = destination$iv$iv;
            boolean bl = false;
            collection2.add(BukkitAdapter.adapt((Entity)it));
        }
        collection2 = (List)destination$iv$iv;
        $this$map$iv = lt;
        collection2 = CollectionsKt.toHashSet((Iterable)collection2);
        $i$f$map = false;
        $this$mapTo$iv$iv = $this$map$iv;
        destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            it = (Location)item$iv$iv;
            collection = destination$iv$iv;
            boolean bl = false;
            collection.add(BukkitAdapter.adapt((Location)it));
        }
        collection = (List)destination$iv$iv;
        Function<Entity, Boolean> function = arg_0 -> Skill4.execute$lambda$2(targetFilter, arg_0);
        float f = power;
        HashSet hashSet = CollectionsKt.toHashSet((Iterable)collection);
        Collection<AbstractEntity> collection3 = collection2;
        AbstractLocation abstractLocation3 = abstractLocation2;
        AbstractEntity abstractEntity3 = abstractEntity2;
        SkillCaster skillCaster3 = skillCaster2;
        SkillTrigger skillTrigger3 = skillTrigger2;
        return skillMechanic.executeSkills((SkillMetadata)new MetadataImpl(skillTrigger3, skillCaster3, abstractEntity3, abstractLocation3, (HashSet<AbstractEntity>)collection3, hashSet, f, function));
    }

    @Override
    public boolean onCooldown(@NotNull Entity caster) {
        Intrinsics.checkNotNullParameter((Object)caster, (String)"caster");
        return this.source.onCooldown((SkillCaster)new CasterImpl(BukkitAdapter.adapt((Entity)caster), MapsKt.emptyMap()));
    }

    @Override
    public float getCooldown(@NotNull Entity caster) {
        Intrinsics.checkNotNullParameter((Object)caster, (String)"caster");
        return this.source.getCooldown((SkillCaster)new CasterImpl(BukkitAdapter.adapt((Entity)caster), MapsKt.emptyMap()));
    }

    @Override
    public void setCooldown(@NotNull Entity caster, double time) {
        Intrinsics.checkNotNullParameter((Object)caster, (String)"caster");
        this.source.setCooldown((SkillCaster)new CasterImpl(BukkitAdapter.adapt((Entity)caster), MapsKt.emptyMap()), (float)time);
    }

    private static final Boolean execute$lambda$2(Function1 $tmp0, Entity p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        Intrinsics.checkNotNullParameter((Object)p0, (String)"p0");
        return (Boolean)$tmp0.invoke((Object)p0);
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u00002\u00020\u00012\u00020\u0002B#\u0012\b\u0010\u0003\u001a\u0004\u0018\u00010\u0004\u0012\u0012\u0010\u0005\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\b0\u0006\u00a2\u0006\u0002\u0010\tR \u0010\u0005\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\b0\u0006X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/um/impl4/Skill4$CasterImpl;", "Lio/lumine/xikage/mythicmobs/mobs/GenericCaster;", "Link/ptms/chemdah/um/Skill$ActiveCaster;", "entity", "Lio/lumine/xikage/mythicmobs/adapters/AbstractEntity;", "parameters", "", "", "", "(Lio/lumine/xikage/mythicmobs/adapters/AbstractEntity;Ljava/util/Map;)V", "getParameters", "()Ljava/util/Map;", "implementation-v4"})
    public static final class CasterImpl
    extends GenericCaster
    implements Skill.ActiveCaster {
        @NotNull
        private final Map<String, Object> parameters;

        public CasterImpl(@Nullable AbstractEntity entity, @NotNull Map<String, ? extends Object> parameters) {
            Intrinsics.checkNotNullParameter(parameters, (String)"parameters");
            super(entity);
            this.parameters = parameters;
        }

        @Override
        @NotNull
        public Map<String, Object> getParameters() {
            return this.parameters;
        }
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000D\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\b\b\u0018\u00002\u00020\u0001Bq\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0016\u0010\n\u001a\u0012\u0012\u0004\u0012\u00020\u00070\u000bj\b\u0012\u0004\u0012\u00020\u0007`\f\u0012\u0016\u0010\r\u001a\u0012\u0012\u0004\u0012\u00020\t0\u000bj\b\u0012\u0004\u0012\u00020\t`\f\u0012\u0006\u0010\u000e\u001a\u00020\u000f\u0012\u0012\u0010\u0010\u001a\u000e\u0012\u0004\u0012\u00020\u0012\u0012\u0004\u0012\u00020\u00130\u0011\u00a2\u0006\u0002\u0010\u0014J\u0010\u0010\u0017\u001a\u00020\u00012\u0006\u0010\u0018\u001a\u00020\u0007H\u0016J\u0016\u0010\u0019\u001a\u00020\u00012\f\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u00070\u000bH\u0016R\u001d\u0010\u0010\u001a\u000e\u0012\u0004\u0012\u00020\u0012\u0012\u0004\u0012\u00020\u00130\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016\u00a8\u0006\u001b"}, d2={"Link/ptms/chemdah/um/impl4/Skill4$MetadataImpl;", "Lio/lumine/xikage/mythicmobs/skills/SkillMetadata;", "cause", "Lio/lumine/xikage/mythicmobs/skills/SkillTrigger;", "caster", "Lio/lumine/xikage/mythicmobs/skills/SkillCaster;", "trigger", "Lio/lumine/xikage/mythicmobs/adapters/AbstractEntity;", "origin", "Lio/lumine/xikage/mythicmobs/adapters/AbstractLocation;", "et", "Ljava/util/HashSet;", "Lkotlin1822/collections/HashSet;", "lt", "power", "", "targetFilter", "Ljava/util/function/Function;", "Lorg/bukkit/entity/Entity;", "", "(Lio/lumine/xikage/mythicmobs/skills/SkillTrigger;Lio/lumine/xikage/mythicmobs/skills/SkillCaster;Lio/lumine/xikage/mythicmobs/adapters/AbstractEntity;Lio/lumine/xikage/mythicmobs/adapters/AbstractLocation;Ljava/util/HashSet;Ljava/util/HashSet;FLjava/util/function/Function;)V", "getTargetFilter", "()Ljava/util/function/Function;", "setEntityTarget", "target", "setEntityTargets", "targets", "implementation-v4"})
    @SourceDebugExtension(value={"SMAP\nSkill4.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Skill4.kt\nink/ptms/um/impl4/Skill4$MetadataImpl\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,103:1\n766#2:104\n857#2,2:105\n766#2:107\n857#2,2:108\n*S KotlinDebug\n*F\n+ 1 Skill4.kt\nink/ptms/um/impl4/Skill4$MetadataImpl\n*L\n89#1:104\n89#1:105,2\n99#1:107\n99#1:108,2\n*E\n"})
    public static final class MetadataImpl
    extends SkillMetadata {
        @NotNull
        private final Function<Entity, Boolean> targetFilter;

        /*
         * WARNING - void declaration
         */
        public MetadataImpl(@NotNull SkillTrigger cause, @NotNull SkillCaster caster, @NotNull AbstractEntity trigger2, @NotNull AbstractLocation origin, @NotNull HashSet<AbstractEntity> et, @NotNull HashSet<AbstractLocation> lt, float power, @NotNull Function<Entity, Boolean> targetFilter) {
            void $this$filterTo$iv$iv;
            void $this$filter$iv;
            Intrinsics.checkNotNullParameter((Object)cause, (String)"cause");
            Intrinsics.checkNotNullParameter((Object)caster, (String)"caster");
            Intrinsics.checkNotNullParameter((Object)trigger2, (String)"trigger");
            Intrinsics.checkNotNullParameter((Object)origin, (String)"origin");
            Intrinsics.checkNotNullParameter(et, (String)"et");
            Intrinsics.checkNotNullParameter(lt, (String)"lt");
            Intrinsics.checkNotNullParameter(targetFilter, (String)"targetFilter");
            Iterable iterable = et;
            AbstractLocation abstractLocation = origin;
            AbstractEntity abstractEntity = trigger2;
            SkillCaster skillCaster = caster;
            SkillTrigger skillTrigger = cause;
            MetadataImpl metadataImpl = this;
            boolean $i$f$filter = false;
            void var11_16 = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                AbstractEntity it = (AbstractEntity)element$iv$iv;
                boolean bl = false;
                Boolean bl2 = targetFilter.apply(it.getBukkitEntity());
                Intrinsics.checkNotNullExpressionValue((Object)bl2, (String)"apply(...)");
                if (!bl2.booleanValue()) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            List list2 = (List)destination$iv$iv;
            super(skillTrigger, skillCaster, abstractEntity, abstractLocation, CollectionsKt.toHashSet((Iterable)list2), lt, power);
            this.targetFilter = targetFilter;
        }

        @NotNull
        public final Function<Entity, Boolean> getTargetFilter() {
            return this.targetFilter;
        }

        @NotNull
        public SkillMetadata setEntityTarget(@NotNull AbstractEntity target) {
            Intrinsics.checkNotNullParameter((Object)target, (String)"target");
            Boolean bl = this.targetFilter.apply(target.getBukkitEntity());
            Intrinsics.checkNotNullExpressionValue((Object)bl, (String)"apply(...)");
            if (bl.booleanValue()) {
                SkillMetadata skillMetadata = super.setEntityTarget(target);
                Intrinsics.checkNotNullExpressionValue((Object)skillMetadata, (String)"setEntityTarget(...)");
                return skillMetadata;
            }
            return this;
        }

        /*
         * WARNING - void declaration
         */
        @NotNull
        public SkillMetadata setEntityTargets(@NotNull HashSet<AbstractEntity> targets) {
            void $this$filterTo$iv$iv;
            void $this$filter$iv;
            Intrinsics.checkNotNullParameter(targets, (String)"targets");
            Iterable iterable = targets;
            MetadataImpl metadataImpl = this;
            boolean $i$f$filter = false;
            void var4_5 = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                AbstractEntity it = (AbstractEntity)element$iv$iv;
                boolean bl = false;
                Boolean bl2 = this.targetFilter.apply(it.getBukkitEntity());
                Intrinsics.checkNotNullExpressionValue((Object)bl2, (String)"apply(...)");
                if (!bl2.booleanValue()) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            SkillMetadata skillMetadata = super.setEntityTargets(CollectionsKt.toHashSet((Iterable)((List)destination$iv$iv)));
            Intrinsics.checkNotNullExpressionValue((Object)skillMetadata, (String)"setEntityTargets(...)");
            return skillMetadata;
        }
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u0014\u0010\u0005\u001a\u00020\u0006X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/um/impl4/Skill4$Trigger;", "Link/ptms/chemdah/um/Skill$Trigger;", "obj", "", "(Ljava/lang/Object;)V", "name", "", "getName", "()Ljava/lang/String;", "source", "Lio/lumine/xikage/mythicmobs/skills/SkillTrigger;", "getSource", "()Lio/lumine/xikage/mythicmobs/skills/SkillTrigger;", "implementation-v4"})
    public static final class Trigger
    implements Skill.Trigger {
        @NotNull
        private final SkillTrigger source;
        @NotNull
        private final String name;

        public Trigger(@NotNull Object obj) {
            Intrinsics.checkNotNullParameter((Object)obj, (String)"obj");
            this.source = (SkillTrigger)obj;
            this.name = this.source.name();
        }

        @NotNull
        public final SkillTrigger getSource() {
            return this.source;
        }

        @Override
        @NotNull
        public String getName() {
            return this.name;
        }
    }
}

