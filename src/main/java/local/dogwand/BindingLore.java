package local.dogwand;

import java.util.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Binding IDs and displayed count are committed in the same ItemMeta snapshot. */
public final class BindingLore {
    private BindingLore(){}
    private static String text(Component c){String value=c instanceof TextComponent t?t.content():"";for(Component child:c.children())value+=text(child);return value;}
    public static List<Component> withCount(List<Component> current,int count){
        List<Component> lines=new ArrayList<>();
        if(current!=null)for(Component line:current)if(!text(line).matches("\\s*\\d+ bound dog(?:\\(s\\)|s)?\\s*"))lines.add(line);
        lines.add(Component.text(count+" bound dog(s)",NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC,false));return lines;
    }
    public static void write(ItemMeta meta,NamespacedKey key,Collection<UUID> dogs){
        var unique=new LinkedHashSet<>(dogs);meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,WandBindings.encode(unique));meta.lore(withCount(meta.lore(),unique.size()));
    }
}
