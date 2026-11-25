package local.dogwand;

import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Per-item selection; the namespaced-key constructor supports isolated container tests. */
public final class ModeRegistry {
    private final LinkedHashMap<String, WandMode> modes = new LinkedHashMap<>();
    private final NamespacedKey selection;
    private String lastMode;
    private DogWandPlugin plugin;
    public ModeRegistry(DogWandPlugin plugin) { this(new NamespacedKey(plugin, "mode"));this.plugin=plugin; }
    ModeRegistry(NamespacedKey selection) {this.selection=selection;}
    public void register(WandMode mode) {
        if (modes.putIfAbsent(mode.id(), mode) != null) throw new IllegalArgumentException("Duplicate mode " + mode.id());
        if(lastMode!=null){WandMode last=modes.remove(lastMode);modes.put(lastMode,last);}
    }
    public void registerLast(WandMode mode) {register(mode);lastMode=mode.id();}
    public List<String> ids() { return List.copyOf(modes.keySet()); }
    public List<WandMode> all(){return List.copyOf(modes.values());}
    public WandMode current(Player player) {
        var item=plugin==null?null:plugin.activeWand(player);
        var data=item==null?player.getPersistentDataContainer():item.getItemMeta().getPersistentDataContainer();
        return current(data);
    }
    WandMode current(io.papermc.paper.persistence.PersistentDataContainerView data){
        String id = data.has(selection,PersistentDataType.STRING)?data.get(selection, PersistentDataType.STRING):null;
        return modes.getOrDefault(id, modes.values().iterator().next());
    }
    public WandMode cycle(Player player) {
        var item=plugin==null?null:plugin.activeWand(player);
        var meta=item==null?null:item.getItemMeta();
        WandMode next=cycle(meta==null?player.getPersistentDataContainer():meta.getPersistentDataContainer());
        if(item!=null){item.setItemMeta(meta);var hand=plugin.isWand(player.getInventory().getItemInMainHand())?org.bukkit.inventory.EquipmentSlot.HAND:org.bukkit.inventory.EquipmentSlot.OFF_HAND;player.getInventory().setItem(hand,item);}
        return next;
    }
    WandMode cycle(org.bukkit.persistence.PersistentDataContainer data){
        List<WandMode> list = List.copyOf(modes.values());
        WandMode next = list.get((list.indexOf(current(data)) + 1) % list.size());
        data.set(selection,PersistentDataType.STRING,next.id());
        return next;
    }
}
