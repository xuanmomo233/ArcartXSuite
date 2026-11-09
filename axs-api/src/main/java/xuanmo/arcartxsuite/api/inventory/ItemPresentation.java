package xuanmo.arcartxsuite.api.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;

/** UI representation of a single item without changing its stack. */
public final class ItemPresentation {
    private final ItemBridgeAPI items;
    private final VanillaItemNameBridge names;

    public ItemPresentation(ItemBridgeAPI items, VanillaItemNameBridge names) {
        this.items = items;
        this.names = names;
    }

    public String icon(ItemStack item){
        if(InventoryItems.empty(item))return "{\"id\":\"minecraft:air\",\"Count\":1b}";
        ItemStack one=item.clone();one.setAmount(1);
        if(items!=null&&items.isAvailable()){var json=items.itemToJson(one);if(json.isPresent())return json.get();}
        return "{\"id\":\""+one.getType().getKey()+"\",\"Count\":1b}";
    }
    public String name(ItemStack item){
        if(InventoryItems.empty(item))return "";
        if(item.hasItemMeta()&&item.getItemMeta().hasDisplayName())return org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if(item.getItemMeta() instanceof PotionMeta meta){
            var data=meta.getBasePotionData();
            String effect=switch(data.getType().name()){
                case "WATER"->"水瓶";case "AWKWARD"->"粗制药水";case "MUNDANE"->"平凡药水";case "THICK"->"浓稠药水";
                case "NIGHT_VISION"->"夜视药水";case "INVISIBILITY"->"隐身药水";case "JUMP"->"跳跃药水";
                case "FIRE_RESISTANCE"->"抗火药水";case "SPEED"->"迅捷药水";case "SLOWNESS"->"迟缓药水";
                case "WATER_BREATHING"->"水肺药水";case "INSTANT_HEAL"->"治疗药水";case "INSTANT_DAMAGE"->"伤害药水";
                case "POISON"->"剧毒药水";case "REGEN"->"再生药水";case "STRENGTH"->"力量药水";
                case "WEAKNESS"->"虚弱药水";case "TURTLE_MASTER"->"神龟药水";case "SLOW_FALLING"->"缓降药水";default->data.getType().name();
            };
            return (item.getType()==Material.SPLASH_POTION?"喷溅·":item.getType()==Material.LINGERING_POTION?"滞留·":"")+effect+(data.isUpgraded()?" II":data.isExtended()?"（延长）":"");
        }
        return names!=null?names.translate(item.getType()):item.getType().name();
    }
}
