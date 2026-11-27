package xuanmo.arcartxsuite.conversation.service;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Resolves optional Chemdah NPC APIs against the runtime class, once per ABI. */
final class ChemdahNpcAccess {
    private record WorldAccessor(Method getter, boolean legacyUnscoped) {}

    private static final ClassValue<WorldAccessor> WORLD_ACCESSORS = new ClassValue<>() {
        @Override
        protected WorldAccessor computeValue(Class<?> type) {
            try {
                return new WorldAccessor(type.getMethod("getWorlds"), false);
            } catch (NoSuchMethodException missing) {
                return new WorldAccessor(null, isLegacyUnscoped(type));
            } catch (RuntimeException | LinkageError unavailable) {
                return new WorldAccessor(null, false);
            }
        }
    };
    private static final ClassValue<Optional<Method>> PLAYER_MATCHERS = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                var method = type.getMethod("isNPC", String.class, String.class, Player.class);
                return method.getReturnType() == boolean.class ? Optional.of(method) : Optional.empty();
            } catch (NoSuchMethodException | RuntimeException | LinkageError unavailable) {
                return Optional.empty();
            }
        }
    };

    private ChemdahNpcAccess() {}

    /** Null means that a world restriction could not be read. An empty list is a known unrestricted binding. */
    static List<String> worlds(Object binding) {
        var accessor = WORLD_ACCESSORS.get(binding.getClass());
        if (accessor.getter() == null) return accessor.legacyUnscoped() ? List.of() : null;
        try {
            Object raw = accessor.getter().invoke(binding);
            Iterable<?> values;
            if (raw instanceof Iterable<?> iterable) values = iterable;
            else if (raw instanceof String[] array) values = java.util.Arrays.asList(array);
            else return null;
            var worlds = new ArrayList<String>();
            for (Object value : values) {
                if (!(value instanceof String world)) return null;
                worlds.add(world);
            }
            return List.copyOf(worlds);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    static boolean hasPlayerMatcher(Object conversation) {
        return PLAYER_MATCHERS.get(conversation.getClass()).isPresent();
    }

    static boolean matchesPlayer(Object conversation, String namespace, String npcId, Player player) {
        var method = PLAYER_MATCHERS.get(conversation.getClass());
        if (method.isEmpty()) return false;
        try {
            return Boolean.TRUE.equals(method.get().invoke(conversation, namespace, npcId, player));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    private static boolean isLegacyUnscoped(Class<?> type) {
        try {
            if (type.getSuperclass() != Object.class) return false;
            int fields = 0;
            for (var field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType() != String.class
                        || !(field.getName().equals("namespace") || field.getName().equals("value"))) return false;
                fields++;
            }
            // Earlier Chemdah bindings contained only their namespace and NPC id.
            return fields == 2;
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }
}
