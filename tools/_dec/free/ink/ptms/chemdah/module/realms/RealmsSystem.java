/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.Awake
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.configuration.Config
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  ink.ptms.chemdah.taboolib.module.navigation.BoundingBox
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitEventKt
 *  ink.ptms.chemdah.taboolib.platform.util.BukkitPluginKt
 *  kotlin.Metadata
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerMoveEvent
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.util.Vector
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.realms;

import ink.ptms.chemdah.module.Module;
import ink.ptms.chemdah.module.realms.Realms;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.configuration.Config;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.module.navigation.BoundingBox;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion;
import ink.ptms.chemdah.taboolib.platform.util.BukkitEventKt;
import ink.ptms.chemdah.taboolib.platform.util.BukkitPluginKt;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Awake
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000B\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u0012H\u0003J\u0010\u0010\u0013\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u0014H\u0003J\b\u0010\u0015\u001a\u00020\u0010H\u0016J\f\u0010\u0016\u001a\u0004\u0018\u00010\u000b*\u00020\u0017R \u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0003\u001a\u00020\u00048\u0006@BX\u0087.\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R-\u0010\b\u001a\u001e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\u000b0\tj\u000e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\u000b`\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u0018"}, d2={"Link/ptms/chemdah/module/realms/RealmsSystem;", "Link/ptms/chemdah/module/Module;", "()V", "<set-?>", "Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "conf", "getConf", "()Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "realmsMap", "Ljava/util/HashMap;", "", "Link/ptms/chemdah/module/realms/Realms;", "Lkotlin1822/collections/HashMap;", "getRealmsMap", "()Ljava/util/HashMap;", "onJoin", "", "e", "Lorg/bukkit/event/player/PlayerJoinEvent;", "onMove", "Lorg/bukkit/event/player/PlayerMoveEvent;", "reload", "getRealms", "Lorg/bukkit/Location;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nRealmsSystem.kt\nKotlin\n*S Kotlin\n*F\n+ 1 RealmsSystem.kt\nink/ptms/chemdah/module/realms/RealmsSystem\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,84:1\n1855#2,2:85\n288#2,2:87\n1855#2,2:89\n1855#2,2:91\n1855#2,2:93\n*S KotlinDebug\n*F\n+ 1 RealmsSystem.kt\nink/ptms/chemdah/module/realms/RealmsSystem\n*L\n33#1:85,2\n39#1:87,2\n45#1:89,2\n62#1:91,2\n72#1:93,2\n*E\n"})
public final class RealmsSystem
implements Module {
    @NotNull
    public static final RealmsSystem INSTANCE = new RealmsSystem();
    @Config(value="module/realms.yml")
    private static Configuration conf;
    @NotNull
    private static final HashMap<String, Realms> realmsMap;

    private RealmsSystem() {
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
    public final HashMap<String, Realms> getRealmsMap() {
        return realmsMap;
    }

    @Override
    public void reload() {
        realmsMap.clear();
        this.getConf().reload();
        Iterable $this$forEach$iv = this.getConf().getKeys(false);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            String it = (String)element$iv;
            boolean bl = false;
            Map map = realmsMap;
            ConfigurationSection configurationSection = INSTANCE.getConf().getConfigurationSection(it);
            Intrinsics.checkNotNull((Object)configurationSection);
            map.put(it, new Realms(configurationSection));
        }
    }

    /*
     * Enabled aggressive block sorting
     */
    @Nullable
    public final Realms getRealms(@NotNull Location $this$getRealms) {
        Object element$iv;
        Object v1;
        boolean bl;
        Intrinsics.checkNotNullParameter((Object)$this$getRealms, (String)"<this>");
        Collection<Realms> collection = realmsMap.values();
        Intrinsics.checkNotNullExpressionValue(collection, (String)"realmsMap.values");
        Iterable $this$firstOrNull$iv = collection;
        boolean $i$f$firstOrNull = false;
        Iterator iterator = $this$firstOrNull$iv.iterator();
        do {
            if (!iterator.hasNext()) {
                v1 = null;
                return v1;
            }
            element$iv = iterator.next();
            Realms it = (Realms)element$iv;
            boolean bl2 = false;
            String string = it.getWorld();
            World world = $this$getRealms.getWorld();
            Intrinsics.checkNotNull((Object)world);
            if (Intrinsics.areEqual((Object)string, (Object)world.getName())) {
                BoundingBox boundingBox = it.getArea();
                Vector vector = $this$getRealms.toVector();
                Intrinsics.checkNotNullExpressionValue((Object)vector, (String)"toVector()");
                if (boundingBox.contains(vector)) {
                    bl = true;
                    continue;
                }
            }
            bl = false;
        } while (!bl);
        v1 = element$iv;
        return v1;
    }

    @SubscribeEvent
    private final void onJoin(PlayerJoinEvent e) {
        Location location = e.getPlayer().getLocation();
        Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.player.location");
        if (this.getRealms(location) != null) {
            Collection collection = Bukkit.getOnlinePlayers();
            Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"getOnlinePlayers()");
            Iterable $this$forEach$iv = collection;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                Player player2 = (Player)element$iv;
                boolean bl = false;
                if (Intrinsics.areEqual((Object)player2.getName(), (Object)e.getPlayer().getName())) continue;
                if (MinecraftVersion.INSTANCE.getMajorLegacy() >= 11300) {
                    player2.hidePlayer((Plugin)BukkitPluginKt.getBukkitPlugin(), e.getPlayer());
                    continue;
                }
                player2.hidePlayer(e.getPlayer());
            }
        }
    }

    @SubscribeEvent
    private final void onMove(PlayerMoveEvent e) {
        block6: {
            if (!BukkitEventKt.isBlockMovement((PlayerMoveEvent)e)) break block6;
            Location location = e.getTo();
            Intrinsics.checkNotNull((Object)location);
            Realms realms2 = this.getRealms(location);
            if (realms2 != null) {
                Collection collection = Bukkit.getOnlinePlayers();
                Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"getOnlinePlayers()");
                Iterable $this$forEach$iv = collection;
                boolean $i$f$forEach = false;
                for (Object element$iv : $this$forEach$iv) {
                    Player player2 = (Player)element$iv;
                    boolean bl = false;
                    if (Intrinsics.areEqual((Object)player2.getName(), (Object)e.getPlayer().getName()) || !player2.canSee(e.getPlayer())) continue;
                    if (MinecraftVersion.INSTANCE.getMajorLegacy() >= 11300) {
                        player2.hidePlayer((Plugin)BukkitPluginKt.getBukkitPlugin(), e.getPlayer());
                        continue;
                    }
                    player2.hidePlayer(e.getPlayer());
                }
            } else {
                Collection collection = Bukkit.getOnlinePlayers();
                Intrinsics.checkNotNullExpressionValue((Object)collection, (String)"getOnlinePlayers()");
                Iterable $this$forEach$iv = collection;
                boolean $i$f$forEach = false;
                for (Object element$iv : $this$forEach$iv) {
                    Player player3 = (Player)element$iv;
                    boolean bl = false;
                    if (Intrinsics.areEqual((Object)player3.getName(), (Object)e.getPlayer().getName()) || player3.canSee(e.getPlayer())) continue;
                    if (MinecraftVersion.INSTANCE.getMajorLegacy() >= 11300) {
                        player3.showPlayer((Plugin)BukkitPluginKt.getBukkitPlugin(), e.getPlayer());
                        continue;
                    }
                    player3.showPlayer(e.getPlayer());
                }
            }
        }
    }

    static {
        realmsMap = new HashMap();
        Module.Companion.register(INSTANCE);
    }
}

