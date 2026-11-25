package local.dogwand;

import java.util.*;
import java.nio.file.*;
import java.io.IOException;
import org.bukkit.*;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

/** Durable owners and positively known locations. Unloaded or offline is never proof of deletion. */
public final class WandLedger {
    private static final class Entry {UUID owner;Set<String> locations=new HashSet<>();boolean retired;int missing=-1;}
    private DogWandPlugin plugin;private final Path file;private final DogRegistry registry;private final java.util.function.Predicate<ItemStack> wand;private final java.util.function.Consumer<UUID> retireOrder;private final NamespacedKey idKey,ownerKey,dogsKey;
    private final Map<UUID,Entry> entries=new HashMap<>();private Map<UUID,Set<String>> scanning;private boolean dirty;
    public WandLedger(DogWandPlugin plugin,DogRegistry registry){this(plugin.getDataFolder().toPath().resolve("wands.yml"),registry,plugin::isWand,id->plugin.dogCommands().retireWand(id));this.plugin=plugin;}
    WandLedger(Path file,DogRegistry registry,java.util.function.Predicate<ItemStack> wand,java.util.function.Consumer<UUID> retireOrder){
        this.file=file;this.registry=registry;this.wand=wand;this.retireOrder=retireOrder;idKey=new NamespacedKey("dogwand","wand_id");ownerKey=new NamespacedKey("dogwand","wand_owner");dogsKey=new NamespacedKey("dogwand","bound_dogs");
        var y=YamlConfiguration.loadConfiguration(file.toFile());var section=y.getConfigurationSection("wands");
        if(section!=null)for(String id:section.getKeys(false))try{Entry e=new Entry();String owner=section.getString(id+".owner");e.owner=owner==null?null:UUID.fromString(owner);e.locations.addAll(section.getStringList(id+".locations"));e.retired=section.getBoolean(id+".retired");entries.put(UUID.fromString(id),e);}catch(RuntimeException ignored){}
    }
    private static UUID uuid(String value){try{return UUID.fromString(value);}catch(RuntimeException ex){return null;}}
    public UUID id(ItemStack item){return !wand.test(item)?null:uuid(item.getItemMeta().getPersistentDataContainer().get(idKey,PersistentDataType.STRING));}
    public UUID owner(ItemStack item){if(!wand.test(item))return null;Entry e=entries.get(id(item));return e!=null&&e.owner!=null?e.owner:uuid(item.getItemMeta().getPersistentDataContainer().get(ownerKey,PersistentDataType.STRING));}
    public boolean retired(UUID id){Entry e=entries.get(id);return e!=null&&e.retired;}
    public boolean owns(Player player,ItemStack item){if(!wand.test(item))return true;observe(item,player.getUniqueId(),null);return player.getUniqueId().equals(owner(item));}
    public boolean foreign(Player player,ItemStack item){boolean[] foreign={false};WandContents.visit(item,wand,child->{observe(child,player.getUniqueId(),null);foreign[0]|=!player.getUniqueId().equals(owner(child));});return foreign[0];}
    private boolean contents(ItemStack item,UUID fallback,String location){return WandContents.visit(item,wand,child->observe(child,fallback,location));}
    public void observe(ItemStack item,UUID fallback,String location){
        if(!wand.test(item))return;var meta=item.getItemMeta();var data=meta.getPersistentDataContainer();UUID id=uuid(data.get(idKey,PersistentDataType.STRING));UUID savedOwner=uuid(data.get(ownerKey,PersistentDataType.STRING));Entry e=id==null?null:entries.get(id);
        if(e!=null&&e.retired){savedOwner=e.owner;id=null;BindingLore.write(meta,dogsKey,List.of());data.remove(new NamespacedKey("dogwand","guard_region"));data.remove(new NamespacedKey("dogwand","auto_target"));data.remove(new NamespacedKey("dogwand","fetch_enabled"));e=null;}
        if(id==null){id=UUID.randomUUID();data.set(idKey,PersistentDataType.STRING,id.toString());}
        if(e==null){e=new Entry();e.owner=savedOwner;entries.put(id,e);dirty=true;}
        Set<UUID> dogs=WandBindings.decode(data.get(dogsKey,PersistentDataType.STRING));
        if(e.owner==null){e.owner=registry.inferOwner(dogs);if(e.owner==null)e.owner=fallback;if(e.owner!=null)dirty=true;}
        if(e.owner!=null)data.set(ownerKey,PersistentDataType.STRING,e.owner.toString());
        if(!meta.equals(item.getItemMeta()))item.setItemMeta(meta);
        registry.bindings(id,dogs);
        if(location!=null){if(scanning!=null)scanning.computeIfAbsent(id,k->new HashSet<>()).add(location);else if(!e.locations.equals(Set.of(location))){e.locations=new HashSet<>(Set.of(location));dirty=true;}}
        e.missing=-1;
    }
    public void inventory(Inventory inventory,String location,UUID fallback){
        if(inventory instanceof DoubleChestInventory doubleChest){Inventory left=doubleChest.getLeftSide(),right=doubleChest.getRightSide();inventory(left,left.getLocation()==null?location:blockLocation(left.getLocation()),fallback);inventory(right,right.getLocation()==null?location:blockLocation(right.getLocation()),fallback);return;}
        for(int slot=0;slot<inventory.getSize();slot++){if(inventory instanceof CraftingInventory&&slot==0)continue;ItemStack item=inventory.getItem(slot);if(contents(item,fallback,location))inventory.setItem(slot,item);}}
    public static String blockLocation(Location p){return "b:"+p.getWorld().getUID()+":"+p.getBlockX()+":"+p.getBlockY()+":"+p.getBlockZ();}
    public static String entityLocation(Entity e){Location p=e.getLocation();return "m:"+e.getWorld().getUID()+":"+(p.getBlockX()>>4)+":"+(p.getBlockZ()>>4)+":"+e.getUniqueId();}
    public String location(Inventory inventory,Player viewer){if(inventory.getType()==org.bukkit.event.inventory.InventoryType.ENDER_CHEST)return "e:"+viewer.getUniqueId();if(inventory instanceof PlayerInventory)return "p:"+viewer.getUniqueId();Location p=inventory.getLocation();return p!=null?blockLocation(p):(inventory instanceof CraftingInventory?"g:":"v:")+viewer.getUniqueId();}
    public void player(Player player,boolean enforce){
        var inventory=player.getInventory();String where="p:"+player.getUniqueId();
        for(int slot=0;slot<inventory.getSize();slot++){var item=inventory.getItem(slot);if(!contents(item,player.getUniqueId(),where))continue;
            if(enforce&&foreign(player,item)){Item drop=player.getWorld().dropItem(player.getLocation(),item.clone(),d->d.setOwner(owner(item)));if(drop.isValid()){inventory.setItem(slot,null);contents(drop.getItemStack(),null,entityLocation(drop));}}
            else inventory.setItem(slot,item);
        }
        ItemStack cursor=player.getItemOnCursor();if(contents(cursor,player.getUniqueId(),where)&&enforce&&foreign(player,cursor)){Item drop=player.getWorld().dropItem(player.getLocation(),cursor.clone(),d->d.setOwner(owner(cursor)));if(drop.isValid()){player.setItemOnCursor(null);contents(drop.getItemStack(),null,entityLocation(drop));}}
        inventory(player.getEnderChest(),"e:"+player.getUniqueId(),player.getUniqueId());
        if(enforce)for(int slot=0;slot<player.getEnderChest().getSize();slot++){ItemStack item=player.getEnderChest().getItem(slot);if(foreign(player,item)){Item drop=player.getWorld().dropItem(player.getLocation(),item.clone(),d->d.setOwner(owner(item)));if(drop.isValid()){player.getEnderChest().setItem(slot,null);contents(drop.getItemStack(),null,entityLocation(drop));}}}
    }
    public void entity(Entity entity){
                    if(entity instanceof Item item){ItemStack stack=item.getItemStack();if(contents(stack,null,entityLocation(item)))item.setItemStack(stack);UUID owner=owner(stack);if(owner!=null)item.setOwner(owner);}
                    else if(entity instanceof InventoryHolder holder&&!(entity instanceof Player))inventory(holder.getInventory(),entityLocation(entity),null);
                    if(entity instanceof ItemFrame frame){ItemStack item=frame.getItem();if(contents(item,null,entityLocation(frame)))frame.setItem(item);}
                    if(entity instanceof ItemDisplay display){ItemStack item=display.getItemStack();if(contents(item,null,entityLocation(display)))display.setItemStack(item);}
                    if(entity instanceof LivingEntity living&&!(entity instanceof Player)){var gear=living.getEquipment();if(gear!=null)for(EquipmentSlot slot:EquipmentSlot.values()){if(slot==EquipmentSlot.SADDLE||slot==EquipmentSlot.BODY)continue;ItemStack item=gear.getItem(slot);if(contents(item,null,entityLocation(entity)))gear.setItem(slot,item);}}
                    if(plugin!=null&&entity instanceof Wolf wolf){ItemStack cargo=plugin.dogCommands().cargo(wolf);if(contents(cargo,wolf.getOwnerUniqueId(),entityLocation(wolf)))plugin.dogCommands().storeCargo(wolf,cargo);}
    }
    public boolean observeContents(ItemStack item,UUID fallback,String location){return contents(item,fallback,location);}
    private boolean readable(String location){try{String[] p=location.split(":");return switch(p[0]){
        case "p","e","g"->Bukkit.getPlayer(UUID.fromString(p[1]))!=null;
        case "b"->{World world=Bukkit.getWorld(UUID.fromString(p[1]));yield world!=null&&world.isChunkLoaded(Integer.parseInt(p[2])>>4,Integer.parseInt(p[4])>>4);}
        case "m"->{World world=Bukkit.getWorld(UUID.fromString(p[1]));yield world!=null&&world.isChunkLoaded(Integer.parseInt(p[2]),Integer.parseInt(p[3]));}
        default->false;};}catch(RuntimeException ex){return false;}}
    public void audit(){
        scanning=new HashMap<>();
        try{
            for(Player player:Bukkit.getOnlinePlayers()){player(player,true);Inventory top=player.getOpenInventory().getTopInventory();inventory(top,location(top,player),null);}
            for(World world:Bukkit.getWorlds()){
                for(Entity entity:world.getEntities())entity(entity);
                for(Chunk chunk:world.getLoadedChunks())for(var tile:chunk.getTileEntities())if(tile instanceof InventoryHolder container)inventory(tile instanceof org.bukkit.block.Chest chest?chest.getBlockInventory():container.getInventory(),blockLocation(tile.getLocation()),null);
            }
            int now=Bukkit.getCurrentTick();
            for(var pair:List.copyOf(entries.entrySet())){Entry e=pair.getValue();if(e.retired)continue;Set<String> found=scanning.get(pair.getKey());
                if(found!=null){if(!e.locations.equals(found)){e.locations=new HashSet<>(found);dirty=true;}e.missing=-1;}
                else if(!e.locations.isEmpty()&&e.locations.stream().allMatch(this::readable)){if(e.missing<0)e.missing=now;else if(now-e.missing>=100)retire(pair.getKey(),null);}
                else e.missing=-1;
            }
        }finally{scanning=null;}flush();
    }
    public boolean retire(UUID id,UUID requester){Entry e=entries.get(id);if(e==null||e.retired||(requester!=null&&!requester.equals(e.owner)))return false;e.retired=true;dirty=true;registry.bindings(id,List.of());retireOrder.accept(id);flush();return true;}
    public List<UUID> owned(UUID player){return entries.entrySet().stream().filter(e->!e.getValue().retired&&player.equals(e.getValue().owner)).map(Map.Entry::getKey).sorted().toList();}
    public void flush(){if(!dirty)return;try{var y=new YamlConfiguration();entries.forEach((id,e)->{String key="wands."+id;y.set(key+".owner",e.owner==null?null:e.owner.toString());y.set(key+".retired",e.retired);y.set(key+".locations",new ArrayList<>(e.locations));});Files.createDirectories(file.getParent());Path tmp=file.resolveSibling("wands.yml.tmp");Files.writeString(tmp,y.saveToString());try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}registry.flush();dirty=false;}catch(IOException ex){java.util.logging.Logger.getLogger("DogWand").log(java.util.logging.Level.SEVERE,"Could not save wand ownership ledger",ex);}}
}
