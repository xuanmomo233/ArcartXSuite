package xuanmo.arcartxsuite.warehouse.service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarehouseUiContractTest {
    static final Set<String> WRITES = Set.of("withdraw","withdraw_quick","withdraw_all","deposit_slot","deposit_all_backpack","toggle_lock","shared_mode",
        "bank_deposit","bank_withdraw","fixed_create","fixed_claim","fixed_delete","warehouse_upgrade","buy_slots","shared_create","shared_delete",
        "shared_rename","personal_rename","shared_transfer","shared_transfer_confirm","shared_transfer_reject","shared_invite","shared_remove",
        "shared_showcase_toggle","personal_showcase_toggle","toggle_auto_pickup","toggle_auto_mythic","toggle_auto_notify","password_set","password_lock");
    @Test void callbacksAndTokensMatchAllThreeWarehouseScreens() throws Exception {
        for(String file : List.of("warehouse_storage.yml","warehouse_manage.yml","warehouse_bank.yml")) {
            String source;
            try(var stream=Objects.requireNonNull(getClass().getResourceAsStream("/arcartx/ui/"+file))) {source=new String(stream.readAllBytes(),StandardCharsets.UTF_8);}
            YamlConfiguration yaml=new YamlConfiguration();yaml.loadFromString(source);
            assertNotNull(yaml.getConfigurationSection("ui.action"),file);
            assertNotNull(yaml.getString("ui.packetHandler.update"),file);
            Set<String> references=new HashSet<>(),definitions=new HashSet<>();
            collectVals(yaml,definitions);
            Matcher vals=Pattern.compile("val\\.([\\p{L}\\p{N}_]+)").matcher(source);
            while(vals.find()) references.add(vals.group(1));
            references.removeAll(definitions);
            assertTrue(references.isEmpty(),file+" 未绑定的控件引用: "+references);
            Matcher calls=Pattern.compile("Packet\\.send\\([^\\r\\n]*").matcher(source);
            while(calls.find()) {
                String call=calls.group();
                Matcher action=Pattern.compile("'([a-z_]+)'").matcher(call);
                if(action.find() && WRITES.contains(action.group(1))) assertTrue(call.contains("var.actionToken"),file+" 缺少令牌: "+call);
            }
            assertFalse(Pattern.compile("\\.round(?!\\s*\\()").matcher(source).find(),file+" round 必须调用函数");
        }
    }
    private static void collectVals(ConfigurationSection section,Set<String> result) {
        for(String key:section.getKeys(false)) {
            if(key.equals("val")) result.add(section.getString(key));
            ConfigurationSection child=section.getConfigurationSection(key);
            if(child!=null) collectVals(child,result);
        }
    }
}
