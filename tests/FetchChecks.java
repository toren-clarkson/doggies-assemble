package local.dogwand;

import java.util.*;
import java.lang.reflect.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.craftbukkit.persistence.*;
import org.bukkit.util.BoundingBox;

/** Actual production coordinator and item transfer methods against deterministic event/world adapters. */
public final class FetchChecks {
    static int checks,tick;static boolean denyPickup,denyDrop;static World world;static Player owner;static UUID ownerId=UUID.randomUUID();
    static Map<UUID,Drop> entities=new HashMap<>();static List<Drop> spawned=new ArrayList<>();static List<Mob> threats=new ArrayList<>();
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    @SuppressWarnings("unchecked")static <T>T proxy(Class<T> type,InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},handler);}
    static Object fallback(Object p,Method m,Object[] a){return switch(m.getName()){case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);case "toString"->"FetchFixture";default->{if(m.getReturnType()==boolean.class)yield false;if(m.getReturnType()==int.class)yield 0;if(m.getReturnType()==double.class)yield 0.0;yield null;}};}
    static class Drop {
        UUID id=UUID.randomUUID(),reserved;boolean alive=true,mobPickup=true;int delay;ItemStack stack;Location location;Item item;
        CraftPersistentDataContainer data=new CraftPersistentDataContainer(new CraftPersistentDataTypeRegistry());
        Drop(ItemStack stack,Location at){this.stack=stack;this.location=at;item=proxy(Item.class,(p,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "getWorld"->world;case "getLocation"->location.clone();case "isValid"->alive;case "isDead"->!alive;
            case "getItemStack"->this.stack;case "getOwner"->reserved;case "setOwner"->{reserved=(UUID)a[0];yield null;}
            case "getPersistentDataContainer"->data;
            case "getPickupDelay"->delay;case "setPickupDelay"->{delay=(Integer)a[0];yield null;}case "canMobPickup"->mobPickup;
            case "getBoundingBox"->new BoundingBox(location.getX()-.12,64,location.getZ()-.12,location.getX()+.12,64.25,location.getZ()+.12);
            case "remove"->{alive=false;yield null;}default->fallback(p,m,a);
        });entities.put(id,this);}
    }
    static class Dog {
        UUID id=UUID.randomUUID();Location location=new Location(world,0,64,0);CraftPersistentDataContainer data=new CraftPersistentDataContainer(new CraftPersistentDataTypeRegistry());Wolf dog;
        Dog(){dog=proxy(Wolf.class,(p,m,a)->switch(m.getName()){case "getUniqueId"->id;case "getWorld"->world;case "getLocation"->location.clone();case "getPersistentDataContainer"->data;
            case "getOwnerUniqueId"->ownerId;case "isValid","isTamed","hasLineOfSight"->true;case "isDead","isSitting"->false;
            case "getBoundingBox"->new BoundingBox(location.getX()-.31,64,location.getZ()-.31,location.getX()+.31,64.9,location.getZ()+.31);default->fallback(p,m,a);});}
    }
    static ItemStack stack()throws Exception{
        var defaults=net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build();
        var holder=net.minecraft.world.item.Items.DIAMOND.builtInRegistryHolder();holder.bindComponents(defaults);
        var ctor=net.minecraft.world.item.ItemStack.class.getDeclaredConstructor(net.minecraft.core.Holder.class,int.class,net.minecraft.core.component.PatchedDataComponentMap.class);ctor.setAccessible(true);
        var nativeItem=(net.minecraft.world.item.ItemStack)ctor.newInstance(holder,32,new net.minecraft.core.component.PatchedDataComponentMap(defaults));return org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(nativeItem);
    }
    static DogCommands.Order order(List<UUID> items){return new DogCommands.Order(ownerId,1,DogCommands.Kind.FETCH,world.getUID(),0,64,0,null,UUID.randomUUID(),null,items);}
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        org.bukkit.craftbukkit.CraftRegistry.setMinecraftRegistry(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        UUID worldId=UUID.randomUUID();world=proxy(World.class,(p,m,a)->switch(m.getName()){
            case "getUID"->worldId;case "getEntitiesByClass"->threats;case "getNearbyEntities"->entities.values().stream().filter(d->d.alive).map(d->d.item).toList();case "dropItem"->{Drop drop=new Drop(((ItemStack)a[1]).clone(),((Location)a[0]).clone());if(a.length>2)((java.util.function.Consumer<Item>)a[2]).accept(drop.item);spawned.add(drop);yield drop.item;}default->fallback(p,m,a);});
        owner=proxy(Player.class,(p,m,a)->switch(m.getName()){case "getUniqueId"->ownerId;case "getWorld"->world;case "getLocation"->new Location(world,0,64,0);default->fallback(p,m,a);});
        PluginManager manager=proxy(PluginManager.class,(p,m,a)->{if(m.getName().equals("callEvent")){if(a[0] instanceof EntityPickupItemEvent e&&denyPickup)e.setCancelled(true);if(a[0] instanceof EntityDropItemEvent e&&denyDrop)e.setCancelled(true);return null;}return fallback(p,m,a);});
        Server server=proxy(Server.class,(p,m,a)->switch(m.getName()){case "getLogger"->java.util.logging.Logger.getLogger("FetchFixture");case "getName","getVersion","getBukkitVersion"->"FetchFixture";case "getPluginManager"->manager;case "getCurrentTick"->tick;
            case "getEntity"->{Drop d=entities.get(a[0]);yield d==null?threats.stream().filter(mob->mob.getUniqueId().equals(a[0])).findFirst().orElse(null):d.alive?d.item:null;}case "getPlayer"->ownerId.equals(a[0])?owner:null;
            case "getUnsafe"->org.bukkit.craftbukkit.util.CraftMagicNumbers.INSTANCE;default->fallback(p,m,a);});Bukkit.setServer(server);
        var fetch=new FetchOrders(new NamespacedKey("dogwand","fetch_cargo"),new NamespacedKey("dogwand","fetch_cargo_owner"));
        Drop first=new Drop(stack(),new Location(world,4,64,0)),second=new Drop(stack(),new Location(world,6,64,0));Dog a=new Dog(),b=new Dog(),c=new Dog();
        var metadata=first.stack.getItemMeta();metadata.displayName(net.kyori.adventure.text.Component.text("Fetched loot"));metadata.getPersistentDataContainer().set(new NamespacedKey("fixture","custom"),org.bukkit.persistence.PersistentDataType.STRING,"preserve");first.stack.setItemMeta(metadata);
        var order=order(List.of(first.id,second.id));var members=List.of(new FetchOrders.Member(a.dog,order),new FetchOrders.Member(b.dog,order),new FetchOrders.Member(c.dog,order));fetch.plan(members);
        List<Item> targets=members.stream().map(m->fetch.target(m.dog())).filter(Objects::nonNull).toList();check(targets.size()==2&&targets.stream().map(Entity::getUniqueId).distinct().count()==2,"one dog per item entity; spare dog remains unassigned");
        Dog assigned=members.stream().filter(m->fetch.target(m.dog())==first.item).map(m->m.dog()==a.dog?a:m.dog()==b.dog?b:c).findFirst().orElseThrow();assigned.location=first.location.clone();
        denyPickup=true;check(!fetch.pickup(assigned.dog,first.item,owner)&&first.alive&&!fetch.carrying(assigned.dog),"cancelled pickup leaves original stack and no cargo");
        denyPickup=false;tick=201;fetch.plan(members);
        assigned=members.stream().filter(m->fetch.target(m.dog())==first.item).map(m->m.dog()==a.dog?a:m.dog()==b.dog?b:c).findFirst().orElseThrow();assigned.location=first.location.clone();
        check(fetch.pickup(assigned.dog,first.item,owner)&&!first.alive&&fetch.carrying(assigned.dog),"successful pickup moves full stack into durable wolf cargo");
        var restored=new FetchOrders(new NamespacedKey("dogwand","fetch_cargo"),new NamespacedKey("dogwand","fetch_cargo_owner"));check(restored.carrying(assigned.dog),"coordinator reload retains item cargo on wolf");
        assigned.location=new Location(world,0,64,0);denyDrop=true;check(!restored.deliver(assigned.dog,owner)&&restored.carrying(assigned.dog)&&!spawned.getLast().alive,"cancelled drop retains cargo and removes attempted duplicate");
        denyDrop=false;check(restored.deliver(assigned.dog,owner)&&!restored.carrying(assigned.dog),"delivery clears cargo only after successful drop");
        Drop delivered=spawned.getLast();check(delivered.stack.getAmount()==32&&delivered.stack.isSimilar(first.stack)&&ownerId.equals(delivered.reserved)&&delivered.location.distanceSquared(owner.getLocation())<1,"delivery preserves custom stack data and drops at owner feet with owner reservation");
        check(!restored.deliver(assigned.dog,owner)&&spawned.getLast()==delivered,"completed delivery cannot duplicate items");
        second.alive=false;restored.plan(members);check(members.stream().allMatch(m->restored.idle(m.dog())),"continuous fetching idles without picking up its completed delivery");
        Drop later=new Drop(stack(),new Location(world,8,64,0));restored.plan(members);check(members.stream().filter(m->restored.target(m.dog())==later.item).count()==1,"continuous fetching discovers a newly dropped item without another click");later.alive=false;
        Drop merged=new Drop(stack(),new Location(world,1,64,0));FetchOrders.preserveDelivery(delivered.item,merged.item);check(!FetchOrders.selectable(merged.item,owner),"delivery merge retains loop prevention");merged.alive=false;
        Drop protectedItem=new Drop(stack(),new Location(world,2,64,0));protectedItem.reserved=UUID.randomUUID();check(!FetchOrders.selectable(protectedItem.item,owner),"fetch respects another player's item reservation");
        protectedItem.reserved=null;protectedItem.mobPickup=false;check(!FetchOrders.selectable(protectedItem.item,owner),"fetch respects mob-pickup prohibition");
        protectedItem.mobPickup=true;protectedItem.location=new Location(world,20,64,0);check(FetchOrders.available(protectedItem.item,owner),"twenty-block sphere includes exact boundary");protectedItem.location.setX(20.1);check(!FetchOrders.available(protectedItem.item,owner),"items beyond twenty blocks excluded");
        protectedItem.location.setX(2);protectedItem.delay=10;check(FetchOrders.selectable(protectedItem.item,owner)&&!FetchOrders.available(protectedItem.item,owner),"fresh drops join batch but wait for pickup delay");
        var delayed=List.of(new FetchOrders.Member(a.dog,order(List.of(protectedItem.id))));restored.plan(delayed);check(restored.idle(a.dog)&&restored.target(a.dog)==null,"continuous fetching waits for pickup delay");
        protectedItem.delay=0;restored.plan(delayed);a.location=protectedItem.location.clone();check(restored.pickup(a.dog,protectedItem.item,owner),"delayed item can be picked up once available");
        var drops=new ArrayList<ItemStack>();restored.death(a.dog,drops);check(drops.size()==1&&drops.getFirst().getAmount()==32&&!restored.carrying(a.dog),"dog death releases carried stack exactly once");restored.death(a.dog,drops);check(drops.size()==1,"repeated death cleanup cannot duplicate cargo");
        var planner=new AutomaticOrders();var autoOrder=new DogCommands.Order(ownerId,1,DogCommands.Kind.FOLLOW_HOSTILE,worldId,0,64,0,null);
        var army=java.util.stream.IntStream.range(0,32).mapToObj(i->new Dog()).map(d->new AutomaticOrders.Member(d.dog,autoOrder)).toList();
        for(int i=0;i<3;i++){UUID id=UUID.randomUUID();double x=2+i*5;threats.add(proxy(Zombie.class,(p,m,v)->switch(m.getName()){case "getUniqueId"->id;case "getWorld"->world;case "getLocation"->new Location(world,x,64,1);case "isValid"->true;default->fallback(p,m,v);}));}
        planner.plan(army);var loads=new HashMap<UUID,Integer>();army.forEach(m->{var mob=planner.target(m.dog(),m.order());if(mob!=null)loads.merge(mob.getUniqueId(),1,Integer::sum);});
        check(loads.size()==3&&Collections.max(loads.values())-Collections.min(loads.values())<=1,"production auto planner splits army across multiple nearby mobs");
        check(army.stream().allMatch(m->planner.target(m.dog(),m.order())!=null),"all standing auto-attack dogs receive a target without reserves");
        var selectedOrder=new DogCommands.Order(ownerId,3,DogCommands.Kind.ATTACK,worldId,0,64,0,threats.getFirst().getUniqueId(),UUID.randomUUID(),null,threats.stream().map(Entity::getUniqueId).toList());
        var selectedArmy=army.stream().map(m->new AutomaticOrders.Member(m.dog(),selectedOrder)).toList();planner.plan(selectedArmy);var selectedLoads=new HashMap<UUID,Integer>();selectedArmy.forEach(m->{var target=planner.target(m.dog(),m.order());if(target!=null)selectedLoads.merge(target.getUniqueId(),1,Integer::sum);});
        check(selectedLoads.size()==3&&selectedLoads.values().stream().mapToInt(Integer::intValue).sum()==32&&Collections.max(selectedLoads.values())-Collections.min(selectedLoads.values())<=1,"point selections attack all selected mobs simultaneously with equal groups");
        threats.removeLast();planner.plan(selectedArmy);var survivors=new HashMap<UUID,Integer>();selectedArmy.forEach(m->{var target=planner.target(m.dog(),m.order());if(target!=null)survivors.merge(target.getUniqueId(),1,Integer::sum);});check(survivors.size()==2&&survivors.values().stream().allMatch(n->n==16),"missing selected mob redistributes the entire pack across surviving mobs");
        var region=new GuardRegion(0,63,0,8,68,8);var guardOrder=new DogCommands.Order(ownerId,2,DogCommands.Kind.GUARD,worldId,1,64,1,null,UUID.randomUUID(),region);
        var guards=army.stream().map(m->new AutomaticOrders.Member(m.dog(),guardOrder)).toList();planner.plan(guards);
        var guardTargets=guards.stream().map(m->planner.target(m.dog(),m.order())).filter(Objects::nonNull).map(Entity::getUniqueId).collect(java.util.stream.Collectors.toSet());
        check(guardTargets.size()==2,"production guard planner splits defenders and excludes mobs outside the prism");
        threats.clear();planner.plan(army);check(army.stream().allMatch(m->planner.target(m.dog(),m.order())==null),"production auto planner drops all combat assignments when no mobs remain");
        System.out.println("Fetch checks passed: "+checks);
    }
}
