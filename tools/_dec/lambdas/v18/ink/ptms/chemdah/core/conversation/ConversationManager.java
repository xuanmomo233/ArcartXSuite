/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
 *  ink.ptms.chemdah.taboolib.module.configuration.Config
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  kotlin.Metadata
 *  kotlin1822.collections.ArraysKt
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.core.conversation;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.ConversationEvents;
import ink.ptms.chemdah.api.event.collect.PluginReloadEvent;
import ink.ptms.chemdah.core.conversation.Conversation;
import ink.ptms.chemdah.core.conversation.ConversationManager;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.configuration.Config;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.util.ConfigurationKt;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Metadata;
import kotlin1822.collections.ArraysKt;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000F\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0011\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u0011J;\u0010\u0012\u001a\u0004\u0018\u00010\u00132\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0014\u001a\u00020\n2\b\u0010\u0015\u001a\u0004\u0018\u00010\u00012\u0012\u0010\u0016\u001a\n\u0012\u0006\b\u0001\u0012\u00020\n0\u0017\"\u00020\n\u00a2\u0006\u0002\u0010\u0018J\u0010\u0010\u0019\u001a\u00020\u000f2\u0006\u0010\u001a\u001a\u00020\u001bH\u0003R \u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0003\u001a\u00020\u00048\u0006@BX\u0087.\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u001d\u0010\b\u001a\u000e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\u000b0\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\r\u00a8\u0006\u001c"}, d2={"Link/ptms/chemdah/core/conversation/ConversationManager;", "", "()V", "<set-?>", "Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "conf", "getConf", "()Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "sessions", "Ljava/util/concurrent/ConcurrentHashMap;", "", "Link/ptms/chemdah/core/conversation/Session;", "getSessions", "()Ljava/util/concurrent/ConcurrentHashMap;", "closeSession", "", "player", "Lorg/bukkit/entity/Player;", "getConversation", "Link/ptms/chemdah/core/conversation/Conversation;", "namespace", "source", "name", "", "(Lorg/bukkit/entity/Player;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/String;)Link/ptms/chemdah/core/conversation/Conversation;", "onLoad", "e", "Link/ptms/chemdah/api/event/collect/PluginReloadEvent$Conversation;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nConversationManager.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ConversationManager.kt\nink/ptms/chemdah/core/conversation/ConversationManager\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 _Arrays.kt\nkotlin/collections/ArraysKt___ArraysKt\n+ 4 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n*L\n1#1,70:1\n288#2:71\n289#2:74\n12744#3,2:72\n215#4,2:75\n*S KotlinDebug\n*F\n+ 1 ConversationManager.kt\nink/ptms/chemdah/core/conversation/ConversationManager\n*L\n46#1:71\n46#1:74\n46#1:72,2\n64#1:75,2\n*E\n"})
public final class ConversationManager {
    @NotNull
    public static final ConversationManager INSTANCE = new ConversationManager();
    @Config(value="core/conversation.yml", migrate=true)
    private static Configuration conf;
    @NotNull
    private static final ConcurrentHashMap<String, Session> sessions;

    private ConversationManager() {
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
    public final ConcurrentHashMap<String, Session> getSessions() {
        return sessions;
    }

    @Nullable
    public final Conversation getConversation(@NotNull Player player, @NotNull String namespace, @Nullable Object source, String ... name) {
        Object v2;
        block3: {
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            Intrinsics.checkNotNullParameter((Object)namespace, (String)"namespace");
            Intrinsics.checkNotNullParameter((Object)name, (String)"name");
            Collection<Conversation> collection = ChemdahAPI.INSTANCE.getConversation().values();
            Intrinsics.checkNotNullExpressionValue(collection, (String)"ChemdahAPI.conversation.values");
            Iterable $this$firstOrNull$iv = collection;
            boolean $i$f$firstOrNull = false;
            for (Object element$iv : $this$firstOrNull$iv) {
                boolean bl;
                block2: {
                    Conversation it = (Conversation)element$iv;
                    boolean bl2 = false;
                    String[] $this$any$iv = name;
                    boolean $i$f$any = false;
                    int n = $this$any$iv.length;
                    for (int i = 0; i < n; ++i) {
                        String element$iv2;
                        String name2 = element$iv2 = $this$any$iv[i];
                        boolean bl3 = false;
                        if (!it.isNPC(namespace, name2)) continue;
                        bl = true;
                        break block2;
                    }
                    bl = false;
                }
                if (!bl) continue;
                v2 = element$iv;
                break block3;
            }
            v2 = null;
        }
        Conversation conversation2 = v2;
        ConversationEvents.Select event = new ConversationEvents.Select(player, namespace, ArraysKt.toList((Object[])name), conversation2, source);
        return event.call() ? event.getConversation() : null;
    }

    public final void closeSession(@NotNull Player player) {
        block0: {
            Intrinsics.checkNotNullParameter((Object)player, (String)"player");
            Session session = ChemdahAPI.INSTANCE.getConversationSession(player);
            if (session == null) break block0;
            session.close(true);
        }
    }

    @SubscribeEvent
    private final void onLoad(PluginReloadEvent.Conversation e) {
        Map $this$forEach$iv = ChemdahAPI.INSTANCE.getConversation();
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry element$iv;
            Map.Entry entry = element$iv = iterator.next();
            boolean bl = false;
            Conversation conversation2 = (Conversation)entry.getValue();
            ConfigurationSection root2 = conversation2.getOption().getRoot();
            List agents2 = ConfigurationKt.sectionAs(root2, "agent", onLoad.1.agents.1.INSTANCE, onLoad.1.agents.2.INSTANCE);
            CollectionsKt.addAll((Collection)conversation2.getAgent(), (Iterable)agents2);
        }
    }

    static {
        sessions = new ConcurrentHashMap();
    }
}

