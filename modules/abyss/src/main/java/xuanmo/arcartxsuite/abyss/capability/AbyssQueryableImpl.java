package xuanmo.arcartxsuite.abyss.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.api.capability.AbyssQueryable;

/**
 * {@link AbyssQueryable} 实现：委托 {@link AbyssService}。
 */
public final class AbyssQueryableImpl implements AbyssQueryable {

    private final AbyssService service;

    public AbyssQueryableImpl(AbyssService service) {
        this.service = service;
    }

    private @Nullable AbyssInstance instanceOf(UUID player) {
        Player p = Bukkit.getPlayer(player);
        return p != null ? service.instances().getInstance(p) : null;
    }

    @Override
    public boolean isInDungeon(UUID player) {
        return instanceOf(player) != null;
    }

    @Override
    public @Nullable String currentDungeonId(UUID player) {
        AbyssInstance i = instanceOf(player);
        return i != null ? i.getTemplate().id() : null;
    }

    @Override
    public long currentRunningSeconds(UUID player) {
        AbyssInstance i = instanceOf(player);
        return i != null ? i.getRunningTime() : 0;
    }

    @Override
    public long cooldownRemaining(UUID player, String dungeonId) {
        return service.cooldowns().remaining(player, dungeonId);
    }

    @Override
    public int stamina(UUID player) {
        return service.stamina().enabled()
            ? service.stamina().getStamina(player) : -1;
    }

    @Override
    public long completions(UUID player, String dungeonId) {
        long[] r = service.playerData().loadRecord(player, dungeonId);
        return r != null ? r[0] : 0;
    }

    @Override
    public long bestTime(UUID player, String dungeonId) {
        long[] r = service.playerData().loadRecord(player, dungeonId);
        return r != null ? r[1] : 0;
    }

    @Override
    public List<String> dungeonIds() {
        return service.instances().getAllTemplates().stream()
            .map(xuanmo.arcartxsuite.abyss.template.AbyssTemplate::id).toList();
    }
}
