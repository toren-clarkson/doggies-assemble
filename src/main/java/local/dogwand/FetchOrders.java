package local.dogwand;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/** One item-entity reservation per dog. Cargo stays on the wolf through unload/restart. */
public final class FetchOrders {
    public record Member(Wolf dog,DogCommands.Order order){}
    private final NamespacedKey cargoKey,cargoOwnerKey;
    private final Map<UUID,UUID> claims=new HashMap<>();
    private final Map<UUID,Integer> rejected=new HashMap<>();
    private static final NamespacedKey DELIVERY=new NamespacedKey("dogwand","fetch_delivered");
    public static boolean delivered(Item item){return item.getPersistentDataContainer().has(DELIVERY,PersistentDataType.BYTE);}
    public static void preserveDelivery(Item source,Item target){if(delivered(source)||delivered(target))target.getPersistentDataContainer().set(DELIVERY,PersistentDataType.BYTE,(byte)1);}
    public FetchOrders(DogWandPlugin plugin){this(new NamespacedKey(plugin,"fetch_cargo"),new NamespacedKey(plugin,"fetch_cargo_owner"));}
    FetchOrders(NamespacedKey cargoKey,NamespacedKey cargoOwnerKey){this.cargoKey=cargoKey;this.cargoOwnerKey=cargoOwnerKey;}
    public static boolean selectable(Item item,Player owner){if(delivered(item))return false;boolean[] foreign={false};var key=new NamespacedKey("dogwand","wand_owner");WandContents.visit(item.getItemStack(),stack->stack.getPersistentDataContainer().has(key,PersistentDataType.STRING),stack->foreign[0]|=!owner.getUniqueId().toString().equals(stack.getPersistentDataContainer().get(key,PersistentDataType.STRING)));return !foreign[0]&&item.isValid()&&!item.isDead()&&item.canMobPickup()
        &&(item.getOwner()==null||item.getOwner().equals(owner.getUniqueId()))&&item.getWorld()==owner.getWorld()&&item.getLocation().distanceSquared(owner.getLocation())<=400;}
    public static boolean available(Item item,Player owner){return selectable(item,owner)&&item.getPickupDelay()<=0;}
    public void release(UUID dog){claims.remove(dog);}
    public boolean carrying(Wolf dog){return dog.getPersistentDataContainer().has(cargoKey,PersistentDataType.BYTE_ARRAY);}
    public ItemStack cargo(Wolf dog){return carrying(dog)?ItemStack.deserializeBytes(dog.getPersistentDataContainer().get(cargoKey,PersistentDataType.BYTE_ARRAY)):null;}
    public void storeCargo(Wolf dog,ItemStack item){if(carrying(dog))dog.getPersistentDataContainer().set(cargoKey,PersistentDataType.BYTE_ARRAY,item.serializeAsBytes());}
    public Item target(Wolf dog){UUID id=claims.get(dog.getUniqueId());Entity found=id==null?null:Bukkit.getEntity(id);return found instanceof Item item&&item.isValid()?item:null;}
    public boolean working(Wolf dog,DogCommands.Order order){Player owner=Bukkit.getPlayer(order.owner());return owner!=null&&owner.getWorld()==dog.getWorld()&&(carrying(dog)||target(dog)!=null);}
    public void plan(List<Member> members){
        int now=Bukkit.getCurrentTick();rejected.entrySet().removeIf(e->e.getValue()<=now);Set<UUID> dogs=new HashSet<>();members.forEach(m->dogs.add(m.dog().getUniqueId()));claims.keySet().retainAll(dogs);
        Map<UUID,List<Item>> choices=new HashMap<>();
        Map<UUID,List<Item>> nearby=new HashMap<>();
        for(Member m:members){Wolf dog=m.dog();Player owner=Bukkit.getPlayer(m.order().owner());
            if(owner==null||owner.getWorld()!=dog.getWorld()){claims.remove(dog.getUniqueId());continue;}
            var waiting=nearby.computeIfAbsent(owner.getUniqueId(),id->owner.getWorld().getNearbyEntities(owner.getLocation(),20,20,20).stream().filter(Item.class::isInstance).map(Item.class::cast).filter(i->selectable(i,owner)&&!rejected.containsKey(i.getUniqueId())).toList());
            var items=waiting.stream().filter(i->i.getPickupDelay()<=0).toList();
            choices.put(dog.getUniqueId(),items);
            UUID claimed=claims.get(dog.getUniqueId());if(carrying(dog)||items.stream().noneMatch(i->i.getUniqueId().equals(claimed)))claims.remove(dog.getUniqueId());
        }
        Set<UUID> reserved=new HashSet<>(claims.values());
        for(Member m:members.stream().sorted(Comparator.comparing(x->x.dog().getUniqueId())).toList()){
            Wolf dog=m.dog();if(carrying(dog)||claims.containsKey(dog.getUniqueId()))continue;
            Item next=choices.getOrDefault(dog.getUniqueId(),List.of()).stream().filter(i->!reserved.contains(i.getUniqueId()))
                .min(Comparator.comparingDouble(i->dog.getLocation().distanceSquared(i.getLocation()))).orElse(null);
            if(next!=null){claims.put(dog.getUniqueId(),next.getUniqueId());reserved.add(next.getUniqueId());}
        }
    }
    public boolean idle(Wolf dog){return !carrying(dog)&&!claims.containsKey(dog.getUniqueId());}
    public boolean pickup(Wolf dog,Item item,Player owner){
        if(carrying(dog)||!available(item,owner)||!item.getUniqueId().equals(claims.get(dog.getUniqueId())))return false;
        if(!dog.getBoundingBox().clone().expand(.65,.4,.65).overlaps(item.getBoundingBox()))return false;
        var event=new EntityPickupItemEvent(dog,item,0);
        if(!event.callEvent()){reject(dog);return false;}
        if(!item.isValid()||!available(item,owner))return false;
        ItemStack stack=item.getItemStack().clone();if(stack.isEmpty())return false;
        byte[] bytes=stack.serializeAsBytes();dog.getPersistentDataContainer().set(cargoKey,PersistentDataType.BYTE_ARRAY,bytes);
        dog.getPersistentDataContainer().set(cargoOwnerKey,PersistentDataType.STRING,owner.getUniqueId().toString());
        item.remove();release(dog.getUniqueId());owner.playSound(dog.getLocation(),Sound.ENTITY_ITEM_PICKUP,.35f,1.15f);return true;
    }
    public void reject(Wolf dog){UUID item=claims.remove(dog.getUniqueId());if(item!=null)rejected.put(item,Bukkit.getCurrentTick()+200);}
    public boolean deliver(Wolf dog,Player owner){
        if(!carrying(dog)||owner==null||owner.getWorld()!=dog.getWorld()||dog.getLocation().distanceSquared(owner.getLocation())>6.25||!dog.hasLineOfSight(owner))return false;
        return drop(dog,owner.getLocation().add(0,.15,0),true);
    }
    public boolean drop(Wolf dog,Location location){
        return drop(dog,location,false);
    }
    private boolean drop(Wolf dog,Location location,boolean delivery){
        if(!carrying(dog))return true;
        ItemStack cargo=ItemStack.deserializeBytes(dog.getPersistentDataContainer().get(cargoKey,PersistentDataType.BYTE_ARRAY));
        String saved=dog.getPersistentDataContainer().get(cargoOwnerKey,PersistentDataType.STRING);UUID owner=saved==null?dog.getOwnerUniqueId():UUID.fromString(saved);
        Item drop=location.getWorld().dropItem(location,cargo,item->{item.setOwner(owner);item.setPickupDelay(10);item.setVelocity(new Vector());if(delivery)item.getPersistentDataContainer().set(DELIVERY,PersistentDataType.BYTE,(byte)1);});
        if(!drop.isValid())return false;
        if(!new EntityDropItemEvent(dog,drop).callEvent()){drop.remove();return false;}
        if(!drop.isValid())return false;removeCargo(dog);return true;
    }
    private void removeCargo(Wolf dog){dog.getPersistentDataContainer().remove(cargoKey);dog.getPersistentDataContainer().remove(cargoOwnerKey);}
    public void death(Wolf dog,List<ItemStack> drops){if(carrying(dog)){drops.add(ItemStack.deserializeBytes(dog.getPersistentDataContainer().get(cargoKey,PersistentDataType.BYTE_ARRAY)));removeCargo(dog);}release(dog.getUniqueId());}
}
