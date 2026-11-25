package local.dogwand;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Voxel traversal. Never asks World.rayTraceBlocks to load a distant chunk. */
public final class LoadedRaycast {
    public record Hit(Block block, Entity entity, Vector position) {}
    private LoadedRaycast() {}

    public static Hit cast(Player player) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        Vector start = eye.toVector(), dir = eye.getDirection().normalize();
        double limit = worldLimit(eye, dir);
        if (limit <= 0) return null;
        int x = eye.getBlockX(), y = eye.getBlockY(), z = eye.getBlockZ();
        int sx = sign(dir.getX()), sy = sign(dir.getY()), sz = sign(dir.getZ());
        double tx = first(start.getX(), dir.getX(), x, sx);
        double ty = first(start.getY(), dir.getY(), y, sy);
        double tz = first(start.getZ(), dir.getZ(), z, sz);
        double dx = delta(dir.getX()), dy = delta(dir.getY()), dz = delta(dir.getZ());
        double entered = 0, nearestEntity = Double.POSITIVE_INFINITY;
        Entity entity = null;
        Set<Long> checkedChunks = new HashSet<>();
        Set<UUID> checkedEntities = new HashSet<>();
        while (entered <= limit && y >= world.getMinHeight() && y < world.getMaxHeight()) {
            int cx = x >> 4, cz = z >> 4;
            if (!world.isChunkLoaded(cx, cz)) return null;
            // Include neighboring chunks for hitboxes protruding across chunk boundaries.
            for (int nx = cx - 1; nx <= cx + 1; nx++) for (int nz = cz - 1; nz <= cz + 1; nz++) {
                long key = ((long) nx << 32) ^ (nz & 0xffffffffL);
                if (!world.isChunkLoaded(nx, nz) || !checkedChunks.add(key)) continue;
                for (Entity candidate : world.getChunkAt(nx, nz).getEntities()) {
                    if (candidate == player || !candidate.isValid() || !checkedEntities.add(candidate.getUniqueId())) continue;
                    if (candidate instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) continue;
                    BoundingBox box = candidate.getBoundingBox();
                    if (box.getWidthX() <= 0 || box.getHeight() <= 0 || box.getWidthZ() <= 0) continue;
                    RayTraceResult hit = box.rayTrace(start, dir, limit);
                    if (hit == null) continue;
                    double distance = hit.getHitPosition().distance(start);
                    if (distance < nearestEntity) { nearestEntity = distance; entity = candidate; }
                }
            }
            double end = Math.min(limit, Math.min(tx, Math.min(ty, tz)));
            Block block = world.getBlockAt(x, y, z);
            // Block.rayTrace tests this block's actual shape, including slabs and fences.
            RayTraceResult blockHit = block.rayTrace(eye, dir, Math.min(limit, end + 0.00001), FluidCollisionMode.NEVER);
            double bd = blockHit == null ? Double.POSITIVE_INFINITY : blockHit.getHitPosition().distance(start);
            if (entity != null && nearestEntity <= end + 0.000001 && nearestEntity < bd)
                return new Hit(null, entity, start.clone().add(dir.clone().multiply(nearestEntity)));
            if (blockHit != null) return new Hit(block, null, blockHit.getHitPosition());
            if (end >= limit) return null;
            entered = end;
            // Advance all tied axes, without skipping diagonally adjacent voxels by rounding.
            if (tx <= end + 1e-9) { x += sx; tx += dx; }
            if (ty <= end + 1e-9) { y += sy; ty += dy; }
            if (tz <= end + 1e-9) { z += sz; tz += dz; }
        }
        return null;
    }

    private static int sign(double v) { return v > 0 ? 1 : v < 0 ? -1 : 0; }
    private static double delta(double v) { return v == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / v); }
    private static double first(double pos, double dir, int block, int step) {
        return step == 0 ? Double.POSITIVE_INFINITY : ((step > 0 ? block + 1 : block) - pos) / dir;
    }
    private static double worldLimit(Location eye, Vector dir) {
        World world = eye.getWorld();
        WorldBorder border = world.getWorldBorder();
        double half = border.getSize() / 2;
        Location center = border.getCenter();
        BoundingBox bounds = new BoundingBox(Math.max(-29999984, center.getX() - half), world.getMinHeight(),
                Math.max(-29999984, center.getZ() - half), Math.min(29999984, center.getX() + half),
                world.getMaxHeight(), Math.min(29999984, center.getZ() + half));
        if (!bounds.contains(eye.toVector())) return 0;
        // A start inside a box traces to its exit face.
        RayTraceResult exit = bounds.rayTrace(eye.toVector(), dir, 100000000);
        return exit == null ? 0 : exit.getHitPosition().distance(eye.toVector());
    }
}
