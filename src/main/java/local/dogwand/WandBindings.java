package local.dogwand;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

/** Bindings live on the item; a dog may appear on any number of independent wands. */
public final class WandBindings {
    private final DogWandPlugin plugin;
    private final NamespacedKey idKey,dogsKey,uiKey;
    private final DogRegistry registry;
    private final WandLedger ledger;
    public record Selection(UUID id,Set<UUID> dogs,EquipmentSlot hand) {}
    public record RegionSelection(UUID world,GuardRegion region){}
    public WandBindings(DogWandPlugin plugin) {
        this.plugin=plugin;idKey=new NamespacedKey(plugin,"wand_id");dogsKey=new NamespacedKey(plugin,"bound_dogs");uiKey=new NamespacedKey(plugin,"wand_ui_version");
        registry=new DogRegistry(plugin.getDataFolder().toPath().resolve("known-dogs.yml"));
        ledger=new WandLedger(plugin,registry);
    }
    public DogRegistry registry(){return registry;}
    public WandLedger ledger(){return ledger;}
    public void flush(){try{registry.flush();}catch(java.io.IOException ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not save dog registry",ex);}}
    public Set<UUID> dogs(ItemStack item){if(!plugin.isWand(item))return Set.of();var data=item.getItemMeta().getPersistentDataContainer();return registry.living(data.has(dogsKey,PersistentDataType.STRING)?decode(data.get(dogsKey,PersistentDataType.STRING)):Set.of());}
    public boolean prune(ItemStack item){
        if(!plugin.isWand(item))return false;var previous=item.getItemMeta();ledger.observe(item,null,null);boolean normalized=!previous.equals(item.getItemMeta());var meta=item.getItemMeta();var data=meta.getPersistentDataContainer();
        Set<UUID> ids=data.has(dogsKey,PersistentDataType.STRING)?decode(data.get(dogsKey,PersistentDataType.STRING)):Set.of(),living=registry.living(ids);
        try{registry.bindings(UUID.fromString(data.get(idKey,PersistentDataType.STRING)),living);}catch(RuntimeException ignored){}
        if(ids.equals(living)&&BindingLore.withCount(meta.lore(),living.size()).equals(meta.lore()))return normalized;
        BindingLore.write(meta,dogsKey,living);item.setItemMeta(meta);return true;
    }
    public void pruneInventory(Inventory inventory){for(int slot=0;slot<inventory.getSize();slot++){var item=inventory.getItem(slot);if(prune(item))inventory.setItem(slot,item);}}
    public void died(Wolf wolf){
        if(!wolf.isTamed()&&!registry.known(wolf.getUniqueId()))return;
        try{registry.died(wolf.getUniqueId());}catch(java.io.IOException ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not persist dead dog cleanup",ex);}
        for(Player player:Bukkit.getOnlinePlayers()){pruneInventory(player.getInventory());pruneInventory(player.getOpenInventory().getTopInventory());}
    }
    public static Set<UUID> decode(String text) {
        Set<UUID> ids=new LinkedHashSet<>();
        if(text!=null) for(String token:text.split(",")) try {ids.add(UUID.fromString(token));}catch(IllegalArgumentException ignored) {}
        return ids;
    }
    public static String encode(Collection<UUID> ids) {return String.join(",",ids.stream().map(UUID::toString).toList());}
    public Selection selection(Player player) {
        EquipmentSlot hand=plugin.isWand(player.getInventory().getItemInMainHand())?EquipmentSlot.HAND:EquipmentSlot.OFF_HAND;
        ItemStack item=player.getInventory().getItem(hand);
        if(!plugin.isWand(item)) return null;
        if(!ledger.owns(player,item))return null;
        ledger.observe(item,player.getUniqueId(),"p:"+player.getUniqueId());
        if(prune(item))player.getInventory().setItem(hand,item);
        var meta=item.getItemMeta();var data=meta.getPersistentDataContainer();
        String saved=data.has(idKey,PersistentDataType.STRING)?data.get(idKey,PersistentDataType.STRING):null;
        UUID id;
        try{id=UUID.fromString(saved);}catch(Exception ex){id=UUID.randomUUID();data.set(idKey,PersistentDataType.STRING,id.toString());item.setItemMeta(meta);player.getInventory().setItem(hand,item);}
        String encoded=data.has(dogsKey,PersistentDataType.STRING)?data.get(dogsKey,PersistentDataType.STRING):null;
        registry.bindings(id,decode(encoded));
        return new Selection(id,Collections.unmodifiableSet(decode(encoded)),hand);
    }
    public void bind(Player player,Entity directTarget) {
        Selection selection=selection(player);if(selection==null)return;
        Entity hit=directTarget;
        if(hit==null){var ray=LoadedRaycast.cast(player);hit=ray==null?null:ray.entity();}
        if(!(hit instanceof Wolf wolf) || !wolf.isTamed() || !player.getUniqueId().equals(wolf.getOwnerUniqueId())) {
            plugin.feedback(player,"Point at one of your tamed dogs to bind it.");return;
        }
        registry.observe(wolf);
        Set<UUID> ids=new LinkedHashSet<>(selection.dogs());
        boolean removed=ids.remove(wolf.getUniqueId());
        if(removed){if(!plugin.dogCommands().unbind(player,wolf,selection.id()))return;}
        else ids.add(wolf.getUniqueId());
        ItemStack item=player.getInventory().getItem(selection.hand());var meta=item.getItemMeta();
        BindingLore.write(meta,dogsKey,ids);item.setItemMeta(meta);
        player.getInventory().setItem(selection.hand(),item);
        registry.bindings(selection.id(),ids);flush();
        plugin.feedback(player,(removed?"Unbound ":"Bound ")+wolf.getName()+" | "+ids.size()+" dog(s) on this wand");
        player.playSound(player.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,.4f,1.4f);
    }
    public void bindAll(Player player){bindPercent(player,100);}
    public void bindPercent(Player player,int percent){
        bindPercent(player,percent,false);
    }
    private void bindPercent(Player player,int percent,boolean all){
        if(percent<=0){plugin.feedback(player,"Bulk binding cancelled.");return;}
        var selection=selection(player);if(selection==null)return;plugin.dogCommands().observeLoaded();
        List<UUID> free=new ArrayList<>(registry.owned(player.getUniqueId()).stream().filter(id->all||!registry.bound(id)).toList());Collections.shuffle(free);
        int take=BulkSelection.count(free.size(),percent);Set<UUID> ids=new LinkedHashSet<>(selection.dogs());int before=ids.size();ids.addAll(free.subList(0,take));
        var item=player.getInventory().getItem(selection.hand());var meta=item.getItemMeta();BindingLore.write(meta,dogsKey,ids);item.setItemMeta(meta);player.getInventory().setItem(selection.hand(),item);registry.bindings(selection.id(),ids);flush();
        plugin.feedback(player,"Selected "+take+" ("+percent+"% "+(all?"all tamed":"free")+") | Added "+(ids.size()-before)+" | "+ids.size()+" on this wand");player.playSound(player.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,.4f,1.4f);
    }
    public void confirmBulk(Player player,int clicks){var choice=BulkSelection.choice(clicks);switch(choice.scope()){
        case FREE->bindPercent(player,choice.percent(),false);case ALL->bindPercent(player,choice.percent(),true);
        case CANCEL->plugin.feedback(player,"Bulk binding cancelled.");
        case UNBIND->{var selected=selection(player);if(selected==null)return;if(!plugin.dogCommands().unbindGroup(player,selected.id(),selected.dogs()))return;var item=player.getInventory().getItem(selected.hand());var meta=item.getItemMeta();BindingLore.write(meta,dogsKey,Set.of());item.setItemMeta(meta);player.getInventory().setItem(selected.hand(),item);registry.bindings(selected.id(),Set.of());autoState(player,0);fetchEnabled(player,false);guardRegion(player,null);flush();plugin.feedback(player,"Unbound all dogs from this wand.");}
    }}
    public boolean fetchEnabled(Player player){var item=plugin.activeWand(player);if(item==null)return false;var data=item.getItemMeta().getPersistentDataContainer();var key=new NamespacedKey(plugin,"fetch_enabled");return data.has(key,PersistentDataType.BYTE)&&data.get(key,PersistentDataType.BYTE)==1;}
    public void fetchEnabled(Player player,boolean enabled){var selected=selection(player);if(selected==null)return;var item=player.getInventory().getItem(selected.hand());var meta=item.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"fetch_enabled"),PersistentDataType.BYTE,(byte)(enabled?1:0));item.setItemMeta(meta);player.getInventory().setItem(selected.hand(),item);}
    public RegionSelection guardRegion(Player player){
        var item=plugin.activeWand(player);if(item==null)return null;var data=item.getItemMeta().getPersistentDataContainer();var key=new NamespacedKey(plugin,"guard_region");
        if(!data.has(key,PersistentDataType.STRING))return null;
        try{String[] parts=data.get(key,PersistentDataType.STRING).split("\\|",2);return new RegionSelection(UUID.fromString(parts[0]),GuardRegion.decode(parts[1]));}catch(RuntimeException ex){return null;}
    }
    public void guardRegion(Player player,RegionSelection region){var selected=selection(player);if(selected==null)return;var item=player.getInventory().getItem(selected.hand());var meta=item.getItemMeta();var key=new NamespacedKey(plugin,"guard_region");if(region==null)meta.getPersistentDataContainer().remove(key);else meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,region.world()+"|"+region.region().encode());item.setItemMeta(meta);player.getInventory().setItem(selected.hand(),item);}
    public int autoState(Player player){var item=plugin.activeWand(player);if(item==null)return 0;var key=new NamespacedKey(plugin,"auto_target");var data=item.getItemMeta().getPersistentDataContainer();return data.has(key,PersistentDataType.BYTE)?Math.floorMod(data.get(key,PersistentDataType.BYTE),3):0;}
    public void autoState(Player player,int state){var selected=selection(player);if(selected==null)return;var item=player.getInventory().getItem(selected.hand());var meta=item.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"auto_target"),PersistentDataType.BYTE,(byte)state);item.setItemMeta(meta);player.getInventory().setItem(selected.hand(),item);}
}
