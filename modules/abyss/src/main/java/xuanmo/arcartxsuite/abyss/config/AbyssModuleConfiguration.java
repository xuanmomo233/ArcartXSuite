package xuanmo.arcartxsuite.abyss.config;

import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

/**
 * ArcartXAbyss.yml 配置解析。全部 record + 静态 load。
 */
public record AbyssModuleConfiguration(
    AbyssSection abyss,
    DeathSection death,
    EscapeSection escape,
    RestrictionsSection restrictions,
    PartySection party,
    StaminaSection stamina,
    LeaderboardSection leaderboard,
    StatisticsSection statistics,
    StorageSection storage,
    UiSection ui,
    boolean debug
) {

    public static AbyssModuleConfiguration load(ConfigurationSection yaml) {
        return new AbyssModuleConfiguration(
            AbyssSection.load(yaml.getConfigurationSection("abyss")),
            DeathSection.load(yaml.getConfigurationSection("death")),
            EscapeSection.load(yaml.getConfigurationSection("escape")),
            RestrictionsSection.load(yaml.getConfigurationSection("restrictions")),
            PartySection.load(yaml.getConfigurationSection("party")),
            StaminaSection.load(yaml.getConfigurationSection("stamina")),
            LeaderboardSection.load(yaml.getConfigurationSection("leaderboard")),
            StatisticsSection.load(yaml.getConfigurationSection("statistics")),
            StorageSection.load(yaml.getConfigurationSection("storage")),
            UiSection.load(yaml.getConfigurationSection("ui")),
            yaml.getBoolean("debug", false)
        );
    }

    // ── 副本实例 ────────────────────────────────────────

    public record AbyssSection(
        String worldPrefix,
        int instanceCleanupTimeout,
        int instanceCheckInterval,
        long worldLoadTimeout,
        int maxEntitiesPerInstance,
        boolean nmsFastLoad,
        boolean asyncWorldCopy
    ) {
        static AbyssSection load(ConfigurationSection s) {
            if (s == null) {
                return new AbyssSection("axs_abyss_", 7200, 30, 120000L, 100, true, true);
            }
            return new AbyssSection(
                s.getString("world-prefix", "axs_abyss_"),
                s.getInt("instance-cleanup-timeout", 7200),
                s.getInt("instance-check-interval", 30),
                s.getLong("world-load-timeout", 120000L),
                s.getInt("max-entities-per-instance", 100),
                s.getBoolean("nms-fast-load", true),
                s.getBoolean("async-world-copy", true)
            );
        }
    }

    // ── 死亡/队伍覆灭 ───────────────────────────────────

    public enum TeamWipeMode { STRICT, DELAYED }

    public record DeathSection(
        boolean fatalDamageCheck,
        boolean allowTotem,
        TeamWipeMode teamWipeMode,
        int teamWipeDelayTicks
    ) {
        static DeathSection load(ConfigurationSection s) {
            if (s == null) {
                return new DeathSection(true, true, TeamWipeMode.STRICT, 60);
            }
            return new DeathSection(
                s.getBoolean("fatal-damage-check", true),
                s.getBoolean("allow-totem", true),
                TeamWipeMode.valueOf(s.getString("team-wipe-mode", "STRICT").toUpperCase()),
                s.getInt("team-wipe-delay-ticks", 60)
            );
        }
    }

    // ── 防逃逸 ──────────────────────────────────────────

    public enum EscapeMode { BLOCK, TELEPORT_BACK, FAIL }

    public record EscapeSection(
        EscapeMode mode,
        boolean blockEnderPearl,
        boolean blockChorusFruit,
        boolean blockPortals,
        boolean blockSpectateTeleport
    ) {
        static EscapeSection load(ConfigurationSection s) {
            if (s == null) {
                return new EscapeSection(EscapeMode.BLOCK, true, true, true, true);
            }
            return new EscapeSection(
                EscapeMode.valueOf(s.getString("mode", "BLOCK").toUpperCase()),
                s.getBoolean("block-ender-pearl", true),
                s.getBoolean("block-chorus-fruit", true),
                s.getBoolean("block-portal", true),
                s.getBoolean("block-spectate-teleport", true)
            );
        }
    }

    // ── 副本内行为限制 ──────────────────────────────────

    public record RestrictionsSection(
        java.util.List<String> blockedCommands,
        java.util.List<String> blockedItems,
        boolean blockBreak,
        boolean blockPlace
    ) {
        static RestrictionsSection load(ConfigurationSection s) {
            if (s == null) {
                return new RestrictionsSection(
                    java.util.List.of("spawn", "tpa", "home", "back", "warp", "tp"),
                    java.util.List.of("CHORUS_FRUIT"), false, false);
            }
            return new RestrictionsSection(
                s.getStringList("blocked-commands"),
                s.getStringList("blocked-items"),
                s.getBoolean("block-break", false),
                s.getBoolean("block-place", false)
            );
        }
    }

    // ── 队伍 ────────────────────────────────────────────

    public enum ExpShareMode { NONE, EQUAL, DISTANCE }
    public enum LootMode { FREE, ROUND_ROBIN, LEADER }

    public record PartySection(
        int maxMembers,
        int inviteTimeout,
        int storagePages,
        boolean allowCustomName,
        ExpShareMode expShareMode,
        LootMode lootMode
    ) {
        static PartySection load(ConfigurationSection s) {
            if (s == null) {
                return new PartySection(4, 60, 1, true, ExpShareMode.EQUAL, LootMode.FREE);
            }
            return new PartySection(
                s.getInt("max-members", 4),
                s.getInt("invite-timeout", 60),
                s.getInt("storage-pages", 1),
                s.getBoolean("allow-custom-name", true),
                ExpShareMode.valueOf(s.getString("exp-share-mode", "EQUAL").toUpperCase()),
                LootMode.valueOf(s.getString("loot-mode", "FREE").toUpperCase())
            );
        }
    }

    // ── 体力 ────────────────────────────────────────────

    public record StaminaSection(
        boolean enabled,
        int max,
        int regenAmount,
        int regenIntervalSeconds
    ) {
        static StaminaSection load(ConfigurationSection s) {
            if (s == null) {
                return new StaminaSection(true, 100, 1, 300);
            }
            return new StaminaSection(
                s.getBoolean("enabled", true),
                s.getInt("max", 100),
                s.getInt("regen-amount", 1),
                s.getInt("regen-interval-seconds", 300)
            );
        }
    }

    // ── 排行榜 ──────────────────────────────────────────

    public record LeaderboardSection(
        boolean enabled,
        int size,
        boolean autoShowComplete
    ) {
        static LeaderboardSection load(ConfigurationSection s) {
            if (s == null) {
                return new LeaderboardSection(true, 20, true);
            }
            return new LeaderboardSection(
                s.getBoolean("enabled", true),
                s.getInt("size", 20),
                s.getBoolean("auto-show-complete", true)
            );
        }
    }

    // ── 战斗统计 ────────────────────────────────────────

    public record StatisticsSection(
        boolean enabled,
        boolean trackDamage,
        boolean trackDamageTaken,
        boolean trackHealing,
        boolean autoShow
    ) {
        static StatisticsSection load(ConfigurationSection s) {
            if (s == null) {
                return new StatisticsSection(true, true, true, true, true);
            }
            return new StatisticsSection(
                s.getBoolean("enabled", true),
                s.getBoolean("track-damage", true),
                s.getBoolean("track-damage-taken", true),
                s.getBoolean("track-healing", true),
                s.getBoolean("auto-show", true)
            );
        }
    }

    // ── 存储 ────────────────────────────────────────────

    public record StorageSection(
        String mode, String sqliteFileName,
        String host, int port, String database,
        String username, String password, String tablePrefix, int poolSize
    ) {
        public boolean isSqlite() {
            return !"mysql".equalsIgnoreCase(mode);
        }

        public boolean hasOverride() {
            return "mysql".equalsIgnoreCase(mode);
        }

        public StorageDescriptor toDescriptor() {
            if (!isSqlite()) {
                return StorageDescriptor.mysql(
                    host, port, database, username, password, poolSize, tablePrefix);
            }
            return StorageDescriptor.sqlite(sqliteFileName);
        }

        static StorageSection load(ConfigurationSection s) {
            if (s == null) {
                return new StorageSection("sqlite", "abyss.db",
                    "127.0.0.1", 3306, "arcartxsuite", "root", "", "axs_abyss_", 8);
            }
            return new StorageSection(
                s.getString("mode", "sqlite"),
                s.getString("sqlite-file-name", "abyss.db"),
                s.getString("host", "127.0.0.1"),
                s.getInt("port", 3306),
                s.getString("database", "arcartxsuite"),
                s.getString("username", "root"),
                s.getString("password", ""),
                s.getString("table-prefix", "axs_abyss_"),
                s.getInt("pool-size", 8)
            );
        }
    }

    // ── UI ──────────────────────────────────────────────

    public record UiSection(
        boolean registerOnEnable,
        boolean overwriteUiFiles,
        String browserId,
        String confirmId,
        String lobbyId,
        String partyId,
        String chestId,
        String statsId,
        String leaderboardId,
        String hudId
    ) {
        static UiSection load(ConfigurationSection s) {
            if (s == null) {
                return new UiSection(true, false,
                    "abyss_browser", "abyss_confirm", "abyss_lobby",
                    "abyss_party", "abyss_chest", "abyss_stats",
                    "abyss_leaderboard", "abyss_hud");
            }
            return new UiSection(
                s.getBoolean("register-on-enable", true),
                s.getBoolean("overwrite-ui-files", false),
                s.getString("browser-id", "abyss_browser"),
                s.getString("confirm-id", "abyss_confirm"),
                s.getString("lobby-id", "abyss_lobby"),
                s.getString("party-id", "abyss_party"),
                s.getString("chest-id", "abyss_chest"),
                s.getString("stats-id", "abyss_stats"),
                s.getString("leaderboard-id", "abyss_leaderboard"),
                s.getString("hud-id", "abyss_hud")
            );
        }
    }
}
