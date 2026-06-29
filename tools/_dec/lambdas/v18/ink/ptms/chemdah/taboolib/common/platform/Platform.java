/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.taboolib.common.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum Platform {
    BUKKIT("Bukkit", "org.bukkit.Bukkit"),
    BUNGEE("Bungee", "net.md_5.bungee.BungeeCord"),
    VELOCITY("Velocity", "com.velocitypowered.api.plugin.Plugin"),
    AFYBROKER("AfyBroker", "net.afyer.afybroker.server.Broker"),
    APPLICATION("Application", null);

    public static final Platform CURRENT;
    @NotNull
    final String key;
    @Nullable
    final String checkClass;

    private Platform(String key, String checkClass) {
        this.key = key;
        this.checkClass = checkClass;
    }

    @NotNull
    public String key() {
        return this.key;
    }

    @Nullable
    public String checkClass() {
        return this.checkClass;
    }

    public static Platform[] minecraft() {
        return new Platform[]{BUKKIT, BUNGEE, VELOCITY, AFYBROKER};
    }

    private static Platform current() {
        for (Platform platform : Platform.minecraft()) {
            try {
                Class.forName(platform.checkClass());
                return platform;
            }
            catch (ClassNotFoundException classNotFoundException) {
            }
        }
        return APPLICATION;
    }

    static {
        CURRENT = Platform.current();
    }
}

