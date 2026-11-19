package xuanmo.arcartxsuite.regions.config;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.RegionFlag;

public final class RegionTemplates {

    private final Map<String, Map<RegionFlag, Setting>> templates = new TreeMap<>();

    public RegionTemplates(ConfigurationSection section) {
        if (section == null) return;
        for (String name : section.getKeys(false)) {
            ConfigurationSection flags = section.getConfigurationSection(name);
            if (flags == null || !name.matches("[a-z0-9_-]{1,64}")) {
                throw new IllegalArgumentException("无效的区域模板 " + name);
            }
            Map<RegionFlag, Setting> settings = new EnumMap<>(RegionFlag.class);
            for (String key : flags.getKeys(false)) {
                RegionFlag flag = RegionFlag.fromKey(key);
                if (flag == null) throw new IllegalArgumentException("区域模板 " + name + " 包含未知标志 " + key);
                ConfigurationSection fields = flags.getConfigurationSection(key);
                String value = fields == null ? flags.getString(key) : fields.getString("state");
                String text = fields == null ? null : fields.getString("text");
                if (text != null && flag != RegionFlag.GREETING && flag != RegionFlag.FAREWELL) {
                    throw new IllegalArgumentException("区域模板 " + name + " 的 " + key + " 不支持文本");
                }
                settings.put(flag, new Setting(RegionFlag.State.parse(value), text));
            }
            templates.put(name, Map.copyOf(settings));
        }
    }

    public Set<String> names() { return Set.copyOf(templates.keySet()); }

    public Map<RegionFlag, Setting> get(String name, boolean admin) {
        var flags = templates.get(name.toLowerCase(Locale.ROOT));
        if (flags == null) throw new IllegalArgumentException("未知区域模板 " + name + "。");
        if (!admin && flags.keySet().stream().anyMatch(flag -> !flag.claimEditable())) {
            throw new IllegalArgumentException("此区域模板需要管理员权限。");
        }
        return flags;
    }

    public static void apply(Region region, Map<RegionFlag, Setting> settings) {
        settings.forEach((flag, value) -> flag.apply(region, value.state(), value.text()));
    }

    public record Setting(RegionFlag.State state, String text) {}
}
