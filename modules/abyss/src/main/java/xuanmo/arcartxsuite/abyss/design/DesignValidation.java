package xuanmo.arcartxsuite.abyss.design;

import java.io.IOException;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

/** Compatibility entry point; all rules live in the shared template validator. */
final class DesignValidation {
    private DesignValidation() { }
    static void validate(Map<String,YamlConfiguration> files) throws IOException {
        xuanmo.arcartxsuite.abyss.template.AbyssValidation.validate(files);
    }
}
