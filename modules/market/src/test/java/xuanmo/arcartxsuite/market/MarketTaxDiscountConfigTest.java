package xuanmo.arcartxsuite.market;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration;

class MarketTaxDiscountConfigTest {
    @Test
    void preservesDottedPermissionNamesAndIgnoresInvalidRates() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("""
            auction:
              tax-discount:
                axsmarket.testdiscount: 0.05
                axsmarket.invalid: invalid
                axsmarket.free: 0
            player-shop:
              tax-discount:
                axsmarket.shop.discount: 0.1
            """);

        var loaded = MarketModuleConfiguration.load(config, new YamlConfiguration(), Logger.getAnonymousLogger());

        assertEquals(Map.of("axsmarket.testdiscount", 0.05, "axsmarket.free", 0.0), loaded.auction().taxDiscount());
        assertEquals(Map.of("axsmarket.shop.discount", 0.1), loaded.playerShop().taxDiscount());
    }
}
