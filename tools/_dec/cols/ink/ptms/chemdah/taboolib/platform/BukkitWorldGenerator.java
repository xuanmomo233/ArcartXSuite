/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.taboolib.platform;

import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

public interface BukkitWorldGenerator {
    @Nullable
    public ChunkGenerator getDefaultWorldGenerator(String var1, @Nullable String var2);
}

