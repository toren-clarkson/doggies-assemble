package local.dogwand;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.event.player.PlayerArmSwingEvent;

public final class DogWandPlugin extends JavaPlugin implements Listener {
    private NamespacedKey wandKey,recipeKey;
    private ItemStack wand;
    private DogCommands commands;
    private ModeRegistry modes;
    private WandBindings bindings;
    private final Map<UUID,Integer> lastLeft=new HashMap<>(),lastRight=new HashMap<>(),lastRelease=new HashMap<>(),pendingSwing=new HashMap<>();
    private final Set<UUID> sneaking=new HashSet<>(),bulkChord=new HashSet<>();
    private final Map<UUID,Integer> feedbackUntil=new HashMap<>();
    public DogCommands dogCommands() {return commands;}
    public ModeRegistry modes() {return modes;}
    public WandBindings bindings() {return bindings;}
    public ItemStack activeWand(Player p){if(isWand(p.getInventory().getItemInMainHand()))return p.getInventory().getItemInMainHand();if(isWand(p.getInventory().getItemInOffHand()))return p.getInventory().getItemInOffHand();return null;}
    @Override public void onEnable() {
        saveDefaultConfig();wandKey=new NamespacedKey(this,"wand");recipeKey=new NamespacedKey(this,"dog_wand");
        // The long, cancellable use action makes air right-clicks observable in either hand.
        // The listener cancels it before consumption, with no food requirement or durability.
        wand=Bukkit.getItemFactory().createItemStack("minecraft:stick[minecraft:max_stack_size=1,minecraft:item_name={text:'Dog Wand',color:'gold'},minecraft:item_model='dogwand:dog_wand',minecraft:custom_data={PublicBukkitValues:{'dogwand:wand':1}},minecraft:consumable={consume_seconds:3600.0,animation:'none',has_consume_particles:false,sound:'minecraft:intentionally_empty'},minecraft:lore=[{text:'Left click: Selected mode',color:'gray',italic:false},{text:'Right click: Bind your dog',color:'gray',italic:false},{text:'Shift: Cycle modes',color:'dark_gray',italic:false}]]");
        bindings=new WandBindings(this);commands=new DogCommands(this);modes=new ModeRegistry(this);
        modes.register(new WandMode() {
            public String id() {return "point_order";}
            public String name() {return "Point Order";}
            public String description(){return "Point at a block to stay, or a mob to attack.";}
            public void leftClick(Player player) {commands.point(player);}
        });
        modes.register(mode("sit_stand","Sit / Stand","Sit the group, or stand them if all sit.",commands::sitStand));
        modes.register(mode("highlight","Highlight Dogs","Show your dogs only to you for 10 seconds.",commands::highlight));
        modes.register(mode("auto_target","Auto Target","Cycle Off / Hostile Only / All Mobs within 20 blocks.",commands::toggleAuto));
        modes.register(mode("feed","Feed Dogs","Heal dogs within 10 blocks using inventory meat.",commands::feed));
        modes.register(mode("guard","Guard Region","Set two corners. Roam inside; defend against hostile mobs.",commands::guard));
        modes.registerLast(mode("release","Release Orders","Clear orders, guard duty and automatic targeting.",commands::release));
        var meta=wand.getItemMeta();meta.lore(WandText.lore(modes,modes.all().getFirst(),modes.all().getFirst().description(),0));
        meta.itemName(Component.text("Dog Wand",NamedTextColor.GOLD).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));wand.setItemMeta(meta);
        Bukkit.getPluginManager().registerEvents(this,this);
        // Datapack supplies the recipe. A fallback also supports plugin + resource pack installs.
        installRecipe();
        for(World world:Bukkit.getWorlds()) for(Wolf wolf:world.getEntitiesByClass(Wolf.class)) commands.track(wolf);
        for(Player player:Bukkit.getOnlinePlayers()) {discoverFromInventory(player);if(player.isSneaking()) sneaking.add(player.getUniqueId());}
        Bukkit.getScheduler().runTaskTimer(this,commands::maintain,1,1);
        Bukkit.getScheduler().runTaskTimer(this,()-> {
            int now=Bukkit.getCurrentTick();
            for(Player player:Bukkit.getOnlinePlayers()) if(usable(player) && now>=feedbackUntil.getOrDefault(player.getUniqueId(),0)) showMode(player);
        },1,20);
        Bukkit.getScheduler().runTaskTimer(this,()->{for(Player player:Bukkit.getOnlinePlayers())if(usable(player)&&modes.current(player).id().equals("guard"))OwnerEffects.box(player,bindings.guardRegion(player));},1,10);
    }
    private WandMode mode(String id,String name,String description,java.util.function.Consumer<Player> action) {
        return new WandMode(){public String id(){return id;}public String name(){return name;}public String description(){return description;}public void leftClick(Player p){action.accept(p);}};
    }
    private void installRecipe() {
        if(Bukkit.getRecipe(recipeKey)!=null) return;
        ShapedRecipe recipe=new ShapedRecipe(recipeKey,wand.clone());recipe.shape("BBB","BSB","BBB");
        recipe.setIngredient('B',Material.BONE);recipe.setIngredient('S',Material.STICK);Bukkit.addRecipe(recipe);
    }
    @Override public void onDisable() {if(commands!=null) commands.stop();if(bindings!=null)bindings.flush();}
    public ItemStack createWand() {return wand.clone();}
    public boolean isWand(ItemStack item) {
        return item!=null && item.getType()==Material.STICK && item.hasItemMeta()
                && WandIdentity.matches(item.getItemMeta().getPersistentDataContainer(),wandKey);
    }
    public boolean holding(Player player) {return isWand(player.getInventory().getItemInMainHand()) || isWand(player.getInventory().getItemInOffHand());}
    private boolean usable(Player player) {return player.hasPermission("dogwand.use") && player.getGameMode()!=GameMode.SPECTATOR && holding(player);}
    public void feedback(Player player,String message) {
        if(getConfig().getBoolean("feedback",true)) {
            NamedTextColor tone=message.startsWith("No ")||message.startsWith("Could not")||message.startsWith("The box")||message.startsWith("Both ")||message.startsWith("Private ")?NamedTextColor.RED:message.startsWith("Unbound")||message.startsWith("Released")?NamedTextColor.YELLOW:NamedTextColor.GREEN;
            player.sendActionBar(Component.text("Dog Wand ",NamedTextColor.GOLD).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD).append(Component.text(message,tone).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD,false)));
            feedbackUntil.put(player.getUniqueId(),Bukkit.getCurrentTick()+40);
        }
    }
    private void showMode(Player player) {
        var wand=bindings.selection(player);
        var mode=modes.current(player);
        player.sendActionBar(Component.text(mode.name(),WandText.color(mode.id())).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD).append(Component.text(" | "+commands.hint(player,mode),NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD,false)));
        WandText.refresh(this,player);
    }
    private void discoverFromInventory(Player player) {
        if(player.getInventory().contains(Material.BONE)) player.discoverRecipe(recipeKey);
    }
    private boolean cooldown(Map<UUID,Integer> map,Player player,int tick) {
        int gap=Math.max(1,getConfig().getInt("click-cooldown-ticks",4));
        Integer before=map.get(player.getUniqueId());
        if(before!=null && tick-before<gap) return true;
        map.put(player.getUniqueId(),tick);return false;
    }
    private void left(Player player) {
        if(!usable(player)) return;
        int tick=Bukkit.getCurrentTick();
        if(cooldown(lastLeft,player,tick)) return;
        var mode=modes.current(player);mode.leftClick(player);OwnerEffects.action(player,mode.id());WandText.refresh(this,player);
    }
    private void refreshAfterEvent(Player player){Bukkit.getScheduler().runTask(this,()->{if(player.isOnline()){bindings.pruneInventory(player.getInventory());if(holding(player))WandText.refresh(this,player);player.updateInventory();}});}
    private void right(Player player,Entity directTarget) {
        if(!usable(player)) return;
        pendingSwing.remove(player.getUniqueId());
        int tick=Bukkit.getCurrentTick();
        Integer previousRight=lastRight.put(player.getUniqueId(),tick);
        // Held Use repeats every few ticks. Require an idle gap so a hold cannot bind/unbind repeatedly.
        if(previousRight!=null && tick-previousRight<6)return;
        if(cooldown(lastRelease,player,tick)) return;
        if(player.isSneaking()||sneaking.contains(player.getUniqueId())){bulkChord.add(player.getUniqueId());bindings.bindAll(player);}else bindings.bind(player,directTarget);WandText.refresh(this,player);refreshAfterEvent(player);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        Player player=event.getPlayer();if(!usable(player)) return;
        switch(event.getAction()) {
            case RIGHT_CLICK_AIR,RIGHT_CLICK_BLOCK -> {
                event.setUseInteractedBlock(Event.Result.DENY);event.setUseItemInHand(Event.Result.DENY);right(player,null);
            }
            case LEFT_CLICK_AIR,LEFT_CLICK_BLOCK -> {
                event.setUseInteractedBlock(Event.Result.DENY);event.setUseItemInHand(Event.Result.DENY);queueLeft(player);
            }
            default -> {}
        }
    }
    private void queueLeft(Player player) {
        if(!usable(player)) return;
        UUID id=player.getUniqueId();int requested=Bukkit.getCurrentTick();
        if(pendingSwing.putIfAbsent(id,requested)!=null) return;
        // Right-click interactions also produce arm swings. Resolve after that tick's events.
        Bukkit.getScheduler().runTask(this,()-> {
            Integer pending=pendingSwing.remove(id);
            if(pending==null || pending!=requested || !player.isOnline()) return;
            Integer right=lastRight.get(id);
            if(right!=null && right>=requested-1) return;
            left(player);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void swing(PlayerArmSwingEvent event) {queueLeft(event.getPlayer());}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void interactEntity(PlayerInteractEntityEvent event) {
        if(usable(event.getPlayer())) {event.setCancelled(true);right(event.getPlayer(),event.getRightClicked());}
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void interactAtEntity(PlayerInteractAtEntityEvent event) {interactEntity(event);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void teleport(EntityTeleportEvent event) {
        if(event.getEntity() instanceof Wolf wolf && commands.order(wolf)!=null) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void move(io.papermc.paper.event.entity.EntityMoveEvent event){if(event.getEntity() instanceof Wolf wolf){var desired=event.getTo();var clipped=commands.constrainMove(wolf,event.getFrom(),desired);if(!clipped.equals(desired)){event.setTo(clipped);var v=wolf.getVelocity();if(clipped.getX()!=desired.getX())v.setX(0);if(clipped.getZ()!=desired.getZ())v.setZ(0);wolf.setVelocity(v);}}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void attack(EntityDamageByEntityEvent event) {
        if(event.getDamager() instanceof Player player && usable(player)) {event.setCancelled(true);queueLeft(player);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damageBlock(BlockDamageEvent event) {if(usable(event.getPlayer())) event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent event) {if(usable(event.getPlayer())) event.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void sneak(PlayerToggleSneakEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        if(event.isSneaking()){sneaking.add(id);return;}
        boolean wasSneaking=sneaking.remove(id),usedBulk=bulkChord.remove(id);
        if(!wasSneaking||usedBulk||!usable(event.getPlayer()))return;
        WandMode mode=modes.cycle(event.getPlayer());
        showMode(event.getPlayer());
        feedbackUntil.remove(id);
        event.getPlayer().playSound(event.getPlayer().getLocation(),Sound.UI_BUTTON_CLICK,.3f,1.4f);
    }
    @EventHandler public void join(PlayerJoinEvent event) {bindings.pruneInventory(event.getPlayer().getInventory());discoverFromInventory(event.getPlayer());}
    @EventHandler public void openInventory(org.bukkit.event.inventory.InventoryOpenEvent event){bindings.pruneInventory(event.getInventory());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void pickup(EntityPickupItemEvent event) {
        if(event.getEntity() instanceof Player p && isWand(event.getItem().getItemStack()))Bukkit.getScheduler().runTask(this,()->bindings.pruneInventory(p.getInventory()));
        if(event.getEntity() instanceof Player player && event.getItem().getItemStack().getType()==Material.BONE)
            Bukkit.getScheduler().runTask(this,()->{if(player.isOnline()) discoverFromInventory(player);});
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        UUID id=event.getPlayer().getUniqueId();lastLeft.remove(id);lastRight.remove(id);lastRelease.remove(id);pendingSwing.remove(id);sneaking.remove(id);bulkChord.remove(id);feedbackUntil.remove(id);
    }
    @EventHandler public void load(EntitiesLoadEvent event) {for(Entity entity:event.getEntities()) if(entity instanceof Wolf wolf) commands.track(wolf);}
    @EventHandler public void unload(EntitiesUnloadEvent event) {for(Entity entity:event.getEntities()) if(entity instanceof Wolf wolf) commands.untrack(wolf);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void spawn(CreatureSpawnEvent event) {if(event.getEntity() instanceof Wolf wolf) Bukkit.getScheduler().runTask(this,()->{if(wolf.isValid())commands.track(wolf);});}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void tame(EntityTameEvent event) {if(event.getEntity() instanceof Wolf wolf) Bukkit.getScheduler().runTask(this,()->commands.track(wolf));}
    @EventHandler public void death(EntityDeathEvent event) {if(event.getEntity() instanceof Wolf wolf){bindings.died(wolf);commands.untrack(wolf);for(Player player:Bukkit.getOnlinePlayers()){if(usable(player))showMode(player);refreshAfterEvent(player);}}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void target(EntityTargetLivingEntityEvent event) {
        if(!(event.getEntity() instanceof Wolf wolf)) return;
        DogCommands.Order order=commands.order(wolf);if(order==null || event.getTarget()==null) return;
        if(wolf.isSitting() || !commands.allowedTarget(wolf,event.getTarget())) event.setCancelled(true);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(args.length==0) {sender.sendMessage("Dog Wand: right click your dog to bind/unbind; Shift+right binds all; tap Shift to cycle; left click uses the mode. Main-hand wand takes priority.");return true;}
        if(args[0].equalsIgnoreCase("mode") && sender instanceof Player player) {showMode(player);return true;}
        if(!sender.hasPermission("dogwand.admin")) {sender.sendMessage("You do not have permission.");return true;}
        if(args[0].equalsIgnoreCase("give")) {
            Player player=args.length>1?Bukkit.getPlayerExact(args[1]):sender instanceof Player p?p:null;
            if(player==null) {sender.sendMessage("Specify an online player.");return true;}
            var overflow=player.getInventory().addItem(createWand());
            overflow.values().forEach(item->player.getWorld().dropItemNaturally(player.getLocation(),item));return true;
        }
        if(args[0].equalsIgnoreCase("reload")) {reloadConfig();installRecipe();sender.sendMessage("Dog Wand configuration reloaded.");return true;}
        return false;
    }
}
