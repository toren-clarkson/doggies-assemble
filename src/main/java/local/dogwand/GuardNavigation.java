package local.dogwand;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.entity.*;

public final class GuardNavigation {
    private GuardNavigation(){}
    public static Location nodeCenter(Location node,double width){double offset=Math.floor(width+1)*.5;return node.clone().add(offset,0,offset);}
    public static boolean pathFits(GuardRegion region,List<Location> nodes,int next,double width,double height){
        for(int i=Math.max(0,next);i<nodes.size();i++)if(!region.bodyFits(nodeCenter(nodes.get(i),width),width,height))return false;return true;
    }
    public static double targetScore(double distance,int otherGuards){return distance+4.0*otherGuards;}
    /** Arrival for entry depends on body clearance, not the normal go/stay radius. */
    public static boolean settled(GuardRegion r,Location p,double width,double height){
        double pad=Math.max(0,Math.min(.08,Math.min((r.maxX()+1.0-r.minX()-width)/2,(r.maxZ()+1.0-r.minZ()-width)/2)-.001));
        return r.bodyFits(p,width,height)&&p.getX()-width/2>=r.minX()+pad&&p.getX()+width/2<=r.maxX()+1-pad&&p.getZ()-width/2>=r.minZ()+pad&&p.getZ()+width/2<=r.maxZ()+1-pad;
    }
    public static org.bukkit.util.Vector entryAim(GuardRegion r,org.bukkit.util.Vector from){
        double ix=Math.min(1.5,(r.maxX()+1.0-r.minX())/2),iz=Math.min(1.5,(r.maxZ()+1.0-r.minZ())/2);
        double x=Math.clamp(from.getX(),r.minX()+ix,r.maxX()+1-ix),z=Math.clamp(from.getZ(),r.minZ()+iz,r.maxZ()+1-iz);
        if(r.maxX()+1.0-r.minX()<=2)x=Math.clamp(x+Math.signum((r.minX()+r.maxX()+1.0)/2-from.getX())*.2,r.minX()+.5,r.maxX()+.5);
        if(r.maxZ()+1.0-r.minZ()<=2)z=Math.clamp(z+Math.signum((r.minZ()+r.maxZ()+1.0)/2-from.getZ())*.2,r.minZ()+.5,r.maxZ()+.5);
        return new org.bukkit.util.Vector(x,Math.clamp(from.getY(),r.minY(),r.maxY()+.05),z);
    }
    public static Location clipMove(GuardRegion r,Location from,Location to,double width,double height){
        if(!r.bodyFits(from,width,height)||r.bodyFits(to,width,height))return to;
        Location clipped=to.clone();double half=width/2;
        clipped.setX(Math.clamp(to.getX(),r.minX()+half,r.maxX()+1-half));
        clipped.setY(Math.clamp(to.getY(),r.minY(),r.maxY()+1-height));
        clipped.setZ(Math.clamp(to.getZ(),r.minZ()+half,r.maxZ()+1-half));return clipped;
    }
    public static org.bukkit.util.Vector finishEntry(Location from,Location destination,org.bukkit.util.Vector velocity){
        // Native navigation may finish near its last node. Complete the small horizontal gap with collision-aware movement.
        var delta=destination.toVector().subtract(from.toVector());delta.setY(0);
        if(delta.lengthSquared()>.0064)delta.normalize().multiply(.08);
        delta.setY(velocity.getY());return delta;
    }
    public static Location approach(Wolf wolf,LivingEntity target,GuardRegion region){
        return approach(wolf,target,region,0);
    }
    public static Location approach(Wolf wolf,LivingEntity target,GuardRegion region,int failedPaths){
        int slot=Math.floorMod(wolf.getUniqueId().hashCode()+failedPaths,8);
        for(int i=0;i<8;i++){
            double angle=(slot+i)*Math.PI/4;var wanted=target.getLocation().toVector().add(new org.bukkit.util.Vector(Math.cos(angle)*1.1,0,Math.sin(angle)*1.1));
            Location point=AutomaticOrders.inside(wolf.getWorld(),region,wanted,wolf.getWidth(),wolf.getHeight());
            if(point!=null)return point;
        }
        return AutomaticOrders.inside(wolf.getWorld(),region,target.getLocation().toVector(),wolf.getWidth(),wolf.getHeight());
    }
}
