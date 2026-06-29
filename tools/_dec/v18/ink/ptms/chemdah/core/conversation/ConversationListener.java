/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.sucy.skill.api.event.PlayerCastSkillEvent
 *  ink.ptms.chemdah.taboolib.common.io.ProjectInfoKt
 *  ink.ptms.chemdah.taboolib.common.platform.Awake
 *  ink.ptms.chemdah.taboolib.common.platform.Schedule
 *  ink.ptms.chemdah.taboolib.common.platform.event.EventPriority
 *  ink.ptms.chemdah.taboolib.common.platform.event.OptionalEvent
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor$PlatformTask
 *  ink.ptms.chemdah.taboolib.common5.Baffle
 *  ink.ptms.chemdah.taboolib.module.configuration.ConfigNode
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftServerUtilKt
 *  kotlin.Metadata
 *  kotlin1822.Pair
 *  kotlin1822.Result
 *  kotlin1822.ResultKt
 *  kotlin1822.TuplesKt
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  net.minecraft.network.protocol.game.PacketPlayOutUpdateHealth
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.bukkit.event.player.PlayerCommandPreprocessEvent
 *  org.bukkit.event.player.PlayerDropItemEvent
 *  org.bukkit.event.player.PlayerMoveEvent
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.potion.PotionEffectType
 *  org.bukkit.util.Vector
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.conversation;

import com.sucy.skill.api.event.PlayerCastSkillEvent;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.ConversationEvents;
import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.core.conversation.Conversation;
import ink.ptms.chemdah.core.conversation.ConversationManager;
import ink.ptms.chemdah.core.conversation.PlayerReply;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.conversation.Source;
import ink.ptms.chemdah.core.conversation.theme.WorldSide;
import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.io.ProjectInfoKt;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.platform.Schedule;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.event.OptionalEvent;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.service.PlatformExecutor;
import ink.ptms.chemdah.taboolib.common5.Baffle;
import ink.ptms.chemdah.taboolib.module.configuration.ConfigNode;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftServerUtilKt;
import ink.ptms.chemdah.util.FuturesKt;
import ink.ptms.chemdah.util.UtilsKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.Result;
import kotlin1822.ResultKt;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import net.minecraft.network.protocol.game.PacketPlayOutUpdateHealth;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0098\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010$\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010#\u001a\u00020$2\u0006\u0010%\u001a\u00020&H\u0003J\u0010\u0010'\u001a\u00020$2\u0006\u0010%\u001a\u00020(H\u0003J\u0010\u0010)\u001a\u00020$2\u0006\u0010%\u001a\u00020*H\u0003J\u0010\u0010+\u001a\u00020$2\u0006\u0010%\u001a\u00020,H\u0003J\b\u0010-\u001a\u00020$H\u0003J\u0010\u0010.\u001a\u00020$2\u0006\u0010%\u001a\u00020/H\u0003J\u0010\u00100\u001a\u00020$2\u0006\u0010%\u001a\u000201H\u0003J\u0010\u00102\u001a\u00020$2\u0006\u00103\u001a\u000204H\u0003J\u0010\u00105\u001a\u00020$2\u0006\u0010%\u001a\u000206H\u0003J\b\u00107\u001a\u00020$H\u0003J\u0018\u00108\u001a\u00020$2\u0006\u00109\u001a\u00020:2\u0006\u0010;\u001a\u00020\u0010H\u0002R\u001e\u0010\u0003\u001a\u00020\u00048\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\bR\u0013\u0010\t\u001a\u00070\n\u00a2\u0006\u0002\b\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R%\u0010\f\u001a\u0016\u0012\f\u0012\n \u000f*\u0004\u0018\u00010\u000e0\u000e\u0012\u0004\u0012\u00020\u00100\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R#\u0010\u0013\u001a\u0014\u0012\u0004\u0012\u00020\u0015\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00170\u00160\u0014\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0019R\u001d\u0010\u001a\u001a\u000e\u0012\u0004\u0012\u00020\u0015\u0012\u0004\u0012\u00020\u001b0\u0014\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u0019R\u001e\u0010\u001d\u001a\u00020\u001e8\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001f\u0010 \"\u0004\b!\u0010\"\u00a8\u0006<"}, d2={"Link/ptms/chemdah/core/conversation/ConversationListener;", "", "()V", "closeDistance", "", "getCloseDistance", "()D", "setCloseDistance", "(D)V", "fakeFoodLevel", "Link/ptms/chemdah/taboolib/common5/Baffle;", "Lorg/jetbrains/annotations/NotNull;", "freezeEffects", "", "Lorg/bukkit/potion/PotionEffectType;", "kotlin1822.jvm.PlatformType", "", "getFreezeEffects", "()Ljava/util/Map;", "playerEffects", "Ljava/util/concurrent/ConcurrentHashMap;", "", "", "Lorg/bukkit/potion/PotionEffect;", "getPlayerEffects", "()Ljava/util/concurrent/ConcurrentHashMap;", "playerSpeed", "", "getPlayerSpeed", "useFadeEffect", "", "getUseFadeEffect", "()Z", "setUseFadeEffect", "(Z)V", "onBegin", "", "e", "Link/ptms/chemdah/api/event/collect/ConversationEvents$Begin;", "onClosed", "Link/ptms/chemdah/api/event/collect/ConversationEvents$Closed;", "onCommand", "Lorg/bukkit/event/player/PlayerCommandPreprocessEvent;", "onDamage", "Lorg/bukkit/event/entity/EntityDamageEvent;", "onDisable", "onDropItem", "Lorg/bukkit/event/player/PlayerDropItemEvent;", "onMove", "Lorg/bukkit/event/player/PlayerMoveEvent;", "onPlayerCastSkillEvent", "event", "Link/ptms/chemdah/taboolib/common/platform/event/OptionalEvent;", "onReleased", "Link/ptms/chemdah/api/event/collect/PlayerEvents$Released;", "onTick", "sendFakeFoodLevel", "player", "Lorg/bukkit/entity/Player;", "foodLevel", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nConversationListener.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ConversationListener.kt\nink/ptms/chemdah/core/conversation/ConversationListener\n+ 2 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 4 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 5 OptionalEvent.kt\ntaboolib/common/platform/event/OptionalEvent\n*L\n1#1,285:1\n215#2,2:286\n215#2,2:288\n135#2,9:290\n215#2:299\n216#2:301\n144#2:302\n215#2,2:306\n1#3:300\n1#3:308\n766#4:303\n857#4,2:304\n288#4,2:309\n16#5:311\n*S KotlinDebug\n*F\n+ 1 ConversationListener.kt\nink/ptms/chemdah/core/conversation/ConversationListener\n*L\n70#1:286,2\n100#1:288,2\n127#1:290,9\n127#1:299\n127#1:301\n127#1:302\n129#1:306,2\n127#1:300\n127#1:303\n127#1:304,2\n253#1:309,2\n269#1:311\n*E\n"})
public final class ConversationListener {
    @NotNull
    public static final ConversationListener INSTANCE = new ConversationListener();
    @ConfigNode(value="default-conversation.use-fade-effect")
    private static boolean useFadeEffect = true;
    @ConfigNode(value="theme-chat.close-distance", bind="core/conversation.yml")
    private static double closeDistance = 0.5;
    @NotNull
    private static final Map<PotionEffectType, Integer> freezeEffects;
    @NotNull
    private static final ConcurrentHashMap<String, List<PotionEffect>> playerEffects;
    @NotNull
    private static final ConcurrentHashMap<String, Float> playerSpeed;
    @NotNull
    private static final Baffle fakeFoodLevel;

    private ConversationListener() {
    }

    public final boolean getUseFadeEffect() {
        return useFadeEffect;
    }

    public final void setUseFadeEffect(boolean bl) {
        useFadeEffect = bl;
    }

    public final double getCloseDistance() {
        return closeDistance;
    }

    public final void setCloseDistance(double d) {
        closeDistance = d;
    }

    @NotNull
    public final Map<PotionEffectType, Integer> getFreezeEffects() {
        return freezeEffects;
    }

    @NotNull
    public final ConcurrentHashMap<String, List<PotionEffect>> getPlayerEffects() {
        return playerEffects;
    }

    @NotNull
    public final ConcurrentHashMap<String, Float> getPlayerSpeed() {
        return playerSpeed;
    }

    @Schedule(async=true, period=1L)
    private final void onTick() {
        Map $this$forEach$iv = ConversationManager.INSTANCE.getSessions();
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            boolean tooFar;
            String[] stringArray;
            String[] stringArray2;
            boolean anotherWorld;
            Conversation conversation2;
            Map.Entry element$iv;
            Map.Entry entry = element$iv = iterator.next();
            boolean bl = false;
            String name = (String)entry.getKey();
            Session session = (Session)entry.getValue();
            if (session.isClosed() || !((conversation2 = session.getConversation()).getTheme() instanceof WorldSide)) continue;
            String[] stringArray3 = new String[]{"NO_MOVE"};
            if (conversation2.hasFlag(stringArray3) && fakeFoodLevel.hasNext(name)) {
                INSTANCE.sendFakeFoodLevel(session.getPlayer(), 3);
            }
            if (conversation2.hasFlag(stringArray3 = new String[]{"FORCE_DISPLAY"})) continue;
            World world = session.getLocation().getWorld();
            boolean bl2 = anotherWorld = !Intrinsics.areEqual((Object)(world != null ? world.getName() : null), (Object)session.getPlayer().getWorld().getName());
            boolean turnHead = session.getLocation().getDirection().dot(session.getPlayer().getLocation().getDirection()) < 0.0 && conversation2.noFlag(stringArray2 = new String[]{"IGNORE_TURN_HEAD"});
            boolean bl3 = session.getDistance() > closeDistance && conversation2.noFlag(stringArray = new String[]{"IGNORE_TOO_FAR"}) ? true : (tooFar = false);
            if (!anotherWorld && !turnHead && !tooFar) continue;
            ExecutorKt.submit$default((boolean)false, (boolean)false, (long)0L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(session){
                final /* synthetic */ Session $session;
                {
                    this.$session = $session;
                    super(1);
                }

                public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                    Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                    this.$session.close(true);
                }
            }), (int)15, null);
        }
    }

    @Awake(value=LifeCycle.DISABLE)
    private final void onDisable() {
        Map $this$forEach$iv = ConversationManager.INSTANCE.getSessions();
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry element$iv;
            Map.Entry entry = element$iv = iterator.next();
            boolean bl = false;
            Session session = (Session)entry.getValue();
            if (session.isClosed()) continue;
            ConversationManager.INSTANCE.closeSession(session.getPlayer());
        }
    }

    @SubscribeEvent
    private final void onReleased(PlayerEvents.Released e) {
        ConversationManager.INSTANCE.closeSession(e.getPlayer());
        ConversationManager.INSTANCE.getSessions().remove(e.getPlayer().getName());
        fakeFoodLevel.reset(e.getPlayer().getName());
    }

    /*
     * WARNING - void declaration
     */
    @SubscribeEvent
    private final void onBegin(ConversationEvents.Begin e) {
        if (e.getConversation().getTheme() instanceof WorldSide) {
            Object $this$forEach$iv;
            Iterator $this$filter$iv;
            Object object = new String[]{"NO_EFFECT"};
            if (e.getConversation().noFlag((String[])object)) {
                void $this$filterTo$iv$iv;
                Float $this$mapNotNullTo$iv$iv;
                object = playerEffects;
                String string = e.getSession().getPlayer().getName();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"e.session.player.name");
                String string2 = string;
                Object $this$mapNotNull$iv = freezeEffects;
                boolean $i$f$mapNotNull = false;
                Map<PotionEffectType, Integer> map = $this$mapNotNull$iv;
                Collection destination$iv$iv = new ArrayList();
                boolean $i$f$mapNotNullTo = false;
                void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
                boolean $i$f$forEach = false;
                Iterator iterator = $this$forEach$iv$iv$iv.entrySet().iterator();
                while (iterator.hasNext()) {
                    PotionEffect it$iv$iv;
                    Map.Entry element$iv$iv$iv;
                    Map.Entry element$iv$iv = element$iv$iv$iv = iterator.next();
                    boolean bl = false;
                    Map.Entry it = element$iv$iv;
                    boolean bl2 = false;
                    if (e.getSession().getPlayer().getPotionEffect((PotionEffectType)it.getKey()) == null) continue;
                    boolean bl3 = false;
                    destination$iv$iv.add(it$iv$iv);
                }
                $this$mapNotNull$iv = (List)destination$iv$iv;
                boolean $i$f$filter = false;
                $this$mapNotNullTo$iv$iv = $this$filter$iv;
                destination$iv$iv = new ArrayList();
                boolean $i$f$filterTo = false;
                for (Object element$iv$iv : $this$filterTo$iv$iv) {
                    PotionEffect it = (PotionEffect)element$iv$iv;
                    boolean bl = false;
                    int n = it.getDuration();
                    boolean bl4 = 10 <= n ? n < 10000 : false;
                    if (!bl4) continue;
                    destination$iv$iv.add(element$iv$iv);
                }
                $this$filter$iv = (List)destination$iv$iv;
                object.put(string2, $this$filter$iv);
                $this$forEach$iv = freezeEffects;
                boolean $i$f$forEach2 = false;
                $this$filter$iv = $this$forEach$iv.entrySet().iterator();
                while ($this$filter$iv.hasNext()) {
                    Map.Entry element$iv;
                    Map.Entry it = element$iv = $this$filter$iv.next();
                    boolean bl = false;
                    Conversation conversation2 = e.getConversation();
                    String[] stringArray = new String[1];
                    StringBuilder stringBuilder = new StringBuilder().append("NO_EFFECT:");
                    String string3 = ((PotionEffectType)it.getKey()).getName();
                    Intrinsics.checkNotNullExpressionValue((Object)string3, (String)"it.key.name");
                    String string4 = string3.toUpperCase(Locale.ROOT);
                    Intrinsics.checkNotNullExpressionValue((Object)string4, (String)"this as java.lang.String).toUpperCase(Locale.ROOT)");
                    stringArray[0] = stringBuilder.append(string4).toString();
                    if (!conversation2.noFlag(stringArray)) continue;
                    e.getSession().getPlayer().addPotionEffect(UtilsKt.hidden(new PotionEffect((PotionEffectType)it.getKey(), 99999, ((Number)it.getValue()).intValue())));
                }
            }
            $this$forEach$iv = new String[]{"NO_MOVE"};
            if (e.getConversation().hasFlag((String[])$this$forEach$iv)) {
                $this$forEach$iv = playerSpeed;
                String string = e.getSession().getPlayer().getName();
                Intrinsics.checkNotNullExpressionValue((Object)string, (String)"e.session.player.name");
                String $i$f$forEach2 = string;
                $this$filter$iv = Float.valueOf(e.getSession().getPlayer().getWalkSpeed());
                $this$forEach$iv.put($i$f$forEach2, $this$filter$iv);
                e.getSession().getPlayer().setWalkSpeed(0.0f);
                this.sendFakeFoodLevel(e.getSession().getPlayer(), 3);
            }
            $this$forEach$iv = new String[]{"FORCE_LOOK"};
            if (e.getConversation().hasFlag((String[])$this$forEach$iv)) {
                Source<?> source = e.getSession().getSource();
                Intrinsics.checkNotNull(source, (String)"null cannot be cast to non-null type ink.ptms.chemdah.core.conversation.Source<kotlin.Any>");
                Source<?> source2 = source;
                Vector vector = source2.getOriginLocation(source2.getEntity()).subtract(e.getSession().getPlayer().getEyeLocation()).toVector().normalize();
                Intrinsics.checkNotNullExpressionValue((Object)vector, (String)"source.getOriginLocation\u2026n).toVector().normalize()");
                Vector direction = vector;
                Location location = e.getSession().getPlayer().getLocation().clone();
                Intrinsics.checkNotNullExpressionValue((Object)location, (String)"e.session.player.location.clone()");
                Location temp = location;
                temp.setDirection(direction);
                try {
                    boolean bl = false;
                    temp.checkFinite();
                    Object object2 = Result.constructor-impl((Object)e.getSession().getPlayer().teleport(temp));
                }
                catch (Throwable throwable) {
                    Object object3 = Result.constructor-impl((Object)ResultKt.createFailure((Throwable)throwable));
                }
            }
        }
    }

    @SubscribeEvent
    private final void onClosed(ConversationEvents.Closed e) {
        if (e.getConversation().getTheme() instanceof WorldSide) {
            String[] stringArray = new String[]{"NO_MOVE"};
            if (e.getConversation().hasFlag(stringArray)) {
                Float originSpeed = playerSpeed.remove(e.getSession().getPlayer().getName());
                if (originSpeed != null) {
                    ExecutorKt.submit$default((boolean)false, (boolean)false, (long)0L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(e, originSpeed){
                        final /* synthetic */ ConversationEvents.Closed $e;
                        final /* synthetic */ Float $originSpeed;
                        {
                            this.$e = $e;
                            this.$originSpeed = $originSpeed;
                            super(1);
                        }

                        public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                            Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                            this.$e.getSession().getPlayer().setWalkSpeed(this.$originSpeed.floatValue());
                        }
                    }), (int)15, null);
                }
                this.sendFakeFoodLevel(e.getSession().getPlayer(), e.getSession().getPlayer().getFoodLevel());
            }
            stringArray = new String[]{"NO_EFFECT"};
            if (e.getConversation().noFlag(stringArray)) {
                ExecutorKt.submit$default((boolean)false, (boolean)false, (long)0L, (long)0L, (Function1)((Function1)new Function1<PlatformExecutor.PlatformTask, Unit>(e){
                    final /* synthetic */ ConversationEvents.Closed $e;
                    {
                        this.$e = $e;
                        super(1);
                    }

                    /*
                     * WARNING - void declaration
                     */
                    public final void invoke(@NotNull PlatformExecutor.PlatformTask $this$submit) {
                        void $this$forEach$iv;
                        Intrinsics.checkNotNullParameter((Object)$this$submit, (String)"$this$submit");
                        Map<PotionEffectType, Integer> map = ConversationListener.INSTANCE.getFreezeEffects();
                        Object object = this.$e;
                        boolean $i$f$forEach = false;
                        Iterator<Map.Entry<K, V>> iterator = $this$forEach$iv.entrySet().iterator();
                        while (iterator.hasNext()) {
                            Map.Entry<K, V> element$iv;
                            Map.Entry<K, V> it = element$iv = iterator.next();
                            boolean bl = false;
                            object.getSession().getPlayer().removePotionEffect((PotionEffectType)it.getKey());
                        }
                        List<PotionEffect> list2 = ConversationListener.INSTANCE.getPlayerEffects().remove(this.$e.getSession().getPlayer().getName());
                        if (list2 != null) {
                            void $this$forEach$iv2;
                            object = list2;
                            ConversationEvents.Closed closed = this.$e;
                            boolean $i$f$forEach2 = false;
                            for (T element$iv : $this$forEach$iv2) {
                                PotionEffect it = (PotionEffect)element$iv;
                                boolean bl = false;
                                closed.getSession().getPlayer().addPotionEffect(it);
                            }
                        }
                        if (ConversationListener.INSTANCE.getUseFadeEffect() && !this.$e.getSession().getPlayer().hasPotionEffect(PotionEffectType.BLINDNESS)) {
                            this.$e.getSession().getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
                        }
                    }
                }), (int)15, null);
            }
        }
    }

    @SubscribeEvent(priority=EventPriority.MONITOR, ignoreCancelled=true)
    private final void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            Session session = ConversationManager.INSTANCE.getSessions().get(e.getEntity().getName());
            if (session == null) {
                return;
            }
            Session session2 = session;
            if (session2.isClosed()) {
                return;
            }
            String[] stringArray = new String[]{"FORCE_DISPLAY"};
            if (session2.getConversation().hasFlag(stringArray)) {
                e.setCancelled(true);
                return;
            }
            session2.close(true);
        }
    }

    @SubscribeEvent(priority=EventPriority.MONITOR, ignoreCancelled=true)
    private final void onMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        Intrinsics.checkNotNullExpressionValue((Object)player, (String)"e.player");
        Session session = ChemdahAPI.INSTANCE.getConversationSession(player);
        if (session == null) {
            return;
        }
        Session session2 = session;
        String[] stringArray = new String[]{"NO_MOVE"};
        if (session2.getConversation().hasFlag(stringArray) && session2.getConversation().getTheme() instanceof WorldSide) {
            Location location = e.getTo();
            Location location2 = e.getTo();
            e.setTo(new Location(e.getFrom().getWorld(), e.getFrom().getX(), e.getFrom().getY(), e.getFrom().getZ(), location != null ? location.getYaw() : e.getFrom().getYaw(), location2 != null ? location2.getPitch() : e.getFrom().getPitch()));
        }
    }

    @SubscribeEvent(priority=EventPriority.MONITOR, ignoreCancelled=true)
    private final void onDropItem(PlayerDropItemEvent e) {
        Player player = e.getPlayer();
        Intrinsics.checkNotNullExpressionValue((Object)player, (String)"e.player");
        Session session = ChemdahAPI.INSTANCE.getConversationSession(player);
        if (session == null) {
            return;
        }
        Session session2 = session;
        String[] stringArray = new String[]{"FORCE_DISPLAY"};
        if (session2.getConversation().hasFlag(stringArray)) {
            e.setCancelled(true);
        }
    }

    @SubscribeEvent
    private final void onCommand(PlayerCommandPreprocessEvent e) {
        String string = e.getMessage();
        Intrinsics.checkNotNullExpressionValue((Object)string, (String)"e.message");
        if (StringsKt.startsWith$default((String)string, (String)"/session", (boolean)false, (int)2, null)) {
            e.setCancelled(true);
            String string2 = e.getMessage();
            Intrinsics.checkNotNullExpressionValue((Object)string2, (String)"e.message");
            Object object = new String[]{" "};
            Object it = object = CollectionsKt.toMutableList((Collection)StringsKt.split$default((CharSequence)string2, (String[])object, (boolean)false, (int)0, (int)6, null));
            boolean bl = false;
            it.removeFirst();
            Object args = object;
            if (args.size() == 2 && Intrinsics.areEqual(args.get(0), (Object)"reply")) {
                Object v4;
                Session session;
                block5: {
                    Player player = e.getPlayer();
                    Intrinsics.checkNotNullExpressionValue((Object)player, (String)"e.player");
                    Session session2 = ChemdahAPI.INSTANCE.getConversationSession(player);
                    if (session2 == null) {
                        return;
                    }
                    session = session2;
                    Iterable $this$firstOrNull$iv = session.getConversation().getPlayerSide().getReply();
                    boolean $i$f$firstOrNull = false;
                    for (Object element$iv : $this$firstOrNull$iv) {
                        PlayerReply it2 = (PlayerReply)element$iv;
                        boolean bl2 = false;
                        if (!Intrinsics.areEqual((Object)it2.getRid().toString(), args.get(1))) continue;
                        v4 = element$iv;
                        break block5;
                    }
                    v4 = null;
                }
                PlayerReply playerReply = v4;
                if (playerReply == null) {
                    return;
                }
                PlayerReply reply = playerReply;
                FuturesKt.applyWithError(reply.check(session), (Function1)new Function1<Boolean, Unit>(reply, session){
                    final /* synthetic */ PlayerReply $reply;
                    final /* synthetic */ Session $session;
                    {
                        this.$reply = $reply;
                        this.$session = $session;
                        super(1);
                    }

                    public final void invoke(boolean cond) {
                        if (cond) {
                            this.$reply.select(this.$session);
                        }
                    }
                });
            }
        }
    }

    @SubscribeEvent(bind="com.sucy.skill.api.event.PlayerCastSkillEvent")
    private final void onPlayerCastSkillEvent(OptionalEvent event) {
        OptionalEvent this_$iv = event;
        boolean $i$f$get = false;
        Object object = this_$iv.getSource();
        if (object == null) {
            throw new NullPointerException("null cannot be cast to non-null type com.sucy.skill.api.event.PlayerCastSkillEvent");
        }
        PlayerCastSkillEvent e = (PlayerCastSkillEvent)object;
        Player player = e.getPlayer();
        Intrinsics.checkNotNullExpressionValue((Object)player, (String)"e.player");
        if (ChemdahAPI.INSTANCE.getConversationSession(player) != null) {
            e.setCancelled(true);
        }
    }

    private final void sendFakeFoodLevel(Player player, int foodLevel) {
        block2: {
            try {
                MinecraftServerUtilKt.sendPacket((Player)player, (Object)new PacketPlayOutUpdateHealth((float)player.getHealth(), foodLevel, 0.0f));
            }
            catch (Throwable ex) {
                if (!ProjectInfoKt.isDevelopmentMode()) break block2;
                ex.printStackTrace();
            }
        }
    }

    static {
        Pair[] pairArray = new Pair[]{TuplesKt.to((Object)PotionEffectType.BLINDNESS, (Object)0), TuplesKt.to((Object)PotionEffectType.SLOW, (Object)2)};
        freezeEffects = MapsKt.mapOf((Pair[])pairArray);
        playerEffects = new ConcurrentHashMap();
        playerSpeed = new ConcurrentHashMap();
        Baffle baffle = Baffle.of((long)1L, (TimeUnit)TimeUnit.SECONDS);
        Intrinsics.checkNotNullExpressionValue((Object)baffle, (String)"of(1, TimeUnit.SECONDS)");
        fakeFoodLevel = baffle;
    }
}

