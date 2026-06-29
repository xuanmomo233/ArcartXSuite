/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex$Companion
 *  io.lumine.mythic.api.adapters.AbstractEntity
 *  io.lumine.mythic.api.adapters.AbstractLocation
 *  io.lumine.mythic.api.mobs.GenericCaster
 *  io.lumine.mythic.api.skills.SkillCaster
 *  io.lumine.mythic.api.skills.SkillMetadata
 *  io.lumine.mythic.api.skills.SkillTrigger
 *  io.lumine.mythic.api.skills.placeholders.PlaceholderInt
 *  io.lumine.mythic.bukkit.BukkitAdapter
 *  io.lumine.mythic.bukkit.adapters.BukkitPlayer
 *  io.lumine.mythic.core.skills.SkillMechanic
 *  io.lumine.mythic.core.skills.SkillMetadataImpl
 *  io.lumine.mythic.core.skills.mechanics.DelaySkill
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
package ink.ptms.chemdah.um.impl5;

import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import ink.ptms.chemdah.um.Skill;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.api.mobs.GenericCaster;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillTrigger;
import io.lumine.mythic.api.skills.placeholders.PlaceholderInt;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.adapters.BukkitPlayer;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.skills.SkillMetadataImpl;
import io.lumine.mythic.core.skills.mechanics.DelaySkill;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000p\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\"\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0003\b\u0000\u0018\u00002\u00020\u0001:\u0002*+B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004Jl\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u00162\f\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\u00160\u00192\f\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u001b0\u00192\u0006\u0010\u001c\u001a\u00020\u001d2\u0012\u0010\u001e\u001a\u000e\u0012\u0004\u0012\u00020 \u0012\u0004\u0012\u00020\u00030\u001f2\u0012\u0010!\u001a\u000e\u0012\u0004\u0012\u00020\u0016\u0012\u0004\u0012\u00020\u00120\"H\u0016J\u0010\u0010#\u001a\u00020\u001d2\u0006\u0010$\u001a\u00020\u0016H\u0016J\u0010\u0010%\u001a\u00020\u00122\u0006\u0010$\u001a\u00020\u0016H\u0016J\u0018\u0010&\u001a\u00020'2\u0006\u0010$\u001a\u00020\u00162\u0006\u0010(\u001a\u00020)H\u0016R\u0014\u0010\u0005\u001a\u00020\u00068VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0007\u0010\bR\u0013\u0010\t\u001a\u0004\u0018\u00010\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\r\u001a\u00020\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006,"}, d2={"Link/ptms/chemdah/um/impl5/Skill5;", "Link/ptms/chemdah/um/Skill;", "obj", "", "(Ljava/lang/Object;)V", "delay", "", "getDelay", "()I", "placeholderDelay", "Lio/lumine/mythic/api/skills/placeholders/PlaceholderInt;", "getPlaceholderDelay", "()Lio/lumine/mythic/api/skills/placeholders/PlaceholderInt;", "source", "Lio/lumine/mythic/core/skills/SkillMechanic;", "getSource", "()Lio/lumine/mythic/core/skills/SkillMechanic;", "execute", "", "trigger", "Link/ptms/chemdah/um/Skill$Trigger;", "entity", "Lorg/bukkit/entity/Entity;", "target", "et", "", "lt", "Lorg/bukkit/Location;", "power", "", "parameters", "", "", "targetFilter", "Lkotlin1822/Function1;", "getCooldown", "caster", "onCooldown", "setCooldown", "", "time", "", "CasterImpl", "Trigger", "implementation-v5"})
@SourceDebugExtension(value={"SMAP\nSkill5.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Skill5.kt\nink/ptms/um/impl5/Skill5\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,75:1\n1549#2:76\n1620#2,3:77\n1549#2:80\n1620#2,3:81\n*S KotlinDebug\n*F\n+ 1 Skill5.kt\nink/ptms/um/impl5/Skill5\n*L\n44#1:76\n44#1:77,3\n45#1:80\n45#1:81,3\n*E\n"})
public final class Skill5
implements Skill {
    @NotNull
    private final SkillMechanic source;
    @Nullable
    private final PlaceholderInt placeholderDelay;

    public Skill5(@NotNull Object obj) {
        Intrinsics.checkNotNullParameter((Object)obj, (String)"obj");
        SkillMechanic skillMechanic = this.source = (SkillMechanic)obj;
        DelaySkill delaySkill = skillMechanic instanceof DelaySkill ? (DelaySkill)skillMechanic : null;
        this.placeholderDelay = delaySkill != null ? (PlaceholderInt)Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)delaySkill, (String)"ticks", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null) : null;
    }

    @NotNull
    public final SkillMechanic getSource() {
        return this.source;
    }

    @Nullable
    public final PlaceholderInt getPlaceholderDelay() {
        return this.placeholderDelay;
    }

    @Override
    public int getDelay() {
        PlaceholderInt placeholderInt = this.placeholderDelay;
        return placeholderInt != null ? placeholderInt.get() : -1;
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
        Iterable iterable = et;
        AbstractLocation abstractLocation = BukkitAdapter.adapt((Location)entity.getLocation());
        AbstractEntity abstractEntity = BukkitAdapter.adapt((Entity)target);
        SkillCaster skillCaster = (SkillCaster)new CasterImpl(caster, parameters);
        SkillTrigger<?> skillTrigger = ((Trigger)trigger2).getSource();
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
        float f = power;
        Collection collection3 = CollectionsKt.toHashSet((Iterable)collection);
        Collection<AbstractEntity> collection4 = collection2;
        AbstractLocation abstractLocation2 = abstractLocation;
        AbstractEntity abstractEntity2 = abstractEntity;
        SkillCaster skillCaster2 = skillCaster;
        SkillTrigger<?> skillTrigger2 = skillTrigger;
        return skillMechanic.execute((SkillMetadata)new SkillMetadataImpl(skillTrigger2, skillCaster2, abstractEntity2, abstractLocation2, collection4, collection3, f));
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
        this.source.setCooldown((SkillCaster)new CasterImpl(BukkitAdapter.adapt((Entity)caster), MapsKt.emptyMap()), time);
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0010\u0000\n\u0002\b\u0004\u0018\u00002\u00020\u00012\u00020\u0002B#\u0012\b\u0010\u0003\u001a\u0004\u0018\u00010\u0004\u0012\u0012\u0010\u0005\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\b0\u0006\u00a2\u0006\u0002\u0010\tR \u0010\u0005\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\b0\u0006X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/um/impl5/Skill5$CasterImpl;", "Lio/lumine/mythic/api/mobs/GenericCaster;", "Link/ptms/chemdah/um/Skill$ActiveCaster;", "entity", "Lio/lumine/mythic/api/adapters/AbstractEntity;", "parameters", "", "", "", "(Lio/lumine/mythic/api/adapters/AbstractEntity;Ljava/util/Map;)V", "getParameters", "()Ljava/util/Map;", "implementation-v5"})
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

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004R\u0014\u0010\u0005\u001a\u00020\u0006X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0015\u0010\t\u001a\u0006\u0012\u0002\b\u00030\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/um/impl5/Skill5$Trigger;", "Link/ptms/chemdah/um/Skill$Trigger;", "obj", "", "(Ljava/lang/Object;)V", "name", "", "getName", "()Ljava/lang/String;", "source", "Lio/lumine/mythic/api/skills/SkillTrigger;", "getSource", "()Lio/lumine/mythic/api/skills/SkillTrigger;", "implementation-v5"})
    public static final class Trigger
    implements Skill.Trigger {
        @NotNull
        private final SkillTrigger<?> source;
        @NotNull
        private final String name;

        public Trigger(@NotNull Object obj) {
            Intrinsics.checkNotNullParameter((Object)obj, (String)"obj");
            this.source = (SkillTrigger)obj;
            Object object = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, this.source, (String)"name", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
            Intrinsics.checkNotNull((Object)object);
            this.name = (String)object;
        }

        @NotNull
        public final SkillTrigger<?> getSource() {
            return this.source;
        }

        @Override
        @NotNull
        public String getName() {
            return this.name;
        }
    }
}

