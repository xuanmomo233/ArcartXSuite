package xuanmo.arcartxsuite.module;

import java.io.File;
import java.util.Arrays;
import xuanmo.arcartxsuite.api.AXSModule;
import xuanmo.arcartxsuite.api.ModuleDescriptor;

/**
 * 已加载模块的运行时状态。
 */
final class LoadedModule {

    private final ModuleDescriptor descriptor;
    private final AXSModule instance;
    private final ClassLoader classLoader;
    private final File jarFile;
    private final byte[] jarBytes;
    private final byte[] moduleSeed;
    private DefaultModuleContext context;
    private boolean enabled;

    LoadedModule(ModuleDescriptor descriptor, AXSModule instance, ClassLoader classLoader, File jarFile) {
        this.descriptor = descriptor;
        this.instance = instance;
        this.classLoader = classLoader;
        this.jarFile = jarFile;
        this.jarBytes = null;
        this.moduleSeed = null;
        this.enabled = false;
    }

    LoadedModule(ModuleDescriptor descriptor, AXSModule instance, ClassLoader classLoader, byte[] jarBytes, byte[] moduleSeed) {
        this.descriptor = descriptor;
        this.instance = instance;
        this.classLoader = classLoader;
        this.jarFile = null;
        this.jarBytes = jarBytes == null ? null : jarBytes.clone();
        this.moduleSeed = moduleSeed == null ? null : moduleSeed.clone();
        this.enabled = false;
    }

    void setContext(DefaultModuleContext context) {
        this.context = context;
    }

    DefaultModuleContext context() {
        return context;
    }

    ModuleDescriptor descriptor() {
        return descriptor;
    }

    AXSModule instance() {
        return instance;
    }

    ClassLoader classLoader() {
        return classLoader;
    }

    File jarFile() {
        return jarFile;
    }

    byte[] jarBytes() {
        return jarBytes == null ? null : jarBytes.clone();
    }

    byte[] moduleSeed() {
        return moduleSeed == null ? null : moduleSeed.clone();
    }

    boolean isEnabled() {
        return enabled;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    void clearSensitiveMaterial() {
        wipe(jarBytes);
        wipe(moduleSeed);
    }
    private static void wipe(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }
}
