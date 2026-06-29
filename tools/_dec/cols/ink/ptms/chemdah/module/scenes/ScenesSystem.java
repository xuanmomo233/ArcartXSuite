/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.module.scenes;

import ink.ptms.chemdah.module.Module;
import ink.ptms.chemdah.module.scenes.ScenesFile;
import ink.ptms.chemdah.taboolib.common.platform.Awake;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import ink.ptms.chemdah.taboolib.module.configuration.Config;
import ink.ptms.chemdah.taboolib.module.configuration.Configuration;
import java.util.HashMap;
import java.util.Map;
import kotlin.Metadata;
import kotlin1822.jvm.internal.Intrinsics;
import kotlin1822.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

@Awake
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u000f\u001a\u00020\u0010H\u0016R \u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0003\u001a\u00020\u00048\u0006@BX\u0087.\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R-\u0010\b\u001a\u001e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\u000b0\tj\u000e\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\u000b`\f\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u0011"}, d2={"Link/ptms/chemdah/module/scenes/ScenesSystem;", "Link/ptms/chemdah/module/Module;", "()V", "<set-?>", "Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "conf", "getConf", "()Link/ptms/chemdah/taboolib/module/configuration/Configuration;", "scenesMap", "Ljava/util/HashMap;", "", "Link/ptms/chemdah/module/scenes/ScenesFile;", "Lkotlin1822/collections/HashMap;", "getScenesMap", "()Ljava/util/HashMap;", "reload", "", "Chemdah"})
@SourceDebugExtension(value={"SMAP\nScenesSystem.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ScenesSystem.kt\nink/ptms/chemdah/module/scenes/ScenesSystem\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,29:1\n1855#2,2:30\n*S KotlinDebug\n*F\n+ 1 ScenesSystem.kt\nink/ptms/chemdah/module/scenes/ScenesSystem\n*L\n25#1:30,2\n*E\n"})
public final class ScenesSystem
implements Module {
    @NotNull
    public static final ScenesSystem INSTANCE = new ScenesSystem();
    @Config(value="module/scenes.yml")
    private static Configuration conf;
    @NotNull
    private static final HashMap<String, ScenesFile> scenesMap;

    private ScenesSystem() {
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
    public final HashMap<String, ScenesFile> getScenesMap() {
        return scenesMap;
    }

    @Override
    public void reload() {
        scenesMap.clear();
        this.getConf().reload();
        Iterable $this$forEach$iv = this.getConf().getKeys(false);
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            String it = (String)element$iv;
            boolean bl = false;
            Map map = scenesMap;
            ConfigurationSection configurationSection = INSTANCE.getConf().getConfigurationSection(it);
            Intrinsics.checkNotNull((Object)configurationSection);
            map.put(it, new ScenesFile(configurationSection));
        }
    }

    static {
        scenesMap = new HashMap();
        Module.Companion.register(INSTANCE);
    }
}

