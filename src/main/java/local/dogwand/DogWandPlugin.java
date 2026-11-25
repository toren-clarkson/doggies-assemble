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
    private static final class PendingBinding {final UUID wand;int clicks;boolean pressed;PendingBinding(UUID wand){this.wand=wand;}}
    private final Map<UUID,PendingBinding> pendingBindings=new HashMap<>();
    private final Set<UUID> finishingBulkClick=new HashSet<>();
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
            public String description(){return "Point and click at a location. Mobs at the location gets attacked. If non, then the dogs stay there.";}
            public void leftClick(Player player) {commands.point(player);}
        });
        modes.register(mode("sit_stand","Sit / Stand","Toggles sit/stand for dogs bound.",commands::sitStand));
        modes.register(mode("highlight","Highlight Dogs","Glows bound dogs for 10 seconds.",commands::highlight));
        modes.register(mode("auto_target","Auto Target","Cycle between: Off / Hostile Only / All Mobs within 20 blocks.",commands::toggleAuto));
        modes.register(mode("feed","Feed Dogs","Heal dogs within 10 blocks using meat in your inventory.",commands::feed));
        modes.register(mode("guard","Guard Region","Set two corners to select a region bound dogs defend against hostile mobs.",commands::guard));
        modes.register(mode("fetch","Fetch Items","Toggle item fetching within 20 blocks. Return items to your feet.",commands::fetch));
        modes.registerLast(mode("release","Release Orders","Clear any orders, return to normal behavior.",commands::release));
        var meta=wand.getItemMeta();meta.lore(WandText.lore(modes,modes.all().getFirst(),modes.all().getFirst().description(),0));
        meta.itemName(Component.text("Dog Wand",NamedTextColor.GOLD).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));wand.setItemMeta(meta);
        Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getPluginManager().registerEvents(new WandSecurity(this),this);
        // Datapack supplies the recipe. A fallback also supports plugin + resource pack installs.
        installRecipe();
        for(World world:Bukkit.getWorlds()) for(Wolf wolf:world.getEntitiesByClass(Wolf.class)) commands.track(wolf);
        for(Player player:Bukkit.getOnlinePlayers()) {bindings.ledger().player(player,true);bindings.pruneInventory(player.getInventory());discoverFromInventory(player);if(player.isSneaking()) sneaking.add(player.getUniqueId());}
        Bukkit.getScheduler().runTaskTimer(this,()->bindings.ledger().audit(),1,100);
        Bukkit.getScheduler().runTaskTimer(this,()->{for(Player p:Bukkit.getOnlinePlayers())bindings.ledger().player(p,true);},1,20);
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
    @Override public void onDisable() {if(commands!=null) commands.stop();if(bindings!=null){bindings.flush();bindings.ledger().flush();}}
    public ItemStack createWand() {return wand.clone();}
    public boolean isWand(ItemStack item) {
        return item!=null && item.getType()==Material.STICK && item.hasItemMeta()
                && WandIdentity.matches(item.getItemMeta().getPersistentDataContainer(),wandKey);
    }
    public boolean holding(Player player) {return isWand(player.getInventory().getItemInMainHand()) || isWand(player.getInventory().getItemInOffHand());}
    private boolean usable(Player player) {return player.hasPermission("dogwand.use") && player.getGameMode()!=GameMode.SPECTATOR && holding(player)&&bindings.ledger().owns(player,activeWand(player));}
    public void feedback(Player player,String message) {
        if(getConfig().getBoolean("feedback",true)) {
            NamedTextColor tone=message.startsWith("No ")||message.startsWith("Could not")||message.startsWith("The box")||message.startsWith("Both ")||message.startsWith("Private ")?NamedTextColor.RED:message.startsWith("Unbound")||message.startsWith("Released")?NamedTextColor.YELLOW:NamedTextColor.GREEN;
            player.sendActionBar(Component.text("Dog Wand ",NamedTextColor.GOLD).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD).append(Component.text(message,tone).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD,false)));
            feedbackUntil.put(player.getUniqueId(),Bukkit.getCurrentTick()+40);
        }
    }
    private void showMode(Player player) {
        var pending=pendingBindings.get(player.getUniqueId());if(pending!=null){bulkPreview(player,pending);WandText.refresh(this,player);return;}
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
    private void bulkPreview(Player player,PendingBinding pending){feedback(player,"Bulk bind: "+BulkSelection.choice(pending.clicks).label()+" | Release Shift to confirm");}
    private void right(Player player,Entity directTarget) {
        if(!usable(player)) return;
        pendingSwing.remove(player.getUniqueId());
        int tick=Bukkit.getCurrentTick();
        if(player.isSneaking()||sneaking.contains(player.getUniqueId())){
            lastRight.put(player.getUniqueId(),tick);bulkChord.add(player.getUniqueId());var selected=bindings.selection(player);if(selected==null)return;
            var pending=pendingBindings.computeIfAbsent(player.getUniqueId(),id->new PendingBinding(selected.id()));
            if(!pending.wand.equals(selected.id())){feedback(player,"Bulk bind cancelled: keep the same wand selected.");pending.clicks=12;return;}
            if(pending.pressed)return;pending.pressed=true;pending.clicks++;bulkPreview(player,pending);
            Bukkit.getScheduler().runTask(this,()->{if(player.isOnline()&&pendingBindings.get(player.getUniqueId())==pending&&pending.pressed)player.startUsingItem(selected.hand());});return;
        }
        if(finishingBulkClick.contains(player.getUniqueId())){lastRight.put(player.getUniqueId(),tick);if(!player.hasActiveItem())finishingBulkClick.remove(player.getUniqueId());return;}
        Integer previousRight=lastRight.put(player.getUniqueId(),tick);
        // Held Use repeats every few ticks. Require an idle gap so a hold cannot bind/unbind repeatedly.
        if(previousRight!=null && tick-previousRight<6)return;
        if(cooldown(lastRelease,player,tick)) return;
        bindings.bind(player,directTarget);WandText.refresh(this,player);refreshAfterEvent(player);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        Player player=event.getPlayer();if(!usable(player)) return;
        switch(event.getAction()) {
            case RIGHT_CLICK_AIR,RIGHT_CLICK_BLOCK -> {
                event.setUseInteractedBlock(Event.Result.DENY);event.setUseItemInHand(player.isSneaking()||sneaking.contains(player.getUniqueId())?Event.Result.ALLOW:Event.Result.DENY);right(player,null);
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
    @EventHandler public void stopUsing(io.papermc.paper.event.player.PlayerStopUsingItemEvent event){if(isWand(event.getItem())){finishingBulkClick.remove(event.getPlayer().getUniqueId());var pending=pendingBindings.get(event.getPlayer().getUniqueId());if(pending!=null&&pending.wand.equals(bindings.ledger().id(event.getItem())))pending.pressed=false;}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void consume(PlayerItemConsumeEvent event){if(isWand(event.getItem()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void teleport(EntityTeleportEvent event) {
        if(event.getEntity() instanceof Wolf wolf && commands.controlling(wolf)) event.setCancelled(true);
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
        if(usedBulk){var pending=pendingBindings.remove(id);Player player=event.getPlayer();if(pending!=null&&pending.pressed)finishingBulkClick.add(id);else player.clearActiveItem();
            if(pending!=null&&usable(player)){var selected=bindings.selection(player);if(selected!=null&&selected.id().equals(pending.wand))bindings.confirmBulk(player,pending.clicks);else feedback(player,"Bulk binding cancelled: selected wand changed.");WandText.refresh(this,player);refreshAfterEvent(player);}return;}
        if(!wasSneaking||!usable(event.getPlayer()))return;
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
        UUID id=event.getPlayer().getUniqueId();lastLeft.remove(id);lastRight.remove(id);lastRelease.remove(id);pendingSwing.remove(id);sneaking.remove(id);bulkChord.remove(id);pendingBindings.remove(id);finishingBulkClick.remove(id);feedbackUntil.remove(id);
    }
    @EventHandler public void load(EntitiesLoadEvent event) {for(Entity entity:event.getEntities()) if(entity instanceof Wolf wolf) commands.track(wolf);}
    @EventHandler public void unload(EntitiesUnloadEvent event) {for(Entity entity:event.getEntities()) if(entity instanceof Wolf wolf) commands.untrack(wolf);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void spawn(CreatureSpawnEvent event) {if(event.getEntity() instanceof Wolf wolf) Bukkit.getScheduler().runTask(this,()->{if(wolf.isValid())commands.track(wolf);});}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void tame(EntityTameEvent event) {if(event.getEntity() instanceof Wolf wolf) Bukkit.getScheduler().runTask(this,()->commands.track(wolf));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void death(EntityDeathEvent event) {if(event.getEntity() instanceof Wolf wolf){commands.died(wolf,event.getDrops());bindings.died(wolf);commands.untrack(wolf);for(Player player:Bukkit.getOnlinePlayers()){if(usable(player))showMode(player);refreshAfterEvent(player);}}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void cramming(EntityDamageEvent event){if(event.getCause()==EntityDamageEvent.DamageCause.CRAMMING&&event.getEntity() instanceof Wolf wolf&&wolf.isTamed()&&(commands.order(wolf)!=null||bindings.registry().bound(wolf.getUniqueId())))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void target(EntityTargetLivingEntityEvent event) {
        if(!(event.getEntity() instanceof Wolf wolf)) return;
        DogCommands.Order order=commands.order(wolf);if(order==null || event.getTarget()==null) return;
        if(wolf.isSitting() || !commands.allowedTarget(wolf,event.getTarget())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void merge(ItemMergeEvent event){FetchOrders.preserveDelivery(event.getEntity(),event.getTarget());}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(args.length==0) {sender.sendMessage("Dog Wand: right click your dog to bind/unbind; hold Shift + right click: 1-5 free %, 6-10 all %, 11 unbind all, 12+ cancel; release Shift to confirm; tap Shift to cycle; left click uses the mode. Main-hand wand takes priority.");return true;}
        if(args[0].equalsIgnoreCase("mode") && sender instanceof Player player) {showMode(player);return true;}
        if(args[0].equalsIgnoreCase("wands")&&sender instanceof Player player){player.sendMessage("Your active wand IDs: "+bindings.ledger().owned(player.getUniqueId()));return true;}
        if(args[0].equalsIgnoreCase("forget")&&sender instanceof Player player&&args.length==2){try{boolean done=bindings.ledger().retire(UUID.fromString(args[1]),player.getUniqueId());player.sendMessage(done?"Wand retired; its bindings and orders released.":"No active wand of yours has that ID.");}catch(IllegalArgumentException ex){player.sendMessage("Use /dogwand forget <wand UUID> from /dogwand wands.");}return true;}
        if(!sender.hasPermission("dogwand.admin")) {sender.sendMessage("You do not have permission.");return true;}
        if(args[0].equalsIgnoreCase("give")) {
            Player player=args.length>1?Bukkit.getPlayerExact(args[1]):sender instanceof Player p?p:null;
            if(player==null) {sender.sendMessage("Specify an online player.");return true;}
            var created=createWand();bindings.ledger().observe(created,player.getUniqueId(),"p:"+player.getUniqueId());var overflow=player.getInventory().addItem(created);
            overflow.values().forEach(item->player.getWorld().dropItemNaturally(player.getLocation(),item));return true;
        }
        if(args[0].equalsIgnoreCase("reload")) {reloadConfig();installRecipe();sender.sendMessage("Dog Wand configuration reloaded.");return true;}
        return false;
    }
}
