package local.dogwand;

import java.util.*;
import java.util.function.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;

/** Inspect ordinary nested storage without treating a stored wand as deleted. */
public final class WandContents {
    private WandContents(){}
    public static boolean visit(ItemStack item,Predicate<ItemStack> wand,Consumer<ItemStack> action){return visit(item,wand,action,0);}
    private static boolean visit(ItemStack item,Predicate<ItemStack> wand,Consumer<ItemStack> action,int depth){
        if(item==null||item.isEmpty()||depth>16)return false;if(wand.test(item)){action.accept(item);return true;}if(!item.hasItemMeta())return false;
        var meta=item.getItemMeta();boolean found=false;
        if(meta instanceof BundleMeta bundle){List<ItemStack> contents=new ArrayList<>(bundle.getItems());for(ItemStack child:contents)found|=visit(child,wand,action,depth+1);if(found){bundle.setItems(contents);item.setItemMeta(bundle);}}
        else if(meta instanceof BlockStateMeta block&&block.hasBlockState()&&block.getBlockState() instanceof InventoryHolder holder){
            var state=block.getBlockState();var inventory=((InventoryHolder)state).getInventory();for(int slot=0;slot<inventory.getSize();slot++){ItemStack child=inventory.getItem(slot);if(visit(child,wand,action,depth+1)){inventory.setItem(slot,child);found=true;}}
            if(found){block.setBlockState(state);item.setItemMeta(block);}
        }return found;
    }
}
