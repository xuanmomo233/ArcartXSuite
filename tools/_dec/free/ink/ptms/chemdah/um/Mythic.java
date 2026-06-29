/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin.jvm.JvmStatic
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.um;

import ink.ptms.chemdah.um.Item;
import ink.ptms.chemdah.um.Mob;
import ink.ptms.chemdah.um.MobType;
import ink.ptms.chemdah.um.Skill;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kotlin.Metadata;
import kotlin.jvm.JvmStatic;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u0084\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u001e\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\bf\u0018\u0000 52\u00020\u0001:\u00015JX\u0010\u0005\u001a\u00020\u00032\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\t2\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u00072\b\b\u0002\u0010\u000b\u001a\u00020\f2\u000e\b\u0002\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u00070\u000e2\u000e\b\u0002\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\f0\u000e2\b\b\u0002\u0010\u0010\u001a\u00020\u0011H&J\b\u0010\u0012\u001a\u00020\u0013H&J\u0012\u0010\u0014\u001a\u0004\u0018\u00010\u00152\u0006\u0010\u0016\u001a\u00020\tH&J\u000e\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\t0\u0018H&J\u0012\u0010\u0019\u001a\u0004\u0018\u00010\t2\u0006\u0010\u001a\u001a\u00020\u001bH&J\u000e\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u00150\u0018H&J\u001e\u0010\u001d\u001a\u0004\u0018\u00010\u001b2\u0006\u0010\u0016\u001a\u00020\t2\n\b\u0002\u0010\u001e\u001a\u0004\u0018\u00010\u001fH&J\u0012\u0010 \u001a\u0004\u0018\u00010!2\u0006\u0010\"\u001a\u00020#H&J\u0012\u0010 \u001a\u0004\u0018\u00010!2\u0006\u0010$\u001a\u00020\u0007H&J\u000e\u0010%\u001a\b\u0012\u0004\u0012\u00020\t0\u0018H&J\u0012\u0010&\u001a\u0004\u0018\u00010'2\u0006\u0010\u0016\u001a\u00020\tH&J\u0012\u0010(\u001a\u0004\u0018\u00010)2\u0006\u0010*\u001a\u00020\tH&J\u0010\u0010+\u001a\u00020\u00132\u0006\u0010\u0016\u001a\u00020\tH&J\u0012\u0010,\u001a\u0004\u0018\u00010-2\u0006\u0010\u001e\u001a\u00020\u001fH&J\u0018\u0010.\u001a\u00020\u00032\u0006\u0010/\u001a\u0002002\u0006\u00101\u001a\u00020\tH&J\u0018\u00102\u001a\u00020\u00032\u0006\u0010/\u001a\u0002002\u0006\u00101\u001a\u00020\tH&J\u0010\u00103\u001a\u00020\u00032\u0006\u00101\u001a\u00020\tH&J\u0010\u00104\u001a\u00020\u00032\u0006\u00101\u001a\u00020\tH&R\u0012\u0010\u0002\u001a\u00020\u0003X\u00a6\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0002\u0010\u0004\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u00066\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/um/Mythic;", "", "isLegacy", "", "()Z", "castSkill", "caster", "Lorg/bukkit/entity/Entity;", "skillName", "", "trigger", "origin", "Lorg/bukkit/Location;", "et", "", "lt", "power", "", "getDefaultSkillTrigger", "Link/ptms/chemdah/um/Skill$Trigger;", "getItem", "Link/ptms/chemdah/um/Item;", "name", "getItemIDList", "", "getItemId", "itemStack", "Lorg/bukkit/inventory/ItemStack;", "getItemList", "getItemStack", "player", "Lorg/bukkit/entity/Player;", "getMob", "Link/ptms/chemdah/um/Mob;", "uuid", "Ljava/util/UUID;", "entity", "getMobIDList", "getMobType", "Link/ptms/chemdah/um/MobType;", "getSkillMechanic", "Link/ptms/chemdah/um/Skill;", "skillLine", "getSkillTrigger", "getTargetedEntity", "Lorg/bukkit/entity/LivingEntity;", "registerItem", "file", "Ljava/io/File;", "node", "registerMob", "unregisterItem", "unregisterMob", "Companion", "common"})
public interface Mythic {
    @NotNull
    public static final Companion Companion = ink.ptms.chemdah.um.Mythic$Companion.$$INSTANCE;

    public boolean isLegacy();

    @Nullable
    public Item getItem(@NotNull String var1);

    @Nullable
    public String getItemId(@NotNull ItemStack var1);

    @Nullable
    public ItemStack getItemStack(@NotNull String var1, @Nullable Player var2);

    public static /* synthetic */ ItemStack getItemStack$default(Mythic mythic, String string, Player player2, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: getItemStack");
        }
        if ((n & 2) != 0) {
            player2 = null;
        }
        return mythic.getItemStack(string, player2);
    }

    @NotNull
    public List<String> getItemIDList();

    @NotNull
    public List<Item> getItemList();

    @Nullable
    public Mob getMob(@NotNull Entity var1);

    @Nullable
    public Mob getMob(@NotNull UUID var1);

    @NotNull
    public List<String> getMobIDList();

    @Nullable
    public MobType getMobType(@NotNull String var1);

    @NotNull
    public Skill.Trigger getSkillTrigger(@NotNull String var1);

    @NotNull
    public Skill.Trigger getDefaultSkillTrigger();

    @Nullable
    public Skill getSkillMechanic(@NotNull String var1);

    @Nullable
    public LivingEntity getTargetedEntity(@NotNull Player var1);

    public boolean castSkill(@NotNull Entity var1, @NotNull String var2, @Nullable Entity var3, @NotNull Location var4, @NotNull Collection<? extends Entity> var5, @NotNull Collection<? extends Location> var6, float var7);

    public static /* synthetic */ boolean castSkill$default(Mythic mythic, Entity entity, String string, Entity entity2, Location location, Collection collection, Collection collection2, float f, int n, Object object) {
        if (object != null) {
            throw new UnsupportedOperationException("Super calls with default arguments not supported in this target, function: castSkill");
        }
        if ((n & 4) != 0) {
            entity2 = null;
        }
        if ((n & 8) != 0) {
            Location location2 = entity.getLocation();
            Intrinsics.checkNotNullExpressionValue((Object)location2, (String)"getLocation(...)");
            location = location2;
        }
        if ((n & 0x10) != 0) {
            collection = CollectionsKt.emptyList();
        }
        if ((n & 0x20) != 0) {
            collection2 = CollectionsKt.emptyList();
        }
        if ((n & 0x40) != 0) {
            f = 1.0f;
        }
        return mythic.castSkill(entity, string, entity2, location, collection, collection2, f);
    }

    public boolean registerItem(@NotNull File var1, @NotNull String var2);

    public boolean unregisterItem(@NotNull String var1);

    public boolean registerMob(@NotNull File var1, @NotNull String var2);

    public boolean unregisterMob(@NotNull String var1);

    @NotNull
    public static Mythic getAPI() {
        return Companion.getAPI();
    }

    public static void setAPI(@NotNull Mythic mythic) {
        Companion.setAPI(mythic);
    }

    @Metadata(mv={1, 9, 0}, k=1, xi=48, d1={"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0006\u0010\n\u001a\u00020\u000bR$\u0010\u0003\u001a\u00020\u00048\u0006@\u0006X\u0087.\u00a2\u0006\u0014\n\u0000\u0012\u0004\b\u0005\u0010\u0002\u001a\u0004\b\u0006\u0010\u0007\"\u0004\b\b\u0010\t\u00a8\u0006\f"}, d2={"Link/ptms/chemdah/um/Mythic$Companion;", "", "()V", "API", "Link/ptms/chemdah/um/Mythic;", "getAPI$annotations", "getAPI", "()Link/ptms/chemdah/um/Mythic;", "setAPI", "(Link/ptms/chemdah/um/Mythic;)V", "isLoaded", "", "common"})
    public static final class Companion {
        static final /* synthetic */ Companion $$INSTANCE;
        public static Mythic API;

        private Companion() {
        }

        @NotNull
        public final Mythic getAPI() {
            Mythic mythic = API;
            if (mythic != null) {
                return mythic;
            }
            Intrinsics.throwUninitializedPropertyAccessException((String)"API");
            return null;
        }

        public final void setAPI(@NotNull Mythic mythic) {
            Intrinsics.checkNotNullParameter((Object)mythic, (String)"<set-?>");
            API = mythic;
        }

        @JvmStatic
        public static /* synthetic */ void getAPI$annotations() {
        }

        public final boolean isLoaded() {
            return API != null;
        }

        static {
            $$INSTANCE = new Companion();
        }
    }
}

