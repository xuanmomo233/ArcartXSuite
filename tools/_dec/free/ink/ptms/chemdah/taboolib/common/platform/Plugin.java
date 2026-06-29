/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.taboolib.common.platform;

import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Plugin {
    private static Plugin instance = null;

    public void onLoad() {
    }

    public void onEnable() {
    }

    public void onActive() {
    }

    public void onDisable() {
    }

    @Nullable
    public File nativeJarFile() {
        return null;
    }

    @Nullable
    public File nativeDataFolder() {
        return null;
    }

    @Nullable
    public static Plugin getInstance() {
        return instance;
    }

    public static void setInstance(@NotNull Plugin instance) {
        if (Plugin.instance != null) {
            throw new IllegalStateException("Plugin instance already set.");
        }
        Plugin.instance = instance;
    }
}

