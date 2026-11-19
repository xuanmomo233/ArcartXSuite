package xuanmo.arcartxsuite.vanilla.recipe;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;

/** The native loom owns its available patterns, selection events and result. */
public final class ArtisanNativeAccess {
    public record Choice(int index,PatternType pattern,String code) { }

    public static final class Loom {
        private final List<?> nativePatterns;
        private final List<Choice> choices;
        private final int selected;

        private Loom(List<?> nativePatterns, List<Choice> choices, int selected) {
            this.nativePatterns = nativePatterns;
            this.choices = choices;
            this.selected = selected;
        }

        public List<Choice> choices() { return choices; }
        public int selected() { return selected; }
    }
    private record Access(Method handle,Method patterns,Method selected,Method button,Method holderValue,Method hash) { }
    private final Map<Class<?>,Access> cache=new HashMap<>();
    private Access access(Player player)throws ReflectiveOperationException{
        Class<?> view=player.getOpenInventory().getClass();Access a=cache.get(view);if(a!=null)return a;
        Method handle=view.getMethod("getHandle");Object menu=handle.invoke(player.getOpenInventory());Class<?> type=menu.getClass();
        Method patterns=method(type,List.class,new String[]{"getSelectablePatterns","l"});
        Method selected=method(type,int.class,new String[]{"getSelectedBannerPatternIndex","m"});
        Method button=null;Object actor=player.getClass().getMethod("getHandle").invoke(player);
        for(Method m:type.getMethods())if(List.of("clickMenuButton","b").contains(m.getName())&&m.getReturnType()==boolean.class&&m.getParameterCount()==2&&m.getParameterTypes()[0].isInstance(actor)&&m.getParameterTypes()[1]==int.class){button=m;break;}
        if(button==null)throw new NoSuchMethodException("loom button");
        Class<?> holder=Class.forName("net.minecraft.core.Holder"),pattern=Class.forName("net.minecraft.world.level.block.entity.EnumBannerPatternType");
        Method value=holder.getMethod("a"),hash=method(pattern,String.class,new String[]{"getHashname","a"});
        a=new Access(handle,patterns,selected,button,value,hash);cache.put(view,a);return a;
    }
    private static Method method(Class<?> type,Class<?> result,String[] names)throws NoSuchMethodException{
        for(String name:names)try{Method m=type.getMethod(name);if(m.getReturnType()==result||result.isAssignableFrom(m.getReturnType()))return m;}catch(NoSuchMethodException ignored){ }
        throw new NoSuchMethodException(type.getName()+Arrays.toString(names));
    }
    public Loom loom(Player player, Loom previous) {
        try {
            Access access = access(player);
            Object menu = access.handle().invoke(player.getOpenInventory());
            List<?> patterns = (List<?>) access.patterns().invoke(menu);
            int selected = (int) access.selected().invoke(menu);
            return snapshotLoom(patterns, selected, previous, access.holderValue(), access.hash());
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    static Loom snapshotLoom(List<?> patterns, int selected, Loom previous, Method holderValue, Method patternCode)
            throws ReflectiveOperationException {
        if (previous != null && previous.nativePatterns.equals(patterns)) {
            if (previous.selected == selected) return previous;
            return new Loom(previous.nativePatterns, previous.choices, selected);
        }

        List<Choice> choices = new ArrayList<>(patterns.size());
        int index = 0;
        for (Object holder : patterns) {
            Object pattern = holderValue.invoke(holder);
            String code = (String) patternCode.invoke(pattern);
            choices.add(new Choice(index++, PatternType.getByIdentifier(code), code));
        }
        return new Loom(List.copyOf(patterns), List.copyOf(choices), selected);
    }
    public boolean select(Player player,int index){
        try{Access a=access(player);Object menu=a.handle.invoke(player.getOpenInventory()),actor=player.getClass().getMethod("getHandle").invoke(player);return (boolean)a.button.invoke(menu,actor,index);}
        catch(ReflectiveOperationException|RuntimeException e){return false;}
    }
}
