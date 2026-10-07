package xuanmo.arcartxsuite.abyss.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/**
 * shops.yml 解析：副本内商店（金币结算 + 商品脚本）。
 *
 * <pre>
 * shops:
 *   caravan:
 *     title: "旅行商队"
 *     currency-var: coins          # 结算用实例变量（缺省 coins）
 *     goods:
 *       - id: heal
 *         name: "&a治疗药剂"
 *         icon: "material:GOLDEN_APPLE"   # material:X / item:X / 裸 MATERIAL
 *         desc: ["&7恢复 12 点生命", "&75 秒再生"]
 *         price: 8
 *         script:                      # 购买成功执行（@trigger=购买者）
 *           - "heal @trigger 12"
 *           - "apply_potion @trigger REGENERATION 1 5"
 * </pre>
 */
public record ShopConfig(
    Map<String, Shop> shops
) {
    public static final ShopConfig EMPTY = new ShopConfig(Map.of());

    public record Shop(String id, String title, String currencyVar, List<Goods> goods) { }

    public record Goods(String id, String icon, String name, List<String> desc,
                        int price, List<String> script) { }

    public @Nullable Shop shop(String id) {
        return shops.get(id);
    }

    public static ShopConfig load(@Nullable ConfigurationSection root) {
        if (root == null) return EMPTY;
        ConfigurationSection shops = root.getConfigurationSection("shops");
        if (shops == null) {
            // 顶层直接是商店表也兼容
            shops = root;
        }
        Map<String, Shop> map = new LinkedHashMap<>();
        for (String id : shops.getKeys(false)) {
            ConfigurationSection s = shops.getConfigurationSection(id);
            if (s == null) continue;
            List<Goods> goods = new ArrayList<>();
            for (Map<?, ?> g : s.getMapList("goods")) {
                goods.add(new Goods(
                    str(g.get("id"), "goods_" + goods.size()),
                    str(g.get("icon"), "STONE"),
                    str(g.get("name"), "商品"),
                    strList(g.get("desc")),
                    num(g.get("price"), 0),
                    strList(g.get("script"))));
            }
            map.put(id, new Shop(id,
                s.getString("title", id),
                s.getString("currency-var", s.getString("currency_var", "coins")),
                goods));
        }
        return new ShopConfig(map);
    }

    private static String str(Object o, String def) {
        return o != null ? String.valueOf(o) : def;
    }

    private static int num(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private static List<String> strList(Object o) {
        if (o instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            for (Object e : l) out.add(String.valueOf(e));
            return out;
        }
        return List.of();
    }
}
