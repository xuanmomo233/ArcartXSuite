/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  org.bukkit.Bukkit
 *  org.bukkit.block.Block
 */
package ink.ptms.chemdah.library.redlib;

import ink.ptms.chemdah.library.redlib.BlockDataManager;
import ink.ptms.chemdah.library.redlib.BlockPosition;
import ink.ptms.chemdah.library.redlib.ChunkPosition;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;

public class DataBlock {
    protected Map<String, Object> data;
    private final BlockDataManager manager;
    private final BlockPosition block;
    private final String world;
    private Map<String, Object> transientProperties;

    DataBlock(Map<String, Object> data2, BlockPosition block, String world, BlockDataManager manager) {
        this.data = data2;
        this.block = block;
        this.manager = manager;
        this.world = world;
    }

    public BlockDataManager getManager() {
        return this.manager;
    }

    public Map<String, Object> getTransientProperties() {
        if (this.transientProperties == null) {
            this.transientProperties = new HashMap<String, Object>();
        }
        return this.transientProperties;
    }

    public Block getBlock() {
        return Objects.requireNonNull(Bukkit.getWorld((String)this.world)).getBlockAt(this.block.getX(), this.block.getY(), this.block.getZ());
    }

    protected ChunkPosition getChunkPosition() {
        return new ChunkPosition(this.block, this.world);
    }

    protected BlockPosition getBlockPosition() {
        return this.block;
    }

    public Object getObject(String key) {
        return this.data.get(key);
    }

    public String getString(String key) {
        return Coerce.toString((Object)this.data.get(key));
    }

    public Integer getInt(String key) {
        return Coerce.toInteger((Object)this.data.get(key));
    }

    public Long getLong(String key) {
        return Coerce.toLong((Object)this.data.get(key));
    }

    public Double getDouble(String key) {
        return Coerce.toDouble((Object)this.data.get(key));
    }

    public Boolean getBoolean(String key) {
        return Coerce.toBoolean((Object)this.data.get(key));
    }

    public boolean contains(String key) {
        return this.data.containsKey(key);
    }

    public void clear() {
        this.data.clear();
    }

    public void set(String key, Object value2) {
        this.manager.setModified(new ChunkPosition(this.block, this.world));
        if (value2 == null) {
            this.data.remove(key);
            return;
        }
        this.data.put(key, value2);
    }

    public void remove(String key) {
        this.set(key, null);
    }

    public Map<String, Object> getData() {
        return this.data;
    }

    public Set<String> getKeys() {
        return this.data.keySet();
    }

    public String toString() {
        return "DataBlock{data=" + this.data + ", block=" + this.block + ", world='" + this.world + '\'' + '}';
    }
}

