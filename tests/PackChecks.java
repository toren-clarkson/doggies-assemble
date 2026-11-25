package local.dogwand;

import java.util.*;
import java.nio.file.*;
import org.bukkit.util.Vector;

public final class PackChecks {
    static int count;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);count++;System.out.println("PASS "+label);}
    public static void main(String[] args)throws Exception{
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID(),wand=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
        var initial=new DogCommands.Order(owner,3,DogCommands.Kind.ATTACK,world,1,64,2,a,wand,null);
        var queued=initial.enqueue(b).enqueue(c).enqueue(b);
        check(queued.target().equals(a)&&queued.queue().equals(List.of(a,b,c)),"appending selections preserves unique selected targets and legacy encoding");
        check(DogCommands.Order.decode(queued.encode()).equals(queued),"attack queue survives saved order roundtrip");
        var advanced=queued.withQueue(List.of(b,c));check(advanced.target().equals(b)&&advanced.queue().equals(List.of(b,c)),"finishing first target advances to second without losing third");
        check(initial.queue().equals(List.of(a)),"queue append cannot mutate previous order snapshot");
        String old="3|"+owner+"|3|ATTACK|"+world+"|1.0|64.0|2.0|"+a+"|"+wand+"|-";
        check(DogCommands.Order.decode(old).queue().equals(List.of(a)),"legacy attack upgrades to single queued target");
        var fetch=new DogCommands.Order(owner,9,DogCommands.Kind.FETCH,world,0,64,0,null,wand,null,List.of(a,b));
        check(DogCommands.Order.decode(fetch.encode()).equals(fetch),"fetch batch item IDs survive save and load");
        for(int n:new int[]{1,8,32,100}){var points=PackFormation.stay(n);boolean spaced=points.size()==n;
            for(int i=0;i<n;i++)for(int j=i+1;j<n;j++)spaced&=points.get(i).distance(points.get(j))>=1.39;
            check(spaced,"distinct spaced stay slots for "+n+" dogs");}
        check(PackFormation.radius(100)>PackFormation.radius(32)&&PackFormation.radius(32)>PackFormation.radius(8),"acceptable region radius grows with bound pack size");
        check(PackFormation.stay(10).equals(PackFormation.stay(10)),"formation slot assignment is stable");
        boolean attacking=true;for(int i=0;i<100;i++)attacking&=PackFormation.combat(i).length()<=1.300001;
        check(attacking,"every combat slot approaches the enemy; no eight-dog reserve cap");
        check(PackFormation.stay(2).get(1).length()<1.8,"stay slots are denser than 1.5 formations");
        Vector aim=new Vector(0,64,0);
        check(PointTargets.within(aim,new org.bukkit.util.BoundingBox(2.5,64,0,3,65,1)),"point selection includes mobs touching the 2.5-block radius");
        check(!PointTargets.within(aim,new org.bukkit.util.BoundingBox(2.5001,64,0,3,65,1)),"point selection excludes mobs just beyond radius");
        check(!PointTargets.within(aim,new org.bukkit.util.BoundingBox(2,64,2,3,65,3)),"point selection uses a sphere rather than the surrounding cube");
        check(PointTargets.within(aim,new org.bukkit.util.BoundingBox(-.2,66.5,-.2,.2,67,.2)),"point selection respects vertical radius");
        List<Integer> dogs=java.util.stream.IntStream.range(0,37).boxed().toList();var split=TargetDistributor.split(dogs,List.of("near","far","farther"),(dog,target)->target.equals("near")?1:1000);
        var loads=new HashMap<String,Integer>();split.values().forEach(target->loads.merge(target,1,Integer::sum));
        check(split.size()==37&&Collections.max(loads.values())-Collections.min(loads.values())<=1,"army distributes evenly even when every dog prefers the same nearest threat");
        check(TargetDistributor.split(dogs,List.<String>of(),(d,t)->0).isEmpty(),"no threats produces no combat assignments");
        check(TargetDistributor.split(List.of(1,2),List.of("a","b","c"),(d,t)->0).values().stream().distinct().count()==2,"two dogs cover two different enemies");
        var box=new GuardRegion(0,63,0,1000,68,1000);var viewer=new Vector(40,64,0);var particles=EffectGeometry.box(box,viewer,96);
        Set<Double> edge=new HashSet<>();particles.stream().filter(p->p.getY()==63&&p.getZ()==0).forEach(p->edge.add(p.getX()));
        boolean dense=true;for(int x=0;x<130;x++)dense&=edge.contains((double)x);
        check(dense,"large guard edge keeps one particle at every visible block");
        check(particles.stream().allMatch(p->p.distance(viewer)<=96.00001)&&particles.size()<2000,"far edges clipped by viewer distance without diluting visible density");
        check(new HashSet<>(particles).size()==particles.size(),"shared corner particles do not duplicate");
        var huge=EffectGeometry.box(new GuardRegion(-30000000,0,-30000000,30000000,300,30000000),new Vector(-30000000,64,-30000000),96);
        check(!huge.isEmpty()&&huge.size()<2000,"world-scale region preview remains bounded near the viewer");
        var dir=Files.createTempDirectory("dogwand-bound-");var file=dir.resolve("registry.yml");var registry=new DogRegistry(file);
        registry.bindings(wand,List.of(a,b));check(registry.bound(a)&&registry.bound(b)&&!registry.bound(c),"cramming protection tracks bound dogs only");
        UUID second=UUID.randomUUID();registry.bindings(second,List.of(a));registry.bindings(wand,List.of(b));check(registry.bound(a),"unbinding one wand preserves another wand's protection");
        registry.bindings(second,List.of());check(!registry.bound(a)&&registry.bound(b),"last unbind removes bound protection");registry.flush();
        var restored=new DogRegistry(file);check(restored.bound(b)&&!restored.bound(a),"bound protection index persists through restart");restored.died(b);check(!restored.bound(b),"death removes bound protection");
        restored.bindings(wand,Set.of());restored.flush();check(new DogRegistry(file).tracked(wand)&&new DogRegistry(file).dogs(wand).isEmpty(),"Unbind All preserves a known empty binding group across restart");
        Files.delete(file);Files.delete(dir);System.out.println("Pack checks passed: "+count);
    }
}
