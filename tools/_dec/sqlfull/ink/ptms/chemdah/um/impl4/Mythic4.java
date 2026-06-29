/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.um.impl4;

import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.util.LazyMakerKt;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import ink.ptms.chemdah.taboolib.module.nms.NMSTranslateKt;
import ink.ptms.chemdah.um.Mob;
import ink.ptms.chemdah.um.Mythic;
import ink.ptms.chemdah.um.Skill;
import ink.ptms.chemdah.um.impl4.Item;
import ink.ptms.chemdah.um.impl4.MobType;
import ink.ptms.chemdah.um.impl4.Skill4;
import ink.ptms.chemdah.um.impl4.UtilsKt;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.adapters.AbstractEntity;
import io.lumine.xikage.mythicmobs.adapters.AbstractItemStack;
import io.lumine.xikage.mythicmobs.adapters.AbstractPlayer;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitAdapter;
import io.lumine.xikage.mythicmobs.drops.DropMetadata;
import io.lumine.xikage.mythicmobs.io.MythicConfig;
import io.lumine.xikage.mythicmobs.io.MythicLineConfig;
import io.lumine.xikage.mythicmobs.items.ItemManager;
import io.lumine.xikage.mythicmobs.items.MythicItem;
import io.lumine.xikage.mythicmobs.mobs.ActiveMob;
import io.lumine.xikage.mythicmobs.mobs.GenericCaster;
import io.lumine.xikage.mythicmobs.mobs.MobManager;
import io.lumine.xikage.mythicmobs.mobs.MythicMob;
import io.lumine.xikage.mythicmobs.skills.SkillCaster;
import io.lumine.xikage.mythicmobs.skills.SkillManager;
import io.lumine.xikage.mythicmobs.skills.SkillMechanic;
import io.lumine.xikage.mythicmobs.skills.SkillTrigger;
import io.lumine.xikage.mythicmobs.util.MythicUtil;
import io.lumine.xikage.mythicmobs.utils.config.file.FileConfiguration;
import io.lumine.xikage.mythicmobs.utils.config.file.YamlConfiguration;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.Result;
import kotlin1822.ResultKt;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.jvm.optionals.OptionalsKt;
import kotlin1822.text.StringsKt;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u00b0\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u001e\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\b\u0000\u0018\u00002\u00020\u0001:\u0001PB\u0005\u00a2\u0006\u0002\u0010\u0002JN\u0010!\u001a\u00020\b2\u0006\u0010\"\u001a\u00020#2\u0006\u0010$\u001a\u00020\u00122\b\u0010%\u001a\u0004\u0018\u00010#2\u0006\u0010&\u001a\u00020'2\f\u0010(\u001a\b\u0012\u0004\u0012\u00020#0)2\f\u0010*\u001a\b\u0012\u0004\u0012\u00020'0)2\u0006\u0010+\u001a\u00020,H\u0016J\b\u0010-\u001a\u00020.H\u0016J\u0012\u0010/\u001a\u0004\u0018\u0001002\u0006\u00101\u001a\u00020\u0012H\u0016J\u000e\u00102\u001a\b\u0012\u0004\u0012\u00020\u001203H\u0016J\u0012\u00104\u001a\u0004\u0018\u00010\u00122\u0006\u00105\u001a\u000206H\u0016J\u000e\u00107\u001a\b\u0012\u0004\u0012\u00020003H\u0016J\u001c\u00108\u001a\u0004\u0018\u0001062\u0006\u00101\u001a\u00020\u00122\b\u00109\u001a\u0004\u0018\u00010:H\u0016J\u0012\u0010;\u001a\u0004\u0018\u00010<2\u0006\u0010=\u001a\u00020>H\u0016J\u0012\u0010;\u001a\u0004\u0018\u00010<2\u0006\u0010?\u001a\u00020#H\u0016J\u000e\u0010@\u001a\b\u0012\u0004\u0012\u00020\u001203H\u0016J\u0012\u0010A\u001a\u0004\u0018\u00010B2\u0006\u00101\u001a\u00020\u0012H\u0016J\u0012\u0010C\u001a\u0004\u0018\u00010D2\u0006\u0010E\u001a\u00020\u0012H\u0016J\u0010\u0010F\u001a\u00020.2\u0006\u00101\u001a\u00020\u0012H\u0016J\u0012\u0010G\u001a\u0004\u0018\u00010H2\u0006\u00109\u001a\u00020:H\u0016J\u0018\u0010I\u001a\u00020\b2\u0006\u0010J\u001a\u00020K2\u0006\u0010L\u001a\u00020\u0012H\u0016J\u0018\u0010M\u001a\u00020\b2\u0006\u0010J\u001a\u00020K2\u0006\u0010L\u001a\u00020\u0012H\u0016J\u0010\u0010N\u001a\u00020\b2\u0006\u0010L\u001a\u00020\u0012H\u0016J\u0010\u0010O\u001a\u00020\b2\u0006\u0010L\u001a\u00020\u0012H\u0016R\u0011\u0010\u0003\u001a\u00020\u00048F\u00a2\u0006\u0006\u001a\u0004\b\u0005\u0010\u0006R\u0014\u0010\u0007\u001a\u00020\bX\u0096D\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\tR\u001b\u0010\n\u001a\u00020\u000b8FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u000e\u0010\u000f\u001a\u0004\b\f\u0010\rR'\u0010\u0010\u001a\u000e\u0012\u0004\u0012\u00020\u0012\u0012\u0004\u0012\u00020\u00130\u00118FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0016\u0010\u000f\u001a\u0004\b\u0014\u0010\u0015R\u001b\u0010\u0017\u001a\u00020\u00188FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u001b\u0010\u000f\u001a\u0004\b\u0019\u0010\u001aR\u001b\u0010\u001c\u001a\u00020\u001d8FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b \u0010\u000f\u001a\u0004\b\u001e\u0010\u001f\u00a8\u0006Q"}, d2={"Link/ptms/chemdah/um/impl4/Mythic4;", "Link/ptms/chemdah/um/Mythic;", "()V", "api", "Lio/lumine/xikage/mythicmobs/MythicMobs;", "getApi", "()Lio/lumine/xikage/mythicmobs/MythicMobs;", "isLegacy", "", "()Z", "itemManager", "Lio/lumine/xikage/mythicmobs/items/ItemManager;", "getItemManager", "()Lio/lumine/xikage/mythicmobs/items/ItemManager;", "itemManager$delegate", "Lkotlin1822/Lazy;", "mmList", "Ljava/util/concurrent/ConcurrentHashMap;", "", "Lio/lumine/xikage/mythicmobs/mobs/MythicMob;", "getMmList", "()Ljava/util/concurrent/ConcurrentHashMap;", "mmList$delegate", "mobManager", "Lio/lumine/xikage/mythicmobs/mobs/MobManager;", "getMobManager", "()Lio/lumine/xikage/mythicmobs/mobs/MobManager;", "mobManager$delegate", "skillManager", "Lio/lumine/xikage/mythicmobs/skills/SkillManager;", "getSkillManager", "()Lio/lumine/xikage/mythicmobs/skills/SkillManager;", "skillManager$delegate", "castSkill", "caster", "Lorg/bukkit/entity/Entity;", "skillName", "trigger", "origin", "Lorg/bukkit/Location;", "et", "", "lt", "power", "", "getDefaultSkillTrigger", "Link/ptms/chemdah/um/Skill$Trigger;", "getItem", "Link/ptms/chemdah/um/Item;", "name", "getItemIDList", "", "getItemId", "itemStack", "Lorg/bukkit/inventory/ItemStack;", "getItemList", "getItemStack", "player", "Lorg/bukkit/entity/Player;", "getMob", "Link/ptms/chemdah/um/Mob;", "uuid", "Ljava/util/UUID;", "entity", "getMobIDList", "getMobType", "Link/ptms/chemdah/um/MobType;", "getSkillMechanic", "Link/ptms/chemdah/um/Skill;", "skillLine", "getSkillTrigger", "getTargetedEntity", "Lorg/bukkit/entity/LivingEntity;", "registerItem", "file", "Ljava/io/File;", "node", "registerMob", "unregisterItem", "unregisterMob", "Loader", "implementation-v4"})
@SourceDebugExtension(value={"SMAP\nMythic4.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Mythic4.kt\nink/ptms/um/impl4/Mythic4\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,162:1\n288#2,2:163\n1549#2:166\n1620#2,3:167\n1549#2:170\n1620#2,3:171\n1#3:165\n*S KotlinDebug\n*F\n+ 1 Mythic4.kt\nink/ptms/um/impl4/Mythic4\n*L\n67#1:163,2\n78#1:166\n78#1:167,3\n82#1:170\n82#1:171,3\n*E\n"})
public final class Mythic4
implements Mythic {
    @NotNull
    private final Lazy mobManager$delegate = LazyMakerKt.unsafeLazy((Function0)((Function0)new Function0<MobManager>(this){
        final /* synthetic */ Mythic4 this$0;
        {
            this.this$0 = $receiver;
            super(0);
        }

        public final MobManager invoke() {
            return this.this$0.getApi().getMobManager();
        }
    }));
    @NotNull
    private final Lazy itemManager$delegate = LazyMakerKt.unsafeLazy((Function0)((Function0)new Function0<ItemManager>(this){
        final /* synthetic */ Mythic4 this$0;
        {
            this.this$0 = $receiver;
            super(0);
        }

        public final ItemManager invoke() {
            return this.this$0.getApi().getItemManager();
        }
    }));
    @NotNull
    private final Lazy skillManager$delegate = LazyMakerKt.unsafeLazy((Function0)((Function0)new Function0<SkillManager>(this){
        final /* synthetic */ Mythic4 this$0;
        {
            this.this$0 = $receiver;
            super(0);
        }

        public final SkillManager invoke() {
            return this.this$0.getApi().getSkillManager();
        }
    }));
    @NotNull
    private final Lazy mmList$delegate = LazyMakerKt.unsafeLazy((Function0)((Function0)new Function0<ConcurrentHashMap<String, MythicMob>>(this){
        final /* synthetic */ Mythic4 this$0;
        {
            this.this$0 = $receiver;
            super(0);
        }

        @NotNull
        public final ConcurrentHashMap<String, MythicMob> invoke() {
            Object object = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)this.this$0.getMobManager(), (String)"mmList", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
            Intrinsics.checkNotNull((Object)object);
            return (ConcurrentHashMap)object;
        }
    }));
    private final boolean isLegacy;

    public Mythic4() {
        this.isLegacy = true;
    }

    @NotNull
    public final MythicMobs getApi() {
        MythicMobs mythicMobs = MythicMobs.inst();
        Intrinsics.checkNotNullExpressionValue((Object)mythicMobs, (String)"inst(...)");
        return mythicMobs;
    }

    @NotNull
    public final MobManager getMobManager() {
        Lazy lazy = this.mobManager$delegate;
        Object object = lazy.getValue();
        Intrinsics.checkNotNullExpressionValue((Object)object, (String)"getValue(...)");
        return (MobManager)object;
    }

    @NotNull
    public final ItemManager getItemManager() {
        Lazy lazy = this.itemManager$delegate;
        Object object = lazy.getValue();
        Intrinsics.checkNotNullExpressionValue((Object)object, (String)"getValue(...)");
        return (ItemManager)object;
    }

    @NotNull
    public final SkillManager getSkillManager() {
        Lazy lazy = this.skillManager$delegate;
        Object object = lazy.getValue();
        Intrinsics.checkNotNullExpressionValue((Object)object, (String)"getValue(...)");
        return (SkillManager)object;
    }

    @NotNull
    public final ConcurrentHashMap<String, MythicMob> getMmList() {
        Lazy lazy = this.mmList$delegate;
        return (ConcurrentHashMap)lazy.getValue();
    }

    @Override
    public boolean isLegacy() {
        return this.isLegacy;
    }

    @Override
    @Nullable
    public LivingEntity getTargetedEntity(@NotNull Player player) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        return MythicUtil.getTargetedEntity((Player)player);
    }

    @Override
    @Nullable
    public ink.ptms.chemdah.um.Item getItem(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Optional optional = this.getItemManager().getItem(name);
        if (optional == null || (optional = (MythicItem)optional.get()) == null) {
            return null;
        }
        return new Item((MythicItem)optional);
    }

    @Override
    @Nullable
    public String getItemId(@NotNull ItemStack itemStack) {
        Object v0;
        block1: {
            Intrinsics.checkNotNullParameter((Object)itemStack, (String)"itemStack");
            Iterable $this$firstOrNull$iv = this.getItemList();
            boolean $i$f$firstOrNull = false;
            for (Object element$iv : $this$firstOrNull$iv) {
                ink.ptms.chemdah.um.Item item2 = (ink.ptms.chemdah.um.Item)element$iv;
                boolean bl = false;
                if (!StringsKt.equals((String)NMSTranslateKt.getName$default((ItemStack)itemStack, null, (int)1, null), (String)item2.getDisplayName(), (boolean)true)) continue;
                v0 = element$iv;
                break block1;
            }
            v0 = null;
        }
        ink.ptms.chemdah.um.Item item3 = v0;
        return item3 != null ? item3.getInternalName() : null;
    }

    @Override
    @Nullable
    public ItemStack getItemStack(@NotNull String name, @Nullable Player player) {
        DropMetadata dropMetadata;
        block10: {
            block9: {
                ItemStack itemStack;
                AbstractItemStack abstractItemStack;
                DropMetadata meta;
                DropMetadata dropMetadata2;
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
                    dropMetadata2 = new DropMetadata((SkillCaster)new GenericCaster((AbstractEntity)target), (AbstractEntity)target);
                } else {
                    dropMetadata2 = null;
                }
                if ((dropMetadata = (meta = dropMetadata2)) == null) break block9;
                DropMetadata it = dropMetadata;
                boolean bl = false;
                Optional optional = this.getItemManager().getItem(name);
                if (optional != null && (optional = (MythicItem)optional.get()) != null && (optional = (abstractItemStack = optional.generateItemStack(it, 1))) != null) {
                    Intrinsics.checkNotNull((Object)optional);
                    itemStack = UtilsKt.toBukkit(abstractItemStack);
                } else {
                    itemStack = null;
                }
                dropMetadata = itemStack;
                if (itemStack != null) break block10;
            }
            dropMetadata = this.getItemManager().getItemStack(name);
        }
        return dropMetadata;
    }

    /*
     * WARNING - void declaration
     */
    @Override
    @NotNull
    public List<String> getItemIDList() {
        void $this$mapTo$iv$iv;
        Collection collection = this.getItemManager().getItems();
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
    public List<ink.ptms.chemdah.um.Item> getItemList() {
        void $this$mapTo$iv$iv;
        Collection collection = this.getItemManager().getItems();
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
            collection2.add(new Item((MythicItem)it));
        }
        return (List)destination$iv$iv;
    }

    @Override
    @Nullable
    public Mob getMob(@NotNull Entity entity) {
        Intrinsics.checkNotNullParameter((Object)entity, (String)"entity");
        ActiveMob activeMob = this.getMobManager().getMythicMobInstance(entity);
        if (activeMob == null) {
            return null;
        }
        return new ink.ptms.chemdah.um.impl4.Mob(activeMob);
    }

    @Override
    @Nullable
    public Mob getMob(@NotNull UUID uuid) {
        Intrinsics.checkNotNullParameter((Object)uuid, (String)"uuid");
        Optional optional = this.getMobManager().getActiveMob(uuid);
        Intrinsics.checkNotNullExpressionValue((Object)optional, (String)"getActiveMob(...)");
        ActiveMob activeMob = (ActiveMob)OptionalsKt.getOrNull((Optional)optional);
        if (activeMob == null) {
            return null;
        }
        return new ink.ptms.chemdah.um.impl4.Mob(activeMob);
    }

    @Override
    @NotNull
    public List<String> getMobIDList() {
        Collection collection = this.getMobManager().getMobNames();
        Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"getMobNames(...)");
        return CollectionsKt.toList((Iterable)collection);
    }

    @Override
    @Nullable
    public ink.ptms.chemdah.um.MobType getMobType(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        MythicMob mythicMob = this.getMobManager().getMythicMob(name);
        if (mythicMob == null) {
            return null;
        }
        return new MobType(mythicMob);
    }

    @Override
    @NotNull
    public Skill.Trigger getSkillTrigger(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        String string = name.toUpperCase(Locale.ROOT);
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"toUpperCase(...)");
        return new Skill4.Trigger(SkillTrigger.valueOf((String)string));
    }

    @Override
    @NotNull
    public Skill.Trigger getDefaultSkillTrigger() {
        return new Skill4.Trigger(SkillTrigger.DEFAULT);
    }

    @Override
    @Nullable
    public Skill getSkillMechanic(@NotNull String skillLine) {
        Intrinsics.checkNotNullParameter((Object)skillLine, (String)"skillLine");
        SkillMechanic skillMechanic = this.getSkillManager().getSkillMechanic(MythicLineConfig.unparseBlock((String)skillLine));
        if (skillMechanic == null) {
            return null;
        }
        return new Skill4(skillMechanic);
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
        return this.getItemManager().registerItem(node, new MythicItem(file.getName(), node, new MythicConfig(node, file, (FileConfiguration)YamlConfiguration.loadConfiguration((File)file))));
    }

    @Override
    public boolean unregisterItem(@NotNull String node) {
        boolean bl;
        Intrinsics.checkNotNullParameter((Object)node, (String)"node");
        Object object = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)this.getItemManager(), (String)"items", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
        Intrinsics.checkNotNull((Object)object);
        ConcurrentHashMap it = (ConcurrentHashMap)object;
        boolean bl2 = false;
        if (it.contains(node)) {
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
        if (!this.getMmList().contains(node)) {
            ((Map)this.getMmList()).put(node, new MythicMob(file.getName(), node, new MythicConfig(node, file, (FileConfiguration)YamlConfiguration.loadConfiguration((File)file))));
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
        if (this.getMmList().contains(node)) {
            this.getMmList().remove(node);
            bl = true;
        } else {
            bl = false;
        }
        return bl;
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H\u0007\u00a8\u0006\u0005"}, d2={"Link/ptms/chemdah/um/impl4/Mythic4$Loader;", "", "()V", "setup", "", "implementation-v4"})
    @SourceDebugExtension(value={"SMAP\nMythic4.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Mythic4.kt\nink/ptms/um/impl4/Mythic4$Loader\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,162:1\n1#2:163\n*E\n"})
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
                object = Result.constructor-impl(Class.forName("io.lumine.xikage.mythicmobs.MythicMobs"));
            }
            catch (Throwable throwable) {
                object = Result.constructor-impl((Object)ResultKt.createFailure((Throwable)throwable));
            }
            object2 = object;
            if ((Result.isFailure-impl((Object)object2) ? null : object2) != null) {
                Mythic.Companion.setAPI(new Mythic4());
            }
        }
    }
}

