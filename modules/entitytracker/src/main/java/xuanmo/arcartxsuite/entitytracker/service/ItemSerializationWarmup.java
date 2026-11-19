package xuanmo.arcartxsuite.entitytracker.service;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.*;
import java.util.*;
import org.bukkit.util.io.BukkitObjectOutputStream;

/** Warms Java serialization descriptors off Main without creating or reading any Bukkit item/world. */
final class ItemSerializationWarmup {
    private ItemSerializationWarmup() {}

    static void prepare() throws IOException, ClassNotFoundException {
        var wrapper=Class.forName("org.bukkit.util.io.Wrapper",false,BukkitObjectOutputStream.class.getClassLoader());
        ObjectStreamClass.lookup(wrapper);
        try(var bytes=new ByteArrayOutputStream();var stream=new BukkitObjectOutputStream(bytes)) {
            stream.writeObject(ImmutableMap.of("type","DIAMOND","meta",ImmutableMap.of(
                "name","warmup","lore",ImmutableList.of("line"),"values",new HashMap<>(Map.of("key","value")))));
            stream.writeObject(new ArrayList<>(List.of("item",3,new byte[]{1,2,3})));
        }
    }
}
