package xuanmo.arcartxsuite.abyss.placeholder;

import java.util.Locale;
import java.util.function.Supplier;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.party.Party;
import xuanmo.arcartxsuite.abyss.service.AbyssService;

/**
 * Abyss 模块 PlaceholderAPI 扩展：{@code %axsabyss_*}%
 * <p>
 * 变量：
 * stamina / stamina_max / in_dungeon / dungeon_id / dungeon_name /
 * dungeon_time / deaths / alive_count / party_size / party_name /
 * party_leader / cooldown_<地牢> / completions_<地牢> / best_time_<地牢>
 */
public final class AbyssPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Supplier<AbyssService> serviceProvider;

    public AbyssPlaceholderExpansion(JavaPlugin plugin, Supplier<AbyssService> serviceProvider) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
    }

    @Override public @NotNull String getIdentifier() { return "axsabyss"; }
    @Override public @NotNull String getAuthor() { return "arcartxsuite"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) return "";
        AbyssService service = serviceProvider.get();
        if (service == null) return "";
        String p = params.toLowerCase(Locale.ROOT);
        Player player = offlinePlayer.getPlayer();

        switch (p) {
            case "stamina":
                return String.valueOf(service.stamina().getStamina(offlinePlayer.getUniqueId()));
            case "stamina_max":
                return String.valueOf(service.stamina().max());
            case "in_dungeon":
                return String.valueOf(player != null && service.instances().getInstance(player) != null);
            case "dungeon_id": {
                AbyssInstance i = player != null ? service.instances().getInstance(player) : null;
                return i != null ? i.getTemplate().id() : "";
            }
            case "dungeon_name": {
                AbyssInstance i = player != null ? service.instances().getInstance(player) : null;
                return i != null ? i.getTemplate().name() : "";
            }
            case "dungeon_time": {
                AbyssInstance i = player != null ? service.instances().getInstance(player) : null;
                return i != null ? String.valueOf(i.getRunningTime()) : "0";
            }
            case "deaths": {
                AbyssInstance i = player != null ? service.instances().getInstance(player) : null;
                return i != null ? String.valueOf(i.getDeaths(player.getUniqueId())) : "0";
            }
            case "alive_count": {
                AbyssInstance i = player != null ? service.instances().getInstance(player) : null;
                return i != null ? String.valueOf(i.getAlivePlayerCount()) : "0";
            }
            case "dungeon_kills": {
                AbyssInstance i = player != null ? service.instances().getInstance(player) : null;
                return i != null ? String.valueOf(
                    i.getKillCounts().getOrDefault(player.getUniqueId(), 0)) : "0";
            }
            case "dungeon_total_kills": {
                AbyssInstance i = player != null ? service.instances().getInstance(player) : null;
                return i != null ? String.valueOf(
                    i.getKillCounts().values().stream().mapToInt(Integer::intValue).sum()) : "0";
            }
            case "party_size": {
                Party party = player != null ? service.party().partyOf(player) : null;
                return String.valueOf(party != null ? party.size() : 0);
            }
            case "party_name": {
                Party party = player != null ? service.party().partyOf(player) : null;
                return party != null ? party.name() : "";
            }
            case "party_leader": {
                Party party = player != null ? service.party().partyOf(player) : null;
                if (party == null) return "";
                Player leader = Bukkit.getPlayer(party.leader());
                return leader != null ? leader.getName() : party.leader().toString().substring(0, 8);
            }
            default: break;
        }
        if (p.startsWith("cooldown_")) {
            String dungeon = p.substring("cooldown_".length());
            return String.valueOf(service.cooldowns().remaining(offlinePlayer.getUniqueId(), dungeon));
        }
        if (p.startsWith("completions_")) {
            String dungeon = p.substring("completions_".length());
            long[] r = service.playerData().loadRecord(offlinePlayer.getUniqueId(), dungeon);
            return String.valueOf(r != null ? r[0] : 0);
        }
        if (p.startsWith("best_time_")) {
            String dungeon = p.substring("best_time_".length());
            long[] r = service.playerData().loadRecord(offlinePlayer.getUniqueId(), dungeon);
            return String.valueOf(r != null ? r[1] : 0);
        }
        return "";
    }
}
