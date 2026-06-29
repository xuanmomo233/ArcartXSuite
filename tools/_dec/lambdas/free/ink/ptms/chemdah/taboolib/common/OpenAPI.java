/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.taboolib.common;

import ink.ptms.chemdah.taboolib.common.OpenListener;
import ink.ptms.chemdah.taboolib.common.OpenResult;
import ink.ptms.chemdah.taboolib.common.TabooLib;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class OpenAPI {
    @NotNull
    public static OpenResult call(String name, Object[] data2) {
        for (Map.Entry<String, Object> entry : TabooLib.getAwakenedClasses().entrySet()) {
            OpenResult result;
            if (!(entry.getValue() instanceof OpenListener) || !(result = ((OpenListener)entry.getValue()).call(name, data2)).isSuccessful()) continue;
            return result;
        }
        return OpenResult.failed();
    }
}

