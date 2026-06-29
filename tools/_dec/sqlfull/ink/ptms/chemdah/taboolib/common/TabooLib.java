/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.taboolib.common;

import ink.ptms.chemdah.taboolib.common.ClassAppender;
import ink.ptms.chemdah.taboolib.common.LifeCycle;
import ink.ptms.chemdah.taboolib.common.LifeCycleTask;
import ink.ptms.chemdah.taboolib.common.PrimitiveIO;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;

public class TabooLib {
    private static boolean isStopped = false;
    private static LifeCycle currentLifeCycle = LifeCycle.NONE;
    private static final ConcurrentHashMap<String, Object> awakenedClasses = new ConcurrentHashMap();
    private static final ConcurrentHashMap<LifeCycle, List<LifeCycleTask>> lifeCycleTask = new ConcurrentHashMap();
    private static ClassFinder classFinder = new ClassFinder(){

        @Override
        public Class<?> getClass(String name) throws ClassNotFoundException {
            return Class.forName(name);
        }

        @Override
        public Class<?> getClass(String name, boolean initialize) throws ClassNotFoundException {
            return Class.forName(name, initialize, TabooLib.class.getClassLoader());
        }

        @Override
        public Class<?> getClass(String name, boolean initialize, ClassLoader classLoader) throws ClassNotFoundException {
            return Class.forName(name, initialize, classLoader);
        }
    };

    public static void lifeCycle(LifeCycle lifeCycle) {
        if (isStopped) {
            return;
        }
        if (!TabooLib.isKotlinEnvironment()) {
            isStopped = true;
            throw new RuntimeException("Runtime environment setup failed, please feedback! (Kotlin Environment Not Found)");
        }
        long time = TabooLib.execution(() -> {
            currentLifeCycle = lifeCycle;
            List<LifeCycleTask> taskList = lifeCycleTask.remove((Object)lifeCycle);
            if (taskList != null) {
                for (LifeCycleTask task : taskList) {
                    task.run();
                }
            }
        });
        PrimitiveIO.debug("LifeCycle \"{0}\" completed in {1} ms.", new Object[]{lifeCycle, time});
    }

    public static void registerLifeCycleTask(LifeCycle lifeCycle, final int priority, final Runnable runnable) {
        if (currentLifeCycle.ordinal() >= lifeCycle.ordinal()) {
            runnable.run();
        } else {
            List<Object> tasks;
            if (lifeCycleTask.containsKey((Object)lifeCycle)) {
                tasks = lifeCycleTask.get((Object)lifeCycle);
            } else {
                tasks = new CopyOnWriteArrayList();
                lifeCycleTask.put(lifeCycle, tasks);
            }
            tasks.add(new LifeCycleTask(){

                @Override
                public int priority() {
                    return priority;
                }

                @Override
                public void run() {
                    runnable.run();
                }
            });
            tasks.sort(Comparator.comparingInt(LifeCycleTask::priority));
        }
    }

    public static boolean isKotlinEnvironment() {
        try {
            Class.forName("kotlin1822.Lazy", false, ClassAppender.getClassLoader());
            return true;
        }
        catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static boolean isKotlinCoroutinesEnvironment() {
        try {
            Class.forName("kotlin1822x.coroutines173.CoroutineScope", false, ClassAppender.getClassLoader());
            return true;
        }
        catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    @NotNull
    public static LifeCycle getCurrentLifeCycle() {
        return currentLifeCycle;
    }

    public static boolean isStopped() {
        return isStopped;
    }

    public static void setStopped(boolean value2) {
        isStopped = value2;
    }

    public static Map<String, Object> getAwakenedClasses() {
        return awakenedClasses;
    }

    public static Class<?> getClass(String name) throws ClassNotFoundException {
        return classFinder.getClass(name);
    }

    public static Class<?> getClass(String name, boolean initialize) throws ClassNotFoundException {
        return classFinder.getClass(name, initialize);
    }

    public static Class<?> getClass(String name, boolean initialize, ClassLoader classLoader) throws ClassNotFoundException {
        return classFinder.getClass(name, initialize, classLoader);
    }

    public static void setClassFinder(ClassFinder classFinder) {
        TabooLib.classFinder = classFinder;
    }

    public static ClassFinder getClassFinder() {
        return classFinder;
    }

    public static long execution(Runnable task) {
        long startTime = System.nanoTime();
        task.run();
        long endTime = System.nanoTime();
        return (endTime - startTime) / 1000000L;
    }

    public static abstract class ClassFinder {
        public abstract Class<?> getClass(String var1) throws ClassNotFoundException;

        public abstract Class<?> getClass(String var1, boolean var2) throws ClassNotFoundException;

        public abstract Class<?> getClass(String var1, boolean var2, ClassLoader var3) throws ClassNotFoundException;
    }
}

