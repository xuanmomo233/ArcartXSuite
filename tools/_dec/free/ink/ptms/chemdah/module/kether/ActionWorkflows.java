/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender
 *  ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer
 *  ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent
 *  ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt
 *  ink.ptms.chemdah.taboolib.common.util.LazyMakerKt
 *  ink.ptms.chemdah.taboolib.module.configuration.Configuration
 *  ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt
 *  ink.ptms.chemdah.taboolib.module.kether.KetherParser
 *  ink.ptms.chemdah.taboolib.module.kether.KetherShell
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptContext
 *  ink.ptms.chemdah.taboolib.module.kether.ScriptService
 *  kotlin.Metadata
 *  kotlin1822.Lazy
 *  kotlin1822.jvm.functions.Function0
 *  kotlin1822.jvm.functions.Function1
 *  kotlin1822.jvm.internal.Intrinsics
 *  kotlin1822.jvm.internal.SourceDebugExtension
 *  kotlin1822.text.StringsKt
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.module.kether;

import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.module.kether.ActionWorkflows;
import ink.ptms.chemdah.taboolib.common.platform.ProxyCommandSender;
import ink.ptms.chemdah.taboolib.common.platform.ProxyPlayer;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.common.platform.function.AdapterKt;
import ink.ptms.chemdah.taboolib.common.util.LazyMakerKt;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.module.kether.KetherHelperKt;
import ink.ptms.chemdah.taboolib.module.kether.KetherParser;
import ink.ptms.chemdah.taboolib.module.kether.KetherShell;
import ink.ptms.chemdah.taboolib.module.kether.ScriptActionParser;
import ink.ptms.chemdah.taboolib.module.kether.ScriptContext;
import ink.ptms.chemdah.taboolib.module.kether.ScriptService;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Metadata;
import kotlin1822.Lazy;
import kotlin1822.jvm.functions.Function0;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import kotlin1822.text.StringsKt;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000@\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0010\u001a\f\u0012\b\b\u0001\u0012\u0004\u0018\u00010\u00010\u0011H\u0007J\u0010\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u0015H\u0003J\u0010\u0010\u0016\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u0017H\u0003R\u001b\u0010\u0003\u001a\u00020\u00048FX\u0086\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0007\u0010\b\u001a\u0004\b\u0005\u0010\u0006R#\u0010\t\u001a\u0014\u0012\u0004\u0012\u00020\u000b\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\f0\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0018"}, d2={"Link/ptms/chemdah/module/kether/ActionWorkflows;", "", "()V", "data", "Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "getData", "()Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "data$delegate", "Lkotlin1822/Lazy;", "fetchMap", "Ljava/util/concurrent/ConcurrentHashMap;", "", "", "Link/ptms/chemdah/taboolib/module/kether/ScriptContext;", "getFetchMap", "()Ljava/util/concurrent/ConcurrentHashMap;", "fetch", "Link/ptms/chemdah/taboolib/module/kether/ScriptActionParser;", "onReleased", "", "e", "Link/ptms/chemdah/api/event/collect/PlayerEvents$Released;", "onSelected", "Link/ptms/chemdah/api/event/collect/PlayerEvents$Selected;", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nActionWorkflows.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ActionWorkflows.kt\nink/ptms/chemdah/module/kether/ActionWorkflows\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,66:1\n1855#2,2:67\n*S KotlinDebug\n*F\n+ 1 ActionWorkflows.kt\nink/ptms/chemdah/module/kether/ActionWorkflows\n*L\n36#1:67,2\n*E\n"})
public final class ActionWorkflows {
    @NotNull
    public static final ActionWorkflows INSTANCE = new ActionWorkflows();
    @NotNull
    private static final Lazy data$delegate = LazyMakerKt.unsafeLazy((Function0)data.2.INSTANCE);
    @NotNull
    private static final ConcurrentHashMap<String, List<ScriptContext>> fetchMap = new ConcurrentHashMap();

    private ActionWorkflows() {
    }

    @NotNull
    public final Configuration getData() {
        Lazy lazy = data$delegate;
        return (Configuration)lazy.getValue();
    }

    @NotNull
    public final ConcurrentHashMap<String, List<ScriptContext>> getFetchMap() {
        return fetchMap;
    }

    @SubscribeEvent
    private final void onSelected(PlayerEvents.Selected e) {
        e.getPlayerProfile().getPersistentDataContainer().forEach(arg_0 -> ActionWorkflows.onSelected$lambda$0(e, arg_0));
    }

    @SubscribeEvent
    private final void onReleased(PlayerEvents.Released e) {
        block1: {
            List<ScriptContext> list2 = fetchMap.remove(e.getPlayer().getName());
            if (list2 == null) break block1;
            Iterable $this$forEach$iv = list2;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                ScriptContext it = (ScriptContext)element$iv;
                boolean bl = false;
                ScriptService.INSTANCE.terminateQuest(it);
            }
        }
    }

    @KetherParser(value={"fetch"})
    @NotNull
    public final ScriptActionParser<? extends Object> fetch() {
        return KetherHelperKt.scriptParser((Function1)fetch.1.INSTANCE);
    }

    private static final void onSelected$lambda$0(PlayerEvents.Selected $e, Map.Entry entry) {
        Intrinsics.checkNotNullParameter((Object)((Object)$e), (String)"$e");
        Intrinsics.checkNotNullParameter((Object)entry, (String)"<name for destructuring parameter 0>");
        String k = (String)entry.getKey();
        if (StringsKt.startsWith$default((String)k, (String)"workflows.fetch.", (boolean)false, (int)2, null)) {
            $e.getPlayerProfile().getPersistentDataContainer().remove(k);
            String string = k.substring(16);
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"this as java.lang.String).substring(startIndex)");
            String id2 = string;
            String string2 = INSTANCE.getData().getString(id2);
            if (string2 == null) {
                return;
            }
            String fetch2 = string2;
            KetherShell ketherShell = KetherShell.INSTANCE;
            ProxyPlayer proxyPlayer = AdapterKt.adaptPlayer((Object)$e.getPlayer());
            List<String> list2 = UtilsForKetherKt.getNamespace();
            KetherShell.eval$default((KetherShell)ketherShell, (String)fetch2, (boolean)false, list2, null, (ProxyCommandSender)((ProxyCommandSender)proxyPlayer), null, null, (int)106, null);
        }
    }
}

