package local.dogwand;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Resolve a clicked surface to nearby footing with room for an adult wolf. */
public final class Destinations {
    private static final Set<Material> HAZARDS = EnumSet.of(Material.LAVA, Material.FIRE, Material.SOUL_FIRE,
            Material.MAGMA_BLOCK, Material.CACTUS, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.WITHER_ROSE);
    private Destinations() {}
    public static Location near(World world, Vector point, int radius, int vertical) {
        return near(world,point,radius,vertical,p->true);
    }
    public static Location near(World world, Vector point, int radius, int vertical, java.util.function.Predicate<Location> acceptable) {
        Location best = null;
        double score = Double.POSITIVE_INFINITY;
        int bx = (int) Math.floor(point.getX()), by = (int) Math.floor(point.getY()), bz = (int) Math.floor(point.getZ());
        for (int x = bx - radius; x <= bx + radius; x++) for (int z = bz - radius; z <= bz + radius; z++) {
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            for (int y = Math.max(world.getMinHeight(), by - vertical); y <= Math.min(world.getMaxHeight()-2, by + vertical); y++) {
                Block support = world.getBlockAt(x, y, z);
                if (HAZARDS.contains(support.getType())) continue;
                for (BoundingBox shape : support.getCollisionShape().getBoundingBoxes()) {
                    if (!shape.contains(0.5, Math.max(shape.getMinY(), shape.getMaxY()-0.0001), 0.5)) continue;
                    Location loc = new Location(world, x + 0.5, y + shape.getMaxY(), z + 0.5);
                    if (!clear(loc) || !acceptable.test(loc)) continue;
                    double distance = loc.toVector().distanceSquared(point);
                    if (distance < score) { score = distance; best = loc; }
                }
            }
        }
        return best;
    }

    public static boolean clear(Location loc) {
        World w = loc.getWorld();
        BoundingBox body = new BoundingBox(loc.getX()-.31, loc.getY()+.001, loc.getZ()-.31,
                loc.getX()+.31, loc.getY()+.96, loc.getZ()+.31);
        for (int x=(int)Math.floor(body.getMinX()); x<=(int)Math.floor(body.getMaxX()); x++)
            for (int z=(int)Math.floor(body.getMinZ()); z<=(int)Math.floor(body.getMaxZ()); z++) {
                if (!w.isChunkLoaded(x>>4,z>>4)) return false;
                for (int y=(int)Math.floor(body.getMinY())-1; y<=(int)Math.floor(body.getMaxY()); y++) {
                    if (y < w.getMinHeight() || y >= w.getMaxHeight()) return false;
                    Block b=w.getBlockAt(x,y,z);
                    // Hazards below the feet count; ordinary support collision does not.
                    if (HAZARDS.contains(b.getType())) return false;
                    for (BoundingBox box:b.getCollisionShape().getBoundingBoxes())
                        if (box.clone().shift(x,y,z).overlaps(body)) return false;
                }
            }
        return true;
    }
}
