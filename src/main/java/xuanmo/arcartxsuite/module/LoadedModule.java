package xuanmo.arcartxsuite.module;

import java.io.File;
import xuanmo.arcartxsuite.api.AXSModule;
import xuanmo.arcartxsuite.api.ModuleDescriptor;

/**
 * 已加载模块的运行时状态。
 */
final class LoadedModule {

    private final ModuleDescriptor descriptor;
    private final AXSModule instance;
    private final ModuleClassLoader classLoader;
    private final File jarFile;
    private boolean enabled;

    LoadedModule(ModuleDescriptor descriptor, AXSModule instance, ModuleClassLoader classLoader, File jarFile) {
        this.descriptor = descriptor;
        this.instance = instance;
        this.classLoader = classLoader;
        this.jarFile = jarFile;
        this.enabled = false;
    }

    ModuleDescriptor descriptor() {
        return descriptor;
    }

    AXSModule instance() {
        return instance;
    }

    ModuleClassLoader classLoader() {
        return classLoader;
    }

    File jarFile() {
        return jarFile;
    }

    boolean isEnabled() {
        return enabled;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
