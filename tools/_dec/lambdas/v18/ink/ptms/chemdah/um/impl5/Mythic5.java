/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.Awake
 *  ink.ptms.chemdah.taboolib.common.util.OptionalKt
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex$Companion
 *  ink.ptms.chemdah.taboolib.module.nms.ItemTagData
 *  ink.ptms.chemdah.taboolib.module.nms.NMSItemTagKt
 *  io.lumine.mythic.api.MythicPlugin
 *  io.lumine.mythic.api.MythicProvider
 *  io.lumine.mythic.api.adapters.AbstractEntity
 *  io.lumine.mythic.api.adapters.AbstractItemStack
 *  io.lumine.mythic.api.adapters.AbstractPlayer
 *  io.lumine.mythic.api.config.MythicConfig
 *  io.lumine.mythic.api.drops.DropMetadata
 *  io.lumine.mythic.api.mobs.GenericCaster
 *  io.lumine.mythic.api.mobs.MobManager
 *  io.lumine.mythic.api.mobs.MythicMob
 *  io.lumine.mythic.api.skills.SkillCaster
 *  io.lumine.mythic.api.skills.SkillTrigger
 *  io.lumine.mythic.bukkit.BukkitAdapter
 *  io.lumine.mythic.bukkit.MythicBukkit
 *  io.lumine.mythic.core.config.MythicConfigImpl
 *  io.lumine.mythic.core.config.MythicLineConfigImpl
 *  io.lumine.mythic.core.drops.DropMetadataImpl
 *  io.lumine.mythic.core.items.ItemExecutor
 *  io.lumine.mythic.core.items.MythicItem
 *  io.lumine.mythic.core.mobs.ActiveMob
 *  io.lumine.mythic.core.mobs.MobExecutor
 *  io.lumine.mythic.core.mobs.MobType
 *  io.lumine.mythic.core.skills.SkillMechanic
 *  io.lumine.mythic.core.utils.MythicUtil
 *  kotlin.Metadata
 *  kotlin1822.Lazy
 *  kotlin1822.LazyKt
 *  kotlin1822.Result
 *  kotlin1822.ResultKt
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.jvm.optionals.OptionalsKt
 *  org.bukkit.Location
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.um.impl5;

import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.util.OptionalKt;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import ink.ptms.chemdah.taboolib.module.nms.ItemTagData;
import ink.ptms.chemdah.taboolib.module.nms.NMSItemTagKt;
import ink.ptms.chemdah.um.Item;
import ink.ptms.chemdah.um.Mob;
import ink.ptms.chemdah.um.MobType;
import ink.ptms.chemdah.um.Mythic;
import ink.ptms.chemdah.um.Skill;
import ink.ptms.chemdah.um.impl5.Skill5;
import ink.ptms.chemdah.um.impl5.UtilsKt;
import io.lumine.mythic.api.MythicPlugin;
import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.adapters.AbstractItemStack;
import io.lumine.mythic.api.adapters.AbstractPlayer;
import io.lumine.mythic.api.config.MythicConfig;
import io.lumine.mythic.api.drops.DropMetadata;
import io.lumine.mythic.api.mobs.GenericCaster;
import io.lumine.mythic.api.mobs.MobManager;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.SkillTrigger;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.config.MythicConfigImpl;
import io.lumine.mythic.core.config.MythicLineConfigImpl;
import io.lumine.mythic.core.drops.DropMetadataImpl;
import io.lumine.mythic.core.items.ItemExecutor;
import io.lumine.mythic.core.items.MythicItem;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.core.mobs.MobExecutor;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.MythicUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.LazyKt;
import kotlin1822.Result;
import kotlin1822.ResultKt;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.jvm.optionals.OptionalsKt;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u0098\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010%\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u001e\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\b\u0000\u0018\u00002\u00020\u0001:\u0001AB\u0005\u00a2\u0006\u0002\u0010\u0002JN\u0010\u0012\u001a\u00020\b2\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\f2\b\u0010\u0016\u001a\u0004\u0018\u00010\u00142\u0006\u0010\u0017\u001a\u00020\u00182\f\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\u00140\u001a2\f\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u00180\u001a2\u0006\u0010\u001c\u001a\u00020\u001dH\u0016J\b\u0010\u001e\u001a\u00020\u001fH\u0016J\u0012\u0010 \u001a\u0004\u0018\u00010!2\u0006\u0010\"\u001a\u00020\fH\u0016J\u000e\u0010#\u001a\b\u0012\u0004\u0012\u00020\f0$H\u0016J\u0012\u0010%\u001a\u0004\u0018\u00010\f2\u0006\u0010&\u001a\u00020'H\u0016J\u000e\u0010(\u001a\b\u0012\u0004\u0012\u00020!0$H\u0016J\u001c\u0010)\u001a\u0004\u0018\u00010'2\u0006\u0010\"\u001a\u00020\f2\b\u0010*\u001a\u0004\u0018\u00010+H\u0016J\u0012\u0010,\u001a\u0004\u0018\u00010-2\u0006\u0010.\u001a\u00020/H\u0016J\u0012\u0010,\u001a\u0004\u0018\u00010-2\u0006\u00100\u001a\u00020\u0014H\u0016J\u000e\u00101\u001a\b\u0012\u0004\u0012\u00020\f0$H\u0016J\u0012\u00102\u001a\u0004\u0018\u0001032\u0006\u0010\"\u001a\u00020\fH\u0016J\u0012\u00104\u001a\u0004\u0018\u0001052\u0006\u00106\u001a\u00020\fH\u0016J\u0010\u00107\u001a\u00020\u001f2\u0006\u0010\"\u001a\u00020\fH\u0016J\u0012\u00108\u001a\u0004\u0018\u0001092\u0006\u0010*\u001a\u00020+H\u0016J\u0018\u0010:\u001a\u00020\b2\u0006\u0010;\u001a\u00020<2\u0006\u0010=\u001a\u00020\fH\u0016J\u0018\u0010>\u001a\u00020\b2\u0006\u0010;\u001a\u00020<2\u0006\u0010=\u001a\u00020\fH\u0016J\u0010\u0010?\u001a\u00020\b2\u0006\u0010=\u001a\u00020\fH\u0016J\u0010\u0010@\u001a\u00020\b2\u0006\u0010=\u001a\u00020\fH\u0016R\u0011\u0010\u0003\u001a\u00020\u00048F\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006R\u0014\u0010\u0007\u001a\u00020\bX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\tR'\u0010\n\u001a\u000e\u0012\u0004\u0012\u00020\f\u0012\u0004\u0012\u00020\r0\u000b8FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0010\u0010\u0011\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006B"}, d2={"Link/ptms/chemdah/um/impl5/Mythic5;", "Link/ptms/chemdah/um/Mythic;", "()V", "api", "Lio/lumine/mythic/bukkit/MythicBukkit;", "getApi", "()Lio/lumine/mythic/bukkit/MythicBukkit;", "isLegacy", "", "()Z", "mmList", "", "", "Lio/lumine/mythic/api/mobs/MythicMob;", "getMmList", "()Ljava/util/Map;", "mmList$delegate", "Lkotlin1822/Lazy;", "castSkill", "caster", "Lorg/bukkit/entity/Entity;", "skillName", "trigger", "origin", "Lorg/bukkit/Location;", "et", "", "lt", "power", "", "getDefaultSkillTrigger", "Link/ptms/chemdah/um/Skill$Trigger;", "getItem", "Link/ptms/chemdah/um/Item;", "name", "getItemIDList", "", "getItemId", "itemStack", "Lorg/bukkit/inventory/ItemStack;", "getItemList", "getItemStack", "player", "Lorg/bukkit/entity/Player;", "getMob", "Link/ptms/chemdah/um/Mob;", "uuid", "Ljava/util/UUID;", "entity", "getMobIDList", "getMobType", "Link/ptms/chemdah/um/MobType;", "getSkillMechanic", "Link/ptms/chemdah/um/Skill;", "skillLine", "getSkillTrigger", "getTargetedEntity", "Lorg/bukkit/entity/LivingEntity;", "registerItem", "file", "Ljava/io/File;", "node", "registerMob", "unregisterItem", "unregisterMob", "Loader", "implementation-v5"})
@SourceDebugExtension(value={"SMAP\nMythic5.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Mythic5.kt\nink/ptms/um/impl5/Mythic5\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,165:1\n1#2:166\n1549#3:167\n1620#3,3:168\n1549#3:171\n1620#3,3:172\n*S KotlinDebug\n*F\n+ 1 Mythic5.kt\nink/ptms/um/impl5/Mythic5\n*L\n66#1:167\n66#1:168,3\n70#1:171\n70#1:172,3\n*E\n"})
public final class Mythic5
implements Mythic {
    @NotNull
    private final Lazy mmList$delegate = LazyKt.lazy((Function0)((Function0)new Function0<Map<String, MythicMob>>(this){
        final /* synthetic */ Mythic5 this$0;
        {
            this.this$0 = $receiver;
            super(0);
        }

        @NotNull
        public final Map<String, MythicMob> invoke() {
            MobExecutor mobExecutor = this.this$0.getApi().getMobManager();
            Intrinsics.checkNotNullExpressionValue((Object)mobExecutor, (String)"getMobManager(...)");
            Object object = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)mobExecutor, (String)"mmList", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
            Intrinsics.checkNotNull((Object)object);
            return (Map)object;
        }
    }));
    private final boolean isLegacy;

    @NotNull
    public final MythicBukkit getApi() {
        MythicPlugin mythicPlugin = MythicProvider.get();
        Intrinsics.checkNotNull((Object)mythicPlugin, (String)"null cannot be cast to non-null type io.lumine.mythic.bukkit.MythicBukkit");
        return (MythicBukkit)mythicPlugin;
    }

    @NotNull
    public final Map<String, MythicMob> getMmList() {
        Lazy lazy = this.mmList$delegate;
        return (Map)lazy.getValue();
    }

    @Override
    public boolean isLegacy() {
        return this.isLegacy;
    }

    @Override
    @Nullable
    public Item getItem(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Optional optional = this.getApi().getItemManager().getItem(name);
        Intrinsics.checkNotNullExpressionValue((Object)optional, (String)"getItem(...)");
        MythicItem mythicItem = (MythicItem)OptionalKt.orNull((Optional)optional);
        if (mythicItem == null) {
            return null;
        }
        return new ink.ptms.chemdah.um.impl5.Item(mythicItem);
    }

    @Override
    @Nullable
    public String getItemId(@NotNull ItemStack itemStack) {
        Intrinsics.checkNotNullParameter((Object)itemStack, (String)"itemStack");
        ItemTagData itemTagData = (ItemTagData)NMSItemTagKt.getItemTag$default((ItemStack)itemStack, (boolean)false, (int)1, null).get((Object)"MYTHIC_TYPE");
        return itemTagData != null ? itemTagData.asString() : null;
    }

    @Override
    @Nullable
    public ItemStack getItemStack(@NotNull String name, @Nullable Player player) {
        Object object;
        block10: {
            Object object2;
            block9: {
                ItemStack itemStack;
                AbstractItemStack abstractItemStack;
                DropMetadataImpl meta;
                DropMetadataImpl dropMetadataImpl;
                AbstractPlayer target;
                AbstractPlayer abstractPlayer;
                Intrinsics.checkNotNullParameter((Object)name, (String)"name");
                Player player2 = player;
                if (player2 != null) {
                    Player it = player2;
                    boolean bl = false;
                    abstractPlayer = BukkitAdapter.adapt((Player)it);
                } else {
                    abstractPlayer = null;
                }
                AbstractPlayer abstractPlayer2 = target = abstractPlayer;
                if (abstractPlayer2 != null) {
                    AbstractPlayer it = abstractPlayer2;
                    boolean bl = false;
                    dropMetadataImpl = new DropMetadataImpl((SkillCaster)new GenericCaster((AbstractEntity)target), (AbstractEntity)target);
                } else {
                    dropMetadataImpl = null;
                }
                if ((object = (meta = dropMetadataImpl)) == null) break block9;
                DropMetadataImpl it = object;
                boolean bl = false;
                Object object3 = this.getApi().getItemManager();
                if (object3 != null && (object3 = object3.getItem(name)) != null && (object3 = (MythicItem)((Optional)object3).get()) != null && (object3 = (abstractItemStack = object3.generateItemStack((DropMetadata)it, 1))) != null) {
                    Intrinsics.checkNotNull((Object)object3);
                    itemStack = UtilsKt.toBukkit(abstractItemStack);
                } else {
                    itemStack = null;
                }
                object = itemStack;
                if (itemStack != null) break block10;
            }
            object = (object2 = this.getApi().getItemManager()) != null && (object2 = object2.getItem(name)) != null && (object2 = (MythicItem)((Optional)object2).get()) != null && (object2 = object2.generateItemStack(1)) != null ? UtilsKt.toBukkit((AbstractItemStack)object2) : null;
        }
        return object;
    }

    /*
     * WARNING - void declaration
     */
    @Override
    @NotNull
    public List<String> getItemIDList() {
        void $this$mapTo$iv$iv;
        Collection collection = this.getApi().getItemManager().getItems();
        Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"getItems(...)");
        Iterable $this$map$iv = collection;
        boolean $i$f$map = false;
        Iterable iterable = $this$map$iv;
        Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            void it;
            MythicItem mythicItem = (MythicItem)item$iv$iv;
            Collection collection2 = destination$iv$iv;
            boolean bl = false;
            collection2.add(it.getInternalName());
        }
        return (List)destination$iv$iv;
    }

    /*
     * WARNING - void declaration
     */
    @Override
    @NotNull
    public List<Item> getItemList() {
        void $this$mapTo$iv$iv;
        Collection collection = this.getApi().getItemManager().getItems();
        Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"getItems(...)");
        Iterable $this$map$iv = collection;
        boolean $i$f$map = false;
        Iterable iterable = $this$map$iv;
        Collection destination$iv$iv = new ArrayList(CollectionsKt.collectionSizeOrDefault((Iterable)$this$map$iv, (int)10));
        boolean $i$f$mapTo = false;
        for (Object item$iv$iv : $this$mapTo$iv$iv) {
            void it;
            MythicItem mythicItem = (MythicItem)item$iv$iv;
            Collection collection2 = destination$iv$iv;
            boolean bl = false;
            Intrinsics.checkNotNull((Object)it);
            collection2.add(new ink.ptms.chemdah.um.impl5.Item((MythicItem)it));
        }
        return (List)destination$iv$iv;
    }

    @Override
    @Nullable
    public Mob getMob(@NotNull Entity entity) {
        Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
        MobManager mobManager2 = MythicProvider.get().getMobManager();
        Intrinsics.checkNotNull((Object)mobManager2, (String)"null cannot be cast to non-null type io.lumine.mythic.core.mobs.MobExecutor");
        ActiveMob activeMob = ((MobExecutor)mobManager2).getMythicMobInstance(entity);
        if (activeMob == null) {
            return null;
        }
        return new ink.ptms.chemdah.um.impl5.Mob(activeMob);
    }

    @Override
    @Nullable
    public Mob getMob(@NotNull UUID uuid) {
        Intrinsics.checkNotNullParameter((Object)uuid, (String)"uuid");
        MobManager mobManager2 = MythicProvider.get().getMobManager();
        Intrinsics.checkNotNull((Object)mobManager2, (String)"null cannot be cast to non-null type io.lumine.mythic.core.mobs.MobExecutor");
        Optional optional = ((MobExecutor)mobManager2).getActiveMob(uuid);
        Intrinsics.checkNotNullExpressionValue((Object)optional, (String)"getActiveMob(...)");
        ActiveMob activeMob = (ActiveMob)OptionalsKt.getOrNull((Optional)optional);
        if (activeMob == null) {
            return null;
        }
        return new ink.ptms.chemdah.um.impl5.Mob(activeMob);
    }

    @Override
    @NotNull
    public List<String> getMobIDList() {
        Collection collection = this.getApi().getMobManager().getMobNames();
        Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"getMobNames(...)");
        return CollectionsKt.toList((Iterable)collection);
    }

    @Override
    @Nullable
    public MobType getMobType(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Optional optional = this.getApi().getMobManager().getMythicMob(name);
        Intrinsics.checkNotNullExpressionValue((Object)optional, (String)"getMythicMob(...)");
        MythicMob mythicMob = (MythicMob)OptionalKt.orNull((Optional)optional);
        if (mythicMob == null) {
            return null;
        }
        return new ink.ptms.chemdah.um.impl5.MobType(mythicMob);
    }

    @Override
    @NotNull
    public Skill.Trigger getSkillTrigger(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Class<?> clazz = Class.forName(SkillTrigger.class.getName());
        Intrinsics.checkNotNullExpressionValue(clazz, (String)"forName(...)");
        Object[] objectArray = new Object[1];
        Intrinsics.checkNotNullExpressionValue((Object)name.toUpperCase(Locale.ROOT), (String)"toUpperCase(...)");
        Object object = Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, clazz, (String)"get", (Object[])objectArray, (boolean)true, (boolean)false, (boolean)false, null, (int)56, null);
        if (object == null) {
            return this.getDefaultSkillTrigger();
        }
        Object invokeMethod = object;
        return new Skill5.Trigger(invokeMethod);
    }

    @Override
    @NotNull
    public Skill.Trigger getDefaultSkillTrigger() {
        Object invokeMethod;
        Class<?> clazz = Class.forName(SkillTrigger.class.getName());
        Intrinsics.checkNotNullExpressionValue(clazz, (String)"forName(...)");
        Object[] objectArray = new Object[]{"DEFAULT"};
        Object object = invokeMethod = Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, clazz, (String)"get", (Object[])objectArray, (boolean)true, (boolean)false, (boolean)false, null, (int)56, null);
        Intrinsics.checkNotNull((Object)object);
        return new Skill5.Trigger(object);
    }

    @Override
    @Nullable
    public Skill getSkillMechanic(@NotNull String skillLine) {
        Intrinsics.checkNotNullParameter((Object)skillLine, (String)"skillLine");
        SkillMechanic skillMechanic = this.getApi().getSkillManager().getMechanic(MythicLineConfigImpl.unparseBlock((String)skillLine));
        if (skillMechanic == null) {
            return null;
        }
        return new Skill5(skillMechanic);
    }

    @Override
    @Nullable
    public LivingEntity getTargetedEntity(@NotNull Player player) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        return MythicUtil.getTargetedEntity((Player)player);
    }

    @Override
    public boolean castSkill(@NotNull Entity caster, @NotNull String skillName, @Nullable Entity trigger2, @NotNull Location origin, @NotNull Collection<? extends Entity> et, @NotNull Collection<? extends Location> lt, float power) {
        Intrinsics.checkNotNullParameter((Object)caster, (String)"caster");
        Intrinsics.checkNotNullParameter((Object)skillName, (String)"skillName");
        Intrinsics.checkNotNullParameter((Object)origin, (String)"origin");
        Intrinsics.checkNotNullParameter(et, (String)"et");
        Intrinsics.checkNotNullParameter(lt, (String)"lt");
        return this.getApi().getAPIHelper().castSkill(caster, skillName, trigger2, origin, et, lt, power);
    }

    @Override
    public boolean registerItem(@NotNull File file, @NotNull String node) {
        Intrinsics.checkNotNullParameter((Object)file, (String)"file");
        Intrinsics.checkNotNullParameter((Object)node, (String)"node");
        return this.getApi().getItemManager().registerItem(node, new MythicItem(null, file, node, (MythicConfig)new MythicConfigImpl(node, file, (FileConfiguration)YamlConfiguration.loadConfiguration((File)file))));
    }

    @Override
    public boolean unregisterItem(@NotNull String node) {
        boolean bl;
        Intrinsics.checkNotNullParameter((Object)node, (String)"node");
        ItemExecutor itemExecutor = this.getApi().getItemManager();
        Intrinsics.checkNotNullExpressionValue((Object)itemExecutor, (String)"getItemManager(...)");
        Object object = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)itemExecutor, (String)"items", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
        Intrinsics.checkNotNull((Object)object);
        HashMap it = (HashMap)object;
        boolean bl2 = false;
        if (it.containsKey(node)) {
            it.remove(node);
            bl = true;
        } else {
            bl = false;
        }
        return bl;
    }

    @Override
    public boolean registerMob(@NotNull File file, @NotNull String node) {
        boolean bl;
        Intrinsics.checkNotNullParameter((Object)file, (String)"file");
        Intrinsics.checkNotNullParameter((Object)node, (String)"node");
        if (!this.getMmList().containsKey(node)) {
            this.getMmList().put(node, (MythicMob)new io.lumine.mythic.core.mobs.MobType(MythicBukkit.inst().getMobManager(), null, file, node, (MythicConfig)new MythicConfigImpl(node, file, (FileConfiguration)YamlConfiguration.loadConfiguration((File)file))));
            bl = true;
        } else {
            bl = false;
        }
        return bl;
    }

    @Override
    public boolean unregisterMob(@NotNull String node) {
        boolean bl;
        Intrinsics.checkNotNullParameter((Object)node, (String)"node");
        if (this.getMmList().containsKey(node)) {
            this.getMmList().remove(node);
            bl = true;
        } else {
            bl = false;
        }
        return bl;
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H\u0007\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/um/impl5/Mythic5$Loader;", "", "()V", "setup", "", "implementation-v5"})
    @SourceDebugExtension(value={"SMAP\nMythic5.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Mythic5.kt\nink/ptms/um/impl5/Mythic5$Loader\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,165:1\n1#2:166\n*E\n"})
    public static final class Loader {
        @NotNull
        public static final Loader INSTANCE = new Loader();

        private Loader() {
        }

        @Awake(value=LifeCycle.ENABLE)
        public final void setup() {
            Object object;
            Object object2 = this;
            try {
                Loader $this$setup_u24lambda_u240 = object2;
                boolean bl = false;
                object = Result.constructor-impl(Class.forName("io.lumine.mythic.api.MythicProvider"));
            }
            catch (Throwable throwable) {
                object = Result.constructor-impl((Object)ResultKt.createFailure((Throwable)throwable));
            }
            object2 = object;
            if ((Result.isFailure-impl((Object)object2) ? null : object2) != null) {
                Mythic.Companion.setAPI(new Mythic5());
            }
        }
    }
}

