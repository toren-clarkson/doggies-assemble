package local.dogwand;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import org.bukkit.entity.Player;

public final class WandText {
    private WandText(){}
    public static NamedTextColor color(String id){return switch(id){case "point_order"->NamedTextColor.AQUA;case "sit_stand"->NamedTextColor.GOLD;case "highlight"->NamedTextColor.LIGHT_PURPLE;case "auto_target"->NamedTextColor.RED;case "feed"->NamedTextColor.GREEN;case "guard"->NamedTextColor.BLUE;default->NamedTextColor.GRAY;};}
    public static List<String> wrap(String text,int limit){
        List<String> lines=new ArrayList<>();String line="";
        for(String word:text.split(" ")){if(!line.isEmpty() && line.length()+word.length()+1>limit){lines.add(line);line="";}line+=line.isEmpty()?word:" "+word;}
        if(!line.isEmpty())lines.add(line);return lines;
    }
    public static List<Component> lore(ModeRegistry registry,WandMode selected,String hint,int count){
        List<Component> out=new ArrayList<>();
        out.add(Component.text("LEFT ",NamedTextColor.YELLOW).append(Component.text("Use mode   ",NamedTextColor.GRAY)).append(Component.text("SHIFT ",NamedTextColor.YELLOW)).append(Component.text("Cycle",NamedTextColor.GRAY)));
        out.add(Component.text("RIGHT ",NamedTextColor.YELLOW).append(Component.text("Bind / unbind your dog",NamedTextColor.GRAY)));
        out.add(Component.text("SHIFT + RIGHT ",NamedTextColor.YELLOW).append(Component.text("Bind all your dogs",NamedTextColor.GRAY)));
        out.add(Component.empty());
        for(WandMode mode:registry.all())out.add(Component.text((mode.id().equals(selected.id())?"> ":"  ")+mode.name(),mode.id().equals(selected.id())?color(mode.id()):NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD,mode.id().equals(selected.id())));
        out.add(Component.empty());
        for(String line:wrap(selected.description(),39))out.add(Component.text(line,NamedTextColor.GRAY));
        if(!hint.equals(selected.description()))for(String line:wrap(hint,39))out.add(Component.text(line,color(selected.id())));
        out.add(Component.text(count+" bound dog(s)",NamedTextColor.DARK_AQUA));
        return out.stream().map(c->c.decoration(TextDecoration.ITALIC,false)).toList();
    }
    public static void refresh(DogWandPlugin plugin,Player player){
        var item=plugin.activeWand(player);if(item==null)return;
        var selected=plugin.modes().current(player);var binding=plugin.bindings().selection(player);
        if(binding==null)return;
        String hint=plugin.dogCommands().hint(player,selected);
        item=player.getInventory().getItem(binding.hand());
        var desired=lore(plugin.modes(),selected,hint,plugin.bindings().dogs(item).size());
        var meta=item.getItemMeta();if(desired.equals(meta.lore()))return;
        meta.lore(desired);item.setItemMeta(meta);
        player.getInventory().setItem(binding.hand(),item);
    }
}
