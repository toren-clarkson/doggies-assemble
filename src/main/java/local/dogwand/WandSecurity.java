package local.dogwand;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;

/** Enforce owners at vanilla transfer boundaries; reconcile direct plugin writes afterwards. */
public final class WandSecurity implements Listener {
    private final DogWandPlugin plugin;private final WandLedger ledger;
    public WandSecurity(DogWandPlugin plugin){this.plugin=plugin;ledger=plugin.bindings().ledger();}
    private boolean foreign(Player p,ItemStack item){return ledger.foreign(p,item);}
    private void later(Player player,Inventory inventory){Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline()){ledger.player(player,true);if(inventory!=null)ledger.inventory(inventory,ledger.location(inventory,player),null);ledger.flush();}});}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pickup(EntityPickupItemEvent event){if(event.getEntity() instanceof Player p&&foreign(p,event.getItem().getItemStack())){event.setCancelled(true);plugin.feedback(p,"This item contains a Dog Wand owned by another player.");}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void click(InventoryClickEvent event){if(!(event.getWhoClicked() instanceof Player p))return;
        ItemStack hotbar=event.getHotbarButton()>=0?p.getInventory().getItem(event.getHotbarButton()):null;
        if((event.getSlotType()!=InventoryType.SlotType.RESULT&&foreign(p,event.getCurrentItem()))||foreign(p,event.getCursor())||foreign(p,hotbar)){event.setCancelled(true);plugin.feedback(p,"This Dog Wand belongs to another player.");return;}
        later(p,event.getView().getTopInventory());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void drag(InventoryDragEvent event){if(event.getWhoClicked() instanceof Player p){if(foreign(p,event.getOldCursor()))event.setCancelled(true);else later(p,event.getView().getTopInventory());}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void open(InventoryOpenEvent event){if(event.getPlayer() instanceof Player p)later(p,event.getInventory());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void close(InventoryCloseEvent event){if(event.getPlayer() instanceof Player p)later(p,event.getInventory());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void craft(CraftItemEvent event){if(event.getWhoClicked() instanceof Player p&&plugin.isWand(event.getCurrentItem())){
        ItemStack result=event.getCurrentItem().clone();
        if(event.isShiftClick()&&event.getRecipe() instanceof Keyed recipe&&recipe.getKey().equals(new NamespacedKey(plugin,"dog_wand"))){
            event.setCancelled(true);ItemStack[] matrix=event.getInventory().getMatrix();int batches=64;
            for(ItemStack ingredient:matrix)if(ingredient!=null&&!ingredient.isEmpty())batches=Math.min(batches,ingredient.getAmount());
            int made=0;for(;made<batches&&p.getInventory().firstEmpty()>=0;made++){ItemStack one=result.clone();var meta=one.getItemMeta();meta.getPersistentDataContainer().remove(new NamespacedKey(plugin,"wand_id"));one.setItemMeta(meta);ledger.observe(one,p.getUniqueId(),"p:"+p.getUniqueId());if(!p.getInventory().addItem(one).isEmpty())break;}
            if(made>0){for(ItemStack ingredient:matrix)if(ingredient!=null&&!ingredient.isEmpty())ingredient.setAmount(ingredient.getAmount()-made);event.getInventory().setMatrix(matrix);}later(p,event.getInventory());return;
        }
        var meta=result.getItemMeta();meta.getPersistentDataContainer().remove(new NamespacedKey(plugin,"wand_id"));result.setItemMeta(meta);ledger.observe(result,p.getUniqueId(),"p:"+p.getUniqueId());event.setCurrentItem(result);later(p,null);
    }}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void drop(PlayerDropItemEvent event){Item item=event.getItemDrop();ItemStack stack=item.getItemStack();if(ledger.observeContents(stack,event.getPlayer().getUniqueId(),WandLedger.entityLocation(item))){item.setItemStack(stack);item.setOwner(ledger.owner(stack));ledger.flush();}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void spawn(ItemSpawnEvent event){ledger.entity(event.getEntity());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void remove(EntityRemoveEvent event){if(!(event.getEntity() instanceof Item item))return;
        switch(event.getCause()){
            case DEATH,DESPAWN,EXPLODE,OUT_OF_WORLD,ENTER_BLOCK,HIT -> {if(ledger.observeContents(item.getItemStack(),null,WandLedger.entityLocation(item)))Bukkit.getScheduler().runTask(plugin,ledger::audit);}
            case UNLOAD -> ledger.entity(item);
            default -> {}
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void transfer(InventoryMoveItemEvent event){Inventory from=event.getSource(),to=event.getDestination();if(!WandContents.visit(event.getItem(),plugin::isWand,item->{}))return;
        Bukkit.getScheduler().runTask(plugin,()->{Location a=from.getLocation(),b=to.getLocation();ledger.inventory(from,a==null?"v:unknown":WandLedger.blockLocation(a),null);ledger.inventory(to,b==null?"v:unknown":WandLedger.blockLocation(b),null);ledger.flush();});
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event){ledger.player(event.getPlayer(),false);ledger.flush();}
    @EventHandler(priority=EventPriority.MONITOR)
    public void unload(EntitiesUnloadEvent event){for(Entity e:event.getEntities())ledger.entity(e);ledger.flush();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void armorStand(PlayerArmorStandManipulateEvent event){if(foreign(event.getPlayer(),event.getArmorStandItem())||foreign(event.getPlayer(),event.getPlayerItem()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void hopper(InventoryPickupItemEvent event){Inventory inventory=event.getInventory();if(WandContents.visit(event.getItem().getItemStack(),plugin::isWand,item->{}))Bukkit.getScheduler().runTask(plugin,()->{Location at=inventory.getLocation();ledger.inventory(inventory,at==null?"v:unknown":WandLedger.blockLocation(at),null);ledger.flush();});}
}
