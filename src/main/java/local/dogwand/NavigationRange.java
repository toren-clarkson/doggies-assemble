package local.dogwand;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.*;

/** Presence, not effective value, decides whether our modifier already exists. */
public final class NavigationRange {
    private NavigationRange(){}
    public static void update(AttributeInstance range,NamespacedKey key,boolean enabled){
        if(range==null)return;
        if(!enabled){range.removeModifier(key);return;}
        if(range.getModifier(key)!=null)return;
        double deficit=48-range.getValue();if(deficit>0)range.addTransientModifier(new AttributeModifier(key,deficit,AttributeModifier.Operation.ADD_NUMBER));
    }
}
