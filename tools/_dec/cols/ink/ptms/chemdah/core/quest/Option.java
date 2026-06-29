/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.core.quest;

import ink.ptms.chemdah.taboolib.common.platform.function.IOKt;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import kotlin.Metadata;
import kotlin1822.NoWhenBranchMatchedException;
import kotlin1822.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Retention(value=RetentionPolicy.RUNTIME)
@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u001b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0086\u0002\u0018\u00002\u00020\u0001:\u0001\u0005B\b\u0012\u0006\u0010\u0002\u001a\u00020\u0003R\u000f\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0006\u001a\u0004\b\u0002\u0010\u0004\u00a8\u0006\u0006"}, d2={"Link/ptms/chemdah/core/quest/Option;", "", "type", "Link/ptms/chemdah/core/quest/Option$Type;", "()Link/ptms/chemdah/core/quest/Option$Type;", "Type", "Chemdah"})
public @interface Option {
    public Type type();

    @Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\b\b\u0086\u0001\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u001b\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0086\u0002j\u0002\b\tj\u0002\b\nj\u0002\b\u000bj\u0002\b\fj\u0002\b\rj\u0002\b\u000ej\u0002\b\u000f\u00a8\u0006\u0010"}, d2={"Link/ptms/chemdah/core/quest/Option$Type;", "", "(Ljava/lang/String;I)V", "get", "", "config", "Link/ptms/chemdah/taboolib/library/configuration/ConfigurationSection;", "node", "", "LIST", "MAP_LIST", "SECTION", "TEXT", "NUMBER", "BOOLEAN", "ANY", "Chemdah"})
    public static final class Type
    extends Enum<Type> {
        public static final /* enum */ Type LIST = new Type();
        public static final /* enum */ Type MAP_LIST = new Type();
        public static final /* enum */ Type SECTION = new Type();
        public static final /* enum */ Type TEXT = new Type();
        public static final /* enum */ Type NUMBER = new Type();
        public static final /* enum */ Type BOOLEAN = new Type();
        public static final /* enum */ Type ANY = new Type();
        private static final /* synthetic */ Type[] $VALUES;

        @Nullable
        public final Object get(@NotNull ConfigurationSection config, @NotNull String node) {
            Intrinsics.checkNotNullParameter((Object)config, (String)"config");
            Intrinsics.checkNotNullParameter((Object)node, (String)"node");
            try {
                Object object;
                switch (WhenMappings.$EnumSwitchMapping$0[this.ordinal()]) {
                    case 1: {
                        object = config.getList(node);
                        break;
                    }
                    case 2: {
                        object = config.getMapList(node);
                        break;
                    }
                    case 3: {
                        object = config.getConfigurationSection(node);
                        break;
                    }
                    case 4: {
                        object = config.getString(node);
                        break;
                    }
                    case 5: {
                        object = config.getDouble(node);
                        break;
                    }
                    case 6: {
                        object = config.getBoolean(node);
                        break;
                    }
                    case 7: {
                        object = config.get(node);
                        break;
                    }
                    default: {
                        throw new NoWhenBranchMatchedException();
                    }
                }
                return object;
            }
            catch (Throwable e) {
                Class<?> clazz;
                Object[] objectArray = new Object[]{config.get(node) + " (" + ((clazz = config.get(node)) != null && (clazz = clazz.getClass()) != null ? clazz.getSimpleName() : null) + ") cannot cast to " + (Object)((Object)this) + " (" + node + ')'};
                IOKt.warning((Object[])objectArray);
                return null;
            }
        }

        public static Type[] values() {
            return (Type[])$VALUES.clone();
        }

        public static Type valueOf(String value2) {
            return Enum.valueOf(Type.class, value2);
        }

        static {
            $VALUES = typeArray = new Type[]{Type.LIST, Type.MAP_LIST, Type.SECTION, Type.TEXT, Type.NUMBER, Type.BOOLEAN, Type.ANY};
        }

        @Metadata(mv={1, 8, 0}, k=3, xi=48)
        public final class WhenMappings {
            public static final /* synthetic */ int[] $EnumSwitchMapping$0;

            static {
                int[] nArray = new int[Type.values().length];
                try {
                    nArray[Type.LIST.ordinal()] = 1;
                }
                catch (NoSuchFieldError noSuchFieldError) {
                    // empty catch block
                }
                try {
                    nArray[Type.MAP_LIST.ordinal()] = 2;
                }
                catch (NoSuchFieldError noSuchFieldError) {
                    // empty catch block
                }
                try {
                    nArray[Type.SECTION.ordinal()] = 3;
                }
                catch (NoSuchFieldError noSuchFieldError) {
                    // empty catch block
                }
                try {
                    nArray[Type.TEXT.ordinal()] = 4;
                }
                catch (NoSuchFieldError noSuchFieldError) {
                    // empty catch block
                }
                try {
                    nArray[Type.NUMBER.ordinal()] = 5;
                }
                catch (NoSuchFieldError noSuchFieldError) {
                    // empty catch block
                }
                try {
                    nArray[Type.BOOLEAN.ordinal()] = 6;
                }
                catch (NoSuchFieldError noSuchFieldError) {
                    // empty catch block
                }
                try {
                    nArray[Type.ANY.ordinal()] = 7;
                }
                catch (NoSuchFieldError noSuchFieldError) {
                    // empty catch block
                }
                $EnumSwitchMapping$0 = nArray;
            }
        }
    }
}

