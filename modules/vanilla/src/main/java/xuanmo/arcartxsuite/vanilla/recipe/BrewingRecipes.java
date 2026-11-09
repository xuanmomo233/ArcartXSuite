package xuanmo.arcartxsuite.vanilla.recipe;

import xuanmo.arcartxsuite.api.inventory.ItemSnapshots;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import java.lang.reflect.Method;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

/** Read-only native mix lookup includes Paper's registered custom potion mixes. */
public final class BrewingRecipes {
    public record Mix(String key,ItemStack input,ItemStack ingredient,ItemStack result) { }
    private Method toNative,toBukkit,hasMix,mix,isIngredient,isCustomInput,isCustomIngredient;
    private List<Mix> defaults;
    public BrewingRecipes() {
        try {
            String craft=Bukkit.getServer().getClass().getPackageName();
            Class<?> items=Class.forName(craft+".inventory.CraftItemStack");
            toNative=items.getMethod("asNMSCopy",ItemStack.class);
            Class<?> nativeItem=toNative.getReturnType();
            toBukkit=items.getMethod("asBukkitCopy",nativeItem);
            Class<?> brewer=Class.forName("net.minecraft.world.item.alchemy.PotionBrewer");
            hasMix=brewer.getMethod("a",nativeItem,nativeItem);
            mix=brewer.getMethod("d",nativeItem,nativeItem);
            isIngredient=brewer.getMethod("a",nativeItem);
            try{isCustomInput=brewer.getMethod("isCustomInput",nativeItem);isCustomIngredient=brewer.getMethod("isCustomIngredient",nativeItem);}catch(NoSuchMethodException ignored){ }
        }catch(ReflectiveOperationException e){toNative=null;}
    }
    public boolean available(){return toNative!=null;}
    public ItemStack result(ItemStack input,ItemStack ingredient) {
        if(!available())return null;
        try {
            Object a=toNative.invoke(null,input),b=toNative.invoke(null,ingredient);
            if(!(boolean)hasMix.invoke(null,a,b))return null;
            ItemStack result=(ItemStack)toBukkit.invoke(null,mix.invoke(null,b,a));
            if(InventoryItems.empty(result) || result.isSimilar(input))return null;
            result.setAmount(1);return result;
        }catch(ReflectiveOperationException|RuntimeException e){return null;}
    }
    private boolean accepts(Method method,ItemStack item){try{return method!=null && (boolean)method.invoke(null,toNative.invoke(null,item));}catch(ReflectiveOperationException|RuntimeException e){return false;}}
    private Mix row(ItemStack input,ItemStack ingredient,ItemStack result){return new Mix(ItemSnapshots.hash(ItemSnapshots.encode(input)+":"+ItemSnapshots.encode(ingredient)+":"+ItemSnapshots.encode(result)),input.clone(),ingredient.clone(),result.clone());}
    public List<Mix> collect(Collection<ItemStack> actual) {
        if(!available())return List.of();
        if(defaults==null){
            List<Mix> list=new ArrayList<>();
            List<ItemStack> inputs=new ArrayList<>();
            for(Material material:List.of(Material.POTION,Material.SPLASH_POTION,Material.LINGERING_POTION))for(PotionType type:PotionType.values()){
                if(type==PotionType.UNCRAFTABLE)continue;
                for(int variant=0;variant<3;variant++){
                    if(variant==1&&!type.isExtendable() || variant==2&&!type.isUpgradeable())continue;
                    ItemStack item=new ItemStack(material);PotionMeta meta=(PotionMeta)item.getItemMeta();
                    meta.setBasePotionData(new PotionData(type,variant==1,variant==2));item.setItemMeta(meta);inputs.add(item);
                }
            }
            for(ItemStack input:inputs)for(Material material:List.of(Material.NETHER_WART,Material.REDSTONE,Material.GLOWSTONE_DUST,Material.FERMENTED_SPIDER_EYE,Material.SUGAR,Material.RABBIT_FOOT,Material.BLAZE_POWDER,Material.MAGMA_CREAM,Material.GLISTERING_MELON_SLICE,Material.SPIDER_EYE,Material.GHAST_TEAR,Material.PUFFERFISH,Material.GOLDEN_CARROT,Material.TURTLE_HELMET,Material.PHANTOM_MEMBRANE,Material.GUNPOWDER,Material.DRAGON_BREATH)){
                ItemStack ingredient=new ItemStack(material),result=result(input,ingredient);
                if(result!=null)list.add(row(input,ingredient,result));
            }
            defaults=List.copyOf(list);
        }
        Map<String,Mix> rows=new LinkedHashMap<>();
        for(Mix row:defaults){ItemStack current=result(row.input(),row.ingredient());if(current!=null && current.isSimilar(row.result()))rows.put(row.key(),row);}
        List<ItemStack> inputs=new ArrayList<>(),ingredients=new ArrayList<>();
        for(ItemStack item:actual){
            if(InventoryItems.empty(item))continue;
            ItemStack one=item.clone();one.setAmount(1);
            if((one.getItemMeta() instanceof PotionMeta || accepts(isCustomInput,one)) && inputs.stream().noneMatch(one::isSimilar))inputs.add(one);
            if((accepts(isIngredient,one)||accepts(isCustomIngredient,one)) && ingredients.stream().noneMatch(one::isSimilar))ingredients.add(one);
        }
        for(ItemStack input:inputs)for(ItemStack ingredient:ingredients){ItemStack output=result(input,ingredient);if(output!=null){Mix row=row(input,ingredient,output);rows.put(row.key(),row);}}
        return List.copyOf(rows.values());
    }
}
