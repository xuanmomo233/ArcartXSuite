package xuanmo.arcartxsuite.bridge;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ContentModerator;
import xuanmo.arcartxsuite.api.util.AxsScheduler;
import xuanmo.arcartxsuite.module.AxsLog;

/**
 * 内容审核桥接实现：敏感词过滤（本地词库 + 云端词库 + 标点穿透防护 + 白名单）。
 * <p>
 * 配置位于宿主 config.yml 的 moderation 节。
 */
public final class ContentModeratorImpl implements ContentModerator {

    private final JavaPlugin plugin;
    private final Logger logger;

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // 配置
    private volatile boolean enabled;
    private volatile boolean cancelOnMatch;
    private volatile String replacement = "*";
    private volatile List<String> blockedWords = List.of();
    private volatile List<Pattern> blockedPatterns = List.of();
    private volatile Set<String> ignoredPunctuations = Set.of();
    private volatile Set<String> whitelist = Set.of();
    private volatile boolean cloudEnabled;
    private volatile List<String> cloudUrls = List.of();
    private volatile Set<String> cloudIgnored = Set.of();
    private volatile int cloudRefreshMinutes = 60;

    // 云端词库运行时
    private final CopyOnWriteArrayList<String> cloudWords = new CopyOnWriteArrayList<>();
    private volatile BukkitTask cloudRefreshTask;

    public ContentModeratorImpl(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = AxsLog.logger();
    }

    /**
     * 从 config.yml 加载配置并启动云端词库刷新。
     */
    public void initialize() {
        if (!initialized.compareAndSet(false, true)) {
            reload();
            return;
        }
        reload();
    }

    /**
     * 重载配置（支持热重载）。
     */
    public void reload() {
        YamlConfiguration config = loadRootConfig();
        ConfigurationSection sec = config.getConfigurationSection("moderation");
        if (sec == null) {
            enabled = false;
            cancelCloudRefresh();
            return;
        }
        enabled = sec.getBoolean("enabled", false);
        cancelOnMatch = sec.getBoolean("cancel-on-match", false);
        replacement = sec.getString("replacement", "*");
        blockedWords = lowerList(sec.getStringList("blocked-words"));
        blockedPatterns = compilePatterns(sec.getStringList("blocked-patterns"));
        ignoredPunctuations = lowerSet(sec.getStringList("ignored-punctuations"));
        whitelist = lowerSet(sec.getStringList("whitelist"));

        ConfigurationSection cloudSec = sec.getConfigurationSection("cloud");
        cloudEnabled = cloudSec != null && cloudSec.getBoolean("enabled", false);
        cloudUrls = cloudSec == null ? List.of() : cloudSec.getStringList("urls");
        cloudIgnored = lowerSet(cloudSec == null ? List.of() : cloudSec.getStringList("ignored"));
        cloudRefreshMinutes = cloudSec == null ? 60 : Math.max(1, cloudSec.getInt("refresh-minutes", 60));

        cancelCloudRefresh();
        if (cloudEnabled && !cloudUrls.isEmpty()) {
            scheduleCloudRefresh();
            AxsScheduler.runAsync(plugin, this::refreshCloudWords);
        }
    }

    private void scheduleCloudRefresh() {
        long periodTicks = Math.max(1200L, cloudRefreshMinutes * 60L * 20L);
        cloudRefreshTask = AxsScheduler.runAsyncTimer(plugin, this::refreshCloudWords, 20L, periodTicks);
    }

    private void cancelCloudRefresh() {
        if (cloudRefreshTask != null) {
            cloudRefreshTask.cancel();
            cloudRefreshTask = null;
        }
    }

    private void refreshCloudWords() {
        LinkedHashSet<String> loaded = new LinkedHashSet<>();
        for (String url : cloudUrls) {
            if (url == null || url.isBlank()) continue;
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(URI.create(url.trim()).toURL().openStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String normalized = line.trim().toLowerCase(Locale.ROOT);
                    if (!normalized.isBlank() && !normalized.startsWith("#") && !cloudIgnored.contains(normalized)) {
                        loaded.add(normalized);
                    }
                }
            } catch (Exception e) {
                logger.warning("拉取云端敏感词库失败: " + url + " | " + e.getMessage());
            }
        }
        cloudWords.clear();
        cloudWords.addAll(loaded);
        logger.fine("云端敏感词库已刷新: " + cloudWords.size() + " 条");
    }

    @Override
    public boolean isAvailable() {
        return enabled;
    }

    @Override
    public @NotNull ModerationResult filter(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return ModerationResult.pass("");
        }
        if (!enabled) {
            return ModerationResult.pass(text);
        }
        String current = text;

        // 1. 剥离标点（防止 f.u.c.k 绕过）
        String stripped = stripPunctuations(current);

        // 2. 白名单检查
        for (String allowed : whitelist) {
            if (stripped.toLowerCase(Locale.ROOT).contains(allowed)) {
                return ModerationResult.pass(current);
            }
        }

        // 3. 本地 + 云端屏蔽词
        List<String> words = new ArrayList<>(blockedWords);
        for (String cloudWord : cloudWords) {
            if (cloudWord != null && !cloudWord.isBlank() && !cloudIgnored.contains(cloudWord)) {
                words.add(cloudWord);
            }
        }
        for (String blockedWord : words) {
            if (blockedWord.isBlank()) continue;
            if (stripped.toLowerCase(Locale.ROOT).contains(blockedWord)) {
                if (cancelOnMatch) {
                    return ModerationResult.block(current);
                }
                current = replaceIgnoreCase(current, blockedWord, replacement);
                stripped = stripPunctuations(current);
            }
        }

        // 4. 正则屏蔽
        for (Pattern pattern : blockedPatterns) {
            Matcher matcher = pattern.matcher(stripped);
            if (!matcher.find()) continue;
            if (cancelOnMatch) {
                return ModerationResult.block(current);
            }
            current = matcher.replaceAll(Matcher.quoteReplacement(replacement));
            stripped = stripPunctuations(current);
        }

        return ModerationResult.pass(current);
    }

    private String stripPunctuations(String input) {
        if (ignoredPunctuations.isEmpty()) return input;
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            String ch = String.valueOf(input.charAt(i));
            if (!ignoredPunctuations.contains(ch.toLowerCase(Locale.ROOT))) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String replaceIgnoreCase(String input, String target, String replacement) {
        return Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE)
            .matcher(input)
            .replaceAll(Matcher.quoteReplacement(replacement));
    }

    private YamlConfiguration loadRootConfig() {
        java.io.File configFile = new java.io.File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return new YamlConfiguration();
        return YamlConfiguration.loadConfiguration(configFile);
    }

    private static List<String> lowerList(List<String> list) {
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s != null && !s.isBlank()) result.add(s.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(result);
    }

    private static Set<String> lowerSet(List<String> list) {
        Set<String> result = new LinkedHashSet<>();
        for (String s : list) {
            if (s != null && !s.isBlank()) result.add(s.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(result);
    }

    private List<Pattern> compilePatterns(List<String> patterns) {
        List<Pattern> result = new ArrayList<>();
        for (String p : patterns) {
            if (p == null || p.isBlank()) continue;
            try {
                result.add(Pattern.compile(p, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                logger.warning("moderation.blocked-patterns 正则非法，已跳过: " + p + " | " + e.getMessage());
            }
        }
        return List.copyOf(result);
    }
}
