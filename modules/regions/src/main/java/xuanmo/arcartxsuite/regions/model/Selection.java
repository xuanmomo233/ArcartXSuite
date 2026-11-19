package xuanmo.arcartxsuite.regions.model;

import org.bukkit.Location;

/**
 * 玩家的选区会话 (两个点)。
 */
public final class Selection {

    private String world;
    private long version;
    private int x1, y1, z1;
    private int x2, y2, z2;
    private boolean pos1Set, pos2Set;

    public void setPos1(Location loc) {
        if (world != null && !world.equals(loc.getWorld().getName())) {
            pos2Set = false;
        }
        this.world = loc.getWorld().getName();
        this.x1 = loc.getBlockX();
        this.y1 = loc.getBlockY();
        this.z1 = loc.getBlockZ();
        this.pos1Set = true;
        version++;
    }

    /**
     * 设置选区点 2。
     *
     * @return true 表示设置成功；false 表示跨世界，调用方应提示玩家并保持原 pos2
     */
    public boolean setPos2(Location loc) {
        if (this.world == null) {
            this.world = loc.getWorld().getName();
        } else if (!this.world.equals(loc.getWorld().getName())) {
            return false;
        }
        this.x2 = loc.getBlockX();
        this.y2 = loc.getBlockY();
        this.z2 = loc.getBlockZ();
        this.pos2Set = true;
        version++;
        return true;
    }

    public boolean isComplete() {
        return pos1Set && pos2Set;
    }

    public boolean isPos1Set() { return pos1Set; }
    public boolean isPos2Set() { return pos2Set; }
    public String world() { return world; }
    public int x1() { return x1; }
    public int y1() { return y1; }
    public int z1() { return z1; }
    public int x2() { return x2; }
    public int y2() { return y2; }
    public int z2() { return z2; }

    public long version() { return version; }

    public long volume() {
        if (!isComplete()) return 0;
        return Region.volume(x1, y1, z1, x2, y2, z2);
    }
}
