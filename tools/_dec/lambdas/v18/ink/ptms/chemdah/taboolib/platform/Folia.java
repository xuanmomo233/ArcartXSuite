/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.taboolib.platform;

public class Folia {
    public static boolean isFolia = false;

    static {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }
}

