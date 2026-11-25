package local.dogwand;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.datacomponent.DataComponentTypes;

public final class DogFeeding {
    private DogFeeding(){}
    public record Result(int dogs,int portions){}
    public static Result feed(Player owner,Collection<Wolf> dogs) {
        return feed(owner,dogs,dog->{var attribute=dog.getAttribute(Attribute.MAX_HEALTH);return attribute==null?dog.getHealth():attribute.getValue();});
    }
    static Result feed(Player owner,Collection<Wolf> dogs,java.util.function.ToDoubleFunction<Wolf> maxHealth) {
        int healed=0,portions=0;
        for(Wolf dog:dogs){
            if(dog.getWorld()!=owner.getWorld() || dog.getLocation().distanceSquared(owner.getLocation())>100 || dog.isDead())continue;
            double before=dog.getHealth(),maximum=maxHealth.applyAsDouble(dog);
            while(dog.getHealth()<maximum-.001){
                boolean used=false;
                for(int slot=0;slot<owner.getInventory().getSize();slot++){
                    ItemStack meat=owner.getInventory().getItem(slot);
                    if(meat==null||meat.getAmount()==0||!dog.isBreedItem(meat))continue;
                    var food=meat.getData(DataComponentTypes.FOOD);if(food==null||food.nutrition()<=0)continue;
                    double health=dog.getHealth();dog.heal(Math.min(food.nutrition(),maximum-health),RegainReason.EATING);
                    if(dog.getHealth()<=health+.00001)break;
                    meat.setAmount(meat.getAmount()-1);owner.getInventory().setItem(slot,meat.getAmount()==0?null:meat);portions++;used=true;break;
                }
                if(!used)break;
            }
            if(dog.getHealth()>before){healed++;OwnerEffects.fed(owner,dog);}
        }
        return new Result(healed,portions);
    }
}
