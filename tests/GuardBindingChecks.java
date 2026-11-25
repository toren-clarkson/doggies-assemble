package local.dogwand;

import java.util.*;
import java.nio.file.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.util.Vector;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

/** Regression cases for partial entry, edge sliding, and persisted item count changes. No sockets. */
public final class GuardBindingChecks {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static Location at(double x,double y,double z){return new Location(null,x,y,z);}
    static String plain(Component c){return (c instanceof TextComponent t?t.content():"")+c.children().stream().map(GuardBindingChecks::plain).reduce("",String::concat);}
    static boolean count(org.bukkit.inventory.meta.ItemMeta meta,int n){return meta.lore()!=null&&meta.lore().stream().filter(c->plain(c).matches("\\d+ bound dog\\(s\\)")).map(GuardBindingChecks::plain).toList().equals(List.of(n+" bound dog(s)"));}
    static org.bukkit.inventory.ItemStack wand()throws Exception{
        var defaults=net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,1).build();
        var holder=net.minecraft.world.item.Items.STICK.builtInRegistryHolder();holder.bindComponents(defaults);
        var ctor=net.minecraft.world.item.ItemStack.class.getDeclaredConstructor(net.minecraft.core.Holder.class,int.class,net.minecraft.core.component.PatchedDataComponentMap.class);ctor.setAccessible(true);
        var stack=(net.minecraft.world.item.ItemStack)ctor.newInstance(holder,1,new net.minecraft.core.component.PatchedDataComponentMap(defaults));
        return CraftItemStack.asBukkitCopy(stack);
    }
    public static void main(String[] args)throws Exception{
        var r=new GuardRegion(0,63,0,8,67,8);
        Location outside=at(-.25,64,4.5),edge=at(.31,64,4.5),inside=at(1.5,64,4.5);
        check(outside.toVector().distanceSquared(new Vector(.5,64,4.5))<.85*.85&&!GuardNavigation.settled(r,outside,.62,.9),"old arrival radius cannot accept a partly outside dog");
        check(r.bodyFits(edge,.62,.9)&&!GuardNavigation.settled(r,edge,.62,.9),"entry continues beyond touching the border");
        check(GuardNavigation.settled(r,inside,.62,.9),"interior dog can switch to patrol");
        boolean aims=true;
        for(Vector start:List.of(new Vector(-5,64,4),new Vector(15,64,4),new Vector(4,64,-5),new Vector(4,64,15),new Vector(-5,64,-5))){var aim=GuardNavigation.entryAim(r,start);aims&=GuardNavigation.settled(r,aim.toLocation(null),.62,.9);}
        check(aims,"entry aims safely inside every horizontal face and corner");
        var narrow=new GuardRegion(0,63,0,0,65,0);
        check(GuardNavigation.settled(narrow,GuardNavigation.entryAim(narrow,new Vector(-3,64,-3)).toLocation(null),.62,.9),"one-block-wide region still has a valid entry aim");
        var to=at(-.2,64,5);to.setYaw(35);to.setPitch(-15);
        var clipped=GuardNavigation.clipMove(r,edge,to,.62,.9);
        check(!r.bodyFits(to,.62,.9)&&r.bodyFits(clipped,.62,.9)&&clipped.getZ()==5&&clipped.getX()==edge.getX(),"outward movement clips while tangential movement survives");
        check(clipped.getYaw()==35&&clipped.getPitch()==-15,"clipping preserves look direction");
        var corner=at(.31,64,.31);var along=GuardNavigation.clipMove(r,corner,at(-.1,64,.5),.62,.9);
        check(along.getX()==.31&&along.getZ()==.5,"corner contact permits movement along the other axis");
        check(GuardNavigation.clipMove(r,edge,inside,.62,.9)==inside,"inward movement stays untouched");
        check(GuardNavigation.clipMove(r,outside,edge,.62,.9)==edge,"outside dogs can cross the boundary to enter");
        var ceiling=GuardNavigation.clipMove(r,at(4,67,4),at(4.2,68,4.3),.62,.9);
        check(r.bodyFits(ceiling,.62,.9)&&ceiling.getX()==4.2&&ceiling.getZ()==4.3,"vertical clipping also preserves horizontal movement");
        var velocity=GuardNavigation.finishEntry(outside,inside,new Vector(-1,-.08,0));
        check(velocity.getX()>0&&Math.abs(velocity.getX()-.08)<1e-8&&velocity.getY()==-.08,"final physical entry step is inward, bounded and preserves falling");
        Location simulated=outside.clone();for(int i=0;i<40&&!GuardNavigation.settled(r,simulated,.62,.9);i++)simulated.add(GuardNavigation.finishEntry(simulated,inside,new Vector()));
        check(GuardNavigation.settled(r,simulated,.62,.9),"small physical steps complete entry instead of freezing near target");

        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        org.bukkit.craftbukkit.CraftRegistry.setMinecraftRegistry(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        var item=wand();var meta=item.getItemMeta();var key=new NamespacedKey("dogwand","bound_dogs");
        var help=Component.text("Guard Region",NamedTextColor.BLUE).decorate(TextDecoration.BOLD);
        meta.lore(List.of(help,Component.text("3 bound dog(s)")));
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        BindingLore.write(meta,key,List.of(a,b,b));item.setItemMeta(meta);meta=item.getItemMeta();
        check(WandBindings.decode(meta.getPersistentDataContainer().get(key,PersistentDataType.STRING)).equals(Set.of(a,b))&&count(meta,2),"real Paper item stores unique bindings and matching count together");
        check(meta.lore().getFirst().equals(help),"binding count update preserves styled mode description");
        BindingLore.write(meta,key,List.of(b));item.setItemMeta(meta);
        check(count(item.getItemMeta(),1),"unbinding immediately changes lore on the stored item");
        var duplicate=item.clone();BindingLore.write(duplicate.getItemMeta(),key,List.of());
        check(count(item.getItemMeta(),1),"other item snapshots cannot change this wand's count");
        var folder=Files.createTempDirectory("dogwand-count-");var file=folder.resolve("dogs.yml");var registry=new DogRegistry(file);registry.died(b);
        meta=item.getItemMeta();BindingLore.write(meta,key,registry.living(WandBindings.decode(meta.getPersistentDataContainer().get(key,PersistentDataType.STRING))));item.setItemMeta(meta);
        check(count(item.getItemMeta(),0)&&item.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING).isEmpty(),"death removes UUID and displayed count on a real Paper item");
        meta=item.getItemMeta();meta.lore(List.of(help,Component.text("9 bound dog(s)"),Component.text("3 bound dogs")));item.setItemMeta(meta);
        meta=item.getItemMeta();BindingLore.write(meta,key,Set.of());item.setItemMeta(meta);
        check(count(item.getItemMeta(),0)&&item.getItemMeta().lore().size()==2,"stale and duplicate count rows repair even when binding IDs already match");
        check(BindingLore.withCount(null,0).size()==1,"legacy item without lore gains a count");
        var unchanged=item.getItemMeta().lore();check(BindingLore.withCount(unchanged,0).equals(unchanged),"refresh is stable when count already matches");
        Files.delete(file);Files.delete(folder);System.out.println("Guard/binding checks passed: "+checks);
    }
}
