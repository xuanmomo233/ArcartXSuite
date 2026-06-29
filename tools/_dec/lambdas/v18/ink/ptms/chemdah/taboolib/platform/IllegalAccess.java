/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.library.reflex.UnsafeAccess
 *  org.bukkit.Bukkit
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.PluginDescriptionFile
 */
package ink.ptms.chemdah.taboolib.platform;

import ink.ptms.chemdah.taboolib.common.PrimitiveIO;
import ink.ptms.chemdah.taboolib.common.TabooLib;
import ink.ptms.chemdah.taboolib.library.reflex.UnsafeAccess;
import ink.ptms.chemdah.taboolib.platform.BukkitPlugin;
import java.lang.reflect.Field;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

public class IllegalAccess {
    public static void inject() {
        PrimitiveIO.debug("Injected illegal access warning. ({0}ms)", TabooLib.execution(() -> {
            try {
                ClassLoader classLoader = BukkitPlugin.class.getClassLoader();
                Field descriptionField = classLoader.getClass().getDeclaredField("description");
                PluginDescriptionFile description = (PluginDescriptionFile)UnsafeAccess.INSTANCE.get((Object)classLoader, descriptionField);
                Field seenIllegalAccessField = classLoader.getClass().getDeclaredField("seenIllegalAccess");
                Set accessSelf = (Set)UnsafeAccess.INSTANCE.get((Object)classLoader, seenIllegalAccessField);
                for (Plugin plugin2 : Bukkit.getPluginManager().getPlugins()) {
                    if (!plugin2.getClass().getName().endsWith("platform.BukkitPlugin")) continue;
                    Set accessOther = (Set)UnsafeAccess.INSTANCE.get((Object)plugin2.getClass().getClassLoader(), seenIllegalAccessField);
                    accessOther.add(description.getName());
                    accessSelf.add(plugin2.getName());
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }));
    }
}

