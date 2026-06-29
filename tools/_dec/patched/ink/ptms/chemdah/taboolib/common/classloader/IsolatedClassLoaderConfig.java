/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.taboolib.common.classloader;

import java.util.Collections;
import java.util.Set;

public interface IsolatedClassLoaderConfig {
    default public Set<String> excludedClasses() {
        return Collections.emptySet();
    }

    default public Set<String> excludedPackages() {
        return Collections.emptySet();
    }
}

