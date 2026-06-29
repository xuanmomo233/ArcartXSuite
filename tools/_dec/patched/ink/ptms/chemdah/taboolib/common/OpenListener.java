/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.taboolib.common;

import ink.ptms.chemdah.taboolib.common.OpenResult;
import org.jetbrains.annotations.NotNull;

public interface OpenListener {
    public OpenResult call(@NotNull String var1, Object[] var2);
}

