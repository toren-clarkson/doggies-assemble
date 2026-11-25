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
    public record Selection(UUID id,Set<UUID> dogs,EquipmentSlot hand) {}
    public record RegionSelection(UUID world,GuardRegion region){}
    public WandBindings(DogWandPlugin plugin) {
        this.plugin=plugin;idKey=new NamespacedKey(plugin,"wand_id");dogsKey=new NamespacedKey(plugin,"bound_dogs");uiKey=new NamespacedKey(plugin,"wand_ui_version");
        registry=new DogRegistry(plugin.getDataFolder().toPath().resolve("known-dogs.yml"));
    }
    public DogRegistry registry(){return registry;}
    public void flush(){try{registry.flush();}catch(java.io.IOException ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not save dog registry",ex);}}
    public Set<UUID> dogs(ItemStack item){if(!plugin.isWand(item))return Set.of();var data=item.getItemMeta().getPersistentDataContainer();return registry.living(data.has(dogsKey,PersistentDataType.STRING)?decode(data.get(dogsKey,PersistentDataType.STRING)):Set.of());}
    public boolean prune(ItemStack item){
        if(!plugin.isWand(item))return false;var meta=item.getItemMeta();var data=meta.getPersistentDataContainer();
        Set<UUID> ids=data.has(dogsKey,PersistentDataType.STRING)?decode(data.get(dogsKey,PersistentDataType.STRING)):Set.of(),living=registry.living(ids);
        if(ids.equals(living)&&BindingLore.withCount(meta.lore(),living.size()).equals(meta.lore()))return false;
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
        if(prune(item))player.getInventory().setItem(hand,item);
        var meta=item.getItemMeta();var data=meta.getPersistentDataContainer();
        String saved=data.has(idKey,PersistentDataType.STRING)?data.get(idKey,PersistentDataType.STRING):null;
        UUID id;
        try{id=UUID.fromString(saved);}catch(Exception ex){id=UUID.randomUUID();data.set(idKey,PersistentDataType.STRING,id.toString());item.setItemMeta(meta);player.getInventory().setItem(hand,item);}
        String encoded=data.has(dogsKey,PersistentDataType.STRING)?data.get(dogsKey,PersistentDataType.STRING):null;
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
        plugin.feedback(player,(removed?"Unbound ":"Bound ")+wolf.getName()+" | "+ids.size()+" dog(s) on this wand");
        player.playSound(player.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,.4f,1.4f);
    }
    public void bindAll(Player player){
        var selection=selection(player);if(selection==null)return;plugin.dogCommands().observeLoaded();
        Set<UUID> ids=new LinkedHashSet<>(selection.dogs());int before=ids.size();ids.addAll(registry.owned(player.getUniqueId()));
        var item=player.getInventory().getItem(selection.hand());var meta=item.getItemMeta();BindingLore.write(meta,dogsKey,ids);item.setItemMeta(meta);player.getInventory().setItem(selection.hand(),item);flush();
        plugin.feedback(player,"Bound "+(ids.size()-before)+" more dogs | "+ids.size()+" known dogs on this wand");player.playSound(player.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,.4f,1.4f);
    }
    public RegionSelection guardRegion(Player player){
        var item=plugin.activeWand(player);if(item==null)return null;var data=item.getItemMeta().getPersistentDataContainer();var key=new NamespacedKey(plugin,"guard_region");
        if(!data.has(key,PersistentDataType.STRING))return null;
        try{String[] parts=data.get(key,PersistentDataType.STRING).split("\\|",2);return new RegionSelection(UUID.fromString(parts[0]),GuardRegion.decode(parts[1]));}catch(RuntimeException ex){return null;}
    }
    public void guardRegion(Player player,RegionSelection region){var selected=selection(player);if(selected==null)return;var item=player.getInventory().getItem(selected.hand());var meta=item.getItemMeta();var key=new NamespacedKey(plugin,"guard_region");if(region==null)meta.getPersistentDataContainer().remove(key);else meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,region.world()+"|"+region.region().encode());item.setItemMeta(meta);player.getInventory().setItem(selected.hand(),item);}
    public int autoState(Player player){var item=plugin.activeWand(player);if(item==null)return 0;var key=new NamespacedKey(plugin,"auto_target");var data=item.getItemMeta().getPersistentDataContainer();return data.has(key,PersistentDataType.BYTE)?Math.floorMod(data.get(key,PersistentDataType.BYTE),3):0;}
    public void autoState(Player player,int state){var selected=selection(player);if(selected==null)return;var item=player.getInventory().getItem(selected.hand());var meta=item.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"auto_target"),PersistentDataType.BYTE,(byte)state);item.setItemMeta(meta);player.getInventory().setItem(selected.hand(),item);}
}
