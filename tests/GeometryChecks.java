import local.dogwand.*;
import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.util.*;
import org.bukkit.util.Vector;

/** Tests the production ray traversal against deterministic loaded chunks and hitboxes. No sockets. */
public final class GeometryChecks {
    static Set<String> loaded=new HashSet<>();
    static Map<String,BoundingBox> shapes=new HashMap<>();
    static List<Entity> entities=new ArrayList<>();
    static World world;
    static Location eye;
    static Vector direction;
    static int count;
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},handler);
    }
    static Object fallback(Object p,Method m,Object[] a) {
        return switch(m.getName()) {case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);case "toString"->"Fixture";
            default -> {if(m.getReturnType()==boolean.class)yield false;if(m.getReturnType()==int.class)yield 0;if(m.getReturnType()==double.class)yield 0.0;yield null;}};
    }
    static Block block(int x,int y,int z) {
        return proxy(Block.class,(p,m,a)-> {
            var shape=shapes.get(x+","+y+","+z);
            return switch(m.getName()) {
                case "getX"->x;case "getY"->y;case "getZ"->z;case "getWorld"->world;
                case "getType"->shape==null?Material.AIR:Material.STONE;
                case "getCollisionShape"->proxy(VoxelShape.class,(vp,vm,va)->vm.getName().equals("getBoundingBoxes")?(shape==null?List.of():List.of(shape.clone().shift(-x,-y,-z))):fallback(vp,vm,va));
                case "rayTrace"->shape==null?null:shape.rayTrace(((Location)a[0]).toVector(),(Vector)a[1],(double)a[2]);
                default->fallback(p,m,a);
            };
        });
    }
    static void check(boolean ok,String label) {if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);count++;}
    static void setup() {
        loaded.clear();shapes.clear();entities.clear();
        for(int x=-1;x<=12;x++)for(int z=-1;z<=1;z++)loaded.add(x+","+z);
        WorldBorder border=proxy(WorldBorder.class,(p,m,a)->switch(m.getName()){case "getSize"->100000.0;case "getCenter"->new Location(world,0,0,0);default->fallback(p,m,a);});
        world=proxy(World.class,(p,m,a)->switch(m.getName()) {
            case "getMinHeight"->-64;case "getMaxHeight"->320;case "getWorldBorder"->border;
            case "isChunkLoaded"->loaded.contains(a[0]+","+a[1]);
            case "getChunkAt"->{if(!loaded.contains(a[0]+","+a[1]))throw new AssertionError("Attempted unloaded chunk access");yield proxy(Chunk.class,(cp,cm,ca)->cm.getName().equals("getEntities")?entities.toArray(Entity[]::new):fallback(cp,cm,ca));}
            case "getBlockAt"->{int x=(int)a[0],y=(int)a[1],z=(int)a[2];if(!loaded.contains((x>>4)+","+(z>>4)))throw new AssertionError("Unloaded block access");yield block(x,y,z);}
            default->fallback(p,m,a);
        });
        eye=new Location(world,.5,101.62,.5);direction=new Vector(1,0,0);
    }
    static LoadedRaycast.Hit cast() {
        Player player=proxy(Player.class,(p,m,a)-> {
            if(m.getName().equals("getEyeLocation")){Location l=eye.clone();l.setDirection(direction);return l;}
            return fallback(p,m,a);
        });
        return LoadedRaycast.cast(player);
    }
    static void solid(int x,int y,int z) {shapes.put(x+","+y+","+z,new BoundingBox(x,y,z,x+1,y+1,z+1));}
    static Entity entity(BoundingBox box) {
        UUID id=UUID.randomUUID();return proxy(Entity.class,(p,m,a)->switch(m.getName()){case "getUniqueId"->id;case "isValid"->true;case "getBoundingBox"->box;default->fallback(p,m,a);});
    }
    public static void main(String[] args) {
        setup();solid(160,101,0);check(cast()!=null && cast().block().getX()==160,"160-block target");
        Entity near=entity(new BoundingBox(10,100,0,11,103,1));entities.add(near);check(cast().entity()==near,"nearest entity before distant block");
        solid(5,101,0);check(cast().block().getX()==5,"wall occludes entity");
        setup();solid(160,101,0);loaded.remove("3,0");check(cast()==null,"unloaded gap stops the ray");
        setup();direction=new Vector(0,1,0);check(cast()==null,"sky miss terminates");
        setup();direction=new Vector(0,-1,0);solid(0,99,0);check(cast().block().getY()==99,"vertical downward target");
        setup();direction=new Vector(-1,0,0);solid(-10,101,0);check(cast().block().getX()==-10,"negative-coordinate traversal");
        setup();eye.setY(101.75);shapes.put("5,101,0",new BoundingBox(5,101,0,6,101.5,1));solid(10,101,0);check(cast().block().getX()==10,"ray passes above slab collision shape");
        eye.setY(101.25);check(cast().block().getX()==5,"ray hits lower slab shape");
        setup();solid(10,99,0);Location foot=Destinations.near(world,new Vector(10.5,100,.5),0,1);check(foot!=null && foot.getY()==100,"resolves standing surface");
        solid(10,100,0);check(!Destinations.clear(new Location(world,10.5,100,.5)),"rejects obstructed standing body");
        setup();for(int x=0;x<=8;x++)for(int z=0;z<=8;z++)solid(x,99,z);
        Wolf entrant=proxy(Wolf.class,(p,m,a)->switch(m.getName()){case "getLocation"->new Location(world,-.25,100,4.5);case "getWorld"->world;case "getWidth"->.62;case "getHeight"->.9;default->fallback(p,m,a);});
        var guard=new GuardRegion(0,99,0,8,104,8);var entry=AutomaticOrders.entry(entrant,guard);
        check(entry!=null&&GuardNavigation.settled(guard,entry,.62,.9)&&entry.getX()>=1.5,"guard entry resolves walkable footing safely inside the nearest face");
        var single=new GuardRegion(0,99,4,0,102,4);entry=AutomaticOrders.entry(entrant,single);
        check(entry!=null&&GuardNavigation.settled(single,entry,.62,.9)&&entry.getX()==.5,"guard entry handles a single-block footprint without targeting outside");
        System.out.println("Geometry checks passed: "+count);
    }
}
