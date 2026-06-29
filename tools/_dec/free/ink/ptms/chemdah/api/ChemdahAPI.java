/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt
 *  ink.ptms.chemdah.taboolib.common.platform.function.IOKt
 *  ink.ptms.chemdah.taboolib.common.util.LocaleKt
 *  ink.ptms.chemdah.taboolib.common.util.ResettableLazy
 *  ink.ptms.chemdah.taboolib.module.kether.KetherFunction
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherShell
 *  ink.ptms.chemdah.taboolib.module.kether.KetherShell$VariableMap
 *  ink.ptms.chemdah.taboolib.module.kether.Workspace
 *  ink.ptms.chemdah.taboolib.module.lang.Language
 *  ink.ptms.chemdah.taboolib.platform.bukkit.Parallel
 *  kotlin.Metadata
 *  kotlin1822.Pair
 *  kotlin1822.TuplesKt
 *  kotlin1822.Unit
 *  kotlin1822.collections.MapsKt
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.reflect.KClass
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.api;

import ink.ptms.chemdah.Chemdah;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.ChemdahEventFactory;
import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.conversation.Conversation;
import ink.ptms.chemdah.core.conversation.ConversationLoader;
import ink.ptms.chemdah.core.conversation.ConversationManager;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.conversation.theme.Theme;
import ink.ptms.chemdah.core.database.Database;
import ink.ptms.chemdah.core.quest.CoreConfigDeserializer;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.QuestChecker;
import ink.ptms.chemdah.core.quest.QuestLoader;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import ink.ptms.chemdah.core.quest.TemplateGroup;
import ink.ptms.chemdah.core.quest.addon.Addon;
import ink.ptms.chemdah.core.quest.addon.tracker.QuestTrackHandler;
import ink.ptms.chemdah.core.quest.meta.Meta;
import ink.ptms.chemdah.core.quest.objective.Objective;
import ink.ptms.chemdah.core.quest.objective.bukkit.UnitsKt;
import ink.ptms.chemdah.core.quest.objective.other.ITrigger;
import ink.ptms.chemdah.core.quest.selector.DataMatchHandler;
import ink.ptms.chemdah.module.Module;
import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.platform.function.ExecutorKt;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.common.util.LocaleKt;
import ink.ptms.chemdah.taboolib.common.util.ResettableLazy;
import ink.ptms.chemdah.taboolib.module.kether.KetherFunction;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.module.kether.Workspace;
import ink.ptms.chemdah.taboolib.module.lang.Language;
import ink.ptms.chemdah.taboolib.platform.bukkit.Parallel;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Metadata;
import kotlin1822.Pair;
import kotlin1822.TuplesKt;
import kotlin1822.Unit;
import kotlin1822.collections.MapsKt;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.reflect.KClass;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u00ee\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0002\b\u000f\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010 \n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010$\n\u0002\b\b\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010Y\u001a\u00020Z2\u0006\u0010[\u001a\u00020\u00052\u0006\u0010\\\u001a\u00020\u0006J\u001a\u0010]\u001a\u00020Z2\u0006\u0010[\u001a\u00020\u00052\n\u0010^\u001a\u0006\u0012\u0002\b\u00030\u0013J\u001e\u0010_\u001a\u00020Z2\u0006\u0010[\u001a\u00020\u00052\u000e\u0010`\u001a\n\u0012\u0006\b\u0001\u0012\u00020,0+J\"\u0010a\u001a\u00020Z2\u0006\u0010[\u001a\u00020\u00052\u0012\u0010b\u001a\u000e\u0012\n\b\u0001\u0012\u0006\u0012\u0002\b\u0003090+J\u001a\u0010c\u001a\u00020Z2\u0006\u0010[\u001a\u00020\u00052\n\u0010d\u001a\u0006\u0012\u0002\b\u00030<J\u0016\u0010e\u001a\u00020Z2\u0006\u0010[\u001a\u00020\u00052\u0006\u0010f\u001a\u00020?J\u0016\u0010g\u001a\u00020Z2\u0006\u0010[\u001a\u00020\u00052\u0006\u0010h\u001a\u00020BJ\u0010\u0010\b\u001a\u0004\u0018\u00010\u00062\u0006\u0010[\u001a\u00020\u0005J\u0014\u0010\u0014\u001a\b\u0012\u0002\b\u0003\u0018\u00010\u00132\u0006\u0010[\u001a\u00020\u0005J\u001e\u0010i\u001a\u00020j\"\b\b\u0000\u0010k*\u00020j2\f\u0010l\u001a\b\u0012\u0004\u0012\u0002Hk0mJ\u0018\u0010-\u001a\f\u0012\u0006\b\u0001\u0012\u00020,\u0018\u00010+2\u0006\u0010[\u001a\u00020\u0005J\u001c\u0010:\u001a\u0010\u0012\n\b\u0001\u0012\u0006\u0012\u0002\b\u000309\u0018\u00010+2\u0006\u0010[\u001a\u00020\u0005J\u0014\u0010=\u001a\b\u0012\u0002\b\u0003\u0018\u00010<2\u0006\u0010[\u001a\u00020\u0005J\u0010\u0010@\u001a\u0004\u0018\u00010?2\u0006\u0010[\u001a\u00020\u0005J\u0010\u0010C\u001a\u0004\u0018\u00010B2\u0006\u0010[\u001a\u00020\u0005J\u0010\u0010n\u001a\u0004\u0018\u00010\u00052\u0006\u0010o\u001a\u00020\u0005J\f\u0010p\u001a\b\u0012\u0004\u0012\u00020\u00050qJ8\u0010r\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00010s2\u0006\u0010t\u001a\u00020\u00052\n\b\u0002\u0010u\u001a\u0004\u0018\u00010M2\u0014\b\u0002\u0010v\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00010wJ0\u0010x\u001a\u00020\u00052\u0006\u0010t\u001a\u00020\u00052\n\b\u0002\u0010u\u001a\u0004\u0018\u00010M2\u0014\b\u0002\u0010v\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00010wJ\u0006\u0010y\u001a\u00020ZJ.\u0010z\u001a\u00020Z2\u0006\u0010o\u001a\u00020\u00052\b\u0010{\u001a\u0004\u0018\u00010\u00052\b\b\u0002\u0010|\u001a\u00020U2\n\b\u0002\u0010}\u001a\u0004\u0018\u00010\u0005J\b\u0010H\u001a\u00020ZH\u0003J\u0012\u0010~\u001a\u00020Z*\u00020M2\u0006\u0010{\u001a\u00020\u0005R-\u0010\u0003\u001a\u001e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u0004j\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u0006`\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\n\u001a\u00020\u000b8F\u00a2\u0006\u0006\u001a\u0004\b\f\u0010\rR\u0011\u0010\u000e\u001a\u00020\u000f8F\u00a2\u0006\u0006\u001a\u0004\b\u0010\u0010\u0011R5\u0010\u0012\u001a&\u0012\u0004\u0012\u00020\u0005\u0012\b\u0012\u0006\u0012\u0002\b\u00030\u00130\u0004j\u0012\u0012\u0004\u0012\u00020\u0005\u0012\b\u0012\u0006\u0012\u0002\b\u00030\u0013`\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\tR\u001a\u0010\u0015\u001a\u00020\u0016X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0017\u0010\u0018\"\u0004\b\u0019\u0010\u001aR\u0011\u0010\u001b\u001a\u00020\u001c\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001eR\u001a\u0010\u001f\u001a\u00020 X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b!\u0010\"\"\u0004\b#\u0010$R\u001d\u0010%\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020'0&\u00a2\u0006\b\n\u0000\u001a\u0004\b(\u0010)R=\u0010*\u001a.\u0012\u0004\u0012\u00020\u0005\u0012\f\u0012\n\u0012\u0006\b\u0001\u0012\u00020,0+0\u0004j\u0016\u0012\u0004\u0012\u00020\u0005\u0012\f\u0012\n\u0012\u0006\b\u0001\u0012\u00020,0+`\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b-\u0010\tR\u001a\u0010.\u001a\u00020/X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b0\u00101\"\u0004\b2\u00103R\u0011\u00104\u001a\u0002058F\u00a2\u0006\u0006\u001a\u0004\b6\u00107RE\u00108\u001a6\u0012\u0004\u0012\u00020\u0005\u0012\u0010\u0012\u000e\u0012\n\b\u0001\u0012\u0006\u0012\u0002\b\u0003090+0\u0004j\u001a\u0012\u0004\u0012\u00020\u0005\u0012\u0010\u0012\u000e\u0012\n\b\u0001\u0012\u0006\u0012\u0002\b\u0003090+`\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b:\u0010\tR5\u0010;\u001a&\u0012\u0004\u0012\u00020\u0005\u0012\b\u0012\u0006\u0012\u0002\b\u00030<0\u0004j\u0012\u0012\u0004\u0012\u00020\u0005\u0012\b\u0012\u0006\u0012\u0002\b\u00030<`\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b=\u0010\tR-\u0010>\u001a\u001e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020?0\u0004j\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020?`\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b@\u0010\tR-\u0010A\u001a\u001e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020B0\u0004j\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020B`\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\bC\u0010\tR\u0011\u0010D\u001a\u00020E\u00a2\u0006\b\n\u0000\u001a\u0004\bF\u0010GR\u0011\u0010H\u001a\u00020I\u00a2\u0006\b\n\u0000\u001a\u0004\bJ\u0010KR\u0015\u0010L\u001a\u00020'*\u00020M8F\u00a2\u0006\u0006\u001a\u0004\bN\u0010OR\u0017\u0010P\u001a\u0004\u0018\u00010Q*\u00020M8F\u00a2\u0006\u0006\u001a\u0004\bR\u0010SR\u0015\u0010T\u001a\u00020U*\u00020M8F\u00a2\u0006\u0006\u001a\u0004\bT\u0010VR\u0015\u0010W\u001a\u00020U*\u00020M8F\u00a2\u0006\u0006\u001a\u0004\bX\u0010V\u00a8\u0006\u007f"}, d2={"Link/ptms/chemdah/api/ChemdahAPI;", "", "()V", "conversation", "Ljava/util/HashMap;", "", "Link/ptms/chemdah/core/conversation/Conversation;", "Lkotlin1822/collections/HashMap;", "getConversation", "()Ljava/util/HashMap;", "conversationLoader", "Link/ptms/chemdah/core/conversation/ConversationLoader;", "getConversationLoader", "()Link/ptms/chemdah/core/conversation/ConversationLoader;", "conversationManager", "Link/ptms/chemdah/core/conversation/ConversationManager;", "getConversationManager", "()Link/ptms/chemdah/core/conversation/ConversationManager;", "conversationTheme", "Link/ptms/chemdah/core/conversation/theme/Theme;", "getConversationTheme", "coreConfigDeserializer", "Link/ptms/chemdah/core/quest/CoreConfigDeserializer;", "getCoreConfigDeserializer", "()Link/ptms/chemdah/core/quest/CoreConfigDeserializer;", "setCoreConfigDeserializer", "(Link/ptms/chemdah/core/quest/CoreConfigDeserializer;)V", "dataMatchHandler", "Link/ptms/chemdah/core/quest/selector/DataMatchHandler;", "getDataMatchHandler", "()Link/ptms/chemdah/core/quest/selector/DataMatchHandler;", "eventFactory", "Link/ptms/chemdah/api/ChemdahEventFactory;", "getEventFactory", "()Link/ptms/chemdah/api/ChemdahEventFactory;", "setEventFactory", "(Link/ptms/chemdah/api/ChemdahEventFactory;)V", "playerProfile", "Ljava/util/concurrent/ConcurrentHashMap;", "Link/ptms/chemdah/core/PlayerProfile;", "getPlayerProfile", "()Ljava/util/concurrent/ConcurrentHashMap;", "questAddon", "Ljava/lang/Class;", "Link/ptms/chemdah/core/quest/addon/Addon;", "getQuestAddon", "questChecker", "Link/ptms/chemdah/core/quest/QuestChecker;", "getQuestChecker", "()Link/ptms/chemdah/core/quest/QuestChecker;", "setQuestChecker", "(Link/ptms/chemdah/core/quest/QuestChecker;)V", "questLoader", "Link/ptms/chemdah/core/quest/QuestLoader;", "getQuestLoader", "()Link/ptms/chemdah/core/quest/QuestLoader;", "questMeta", "Link/ptms/chemdah/core/quest/meta/Meta;", "getQuestMeta", "questObjective", "Link/ptms/chemdah/core/quest/objective/Objective;", "getQuestObjective", "questTemplate", "Link/ptms/chemdah/core/quest/Template;", "getQuestTemplate", "questTemplateGroup", "Link/ptms/chemdah/core/quest/TemplateGroup;", "getQuestTemplateGroup", "trackHandler", "Link/ptms/chemdah/core/quest/addon/tracker/QuestTrackHandler;", "getTrackHandler", "()Link/ptms/chemdah/core/quest/addon/tracker/QuestTrackHandler;", "workspace", "Link/ptms/chemdah/taboolib/module/kether/Workspace;", "getWorkspace", "()Link/ptms/chemdah/taboolib/module/kether/Workspace;", "chemdahProfile", "Lorg/bukkit/entity/Player;", "getChemdahProfile", "(Lorg/bukkit/entity/Player;)Link/ptms/chemdah/core/PlayerProfile;", "conversationSession", "Link/ptms/chemdah/core/conversation/Session;", "getConversationSession", "(Lorg/bukkit/entity/Player;)Link/ptms/chemdah/core/conversation/Session;", "isChemdahProfileLoaded", "", "(Lorg/bukkit/entity/Player;)Z", "nonChemdahProfileLoaded", "getNonChemdahProfileLoaded", "addConversation", "", "id", "con", "addConversationTheme", "theme", "addQuestAddon", "addon", "addQuestMeta", "meta", "addQuestObjective", "objective", "addQuestTemplate", "template", "addQuestTemplateGroup", "templateGroup", "getModule", "Link/ptms/chemdah/module/Module;", "T", "kClass", "Lkotlin1822/reflect/KClass;", "getVariable", "key", "getVariables", "", "invokeKether", "Ljava/util/concurrent/CompletableFuture;", "source", "player", "vars", "", "parseFunction", "reloadAll", "setVariable", "value", "append", "default", "callTrigger", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nChemdahAPI.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ChemdahAPI.kt\nink/ptms/chemdah/api/ChemdahAPI\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 _Maps.kt\nkotlin/collections/MapsKt___MapsKt\n+ 4 ArraysJVM.kt\nkotlin/collections/ArraysKt__ArraysJVMKt\n*L\n1#1,428:1\n1855#2:429\n1855#2,2:430\n1856#2:432\n125#3:433\n152#3,3:434\n125#3:439\n152#3,3:440\n37#4,2:437\n37#4,2:443\n*S KotlinDebug\n*F\n+ 1 ChemdahAPI.kt\nink/ptms/chemdah/api/ChemdahAPI\n*L\n230#1:429\n231#1:430,2\n230#1:432\n401#1:433\n401#1:434,3\n406#1:439\n406#1:440,3\n401#1:437,2\n406#1:443,2\n*E\n"})
public final class ChemdahAPI {
    @NotNull
    public static final ChemdahAPI INSTANCE = new ChemdahAPI();
    @NotNull
    private static final Workspace workspace = new Workspace(new File(IOKt.getDataFolder(), "module/script"), null, UtilsForKetherKt.getNamespace(), 2, null);
    @NotNull
    private static final HashMap<String, Conversation> conversation = new HashMap<String, Conversation>(){

        @Nullable
        public Conversation put(@NotNull String key, @NotNull Conversation value2) {
            Intrinsics.checkNotNullParameter((Object)key, (String)"key");
            Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
            if (this.containsKey((Object)key)) {
                Object[] objectArray = new Object[]{"\u5df2\u5b58\u5728\u76f8\u540c\u7684\u5bf9\u8bdd: " + key};
                IOKt.warning((Object[])objectArray);
                return null;
            }
            return super.put(key, value2);
        }

        public void putAll(@NotNull Map<? extends String, ? extends Conversation> m) {
            Intrinsics.checkNotNullParameter(m, (String)"m");
            Map<? extends String, ? extends Conversation> $this$forEach$iv = m;
            boolean $i$f$forEach = false;
            Iterator<Map.Entry<? extends String, ? extends Conversation>> iterator = $this$forEach$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<? extends String, ? extends Conversation> element$iv;
                Map.Entry<? extends String, ? extends Conversation> it = element$iv = iterator.next();
                boolean bl = false;
                this.put(it.getKey(), it.getValue());
            }
        }
    };
    @NotNull
    private static final HashMap<String, Theme<?>> conversationTheme = new HashMap<String, Theme<?>>(){

        @Nullable
        public Theme<?> put(@NotNull String key, @NotNull Theme<?> value2) {
            Intrinsics.checkNotNullParameter((Object)key, (String)"key");
            Intrinsics.checkNotNullParameter(value2, (String)"value");
            if (this.containsKey((Object)key)) {
                Object[] objectArray = new Object[]{"\u5df2\u5b58\u5728\u76f8\u540c\u7684\u5bf9\u8bdd\u4e3b\u9898: " + key};
                IOKt.warning((Object[])objectArray);
                return null;
            }
            return super.put(key, value2);
        }
    };
    @NotNull
    private static final HashMap<String, Class<? extends Meta<?>>> questMeta = new HashMap<String, Class<? extends Meta<?>>>(){

        @Nullable
        public Class<? extends Meta<?>> put(@NotNull String key, @NotNull Class<? extends Meta<?>> value2) {
            Intrinsics.checkNotNullParameter((Object)key, (String)"key");
            Intrinsics.checkNotNullParameter(value2, (String)"value");
            if (this.containsKey((Object)key)) {
                Object[] objectArray = new Object[]{"\u5df2\u5b58\u5728\u76f8\u540c\u7684\u4efb\u52a1\u5143\u6570\u636e: " + key};
                IOKt.warning((Object[])objectArray);
                return null;
            }
            return super.put(key, value2);
        }

        public void putAll(@NotNull Map<? extends String, ? extends Class<? extends Meta<?>>> m) {
            Intrinsics.checkNotNullParameter(m, (String)"m");
            Map<String, Class<Meta<?>>> $this$forEach$iv = m;
            boolean $i$f$forEach = false;
            Iterator<Map.Entry<String, Class<Meta<?>>>> iterator = $this$forEach$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Class<Meta<?>>> element$iv;
                Map.Entry<String, Class<Meta<?>>> it = element$iv = iterator.next();
                boolean bl = false;
                this.put(it.getKey(), it.getValue());
            }
        }
    };
    @NotNull
    private static final HashMap<String, Class<? extends Addon>> questAddon = new HashMap<String, Class<? extends Addon>>(){

        @Nullable
        public Class<? extends Addon> put(@NotNull String key, @NotNull Class<? extends Addon> value2) {
            Intrinsics.checkNotNullParameter((Object)key, (String)"key");
            Intrinsics.checkNotNullParameter(value2, (String)"value");
            if (this.containsKey((Object)key)) {
                Object[] objectArray = new Object[]{"\u5df2\u5b58\u5728\u76f8\u540c\u7684\u4efb\u52a1\u7ec4\u4ef6: " + key};
                IOKt.warning((Object[])objectArray);
                return null;
            }
            return super.put(key, value2);
        }

        public void putAll(@NotNull Map<? extends String, ? extends Class<? extends Addon>> m) {
            Intrinsics.checkNotNullParameter(m, (String)"m");
            Map<? extends String, ? extends Class<? extends Addon>> $this$forEach$iv = m;
            boolean $i$f$forEach = false;
            Iterator<Map.Entry<? extends String, ? extends Class<? extends Addon>>> iterator = $this$forEach$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<? extends String, ? extends Class<? extends Addon>> element$iv;
                Map.Entry<? extends String, ? extends Class<? extends Addon>> it = element$iv = iterator.next();
                boolean bl = false;
                this.put(it.getKey(), it.getValue());
            }
        }
    };
    @NotNull
    private static final HashMap<String, Template> questTemplate = new HashMap<String, Template>(){

        @Nullable
        public Template put(@NotNull String key, @NotNull Template value2) {
            Intrinsics.checkNotNullParameter((Object)key, (String)"key");
            Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
            if (this.containsKey((Object)key)) {
                Object[] objectArray = new Object[]{"\u5df2\u5b58\u5728\u76f8\u540c\u7684\u4efb\u52a1\u6a21\u677f: " + key};
                IOKt.warning((Object[])objectArray);
                return null;
            }
            return super.put(key, value2);
        }

        public void putAll(@NotNull Map<? extends String, ? extends Template> m) {
            Intrinsics.checkNotNullParameter(m, (String)"m");
            Map<? extends String, ? extends Template> $this$forEach$iv = m;
            boolean $i$f$forEach = false;
            Iterator<Map.Entry<? extends String, ? extends Template>> iterator = $this$forEach$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<? extends String, ? extends Template> element$iv;
                Map.Entry<? extends String, ? extends Template> it = element$iv = iterator.next();
                boolean bl = false;
                this.put(it.getKey(), it.getValue());
            }
        }
    };
    @NotNull
    private static final HashMap<String, TemplateGroup> questTemplateGroup = new HashMap<String, TemplateGroup>(){

        @Nullable
        public TemplateGroup put(@NotNull String key, @NotNull TemplateGroup value2) {
            Intrinsics.checkNotNullParameter((Object)key, (String)"key");
            Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
            if (this.containsKey((Object)key)) {
                Object[] objectArray = new Object[]{"\u5df2\u5b58\u5728\u76f8\u540c\u7684\u4efb\u52a1\u7ec4\u6a21\u677f: " + key};
                IOKt.warning((Object[])objectArray);
                return null;
            }
            return super.put(key, value2);
        }

        public void putAll(@NotNull Map<? extends String, ? extends TemplateGroup> m) {
            Intrinsics.checkNotNullParameter(m, (String)"m");
            Map<? extends String, ? extends TemplateGroup> $this$forEach$iv = m;
            boolean $i$f$forEach = false;
            Iterator<Map.Entry<? extends String, ? extends TemplateGroup>> iterator = $this$forEach$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<? extends String, ? extends TemplateGroup> element$iv;
                Map.Entry<? extends String, ? extends TemplateGroup> it = element$iv = iterator.next();
                boolean bl = false;
                this.put(it.getKey(), it.getValue());
            }
        }
    };
    @NotNull
    private static final HashMap<String, Objective<?>> questObjective = new HashMap<String, Objective<?>>(){

        @Nullable
        public Objective<?> put(@NotNull String key, @NotNull Objective<?> value2) {
            Intrinsics.checkNotNullParameter((Object)key, (String)"key");
            Intrinsics.checkNotNullParameter(value2, (String)"value");
            if (this.containsKey((Object)key)) {
                Object[] objectArray = new Object[]{"\u5df2\u5b58\u5728\u76f8\u540c\u7684\u4efb\u52a1\u7c7b\u578b: " + key};
                IOKt.warning((Object[])objectArray);
                return null;
            }
            return super.put(key, value2);
        }

        public void putAll(@NotNull Map<? extends String, ? extends Objective<?>> m) {
            Intrinsics.checkNotNullParameter(m, (String)"m");
            Map<String, Objective<?>> $this$forEach$iv = m;
            boolean $i$f$forEach = false;
            Iterator<Map.Entry<String, Objective<?>>> iterator = $this$forEach$iv.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Objective<?>> element$iv;
                Map.Entry<String, Objective<?>> it = element$iv = iterator.next();
                boolean bl = false;
                this.put(it.getKey(), it.getValue());
            }
        }
    };
    @NotNull
    private static final ConcurrentHashMap<String, PlayerProfile> playerProfile = new ConcurrentHashMap<String, PlayerProfile>(){

        @Nullable
        public PlayerProfile put(@NotNull String key, @NotNull PlayerProfile value2) {
            Intrinsics.checkNotNullParameter((Object)key, (String)"key");
            Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
            Object[] objectArray = new Object[]{"\u73a9\u5bb6\u6570\u636e\u5df2\u5728\u7f13\u5b58\u4e2d\u6ce8\u518c: " + key};
            IOKt.info((Object[])objectArray);
            return super.put(key, value2);
        }

        @Nullable
        public PlayerProfile remove(@NotNull String key) {
            Intrinsics.checkNotNullParameter((Object)key, (String)"key");
            Object[] objectArray = new Object[]{"\u73a9\u5bb6\u6570\u636e\u5df2\u4ece\u7f13\u5b58\u4e2d\u79fb\u9664: " + key};
            IOKt.info((Object[])objectArray);
            return (PlayerProfile)super.remove(key);
        }
    };
    @NotNull
    private static ChemdahEventFactory eventFactory = new ChemdahEventFactory();
    @NotNull
    private static final QuestTrackHandler trackHandler = QuestTrackHandler.INSTANCE;
    @NotNull
    private static final DataMatchHandler dataMatchHandler = DataMatchHandler.INSTANCE;
    @NotNull
    private static QuestChecker questChecker = new QuestChecker();
    @NotNull
    private static CoreConfigDeserializer coreConfigDeserializer = new CoreConfigDeserializer();

    private ChemdahAPI() {
    }

    @NotNull
    public final Workspace getWorkspace() {
        return workspace;
    }

    @NotNull
    public final HashMap<String, Conversation> getConversation() {
        return conversation;
    }

    @NotNull
    public final HashMap<String, Theme<?>> getConversationTheme() {
        return conversationTheme;
    }

    @NotNull
    public final HashMap<String, Class<? extends Meta<?>>> getQuestMeta() {
        return questMeta;
    }

    @NotNull
    public final HashMap<String, Class<? extends Addon>> getQuestAddon() {
        return questAddon;
    }

    @NotNull
    public final HashMap<String, Template> getQuestTemplate() {
        return questTemplate;
    }

    @NotNull
    public final HashMap<String, TemplateGroup> getQuestTemplateGroup() {
        return questTemplateGroup;
    }

    @NotNull
    public final HashMap<String, Objective<?>> getQuestObjective() {
        return questObjective;
    }

    @NotNull
    public final ConcurrentHashMap<String, PlayerProfile> getPlayerProfile() {
        return playerProfile;
    }

    @NotNull
    public final ChemdahEventFactory getEventFactory() {
        return eventFactory;
    }

    public final void setEventFactory(@NotNull ChemdahEventFactory chemdahEventFactory) {
        Intrinsics.checkNotNullParameter((Object)chemdahEventFactory, (String)"<set-?>");
        eventFactory = chemdahEventFactory;
    }

    @NotNull
    public final QuestTrackHandler getTrackHandler() {
        return trackHandler;
    }

    @NotNull
    public final DataMatchHandler getDataMatchHandler() {
        return dataMatchHandler;
    }

    @NotNull
    public final QuestChecker getQuestChecker() {
        return questChecker;
    }

    public final void setQuestChecker(@NotNull QuestChecker questChecker) {
        Intrinsics.checkNotNullParameter((Object)questChecker, (String)"<set-?>");
        ChemdahAPI.questChecker = questChecker;
    }

    @NotNull
    public final QuestLoader getQuestLoader() {
        return QuestLoader.INSTANCE;
    }

    @NotNull
    public final ConversationLoader getConversationLoader() {
        return ConversationLoader.INSTANCE;
    }

    @NotNull
    public final ConversationManager getConversationManager() {
        return ConversationManager.INSTANCE;
    }

    @NotNull
    public final CoreConfigDeserializer getCoreConfigDeserializer() {
        return coreConfigDeserializer;
    }

    public final void setCoreConfigDeserializer(@NotNull CoreConfigDeserializer coreConfigDeserializer) {
        Intrinsics.checkNotNullParameter((Object)coreConfigDeserializer, (String)"<set-?>");
        ChemdahAPI.coreConfigDeserializer = coreConfigDeserializer;
    }

    @NotNull
    public final PlayerProfile getChemdahProfile(@NotNull Player $this$chemdahProfile) {
        Intrinsics.checkNotNullParameter((Object)$this$chemdahProfile, (String)"<this>");
        PlayerProfile playerProfile2 = playerProfile.get($this$chemdahProfile.getName());
        if (playerProfile2 == null) {
            throw new IllegalStateException(LocaleKt.t((String)("\n                " + $this$chemdahProfile.getName() + " \u6ca1\u6709\u4efb\u52a1\u6570\u636e\uff08\u672a\u52a0\u8f7d\u6216\u5df2\u79bb\u7ebf\uff09\n                " + $this$chemdahProfile.getName() + " has no quest data (not loaded or offline)\n            ")).toString());
        }
        return playerProfile2;
    }

    public final boolean isChemdahProfileLoaded(@NotNull Player $this$isChemdahProfileLoaded) {
        Intrinsics.checkNotNullParameter((Object)$this$isChemdahProfileLoaded, (String)"<this>");
        return playerProfile.containsKey($this$isChemdahProfileLoaded.getName());
    }

    public final boolean getNonChemdahProfileLoaded(@NotNull Player $this$nonChemdahProfileLoaded) {
        Intrinsics.checkNotNullParameter((Object)$this$nonChemdahProfileLoaded, (String)"<this>");
        return !playerProfile.containsKey($this$nonChemdahProfileLoaded.getName());
    }

    @Nullable
    public final Session getConversationSession(@NotNull Player $this$conversationSession) {
        Intrinsics.checkNotNullParameter((Object)$this$conversationSession, (String)"<this>");
        return ConversationManager.INSTANCE.getSessions().get($this$conversationSession.getName());
    }

    public final void callTrigger(@NotNull Player $this$callTrigger, @NotNull String value2) {
        Intrinsics.checkNotNullParameter((Object)$this$callTrigger, (String)"<this>");
        Intrinsics.checkNotNullParameter((Object)value2, (String)"value");
        if (!new PlayerEvents.Trigger($this$callTrigger, value2).call()) {
            return;
        }
        PlayerProfile chemdahProfile = this.getChemdahProfile($this$callTrigger);
        Iterable $this$forEach$iv = chemdahProfile.getQuests(true);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            Quest quest2 = (Quest)element$iv;
            boolean bl = false;
            Iterable $this$forEach$iv2 = quest2.getTasks();
            boolean $i$f$forEach2 = false;
            for (Object element$iv2 : $this$forEach$iv2) {
                Task task = (Task)element$iv2;
                boolean bl2 = false;
                Objective<? extends Object> objective2 = task.getObjective();
                ITrigger trigger2 = objective2 instanceof ITrigger ? (ITrigger)objective2 : null;
                Object object = trigger2;
                boolean bl3 = object != null && (object = ((ITrigger)object).getValues(task)) != null ? object.contains(value2) : false;
                if (!bl3) continue;
                QuestLoader.INSTANCE.handleTask(quest2.getProfile(), task, quest2, UnitsKt.getEMPTY_EVENT()).thenAccept(arg_0 -> ChemdahAPI.callTrigger$lambda$2$lambda$1$lambda$0((Function1)new Function1<Void, Unit>(quest2){
                    final /* synthetic */ Quest $quest;
                    {
                        this.$quest = $quest;
                        super(1);
                    }

                    public final void invoke(Void it) {
                        this.$quest.checkCompleteFuture();
                    }
                }, arg_0));
            }
        }
    }

    @Nullable
    public final Conversation getConversation(@NotNull String id2) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        return conversation.get(id2);
    }

    public final void addConversation(@NotNull String id2, @NotNull Conversation con) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter((Object)con, (String)"con");
        ((Map)conversation).put(id2, con);
    }

    @Nullable
    public final Theme<?> getConversationTheme(@NotNull String id2) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        return conversationTheme.get(id2);
    }

    public final void addConversationTheme(@NotNull String id2, @NotNull Theme<?> theme) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter(theme, (String)"theme");
        ((Map)conversationTheme).put(id2, theme);
    }

    @Nullable
    public final Template getQuestTemplate(@NotNull String id2) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        return questTemplate.get(id2);
    }

    public final void addQuestTemplate(@NotNull String id2, @NotNull Template template) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter((Object)template, (String)"template");
        ((Map)questTemplate).put(id2, template);
    }

    @Nullable
    public final TemplateGroup getQuestTemplateGroup(@NotNull String id2) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        return questTemplateGroup.get(id2);
    }

    public final void addQuestTemplateGroup(@NotNull String id2, @NotNull TemplateGroup templateGroup) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter((Object)templateGroup, (String)"templateGroup");
        ((Map)questTemplateGroup).put(id2, templateGroup);
    }

    @Nullable
    public final Class<? extends Meta<?>> getQuestMeta(@NotNull String id2) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        return questMeta.get(id2);
    }

    public final void addQuestMeta(@NotNull String id2, @NotNull Class<? extends Meta<?>> meta) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter(meta, (String)"meta");
        ((Map)questMeta).put(id2, meta);
    }

    @Nullable
    public final Class<? extends Addon> getQuestAddon(@NotNull String id2) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        return questAddon.get(id2);
    }

    public final void addQuestAddon(@NotNull String id2, @NotNull Class<? extends Addon> addon) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter(addon, (String)"addon");
        ((Map)questAddon).put(id2, addon);
    }

    @Nullable
    public final Objective<?> getQuestObjective(@NotNull String id2) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        return questObjective.get(id2);
    }

    public final void addQuestObjective(@NotNull String id2, @NotNull Objective<?> objective2) {
        Intrinsics.checkNotNullParameter((Object)id2, (String)"id");
        Intrinsics.checkNotNullParameter(objective2, (String)"objective");
        ((Map)questObjective).put(id2, objective2);
    }

    @NotNull
    public final <T extends Module> Module getModule(@NotNull KClass<T> kClass) {
        Intrinsics.checkNotNullParameter(kClass, (String)"kClass");
        Module module = (Module)((Map)Module.Companion.getModules()).get(kClass.getSimpleName());
        if (module == null) {
            throw new IllegalStateException(LocaleKt.t((String)("\n                \u6a21\u5757\u4e0d\u5b58\u5728\uff1a" + kClass + "\n                Module not found: " + kClass + "\n            ")).toString());
        }
        return module;
    }

    @NotNull
    public final List<String> getVariables() {
        return Database.Companion.getINSTANCE().variables();
    }

    @Nullable
    public final String getVariable(@NotNull String key) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        return Database.Companion.getINSTANCE().selectVariable(key);
    }

    public final void setVariable(@NotNull String key, @Nullable String value2, boolean append, @Nullable String string) {
        Intrinsics.checkNotNullParameter((Object)key, (String)"key");
        if (value2 == null) {
            Database.Companion.getINSTANCE().releaseVariable(key);
        } else if (append) {
            Database database = Database.Companion.getINSTANCE();
            String string2 = Database.Companion.getINSTANCE().selectVariable(key);
            if (string2 == null) {
                string2 = string;
            }
            database.updateVariable(key, UtilsForKetherKt.increaseAny(string2, value2).toString());
        } else {
            Database.Companion.getINSTANCE().updateVariable(key, value2);
        }
    }

    public static /* synthetic */ void setVariable$default(ChemdahAPI chemdahAPI, String string, String string2, boolean bl, String string3, int n, Object object) {
        if ((n & 4) != 0) {
            bl = false;
        }
        if ((n & 8) != 0) {
            string3 = null;
        }
        chemdahAPI.setVariable(string, string2, bl, string3);
    }

    public final void reloadAll() {
        Language.INSTANCE.reload();
        Chemdah.INSTANCE.getConf().reload();
        Module.Companion.reload();
        ConversationManager.INSTANCE.getConf().reload();
        ConversationLoader.INSTANCE.loadAll();
        QuestLoader.INSTANCE.loadAll();
        ResettableLazy.Companion.reset(new String[0]);
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public final CompletableFuture<Object> invokeKether(@NotNull String source, @Nullable Player player2, @NotNull Map<String, ? extends Object> vars2) {
        void $this$toTypedArray$iv;
        void $this$mapTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        Intrinsics.checkNotNullParameter(vars2, (String)"vars");
        Object $this$map$iv = vars2;
        boolean $i$f$map = false;
        Map<String, ? extends Object> map = $this$map$iv;
        Collection destination$iv$iv = new ArrayList($this$map$iv.size());
        boolean $i$f$mapTo = false;
        Iterator iterator = $this$mapTo$iv$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            void it;
            Map.Entry item$iv$iv;
            Map.Entry entry = item$iv$iv = iterator.next();
            Collection collection = destination$iv$iv;
            boolean bl = false;
            collection.add(TuplesKt.to(it.getKey(), it.getValue()));
        }
        $this$map$iv = (List)destination$iv$iv;
        boolean $i$f$toTypedArray = false;
        void thisCollection$iv = $this$toTypedArray$iv;
        KetherShell ketherShell = thisCollection$iv.toArray(new Pair[0]);
        Pair[] pairArray = Arrays.copyOf(ketherShell, ((Pair[])ketherShell).length);
        KetherShell.VariableMap map2 = new KetherShell.VariableMap(pairArray);
        ketherShell = KetherShell.INSTANCE;
        Player player3 = player2;
        ProxyPlayer proxyPlayer = player3 != null ? AdapterKt.adaptPlayer((Object)player3) : null;
        List<String> list2 = UtilsForKetherKt.getNamespace();
        return KetherShell.eval$default((KetherShell)ketherShell, (String)source, (boolean)false, list2, null, (ProxyCommandSender)((ProxyCommandSender)proxyPlayer), (KetherShell.VariableMap)map2, null, (int)74, null);
    }

    public static /* synthetic */ CompletableFuture invokeKether$default(ChemdahAPI chemdahAPI, String string, Player player2, Map map, int n, Object object) {
        if ((n & 2) != 0) {
            player2 = null;
        }
        if ((n & 4) != 0) {
            map = MapsKt.emptyMap();
        }
        return chemdahAPI.invokeKether(string, player2, map);
    }

    /*
     * WARNING - void declaration
     */
    @NotNull
    public final String parseFunction(@NotNull String source, @Nullable Player player2, @NotNull Map<String, ? extends Object> vars2) {
        void $this$toTypedArray$iv;
        void $this$mapTo$iv$iv;
        Intrinsics.checkNotNullParameter((Object)source, (String)"source");
        Intrinsics.checkNotNullParameter(vars2, (String)"vars");
        Object $this$map$iv = vars2;
        boolean $i$f$map = false;
        Map<String, ? extends Object> map = $this$map$iv;
        Collection destination$iv$iv = new ArrayList($this$map$iv.size());
        boolean $i$f$mapTo = false;
        Iterator iterator = $this$mapTo$iv$iv.entrySet().iterator();
        while (iterator.hasNext()) {
            void it;
            Map.Entry item$iv$iv;
            Map.Entry entry = item$iv$iv = iterator.next();
            Collection collection = destination$iv$iv;
            boolean bl = false;
            collection.add(TuplesKt.to(it.getKey(), it.getValue()));
        }
        $this$map$iv = (List)destination$iv$iv;
        boolean $i$f$toTypedArray = false;
        void thisCollection$iv = $this$toTypedArray$iv;
        KetherFunction ketherFunction = thisCollection$iv.toArray(new Pair[0]);
        Pair[] pairArray = Arrays.copyOf(ketherFunction, ((Pair[])ketherFunction).length);
        KetherShell.VariableMap map2 = new KetherShell.VariableMap(pairArray);
        ketherFunction = KetherFunction.INSTANCE;
        Player player3 = player2;
        ProxyPlayer proxyPlayer = player3 != null ? AdapterKt.adaptPlayer((Object)player3) : null;
        List<String> list2 = UtilsForKetherKt.getNamespace();
        return KetherFunction.parse$default((KetherFunction)ketherFunction, (String)source, (boolean)false, list2, null, (ProxyCommandSender)((ProxyCommandSender)proxyPlayer), (KetherShell.VariableMap)map2, null, (int)74, null);
    }

    public static /* synthetic */ String parseFunction$default(ChemdahAPI chemdahAPI, String string, Player player2, Map map, int n, Object object) {
        if ((n & 2) != 0) {
            player2 = null;
        }
        if ((n & 4) != 0) {
            map = MapsKt.emptyMap();
        }
        return chemdahAPI.parseFunction(string, player2, map);
    }

    @Parallel(id="chemdah_kether_init", runOn=LifeCycle.ACTIVE)
    private final void workspace() {
        try {
            workspace.loadAll();
        }
        catch (Exception e) {
            Object[] objectArray = new Object[]{LocaleKt.t((String)"\n                    \u811a\u672c\u5728\u52a0\u8f7d\u65f6\u53d1\u751f\u9519\u8bef\n                    An error occurred while loading the script\n                ")};
            IOKt.warning((Object[])objectArray);
            KetherHelperKt.printKetherErrorMessage((Throwable)e, (boolean)true);
        }
        ExecutorKt.submitAsync$default((boolean)false, (long)0L, (long)20L, (Function1)workspace.1.INSTANCE, (int)3, null);
    }

    private static final void callTrigger$lambda$2$lambda$1$lambda$0(Function1 $tmp0, Object p0) {
        Intrinsics.checkNotNullParameter((Object)$tmp0, (String)"$tmp0");
        $tmp0.invoke(p0);
    }
}

