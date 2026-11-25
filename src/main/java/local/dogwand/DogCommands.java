package local.dogwand;

import com.destroystokyo.paper.entity.ai.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.bukkit.attribute.*;
import org.bukkit.potion.*;

/** Ownership, durable orders, cancellation generations, and transient native goals. */
public final class DogCommands {
    public enum Kind { STAY, ATTACK, FOLLOW_HOSTILE, FOLLOW_ALL, GUARD, FETCH }
    public record Order(UUID owner,long revision,Kind kind,UUID world,double x,double y,double z,UUID target,UUID wand,GuardRegion region,List<UUID> queue) {
        public Order {queue=List.copyOf(new LinkedHashSet<>(queue));}
        public Order(UUID owner,long revision,Kind kind,UUID world,double x,double y,double z,UUID target){this(owner,revision,kind,world,x,y,z,target,null,null);}
        public Order(UUID owner,long revision,Kind kind,UUID world,double x,double y,double z,UUID target,UUID wand,GuardRegion region){this(owner,revision,kind,world,x,y,z,target,wand,region,target==null?List.of():List.of(target));}
        Location destination(){return new Location(Bukkit.getWorld(world),x,y,z);}
        String encode(){return String.join("|","4",owner.toString(),Long.toString(revision),kind.name(),world.toString(),Double.toString(x),Double.toString(y),Double.toString(z),target==null?"-":target.toString(),wand==null?"-":wand.toString(),region==null?"-":region.encode(),WandBindings.encode(queue));}
        static Order decode(String text){
            String[] p=text.split("\\|",-1);boolean v2=p.length==9&&p[0].equals("2"),v3=p.length==11&&p[0].equals("3"),v4=p.length==12&&p[0].equals("4");if(!v2&&!v3&&!v4)throw new IllegalArgumentException("Order schema");
            UUID target=p[8].equals("-")?null:UUID.fromString(p[8]);
            Order o=new Order(UUID.fromString(p[1]),Long.parseLong(p[2]),Kind.valueOf(p[3]),UUID.fromString(p[4]),Double.parseDouble(p[5]),Double.parseDouble(p[6]),Double.parseDouble(p[7]),target,v2||p[9].equals("-")?null:UUID.fromString(p[9]),v2||p[10].equals("-")?null:GuardRegion.decode(p[10]),v4?List.copyOf(WandBindings.decode(p[11])):target==null?List.of():List.of(target));
            if(!Double.isFinite(o.x)||!Double.isFinite(o.y)||!Double.isFinite(o.z)||(o.kind==Kind.GUARD&&o.region==null)||(o.kind==Kind.ATTACK&&(o.queue.isEmpty()||!Objects.equals(o.target,o.queue.getFirst()))))throw new IllegalArgumentException("Order data");return o;
        }
        Order withQueue(List<UUID> targets){return new Order(owner,revision,kind,world,x,y,z,targets.isEmpty()?null:targets.getFirst(),wand,region,targets);}
        Order enqueue(UUID id){var targets=new ArrayList<>(queue);if(!targets.contains(id))targets.add(id);return withQueue(targets);}
    }
    private record Corner(UUID owner,UUID world,int x,int y,int z){}
    private final Map<UUID,Corner> corners=new HashMap<>();
    private final PrivateHighlights highlights=new PrivateHighlights();
    private final AutomaticOrders automatic=new AutomaticOrders();
    private final FetchOrders fetching;
    private final DogWandPlugin plugin;
    private final NamespacedKey orderKey;
    private final GoalKey<Wolf> goalKey,targetGoalKey;
    private final NamespacedKey rangeKey;
    private final RevisionLedger revisions;
    private final Map<UUID,Wolf> wolves=new HashMap<>();
    private final Map<UUID,Order> active=new HashMap<>();
    private final Map<UUID,CommandGoal> goals=new HashMap<>();
    public DogCommands(DogWandPlugin plugin) {
        this.plugin=plugin;fetching=new FetchOrders(plugin);
        orderKey=new NamespacedKey(plugin,"order_v1");
        goalKey=GoalKey.of(Wolf.class,new NamespacedKey(plugin,"command"));
        targetGoalKey=GoalKey.of(Wolf.class,new NamespacedKey(plugin,"command_target"));
        rangeKey=new NamespacedKey(plugin,"command_navigation_range");
        revisions=new RevisionLedger(plugin.getDataFolder().toPath().resolve("dog-revisions.yml"));
    }
    public void track(Wolf wolf) {
        wolves.put(wolf.getUniqueId(),wolf);plugin.bindings().registry().observe(wolf);
        String saved=wolf.getPersistentDataContainer().get(orderKey,PersistentDataType.STRING);
        if(saved==null) return;
        // Pre-binding orders applied to the owner's entire pack. Retire them during upgrade.
        if(saved.startsWith("1|")){clear(wolf);return;}
        try {
            Order order=Order.decode(saved);
            if(validOwner(wolf,order) && boundOrder(wolf,order) && !plugin.bindings().ledger().retired(order.wand) && order.revision==revision(order.owner,wolf.getUniqueId()) && !wolf.isSitting()) attach(wolf,order);
            else clear(wolf);
        } catch(RuntimeException ex) {
            plugin.getLogger().warning("Discarding invalid saved order on wolf "+wolf.getUniqueId());clear(wolf);
        }
    }
    public void observeLoaded(){for(Wolf wolf:wolves.values())plugin.bindings().registry().observe(wolf);}
    public void untrack(Wolf wolf) {
        automatic.release(wolf.getUniqueId());fetching.release(wolf.getUniqueId());
        wolves.remove(wolf.getUniqueId());active.remove(wolf.getUniqueId());goals.remove(wolf.getUniqueId());
        detach(wolf);
    }
    public void stop() {
        highlights.stop();
        for(Wolf wolf:List.copyOf(wolves.values())) {
            detach(wolf);
            if(active.containsKey(wolf.getUniqueId())) {wolf.getPathfinder().stopPathfinding();wolf.setTarget(null);wolf.setAngry(false);}
        }
        goals.clear();active.clear();wolves.clear();
    }
    public Order order(Wolf wolf) { return active.get(wolf.getUniqueId()); }
    public long revision(UUID owner,UUID dog) {return revisions.get(owner,dog);}
    private Set<UUID> selected(Player player) {var wand=plugin.bindings().selection(player);return wand==null?Set.of():wand.dogs();}
    private List<Wolf> eligible(Player player,Set<UUID> ids) {return wolves.values().stream().filter(w->ids.contains(w.getUniqueId()) && w.isValid() && w.isTamed() && player.getUniqueId().equals(w.getOwnerUniqueId())).toList();}
    private void detach(Wolf wolf) {
        Bukkit.getMobGoals().removeGoal(wolf,goalKey);Bukkit.getMobGoals().removeGoal(wolf,targetGoalKey);
        var range=wolf.getAttribute(Attribute.FOLLOW_RANGE);if(range!=null)range.removeModifier(rangeKey);
    }
    private void navigationRange(Wolf wolf,boolean enabled){NavigationRange.update(wolf.getAttribute(Attribute.FOLLOW_RANGE),rangeKey,enabled);}
    private boolean validOwner(Wolf wolf,Order order) {
        return wolf.isTamed() && order.owner.equals(wolf.getOwnerUniqueId()) && order.world.equals(wolf.getWorld().getUID());
    }
    private boolean boundOrder(Wolf wolf,Order order){return order.wand==null||!plugin.bindings().registry().tracked(order.wand)||plugin.bindings().registry().dogs(order.wand).contains(wolf.getUniqueId());}
    private void attach(Wolf wolf,Order order) {
        clear(wolf,false);
        active.put(wolf.getUniqueId(),order);
        wolf.getPersistentDataContainer().set(orderKey,PersistentDataType.STRING,order.encode());
        CommandGoal goal=new CommandGoal(wolf,order);goals.put(wolf.getUniqueId(),goal);
        navigationRange(wolf,order.kind!=Kind.FOLLOW_HOSTILE&&order.kind!=Kind.FOLLOW_ALL&&order.kind!=Kind.FETCH);
        Bukkit.getMobGoals().addGoal(wolf,-1,goal);
        Bukkit.getMobGoals().addGoal(wolf,-1,new TargetGoal(goal));
    }
    public void clear(Wolf wolf) {clear(wolf,true);}
    private void clear(Wolf wolf,boolean dropCargo) {
        if(dropCargo&&fetching.carrying(wolf))fetching.drop(wolf,wolf.getLocation());
        automatic.release(wolf.getUniqueId());
        fetching.release(wolf.getUniqueId());
        boolean controlled=active.remove(wolf.getUniqueId())!=null || wolf.getPersistentDataContainer().has(orderKey);
        goals.remove(wolf.getUniqueId());detach(wolf);
        wolf.getPersistentDataContainer().remove(orderKey);
        wolf.getPathfinder().stopPathfinding();wolf.setTarget(null);wolf.setAngry(false);wolf.setAggressive(false);
    }
    private boolean prepare(Player player,Set<UUID> ids){
        try{revisions.advance(player.getUniqueId(),ids);}catch(IOException ex){persistenceError(player,ex);return false;}
        for(Wolf wolf:eligible(player,ids))clear(wolf);
        plugin.bindings().autoState(player,0);plugin.bindings().guardRegion(player,null);
        plugin.bindings().fetchEnabled(player,false);
        var binding=plugin.bindings().selection(player);if(binding!=null)corners.remove(binding.id());return true;
    }
    public void release(Player player) {
        Set<UUID> ids=selected(player);int count=eligible(player,ids).size();if(!prepare(player,ids))return;
        plugin.feedback(player,"Released orders | "+count+" loaded dog(s)");
    }
    private void persistenceError(Player player,Exception ex) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not persist dog command revision",ex);
        plugin.feedback(player,"Could not save the order. Check the server log.");
    }
    public boolean attackable(Player owner,Entity entity) {
        if(!(entity instanceof LivingEntity living) || living.isDead() || living.isInvulnerable() || entity==owner) return false;
        if(entity instanceof ArmorStand) return false;
        if(entity instanceof Tameable pet && owner.getUniqueId().equals(pet.getOwnerUniqueId())) return false;
        if(entity instanceof Player target) {
            if(!plugin.getConfig().getBoolean("attack-players",true) || !target.getWorld().getPVP()
                    || target.getGameMode()==GameMode.CREATIVE || target.getGameMode()==GameMode.SPECTATOR) return false;
            var team=owner.getScoreboard().getEntryTeam(owner.getName());
            if(team!=null && team.hasEntry(target.getName()) && !team.allowFriendlyFire()) return false;
        }
        return true;
    }
    public void point(Player owner) {
        LoadedRaycast.Hit hit=LoadedRaycast.cast(owner);if(hit==null)return;
        var targets=new LinkedHashSet<UUID>();
        for(Entity entity:owner.getWorld().getNearbyEntities(hit.position().toLocation(owner.getWorld()),PointTargets.RADIUS,PointTargets.RADIUS,PointTargets.RADIUS))
            if(entity instanceof Mob&&attackable(owner,entity)&&PointTargets.within(hit.position(),entity.getBoundingBox()))targets.add(entity.getUniqueId());
        if(hit.entity()!=null&&attackable(owner,hit.entity()))targets.add(hit.entity().getUniqueId());
        Set<UUID> ids=selected(owner);List<Wolf> dogs=eligible(owner,ids).stream().filter(w->!w.isSitting()&&w.getWorld()==owner.getWorld()).sorted(Comparator.comparing(Entity::getUniqueId)).toList();
        if(dogs.isEmpty()){plugin.feedback(owner,"No bound, loaded, standing dogs in this dimension.");return;}
        UUID wand=plugin.bindings().selection(owner).id();
        if(!targets.isEmpty()){
            var combined=new LinkedHashSet<UUID>();for(Wolf dog:dogs){Order old=order(dog);if(old!=null&&old.kind==Kind.ATTACK&&old.owner.equals(owner.getUniqueId())&&old.world.equals(owner.getWorld().getUID())&&Objects.equals(old.wand,wand))combined.addAll(old.queue);}
            combined.addAll(targets);List<UUID> selectedTargets=List.copyOf(combined);if(!prepare(owner,ids))return;
            Location p=hit.position().toLocation(owner.getWorld());for(Wolf dog:dogs)attach(dog,new Order(owner.getUniqueId(),revision(owner.getUniqueId(),dog.getUniqueId()),Kind.ATTACK,p.getWorld().getUID(),p.getX(),p.getY(),p.getZ(),selectedTargets.getFirst(),wand,null,selectedTargets));
            plan();OwnerEffects.beam(owner,hit.position());plugin.feedback(owner,"Attack selection | "+selectedTargets.size()+" targets | "+dogs.size()+" dogs split evenly");return;
        }
        Location center=Destinations.near(owner.getWorld(),hit.position(),3,4);if(center==null){plugin.feedback(owner,"No suitable standing position near that point.");return;}
        Map<UUID,Location> places=new LinkedHashMap<>();double radius=PackFormation.radius(ids.size())+2;var offsets=PackFormation.stay(dogs.size());
        for(int i=0;i<dogs.size();i++){var desired=center.toVector().add(offsets.get(i));Location foot=Destinations.near(owner.getWorld(),desired,2,4,p->{double dx=p.getX()-center.getX(),dz=p.getZ()-center.getZ();return dx*dx+dz*dz<=radius*radius&&places.values().stream().noneMatch(q->q.toVector().distanceSquared(p.toVector())<1.0);});if(foot!=null)places.put(dogs.get(i).getUniqueId(),foot);}
        if(places.isEmpty()){plugin.feedback(owner,"No safe standing positions near that point.");return;}
        if(!prepare(owner,ids))return;for(Wolf dog:dogs){Location p=places.get(dog.getUniqueId());if(p!=null)attach(dog,new Order(owner.getUniqueId(),revision(owner.getUniqueId(),dog.getUniqueId()),Kind.STAY,p.getWorld().getUID(),p.getX(),p.getY(),p.getZ(),null,wand,null));}
        OwnerEffects.beam(owner,hit.position());plugin.feedback(owner,"Spread and stay | "+places.size()+" dogs | radius "+String.format(java.util.Locale.ROOT,"%.1f",radius));
    }
    public void fetch(Player owner){
        var selected=plugin.bindings().selection(owner);if(selected==null)return;
        boolean on=plugin.bindings().fetchEnabled(owner)||active.values().stream().anyMatch(o->o.kind==Kind.FETCH&&Objects.equals(o.wand,selected.id())&&o.owner.equals(owner.getUniqueId()));
        if(on){release(owner);plugin.feedback(owner,"Fetch: OFF | Released fetch orders");return;}
        if(assign(owner,Kind.FETCH,null,owner.getLocation())){plugin.bindings().fetchEnabled(owner,true);plan();plugin.feedback(owner,"Fetch: ON | Continuously collect items within 20 blocks");}
    }
    public boolean controlling(Wolf dog){Order o=order(dog);if(o==null||dog.isSitting())return false;
        if(o.kind==Kind.FETCH)return fetching.working(dog,o);
        if(o.kind==Kind.FOLLOW_HOSTILE||o.kind==Kind.FOLLOW_ALL||o.kind==Kind.ATTACK){var goal=goals.get(dog.getUniqueId());return goal!=null&&goal.automaticTarget!=null&&goal.automaticTarget.isValid()&&!goal.automaticTarget.isDead();}return true;
    }
    public void died(Wolf dog,List<org.bukkit.inventory.ItemStack> drops){fetching.death(dog,drops);}
    public org.bukkit.inventory.ItemStack cargo(Wolf dog){return fetching.cargo(dog);}
    public void storeCargo(Wolf dog,org.bukkit.inventory.ItemStack item){fetching.storeCargo(dog,item);}
    public void retireWand(UUID wand){for(var e:List.copyOf(active.entrySet()))if(wand.equals(e.getValue().wand)){Wolf dog=wolves.get(e.getKey());if(dog!=null)clear(dog);}}
    public List<String> status(Player player){
        var selection=plugin.bindings().selection(player);if(selection==null||selection.dogs().isEmpty())return List.of("No bound dogs");
        var counts=new LinkedHashMap<String,Integer>();int unloaded=0;for(UUID id:selection.dogs()){
            Wolf dog=wolves.get(id);if(dog==null||!dog.isValid()){unloaded++;continue;}if(!player.getUniqueId().equals(dog.getOwnerUniqueId())){counts.merge("Not currently yours",1,Integer::sum);continue;}
            Order o=order(dog);String action=dog.isSitting()?"Sitting":o==null?"Following owner":switch(o.kind){
                case STAY->"Go / stay";case ATTACK->"Attack ("+o.queue.size()+" selected)";case GUARD->"Guarding region";
                case FETCH->fetching.carrying(dog)?"Fetching: returning":fetching.target(dog)==null?"Fetching: waiting":"Fetching: collecting";
                case FOLLOW_HOSTILE,FOLLOW_ALL->"Auto "+(o.kind==Kind.FOLLOW_HOSTILE?"hostile":"all mobs")+(controlling(dog)?": attacking":": idle / following");};counts.merge(action,1,Integer::sum);
        }
        List<String> lines=new ArrayList<>();counts.forEach((action,n)->lines.add(action+" | "+n));if(unloaded>0)lines.add("Unloaded / unknown | "+unloaded);return lines;
    }
    private void plan(){
        var auto=new ArrayList<AutomaticOrders.Member>();var fetch=new ArrayList<FetchOrders.Member>();
        for(var e:active.entrySet()){Wolf dog=wolves.get(e.getKey());Order o=e.getValue();if(dog==null)continue;
            if(o.kind==Kind.FOLLOW_HOSTILE||o.kind==Kind.FOLLOW_ALL||o.kind==Kind.GUARD||o.kind==Kind.ATTACK)auto.add(new AutomaticOrders.Member(dog,o));
            if(o.kind==Kind.FETCH)fetch.add(new FetchOrders.Member(dog,o));}
        automatic.plan(auto,this::attackable);fetching.plan(fetch);
        for(var m:auto){CommandGoal goal=goals.get(m.dog().getUniqueId());if(goal==null)continue;var previous=goal.automaticTarget;goal.automaticTarget=automatic.target(m.dog(),m.order());
            if(previous!=goal.automaticTarget){goal.approach=null;goal.nextApproach=0;goal.nextPath=0;m.dog().getPathfinder().stopPathfinding();}
            navigationRange(m.dog(),m.order().region!=null||goal.automaticTarget!=null);
            if(goal.automaticTarget!=null&&m.dog().getTarget()!=goal.automaticTarget)m.dog().setTarget(goal.automaticTarget);
            if(previous!=null&&goal.automaticTarget==null&&m.order().region==null){m.dog().getPathfinder().stopPathfinding();m.dog().setTarget(null);m.dog().setAngry(false);m.dog().setAggressive(false);}}
        for(var m:fetch)navigationRange(m.dog(),fetching.working(m.dog(),m.order()));
    }
    /** Clean up outside GoalSelector iteration; removing a running goal inside tick is unsafe. */
    public void maintain() {
        highlights.tick();if(Bukkit.getCurrentTick()%200==0)automatic.trim();if(Bukkit.getCurrentTick()%100==0){observeLoaded();plugin.bindings().flush();}
        for(var entry:List.copyOf(active.entrySet())) {
            Wolf wolf=wolves.get(entry.getKey());Order order=entry.getValue();
            if(wolf==null) {active.remove(entry.getKey());continue;}
            if(!wolf.isValid()) {untrack(wolf);continue;}
            if(!validOwner(wolf,order) || !boundOrder(wolf,order) || order.revision!=revision(order.owner,wolf.getUniqueId()) || wolf.isSitting()) {clear(wolf);continue;}
            if(order.kind==Kind.ATTACK) {
                Player owner=Bukkit.getPlayer(order.owner);
                List<UUID> remaining=order.queue.stream().filter(id->{Entity found=Bukkit.getEntity(id);return found instanceof LivingEntity living&&living.isValid()&&!living.isDead()&&living.getWorld()==wolf.getWorld()&&(owner==null||attackable(owner,living));}).toList();
                if(remaining.isEmpty()){clear(wolf);continue;}
                if(!remaining.equals(order.queue))attach(wolf,order.withQueue(remaining));
            }
        }
        if(Bukkit.getCurrentTick()%10==0)plan();
        if(Bukkit.getCurrentTick()%20==0)for(Wolf dog:wolves.values())if(fetching.carrying(dog)&&(order(dog)==null||order(dog).kind!=Kind.FETCH))fetching.drop(dog,dog.getLocation());
    }

    public void sitStand(Player player) {
        Set<UUID> ids=selected(player);List<Wolf> dogs=eligible(player,ids);
        if(dogs.isEmpty()){plugin.feedback(player,"No bound dogs loaded. Right click your dogs to bind them.");return;}
        boolean sit=dogs.stream().anyMatch(w->!w.isSitting());
        if(!prepare(player,ids))return;
        for(Wolf wolf:dogs)wolf.setSitting(sit);
        plugin.bindings().autoState(player,0);
        plugin.feedback(player,(sit?"Sit":"Stand")+" | "+dogs.size()+" bound dog(s)");
    }
    public void highlight(Player player) {
        List<Wolf> dogs=eligible(player,selected(player));
        if(highlights.show(player,dogs))plugin.feedback(player,"Highlighted "+dogs.size()+" bound dog(s)");
        else plugin.feedback(player,"Private highlights unavailable on this server build; see console.");
    }
    public void feed(Player player){var result=DogFeeding.feed(player,eligible(player,selected(player)));plugin.feedback(player,"Fed "+result.dogs()+" dog(s) using "+result.portions()+" meat items");}
    public boolean unbind(Player player,Wolf wolf,UUID wand){
        Order order=order(wolf);if(order==null||!order.owner.equals(player.getUniqueId())||(order.wand!=null&&!order.wand.equals(wand)))return true;
        try{revisions.advance(player.getUniqueId(),List.of(wolf.getUniqueId()));clear(wolf);return true;}catch(IOException ex){persistenceError(player,ex);return false;}
    }
    public boolean unbindGroup(Player player,UUID wand,Set<UUID> ids){
        var dogs=eligible(player,ids).stream().filter(dog->{Order o=order(dog);return o!=null&&Objects.equals(o.wand,wand)&&o.owner.equals(player.getUniqueId());}).toList();
        try{revisions.advance(player.getUniqueId(),dogs.stream().map(Entity::getUniqueId).toList());for(Wolf dog:dogs)clear(dog);return true;}catch(IOException ex){persistenceError(player,ex);return false;}
    }
    public String hint(Player player,WandMode mode){
        if(mode.id().equals("fetch"))return "["+(plugin.bindings().fetchEnabled(player)?"ON":"OFF")+"] Click to toggle item fetching. Range 20 blocks";
        if(mode.id().equals("auto_target"))return "["+List.of("Off","Hostile Only","All Mobs").get(plugin.bindings().autoState(player))+"] Click to cycle. Range 20 blocks, excluding players";
        if(mode.id().equals("guard")){var wand=plugin.bindings().selection(player);Corner first=wand==null?null:corners.get(wand.id());if(first!=null&&first.owner.equals(player.getUniqueId()))return "Corner 2: click opposite corner to select guard region";return plugin.bindings().guardRegion(player)!=null?"Region selected | Click a block to start a new region selection":"Corner 1: click a block to start the guard region";}
        return mode.description();
    }
    public void toggleAuto(Player player){
        int state=(plugin.bindings().autoState(player)+1)%3;
        if(state==0){release(player);return;}
        if(assign(player,state==1?Kind.FOLLOW_HOSTILE:Kind.FOLLOW_ALL,null,player.getLocation())){plugin.bindings().autoState(player,state);plugin.feedback(player,"Auto Target: "+(state==1?"Hostile Only":"All Mobs")+" within 20 blocks, excluding players");}
    }
    private boolean assign(Player owner,Kind kind,GuardRegion region,Location destination){
        Set<UUID> ids=selected(owner);List<Wolf> dogs=eligible(owner,ids).stream().filter(w->!w.isSitting()&&w.getWorld()==owner.getWorld()).toList();
        if(dogs.isEmpty()){plugin.feedback(owner,"No loaded standing bound dogs in this dimension.");return false;}
        if(!prepare(owner,ids))return false;
        UUID wand=plugin.bindings().selection(owner).id();
        for(Wolf wolf:dogs)attach(wolf,new Order(owner.getUniqueId(),revision(owner.getUniqueId(),wolf.getUniqueId()),kind,destination.getWorld().getUID(),destination.getX(),destination.getY(),destination.getZ(),null,wand,region));
        if(kind!=Kind.FOLLOW_HOSTILE&&kind!=Kind.FOLLOW_ALL)plugin.bindings().autoState(owner,0);
        return true;
    }
    public void guard(Player player){
        var wand=plugin.bindings().selection(player);if(wand==null)return;
        var hit=LoadedRaycast.cast(player);if(hit==null||hit.block()==null){plugin.feedback(player,"Point at a block to set the guard region corner.");return;}
        var block=hit.block();Corner first=corners.get(wand.id());
        if(first==null||!first.owner.equals(player.getUniqueId())){corners.put(wand.id(),new Corner(player.getUniqueId(),block.getWorld().getUID(),block.getX(),block.getY(),block.getZ()));OwnerEffects.corner(player,block.getLocation());plugin.feedback(player,"Corner 1 set: "+block.getX()+", "+block.getY()+", "+block.getZ()+". Click for corner 2.");return;}
        if(!first.world.equals(block.getWorld().getUID())){plugin.feedback(player,"Both corners must be in the same dimension.");return;}
        var region=GuardRegion.between(first.x,first.y,first.z,block.getX(),block.getY(),block.getZ());
        Location footing=AutomaticOrders.inside(block.getWorld(),region,player.getLocation().toVector(),.62,.9);
        if(footing==null)footing=AutomaticOrders.inside(block.getWorld(),region,hit.position(),.62,.9);
        if(footing==null){plugin.feedback(player,"The box needs walkable space, including air above the floor. Choose another second corner.");return;}
        if(assign(player,Kind.GUARD,region,footing)){plugin.bindings().guardRegion(player,new WandBindings.RegionSelection(first.world,region));corners.remove(wand.id());plugin.feedback(player,"Guard Region set. Dogs roam inside and attack hostile mobs entering it.");}
    }
    public boolean mayMove(Wolf wolf,Location from,Location to){Order order=order(wolf);return order==null||order.region==null||!order.region.bodyFits(from,wolf.getWidth(),wolf.getHeight())||order.region.bodyFits(to,wolf.getWidth(),wolf.getHeight());}
    public Location constrainMove(Wolf wolf,Location from,Location to){var order=order(wolf);return order==null||order.region==null?to:GuardNavigation.clipMove(order.region,from,to,wolf.getWidth(),wolf.getHeight());}
    public boolean allowedTarget(Wolf wolf,LivingEntity target){
        Order order=order(wolf);if(order==null||!controlling(wolf))return true;
        if(order.kind==Kind.FETCH)return false;
        CommandGoal goal=goals.get(wolf.getUniqueId());return goal!=null&&goal.automaticTarget==target&&(order.kind==Kind.ATTACK?order.queue.contains(target.getUniqueId()):AutomaticOrders.candidate(target,order.kind!=Kind.FOLLOW_ALL));
    }
    private int combatSlot(Wolf dog,LivingEntity target){
        return goals.values().stream().filter(g->g.wolf.isValid()&&!g.wolf.isSitting()&&g.order.owner.equals(dog.getOwnerUniqueId())
            &&g.wolf.getWorld()==dog.getWorld()&&g.automaticTarget==target)
            .map(g->g.wolf.getUniqueId()).sorted().toList().indexOf(dog.getUniqueId());
    }
    private final class TargetGoal implements Goal<Wolf> {
        private final CommandGoal movement;
        TargetGoal(CommandGoal movement){this.movement=movement;}
        public GoalKey<Wolf> getKey(){return targetGoalKey;}
        public EnumSet<GoalType> getTypes(){return CommandGoalTypes.targeting();}
        public boolean shouldActivate(){return movement.shouldActivate();}
        public boolean shouldStayActive(){return shouldActivate();}
    }

    private final class CommandGoal implements Goal<Wolf> {
        private final Wolf wolf;
        private final Order order;
        private final Goal<Mob> floating;
        private int nextPath=0,nextAttack=0,failures=0;
        private Location lastAttempt,roaming;
        private boolean roamingChosen,entering;
        private Location entryDestination;
        private int nextEntry;
        private Location approach;
        private UUID approachTarget;
        private int nextApproach;
        private LivingEntity automaticTarget;
        private int nextRoam=0;
        CommandGoal(Wolf wolf,Order order) {
            this.wolf=wolf;this.order=order;this.entering=order.region!=null;
            floating=Bukkit.getMobGoals().getGoal(wolf,VanillaGoal.FLOAT);
        }
        @Override public GoalKey<Wolf> getKey() {return goalKey;}
        @Override public EnumSet<GoalType> getTypes() {return CommandGoalTypes.movement();}
        @Override public boolean shouldActivate() {
            return active.get(wolf.getUniqueId())==order && !wolf.isSitting() && validOwner(wolf,order)
                    && order.revision==revision(order.owner,wolf.getUniqueId()) && controlling(wolf);
        }
        @Override public boolean shouldStayActive() {return shouldActivate();}
        @Override public void start() {wolf.getPathfinder().stopPathfinding();wolf.setTarget(null);}
        @Override public void stop() {wolf.getPathfinder().stopPathfinding();}
        @Override public void tick() {
            if(!shouldActivate()) return;
            int now=Bukkit.getCurrentTick();
            // Preserve native floating/jumping while our MOVE goal suppresses owner-following.
            if(floating!=null && floating.shouldActivate()) floating.tick();
            Location dest=order.destination();
            LivingEntity target=null;
            boolean returning=false;
            if(order.kind==Kind.FETCH){
                Player owner=Bukkit.getPlayer(order.owner);if(owner==null||owner.getWorld()!=wolf.getWorld())return;
                returning=true;
                if(fetching.carrying(wolf)){if(fetching.deliver(wolf,owner)){wolf.getPathfinder().stopPathfinding();return;}dest=owner.getLocation();}
                else {Item item=fetching.target(wolf);if(item==null)return;if(fetching.pickup(wolf,item,owner)){wolf.getPathfinder().stopPathfinding();nextPath=now;return;}dest=item.getLocation();}
                if(!wolf.getPathfinder().hasPath()&&wolf.getLocation().distanceSquared(dest)<2.25)wolf.setVelocity(GuardNavigation.finishEntry(wolf.getLocation(),dest,wolf.getVelocity()));
            }else if(order.kind==Kind.FOLLOW_HOSTILE||order.kind==Kind.FOLLOW_ALL||order.kind==Kind.GUARD){
                if(order.region!=null){if(!order.region.bodyFits(wolf.getLocation(),wolf.getWidth(),wolf.getHeight()))entering=true;else if(GuardNavigation.settled(order.region,wolf.getLocation(),wolf.getWidth(),wolf.getHeight()))entering=false;}
                boolean inside=order.region==null||(!entering&&order.region.bodyFits(wolf.getLocation(),wolf.getWidth(),wolf.getHeight()));
                if(!inside)automaticTarget=null;
                if(automaticTarget!=null){
                    Player owner=Bukkit.getPlayer(order.owner);
                    if(!AutomaticOrders.candidate(automaticTarget,order.kind!=Kind.FOLLOW_ALL)||automaticTarget.getWorld()!=wolf.getWorld()
                        ||(order.region!=null?!order.region.contains(automaticTarget.getLocation()):owner==null||owner.getWorld()!=wolf.getWorld()||automaticTarget.getLocation().distanceSquared(owner.getLocation())>400))automaticTarget=null;
                }
                target=automaticTarget;
                if(order.region!=null){
                    if(!inside){target=null;returning=true;
                        if(entryDestination==null||now>=nextEntry){entryDestination=AutomaticOrders.entry(wolf,order.region);nextEntry=now+20;}
                        dest=entryDestination==null?order.destination():entryDestination;
                        if(!wolf.getPathfinder().hasPath()&&wolf.getLocation().distanceSquared(dest)<2.25)wolf.setVelocity(GuardNavigation.finishEntry(wolf.getLocation(),dest,wolf.getVelocity()));
                    }
                    else if(target==null){if(roaming==null||now>=nextRoam){roaming=AutomaticOrders.roam(wolf,order.region);roamingChosen=roaming.distanceSquared(wolf.getLocation())>2.0;nextRoam=now+(roamingChosen?100:20);}dest=roaming;}
                }else if(target==null){Player owner=Bukkit.getPlayer(order.owner);dest=owner!=null&&owner.getWorld()==wolf.getWorld()?owner.getLocation():wolf.getLocation();}
            }else if(order.kind==Kind.ATTACK){
                target=automaticTarget;if(target==null||!target.isValid()||target.isDead()||target.getWorld()!=wolf.getWorld())return;
                Player owner=Bukkit.getPlayer(order.owner);if(owner!=null&&!attackable(owner,target))return;
            }
            if(target!=null){
                int slot=Math.max(0,combatSlot(wolf,target));
                if(approach==null||now>=nextApproach||!target.getUniqueId().equals(approachTarget)){
                    Vector wanted=target.getLocation().toVector().add(PackFormation.combat(slot));
                    approach=order.region==null?Destinations.near(wolf.getWorld(),wanted,1,4):AutomaticOrders.inside(wolf.getWorld(),order.region,wanted,wolf.getWidth(),wolf.getHeight());
                    approachTarget=target.getUniqueId();nextApproach=now+10;
                }
                dest=approach==null?target.getLocation():approach;
                if(wolf.getTarget()!=target)wolf.setTarget(target);wolf.lookAt(target);
                if(!wolf.getPathfinder().hasPath()&&wolf.getLocation().distanceSquared(dest)<2.25&&!wolf.getBoundingBox().clone().expand(.8,.25,.8).overlaps(target.getBoundingBox()))wolf.setVelocity(GuardNavigation.finishEntry(wolf.getLocation(),dest,wolf.getVelocity()));
                if(now>=nextAttack && wolf.getBoundingBox().clone().expand(.8,.25,.8).overlaps(target.getBoundingBox())&&wolf.hasLineOfSight(target)) {wolf.attack(target);nextAttack=now+20;}
            }else{
                if(wolf.getTarget()!=null)wolf.setTarget(null);
                double r=Math.max(.3,plugin.getConfig().getDouble("arrival-radius",.85));
                if(order.kind==Kind.STAY)r=Math.min(.4,r);
                if(order.kind==Kind.FOLLOW_HOSTILE||order.kind==Kind.FOLLOW_ALL)r=2.5;
                if(!returning&&wolf.getLocation().distanceSquared(dest)<=r*r){wolf.getPathfinder().stopPathfinding();failures=0;if(order.region!=null&&roamingChosen){roaming=null;nextRoam=now;roamingChosen=false;}return;}
                if(order.kind==Kind.STAY&&!wolf.getPathfinder().hasPath()&&wolf.getLocation().distanceSquared(dest)<2.25)wolf.setVelocity(GuardNavigation.finishEntry(wolf.getLocation(),dest,wolf.getVelocity()));
                wolf.lookAt(dest);
            }
            if(now<nextPath) return;
            nextPath=now+Math.max(5,plugin.getConfig().getInt("path-recalculate-ticks",20));
            if(lastAttempt!=null && lastAttempt.distanceSquared(dest)<.5 && failures>=5) {
                // Retry at a low rate while retaining the stay order, even when unreachable.
                nextPath=now+100;failures=0;
            }
            Location step=dest;
            double max=Math.clamp(plugin.getConfig().getDouble("navigation-step-blocks",24),8,32);
            Vector delta=dest.toVector().subtract(wolf.getLocation().toVector());
            if(delta.length()>max) {
                Vector toward=wolf.getLocation().toVector().add(delta.normalize().multiply(max));
                step=Destinations.near(wolf.getWorld(),toward,2,5);
                if(step==null) {failures++;return;}
            }
            if(!step.getWorld().isChunkLoaded(step.getBlockX()>>4,step.getBlockZ()>>4)) return;
            var path=wolf.getPathfinder().findPath(step,0);
            if(order.region!=null&&order.region.bodyFits(wolf.getLocation(),wolf.getWidth(),wolf.getHeight())&&path!=null&&!GuardNavigation.pathFits(order.region,path.getPoints(),path.getNextPointIndex(),wolf.getWidth(),wolf.getHeight())){failures++;if(target==null){roaming=null;nextRoam=now;}return;}
            if(path==null || !wolf.getPathfinder().moveTo(path,Math.clamp(plugin.getConfig().getDouble("movement-speed",1.2),.1,3)) || (order.region!=null&&!path.canReachFinalPoint())) failures++;
            else failures=0;
            if(order.kind==Kind.FETCH&&failures>=5&&!fetching.carrying(wolf)){fetching.reject(wolf);wolf.getPathfinder().stopPathfinding();}
            lastAttempt=dest.clone();
        }
    }
}
