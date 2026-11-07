package xuanmo.arcartxsuite.vanilla.recipe;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.util.io.BukkitObjectOutputStream;
import xuanmo.arcartxsuite.api.capability.MaterialContainerAdapter;
import xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess;

/** Native container adapters never claim atomic persistence across player/world files. */
public final class NativeMaterialContainerAdapter implements MaterialContainerAdapter {
    private final boolean furnace;
    public NativeMaterialContainerAdapter(boolean furnace){this.furnace=furnace;}
    @Override public String id(){return furnace?"vanilla:furnace":"vanilla:crafting";}
    @Override public boolean supports(InventoryView v){return furnace?v.getTopInventory() instanceof FurnaceInventory:v.getTopInventory() instanceof CraftingInventory && v.getTopInventory().getSize()==10;}
    private static ItemStack copy(ItemStack s){return s==null?null:s.clone();}
    private static ItemStack[] copy(ItemStack[] a){return Arrays.stream(a).map(NativeMaterialContainerAdapter::copy).toArray(ItemStack[]::new);}
    private static Map<Integer,ItemStack> copy(Map<Integer,ItemStack> source){Map<Integer,ItemStack> m=new LinkedHashMap<>();source.forEach((i,s)->m.put(i,copy(s)));return Collections.unmodifiableMap(m);}
    private static String encode(ItemStack s){
        if(s==null || s.getType().isAir())return "-";
        try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ObjectOutputStream out=new BukkitObjectOutputStream(bytes)){out.writeObject(s);}return Base64.getEncoder().encodeToString(bytes.toByteArray());}
        catch(java.io.IOException e){throw new IllegalArgumentException("无法保存材料证据",e);}
    }
    @Override public Delivery capture(Player player,InventoryView view,Map<Integer,ItemStack> storageChanges,Map<Integer,ItemStack> inputChanges){
        if(!supports(view))throw new IllegalArgumentException("不支持此容器");
        var storage=copy(storageChanges);var target=copy(inputChanges);
        ItemStack[] beforeStorage=copy(player.getInventory().getStorageContents()),beforeTop=copy(view.getTopInventory().getContents());
        ItemStack cursor=copy(player.getItemOnCursor());
        String identity=furnace && view.getTopInventory().getLocation()!=null
            ? view.getTopInventory().getLocation().getWorld().getUID()+":"+view.getTopInventory().getLocation().getBlockX()+":"+view.getTopInventory().getLocation().getBlockY()+":"+view.getTopInventory().getLocation().getBlockZ()
            : player.getUniqueId()+":"+UUID.randomUUID();
        return new Delivery(){
            private boolean applied;
            @Override public WarehouseMaterialAccess.Target target(){
                StringBuilder evidence=new StringBuilder("PLAYER "+player.getUniqueId()+"\n");
                storage.forEach((i,s)->evidence.append("STORAGE ").append(i).append(' ').append(encode(beforeStorage[i])).append(' ').append(encode(s)).append('\n'));
                target.forEach((i,s)->evidence.append("INPUT ").append(i).append(' ').append(encode(beforeTop[i])).append(' ').append(encode(s)).append('\n'));
                return new WarehouseMaterialAccess.Target(id(),identity,furnace?"PERSISTENT_UNPROVEN":"EPHEMERAL",evidence.toString());
            }
            @Override public boolean unchanged(){
                if(applied || !player.isOnline() || player.isDead() || player.getOpenInventory()!=view || !Objects.equals(cursor,player.getItemOnCursor())
                    || !Arrays.equals(beforeStorage,player.getInventory().getStorageContents()))return false;
                // Only input slots participate in this plan. A furnace burning fuel or a
                // crafting result refresh must not invalidate unrelated material inputs.
                for(int i=furnace?0:1;i<(furnace?1:10);i++)if(!Objects.equals(beforeTop[i],view.getTopInventory().getItem(i)))return false;
                if(furnace){var location=view.getTopInventory().getLocation();return location!=null && location.getBlock().getState() instanceof Container c && c.getInventory().equals(view.getTopInventory());}
                return true;
            }
            @Override public void apply(){
                if(!unchanged())throw new IllegalStateException("库存已变化");applied=true;
                // Remove the touched before-images first. A partial application must not
                // leave both the returned old ingredient and its original slot populated.
                target.keySet().forEach(i->view.getTopInventory().setItem(i,null));
                storage.keySet().forEach(i->player.getInventory().setItem(i,null));
                storage.forEach((i,s)->player.getInventory().setItem(i,copy(s)));
                target.forEach((i,s)->view.getTopInventory().setItem(i,copy(s)));
                player.updateInventory();
            }
        };
    }
}
