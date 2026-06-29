/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.event.EventPriority
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex
 *  ink.ptms.chemdah.taboolib.library.reflex.Reflex$Companion
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration$Companion
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftServerUtilKt
 *  ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion
 *  ink.ptms.chemdah.taboolib.module.nms.Packet
 *  ink.ptms.chemdah.taboolib.module.nms.PacketSendEvent
 *  kotlin.Metadata
 *  kotlin1822.Result
 *  kotlin1822.ResultKt
 *  kotlin1822.Unit
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.block.Block
 *  org.bukkit.entity.Player
 *  org.bukkit.event.block.BlockPlaceEvent
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.core.bukkit.NMS;
import ink.ptms.chemdah.core.conversation.Conversation;
import ink.ptms.chemdah.core.conversation.Option;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.quest.QuestDevelopment;
import ink.ptms.chemdah.library.redlib.BlockDataContainerKt;
import ink.ptms.chemdah.library.redlib.DataBlock;
import ink.ptms.chemdah.taboolib.common.platform.event.EventPriority;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.taboolib.library.reflex.Reflex;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftServerUtilKt;
import ink.ptms.chemdah.taboolib.module.nms.MinecraftVersion;
import ink.ptms.chemdah.taboolib.module.nms.Packet;
import ink.ptms.chemdah.taboolib.module.nms.PacketSendEvent;
import ink.ptms.chemdah.util.StringKt;
import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import kotlin.Metadata;
import kotlin1822.Result;
import kotlin1822.ResultKt;
import kotlin1822.Unit;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000T\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0010!\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0001(B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u001a\u001a\u00020\u001b2\u0006\u0010\u001c\u001a\u00020\u001dH\u0003J\u0010\u0010\u001e\u001a\u00020\u001b2\u0006\u0010\u001c\u001a\u00020\u001fH\u0003J\u0010\u0010 \u001a\u00020\u001b2\u0006\u0010\u001c\u001a\u00020!H\u0003J\u000e\u0010\"\u001a\u00020\u0011*\u0004\u0018\u00010\u0001H\u0002J\n\u0010#\u001a\u00020\u0004*\u00020$J\n\u0010%\u001a\u00020\u0004*\u00020&J\n\u0010'\u001a\u00020\u001b*\u00020$R\u001a\u0010\u0003\u001a\u00020\u0004X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\bR\u001a\u0010\t\u001a\u00020\u0004X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u0006\"\u0004\b\u000b\u0010\bR\u001a\u0010\f\u001a\u00020\u0004X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u0006\"\u0004\b\u000e\u0010\bR \u0010\u000f\u001a\u0014\u0012\u0004\u0012\u00020\u0011\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00010\u00120\u0010X\u0082\u0004\u00a2\u0006\u0002\n\u0000R \u0010\u0013\u001a\u0014\u0012\u0004\u0012\u00020\u0011\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00110\u00120\u0010X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0014\u001a\u00020\u0015X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0016\u0010\u0017\"\u0004\b\u0018\u0010\u0019\u00a8\u0006)"}, d2={"Link/ptms/chemdah/core/quest/QuestDevelopment;", "", "()V", "enableBlockContainer", "", "getEnableBlockContainer", "()Z", "setEnableBlockContainer", "(Z)V", "enableMessageTransmit", "getEnableMessageTransmit", "setEnableMessageTransmit", "enableUniqueBlock", "getEnableUniqueBlock", "setEnableUniqueBlock", "playerMessageCache", "Ljava/util/concurrent/ConcurrentHashMap;", "", "", "playerRelease", "uniqueBlockMode", "Link/ptms/chemdah/core/quest/QuestDevelopment$UniqueBlockMode;", "getUniqueBlockMode", "()Link/ptms/chemdah/core/quest/QuestDevelopment$UniqueBlockMode;", "setUniqueBlockMode", "(Link/ptms/chemdah/core/quest/QuestDevelopment$UniqueBlockMode;)V", "onBlockPlace", "", "e", "Lorg/bukkit/event/block/BlockPlaceEvent;", "onPacketSend", "Link/ptms/chemdah/taboolib/module/nms/PacketSendEvent;", "onReleased", "Link/ptms/chemdah/api/event/collect/PlayerEvents$Released;", "getComponent", "hasTransmitMessages", "Lorg/bukkit/entity/Player;", "isPlaced", "Lorg/bukkit/block/Block;", "releaseTransmit", "UniqueBlockMode", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nQuestDevelopment.kt\nKotlin\n*S Kotlin\n*F\n+ 1 QuestDevelopment.kt\nink/ptms/chemdah/core/quest/QuestDevelopment\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,158:1\n1855#2,2:159\n*S KotlinDebug\n*F\n+ 1 QuestDevelopment.kt\nink/ptms/chemdah/core/quest/QuestDevelopment\n*L\n141#1:159,2\n*E\n"})
public final class QuestDevelopment {
    @NotNull
    public static final QuestDevelopment INSTANCE = new QuestDevelopment();
    @NotNull
    private static final ConcurrentHashMap<String, List<String>> playerRelease = new ConcurrentHashMap();
    @NotNull
    private static final ConcurrentHashMap<String, List<Object>> playerMessageCache = new ConcurrentHashMap();
    private static boolean enableBlockContainer;
    private static boolean enableUniqueBlock;
    private static boolean enableMessageTransmit;
    @NotNull
    private static UniqueBlockMode uniqueBlockMode;

    private QuestDevelopment() {
    }

    public final boolean getEnableBlockContainer() {
        return enableBlockContainer;
    }

    public final void setEnableBlockContainer(boolean bl) {
        enableBlockContainer = bl;
    }

    public final boolean getEnableUniqueBlock() {
        return enableUniqueBlock;
    }

    public final void setEnableUniqueBlock(boolean bl) {
        enableUniqueBlock = bl;
    }

    public final boolean getEnableMessageTransmit() {
        return enableMessageTransmit;
    }

    public final void setEnableMessageTransmit(boolean bl) {
        enableMessageTransmit = bl;
    }

    @NotNull
    public final UniqueBlockMode getUniqueBlockMode() {
        return uniqueBlockMode;
    }

    public final void setUniqueBlockMode(@NotNull UniqueBlockMode uniqueBlockMode) {
        Intrinsics.checkNotNullParameter((Object)((Object)uniqueBlockMode), (String)"<set-?>");
        QuestDevelopment.uniqueBlockMode = uniqueBlockMode;
    }

    @SubscribeEvent
    private final void onReleased(PlayerEvents.Released e) {
        playerRelease.remove(e.getPlayer().getName());
        playerMessageCache.remove(e.getPlayer().getName());
    }

    @SubscribeEvent(priority=EventPriority.MONITOR, ignoreCancelled=true)
    private final void onBlockPlace(BlockPlaceEvent e) {
        if (enableUniqueBlock) {
            Block block = e.getBlock();
            Intrinsics.checkNotNullExpressionValue((Object)block, (String)"e.block");
            BlockDataContainerKt.getDataContainerAsync(block, true).thenAccept(arg_0 -> QuestDevelopment.onBlockPlace$lambda$0(onBlockPlace.1.INSTANCE, arg_0));
        }
    }

    @SubscribeEvent
    private final void onPacketSend(PacketSendEvent e) {
        block9: {
            Object object;
            block11: {
                block10: {
                    Object $i$a$-runCatching-QuestDevelopment$onPacketSend$22;
                    String[] chatType2;
                    Object[] objectArray;
                    if (!enableMessageTransmit || !CollectionsKt.listOf((Object[])(objectArray = new String[]{"PacketPlayOutChat", "ClientboundPlayerChatPacket"})).contains(e.getPacket().getName())) break block9;
                    try {
                        boolean v1190;
                        boolean $i$a$-runCatching-QuestDevelopment$onPacketSend$22 = false;
                        Object object2 = Packet.read$default((Packet)e.getPacket(), (String)"chatType", (boolean)false, (int)2, null);
                        chatType2 = object2 != null ? Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)object2, (String)"chatType", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null) : null;
                        int version = MinecraftVersion.INSTANCE.getVersionId();
                        boolean v1205 = version >= 12005 && !Intrinsics.areEqual((Object)String.valueOf(chatType2 != null ? (String)Reflex.Companion.invokeMethod$default((Reflex.Companion)Reflex.Companion, (Object)chatType2, (String)"getRegisteredName", (Object[])new Object[0], (boolean)false, (boolean)false, (boolean)false, null, (int)60, null) : null), (Object)"minecraft:chat");
                        boolean v1204 = version < 12005 && !Intrinsics.areEqual((Object)String.valueOf(chatType2), (Object)"0");
                        boolean bl = v1190 = version < 11900 && !Intrinsics.areEqual((Object)String.valueOf(Packet.read$default((Packet)e.getPacket(), (String)"b", (boolean)false, (int)2, null)), (Object)"CHAT");
                        if (v1205 || v1204 || v1190) {
                            return;
                        }
                        $i$a$-runCatching-QuestDevelopment$onPacketSend$22 = Result.constructor-impl((Object)Unit.INSTANCE);
                    }
                    catch (Throwable chatType2) {
                        $i$a$-runCatching-QuestDevelopment$onPacketSend$22 = Result.constructor-impl((Object)ResultKt.createFailure((Throwable)chatType2));
                    }
                    String a = this.getComponent(e.getPacket().getSource());
                    if (Intrinsics.areEqual((Object)a, (Object)"null")) {
                        return;
                    }
                    chatType2 = new String[]{"PLEASE!PASS!ME!d3486345-e35d-326a-b5c5-787de3814770!", "/CHEMDAH!!d3486345-e35d-326a-b5c5-787de3814770!"};
                    if (StringKt.contains(a, chatType2)) break block10;
                    List<String> list2 = playerRelease.get(e.getPlayer().getName());
                    boolean bl = list2 != null ? list2.contains(a) : false;
                    if (!bl) break block11;
                }
                return;
            }
            List list3 = playerMessageCache.computeIfAbsent(e.getPlayer().getName(), arg_0 -> QuestDevelopment.onPacketSend$lambda$2(onPacketSend.message.1.INSTANCE, arg_0));
            Intrinsics.checkNotNullExpressionValue((Object)list3, (String)"playerMessageCache.compu\u2026 CopyOnWriteArrayList() }");
            List message2 = list3;
            ((Collection)message2).add(e.getPacket().getSource());
            if (message2.size() > 32) {
                CollectionsKt.removeFirstOrNull((List)message2);
            }
            if (Intrinsics.areEqual((object = ChemdahAPI.INSTANCE.getConversationSession(e.getPlayer())) != null && (object = ((Session)object).getConversation()) != null && (object = ((Conversation)object).getOption()) != null ? ((Option)object).getTheme() : null, (Object)"chat")) {
                e.setCancelled(true);
            }
        }
    }

    private final String getComponent(Object $this$getComponent) {
        String string;
        if ($this$getComponent == null) {
            return "null";
        }
        String value2 = null;
        if (MinecraftVersion.INSTANCE.getVersionId() >= 11900) {
            Object object = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)$this$getComponent, (String)"body", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
            string = String.valueOf(object != null ? Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)object, (String)"content", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null) : null);
        } else {
            string = value2 = String.valueOf(Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)$this$getComponent, (String)"a", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null));
        }
        if (Intrinsics.areEqual((Object)value2, (Object)"null") && MinecraftVersion.INSTANCE.getMajor() >= 10) {
            try {
                boolean bl = false;
                value2 = Coerce.toList((Object)Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)$this$getComponent, (String)"components", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null)).toString();
                if (Intrinsics.areEqual((Object)value2, (Object)"[]")) {
                    value2 = String.valueOf(Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, (Object)$this$getComponent, (String)"adventure$message", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null));
                }
                Object object = Result.constructor-impl((Object)Unit.INSTANCE);
            }
            catch (Throwable throwable) {
                Object object = Result.constructor-impl((Object)ResultKt.createFailure((Throwable)throwable));
            }
        }
        return value2;
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public final boolean isPlaced(@NotNull Block $this$isPlaced) {
        Intrinsics.checkNotNullParameter((Object)$this$isPlaced, (String)"<this>");
        if (!enableUniqueBlock) return false;
        DataBlock dataBlock = BlockDataContainerKt.getDataContainer($this$isPlaced, false);
        if (dataBlock == null) return false;
        boolean bl = Intrinsics.areEqual((Object)dataBlock.getBoolean("placed"), (Object)true);
        if (!bl) return false;
        return true;
    }

    public final boolean hasTransmitMessages(@NotNull Player $this$hasTransmitMessages) {
        Intrinsics.checkNotNullParameter((Object)$this$hasTransmitMessages, (String)"<this>");
        return playerMessageCache.containsKey($this$hasTransmitMessages.getName());
    }

    public final void releaseTransmit(@NotNull Player $this$releaseTransmit) {
        block3: {
            Intrinsics.checkNotNullParameter((Object)$this$releaseTransmit, (String)"<this>");
            if (!enableMessageTransmit) break block3;
            CopyOnWriteArrayList<String> list2 = new CopyOnWriteArrayList<String>();
            Map map = playerRelease;
            String string = $this$releaseTransmit.getName();
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"name");
            map.put(string, list2);
            List<Object> list3 = playerMessageCache.get($this$releaseTransmit.getName());
            if (list3 != null) {
                Iterable $this$forEach$iv = list3;
                boolean $i$f$forEach = false;
                Iterator iterator = $this$forEach$iv.iterator();
                while (iterator.hasNext()) {
                    Object element$iv;
                    Object packet2 = element$iv = iterator.next();
                    boolean bl = false;
                    String value2 = INSTANCE.getComponent(packet2);
                    list2.add(value2);
                    if (MinecraftVersion.INSTANCE.getVersionId() >= 11900) {
                        NMS nMS = NMS.Companion.getINSTANCE();
                        Object object = Reflex.Companion.getProperty$default((Reflex.Companion)Reflex.Companion, packet2, (String)"sender", (boolean)false, (boolean)false, (boolean)false, null, (int)30, null);
                        Intrinsics.checkNotNull((Object)object);
                        nMS.sendSystemMessage($this$releaseTransmit, value2, (UUID)object);
                        continue;
                    }
                    MinecraftServerUtilKt.sendPacket((Player)$this$releaseTransmit, packet2);
                }
            }
        }
    }

    private static final void onBlockPlace$lambda$0(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }

    private static final List onPacketSend$lambda$2(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        return (List)$tmp0.invoke(p0);
    }

    static {
        uniqueBlockMode = UniqueBlockMode.AUTO;
        File file = new File(IOKt.getDataFolder(), "development.yml");
        if (file.exists()) {
            Configuration conf = Configuration.Companion.loadFromFile$default((Configuration.Companion)Configuration.Companion, (File)file, null, (boolean)false, (int)6, null);
            enableBlockContainer = conf.getBoolean("enable-block-container");
            enableUniqueBlock = conf.getBoolean("enable-unique-block");
            enableMessageTransmit = conf.getBoolean("enable-message-transmit");
            UniqueBlockMode uniqueBlockMode = (UniqueBlockMode)conf.getEnum("unique-block-mode", UniqueBlockMode.class);
            if (uniqueBlockMode == null) {
                uniqueBlockMode = UniqueBlockMode.AUTO;
            }
            QuestDevelopment.uniqueBlockMode = uniqueBlockMode;
        }
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0005\b\u0086\u0001\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/core/quest/QuestDevelopment$UniqueBlockMode;", "", "(Ljava/lang/String;I)V", "AUTO", "SQLITE", "PDC", "Chemdah"})
    public static final class UniqueBlockMode
    extends Enum<UniqueBlockMode> {
        public static final /* enum */ UniqueBlockMode AUTO = new UniqueBlockMode();
        public static final /* enum */ UniqueBlockMode SQLITE = new UniqueBlockMode();
        public static final /* enum */ UniqueBlockMode PDC = new UniqueBlockMode();
        private static final /* synthetic */ UniqueBlockMode[] $VALUES;

        public static UniqueBlockMode[] values() {
            return (UniqueBlockMode[])$VALUES.clone();
        }

        public static UniqueBlockMode valueOf(String value2) {
            return Enum.valueOf(UniqueBlockMode.class, value2);
        }

        static {
            $VALUES = uniqueBlockModeArray = new UniqueBlockMode[]{UniqueBlockMode.AUTO, UniqueBlockMode.SQLITE, UniqueBlockMode.PDC};
        }
    }
}

