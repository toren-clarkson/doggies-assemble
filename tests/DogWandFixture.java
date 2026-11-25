import local.dogwand.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Isolated integration harness: never included in the distributable plugin. */
public final class DogWandFixture extends JavaPlugin {
    DogWandPlugin plugin; Player a,b; Wolf dog,second,sitting,foreign; Zombie target;
    UUID persisted,deceased;int beforeDeath; Location stay; World world;
    void check(boolean condition,String message) {if(!condition) throw new AssertionError(message);}
    void checkLoreCount(int expected){var item=plugin.activeWand(a);check(item!=null&&item.getItemMeta().lore()!=null&&item.getItemMeta().lore().stream().anyMatch(c->c instanceof net.kyori.adventure.text.TextComponent text&&text.content().equals(expected+" bound dog(s)")),"Stale item lore count; expected "+expected);}
    Player player(String name) {
        var server=((CraftServer)Bukkit.getServer()).getServer();
        var level=((CraftWorld)Bukkit.getWorlds().getFirst()).getHandle();
        var profile=new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()),name);
        var p=new ServerPlayer(server,level,profile,ClientInformation.createDefault()) {
            @Override public boolean isImmobile() {return false;}
        };
        p.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(profile,false)) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet,io.netty.channel.ChannelFutureListener listener) {}
        };
        p.snapTo(.5,100,.5,-90,0);p.setNoGravity(true);
        server.getPlayerList().getPlayers().add(p);server.getPlayerList().getPlayersByUUID().put(p.getUUID(),p);level.addNewPlayer(p);
        return p.getBukkitEntity();
    }
    Wolf wolf(Player owner,double x,double z,boolean sit) {
        Wolf w=world.spawn(new Location(world,x,100,z),Wolf.class);w.setOwner(owner);w.setSitting(sit);w.setPersistent(true);
        plugin.dogCommands().track(w);return w;
    }
    void aim(Location at) {
        Location eye=a.getEyeLocation(), feet=a.getLocation();feet.setDirection(at.toVector().subtract(eye.toVector()));a.teleport(feet);
    }
    void selectMode(String id){for(int i=0;i<plugin.modes().ids().size() && !plugin.modes().current(a).id().equals(id);i++)plugin.modes().cycle(a);}
    void left() {Bukkit.getPluginManager().callEvent(new PlayerArmSwingEvent(a,EquipmentSlot.HAND));}
    PlayerInteractEvent right() {
        var e=new PlayerInteractEvent(a,Action.RIGHT_CLICK_AIR,a.getInventory().getItemInMainHand(),null,BlockFace.SELF,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(e);return e;
    }
    void setup() {
        plugin=(DogWandPlugin)Bukkit.getPluginManager().getPlugin("DogWand");world=Bukkit.getWorlds().getFirst();
        world.setTime(18000);world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);world.setGameRule(GameRule.DO_MOB_SPAWNING,false);
        for(int x=-8;x<=180;x++) for(int z=-8;z<=8;z++) {
            world.getBlockAt(x,99,z).setType(Material.STONE,false);
            for(int y=100;y<=104;y++) world.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
        a=player("DogTestA");b=player("DogTestB");b.teleport(new Location(world,.5,100,7.5));
        a.getInventory().setItemInMainHand(plugin.createWand());
        dog=wolf(a,2.5,3.5,false);second=wolf(a,3.5,4.5,false);sitting=wolf(a,2.5,6.5,true);foreign=wolf(b,3.5,7.5,false);
        plugin.bindings().bind(a,dog);plugin.bindings().bind(a,second);plugin.bindings().bind(a,sitting);
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
        String test=args[0];
        try {
            switch(test) {
                case "setup" -> setup();
                case "recipe" -> {
                    var recipe=(ShapedRecipe)Bukkit.getRecipe(new NamespacedKey("dogwand","dog_wand"));
                    check(recipe!=null,"Missing recipe");check(plugin.isWand(recipe.getResult()),"Recipe item marker mismatch");
                    check(recipe.getShape()[1].equals("BSB"),"Recipe shape");
                    check(!plugin.isWand(new ItemStack(Material.STICK)),"Ordinary stick recognized");
                    check(plugin.createWand().getMaxStackSize()==1,"Wand stacks");
                }
                case "shift" -> {
                    Bukkit.getPluginManager().callEvent(new PlayerToggleSneakEvent(a,true));
                    check(plugin.modes().current(a).id().equals("point_order"),"Holding Shift changed mode before release");
                    Bukkit.getPluginManager().callEvent(new PlayerToggleSneakEvent(a,true));
                    check(plugin.modes().current(a).id().equals("point_order"),"Held Shift cycles repeatedly");
                    Bukkit.getPluginManager().callEvent(new PlayerToggleSneakEvent(a,false));
                    check(plugin.modes().current(a).id().equals("sit_stand"),"Shift tap did not cycle");selectMode("point_order");
                    check(plugin.modes().ids().getLast().equals("release"),"Release mode is not last");
                }
                case "binding" -> {
                    var ids=plugin.bindings().selection(a).dogs();
                    check(ids.size()==3 && !ids.contains(foreign.getUniqueId()),"Wrong binding group");
                    plugin.bindings().bind(a,dog);checkLoreCount(2);check(!plugin.bindings().selection(a).dogs().contains(dog.getUniqueId()),"Right click did not unbind");plugin.bindings().bind(a,dog);checkLoreCount(3);
                    plugin.bindings().bind(a,foreign);check(plugin.bindings().selection(a).dogs().equals(ids),"Bound another owner's dog");
                    selectMode("sit_stand");left();
                }
                case "sitting" -> {
                    check(dog.isSitting() && second.isSitting() && sitting.isSitting(),"Group sit failed");left();
                }
                case "standing" -> {
                    check(!dog.isSitting() && !second.isSitting() && !sitting.isSitting(),"Group stand failed");
                    sitting.setSitting(true);selectMode("highlight");left();
                }
                case "highlighted" -> {
                    check(!dog.hasPotionEffect(org.bukkit.potion.PotionEffectType.GLOWING),"Owner-only highlight leaked into global potion effect");
                    check(!foreign.hasPotionEffect(org.bukkit.potion.PotionEffectType.GLOWING),"Foreign dog highlighted");selectMode("point_order");
                }
                case "auto_hostile" -> {selectMode("auto_target");target=world.spawn(new Location(world,10.5,100,.5),Zombie.class);target.setAI(false);left();}
                case "auto_hostilecheck" -> {check(plugin.bindings().autoState(a)==1 && plugin.dogCommands().order(dog).kind()==DogCommands.Kind.FOLLOW_HOSTILE,"Hostile toggle failed");left();}
                case "auto_allcheck" -> {check(plugin.bindings().autoState(a)==2 && plugin.dogCommands().order(dog).kind()==DogCommands.Kind.FOLLOW_ALL,"All-mobs toggle failed");left();}
                case "auto_offcheck" -> {check(plugin.bindings().autoState(a)==0 && plugin.dogCommands().order(dog)==null,"Off did not release auto targeting");target.remove();selectMode("point_order");}
                case "feeding" -> {dog.teleport(new Location(world,2.5,100,3.5));dog.setHealth(dog.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()-6);a.getInventory().addItem(new ItemStack(Material.BEEF,4));selectMode("feed");left();}
                case "fed" -> {check(dog.getHealth()==dog.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),"Dog not healed");selectMode("guard");aim(new Location(world,30.5,99.99,.5));left();}
                case "corner2" -> {world.getBlockAt(40,104,8).setType(Material.STONE);aim(new Location(world,40.5,104.5,8.5));left();}
                case "guarded" -> {
                    var order=plugin.dogCommands().order(dog);check(order!=null&&order.kind()==DogCommands.Kind.GUARD&&order.region()!=null,"Guard region did not assign");
                    var r=order.region();var from=new Location(world,r.minX()+.5,r.minY()+1,r.minZ()+.5);var to=from.clone();to.setX(r.minX()-.5);
                    to.setZ(from.getZ()+.1);var move=new io.papermc.paper.event.entity.EntityMoveEvent(dog,from,to);Bukkit.getPluginManager().callEvent(move);check(!move.isCancelled()&&r.bodyFits(move.getTo(),dog.getWidth(),dog.getHeight())&&move.getTo().getZ()==to.getZ(),"Guard boundary failed to clip escape while preserving edge movement");
                    plugin.dogCommands().release(a);world.getBlockAt(40,104,8).setType(Material.AIR);selectMode("point_order");
                }
                case "far" -> {
                    world.getBlockAt(160,101,0).setType(Material.STONE);aim(new Location(world,160.5,101.62,.5));
                    var hit=LoadedRaycast.cast(a);check(hit!=null && hit.block()!=null && hit.block().getX()==160,"Far block not selected");
                    world.getBlockAt(160,101,0).setType(Material.AIR);
                }
                case "sky" -> {
                    aim(new Location(world,.5,300,.5));int before=world.getLoadedChunks().length;
                    check(LoadedRaycast.cast(a)==null,"Sky hit");check(before==world.getLoadedChunks().length,"Sky loaded chunks");
                }
                case "boundary" -> {
                    aim(new Location(world,.5,101.62,100000));int before=world.getLoadedChunks().length;
                    check(LoadedRaycast.cast(a)==null,"Unloaded direction hit");check(before==world.getLoadedChunks().length,"Ray loaded chunks");
                }
                case "order" -> {
                    world.getBlockAt(20,100,0).setType(Material.STONE);aim(new Location(world,20.5,100.5,.5));left();
                }
                case "ordered" -> {
                    var order=plugin.dogCommands().order(dog);check(order!=null && order.kind()==DogCommands.Kind.STAY,"No stay order");
                    check(plugin.dogCommands().order(second)!=null,"Second owner dog missed");
                    check(plugin.dogCommands().order(sitting)==null && sitting.isSitting(),"Sitting dog commanded");
                    check(plugin.dogCommands().order(foreign)==null,"Foreign dog commanded");
                    stay=new Location(world,order.x(),order.y(),order.z());
                }
                case "arrived" -> {
                    check(dog.getLocation().distance(stay)<1.5,"Dog did not arrive: "+dog.getLocation()+" goal="+stay);
                    a.teleport(new Location(world,60.5,100,.5));
                }
                case "held" -> {
                    check(dog.getLocation().distance(stay)<1.5,"Dog followed/teleported to owner instead of holding");
                    var tp=new EntityTeleportEvent(dog,dog.getLocation(),a.getLocation());Bukkit.getPluginManager().callEvent(tp);check(tp.isCancelled(),"Active order allowed teleport");
                    dog.setVelocity(new org.bukkit.util.Vector(.6,0,0));
                }
                case "returned" -> check(dog.getLocation().distance(stay)<1.5,"Pushed dog did not return");
                case "release" -> {
                    var e=right();check(e.useItemInHand()==Event.Result.DENY,"Wand consumed/used");check(plugin.dogCommands().order(dog)!=null,"Right click released order");selectMode("release");Bukkit.getScheduler().runTaskLater(this,this::left,3);
                }
                case "released" -> {
                    check(plugin.dogCommands().order(dog)==null,"Release left an order");
                    check(sitting.isSitting(),"Release stood a sitting dog");selectMode("point_order");
                    var tp=new EntityTeleportEvent(dog,dog.getLocation(),a.getLocation());Bukkit.getPluginManager().callEvent(tp);check(!tp.isCancelled(),"Release still blocks teleport");
                }
                case "attack" -> {
                    a.teleport(new Location(world,.5,100,.5));dog.teleport(new Location(world,2.5,100,3.5));second.teleport(new Location(world,3.5,100,4.5));
                    world.getBlockAt(20,100,0).setType(Material.AIR);
                    target=world.spawn(new Location(world,10.5,100,.5),Zombie.class);target.setAI(false);target.setPersistent(true);
                    aim(target.getEyeLocation());left();
                }
                case "attacking" -> {
                    var order=plugin.dogCommands().order(dog);check(order!=null && order.kind()==DogCommands.Kind.ATTACK && order.target().equals(target.getUniqueId()),"Attack target mismatch");
                    check(sitting.isSitting() && plugin.dogCommands().order(sitting)==null,"Attack affected sitting dog");
                    var damage=EntityDamageByEntityEvent.class.getConstructor(Entity.class,Entity.class,EntityDamageEvent.DamageCause.class,double.class).newInstance(a,target,EntityDamageEvent.DamageCause.ENTITY_ATTACK,4.0);
                    Bukkit.getPluginManager().callEvent(damage);check(damage.isCancelled(),"Command click directly damaged target");
                }
                case "bitten" -> check(target.isDead() || target.getHealth()<20,"Dogs did not deal native damage");
                case "recall" -> {plugin.dogCommands().release(a);if(target.isValid())target.remove();}
                case "offhand" -> {
                    a.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));a.getInventory().setItemInOffHand(plugin.createWand());plugin.bindings().bind(a,dog);plugin.bindings().bind(a,second);plugin.bindings().bind(a,sitting);
                    world.getBlockAt(20,100,0).setType(Material.STONE);aim(new Location(world,20.5,100.5,.5));left();
                }
                case "offhandcheck" -> {
                    check(plugin.dogCommands().order(dog)!=null,"Offhand left did not command");
                    var e=new BlockBreakEvent(world.getBlockAt(20,100,0),a);Bukkit.getPluginManager().callEvent(e);check(e.isCancelled(),"Offhand wand broke block with main tool");
                    var inter=new PlayerInteractEntityEvent(a,sitting,EquipmentSlot.OFF_HAND);Bukkit.getPluginManager().callEvent(inter);check(inter.isCancelled(),"Wand toggled sit");
                    check(plugin.dogCommands().order(dog)!=null,"Binding released order");plugin.dogCommands().release(a);
                }
                case "sitcancel" -> {
                    plugin.dogCommands().point(a);dog.setSitting(true);
                }
                case "sitcancelcheck" -> {check(dog.isSitting() && plugin.dogCommands().order(dog)==null,"Manual sit retained queued order");dog.setSitting(false);}
                case "occlusion" -> {
                    target=world.spawn(new Location(world,10.5,100,.5),Zombie.class);target.setAI(false);
                    aim(target.getEyeLocation());world.getBlockAt(5,101,0).setType(Material.STONE);
                    var hit=LoadedRaycast.cast(a);check(hit!=null && hit.block()!=null && hit.block().getX()==5,"Selected entity through wall");
                    world.getBlockAt(5,101,0).setType(Material.AIR);hit=LoadedRaycast.cast(a);
                    check(hit!=null && hit.entity()==target,"Near entity did not occlude block");target.remove();
                }
                case "unloadedcancel" -> {
                    plugin.dogCommands().point(a);String saved=dog.getPersistentDataContainer().get(new NamespacedKey("dogwand","order_v1"),org.bukkit.persistence.PersistentDataType.STRING);
                    check(saved!=null,"Missing persistent order");plugin.dogCommands().untrack(dog);plugin.dogCommands().release(a);plugin.dogCommands().track(dog);
                    check(plugin.dogCommands().order(dog)==null,"Unloaded dog revived cancelled order");
                }
                case "bulk" -> {
                    Wolf extra=wolf(a,5.5,5.5,false);deceased=extra.getUniqueId();String mode=plugin.modes().current(a).id();
                    a.setSneaking(true);Bukkit.getPluginManager().callEvent(new PlayerToggleSneakEvent(a,true));right();a.setSneaking(false);Bukkit.getPluginManager().callEvent(new PlayerToggleSneakEvent(a,false));
                    check(plugin.modes().current(a).id().equals(mode),"Bulk chord unexpectedly cycled mode");
                    check(plugin.bindings().selection(a).dogs().contains(deceased),"Shift-right did not bind known owned dog");
                    check(!plugin.bindings().selection(a).dogs().contains(foreign.getUniqueId()),"Bulk binding included foreign dog");
                    beforeDeath=plugin.bindings().selection(a).dogs().size();extra.setHealth(0);
                }
                case "deathcount" -> {checkLoreCount(beforeDeath-1);var ids=plugin.bindings().selection(a).dogs();check(ids.size()==beforeDeath-1&&!ids.contains(deceased),"Dead dog remained in binding count");}
                case "persist" -> {
                    a.getInventory().setItemInMainHand(plugin.createWand());a.getInventory().setItemInOffHand(null);
                    plugin.bindings().bind(a,dog);dog.setSitting(false);plugin.dogCommands().point(a);check(plugin.dogCommands().order(dog)!=null,"No order to persist");
                    dog.setCustomName("PersistenceDog");persisted=dog.getUniqueId();
                    getConfig().set("dog",persisted.toString());saveConfig();world.save(false);
                }
                case "restart" -> {
                    plugin=(DogWandPlugin)Bukkit.getPluginManager().getPlugin("DogWand");world=Bukkit.getWorlds().getFirst();
                    a=player("DogTestA");a.getInventory().setItemInMainHand(plugin.createWand());
                    UUID id=UUID.fromString(getConfig().getString("dog"));
                    // The test dog is in the loaded spawn platform; only already-loaded entities are inspected.
                    dog=(Wolf)Bukkit.getEntity(id);check(dog!=null,"Saved dog did not reload");
                    check(plugin.dogCommands().order(dog)!=null,"Stay order did not survive restart");plugin.bindings().bind(a,dog);plugin.dogCommands().release(a);
                    check(plugin.dogCommands().order(dog)==null,"Restarted order did not release");
                }
                default -> throw new IllegalArgumentException(test);
            }
            getLogger().info("DWTEST PASS "+test);
        } catch(Throwable ex) {getLogger().log(java.util.logging.Level.SEVERE,"DWTEST FAIL "+test,ex);}
        return true;
    }
}
