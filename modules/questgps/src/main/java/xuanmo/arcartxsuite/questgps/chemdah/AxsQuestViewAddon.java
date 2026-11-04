package xuanmo.arcartxsuite.questgps.chemdah;

import ink.ptms.chemdah.core.quest.Option;
import ink.ptms.chemdah.core.quest.QuestContainer;
import ink.ptms.chemdah.core.quest.addon.Addon;
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;

/** Native Chemdah component; both templates and individual tasks can declare it. */
@Option(type = Option.Type.SECTION)
public final class AxsQuestViewAddon extends Addon {
    public static final String ID = "axs-view";
    private final org.bukkit.configuration.ConfigurationSection view;

    public AxsQuestViewAddon(ConfigurationSection root, QuestContainer container) {
        super(root, container);
        view = ChemdahConfigAccessor.fromRaw(root);
        if (view == null) throw new IllegalArgumentException("无法读取 Chemdah addon.axs-view: " + container.getId());
    }

    org.bukkit.configuration.ConfigurationSection view() {
        return view;
    }
}
