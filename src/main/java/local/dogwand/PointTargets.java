package local.dogwand;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class PointTargets {
    public static final double RADIUS=2.5;
    private PointTargets(){}
    public static boolean within(Vector point,BoundingBox body){
        double dx=point.getX()-Math.clamp(point.getX(),body.getMinX(),body.getMaxX());
        double dy=point.getY()-Math.clamp(point.getY(),body.getMinY(),body.getMaxY());
        double dz=point.getZ()-Math.clamp(point.getZ(),body.getMinZ(),body.getMaxZ());return dx*dx+dy*dy+dz*dz<=RADIUS*RADIUS;
    }
}
