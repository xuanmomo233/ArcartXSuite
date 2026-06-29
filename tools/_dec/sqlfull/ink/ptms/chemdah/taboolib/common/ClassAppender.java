/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.taboolib.common;

import ink.ptms.chemdah.taboolib.common.PrimitiveIO;
import ink.ptms.chemdah.taboolib.common.PrimitiveSettings;
import ink.ptms.chemdah.taboolib.common.TabooLib;
import ink.ptms.chemdah.taboolib.common.classloader.IsolatedClassLoader;
import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import sun.misc.Unsafe;

public class ClassAppender {
    static MethodHandles.Lookup lookup;
    static Unsafe unsafe;
    static List<Callback> callbacks;

    public static ClassLoader addPath(Path path, boolean isIsolated, boolean isExternal) throws Throwable {
        File file = new File(path.toUri().getPath());
        if (isIsolated) {
            IsolatedClassLoader loader = IsolatedClassLoader.INSTANCE;
            loader.addURL(file.toURI().toURL());
            for (Callback i : callbacks) {
                i.add(loader, file, isExternal);
            }
            return loader;
        }
        ClassLoader loader = TabooLib.class.getClassLoader();
        if (loader.getClass().getSimpleName().equals("AppClassLoader")) {
            ClassAppender.addURL(loader, ClassAppender.ucp(loader.getClass()), file, isExternal);
        } else if (loader.getClass().getName().equals("net.minecraft.launchwrapper.LaunchClassLoader")) {
            MethodHandle methodHandle = lookup.findVirtual(URLClassLoader.class, "addURL", MethodType.methodType(Void.TYPE, URL.class));
            methodHandle.invoke(loader, file.toURI().toURL());
        } else {
            ClassAppender.addURL(loader, ClassAppender.ucp(loader), file, isExternal);
        }
        return loader;
    }

    public static ClassLoader getClassLoader() {
        return PrimitiveSettings.IS_ISOLATED_MODE ? IsolatedClassLoader.INSTANCE : TabooLib.class.getClassLoader();
    }

    public static boolean isExists(String path) {
        try {
            Class.forName(path, false, ClassAppender.getClassLoader());
            return true;
        }
        catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static void addURL(ClassLoader loader, Field ucpField, File file, boolean isExternal) throws Throwable {
        if (ucpField == null) {
            throw new IllegalStateException("ucp field not found");
        }
        if (lookup == null) {
            throw new IllegalStateException("lookup not found");
        }
        Object ucp = unsafe.getObject(loader, unsafe.objectFieldOffset(ucpField));
        try {
            MethodHandle methodHandle = lookup.findVirtual(ucp.getClass(), "addURL", MethodType.methodType(Void.TYPE, URL.class));
            methodHandle.invoke(ucp, file.toURI().toURL());
            for (Callback i : callbacks) {
                i.add(loader, file, isExternal);
            }
        }
        catch (NoSuchMethodError e) {
            throw new IllegalStateException("Unsupported (classloader: " + loader.getClass().getName() + ", ucp: " + ucp.getClass().getName() + ")", e);
        }
    }

    private static Field ucp(ClassLoader loader) {
        try {
            return URLClassLoader.class.getDeclaredField("ucp");
        }
        catch (NoSuchFieldError | NoSuchFieldException ignored) {
            return ClassAppender.ucp(loader.getClass());
        }
    }

    private static Field ucp(Class<?> loader) {
        try {
            return loader.getDeclaredField("ucp");
        }
        catch (NoSuchFieldError | NoSuchFieldException e2) {
            Class<?> superclass = loader.getSuperclass();
            if (superclass == Object.class) {
                return null;
            }
            return ClassAppender.ucp(superclass);
        }
    }

    public static void registerCallback(Callback callback) {
        callbacks.add(callback);
    }

    static {
        callbacks = new ArrayList<Callback>();
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe)field.get(null);
            Field lookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            Object lookupBase = unsafe.staticFieldBase(lookupField);
            long lookupOffset = unsafe.staticFieldOffset(lookupField);
            lookup = (MethodHandles.Lookup)unsafe.getObject(lookupBase, lookupOffset);
            if (lookup == null) {
                PrimitiveIO.warning(PrimitiveIO.t("\u672a\u80fd\u627e\u5230 Unsafe lookup\uff0cTabooLib \u5c06\u65e0\u6cd5\u6b63\u5e38\u5de5\u4f5c\u3002", "Unsafe lookup not found, TabooLib will not work properly."), new Object[0]);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public static interface Callback {
        public void add(ClassLoader var1, File var2, boolean var3);
    }
}

