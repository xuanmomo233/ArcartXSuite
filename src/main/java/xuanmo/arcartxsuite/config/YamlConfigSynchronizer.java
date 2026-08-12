package xuanmo.arcartxsuite.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.SyncResult;

public final class YamlConfigSynchronizer {

    private YamlConfigSynchronizer() {
    }

    /**
     * 递归深度上限。
     * <p>
     * 正常的 YAML 配置树深度很少超过 10 层。此上限用于防御 Mohist 等混合端
     * Bukkit YAML 实现的边界情况（如 {@code getConfigurationSection(key)} 返回
     * 当前 section 而非子 section），避免 {@link #mergeSection} 无限递归导致
     * {@link StackOverflowError}。
     */
    private static final int MAX_DEPTH = 64;

    private static final java.util.logging.Logger DEPTH_LOGGER =
        java.util.logging.Logger.getLogger("AXS-ConfigSync");

    /**
     * 把内置默认值的内容合并到一个内存中的 live YAML 副本，不动任何文件。
     * <p>
     * 用于 dry-run / 诊断模式：调用方传入已加载的 live 配置（可为空 {@link YamlConfiguration}）
     * 与 defaults，本方法在 live 上原地合并，返回此次合并新增 / 删除的路径列表。
     *
     * @param live      在内存中可被修改的 live 配置（不为 null）
     * @param defaults  内置默认配置
     * @param policy    合并策略
     * @return 合并差异
     */
    public static MergeOutcome merge(
        YamlConfiguration live,
        YamlConfiguration defaults,
        SyncPolicy policy
    ) {
        Objects.requireNonNull(live, "live");
        Objects.requireNonNull(defaults, "defaults");
        Objects.requireNonNull(policy, "policy");
        List<String> addedPaths = new ArrayList<>();
        List<String> removedPaths = new ArrayList<>();
        mergeSection(live, live, defaults, "", policy, true, true, addedPaths, removedPaths, 0);
        return new MergeOutcome(List.copyOf(addedPaths), List.copyOf(removedPaths));
    }

    /**
     * 加载默认配置文件流为 {@link YamlConfiguration}。
     */
    public static YamlConfiguration parseDefaults(String resourcePath, byte[] bytes) throws IOException {
        try {
            return loadDefaults(resourcePath, bytes);
        } catch (InvalidConfigurationException exception) {
            throw new IOException("内置默认资源格式无效: " + resourcePath, exception);
        }
    }

    /** 列出某 section 下所有叶子路径（递归）。 */
    public static List<String> leafPaths(org.bukkit.configuration.ConfigurationSection section) {
        return listLeafPaths(section, "");
    }

    /** 合并差异。 */
    public record MergeOutcome(List<String> addedPaths, List<String> removedPaths) {
        public MergeOutcome {
            addedPaths = addedPaths == null ? List.of() : List.copyOf(addedPaths);
            removedPaths = removedPaths == null ? List.of() : List.copyOf(removedPaths);
        }

        public boolean isEmpty() {
            return addedPaths.isEmpty() && removedPaths.isEmpty();
        }
    }

    public static SyncResult synchronize(
        File targetFile,
        String resourcePath,
        InputStream defaultsInputStream,
        SyncPolicy policy,
        File backupRoot
    ) throws IOException {
        Objects.requireNonNull(targetFile, "targetFile");
        Objects.requireNonNull(resourcePath, "resourcePath");
        Objects.requireNonNull(policy, "policy");
        if (defaultsInputStream == null) {
            return SyncResult.skipped(resourcePath, targetFile, "内置默认资源不存在。");
        }

        byte[] defaultsBytes = defaultsInputStream.readAllBytes();
        YamlConfiguration defaultsConfiguration;
        try {
            defaultsConfiguration = loadDefaults(resourcePath, defaultsBytes);
        } catch (InvalidConfigurationException exception) {
            throw new IOException("内置默认资源格式无效: " + resourcePath, exception);
        }

        if (!targetFile.exists()) {
            File parent = targetFile.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            Files.write(targetFile.toPath(), defaultsBytes);
            return new SyncResult(
                resourcePath,
                targetFile,
                true,
                true,
                false,
                "",
                listLeafPaths(defaultsConfiguration, ""),
                List.of(),
                null
            );
        }

        YamlConfiguration liveConfiguration = new YamlConfiguration();
        liveConfiguration.options().parseComments(true);
        try {
            liveConfiguration.load(targetFile);
        } catch (InvalidConfigurationException exception) {
            return SyncResult.skipped(resourcePath, targetFile, "现有配置 YAML 格式无效: " + exception.getMessage());
        } catch (IOException exception) {
            return SyncResult.skipped(resourcePath, targetFile, "读取现有配置失败: " + exception.getMessage());
        }

        List<String> addedPaths = new ArrayList<>();
        List<String> removedPaths = new ArrayList<>();
        mergeSection(
            liveConfiguration,
            liveConfiguration,
            defaultsConfiguration,
            "",
            policy,
            true,
            true,
            addedPaths,
            removedPaths,
            0
        );

        if (addedPaths.isEmpty() && removedPaths.isEmpty()) {
            return SyncResult.unchanged(resourcePath, targetFile);
        }

        File backupFile = createBackup(targetFile, resourcePath, backupRoot);
        liveConfiguration.save(targetFile);
        return new SyncResult(
            resourcePath,
            targetFile,
            false,
            true,
            false,
            "",
            addedPaths,
            removedPaths,
            backupFile
        );
    }

    public static BatchSyncResult synchronizeAll(
        File dataFolder,
        List<xuanmo.arcartxsuite.api.config.ConfigSyncSpec> resources,
        ResourceProvider resourceProvider,
        File backupRoot
    ) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(resourceProvider, "resourceProvider");

        List<SyncResult> results = new ArrayList<>(resources.size());
        for (xuanmo.arcartxsuite.api.config.ConfigSyncSpec spec : resources) {
            File targetFile = new File(dataFolder, spec.targetRelativePath().replace('/', File.separatorChar));
            try (InputStream defaultsInputStream = resourceProvider.open(spec.resourcePath())) {
                results.add(
                    synchronize(
                        targetFile,
                        spec.resourcePath(),
                        defaultsInputStream,
                        spec.policy(),
                        backupRoot
                    )
                );
            } catch (IOException exception) {
                results.add(SyncResult.skipped(spec.resourcePath(), targetFile, exception.getMessage()));
            }
        }
        return new BatchSyncResult(results);
    }

    private static YamlConfiguration loadDefaults(String resourcePath, byte[] defaultsBytes)
        throws InvalidConfigurationException {
        YamlConfiguration defaultsConfiguration = new YamlConfiguration();
        defaultsConfiguration.options().parseComments(true);
        defaultsConfiguration.loadFromString(new String(defaultsBytes, StandardCharsets.UTF_8));
        return defaultsConfiguration;
    }

    private static void mergeSection(
        YamlConfiguration liveConfiguration,
        ConfigurationSection liveSection,
        ConfigurationSection defaultsSection,
        String parentPath,
        SyncPolicy policy,
        boolean sectionExistedBefore,
        boolean pruneUnknownPaths,
        List<String> addedPaths,
        List<String> removedPaths,
        int depth
    ) {
        if (depth > MAX_DEPTH) {
            DEPTH_LOGGER.warning("YamlConfigSynchronizer.mergeSection 超过最大递归深度 " + MAX_DEPTH
                + "，已在 path='" + parentPath + "' 处中止。"
                + "这可能由 Mohist 等混合端的 YAML 实现边界情况或配置循环引用引起。");
            return;
        }
        if (policy.isOpaqueSection(parentPath)) {
            return;
        }
        if (policy.isDynamicSection(parentPath) && sectionExistedBefore) {
            mergeExistingDynamicChildren(
                liveConfiguration,
                liveSection,
                defaultsSection,
                parentPath,
                policy,
                addedPaths,
                removedPaths,
                depth
            );
            return;
        }

        for (String key : defaultsSection.getKeys(false)) {
            String path = childPath(parentPath, key);
            ConfigurationSection defaultChildSection = defaultsSection.getConfigurationSection(key);
            if (defaultChildSection != null) {
                // 防御 Mohist 等混合端的 YAML 实现 bug：
                // getConfigurationSection(key) 应返回子 section，但某些实现可能返回当前 section 本身，
                // 导致 mergeSection 无限自递归。检测到这种自引用时跳过。
                if (defaultChildSection == defaultsSection) {
                    DEPTH_LOGGER.warning("YamlConfigSynchronizer.mergeSection 检测到 getConfigurationSection('"
                        + key + "') 返回了当前 section 自身（疑似 Mohist YAML 实现 bug），已跳过该键。");
                    continue;
                }
                boolean existed = liveConfiguration.isSet(path);
                if (!existed) {
                    liveConfiguration.createSection(path);
                    copyComments(liveConfiguration, path, defaultsSection, key);
                    addedPaths.add(path);
                    if (policy.isOpaqueSection(path)) {
                        copySectionContents(liveConfiguration, defaultChildSection, path, addedPaths, depth);
                        continue;
                    }
                }
                ConfigurationSection liveChildSection = liveConfiguration.getConfigurationSection(path);
                if (liveChildSection != null) {
                    mergeSection(
                        liveConfiguration,
                        liveChildSection,
                        defaultChildSection,
                        path,
                        policy,
                        existed,
                        pruneUnknownPaths,
                        addedPaths,
                        removedPaths,
                        depth + 1
                    );
                }
                continue;
            }

            Object defaultValue = defaultsSection.get(key);
            if (!liveConfiguration.isSet(path)) {
                liveConfiguration.set(path, defaultValue);
                copyComments(liveConfiguration, path, defaultsSection, key);
                addedPaths.add(path);
                continue;
            }

            if (pruneUnknownPaths && liveConfiguration.isConfigurationSection(path)) {
                liveConfiguration.set(path, defaultValue);
                copyComments(liveConfiguration, path, defaultsSection, key);
                removedPaths.add(path + ".*");
                addedPaths.add(path);
            }
        }

        for (String liveKey : List.copyOf(liveSection.getKeys(false))) {
            String path = childPath(parentPath, liveKey);
            if ((pruneUnknownPaths && !defaultsSection.isSet(liveKey)) || policy.isObsoletePath(path)) {
                liveConfiguration.set(path, null);
                removedPaths.add(path);
            }
        }
    }

    private static void mergeExistingDynamicChildren(
        YamlConfiguration liveConfiguration,
        ConfigurationSection liveSection,
        ConfigurationSection defaultsSection,
        String parentPath,
        SyncPolicy policy,
        List<String> addedPaths,
        List<String> removedPaths,
        int depth
    ) {
        if (depth > MAX_DEPTH) {
            DEPTH_LOGGER.warning("YamlConfigSynchronizer.mergeExistingDynamicChildren 超过最大递归深度 "
                + MAX_DEPTH + "，已在 path='" + parentPath + "' 处中止。");
            return;
        }
        for (String liveKey : List.copyOf(liveSection.getKeys(false))) {
            String path = childPath(parentPath, liveKey);
            if (policy.isObsoletePath(path)) {
                liveConfiguration.set(path, null);
                removedPaths.add(path);
                continue;
            }

            ConfigurationSection defaultChildSection = defaultsSection.getConfigurationSection(liveKey);
            ConfigurationSection liveChildSection = liveSection.getConfigurationSection(liveKey);
            if (defaultChildSection == null || liveChildSection == null) {
                continue;
            }
            // 防御 Mohist YAML 实现 bug：getConfigurationSection 返回自身
            if (defaultChildSection == defaultsSection || liveChildSection == liveSection) {
                DEPTH_LOGGER.warning("YamlConfigSynchronizer.mergeExistingDynamicChildren 检测到 "
                    + "getConfigurationSection('" + liveKey + "') 返回了当前 section 自身，已跳过。");
                continue;
            }

            mergeSection(
                liveConfiguration,
                liveChildSection,
                defaultChildSection,
                path,
                policy,
                true,
                false,
                addedPaths,
                removedPaths,
                depth + 1
            );
        }
    }

    private static File createBackup(File targetFile, String resourcePath, File backupRoot) throws IOException {
        Objects.requireNonNull(backupRoot, "backupRoot");
        File backupFile = new File(backupRoot, resourcePath.replace('/', File.separatorChar));
        File parent = backupFile.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        Files.copy(targetFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return backupFile;
    }

    private static void copySectionContents(
        YamlConfiguration liveConfiguration,
        ConfigurationSection defaultsSection,
        String parentPath,
        List<String> addedPaths,
        int depth
    ) {
        if (depth > MAX_DEPTH) {
            DEPTH_LOGGER.warning("YamlConfigSynchronizer.copySectionContents 超过最大递归深度 "
                + MAX_DEPTH + "，已在 path='" + parentPath + "' 处中止。");
            return;
        }
        for (String key : defaultsSection.getKeys(false)) {
            String path = childPath(parentPath, key);
            ConfigurationSection defaultChildSection = defaultsSection.getConfigurationSection(key);
            if (defaultChildSection != null) {
                if (defaultChildSection == defaultsSection) {
                    DEPTH_LOGGER.warning("YamlConfigSynchronizer.copySectionContents 检测到 "
                        + "getConfigurationSection('" + key + "') 返回了当前 section 自身，已跳过。");
                    continue;
                }
                liveConfiguration.createSection(path);
                copyComments(liveConfiguration, path, defaultsSection, key);
                addedPaths.add(path);
                copySectionContents(liveConfiguration, defaultChildSection, path, addedPaths, depth + 1);
                continue;
            }

            liveConfiguration.set(path, defaultsSection.get(key));
            copyComments(liveConfiguration, path, defaultsSection, key);
            addedPaths.add(path);
        }
    }

    private static void copyComments(
        YamlConfiguration liveConfiguration,
        String path,
        ConfigurationSection defaultsSection,
        String key
    ) {
        List<String> comments = defaultsSection.getComments(key);
        if (comments != null && !comments.isEmpty()) {
            liveConfiguration.setComments(path, comments);
        }
        List<String> inlineComments = defaultsSection.getInlineComments(key);
        if (inlineComments != null && !inlineComments.isEmpty()) {
            liveConfiguration.setInlineComments(path, inlineComments);
        }
    }

    private static List<String> listLeafPaths(ConfigurationSection section, String parentPath) {
        return listLeafPaths(section, parentPath, 0);
    }

    private static List<String> listLeafPaths(ConfigurationSection section, String parentPath, int depth) {
        List<String> paths = new ArrayList<>();
        if (depth > MAX_DEPTH) {
            DEPTH_LOGGER.warning("YamlConfigSynchronizer.listLeafPaths 超过最大递归深度 "
                + MAX_DEPTH + "，已在 path='" + parentPath + "' 处中止。");
            return paths;
        }
        for (String key : section.getKeys(false)) {
            String path = childPath(parentPath, key);
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                paths.add(path);
                continue;
            }
            if (child == section) {
                DEPTH_LOGGER.warning("YamlConfigSynchronizer.listLeafPaths 检测到 "
                    + "getConfigurationSection('" + key + "') 返回了当前 section 自身，已跳过。");
                continue;
            }
            List<String> childPaths = listLeafPaths(child, path, depth + 1);
            if (childPaths.isEmpty()) {
                paths.add(path);
            } else {
                paths.addAll(childPaths);
            }
        }
        return List.copyOf(paths);
    }

    private static String childPath(String parentPath, String key) {
        return parentPath == null || parentPath.isEmpty() ? key : parentPath + "." + key;
    }

    @FunctionalInterface
    public interface ResourceProvider {
        InputStream open(String resourcePath) throws IOException;
    }

    public record BatchSyncResult(List<SyncResult> results) {
        public BatchSyncResult {
            results = results == null ? List.of() : List.copyOf(results);
        }

        public int createdCount() {
            int count = 0;
            for (SyncResult result : results) {
                if (result.created()) {
                    count++;
                }
            }
            return count;
        }

        public int changedExistingCount() {
            int count = 0;
            for (SyncResult result : results) {
                if (result.changed() && !result.created()) {
                    count++;
                }
            }
            return count;
        }

        public int skippedCount() {
            int count = 0;
            for (SyncResult result : results) {
                if (result.skipped()) {
                    count++;
                }
            }
            return count;
        }

        public int addedPathCount() {
            int count = 0;
            for (SyncResult result : results) {
                if (!result.created()) {
                    count += result.addedCount();
                }
            }
            return count;
        }

        public int removedPathCount() {
            int count = 0;
            for (SyncResult result : results) {
                count += result.removedCount();
            }
            return count;
        }

        public boolean changed() {
            return createdCount() > 0 || changedExistingCount() > 0 || skippedCount() > 0;
        }
    }
}
