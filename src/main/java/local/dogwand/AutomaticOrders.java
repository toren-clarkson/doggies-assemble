package local.dogwand;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;
/** Plan whole groups together so every threat receives a share of the pack. */
public final class AutomaticOrders {
    public record Member(Wolf dog,DogCommands.Order order){}
    private record Group(UUID owner,UUID world,DogCommands.Kind kind,GuardRegion region,List<UUID> selected){}
    private final Map<UUID,LivingEntity> targets=new HashMap<>();
    public void release(UUID dog){targets.remove(dog);}
    public static boolean candidate(Entity entity,boolean hostileOnly){
        return entity instanceof Mob mob && mob.isValid() && !mob.isDead() && !mob.isInvulnerable()
            && !(mob instanceof Tameable pet && pet.isTamed()) && (!hostileOnly || mob instanceof Enemy);
    }
    public void plan(List<Member> members){
        plan(members,(owner,entity)->candidate(entity,false));
    }
    public void plan(List<Member> members,java.util.function.BiPredicate<Player,Entity> permits){
        targets.clear();Map<Group,List<Member>> groups=new LinkedHashMap<>();Map<UUID,List<Mob>> worlds=new HashMap<>();
        members.stream().sorted(Comparator.comparing(m->m.dog().getUniqueId())).forEach(m->{var o=m.order();
            if(o.region()!=null&&!o.region().bodyFits(m.dog().getLocation(),m.dog().getWidth(),m.dog().getHeight()))return;
            groups.computeIfAbsent(new Group(o.owner(),o.world(),o.kind(),o.region(),o.kind()==DogCommands.Kind.ATTACK?o.queue().stream().sorted().toList():List.of()),k->new ArrayList<>()).add(m);});
        for(var entry:groups.entrySet()){
            var group=entry.getKey();var pack=entry.getValue();var world=pack.getFirst().dog().getWorld();Player owner=Bukkit.getPlayer(group.owner());
            if(group.region()==null&&(owner==null||owner.getWorld()!=world))continue;
            List<LivingEntity> choices;
            if(group.kind()==DogCommands.Kind.ATTACK)choices=group.selected().stream().map(Bukkit::getEntity).filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).filter(m->m.isValid()&&!m.isDead()&&m.getWorld()==world&&permits.test(owner,m)).sorted(Comparator.comparing(Entity::getUniqueId)).toList();
            else {var mobs=worlds.computeIfAbsent(world.getUID(),k->List.copyOf(world.getEntitiesByClass(Mob.class)));
            choices=mobs.stream().filter(m->m.getWorld()==world&&candidate(m,group.kind()!=DogCommands.Kind.FOLLOW_ALL)
                &&(group.region()==null?m.getLocation().distanceSquared(owner.getLocation())<=400:group.region().contains(m.getLocation())))
                .sorted(Comparator.comparing(Entity::getUniqueId)).map(LivingEntity.class::cast).toList();}
            TargetDistributor.split(pack,choices,(m,t)->m.dog().getLocation().distanceSquared(t.getLocation())).forEach((m,t)->targets.put(m.dog().getUniqueId(),t));
        }
    }
    public LivingEntity target(Wolf dog,DogCommands.Order order){return targets.get(dog.getUniqueId());}
    public void trim(){}
    public static Location inside(World world,GuardRegion region,Vector desired,double width,double height){
        Vector center=region.closest(desired);Location found=Destinations.near(world,center,2,5,p->region.bodyFits(p,width,height));
        return found!=null&&region.bodyFits(found,width,height)?found:null;
    }
    public static Location entry(Wolf dog,GuardRegion region){return inside(dog.getWorld(),region,GuardNavigation.entryAim(region,dog.getLocation().toVector()),dog.getWidth(),dog.getHeight());}
    public static Location roam(Wolf dog,GuardRegion region){
        dog.getPathfinder().stopPathfinding();
        for(int i=0;i<12;i++){
            var random=java.util.concurrent.ThreadLocalRandom.current();Vector sample=region.closest(dog.getLocation().toVector().add(new Vector(random.nextDouble(-12,12),0,random.nextDouble(-12,12))));
            Location point=inside(dog.getWorld(),region,sample,dog.getWidth(),dog.getHeight());if(point==null||point.distanceSquared(dog.getLocation())<2.25)continue;
            var path=dog.getPathfinder().findPath(point,0);
            if(path!=null&&path.canReachFinalPoint()&&GuardNavigation.pathFits(region,path.getPoints(),path.getNextPointIndex(),dog.getWidth(),dog.getHeight()))return point;
        }return dog.getLocation();
    }
}
