package xuanmo.arcartxsuite.config.diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.config.MigrationOperation;

/**
 * 执行单条 {@link MigrationOperation} 到目标 {@link YamlConfiguration}。
 * <p>
 * 操作通过修改传入的 configuration 完成。返回 true 表示对象有变化。
 */
public final class MigrationOperationExecutor {

    private MigrationOperationExecutor() {
    }

    /**
     * @return 若 configuration 有任何字段变化则 true
     * @throws MigrationFailureException 操作内部错误（如 from 路径是 section 但 to 已存在为非 section）
     */
    public static boolean execute(YamlConfiguration configuration, MigrationOperation operation)
        throws MigrationFailureException {
        try {
            if (operation instanceof MigrationOperation.Rename rename) {
                return renameSibling(configuration, rename.from(), rename.to());
            } else if (operation instanceof MigrationOperation.Remove remove) {
                return removePath(configuration, remove.path());
            } else if (operation instanceof MigrationOperation.Move move) {
                return movePath(configuration, move.from(), move.to());
            } else if (operation instanceof MigrationOperation.SetIfMissing s) {
                return setIfMissing(configuration, s.path(), s.value());
            } else if (operation instanceof MigrationOperation.ValueMap vm) {
                return applyValueMap(configuration, vm.path(), vm.mapping());
            }
            return false;
        } catch (RuntimeException exception) {
            throw new MigrationFailureException(operation, exception.getMessage(), exception);
        }
    }

    private static boolean renameSibling(YamlConfiguration configuration, String from, String to) {
        if (containsWildcard(from)) {
            return renameWithWildcards(configuration, from, to);
        }
        if (!configuration.isSet(from)) {
            return false;
        }
        Object value = configuration.get(from);
        configuration.set(to, value);
        configuration.set(from, null);
        return true;
    }

    private static boolean containsWildcard(String path) {
        return path != null && path.indexOf('*') >= 0;
    }

    /**
     * 支持含 {@code *} 通配符的批量重命名。
     * <p>
     * 例如 {@code from="tasks.daily.*.xp-reward", to="tasks.daily.*.base-xp-reward"}
     * 会遍历 {@code tasks.daily} 下所有子键，将各自的 {@code xp-reward} 重命名为 {@code base-xp-reward}。
     * <p>
     * 要求 {@code from} 和 {@code to} 的路径段数相同，且通配符出现位置一致。
     */
    private static boolean renameWithWildcards(YamlConfiguration configuration, String from, String to) {
        String[] fromParts = from.split("\\.");
        String[] toParts = to.split("\\.");
        if (fromParts.length != toParts.length) {
            return false;
        }
        // 收集 from 中通配符位置
        List<Integer> wildcardIndices = new ArrayList<>();
        for (int i = 0; i < fromParts.length; i++) {
            if ("*".equals(fromParts[i])) {
                wildcardIndices.add(i);
            }
        }
        // 遍历配置树，找到所有匹配 from 模式的具体路径
        List<String> matchedPaths = collectMatchingPaths(configuration, fromParts, 0, "");
        if (matchedPaths.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (String matchedPath : matchedPaths) {
            String[] matchedParts = matchedPath.split("\\.");
            // 用通配符位置的实际值替换 to 中的 *
            String[] resolvedToParts = toParts.clone();
            for (int wi : wildcardIndices) {
                resolvedToParts[wi] = matchedParts[wi];
            }
            String resolvedTo = String.join(".", resolvedToParts);
            if (configuration.isSet(matchedPath)) {
                Object value = configuration.get(matchedPath);
                configuration.set(resolvedTo, value);
                configuration.set(matchedPath, null);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * 递归遍历配置树，收集所有匹配给定通配符模式的具体路径。
     *
     * @param section  当前层级的 ConfigurationSection
     * @param pattern  路径模式按 {@code .} 分割后的数组
     * @param depth    当前匹配到第几层
     * @param prefix   已匹配的路径前缀
     * @return 所有匹配的具体路径列表
     */
    private static List<String> collectMatchingPaths(ConfigurationSection section, String[] pattern, int depth, String prefix) {
        List<String> results = new ArrayList<>();
        if (depth >= pattern.length) {
            return results;
        }
        String segment = pattern[depth];
        boolean isLast = depth == pattern.length - 1;

        if ("*".equals(segment)) {
            // 通配符：遍历当前 section 的所有子键
            for (String key : section.getKeys(false)) {
                String childPath = prefix.isEmpty() ? key : prefix + "." + key;
                if (isLast) {
                    results.add(childPath);
                } else {
                    ConfigurationSection child = section.getConfigurationSection(key);
                    if (child != null) {
                        results.addAll(collectMatchingPaths(child, pattern, depth + 1, childPath));
                    }
                }
            }
        } else {
            // 精确匹配
            if (isLast) {
                if (section.isSet(segment)) {
                    results.add(prefix.isEmpty() ? segment : prefix + "." + segment);
                }
            } else {
                ConfigurationSection child = section.getConfigurationSection(segment);
                if (child != null) {
                    String childPath = prefix.isEmpty() ? segment : prefix + "." + segment;
                    results.addAll(collectMatchingPaths(child, pattern, depth + 1, childPath));
                }
            }
        }
        return results;
    }

    private static boolean removePath(YamlConfiguration configuration, String path) {
        if (!configuration.isSet(path)) {
            return false;
        }
        configuration.set(path, null);
        return true;
    }

    private static boolean movePath(YamlConfiguration configuration, String from, String to) {
        if (!configuration.isSet(from)) {
            return false;
        }
        Object value = configuration.get(from);
        configuration.set(from, null);
        configuration.set(to, value);
        return true;
    }

    private static boolean setIfMissing(YamlConfiguration configuration, String path, Object value) {
        if (configuration.isSet(path)) {
            return false;
        }
        configuration.set(path, value);
        return true;
    }

    private static boolean applyValueMap(YamlConfiguration configuration, String path, Map<String, String> mapping) {
        Object current = configuration.get(path);
        if (current instanceof String s && mapping.containsKey(s)) {
            String mapped = mapping.get(s);
            if (!mapped.equals(s)) {
                configuration.set(path, mapped);
                return true;
            }
        } else if (current instanceof ConfigurationSection) {
            // value-map 仅作用于标量字段，section 跳过
            return false;
        }
        return false;
    }

    /** 执行 {@link MigrationOperation} 时抛出的内部异常。 */
    public static final class MigrationFailureException extends Exception {
        private final MigrationOperation operation;

        public MigrationFailureException(MigrationOperation operation, String message, Throwable cause) {
            super(message, cause);
            this.operation = operation;
        }

        public MigrationOperation operation() {
            return operation;
        }
    }
}
