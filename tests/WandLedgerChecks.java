package local.dogwand;

import java.util.*;
import java.nio.file.*;
import java.lang.reflect.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.persistence.PersistentDataType;

/** Durable owners and deletion reconciliation against real Paper item metadata. */
public final class WandLedgerChecks {
    static int checks,tick;static boolean online=true,loaded=true;static World world;static Player player;static UUID who=UUID.randomUUID(),worldId=UUID.randomUUID();
    static Map<Integer,ItemStack> carried=new HashMap<>();static NamespacedKey marker=new NamespacedKey("dogwand","wand");
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
    @SuppressWarnings("unchecked")static <T>T proxy(Class<T> type,InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},handler);}
    static Object fallback(Object p,Method m,Object[] a){return switch(m.getName()){case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);case "toString"->"WandFixture";default->{if(m.getReturnType()==boolean.class)yield false;if(m.getReturnType()==int.class)yield 0;if(m.getReturnType()==double.class)yield 0.0;yield null;}};}
    static ItemStack wand()throws Exception{ItemStack item=GuardBindingChecks.wand();var meta=item.getItemMeta();meta.getPersistentDataContainer().set(marker,PersistentDataType.BYTE,(byte)1);item.setItemMeta(meta);return item;}
    static ItemStack bundle(ItemStack child)throws Exception{
        var map=net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,1).build();var holder=net.minecraft.world.item.Items.BUNDLE.builtInRegistryHolder();holder.bindComponents(map);
        var ctor=net.minecraft.world.item.ItemStack.class.getDeclaredConstructor(net.minecraft.core.Holder.class,int.class,net.minecraft.core.component.PatchedDataComponentMap.class);ctor.setAccessible(true);var stack=(net.minecraft.world.item.ItemStack)ctor.newInstance(holder,1,new net.minecraft.core.component.PatchedDataComponentMap(map));
        ItemStack item=org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(stack);var meta=(org.bukkit.inventory.meta.BundleMeta)item.getItemMeta();meta.setItems(List.of(child));item.setItemMeta(meta);return item;
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();org.bukkit.craftbukkit.CraftRegistry.setMinecraftRegistry(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        world=proxy(World.class,(p,m,a)->switch(m.getName()){case "getUID"->worldId;case "isChunkLoaded"->loaded;case "getEntities"->List.of();case "getLoadedChunks"->new Chunk[0];default->fallback(p,m,a);});
        PlayerInventory inventory=proxy(PlayerInventory.class,(p,m,a)->switch(m.getName()){case "getSize"->41;case "getItem"->carried.get((Integer)a[0]);case "setItem"->{if(a[1]==null)carried.remove((Integer)a[0]);else carried.put((Integer)a[0],(ItemStack)a[1]);yield null;}default->fallback(p,m,a);});
        Inventory empty=proxy(Inventory.class,(p,m,a)->switch(m.getName()){case "getSize"->0;case "getType"->InventoryType.CRAFTING;default->fallback(p,m,a);});
        InventoryView view=proxy(InventoryView.class,(p,m,a)->switch(m.getName()){case "getTopInventory"->empty;case "getBottomInventory"->inventory;case "getPlayer"->player;case "getType"->InventoryType.CRAFTING;default->fallback(p,m,a);});
        player=proxy(Player.class,(p,m,a)->switch(m.getName()){case "getUniqueId"->who;case "getInventory"->inventory;case "getEnderChest"->empty;case "getOpenInventory"->view;case "getWorld"->world;default->fallback(p,m,a);});
        Server server=proxy(Server.class,(p,m,a)->switch(m.getName()){case "getLogger"->java.util.logging.Logger.getLogger("LedgerFixture");case "getName","getVersion","getBukkitVersion"->"LedgerFixture";case "getCurrentTick"->tick;case "getOnlinePlayers"->online?List.of(player):List.of();case "getPlayer"->online&&who.equals(a[0])?player:null;case "getWorlds"->List.of(world);case "getWorld"->world;default->fallback(p,m,a);});Bukkit.setServer(server);
        Path dir=Files.createTempDirectory("dogwand-ledger-");DogRegistry registry=new DogRegistry(dir.resolve("dogs.yml"));List<UUID> retired=new ArrayList<>();
        java.util.function.Predicate<ItemStack> isWand=item->item!=null&&item.hasItemMeta()&&WandIdentity.matches(item.getItemMeta().getPersistentDataContainer(),marker);
        WandLedger ledger=new WandLedger(dir.resolve("wands.yml"),registry,isWand,retired::add);
        ItemStack first=wand();UUID dog=UUID.randomUUID();registry.observe(dog,who);
        ledger.observe(first,who,"p:"+who);UUID id=ledger.id(first);check(id!=null&&who.equals(ledger.owner(first)),"first legitimate holder gets stable wand identity and owner");
        UUID stranger=UUID.randomUUID();Player other=proxy(Player.class,(p,m,a)->m.getName().equals("getUniqueId")?stranger:fallback(p,m,a));
        check(ledger.owns(player,first)&&!ledger.owns(other,first)&&who.equals(ledger.owner(first)),"foreign interaction cannot claim or use another player's wand");
        var meta=first.getItemMeta();BindingLore.write(meta,new NamespacedKey("dogwand","bound_dogs"),List.of(dog));first.setItemMeta(meta);ledger.observe(first,who,"p:"+who);ledger.flush();
        var saved=new WandLedger(dir.resolve("wands.yml"),registry,isWand,retired::add);check(who.equals(saved.owner(first))&&registry.bound(dog),"owner and active binding survive ledger restart");
        check(!saved.retire(id,stranger)&&registry.bound(dog),"only wand owner can explicitly retire it");
        carried.put(0,first);saved.audit();tick=500;saved.audit();check(!saved.retired(id),"present inventory wand never retires during audits");
        carried.clear();saved.audit();tick+=101;saved.audit();check(saved.retired(id)&&!registry.bound(dog)&&retired.contains(id),"silent inventory deletion retires missing wand and frees dogs after grace");
        saved.observe(first,who,"p:"+who);check(!id.equals(saved.id(first))&&who.equals(saved.owner(first))&&WandBindings.decode(first.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("dogwand","bound_dogs"),PersistentDataType.STRING)).isEmpty(),"recovered retired item becomes a new empty wand owned by original owner");
        ItemStack chest=wand();saved.observe(chest,who,"b:"+worldId+":0:64:0");UUID chestId=saved.id(chest);loaded=false;saved.audit();tick+=1000;saved.audit();check(!saved.retired(chestId),"unloaded storage is never treated as deleted");
        loaded=true;saved.audit();tick+=101;saved.audit();check(saved.retired(chestId),"missing wand in inspectable storage retires after chunk is available");
        ItemStack offline=wand();saved.observe(offline,who,"p:"+who);UUID offlineId=saved.id(offline);online=false;saved.audit();tick+=1000;saved.audit();check(!saved.retired(offlineId),"offline player inventory is never mistaken for deletion");
        online=true;saved.audit();tick+=101;saved.audit();check(saved.retired(offlineId),"external offline deletion reconciles when player returns");
        ItemStack hidden=wand();saved.observe(hidden,who,"v:external-plugin");UUID hiddenId=saved.id(hidden);saved.audit();tick+=1000;saved.audit();check(!saved.retired(hiddenId)&&saved.retire(hiddenId,who),"opaque plugin storage stays safe and owner can retire explicitly");
        ItemStack legacy=wand();meta=legacy.getItemMeta();BindingLore.write(meta,new NamespacedKey("dogwand","bound_dogs"),List.of(dog));legacy.setItemMeta(meta);saved.observe(legacy,stranger,"v:legacy");check(who.equals(saved.owner(legacy)),"legacy ownership inferred from bound dog's known owner");
        ItemStack inner=wand();saved.observe(inner,who,"p:"+who);UUID innerId=saved.id(inner);ItemStack outer=bundle(inner);carried.put(0,outer);saved.audit();tick+=1000;saved.audit();check(!saved.retired(innerId),"wand stored inside an inventory bundle stays registered");
        check(saved.foreign(other,outer)&&!saved.foreign(player,outer),"owner checks inspect nested bundle contents");
        var bundleMeta=(org.bukkit.inventory.meta.BundleMeta)outer.getItemMeta();bundleMeta.setItems(List.of());outer.setItemMeta(bundleMeta);saved.audit();tick+=101;saved.audit();check(saved.retired(innerId),"deleting a nested wand frees its identity after reconciliation");
        for(int clicks=1;clicks<=13;clicks++){int expected=clicks<=5?120-clicks*20:clicks<=10?220-clicks*20:0;BulkSelection.Scope scope=clicks<=5?BulkSelection.Scope.FREE:clicks<=10?BulkSelection.Scope.ALL:clicks==11?BulkSelection.Scope.UNBIND:BulkSelection.Scope.CANCEL;check(BulkSelection.percent(clicks)==expected&&BulkSelection.choice(clicks).scope()==scope,"bulk selection scope and percentage for "+clicks+" clicks");}
        check(BulkSelection.count(3,20)==1&&BulkSelection.count(10,80)==8&&BulkSelection.count(10,0)==0,"fractional selections round up and cancellation binds none");
        var modes=new ModeRegistry(new NamespacedKey("dogwand","mode"));modes.register(new WandMode(){public String id(){return "feed";}public String name(){return "Feed Dogs";}public String description(){return "Feed nearby dogs";}public void leftClick(Player p){}});
        var lore=WandText.lore(modes,modes.all().getFirst(),"Feed nearby dogs",2,List.of("Guarding region | 2"));check(lore.stream().anyMatch(c->c instanceof net.kyori.adventure.text.TextComponent t&&t.content().equals("Guarding region | 2"))&&lore.stream().anyMatch(c->c instanceof net.kyori.adventure.text.TextComponent t&&t.content().contains("Feed Dogs")),"selected-mode instructions and actual orders occupy separate lore sections");
        System.out.println("Wand ledger checks passed: "+checks);
    }
}
