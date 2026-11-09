package xuanmo.arcartxsuite.vanilla.recipe;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.TradeSelectEvent;
import java.lang.reflect.Method;

/** Selects a native offer without vanilla's automatic backpack swapping. */
public final class MerchantSelection {
    private MerchantSelection() { }
    public static boolean select(Player player,int index) {
        var view=player.getOpenInventory();
        if(!WorkshopType.MERCHANT.matches(view) || index<0)return false;
        try {
            Object menu=view.getClass().getMethod("getHandle").invoke(view);
            Method setter=null;
            for(String name:new String[]{"setSelectionHint","e"})try{setter=menu.getClass().getMethod(name,int.class);break;}catch(NoSuchMethodException ignored){ }
            if(setter==null)return false;
            TradeSelectEvent event=new TradeSelectEvent(view,index);
            Bukkit.getPluginManager().callEvent(event);
            if(event.isCancelled() || player.getOpenInventory()!=view)return false;
            setter.invoke(menu,index);
            player.updateInventory();return true;
        }catch(ReflectiveOperationException|RuntimeException e){return false;}
    }
}
