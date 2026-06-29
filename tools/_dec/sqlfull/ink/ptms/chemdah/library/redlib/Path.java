/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.library.redlib;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public class Path {
    public static List<Location> getPath(Location start, Location end, double step) {
        ArrayList<Location> locs = new ArrayList<Location>();
        locs.add(start);
        Vector v = end.clone().subtract(start).toVector();
        v = v.normalize().multiply(step);
        Location current = start.clone();
        while (current.distance(end) > step) {
            locs.add(current.clone());
            current = current.add(v);
        }
        locs.add(end);
        return locs;
    }

    public static List<Location> getPath(Location start, Location end) {
        return Path.getPath(start, end, 1.0);
    }

    public static List<Location> getPath(Location start, Vector direction, double distance, double step) {
        direction = direction.clone().normalize().multiply(distance);
        Location end = start.clone().add(direction);
        return Path.getPath(start, end, step);
    }

    public static List<Location> getPath(Location start, Vector direction, double distance) {
        return Path.getPath(start, direction, distance, 1.0);
    }

    public static List<Location> getPath(Location start, Vector direction) {
        return Path.getPath(start, direction, direction.length(), 1.0);
    }

    public static List<Location> getPath(Location start) {
        return Path.getPath(start, start.getDirection(), start.getDirection().length(), 1.0);
    }

    public static List<Location> getPath(Location start, double step) {
        return Path.getPath(start, start.getDirection(), start.getDirection().length(), step);
    }
}

