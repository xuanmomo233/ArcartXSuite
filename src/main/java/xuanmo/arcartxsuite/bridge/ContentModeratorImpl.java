package xuanmo.arcartxsuite.bridge;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ContentModerator;
import xuanmo.arcartxsuite.module.AxsLog;

/** Suite's shared content moderation bridge. */
public final class ContentModeratorImpl implements ContentModerator {
    private final JavaPlugin plugin;
    private final Logger logger;
    private volatile State state;
    private BukkitTask cloudRefreshTask;

    public ContentModeratorImpl(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = AxsLog.logger();
    }

    public void initialize() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        initialize(YamlConfiguration.loadConfiguration(file).getConfigurationSection("moderation"));
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        reload(YamlConfiguration.loadConfiguration(file).getConfigurationSection("moderation"));
    }

    /** Ordinary module loading applies changed settings without rebuilding unchanged state. */
    public synchronized void initialize(ConfigurationSection section) { apply(section, false); }

    /** Explicit host reload also refreshes remote words once, without resetting a healthy timer. */
    public synchronized void reload(ConfigurationSection section) { apply(section, true); }

    private void apply(ConfigurationSection section, boolean forceCloudRefresh) {
        State previous = state;
        if (section == null || !section.getBoolean("enabled", false)) {
            shutdown();
            return;
        }

        LocalSettings local = new LocalSettings(section.getBoolean("cancel-on-match", false),
            section.getString("replacement", "*"), List.copyOf(section.getStringList("blocked-words")),
            List.copyOf(section.getStringList("blocked-patterns")), List.copyOf(section.getStringList("whitelist")),
            List.copyOf(section.getStringList("ignored-punctuations")), section.getBoolean("ignore-separators", false),
            section.getBoolean("redact.enabled", false));
        List<String> urls = section.getBoolean("cloud.enabled", false)
            ? section.getStringList("cloud.urls").stream().map(String::strip).filter(url -> !url.isEmpty()).distinct().toList() : List.of();
        List<String> ignored = section.getStringList("cloud.ignored").stream()
            .map(word -> word.strip().toLowerCase(java.util.Locale.ROOT)).distinct().sorted().toList();
        CloudSettings cloudSettings = new CloudSettings(urls, ignored,
            Math.max(1, section.getInt("cloud.refresh-minutes", 60)) * 1200L);
        Plugin redactPlugin = local.redactEnabled() ? plugin.getServer().getPluginManager().getPlugin("Redact") : null;
        if (redactPlugin != null && !redactPlugin.isEnabled()) redactPlugin = null;
        boolean bindingChanged = previous == null || previous.redactPlugin() != redactPlugin
            || previous.local().redactEnabled() != local.redactEnabled();
        RedactModeration redact = null;
        if (local.redactEnabled() && !bindingChanged) redact = previous.redact();
        if (local.redactEnabled() && (bindingChanged || (redact == null && redactPlugin != null))) {
            redact = RedactModeration.connect(plugin, logger);
        }
        ModerationRules rules;
        if (previous != null && previous.local().equals(local) && previous.redact() == redact) {
            rules = previous.rules();
        } else {
            RedactModeration activeRedact = redact;
            rules = new ModerationRules(local.cancelOnMatch(), local.replacement(), local.words(),
                compilePatterns(local.patterns()), local.whitelist(), local.punctuation(), local.ignoreSeparators(),
                activeRedact == null ? null : activeRedact::find);
        }
        boolean sameSources = previous != null && previous.cloudSettings().urls().equals(urls)
            && previous.cloudSettings().ignored().equals(ignored);
        ModerationCloudWords cloud = sameSources ? previous.cloud()
            : new ModerationCloudWords(urls, ignored, logger, previous == null ? null : previous.cloud());
        if (previous == null || previous.rules() != rules || previous.cloud() != cloud
                || !previous.local().equals(local) || !previous.cloudSettings().equals(cloudSettings)
                || previous.redactPlugin() != redactPlugin) {
            state = new State(local, cloudSettings, redactPlugin, redact, rules, cloud);
        }
        boolean scheduled = false;
        if (cloud.hasSources()) {
            if (cloudRefreshTask == null || cloudRefreshTask.isCancelled() || !sameSources
                    || previous.cloudSettings().period() != cloudSettings.period()) {
                cancelCloudRefresh();
                cloudRefreshTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> refreshIfActive(cloud), 0L, cloudSettings.period());
                scheduled = true;
            }
            if (forceCloudRefresh && !scheduled) plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> refreshIfActive(cloud));
        } else {
            cancelCloudRefresh();
        }
    }

    private void refreshIfActive(ModerationCloudWords expected) {
        State current = state;
        if (current != null && current.cloud() == expected) expected.refresh();
    }

    public synchronized void shutdown() { state = null; cancelCloudRefresh(); }

    private void cancelCloudRefresh() {
        if (cloudRefreshTask != null) { cloudRefreshTask.cancel(); cloudRefreshTask = null; }
    }

    @Override
    public boolean isAvailable() {
        return state != null;
    }

    @Override
    public @NotNull ModerationResult filter(@Nullable String text) {
        if (text == null || text.isEmpty()) return ModerationResult.pass("");
        State current = state;
        if (current == null) return ModerationResult.pass(text);
        return current.rules().filter(text, current.cloud().matcher());
    }

    private List<Pattern> compilePatterns(List<String> patterns) {
        List<Pattern> compiled = new ArrayList<>();
        for (String pattern : patterns) {
            if (pattern.isBlank()) continue;
            try {
                compiled.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException e) {
                logger.warning("moderation.blocked-patterns 正则非法，已跳过: " + pattern + " | " + e.getMessage());
            }
        }
        return List.copyOf(compiled);
    }

    private record LocalSettings(boolean cancelOnMatch, String replacement, List<String> words,
        List<String> patterns, List<String> whitelist, List<String> punctuation,
        boolean ignoreSeparators, boolean redactEnabled) {}
    private record CloudSettings(List<String> urls, List<String> ignored, long period) {}
    private record State(LocalSettings local, CloudSettings cloudSettings, Plugin redactPlugin,
        RedactModeration redact, ModerationRules rules, ModerationCloudWords cloud) {}
}
