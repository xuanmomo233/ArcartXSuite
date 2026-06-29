/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.Awake
 *  ink.ptms.chemdah.taboolib.common.platform.event.EventPriority
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.configuration.Config
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  ink.ptms.chemdah.taboolib.module.configuration.util.SectionsKt
 *  kotlin.Metadata
 *  kotlin1822.Unit
 *  kotlin1822.collections.IntIterator
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.ranges.IntRange
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.level;

import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.module.Module;
import ink.ptms.chemdah.module.level.Level;
import ink.ptms.chemdah.module.level.LevelOption;
import ink.ptms.chemdah.module.level.LevelReward;
import ink.ptms.chemdah.module.level.LevelSystem;
import ink.ptms.chemdah.module.level.PlayerLevel;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.configuration.Config;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.module.configuration.util.SectionsKt;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import kotlin.Metadata;
import kotlin1822.Unit;
import kotlin1822.collections.IntIterator;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.ranges.IntRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Awake
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000N\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0006\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u000f\u001a\u0004\u0018\u00010\u000b2\u0006\u0010\u0010\u001a\u00020\nJ\u0010\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014H\u0003J\b\u0010\u0015\u001a\u00020\u0012H\u0016J\u0012\u0010\r\u001a\u00020\u0016*\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u000bJ \u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\u00160\u001a*\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u000b2\u0006\u0010\u001b\u001a\u00020\u001cJ \u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\u00160\u001a*\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u000b2\u0006\u0010\u001b\u001a\u00020\u001cJ \u0010\u001e\u001a\b\u0012\u0004\u0012\u00020\u00160\u001a*\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u000b2\u0006\u0010\u001b\u001a\u00020\u001cJ\u001a\u0010\u001f\u001a\u00020\u0012*\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u000b2\u0006\u0010 \u001a\u00020\u0016J \u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\u00160\u001a*\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u000b2\u0006\u0010\u001b\u001a\u00020\u001cJ\"\u0010\u001f\u001a\u00020\u0012*\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u000b2\u0006\u0010\b\u001a\u00020\u001c2\u0006\u0010!\u001a\u00020\u001cR \u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0003\u001a\u00020\u00048\u0006@BX\u0087.\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R-\u0010\b\u001a\u001e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\u000b0\tj\u000e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\u000b`\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\""}, d2={"Link/ptms/chemdah/module/level/LevelSystem;", "Link/ptms/chemdah/module/Module;", "()V", "<set-?>", "Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "conf", "getConf", "()Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "level", "Ljava/util/HashMap;", "", "Link/ptms/chemdah/module/level/LevelOption;", "Lkotlin1822/collections/HashMap;", "getLevel", "()Ljava/util/HashMap;", "getLevelOption", "name", "onLevelChange", "", "e", "Link/ptms/chemdah/api/event/collect/PlayerEvents$LevelChange;", "reload", "Link/ptms/chemdah/module/level/PlayerLevel;", "Link/ptms/chemdah/core/PlayerProfile;", "option", "giveExperience", "Ljava/util/concurrent/CompletableFuture;", "value", "", "giveLevel", "setExperience", "setLevel", "playerLevel", "experience", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nLevelSystem.kt\nKotlin\n*S Kotlin\n*F\n+ 1 LevelSystem.kt\nink/ptms/chemdah/module/level/LevelSystem\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,181:1\n1855#2,2:182\n*S KotlinDebug\n*F\n+ 1 LevelSystem.kt\nink/ptms/chemdah/module/level/LevelSystem\n*L\n160#1:182,2\n*E\n"})
public final class LevelSystem
implements Module {
    @NotNull
    public static final LevelSystem INSTANCE = new LevelSystem();
    @Config(value="module/level.yml")
    private static Configuration conf;
    @NotNull
    private static final HashMap<String, LevelOption> level;

    private LevelSystem() {
    }

    @NotNull
    public final Configuration getConf() {
        Configuration configuration = conf;
        if (configuration != null) {
            return configuration;
        }
        Intrinsics.throwUninitializedPropertyAccessException((String)"conf");
        return null;
    }

    @NotNull
    public final HashMap<String, LevelOption> getLevel() {
        return level;
    }

    @Nullable
    public final LevelOption getLevelOption(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return level.get(name);
    }

    public final void setLevel(@NotNull PlayerProfile $this$setLevel, @NotNull LevelOption option, @NotNull PlayerLevel playerLevel) {
        Intrinsics.checkNotNullParameter((Object)$this$setLevel, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)option, (String)"option");
        Intrinsics.checkNotNullParameter((Object)playerLevel, (String)"playerLevel");
        this.setLevel($this$setLevel, option, playerLevel.getLevel(), playerLevel.getExperience());
    }

    public final void setLevel(@NotNull PlayerProfile $this$setLevel, @NotNull LevelOption option, int level, int experience) {
        Intrinsics.checkNotNullParameter((Object)$this$setLevel, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)option, (String)"option");
        PlayerLevel p = this.getLevel($this$setLevel, option);
        PlayerEvents.LevelChange event = new PlayerEvents.LevelChange($this$setLevel.getPlayer(), option, p.getLevel(), p.getExperience(), level, experience);
        if (event.call()) {
            $this$setLevel.getPersistentDataContainer().set("module.level." + option.getId() + ".level", event.getNewLevel());
            $this$setLevel.getPersistentDataContainer().set("module.level." + option.getId() + ".experience", event.getNewExperience());
        }
    }

    @NotNull
    public final PlayerLevel getLevel(@NotNull PlayerProfile $this$getLevel, @NotNull LevelOption option) {
        Intrinsics.checkNotNullParameter((Object)$this$getLevel, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)option, (String)"option");
        return new PlayerLevel($this$getLevel.getPersistentDataContainer().get("module.level." + option.getId() + ".level", 0).toInt(), $this$getLevel.getPersistentDataContainer().get("module.level." + option.getId() + ".experience", 0).toInt());
    }

    @NotNull
    public final CompletableFuture<PlayerLevel> setLevel(@NotNull PlayerProfile $this$setLevel, @NotNull LevelOption option, int value2) {
        CompletableFuture<PlayerLevel> completableFuture;
        Intrinsics.checkNotNullParameter((Object)$this$setLevel, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)option, (String)"option");
        CompletableFuture<PlayerLevel> future = completableFuture = new CompletableFuture<PlayerLevel>();
        boolean bl = false;
        Level level = option.toLevel(INSTANCE.getLevel($this$setLevel, option));
        level.setLevel(value2).thenAccept(arg_0 -> LevelSystem.setLevel$lambda$1$lambda$0((Function1)new Function1<Void, Unit>(level, $this$setLevel, option, future){
            final /* synthetic */ Level $level;
            final /* synthetic */ PlayerProfile $this_setLevel;
            final /* synthetic */ LevelOption $option;
            final /* synthetic */ CompletableFuture<PlayerLevel> $future;
            {
                this.$level = $level;
                this.$this_setLevel = $receiver;
                this.$option = $option;
                this.$future = $future;
                super(1);
            }

            public final void invoke(Void it) {
                PlayerLevel playerLevel = this.$level.toPlayerLevel();
                LevelSystem.INSTANCE.setLevel(this.$this_setLevel, this.$option, playerLevel);
                this.$future.complete(playerLevel);
            }
        }, arg_0));
        return completableFuture;
    }

    @NotNull
    public final CompletableFuture<PlayerLevel> giveLevel(@NotNull PlayerProfile $this$giveLevel, @NotNull LevelOption option, int value2) {
        CompletableFuture<PlayerLevel> completableFuture;
        Intrinsics.checkNotNullParameter((Object)$this$giveLevel, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)option, (String)"option");
        CompletableFuture<PlayerLevel> future = completableFuture = new CompletableFuture<PlayerLevel>();
        boolean bl = false;
        Level level = option.toLevel(INSTANCE.getLevel($this$giveLevel, option));
        level.addLevel(value2).thenAccept(arg_0 -> LevelSystem.giveLevel$lambda$3$lambda$2((Function1)new Function1<Void, Unit>(level, $this$giveLevel, option, future){
            final /* synthetic */ Level $level;
            final /* synthetic */ PlayerProfile $this_giveLevel;
            final /* synthetic */ LevelOption $option;
            final /* synthetic */ CompletableFuture<PlayerLevel> $future;
            {
                this.$level = $level;
                this.$this_giveLevel = $receiver;
                this.$option = $option;
                this.$future = $future;
                super(1);
            }

            public final void invoke(Void it) {
                PlayerLevel playerLevel = this.$level.toPlayerLevel();
                LevelSystem.INSTANCE.setLevel(this.$this_giveLevel, this.$option, playerLevel);
                this.$future.complete(playerLevel);
            }
        }, arg_0));
        return completableFuture;
    }

    @NotNull
    public final CompletableFuture<PlayerLevel> setExperience(@NotNull PlayerProfile $this$setExperience, @NotNull LevelOption option, int value2) {
        CompletableFuture<PlayerLevel> completableFuture;
        Intrinsics.checkNotNullParameter((Object)$this$setExperience, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)option, (String)"option");
        CompletableFuture<PlayerLevel> future = completableFuture = new CompletableFuture<PlayerLevel>();
        boolean bl = false;
        Level level = option.toLevel(INSTANCE.getLevel($this$setExperience, option));
        level.setExperience(value2).thenAccept(arg_0 -> LevelSystem.setExperience$lambda$5$lambda$4((Function1)new Function1<Void, Unit>(level, $this$setExperience, option, future){
            final /* synthetic */ Level $level;
            final /* synthetic */ PlayerProfile $this_setExperience;
            final /* synthetic */ LevelOption $option;
            final /* synthetic */ CompletableFuture<PlayerLevel> $future;
            {
                this.$level = $level;
                this.$this_setExperience = $receiver;
                this.$option = $option;
                this.$future = $future;
                super(1);
            }

            public final void invoke(Void it) {
                PlayerLevel playerLevel = this.$level.toPlayerLevel();
                LevelSystem.INSTANCE.setLevel(this.$this_setExperience, this.$option, playerLevel);
                this.$future.complete(playerLevel);
            }
        }, arg_0));
        return completableFuture;
    }

    @NotNull
    public final CompletableFuture<PlayerLevel> giveExperience(@NotNull PlayerProfile $this$giveExperience, @NotNull LevelOption option, int value2) {
        CompletableFuture<PlayerLevel> completableFuture;
        Intrinsics.checkNotNullParameter((Object)$this$giveExperience, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)option, (String)"option");
        CompletableFuture<PlayerLevel> future = completableFuture = new CompletableFuture<PlayerLevel>();
        boolean bl = false;
        Level level = option.toLevel(INSTANCE.getLevel($this$giveExperience, option));
        level.addExperience(value2).thenAccept(arg_0 -> LevelSystem.giveExperience$lambda$7$lambda$6((Function1)new Function1<Void, Unit>(level, $this$giveExperience, option, future){
            final /* synthetic */ Level $level;
            final /* synthetic */ PlayerProfile $this_giveExperience;
            final /* synthetic */ LevelOption $option;
            final /* synthetic */ CompletableFuture<PlayerLevel> $future;
            {
                this.$level = $level;
                this.$this_giveExperience = $receiver;
                this.$option = $option;
                this.$future = $future;
                super(1);
            }

            public final void invoke(Void it) {
                PlayerLevel playerLevel = this.$level.toPlayerLevel();
                LevelSystem.INSTANCE.setLevel(this.$this_giveExperience, this.$option, playerLevel);
                this.$future.complete(playerLevel);
            }
        }, arg_0));
        return completableFuture;
    }

    @SubscribeEvent(priority=EventPriority.MONITOR, ignoreCancelled=true)
    private final void onLevelChange(PlayerEvents.LevelChange e) {
        if (e.getNewLevel() > e.getOldLevel()) {
            Iterable $this$forEach$iv = (Iterable)new IntRange(e.getOldLevel() + 1, e.getNewLevel());
            boolean $i$f$forEach = false;
            Iterator iterator = $this$forEach$iv.iterator();
            while (iterator.hasNext()) {
                int element$iv;
                int level = element$iv = ((IntIterator)iterator).nextInt();
                boolean bl = false;
                LevelReward levelReward = e.getOption().getReward(level);
                if (levelReward != null) {
                    levelReward.eval(e.getPlayer(), level);
                }
            }
        }
    }

    @Override
    public void reload() {
        level.clear();
        this.getConf().reload();
        SectionsKt.mapSection((ConfigurationSection)((ConfigurationSection)this.getConf()), (Function1)reload.1.INSTANCE);
    }

    private static final void setLevel$lambda$1$lambda$0(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final void giveLevel$lambda$3$lambda$2(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final void setExperience$lambda$5$lambda$4(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final void giveExperience$lambda$7$lambda$6(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    static {
        level = new HashMap();
        Module.Companion.register(INSTANCE);
    }
}

