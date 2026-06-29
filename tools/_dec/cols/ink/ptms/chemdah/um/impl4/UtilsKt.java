/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.um.impl4;

import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import ink.ptms.chemdah.um.Skill;
import ink.ptms.chemdah.um.impl4.Skill4;
import ink.ptms.chemdah.um.impl4.SkillCasterProxy4;
import ink.ptms.chemdah.um.skill.SkillConfig;
import ink.ptms.chemdah.um.skill.SkillMeta;
import ink.ptms.chemdah.um.skill.data.PlaceholderDouble;
import ink.ptms.chemdah.um.skill.data.PlaceholderInt;
import io.lumine.xikage.mythicmobs.adapters.AbstractEntity;
import io.lumine.xikage.mythicmobs.adapters.AbstractItemStack;
import io.lumine.xikage.mythicmobs.adapters.AbstractLocation;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitAdapter;
import io.lumine.xikage.mythicmobs.io.MythicLineConfig;
import io.lumine.xikage.mythicmobs.skills.SkillCaster;
import io.lumine.xikage.mythicmobs.skills.SkillMetadata;
import io.lumine.xikage.mythicmobs.skills.SkillTrigger;
import io.lumine.xikage.mythicmobs.skills.placeholders.parsers.PlaceholderFloat;
import io.lumine.xikage.mythicmobs.skills.placeholders.parsers.PlaceholderString;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 9, 0}, k=2, xi=48, d1={"\u0000X\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u001a\f\u0010\u0000\u001a\u00020\u0001*\u00020\u0002H\u0000\u001a\f\u0010\u0000\u001a\u00020\u0003*\u00020\u0004H\u0000\u001a\f\u0010\u0005\u001a\u00020\u0006*\u00020\u0007H\u0000\u001a\f\u0010\u0005\u001a\u00020\u0004*\u00020\u0003H\u0000\u001a\f\u0010\u0005\u001a\u00020\b*\u00020\tH\u0000\u001a\f\u0010\n\u001a\u00020\u000b*\u00020\fH\u0000\u001a\f\u0010\n\u001a\u00020\u0007*\u00020\u0006H\u0000\u001a\f\u0010\n\u001a\u00020\r*\u00020\u000eH\u0000\u001a\f\u0010\n\u001a\u00020\u000f*\u00020\u0010H\u0000\u001a\f\u0010\n\u001a\u00020\u0011*\u00020\u0012H\u0000\u001a\f\u0010\n\u001a\u00020\u0013*\u00020\u0014H\u0000\u001a\f\u0010\n\u001a\u00020\u0015*\u00020\u0016H\u0000\u00a8\u0006\u0017"}, d2={"toBukkit", "Lorg/bukkit/inventory/ItemStack;", "Lio/lumine/xikage/mythicmobs/adapters/AbstractItemStack;", "Lorg/bukkit/Location;", "Lio/lumine/xikage/mythicmobs/adapters/AbstractLocation;", "toMythic", "Lio/lumine/xikage/mythicmobs/skills/SkillCaster;", "Link/ptms/chemdah/um/skill/SkillCaster;", "Lio/lumine/xikage/mythicmobs/adapters/AbstractEntity;", "Lorg/bukkit/entity/Entity;", "toUniversal", "Link/ptms/chemdah/um/skill/SkillConfig;", "Lio/lumine/xikage/mythicmobs/io/MythicLineConfig;", "Link/ptms/chemdah/um/skill/SkillMeta;", "Lio/lumine/xikage/mythicmobs/skills/SkillMetadata;", "Link/ptms/chemdah/um/skill/data/PlaceholderDouble;", "Lio/lumine/xikage/mythicmobs/skills/placeholders/parsers/PlaceholderDouble;", "Link/ptms/chemdah/um/skill/data/PlaceholderFloat;", "Lio/lumine/xikage/mythicmobs/skills/placeholders/parsers/PlaceholderFloat;", "Link/ptms/chemdah/um/skill/data/PlaceholderInt;", "Lio/lumine/xikage/mythicmobs/skills/placeholders/parsers/PlaceholderInt;", "Link/ptms/chemdah/um/skill/data/PlaceholderString;", "Lio/lumine/xikage/mythicmobs/skills/placeholders/parsers/PlaceholderString;", "implementation-v4"})
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
        return new SkillCasterProxy4($this$toUniversal);
    }

    @NotNull
    public static final SkillCaster toMythic(@NotNull ink.ptms.chemdah.um.skill.SkillCaster $this$toMythic) {
        Intrinsics.checkNotNullParameter((Object)$this$toMythic, (String)"<this>");
        return ((SkillCasterProxy4)$this$toMythic).getOrigin();
    }

    @NotNull
    public static final ink.ptms.chemdah.um.skill.data.PlaceholderString toUniversal(@NotNull PlaceholderString $this$toUniversal) {
        Intrinsics.checkNotNullParameter((Object)$this$toUniversal, (String)"<this>");
        return new ink.ptms.chemdah.um.skill.data.PlaceholderString($this$toUniversal){
            final /* synthetic */ PlaceholderString $this_toUniversal;
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
    public static final PlaceholderInt toUniversal(@NotNull io.lumine.xikage.mythicmobs.skills.placeholders.parsers.PlaceholderInt $this$toUniversal) {
        Intrinsics.checkNotNullParameter((Object)$this$toUniversal, (String)"<this>");
        return new PlaceholderInt($this$toUniversal){
            final /* synthetic */ io.lumine.xikage.mythicmobs.skills.placeholders.parsers.PlaceholderInt $this_toUniversal;
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
    public static final PlaceholderDouble toUniversal(@NotNull io.lumine.xikage.mythicmobs.skills.placeholders.parsers.PlaceholderDouble $this$toUniversal) {
        Intrinsics.checkNotNullParameter((Object)$this$toUniversal, (String)"<this>");
        return new PlaceholderDouble($this$toUniversal){
            final /* synthetic */ io.lumine.xikage.mythicmobs.skills.placeholders.parsers.PlaceholderDouble $this_toUniversal;
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
    public static final ink.ptms.chemdah.um.skill.data.PlaceholderFloat toUniversal(@NotNull PlaceholderFloat $this$toUniversal) {
        Intrinsics.checkNotNullParameter((Object)$this$toUniversal, (String)"<this>");
        return new ink.ptms.chemdah.um.skill.data.PlaceholderFloat($this$toUniversal){
            final /* synthetic */ PlaceholderFloat $this_toUniversal;
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
        return new SkillConfig($this$toUniversal){
            final /* synthetic */ MythicLineConfig $this_toUniversal;
            {
                this.$this_toUniversal = $receiver;
            }

            @NotNull
            public String line() {
                String string = this.$this_toUniversal.getLine();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getLine(...)");
                return string;
            }

            public int size() {
                return this.$this_toUniversal.size();
            }

            @NotNull
            public Set<Map.Entry<String, Object>> entrySet() {
                Set set2 = this.$this_toUniversal.entrySet();
                Intrinsics.checkNotNullExpressionValue((Object)set2, (String)"entrySet(...)");
                return set2;
            }

            @NotNull
            public String getKey() {
                String string = this.$this_toUniversal.getKey();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"getKey(...)");
                return string;
            }

            @NotNull
            public String getKey(@NotNull String s) {
                Intrinsics.checkNotNullParameter((Object)s, (String)"s");
                String string = this.$this_toUniversal.getKey();
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
            public ink.ptms.chemdah.um.skill.data.PlaceholderString getPlaceholderString(@NotNull String[] key, @NotNull String def, String ... args) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)def, (String)"def");
                Intrinsics.checkNotNullParameter((Object)args, (String)"args");
                PlaceholderString placeholderString = this.$this_toUniversal.getPlaceholderString(key, def, Arrays.copyOf(args, args.length));
                Intrinsics.checkNotNullExpressionValue((Object)placeholderString, (String)"getPlaceholderString(...)");
                return UtilsKt.toUniversal(placeholderString);
            }

            public int getInt(@NotNull String[] key, int def) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                return this.$this_toUniversal.getInteger(key, def);
            }

            @NotNull
            public PlaceholderInt getPlaceholderInt(@NotNull String[] key, int def, String ... args) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)args, (String)"args");
                io.lumine.xikage.mythicmobs.skills.placeholders.parsers.PlaceholderInt placeholderInt = this.$this_toUniversal.getPlaceholderInteger(key, def, Arrays.copyOf(args, args.length));
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
                io.lumine.xikage.mythicmobs.skills.placeholders.parsers.PlaceholderDouble placeholderDouble = this.$this_toUniversal.getPlaceholderDouble(key, def, Arrays.copyOf(args, args.length));
                Intrinsics.checkNotNullExpressionValue((Object)placeholderDouble, (String)"getPlaceholderDouble(...)");
                return UtilsKt.toUniversal(placeholderDouble);
            }

            public float getFloat(@NotNull String[] key, float def) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                return this.$this_toUniversal.getFloat(key, def);
            }

            @NotNull
            public ink.ptms.chemdah.um.skill.data.PlaceholderFloat getPlaceholderFloat(@NotNull String[] key, float def, String ... args) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)args, (String)"args");
                PlaceholderFloat placeholderFloat = this.$this_toUniversal.getPlaceholderFloat(key, def, Arrays.copyOf(args, args.length));
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
                Color color = this.$this_toUniversal.getColor(key, def);
                Intrinsics.checkNotNullExpressionValue((Object)color, (String)"getColor(...)");
                return color;
            }

            @NotNull
            public Color getColor(@NotNull String[] key, @NotNull String def) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)def, (String)"def");
                Color color = this.$this_toUniversal.getColor(key, def);
                Intrinsics.checkNotNullExpressionValue((Object)color, (String)"getColor(...)");
                return color;
            }
        };
    }

    @NotNull
    public static final SkillMeta toUniversal(@NotNull SkillMetadata $this$toUniversal) {
        Intrinsics.checkNotNullParameter((Object)$this$toUniversal, (String)"<this>");
        return new SkillMeta($this$toUniversal){
            @NotNull
            private final HashMap<String, Object> metadataMap;
            final /* synthetic */ SkillMetadata $this_toUniversal;
            {
                this.$this_toUniversal = $receiver;
                Object object = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)$receiver, (String)"metadata", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
                Intrinsics.checkNotNull((Object)object);
                this.metadataMap = (HashMap)object;
            }

            @NotNull
            public final HashMap<String, Object> getMetadataMap() {
                return this.metadataMap;
            }

            @NotNull
            public ink.ptms.chemdah.um.skill.SkillCaster getCaster() {
                SkillCaster skillCaster = this.$this_toUniversal.getCaster();
                Intrinsics.checkNotNullExpressionValue((Object)skillCaster, (String)"getCaster(...)");
                return UtilsKt.toUniversal(skillCaster);
            }

            public void setCaster(@NotNull ink.ptms.chemdah.um.skill.SkillCaster value2) {
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                this.$this_toUniversal.setCaster(UtilsKt.toMythic(value2));
            }

            @NotNull
            public Entity getTrigger() {
                Entity entity = this.$this_toUniversal.getTrigger().getBukkitEntity();
                Intrinsics.checkNotNullExpressionValue((Object)entity, (String)"getBukkitEntity(...)");
                return entity;
            }

            public void setTrigger(@NotNull Entity value2) {
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                this.$this_toUniversal.setTrigger(UtilsKt.toMythic(value2));
            }

            @NotNull
            public Location getOrigin() {
                AbstractLocation abstractLocation = this.$this_toUniversal.getOrigin();
                Intrinsics.checkNotNullExpressionValue((Object)abstractLocation, (String)"getOrigin(...)");
                return UtilsKt.toBukkit(abstractLocation);
            }

            public void setOrigin(@NotNull Location value2) {
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                this.$this_toUniversal.setOrigin(UtilsKt.toMythic(value2));
            }

            @NotNull
            public Skill.Trigger getCause() {
                SkillTrigger skillTrigger = this.$this_toUniversal.getCause();
                Intrinsics.checkNotNullExpressionValue((Object)skillTrigger, (String)"getCause(...)");
                return new Skill4.Trigger(skillTrigger);
            }

            public float getPower() {
                return this.$this_toUniversal.getPower();
            }

            public void setPower(float value2) {
                this.$this_toUniversal.setPower(value2);
            }

            public boolean isAsync() {
                return this.$this_toUniversal.getIsAsync();
            }

            public void setAsync(boolean value2) {
                this.$this_toUniversal.setIsAsync(value2);
            }

            /*
             * WARNING - void declaration
             */
            @NotNull
            public Set<Entity> getEntityTargets() {
                void $this$mapTo$iv$iv;
                HashSet hashSet = this.$this_toUniversal.getEntityTargets();
                Intrinsics.checkNotNullExpressionValue((Object)hashSet, (String)"getEntityTargets(...)");
                Iterable $this$map$iv = hashSet;
                boolean $i$f$map = false;
                Iterable iterable = $this$map$iv;
                Collection destination$iv$iv = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                boolean $i$f$mapTo = false;
                for (T item$iv$iv : $this$mapTo$iv$iv) {
                    void it;
                    AbstractEntity abstractEntity = (AbstractEntity)item$iv$iv;
                    Collection collection = destination$iv$iv;
                    boolean bl = false;
                    collection.add(it.getBukkitEntity());
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
                SkillMetadata skillMetadata = this.$this_toUniversal;
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
                skillMetadata.setEntityTargets(CollectionsKt.toHashSet((Iterable)((List)destination$iv$iv)));
            }

            /*
             * WARNING - void declaration
             */
            @NotNull
            public Set<Location> getLocationTargets() {
                void $this$mapTo$iv$iv;
                HashSet hashSet = this.$this_toUniversal.getLocationTargets();
                Intrinsics.checkNotNullExpressionValue((Object)hashSet, (String)"getLocationTargets(...)");
                Iterable $this$map$iv = hashSet;
                boolean $i$f$map = false;
                Iterable iterable = $this$map$iv;
                Collection destination$iv$iv = new ArrayList<E>(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
                boolean $i$f$mapTo = false;
                for (T item$iv$iv : $this$mapTo$iv$iv) {
                    void it;
                    AbstractLocation abstractLocation = (AbstractLocation)item$iv$iv;
                    Collection collection = destination$iv$iv;
                    boolean bl = false;
                    Intrinsics.checkNotNull((Object)it);
                    collection.add(UtilsKt.toBukkit((AbstractLocation)it));
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
                SkillMetadata skillMetadata = this.$this_toUniversal;
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
                skillMetadata.setLocationTargets(CollectionsKt.toHashSet((Iterable)((List)destination$iv$iv)));
            }

            @NotNull
            public Map<String, Object> getMetadata() {
                return this.metadataMap;
            }

            @NotNull
            public Map<String, String> getParameters() {
                return MapsKt.emptyMap();
            }

            public void setMetadata(@NotNull String key, @NotNull Object value2) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
                ((Map)this.metadataMap).put(key, value2);
            }

            public void setParameter(@NotNull String key, @NotNull String value2) {
                Intrinsics.checkNotNullParameter((Object)key, (String)"key");
                Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
            }

            @NotNull
            public SkillMeta clone() {
                SkillMetadata skillMetadata = this.$this_toUniversal.clone();
                Intrinsics.checkNotNullExpressionValue((Object)skillMetadata, (String)"clone(...)");
                return UtilsKt.toUniversal(skillMetadata);
            }

            @NotNull
            public SkillMeta deepClone() {
                SkillMetadata skillMetadata = this.$this_toUniversal.deepClone();
                Intrinsics.checkNotNullExpressionValue((Object)skillMetadata, (String)"deepClone(...)");
                return UtilsKt.toUniversal(skillMetadata);
            }
        };
    }
}

