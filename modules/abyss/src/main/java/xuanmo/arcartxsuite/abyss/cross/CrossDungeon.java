package xuanmo.arcartxsuite.abyss.cross;

import java.util.List;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;

public interface CrossDungeon {
    void start();
    void stop();
    boolean active();
    void requestRemoteEnter(List<Player> players, AbyssTemplate template, String plan);
    void onPlayerJoin(Player player);
    void onPlayerQuit(Player player);
}
