package local.dogwand;

import org.bukkit.entity.Player;

/** A stable ID, concise help, and one left-click action. Right-click handles binding. */
public interface WandMode {
    String id();
    String name();
    default String description(){return "Left click to use this mode.";}
    void leftClick(Player player);
}
