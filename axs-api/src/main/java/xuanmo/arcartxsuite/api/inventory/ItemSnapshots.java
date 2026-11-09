package xuanmo.arcartxsuite.api.inventory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/** Full item snapshots and content keys, never material-only payment matching. */
public final class ItemSnapshots {
    private ItemSnapshots() { }
    public static String encode(ItemStack item) {
        if (InventoryItems.empty(item)) return "";
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(var out=new BukkitObjectOutputStream(bytes)){out.writeObject(item);}
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch(IOException e){throw new IllegalArgumentException("物品无法保存",e);}
    }
    public static ItemStack decode(String data) {
        if(data==null || data.isEmpty())return null;
        if(data.length()>1048576)throw new IllegalArgumentException("物品快照过大");
        try(var in=new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data)))){
            Object value=in.readObject();
            if(!(value instanceof ItemStack item) || InventoryItems.empty(item))throw new IllegalArgumentException("物品已不可用");
            return item;
        } catch(IOException|ClassNotFoundException e){throw new IllegalArgumentException("物品快照无法还原",e);}
    }
    public static String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
