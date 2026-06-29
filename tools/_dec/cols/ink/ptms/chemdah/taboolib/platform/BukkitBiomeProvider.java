/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.taboolib.platform;

import org.bukkit.generator.BiomeProvider;
import org.jetbrains.annotations.Nullable;

public interface BukkitBiomeProvider {
    @Nullable
    public BiomeProvider getDefaultBiomeProvider(String var1, @Nullable String var2);
}

