package local.dogwand;

import java.util.*;
import java.nio.file.*;
import java.lang.reflect.Proxy;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;

/** Regression checks for guard node coordinates, persistent cleanup, and viewer-only cosmetic APIs. */
public final class FeedbackChecks {
    static int count;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);count++;}
    public static void main(String[] args)throws Exception{
        var region=new GuardRegion(0,63,0,8,67,8);var corner=new Location(null,0,64,0);
        check(!region.bodyFits(corner,.62,.9)&&region.bodyFits(GuardNavigation.nodeCenter(corner,.62),.62,.9),"reproduced and corrected corner-versus-center guard path rejection");
        check(GuardNavigation.pathFits(region,List.of(corner,new Location(null,8,64,8)),0,.62,.9),"paths along both region edges remain valid");
        check(GuardNavigation.pathFits(region,List.of(new Location(null,-4,64,0),corner),1,.62,.9),"visited outside nodes do not block a guard that has entered");
        check(!GuardNavigation.pathFits(region,List.of(corner,new Location(null,-1,64,0)),0,.62,.9),"future path nodes outside region still rejected");
        check(GuardNavigation.targetScore(1,3)>GuardNavigation.targetScore(3,0),"guards prefer less crowded comparable threats");
        Vector start=new Vector(1,65,2),end=new Vector(1000000,65,2);var beam=EffectGeometry.beam(start,end);
        check(beam.size()<=161&&beam.getFirst().equals(start)&&beam.getLast().equals(end),"long beam includes both endpoints with bounded particle count");
        var box=EffectGeometry.box(region);check(box.size()<=240,"region preview packet budget is bounded");
        check(box.stream().allMatch(v->{int faces=0;if(v.getX()==region.minX()||v.getX()==region.maxX()+1)faces++;if(v.getY()==region.minY()||v.getY()==region.maxY()+1)faces++;if(v.getZ()==region.minZ()||v.getZ()==region.maxZ()+1)faces++;return faces>=2;}),"preview points trace box edges instead of filling the volume");
        var dir=Files.createTempDirectory("dogwand-registry-");var file=dir.resolve("known.yml");var registry=new DogRegistry(file);
        UUID owner=UUID.randomUUID(),other=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID(),unknown=UUID.randomUUID();registry.observe(a,owner);registry.observe(b,owner);registry.flush();
        check(registry.owned(owner).equals(Set.of(a,b))&&registry.owned(other).isEmpty(),"bulk index selects only current owner's known dogs");
        registry.died(a);check(registry.living(Set.of(a,b,unknown)).equals(Set.of(b,unknown)),"death prunes bound count without pruning unknown or unloaded dogs");
        var restored=new DogRegistry(file);check(restored.isDead(a)&&restored.owned(owner).equals(Set.of(b)),"death cleanup persists for wands stored across restarts");
        restored.observe(b,other);check(restored.owned(owner).isEmpty()&&restored.owned(other).equals(Set.of(b)),"bulk index updates ownership transfers");
        restored.observe(a,owner);check(restored.isDead(a)&&!restored.owned(owner).contains(a),"stale sightings cannot rebind a dead UUID");
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        UUID worldId=UUID.randomUUID();List<String> sent=new ArrayList<>();
        World world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class[]{World.class},(p,m,v)->{
            if(m.getName().equals("spawnParticle")||m.getName().equals("playSound"))throw new AssertionError("Cosmetic broadcast to world");
            return m.getName().equals("getUID")?worldId:null;
        });
        Player viewer=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,v)->switch(m.getName()){
            case "getWorld"->world;case "getLocation","getEyeLocation"->new Location(world,1,65,2);
            case "spawnParticle"->{sent.add("particle:"+v[0]);yield null;}case "playSound"->{sent.add("sound:"+v[1]);yield null;}default->null;
        });
        Wolf dog=(Wolf)Proxy.newProxyInstance(Wolf.class.getClassLoader(),new Class[]{Wolf.class},(p,m,v)->switch(m.getName()){case "getLocation"->new Location(world,2,64,2);case "getHeight"->.9;default->null;});
        OwnerEffects.fed(viewer,dog);check(sent.stream().anyMatch(v->v.equals("particle:"+Particle.HEART)),"fed dog hearts sent through owner player API");
        sent.clear();OwnerEffects.beam(viewer,new Vector(5,65,2));check(!sent.isEmpty()&&sent.stream().allMatch(v->v.startsWith("particle:")),"point beam sends only owner particles");
        sent.clear();OwnerEffects.box(viewer,new WandBindings.RegionSelection(worldId,region));check(sent.size()==box.size(),"bounding box sent only to requesting owner");
        sent.clear();OwnerEffects.box(viewer,new WandBindings.RegionSelection(UUID.randomUUID(),region));check(sent.isEmpty(),"preview not shown in wrong dimension");
        for(String mode:List.of("point_order","sit_stand","highlight","auto_target","feed","guard","fetch","release")){sent.clear();OwnerEffects.action(viewer,mode);check(sent.size()==1&&sent.getFirst().startsWith("sound:"),"private activation sound: "+mode);}
        Files.delete(file);Files.delete(dir);System.out.println("Feedback checks passed: "+count);
    }
}
