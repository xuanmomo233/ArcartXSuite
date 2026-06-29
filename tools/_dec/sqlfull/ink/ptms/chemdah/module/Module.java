/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module;

import ink.ptms.chemdah.api.event.collect.PluginReloadEvent;
import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\bf\u0018\u0000 \u00042\u00020\u0001:\u0001\u0004J\b\u0010\u0002\u001a\u00020\u0003H\u0016\u00f8\u0001\u0000\u0082\u0002\u0006\n\u0004\b!0\u0001\u00a8\u0006\u0005\u00c0\u0006\u0001"}, d2={"Link/ptms/chemdah/module/Module;", "", "reload", "", "Companion", "Chemdah"})
public interface Module {
    @NotNull
    public static final Companion Companion = ink.ptms.chemdah.module.Module$Companion.$$INSTANCE;

    default public void reload() {
    }

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\n\u001a\u00020\u000bH\u0007J\n\u0010\f\u001a\u00020\u000b*\u00020\u0006R-\u0010\u0003\u001a\u001e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00060\u0004j\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u0006`\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\r"}, d2={"Link/ptms/chemdah/module/Module$Companion;", "", "()V", "modules", "Ljava/util/HashMap;", "", "Link/ptms/chemdah/module/Module;", "Lkotlin1822/collections/HashMap;", "getModules", "()Ljava/util/HashMap;", "reload", "", "register", "Chemdah"})
    @SourceDebugExtension(value={"SMAP\nModule.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Module.kt\nink/ptms/chemdah/module/Module$Companion\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,41:1\n1855#2,2:42\n*S KotlinDebug\n*F\n+ 1 Module.kt\nink/ptms/chemdah/module/Module$Companion\n*L\n32#1:42,2\n*E\n"})
    public static final class Companion {
        static final /* synthetic */ Companion $$INSTANCE;
        @NotNull
        private static final HashMap<String, Module> modules;

        private Companion() {
        }

        @NotNull
        public final HashMap<String, Module> getModules() {
            return modules;
        }

        public final void register(@NotNull Module $this$register) {
            Intrinsics.checkNotNullParameter((Object)$this$register, (String)"<this>");
            Map map = modules;
            String string = $this$register.getClass().getSimpleName();
            Intrinsics.checkNotNullExpressionValue((Object)string, (String)"javaClass.simpleName");
            map.put(string, $this$register);
        }

        @Awake(value=LifeCycle.ENABLE)
        public final void reload() {
            try {
                Collection<Module> collection = modules.values();
                Intrinsics.checkNotNullExpressionValue(collection, (String)"modules.values");
                Iterable $this$forEach$iv = collection;
                boolean $i$f$forEach = false;
                for (Object element$iv : $this$forEach$iv) {
                    Module it = (Module)element$iv;
                    boolean bl = false;
                    it.reload();
                }
                new PluginReloadEvent.Module().call();
            }
            catch (Throwable ex) {
                ex.printStackTrace();
                Object[] objectArray = new Object[]{"Failed to reload module, server will be shutdown."};
                IOKt.warning((Object[])objectArray);
                Bukkit.shutdown();
            }
        }

        static {
            $$INSTANCE = new Companion();
            modules = new HashMap();
        }
    }
}

