package local.dogwand;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;

/** Shared loaded-mob scans; no chunk tickets and no player candidates. */
public final class AutomaticOrders {
    private record Scan(int tick,List<Mob> mobs){}
    private final Map<UUID,Scan> scans=new HashMap<>();
    private final Map<UUID,UUID> claims=new HashMap<>();
    public void release(UUID dog){claims.remove(dog);}
    public static boolean candidate(Entity entity,boolean hostileOnly){
        return entity instanceof Mob mob && mob.isValid() && !mob.isDead() && !mob.isInvulnerable()
                && !(mob instanceof Tameable pet && pet.isTamed()) && (!hostileOnly || mob instanceof Enemy);
    }
    public Mob target(Wolf dog,DogCommands.Order order){
        Player owner=Bukkit.getPlayer(order.owner());GuardRegion region=order.region();
        if(region==null && (owner==null || owner.getWorld()!=dog.getWorld()))return null;
        UUID world=dog.getWorld().getUID();int now=Bukkit.getCurrentTick();
        Scan scan=scans.get(world);if(scan==null||now-scan.tick>=10){scan=new Scan(now,List.copyOf(dog.getWorld().getEntitiesByClass(Mob.class)));scans.put(world,scan);}
        boolean hostile=order.kind()!=DogCommands.Kind.FOLLOW_ALL;
        Mob result=scan.mobs.stream().filter(m->m!=dog && m.getWorld()==dog.getWorld() && candidate(m,hostile) && (region==null?m.getLocation().distanceSquared(owner.getLocation())<=400:region.contains(m.getLocation())))
                .min(Comparator.comparingDouble(m->GuardNavigation.targetScore(m.getLocation().distance(dog.getLocation()),(int)claims.entrySet().stream().filter(e->!e.getKey().equals(dog.getUniqueId())&&e.getValue().equals(m.getUniqueId())).count()))).orElse(null);
        if(result==null)claims.remove(dog.getUniqueId());else claims.put(dog.getUniqueId(),result.getUniqueId());return result;
    }
    public static Location inside(World world,GuardRegion region,Vector desired,double width,double height){
        Vector center=region.closest(desired);
        Location found=Destinations.near(world,center,2,5,p->region.bodyFits(p,width,height));
        return found!=null&&region.bodyFits(found,width,height)?found:null;
    }
    public static Location entry(Wolf dog,GuardRegion region){
        return inside(dog.getWorld(),region,GuardNavigation.entryAim(region,dog.getLocation().toVector()),dog.getWidth(),dog.getHeight());
    }
    public static Location roam(Wolf dog,GuardRegion region){
        dog.getPathfinder().stopPathfinding();
        for(int i=0;i<12;i++){
            var random=java.util.concurrent.ThreadLocalRandom.current();
            Vector sample=region.closest(dog.getLocation().toVector().add(new Vector(random.nextDouble(-12,12),0,random.nextDouble(-12,12))));
            Location point=inside(dog.getWorld(),region,sample,dog.getWidth(),dog.getHeight());if(point==null||point.distanceSquared(dog.getLocation())<2.25)continue;
            var path=dog.getPathfinder().findPath(point);
            if(path!=null&&path.canReachFinalPoint()&&GuardNavigation.pathFits(region,path.getPoints(),path.getNextPointIndex(),dog.getWidth(),dog.getHeight()))return point;
        }
        return dog.getLocation();
    }
    public void trim(){int now=Bukkit.getCurrentTick();scans.entrySet().removeIf(e->now-e.getValue().tick>40||Bukkit.getWorld(e.getKey())==null);}
}
