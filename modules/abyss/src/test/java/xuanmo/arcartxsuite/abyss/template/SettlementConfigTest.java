package xuanmo.arcartxsuite.abyss.template;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettlementConfigTest {
    @Test void immediateCountdownAndManualHaveIndependentDeadlines()throws Exception {
        var yaml=new YamlConfiguration();yaml.loadFromString("exit-mode: COUNTDOWN\nexit-delay-seconds: 45\nfailure:\n  exit-mode: IMMEDIATE\n");
        var config=SettlementConfig.load(yaml);assertEquals(46_000,config.complete().deadline(1000));
        assertEquals(45,config.complete().remaining(1000,46_000));assertEquals(0,config.complete().remaining(50_000,46_000));
        assertEquals(1000,config.failure().deadline(1000));
        yaml.set("exit-mode","MANUAL");yaml.set("manual-timeout-seconds",0);
        assertEquals(Long.MAX_VALUE,SettlementConfig.load(yaml).complete().deadline(1000));
        yaml.set("manual-timeout-seconds",120);assertEquals(121_000,SettlementConfig.load(yaml).complete().deadline(1000));
    }
    @Test void malformedPoliciesAreRejectedAndMissingConfigurationKeepsLootTime() {
        assertEquals(60,SettlementConfig.load(null).complete().seconds());
        var yaml=new YamlConfiguration();yaml.set("exit-mode","invalid");assertThrows(IllegalArgumentException.class,()->SettlementConfig.load(yaml));
        yaml.set("exit-mode","COUNTDOWN");yaml.set("exit-delay-seconds",-1);assertThrows(IllegalArgumentException.class,()->SettlementConfig.load(yaml));
    }
}
