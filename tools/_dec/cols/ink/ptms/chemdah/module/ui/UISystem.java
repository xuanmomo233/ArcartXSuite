/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.ui;

import ink.ptms.chemdah.api.event.collect.PlayerEvents;
import ink.ptms.chemdah.module.Module;
import ink.ptms.chemdah.module.ui.UI;
import ink.ptms.chemdah.module.ui.UISystem;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.platform.event.SubscribeEvent;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.configuration.Config;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import ink.ptms.chemdah.taboolib.module.configuration.util.SectionsKt;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Metadata;
import kotlin1822.jvm.functions.Function1;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Awake
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u00002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u000e\u001a\u0004\u0018\u00010\u000b2\u0006\u0010\u000f\u001a\u00020\nJ\u0010\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0013H\u0003J\b\u0010\u0014\u001a\u00020\u0011H\u0016R \u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0003\u001a\u00020\u00048\u0006@BX\u0087.\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u001d\u0010\b\u001a\u000e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\u000b0\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\r\u00a8\u0006\u0015"}, d2={"Link/ptms/chemdah/module/ui/UISystem;", "Link/ptms/chemdah/module/Module;", "()V", "<set-?>", "Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "conf", "getConf", "()Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "ui", "Ljava/util/concurrent/ConcurrentHashMap;", "", "Link/ptms/chemdah/module/ui/UI;", "getUi", "()Ljava/util/concurrent/ConcurrentHashMap;", "getUI", "name", "onReleased", "", "e", "Link/ptms/chemdah/api/event/collect/PlayerEvents$Released;", "reload", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nUISystem.kt\nKotlin\n*S Kotlin\n*F\n+ 1 UISystem.kt\nink/ptms/chemdah/module/ui/UISystem\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,45:1\n1855#2,2:46\n*S KotlinDebug\n*F\n+ 1 UISystem.kt\nink/ptms/chemdah/module/ui/UISystem\n*L\n37#1:46,2\n*E\n"})
public final class UISystem
implements Module {
    @NotNull
    public static final UISystem INSTANCE = new UISystem();
    @Config(value="module/ui.yml")
    private static Configuration conf;
    @NotNull
    private static final ConcurrentHashMap<String, UI> ui;

    private UISystem() {
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
    public final ConcurrentHashMap<String, UI> getUi() {
        return ui;
    }

    @Nullable
    public final UI getUI(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return ui.get(name);
    }

    @SubscribeEvent
    private final void onReleased(PlayerEvents.Released e) {
        Collection<UI> collection = ui.values();
        Intrinsics.checkNotNullExpressionValue(collection, (String)"ui.values");
        Iterable $this$forEach$iv = collection;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            UI it = (UI)element$iv;
            boolean bl = false;
            it.getPlayerFilters().remove(e.getPlayer().getUniqueId());
        }
    }

    @Override
    public void reload() {
        this.getConf().reload();
        ui.clear();
        ui.putAll(SectionsKt.mapSection((ConfigurationSection)((ConfigurationSection)this.getConf()), (Function1)reload.1.INSTANCE));
    }

    static {
        ui = new ConcurrentHashMap();
        Module.Companion.register(INSTANCE);
    }
}

