/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.taboolib.platform;

import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.PrimitiveIO;
import ink.ptms.chemdah.taboolib.common.TabooLib;
import ink.ptms.chemdah.taboolib.common.classloader.IsolatedClassLoader;
import ink.ptms.chemdah.taboolib.common.platform.Platform;
import ink.ptms.chemdah.taboolib.common.platform.PlatformSide;
import ink.ptms.chemdah.taboolib.common.platform.Plugin;
import ink.ptms.chemdah.taboolib.platform.BukkitBiomeProvider;
import ink.ptms.chemdah.taboolib.platform.BukkitWorldGenerator;
import ink.ptms.chemdah.taboolib.platform.Folia;
import ink.ptms.chemdah.taboolib.platform.FoliaExecutor;
import ink.ptms.chemdah.taboolib.platform.IllegalAccess;
import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@PlatformSide(value={Platform.BUKKIT})
public class BukkitPlugin
extends JavaPlugin {
    @Nullable
    private static Plugin pluginInstance;
    private static BukkitPlugin instance;

    public BukkitPlugin() {
        instance = this;
        IllegalAccess.inject();
        TabooLib.lifeCycle(LifeCycle.INIT);
    }

    public void onLoad() {
        TabooLib.lifeCycle(LifeCycle.LOAD);
        if (pluginInstance != null && !TabooLib.isStopped()) {
            pluginInstance.onLoad();
        }
    }

    public void onEnable() {
        TabooLib.lifeCycle(LifeCycle.ENABLE);
        if (!TabooLib.isStopped() && pluginInstance != null) {
            pluginInstance.onEnable();
        }
        if (!TabooLib.isStopped()) {
            if (Folia.isFolia) {
                FoliaExecutor.ASYNC_SCHEDULER.runNow((org.bukkit.plugin.Plugin)this, task -> this.invokeActive());
            } else {
                Bukkit.getScheduler().runTask((org.bukkit.plugin.Plugin)this, this::invokeActive);
            }
        }
    }

    public void onDisable() {
        if (pluginInstance != null && !TabooLib.isStopped()) {
            pluginInstance.onDisable();
        }
        TabooLib.lifeCycle(LifeCycle.DISABLE);
    }

    public ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id2) {
        if (pluginInstance instanceof BukkitWorldGenerator) {
            return ((BukkitWorldGenerator)((Object)pluginInstance)).getDefaultWorldGenerator(worldName, id2);
        }
        return null;
    }

    @Nullable
    public BiomeProvider getDefaultBiomeProvider(@NotNull String worldName, @Nullable String id2) {
        if (pluginInstance instanceof BukkitBiomeProvider) {
            return ((BukkitBiomeProvider)((Object)pluginInstance)).getDefaultBiomeProvider(worldName, id2);
        }
        return null;
    }

    @NotNull
    public File getFile() {
        return super.getFile();
    }

    @Nullable
    public static Plugin getPluginInstance() {
        return pluginInstance;
    }

    @NotNull
    public static BukkitPlugin getInstance() {
        return instance;
    }

    private void invokeActive() {
        TabooLib.lifeCycle(LifeCycle.ACTIVE);
        if (pluginInstance != null) {
            pluginInstance.onActive();
        }
    }

    static {
        PrimitiveIO.debug("Initialization completed. ({0}ms)", TabooLib.execution(() -> {
            try {
                IsolatedClassLoader.init(BukkitPlugin.class);
                IsolatedClassLoader.INSTANCE.addExcludedClass("ink.ptms.chemdah.taboolib.platform.BukkitWorldGenerator");
                IsolatedClassLoader.INSTANCE.addExcludedClass("ink.ptms.chemdah.taboolib.platform.BukkitBiomeProvider");
            }
            catch (Throwable ex) {
                TabooLib.setStopped(true);
                PrimitiveIO.error(PrimitiveIO.t("\u65e0\u6cd5\u521d\u59cb\u5316\u539f\u59cb\u52a0\u8f7d\u5668\uff0c\u63d2\u4ef6 \"{0}\" \u5c06\u88ab\u7981\u7528\uff01", "Failed to initialize primitive loader, the plugin \"{0}\" will be disabled!"), PrimitiveIO.getRunningFileName());
                throw ex;
            }
            TabooLib.lifeCycle(LifeCycle.CONST);
            pluginInstance = Plugin.getInstance();
        }));
    }
}

