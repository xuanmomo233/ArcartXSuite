/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex$Companion
 *  io.lumine.mythic.api.adapters.AbstractEntity
 *  io.lumine.mythic.api.adapters.AbstractItemStack
 *  io.lumine.mythic.api.adapters.AbstractLocation
 *  io.lumine.mythic.api.config.MythicLineConfig
 *  io.lumine.mythic.api.skills.SkillCaster
 *  io.lumine.mythic.api.skills.SkillMetadata
 *  io.lumine.mythic.api.skills.SkillTrigger
 *  io.lumine.mythic.api.skills.placeholders.PlaceholderDouble
 *  io.lumine.mythic.api.skills.placeholders.PlaceholderFloat
 *  io.lumine.mythic.api.skills.placeholders.PlaceholderInt
 *  io.lumine.mythic.api.skills.placeholders.PlaceholderString
 *  io.lumine.mythic.bukkit.BukkitAdapter
 *  io.lumine.mythic.core.config.MythicLineConfigImpl
 *  io.lumine.mythic.core.skills.SkillMetadataImpl
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.um.impl5;

import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import ink.ptms.chemdah.um.Skill;
import ink.ptms.chemdah.um.impl5.Skill5;
import ink.ptms.chemdah.um.impl5.SkillCasterProxy5;
import ink.ptms.chemdah.um.skill.SkillConfig;
import ink.ptms.chemdah.um.skill.SkillMeta;
import ink.ptms.chemdah.um.skill.data.PlaceholderDouble;
import ink.ptms.chemdah.um.skill.data.PlaceholderFloat;
import ink.ptms.chemdah.um.skill.data.PlaceholderString;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.adapters.AbstractItemStack;
import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillTrigger;
import io.lumine.mythic.api.skills.placeholders.PlaceholderInt;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.config.MythicLineConfigImpl;
import io.lumine.mythic.core.skills.SkillMetadataImpl;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=2, xi=48, d1={"\u0000X\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u001a\f\u0010\u0000\u001a\u00020\u0001*\u00020\u0002H\u0000\u001a\f\u0010\u0000\u001a\u00020\u0003*\u00020\u0004H\u0000\u001a\f\u0010\u0005\u001a\u00020\u0006*\u00020\u0007H\u0000\u001a\f\u0010\u0005\u001a\u00020\u0004*\u00020\u0003H\u0000\u001a\f\u0010\u0005\u001a\u00020\b*\u00020\tH\u0000\u001a\f\u0010\n\u001a\u00020\u000b*\u00020\fH\u0000\u001a\f\u0010\n\u001a\u00020\u0007*\u00020\u0006H\u0000\u001a\f\u0010\n\u001a\u00020\r*\u00020\u000eH\u0000\u001a\f\u0010\n\u001a\u00020\u000f*\u00020\u0010H\u0000\u001a\f\u0010\n\u001a\u00020\u0011*\u00020\u0012H\u0000\u001a\f\u0010\n\u001a\u00020\u0013*\u00020\u0014H\u0000\u001a\f\u0010\n\u001a\u00020\u0015*\u00020\u0016H\u0000\u00a8\u0006\u0017"}, d2={"toBukkit", "Lorg/bukkit/inventory/ItemStack;", "Lio/lumine/mythic/api/adapters/AbstractItemStack;", "Lorg/bukkit/Location;", "Lio/lumine/mythic/api/adapters/AbstractLocation;", "toMythic", "Lio/lumine/mythic/api/skills/SkillCaster;", "Link/ptms/chemdah/um/skill/SkillCaster;", "Lio/lumine/mythic/api/adapters/AbstractEntity;", "Lorg/bukkit/entity/Entity;", "toUniversal", "Link/ptms/chemdah/um/skill/SkillConfig;", "Lio/lumine/mythic/api/config/MythicLineConfig;", "Link/ptms/chemdah/um/skill/SkillMeta;", "Lio/lumine/mythic/api/skills/SkillMetadata;", "Link/ptms/chemdah/um/skill/data/PlaceholderDouble;", "Lio/lumine/mythic/api/skills/placeholders/PlaceholderDouble;", "Link/ptms/chemdah/um/skill/data/PlaceholderFloat;", "Lio/lumine/mythic/api/skills/placeholders/PlaceholderFloat;", "Link/ptms/chemdah/um/skill/data/PlaceholderInt;", "Lio/lumine/mythic/api/skills/placeholders/PlaceholderInt;", "Link/ptms/chemdah/um/skill/data/PlaceholderString;", "Lio/lumine/mythic/api/skills/placeholders/PlaceholderString;", "implementation-v5"})
public final class UtilsKt {
    @NotNull
    public static final ItemStack toBukkit(@NotNull AbstractItemStack $this$toBukkit) {
        Intrinsics.checkNotNullParameter((Object)$this$toBukkit, (String)"<this>");
        ItemStack itemStack = BukkitAdapter.adapt((AbstractItemStack)$this$toBukkit);
        Intrinsics.checkNotNullExpressionValue((Object)itemStack, (String)"adapt(...)");
        return itemStack;
    }

    @NotNull
    public static final Location toBukkit(@NotNull AbstractLocation $this$toBukkit) {
        Intrinsics.checkNotNullParameter((Object)$this$toBukkit, (String)"<this>");
        Location location = BukkitAdapter.adapt((AbstractLocation)$this$toBukkit);
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"adapt(...)");
        return location;
    }

    @NotNull
    public static final AbstractLocation toMythic(@NotNull Location $this$toMythic) {
        Intrinsics.checkNotNullParameter((Object)$this$toMythic, (String)"<this>");
        AbstractLocation abstractLocation = BukkitAdapter.adapt((Location)$this$toMythic);
        Intrinsics.checkNotNullExpressionValue((Object)abstractLocation, (String)"adapt(...)");
        return abstractLocation;
    }

    @NotNull
    public static final AbstractEntity toMythic(@NotNull Entity $this$toMythic) {
        Intrinsics.checkNotNullParameter((Object)$this$toMythic, (String)"<this>");
        AbstractEntity abstractEntity = BukkitAdapter.adapt((Entity)$this$toMythic);
        Intrinsics.checkNotNullExpressionValue((Object)abstractEntity, (String)"adapt(...)");
        return abstractEntity;
    }

    @NotNull
    public static final ink.ptms.chemdah.um.skill.SkillCaster toUniversal(@NotNull SkillCaster $this$toUniversal) {
        Intrinsics.checkNotNullParameter((Object)$this$toUniversal, (String)"<this>");
        return new ink.ptms.chemdah.um.skill.SkillCaster($this$toUniversal){
            final /* synthetic */ SkillCaster $this_toUniversal;
            {
                this.$this_toUniversal = $receiver;
            }

            @NotNull
            public Entity getEntity() {
                Entity entity = this.$this_toUniversal.getEntity().getBukkitEntity();
                Intrinsics.checkNotNullExpressionValue((Object)entity, (String)"getBukkitEntity(...)");
                return entity;
            }

            @NotNull
            public Location getLocation() {
                AbstractLocation abstractLocation = this.$this_toUniversal.getLocation();
                Intrinsics.checkNotNullExpressionValue((Object)abstractLocation, (String)"getLocation(...)");
                return UtilsKt.toBukkit(abstractLocation);
            }

            public double getLevel() {
                return this.$this_toUniversal.getLevel();
            }

            public float getPower() {
                return this.$this_toUniversal.getPower();
            }

            public int getGlobalCooldown() {
                return this.$this_toUniversal.getGlobalCooldown();
            }

            public void setGlobalCooldown(int value2) {
                this.$this_toUniversal.setGlobalCooldown(value2);
            }
        };
    }

    @NotNull
    public static final SkillCaster toMythic(@NotNull ink.ptms.chemdah.um.skill.SkillCaster $this$toMythic) {
        Intrinsics.checkNotNullParameter((Object)$this$toMythic, (String)"<this>");
        return ((SkillCasterProxy5)$this$toMythic).getOrigin();
    }

    @NotNull
    public static final PlaceholderString toUniversal(@NotNull io.lumine.mythic.api.skills.placeholders.PlaceholderString $this$toUniversal) {
        Intrinsics.checkNotNullParameter((Object)$this$toUniversal, (String)"<this>");
        return new PlaceholderString($this$toUniversal){
            final /* synthetic */ io.lumine.mythic.api.skills.placeholders.PlaceholderString $this_toUniversal;
            {
                this.$this_toUniversal = $receiver;
            }

            @NotNull
            public String get() {
                String string = this.$this_toUniversal.get();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"get(...)");
                return string;
            }

            @NotNull
            public String get(@NotNull Entity entity) {
                Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
                String string = this.$this_toUniversal.get(UtilsKt.toMythic(entity));
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"get(...)");
                return string;
            }

            @NotNull
            public String get(@NotNull ink.ptms.chemdah.um.skill.SkillCaster caster) {
                Intrinsics.checkNotNullParameter((Object)caster, (String)"caster");
                String string = this.$this_toUniversal.get(UtilsKt.toMythic(caster));
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"get(...)");
                return string;
            }
        };
    }

    @NotNull
    public static final ink.ptms.chemdah.um.skill.data.PlaceholderInt toUniversal(@NotNull PlaceholderInt $this$toUniversal) {
        Intrinsics.checkNotNullParameter((Object)$this$toUniversal, (String)"<this>");
        return new ink.ptms.chemdah.um.skill.data.PlaceholderInt($this$toUniversal){
            final /* synthetic */ PlaceholderInt $this_toUniversal;
            {
                this.$this_toUniversal = $receiver;
            }

            public int get() {
                return this.$this_toUniversal.get();
            }

            public int get(@NotNull Entity entity) {
                Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
                return this.$this_toUniversal.get(UtilsKt.toMythic(entity));
            }

            public int get(@NotNull ink.ptms.chemdah.um.skill.SkillCaster caster) {
                Intrinsics.checkNotNullParameter((Object)caster, (String)"caster");
                return this.$this_toUniversal.get(UtilsKt.toMythic(caster));
            }
        };
    }

    @NotNull
    public static final PlaceholderDouble toUniversal(@NotNull io.lumine.mythic.api.skills.placeholders.PlaceholderDouble $this$toUniversal) {
        Intrinsics.checkNotNullParameter((Object)$this$toUniversal, (String)"<this>");
        return new PlaceholderDouble($this$toUniversal){
            final /* synthetic */ io.lumine.mythic.api.skills.placeholders.PlaceholderDouble $this_toUniversal;
            {
                this.$this_toUniversal = $receiver;
            }

            public double get() {
                return this.$this_toUniversal.get();
            }

            public double get(@NotNull Entity entity) {
                Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
                return this.$this_toUniversal.get(UtilsKt.toMythic(entity));
            }

            public double get(@NotNull ink.ptms.chemdah.um.skill.SkillCaster caster) {
                Intrinsics.checkNotNullParameter((Object)caster, (String)"caster");
                return this.$this_toUniversal.get(UtilsKt.toMythic(caster));
            }
        };
    }

    @NotNull
    public static final PlaceholderFloat toUniversal(@NotNull io.lumine.mythic.api.skills.placeholders.PlaceholderFloat $this$toUniversal) {
        Intrinsics.checkNotNullParameter((Object)$this$toUniversal, (String)"<this>");
        return new PlaceholderFloat($this$toUniversal){
            final /* synthetic */ io.lumine.mythic.api.skills.placeholders.PlaceholderFloat $this_toUniversal;
            {
                this.$this_toUniversal = $receiver;
            }

            public float get() {
                return this.$this_toUniversal.get();
            }

            public float get(@NotNull Entity entity) {
                Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
                return this.$this_toUniversal.get(UtilsKt.toMythic(entity));
            }

            public float get(@NotNull ink.ptms.chemdah.um.skill.SkillCaster caster) {
                Intrinsics.checkNotNullParameter((Object)caster, (String)"caster");
                return this.$this_toUniversal.get(UtilsKt.toMythic(caster));
            }
        };
    }

    @NotNull
    public static final SkillConfig toUniversal(@NotNull MythicLineConfig $this$toUniversal) {
        Intrinsics.checkNotNullParameter((Object)$this$toUniversal, (String)"<this>");
        MythicLineConfigImpl cfr_ignored_0 = (MythicLineConfigImpl)$this$toUniversal;
        return new SkillConfig($this$toUniversal){
            final /* synthetic */ MythicLineConfig $this_toUniversal;
            {
                this.$this_toUniversal = $receiver;
            }

            @NotNull
            public String line() {
                String string = ((MythicLineConfigImpl)this.$this_toUniversal).getLine();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getLine(...)");
                return string;
            }

            public int size() {
                return ((MythicLineConfigImpl)this.$this_toUniversal).size();
            }

            @NotNull
            public Set<Map.Entry<String, Object>> entrySet() {
                Set set2 = this.$this_toUniversal.entrySet();
                Intrinsics.checkNotNullExpressionValue((Object)set2, (String)"entrySet(...)");
                return set2;
            }

            @NotNull
            public String getKey() {
                String string = ((MythicLineConfigImpl)this.$this_toUniversal).getKey();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getKey(...)");
                return string;
            }

            @NotNull
            public String getKey(@NotNull String s) {
                Intrinsics.checkNotNullParameter((Object)s, (String)"s");
                String string = ((MythicLineConfigImpl)this.$this_toUniversal).getKey();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getKey(...)");
                return string;
            }

            public boolean getBoolean(@NotNull String[] key, boolean def) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                return this.$this_toUniversal.getBoolean(key, def);
            }

            @NotNull
            public String getString(@NotNull String[] key, @NotNull String def) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)def, (String)"def");
                String string = this.$this_toUniversal.getString(key, def, new String[0]);
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getString(...)");
                return string;
            }

            @NotNull
            public PlaceholderString getPlaceholderString(@NotNull String[] key, @NotNull String def, String ... args) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)def, (String)"def");
                Intrinsics.checkNotNullParameter((Object)args, (String)"args");
                io.lumine.mythic.api.skills.placeholders.PlaceholderString placeholderString = this.$this_toUniversal.getPlaceholderString(key, def, Arrays.copyOf(args, args.length));
                Intrinsics.checkNotNullExpressionValue((Object)placeholderString, (String)"getPlaceholderString(...)");
                return UtilsKt.toUniversal(placeholderString);
            }

            public int getInt(@NotNull String[] key, int def) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                return this.$this_toUniversal.getInteger(key, def);
            }

            @NotNull
            public ink.ptms.chemdah.um.skill.data.PlaceholderInt getPlaceholderInt(@NotNull String[] key, int def, String ... args) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)args, (String)"args");
                PlaceholderInt placeholderInt = this.$this_toUniversal.getPlaceholderInteger(key, def, Arrays.copyOf(args, args.length));
                Intrinsics.checkNotNullExpressionValue((Object)placeholderInt, (String)"getPlaceholderInteger(...)");
                return UtilsKt.toUniversal(placeholderInt);
            }

            public double getDouble(@NotNull String[] key, double def) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                return this.$this_toUniversal.getDouble(key, def);
            }

            @NotNull
            public PlaceholderDouble getPlaceholderDouble(@NotNull String[] key, double def, String ... args) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)args, (String)"args");
                io.lumine.mythic.api.skills.placeholders.PlaceholderDouble placeholderDouble = this.$this_toUniversal.getPlaceholderDouble(key, def, Arrays.copyOf(args, args.length));
                Intrinsics.checkNotNullExpressionValue((Object)placeholderDouble, (String)"getPlaceholderDouble(...)");
                return UtilsKt.toUniversal(placeholderDouble);
            }

            public float getFloat(@NotNull String[] key, float def) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                return this.$this_toUniversal.getFloat(key, def);
            }

            @NotNull
            public PlaceholderFloat getPlaceholderFloat(@NotNull String[] key, float def, String ... args) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)args, (String)"args");
                io.lumine.mythic.api.skills.placeholders.PlaceholderFloat placeholderFloat = this.$this_toUniversal.getPlaceholderFloat(key, def, Arrays.copyOf(args, args.length));
                Intrinsics.checkNotNullExpressionValue((Object)placeholderFloat, (String)"getPlaceholderFloat(...)");
                return UtilsKt.toUniversal(placeholderFloat);
            }

            public long getLong(@NotNull String[] key, long def) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                return this.$this_toUniversal.getLong(key, def);
            }

            @NotNull
            public Color getColor(@NotNull String key, @NotNull String def) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)def, (String)"def");
                Color color = ((MythicLineConfigImpl)this.$this_toUniversal).getColor(key, def);
                Intrinsics.checkNotNullExpressionValue((Object)color, (String)"getColor(...)");
                return color;
            }

            @NotNull
            public Color getColor(@NotNull String[] key, @NotNull String def) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)def, (String)"def");
                Color color = ((MythicLineConfigImpl)this.$this_toUniversal).getColor(key, def);
                Intrinsics.checkNotNullExpressionValue((Object)color, (String)"getColor(...)");
                return color;
            }
        };
    }

    @NotNull
    public static final SkillMeta toUniversal(@NotNull SkillMetadata $this$toUniversal) {
        Intrinsics.checkNotNullParameter((Object)$this$toUniversal, (String)"<this>");
        SkillMetadataImpl cfr_ignored_0 = (SkillMetadataImpl)$this$toUniversal;
        return new SkillMeta($this$toUniversal){
            @NotNull
            private final HashMap<String, Object> metadataMap;
            @NotNull
            private final HashMap<String, String> parameterMap;
            final /* synthetic */ SkillMetadata $this_toUniversal;
            {
                this.$this_toUniversal = $receiver;
                Object object = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)$receiver, (String)"metadata", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
                Intrinsics.checkNotNull((Object)object);
                this.metadataMap = (HashMap)object;
                Object object2 = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)$receiver, (String)"parameters", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
                Intrinsics.checkNotNull((Object)object2);
                this.parameterMap = (HashMap)object2;
            }

            @NotNull
            public final HashMap<String, Object> getMetadataMap() {
                return this.metadataMap;
            }

            @NotNull
            public final HashMap<String, String> getParameterMap() {
                return this.parameterMap;
            }

            @NotNull
            public ink.ptms.chemdah.um.skill.SkillCaster getCaster() {
                SkillCaster skillCaster = ((SkillMetadataImpl)this.$this_toUniversal).getCaster();
                Intrinsics.checkNotNullExpressionValue((Object)skillCaster, (String)"getCaster(...)");
                return UtilsKt.toUniversal(skillCaster);
            }

            public void setCaster(@NotNull ink.ptms.chemdah.um.skill.SkillCaster value2) {
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                ((SkillMetadataImpl)this.$this_toUniversal).setCaster(UtilsKt.toMythic(value2));
            }

            @NotNull
            public Entity getTrigger() {
                Entity entity = ((SkillMetadataImpl)this.$this_toUniversal).getTrigger().getBukkitEntity();
                Intrinsics.checkNotNullExpressionValue((Object)entity, (String)"getBukkitEntity(...)");
                return entity;
            }

            public void setTrigger(@NotNull Entity value2) {
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                ((SkillMetadataImpl)this.$this_toUniversal).setTrigger(UtilsKt.toMythic(value2));
            }

            @NotNull
            public Location getOrigin() {
                AbstractLocation abstractLocation = ((SkillMetadataImpl)this.$this_toUniversal).getOrigin();
                Intrinsics.checkNotNullExpressionValue((Object)abstractLocation, (String)"getOrigin(...)");
                return UtilsKt.toBukkit(abstractLocation);
            }

            public void setOrigin(@NotNull Location value2) {
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                ((SkillMetadataImpl)this.$this_toUniversal).setOrigin(UtilsKt.toMythic(value2));
            }

            @NotNull
            public Skill.Trigger getCause() {
                SkillTrigger skillTrigger = ((SkillMetadataImpl)this.$this_toUniversal).getCause();
                Intrinsics.checkNotNullExpressionValue((Object)skillTrigger, (String)"getCause(...)");
                return new Skill5.Trigger(skillTrigger);
            }

            public float getPower() {
                return ((SkillMetadataImpl)this.$this_toUniversal).getPower();
            }

            public void setPower(float value2) {
                ((SkillMetadataImpl)this.$this_toUniversal).setPower(value2);
            }

            public boolean isAsync() {
                return this.$this_toUniversal.isAsync();
            }

            public void setAsync(boolean value2) {
                ((SkillMetadataImpl)this.$this_toUniversal).setIsAsync(value2);
            }

            /*
             * WARNING - void declaration
             */
            @NotNull
            public Set<Entity> getEntityTargets() {
                void $this$mapTo$iv$iv;
                Collection collection = ((SkillMetadataImpl)this.$this_toUniversal).getEntityTargets();
                Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"getEntityTargets(...)");
                Iterable $this$map$iv = collection;
                boolean $i$f$map = false;
                Iterable iterable = $this$map$iv;
                Collection destination$iv$iv = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                boolean $i$f$mapTo = false;
                for (T item$iv$iv : $this$mapTo$iv$iv) {
                    void it;
                    AbstractEntity abstractEntity = (AbstractEntity)item$iv$iv;
                    Collection collection2 = destination$iv$iv;
                    boolean bl = false;
                    collection2.add(it.getBukkitEntity());
                }
                return CollectionsKt.toSet((Iterable)((List)destination$iv$iv));
            }

            /*
             * WARNING - void declaration
             */
            public void setEntityTargets(@NotNull Set<? extends Entity> value2) {
                void $this$mapTo$iv$iv;
                void $this$map$iv;
                Intrinsics.checkNotNullParameter(value2, (String)"value");
                Iterable iterable = value2;
                SkillMetadataImpl skillMetadataImpl = (SkillMetadataImpl)this.$this_toUniversal;
                boolean $i$f$map = false;
                void var4_5 = $this$map$iv;
                Collection destination$iv$iv = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                boolean $i$f$mapTo = false;
                for (T item$iv$iv : $this$mapTo$iv$iv) {
                    void it;
                    Entity entity = (Entity)item$iv$iv;
                    Collection collection = destination$iv$iv;
                    boolean bl = false;
                    collection.add(UtilsKt.toMythic((Entity)it));
                }
                skillMetadataImpl.setEntityTargets((Collection)CollectionsKt.toHashSet((Iterable)((List)destination$iv$iv)));
            }

            /*
             * WARNING - void declaration
             */
            @NotNull
            public Set<Location> getLocationTargets() {
                void $this$mapTo$iv$iv;
                Collection collection = ((SkillMetadataImpl)this.$this_toUniversal).getLocationTargets();
                Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"getLocationTargets(...)");
                Iterable $this$map$iv = collection;
                boolean $i$f$map = false;
                Iterable iterable = $this$map$iv;
                Collection destination$iv$iv = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                boolean $i$f$mapTo = false;
                for (T item$iv$iv : $this$mapTo$iv$iv) {
                    void it;
                    AbstractLocation abstractLocation = (AbstractLocation)item$iv$iv;
                    Collection collection2 = destination$iv$iv;
                    boolean bl = false;
                    Intrinsics.checkNotNull((Object)it);
                    collection2.add(UtilsKt.toBukkit((AbstractLocation)it));
                }
                return CollectionsKt.toSet((Iterable)((List)destination$iv$iv));
            }

            /*
             * WARNING - void declaration
             */
            public void setLocationTargets(@NotNull Set<? extends Location> value2) {
                void $this$mapTo$iv$iv;
                void $this$map$iv;
                Intrinsics.checkNotNullParameter(value2, (String)"value");
                Iterable iterable = value2;
                SkillMetadataImpl skillMetadataImpl = (SkillMetadataImpl)this.$this_toUniversal;
                boolean $i$f$map = false;
                void var4_5 = $this$map$iv;
                Collection destination$iv$iv = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                boolean $i$f$mapTo = false;
                for (T item$iv$iv : $this$mapTo$iv$iv) {
                    void it;
                    Location location = (Location)item$iv$iv;
                    Collection collection = destination$iv$iv;
                    boolean bl = false;
                    collection.add(UtilsKt.toMythic((Location)it));
                }
                skillMetadataImpl.setLocationTargets((Collection)CollectionsKt.toHashSet((Iterable)((List)destination$iv$iv)));
            }

            @NotNull
            public Map<String, Object> getMetadata() {
                return this.metadataMap;
            }

            @NotNull
            public Map<String, String> getParameters() {
                return this.parameterMap;
            }

            public void setMetadata(@NotNull String key, @NotNull Object value2) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                ((Map)this.metadataMap).put(key, value2);
            }

            public void setParameter(@NotNull String key, @NotNull String value2) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                ((Map)this.parameterMap).put(key, value2);
            }

            @NotNull
            public SkillMeta clone() {
                SkillMetadataImpl skillMetadataImpl = ((SkillMetadataImpl)this.$this_toUniversal).clone();
                Intrinsics.checkNotNullExpressionValue((Object)skillMetadataImpl, (String)"clone(...)");
                return UtilsKt.toUniversal((SkillMetadata)skillMetadataImpl);
            }

            @NotNull
            public SkillMeta deepClone() {
                SkillMetadataImpl skillMetadataImpl = ((SkillMetadataImpl)this.$this_toUniversal).deepClone();
                Intrinsics.checkNotNullExpressionValue((Object)skillMetadataImpl, (String)"deepClone(...)");
                return UtilsKt.toUniversal((SkillMetadata)skillMetadataImpl);
            }
        };
    }
}

