/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.taboolib.common.classloader;

import ink.ptms.chemdah.taboolib.common.classloader.IsolatedClassLoaderConfig;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;

public class IsolatedClassLoader
extends URLClassLoader {
    public static IsolatedClassLoader INSTANCE;
    private final Set<String> excludedClasses = new HashSet<String>();
    private final Set<String> excludedPackages = new HashSet<String>();

    public static void init(Class<?> clazz) {
        INSTANCE = new IsolatedClassLoader(clazz);
        try {
            Class<?> delegateClass = Class.forName("ink.ptms.chemdah.taboolib.common.PrimitiveLoader", false, INSTANCE);
            Object delegateObject = delegateClass.getConstructor(new Class[0]).newInstance(new Object[0]);
            delegateClass.getMethod("init", new Class[0]).invoke(delegateObject, new Object[0]);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public IsolatedClassLoader(Class<?> clazz) {
        this(new URL[]{clazz.getProtectionDomain().getCodeSource().getLocation()}, clazz.getClassLoader());
        this.excludedClasses.add(clazz.getName());
    }

    public IsolatedClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.excludedPackages.add("java.");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.classloader.IsolatedClassLoader");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.TabooLib");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.TabooLib$1");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.TabooLib$2");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.TabooLib$ClassFinder");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.ClassAppender");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.ClassAppender$Callback");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.platform.Plugin");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.OpenAPI");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.OpenListener");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.OpenResult");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.LifeCycle");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.LifeCycleTask");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.PrimitiveIO");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.PrimitiveSettings");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.platform.Platform");
        this.excludedClasses.add("ink.ptms.chemdah.taboolib.common.platform.PlatformSide");
        ServiceLoader<IsolatedClassLoaderConfig> serviceLoader = ServiceLoader.load(IsolatedClassLoaderConfig.class, parent);
        for (IsolatedClassLoaderConfig config : serviceLoader) {
            Set<String> configExcludedPackages;
            Set<String> configExcludedClasses = config.excludedClasses();
            if (configExcludedClasses != null && !configExcludedClasses.isEmpty()) {
                this.excludedClasses.addAll(configExcludedClasses);
            }
            if ((configExcludedPackages = config.excludedPackages()) == null || configExcludedPackages.isEmpty()) continue;
            this.excludedPackages.addAll(configExcludedPackages);
        }
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        return this.loadClass(name, resolve, true);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Class<?> loadClass(String name, boolean resolve, boolean checkParents) throws ClassNotFoundException {
        Object object = this.getClassLoadingLock(name);
        synchronized (object) {
            Class<?> findClass = this.findLoadedClass(name);
            if (findClass == null && !this.excludedClasses.contains(name)) {
                boolean flag = true;
                for (String excludedPackage : this.excludedPackages) {
                    if (!name.startsWith(excludedPackage)) continue;
                    flag = false;
                    break;
                }
                if (flag) {
                    findClass = this.findClassOrNull(name);
                }
            }
            if (findClass == null && checkParents) {
                findClass = this.loadClassFromParentOrNull(name);
            }
            if (findClass == null) {
                throw new ClassNotFoundException(name);
            }
            if (resolve) {
                this.resolveClass(findClass);
            }
            return findClass;
        }
    }

    private Class<?> findClassOrNull(String name) {
        try {
            return this.findClass(name);
        }
        catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private Class<?> loadClassFromParentOrNull(String name) {
        try {
            return this.getParent().loadClass(name);
        }
        catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    public void addExcludedClass(String name) {
        this.excludedClasses.add(name);
    }

    public void addExcludedClasses(Collection<String> names) {
        this.excludedClasses.addAll(names);
    }

    public void addExcludedPackage(String name) {
        this.excludedPackages.add(name);
    }

    public void addExcludedPackages(Collection<String> names) {
        this.excludedPackages.addAll(names);
    }
}

