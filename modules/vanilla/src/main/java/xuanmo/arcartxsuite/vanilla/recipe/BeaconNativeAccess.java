package xuanmo.arcartxsuite.vanilla.recipe;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.entity.Player;

/** All effect writes and payment consumption go through the native beacon menu. */
public final class BeaconNativeAccess {
    public record State(int tier,int primary,int secondary,boolean payment) { }
    private record Access(Method handle,Method tier,Method primary,Method secondary,Method payment,Method apply,Method effectId,Method effectById) { }
    private final Map<Class<?>,Access> cache=new HashMap<>();
    private Access access(Player player)throws ReflectiveOperationException{
        Class<?> view=player.getOpenInventory().getClass();Access cached=cache.get(view);if(cached!=null)return cached;
        Method handle=view.getMethod("getHandle");Class<?> menu=handle.invoke(player.getOpenInventory()).getClass();
        Class<?> effect=Class.forName("net.minecraft.world.effect.MobEffectList");
        Method id=effect.getMethod("a",effect),byId=effect.getMethod("a",int.class);
        Access a=new Access(handle,menu.getMethod("l"),menu.getMethod("m"),menu.getMethod("n"),menu.getMethod("o"),menu.getMethod("a",Optional.class,Optional.class),id,byId);cache.put(view,a);return a;
    }
    private static int id(Access a,Object value)throws ReflectiveOperationException{return value==null?-1:((Number)a.effectId.invoke(null,value)).intValue();}
    public State state(Player player){
        try{Access a=access(player);Object menu=a.handle.invoke(player.getOpenInventory());return new State(((Number)a.tier.invoke(menu)).intValue(),id(a,a.primary.invoke(menu)),id(a,a.secondary.invoke(menu)),(boolean)a.payment.invoke(menu));}
        catch(ReflectiveOperationException|RuntimeException e){return null;}
    }
    public boolean apply(Player player,int primary,int secondary){
        try{
            Access a=access(player);Object menu=a.handle.invoke(player.getOpenInventory()),p=a.effectById.invoke(null,primary),s=secondary<0?null:a.effectById.invoke(null,secondary);
            if(p==null||secondary>=0&&s==null)return false;
            a.apply.invoke(menu,Optional.of(p),Optional.ofNullable(s));return true;
        }catch(ReflectiveOperationException|RuntimeException e){return false;}
    }
}
