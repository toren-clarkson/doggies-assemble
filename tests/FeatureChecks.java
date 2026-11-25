package local.dogwand;

import java.util.*;
import java.lang.reflect.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.craftbukkit.persistence.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;

public final class FeatureChecks {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    @SuppressWarnings("unchecked")static <T>T entity(Class<T> type,boolean tamed){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->switch(m.getName()){case "isValid"->true;case "isDead","isInvulnerable"->false;case "isTamed"->tamed;default->null;});}
    static WandMode mode(String id,String name){return new WandMode(){public String id(){return id;}public String name(){return name;}public String description(){return "A compact description for "+name+" that wraps into readable tooltip lines.";}public void leftClick(Player p){}};}
    static org.bukkit.inventory.ItemStack meat(int count)throws Exception{
        var map=net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.FOOD,new net.minecraft.world.food.FoodProperties(3,.3f,false)).set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build();
        var ctor=net.minecraft.world.item.ItemStack.class.getDeclaredConstructor(net.minecraft.core.Holder.class,int.class,net.minecraft.core.component.PatchedDataComponentMap.class);ctor.setAccessible(true);
        var nativeItem=(net.minecraft.world.item.ItemStack)ctor.newInstance(net.minecraft.world.item.Items.BEEF.builtInRegistryHolder(),count,new net.minecraft.core.component.PatchedDataComponentMap(map));
        return org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(nativeItem);
    }
    public static void main(String[] args)throws Exception{
        var region=GuardRegion.between(10,70,20,-2,60,3);
        check(region.equals(new GuardRegion(-2,60,3,10,70,20)),"reversed 3D corner normalization");
        check(region.contains(-2,60,3)&&region.contains(10.99,70.99,20.99),"both corner blocks included");
        check(!region.contains(11,65,10)&&!region.contains(0,71,10)&&!region.contains(0,65,21),"exclusive outer faces on all axes");
        check(region.bodyFits(new Location(null,0,60,10),.62,.9),"dog body fits in guard interior");
        check(!region.bodyFits(new Location(null,10.9,65,10),.62,.9),"body cannot cross horizontal boundary");
        check(!region.bodyFits(new Location(null,0,70.5,10),.62,.9),"body cannot cross vertical boundary");
        check(GuardRegion.decode(region.encode()).equals(region),"guard region persists exactly");
        var rng=new Random(3);boolean samples=true;for(int i=0;i<1000;i++){var p=region.sample(rng);samples&=region.contains(p.getX(),p.getY(),p.getZ());}check(samples,"roam samples remain inside prism");
        check(!AutomaticOrders.candidate(entity(Player.class,false),false)&&!AutomaticOrders.candidate(entity(Player.class,false),true),"automatic targeting never selects players");
        check(AutomaticOrders.candidate(entity(Zombie.class,false),true),"hostile scan accepts enemy mobs");
        check(!AutomaticOrders.candidate(entity(Sheep.class,false),true)&&AutomaticOrders.candidate(entity(Sheep.class,false),false),"all-mobs setting adds passive mobs");
        check(!AutomaticOrders.candidate(entity(Wolf.class,true),false),"automatic targeting protects tamed pets");
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID(),wand=UUID.randomUUID();
        var guard=new DogCommands.Order(owner,7,DogCommands.Kind.GUARD,world,1,62,4,null,wand,region);
        check(DogCommands.Order.decode(guard.encode()).equals(guard),"guard order and issuing wand survive restart");
        var follow=new DogCommands.Order(owner,8,DogCommands.Kind.FOLLOW_ALL,world,1,62,4,null,wand,null);
        check(DogCommands.Order.decode(follow.encode()).equals(follow),"auto-target order survives restart");
        String old="2|"+owner+"|2|STAY|"+world+"|1.0|62.0|4.0|-";
        check(DogCommands.Order.decode(old).kind()==DogCommands.Kind.STAY,"1.1.0 saved orders remain readable");
        var modes=new ModeRegistry(new NamespacedKey("dogwand","mode"));
        for(String id:List.of("point_order","sit_stand","highlight","auto_target","feed","guard"))modes.register(mode(id,id));modes.registerLast(mode("release","Release Orders"));
        var first=new CraftPersistentDataContainer(new CraftPersistentDataTypeRegistry());var second=new CraftPersistentDataContainer(new CraftPersistentDataTypeRegistry());
        modes.cycle(first);check(modes.current(first).id().equals("sit_stand")&&modes.current(second).id().equals("point_order"),"different item containers remember independent modes");
        var restored=new CraftPersistentDataContainer(first.getRaw(),new CraftPersistentDataTypeRegistry());check(modes.current(restored).id().equals("sit_stand"),"selected item mode persists through serialization");
        var selected=modes.all().get(3);var lore=WandText.lore(modes,selected,selected.description(),4);
        check(modes.all().stream().allMatch(m->lore.stream().anyMatch(c->((TextComponent)c).content().contains(m.name()))),"tooltip lists every mode");
        check(lore.stream().anyMatch(c->((TextComponent)c).content().startsWith("> ")&&c.hasDecoration(TextDecoration.BOLD)),"selected tooltip row is bold");
        check(WandText.wrap(selected.description(),39).stream().allMatch(line->line.length()<=39),"description fits compact lore lines");
        check(PrivateHighlights.flags((byte)0x03,true)==(byte)0x43,"private glow preserves existing flags");
        check(PrivateHighlights.flags((byte)0x03,false)==(byte)0x03,"expiry restores original metadata");
        check(PrivateHighlights.flags((byte)0x40,false)==(byte)0x40,"does not erase an independently applied glow");
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var bridge=Class.forName("local.dogwand.PrivateHighlights$Bridge").getDeclaredConstructor();bridge.setAccessible(true);bridge.newInstance();
        check(true,"private metadata packet bridge resolves on cached Paper 26.3");
        org.bukkit.World testWorld=(org.bukkit.World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class[]{World.class},(p,m,a)->m.getName().equals("equals")?p==a[0]:m.getName().equals("hashCode")?System.identityHashCode(p):null);
        Map<Integer,org.bukkit.inventory.ItemStack> inventory=new HashMap<>();
        var items=(org.bukkit.inventory.PlayerInventory)Proxy.newProxyInstance(org.bukkit.inventory.PlayerInventory.class.getClassLoader(),new Class[]{org.bukkit.inventory.PlayerInventory.class},(p,m,a)->switch(m.getName()){case "getSize"->41;case "getItem"->inventory.get((Integer)a[0]);case "setItem"->{if(a[1]==null)inventory.remove((Integer)a[0]);else inventory.put((Integer)a[0],(org.bukkit.inventory.ItemStack)a[1]);yield null;}default->null;});
        Player feeder=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()){case "getWorld"->testWorld;case "getLocation"->new Location(testWorld,0,64,0);case "getInventory"->items;default->null;});
        double[] health={10},distance={2};boolean[] blocked={false};
        Wolf patient=(Wolf)Proxy.newProxyInstance(Wolf.class.getClassLoader(),new Class[]{Wolf.class},(p,m,a)->switch(m.getName()){case "getWorld"->testWorld;case "getLocation"->new Location(testWorld,distance[0],64,0);case "isDead"->false;case "getHealth"->health[0];case "getHeight"->.9;case "isBreedItem"->true;case "heal"->{if(!blocked[0])health[0]=Math.min(20,health[0]+(Double)a[0]);yield null;}default->null;});
        inventory.put(0,meat(4));
        var fed=DogFeeding.feed(feeder,List.of(patient),dog->20);check(fed.dogs()==1&&fed.portions()==4&&health[0]==20&&inventory.isEmpty(),"feeding uses item food nutrition and consumes only required meat");
        inventory.put(0,meat(2));health[0]=10;blocked[0]=true;
        fed=DogFeeding.feed(feeder,List.of(patient),dog->20);check(fed.portions()==0&&inventory.get(0).getAmount()==2,"cancelled healing does not consume meat");
        blocked[0]=false;distance[0]=11;fed=DogFeeding.feed(feeder,List.of(patient),dog->20);check(fed.portions()==0,"feeding rejects dogs outside ten-block radius");
        distance[0]=2;health[0]=20;fed=DogFeeding.feed(feeder,List.of(patient),dog->20);check(fed.portions()==0&&inventory.get(0).getAmount()==2,"healthy dogs do not consume meat");

        System.out.println("Feature checks passed: "+checks);
    }
}
