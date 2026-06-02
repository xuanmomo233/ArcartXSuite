package xuanmo.arcartxsuite.regions.model;

import org.bukkit.Location;

/**
 * 玩家的选区会话 (两个点)。
 */
public final class Selection {

    private String world;
    private int x1, y1, z1;
    private int x2, y2, z2;
    private boolean pos1Set, pos2Set;

    public void setPos1(Location loc) {
        this.world = loc.getWorld().getName();
        this.x1 = loc.getBlockX();
        this.y1 = loc.getBlockY();
        this.z1 = loc.getBlockZ();
        this.pos1Set = true;
    }

    public void setPos2(Location loc) {
        if (this.world == null) {
            this.world = loc.getWorld().getName();
        }
        this.x2 = loc.getBlockX();
        this.y2 = loc.getBlockY();
        this.z2 = loc.getBlockZ();
        this.pos2Set = true;
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

    public long volume() {
        if (!isComplete()) return 0;
        return (long)(Math.abs(x2 - x1) + 1) * (Math.abs(y2 - y1) + 1) * (Math.abs(z2 - z1) + 1);
    }
}
