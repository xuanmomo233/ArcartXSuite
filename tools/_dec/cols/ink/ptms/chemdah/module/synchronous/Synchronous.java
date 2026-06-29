/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.synchronous;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.module.Module;
import ink.ptms.chemdah.module.level.LevelOption;
import ink.ptms.chemdah.module.level.LevelSystem;
import ink.ptms.chemdah.module.level.PlayerLevel;
import ink.ptms.chemdah.module.synchronous.SynchronizedVault;
import ink.ptms.chemdah.module.synchronous.Synchronous;
import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.util.LazyMakerKt;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import ink.ptms.chemdah.taboolib.module.configuration.Config;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftServerUtilKt;
import ink.ptms.chemdah.taboolib.platform.BukkitPlugin;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.Unit;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Awake
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000L\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0014\u001a\u00020\u0015H\u0007J\u0010\u0010\u0016\u001a\u00020\u00152\u0006\u0010\u0017\u001a\u00020\u0018H\u0003J\u0010\u0010\u0019\u001a\u00020\u00152\u0006\u0010\u0017\u001a\u00020\u001aH\u0003J\u0010\u0010\u001b\u001a\u00020\u00152\u0006\u0010\u0017\u001a\u00020\u001cH\u0003J\u0010\u0010\u001d\u001a\u00020\u00152\u0006\u0010\u0017\u001a\u00020\u001eH\u0003J\u000e\u0010\u001f\u001a\u00020\u00152\u0006\u0010 \u001a\u00020!R \u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0003\u001a\u00020\u00048\u0006@BX\u0087.\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u001f\u0010\b\u001a\u0006\u0012\u0002\b\u00030\t8FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\f\u0010\r\u001a\u0004\b\n\u0010\u000bR\u0013\u0010\u000e\u001a\u0004\u0018\u00010\u000f8F\u00a2\u0006\u0006\u001a\u0004\b\u0010\u0010\u0011R\u0013\u0010\u0012\u001a\u0004\u0018\u00010\u000f8F\u00a2\u0006\u0006\u001a\u0004\b\u0013\u0010\u0011\u00a8\u0006\""}, d2={"Link/ptms/chemdah/module/synchronous/Synchronous;", "Link/ptms/chemdah/module/Module;", "()V", "<set-?>", "Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "conf", "getConf", "()Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "packet", "Ljava/lang/Class;", "getPacket", "()Ljava/lang/Class;", "packet$delegate", "Lkotlin1822/Lazy;", "playerDataToVault", "", "getPlayerDataToVault", "()Ljava/lang/String;", "playerLevelToMinecraft", "getPlayerLevelToMinecraft", "init", "", "onExpChange", "e", "Lorg/bukkit/event/player/PlayerExpChangeEvent;", "onLevelChange", "Lorg/bukkit/event/player/PlayerLevelChangeEvent;", "onPlayerEventsLevelChange", "Link/ptms/chemdah/api/event/collect/PlayerEvents$LevelChange;", "onPlayerEventsSelected", "Link/ptms/chemdah/api/event/collect/PlayerEvents$Selected;", "sendSyncLevel", "player", "Lorg/bukkit/entity/Player;", "Chemdah"})
public final class Synchronous
implements Module {
    @NotNull
    public static final Synchronous INSTANCE = new Synchronous();
    @Config(value="module/synchronous.yml")
    private static Configuration conf;
    @NotNull
    private static final Lazy packet$delegate;

    private Synchronous() {
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

    @Nullable
    public final String getPlayerDataToVault() {
        return this.getConf().getString("synchronous.player-data-to-vault");
    }

    @Nullable
    public final String getPlayerLevelToMinecraft() {
        return this.getConf().getString("synchronous.player-level-to-minecraft");
    }

    @NotNull
    public final Class<?> getPacket() {
        Lazy lazy = packet$delegate;
        return (Class)lazy.getValue();
    }

    public final void sendSyncLevel(@NotNull Player player) {
        Intrinsics.checkNotNullParameter((Object)player, (String)"player");
        this.getConf().reload();
        String string = this.getPlayerLevelToMinecraft();
        if (string == null) {
            return;
        }
        LevelOption levelOption = LevelSystem.INSTANCE.getLevelOption(string);
        if (levelOption == null) {
            return;
        }
        LevelOption option = levelOption;
        PlayerLevel playerLevel = LevelSystem.INSTANCE.getLevel(ChemdahAPI.INSTANCE.getChemdahProfile(player), option);
        option.getAlgorithm().getExp(playerLevel.getLevel()).thenAccept(arg_0 -> Synchronous.sendSyncLevel$lambda$0((Function1)new Function1<Integer, Unit>(player, playerLevel){
            final /* synthetic */ Player $player;
            final /* synthetic */ PlayerLevel $playerLevel;
            {
                this.$player = $player;
                this.$playerLevel = $playerLevel;
                super(1);
            }

            public final void invoke(Integer exp) {
                Object[] objectArray = new Object[]{Float.valueOf((float)this.$playerLevel.getExperience() / (float)exp.intValue()), 0, this.$playerLevel.getLevel()};
                Object object = Reflex.Companion.invokeConstructor(Synchronous.INSTANCE.getPacket(), objectArray);
                Intrinsics.checkNotNullExpressionValue((Object)object, (String)"packet.invokeConstructor\u2026(), 0, playerLevel.level)");
                MinecraftServerUtilKt.sendPacket((Player)this.$player, (Object)object);
            }
        }, arg_0));
    }

    @Awake(value=LifeCycle.LOAD)
    public final void init() {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null && this.getPlayerDataToVault() != null) {
            Bukkit.getServicesManager().register(Economy.class, (Object)new SynchronizedVault(), (Plugin)BukkitPlugin.getInstance(), ServicePriority.Highest);
        }
    }

    @SubscribeEvent
    private final void onExpChange(PlayerExpChangeEvent e) {
        if (this.getPlayerLevelToMinecraft() != null) {
            e.setAmount(0);
            Player player = e.getPlayer();
            Intrinsics.checkNotNullExpressionValue((Object)player, (String)"e.player");
            this.sendSyncLevel(player);
        }
    }

    @SubscribeEvent
    private final void onLevelChange(PlayerLevelChangeEvent e) {
        if (this.getPlayerLevelToMinecraft() != null) {
            Player player = e.getPlayer();
            Intrinsics.checkNotNullExpressionValue((Object)player, (String)"e.player");
            this.sendSyncLevel(player);
        }
    }

    @SubscribeEvent
    private final void onPlayerEventsSelected(PlayerEvents.Selected e) {
        if (this.getPlayerLevelToMinecraft() != null) {
            this.sendSyncLevel(e.getPlayer());
        }
    }

    @SubscribeEvent(priority=EventPriority.MONITOR, ignoreCancelled=true)
    private final void onPlayerEventsLevelChange(PlayerEvents.LevelChange e) {
        if (Intrinsics.areEqual((Object)e.getOption().getId(), (Object)this.getPlayerLevelToMinecraft())) {
            e.getOption().getAlgorithm().getExp(e.getNewLevel()).thenAccept(arg_0 -> Synchronous.onPlayerEventsLevelChange$lambda$1((Function1)new Function1<Integer, Unit>(e){
                final /* synthetic */ PlayerEvents.LevelChange $e;
                {
                    this.$e = $e;
                    super(1);
                }

                public final void invoke(Integer exp) {
                    Player player = this.$e.getPlayer();
                    Object[] objectArray = new Object[]{Float.valueOf((float)this.$e.getNewExperience() / (float)exp.intValue()), 0, this.$e.getNewLevel()};
                    Object object = Reflex.Companion.invokeConstructor(Synchronous.INSTANCE.getPacket(), objectArray);
                    Intrinsics.checkNotNullExpressionValue((Object)object, (String)"packet.invokeConstructor\u2026toFloat(), 0, e.newLevel)");
                    MinecraftServerUtilKt.sendPacket((Player)player, (Object)object);
                }
            }, arg_0));
        }
    }

    private static final void sendSyncLevel$lambda$0(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final void onPlayerEventsLevelChange$lambda$1(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    static {
        packet$delegate = LazyMakerKt.unsafeLazy((Function0)packet.2.INSTANCE);
        Module.Companion.register(INSTANCE);
    }
}

