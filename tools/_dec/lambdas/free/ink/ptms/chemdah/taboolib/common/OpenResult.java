/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.taboolib.common;

import java.lang.reflect.Field;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OpenResult {
    private final boolean successful;
    private final Object value;

    public OpenResult(boolean successful, @Nullable Object value2) {
        this.successful = successful;
        this.value = value2;
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public boolean isFailed() {
        return !this.successful;
    }

    @Nullable
    public Object getValue() {
        return this.value;
    }

    @NotNull
    public static OpenResult successful() {
        return new OpenResult(true, null);
    }

    @NotNull
    public static OpenResult successful(@Nullable Object value2) {
        return new OpenResult(true, value2);
    }

    @NotNull
    public static OpenResult failed() {
        return new OpenResult(false, null);
    }

    public static OpenResult cast(Object source) {
        if (source == null) {
            return OpenResult.failed();
        }
        if (source instanceof OpenResult) {
            return (OpenResult)source;
        }
        Object successful = OpenResult.readField(source, "successful");
        Object value2 = OpenResult.readField(source, "value");
        return new OpenResult(Boolean.TRUE.equals(successful), value2);
    }

    private static Object readField(Object source, String fieldName) {
        for (Class<?> type = source.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(source);
            }
            catch (NoSuchFieldException ignored) {
                continue;
            }
            catch (IllegalAccessException ex) {
                throw new IllegalStateException("Cannot access field '" + fieldName + "' in " + source.getClass().getName(), ex);
            }
        }
        throw new IllegalArgumentException("Field '" + fieldName + "' not found in " + source.getClass().getName());
    }
}

