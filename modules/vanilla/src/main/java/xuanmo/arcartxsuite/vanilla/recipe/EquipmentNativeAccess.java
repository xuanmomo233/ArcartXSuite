package xuanmo.arcartxsuite.vanilla.recipe;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;

/** Paper 1.20.1 lacks public rename and enchanting-button setters. Native calls
 * stay here; XP, enchantment rolls and Bukkit events remain in the native menu. */
public final class EquipmentNativeAccess {
    public record Offers(int[] costs,int[] clues,int[] levels,int seed) { }
    private record EnchantAccess(Field costs,Field clues,Field levels,Method seed,Method button) { }
    private final Map<Class<?>,Method> handles=new HashMap<>(),rename=new HashMap<>();
    private final Map<Class<?>,EnchantAccess> enchanting=new HashMap<>();
    private final Map<Integer,String> hintKeys=new HashMap<>();
    private Object menu(InventoryView view)throws ReflectiveOperationException{
        Method method=handles.get(view.getClass());
        if(method==null){method=view.getClass().getMethod("getHandle");handles.put(view.getClass(),method);}
        return method.invoke(view);
    }
    public boolean rename(InventoryView view,String text){
        try{
            Object menu=menu(view);Method method=rename.get(menu.getClass());
            if(method==null){for(String name:List.of("setItemName","a"))try{method=menu.getClass().getMethod(name,String.class);break;}catch(NoSuchMethodException ignored){ }
                if(method==null)return false;rename.put(menu.getClass(),method);}
            method.invoke(menu,text);return true;
        }catch(ReflectiveOperationException|RuntimeException e){return false;}
    }
    public boolean canUse(Player player){
        try{
            Object menu=menu(player.getOpenInventory()),actor=player.getClass().getMethod("getHandle").invoke(player);
            for(Method method:menu.getClass().getMethods())if(List.of("stillValid","a").contains(method.getName())&&method.getReturnType()==boolean.class&&method.getParameterCount()==1&&method.getParameterTypes()[0].isInstance(actor))return (boolean)method.invoke(menu,actor);
        }catch(ReflectiveOperationException|RuntimeException ignored){ }
        return false;
    }
    private static Field field(Class<?> type,String... names)throws NoSuchFieldException{
        for(String name:names)try{return type.getField(name);}catch(NoSuchFieldException ignored){ }
        throw new NoSuchFieldException(Arrays.toString(names));
    }
    private EnchantAccess access(Object menu,Player player)throws ReflectiveOperationException{
        EnchantAccess cached=enchanting.get(menu.getClass());if(cached!=null)return cached;
        Method seed=null,button=null;Class<?> playerType=player.getClass().getMethod("getHandle").getReturnType();
        for(String name:List.of("getEnchantmentSeed","m"))try{seed=menu.getClass().getMethod(name);break;}catch(NoSuchMethodException ignored){ }
        for(Method method:menu.getClass().getMethods())if(List.of("clickMenuButton","b").contains(method.getName())&&method.getReturnType()==boolean.class&&method.getParameterCount()==2&&method.getParameterTypes()[1]==int.class&&method.getParameterTypes()[0].isAssignableFrom(playerType)){button=method;break;}
        if(seed==null||button==null)throw new NoSuchMethodException("enchanting menu methods");
        cached=new EnchantAccess(field(menu.getClass(),"costs","k"),field(menu.getClass(),"enchantClue","l"),field(menu.getClass(),"levelClue","m"),seed,button);enchanting.put(menu.getClass(),cached);return cached;
    }
    public Offers offers(Player player){
        try{Object menu=menu(player.getOpenInventory());EnchantAccess a=access(menu,player);return new Offers(Arrays.copyOf((int[])a.costs().get(menu),3),Arrays.copyOf((int[])a.clues().get(menu),3),Arrays.copyOf((int[])a.levels().get(menu),3),(int)a.seed().invoke(menu));}
        catch(ReflectiveOperationException|RuntimeException e){return null;}
    }
    public boolean enchant(Player player,int index){
        if(index<0||index>2)return false;
        try{Object menu=menu(player.getOpenInventory());EnchantAccess a=access(menu,player);Object nativePlayer=player.getClass().getMethod("getHandle").invoke(player);return (boolean)a.button().invoke(menu,nativePlayer,index);}
        catch(ReflectiveOperationException|RuntimeException e){return false;}
    }
    public String hint(int id){
        if(id<0)return "隐藏附魔";String cached=hintKeys.get(id);if(cached!=null)return cached;
        try{
            Class<?> nms=Class.forName("net.minecraft.world.item.enchantment.Enchantment");Object value=nms.getMethod("c",int.class).invoke(null,id);
            if(value==null)return "隐藏附魔";
            Class<?> craft=Class.forName(Bukkit.getServer().getClass().getPackageName()+".enchantments.CraftEnchantment");Method raw=craft.getMethod("getRaw",Enchantment.class);
            for(Enchantment enchantment:Enchantment.values())if(raw.invoke(null,enchantment)==value){String key=enchantment.getKey().toString();hintKeys.put(id,key);return key;}
        }catch(ReflectiveOperationException|RuntimeException ignored){ }
        return "隐藏附魔";
    }
}
