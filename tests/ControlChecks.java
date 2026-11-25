package local.dogwand;

import java.util.*;
import java.nio.file.*;
import java.lang.reflect.Proxy;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.persistence.*;
import com.destroystokyo.paper.entity.ai.GoalType;

public final class ControlChecks {
    static int count;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);System.out.println("PASS "+label);count++;}
    static WandMode mode(String id){return new WandMode(){public String id(){return id;}public String name(){return id;}public void leftClick(Player p){}};}
    static Player player(){
        var data=new CraftPersistentDataContainer(new CraftPersistentDataTypeRegistry());
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,a)->m.getName().equals("getPersistentDataContainer")?data:null);
    }
    public static void main(String[] args)throws Exception{
        UUID dog=UUID.randomUUID(),other=UUID.randomUUID(),owner=UUID.randomUUID(),newOwner=UUID.randomUUID();
        check(WandBindings.decode(WandBindings.encode(List.of(dog,other))).equals(Set.of(dog,other)),"multiple bindings survive item serialization");
        check(WandBindings.decode(dog+","+dog+",bad").equals(Set.of(dog)),"binding is idempotent and tolerates malformed entries");
        Set<UUID> first=new LinkedHashSet<>(Set.of(dog)),second=new LinkedHashSet<>(Set.of(dog));first.add(other);
        check(second.equals(Set.of(dog)),"separate wand lists can share a dog without sharing all bindings");
        var file=Files.createTempDirectory("dogwand-ledger-").resolve("revisions.yml");var ledger=new RevisionLedger(file);
        ledger.advance(owner,List.of(dog));check(ledger.get(owner,dog)==1 && ledger.get(owner,other)==0,"command on one dog does not invalidate another wand group");
        ledger.advance(owner,List.of(other));check(ledger.get(owner,dog)==1,"independent group command preserves existing order revision");
        ledger.advance(owner,List.of(dog));check(ledger.get(owner,dog)==2,"shared dog receives newer cancellation");
        check(ledger.get(newOwner,dog)==0,"former owner cannot invalidate transferred dog's new owner orders");
        var restored=new RevisionLedger(file);check(restored.get(owner,dog)==2 && restored.get(owner,other)==1,"cancellation revisions survive restart");
        check(CommandGoalTypes.movement().contains(GoalType.MOVE) && CommandGoalTypes.movement().contains(GoalType.LOOK)
                && !CommandGoalTypes.movement().contains(GoalType.TARGET),"movement goal routes to normal selector and blocks owner-follow movement/look");
        check(CommandGoalTypes.targeting().equals(EnumSet.of(GoalType.TARGET)),"target suppression routes separately");
        var modes=new ModeRegistry(new NamespacedKey("dogwand","mode"));
        modes.register(mode("point_order"));modes.register(mode("sit_stand"));modes.register(mode("highlight"));modes.registerLast(mode("release"));
        Player p=player(),q=player();
        check(modes.current(p).id().equals("point_order"),"default mode remains Point Order");
        check(modes.cycle(p).id().equals("sit_stand") && modes.cycle(p).id().equals("highlight") && modes.cycle(p).id().equals("release")
                && modes.cycle(p).id().equals("point_order"),"four-mode cycle wraps in requested order");
        modes.cycle(p);check(modes.current(q).id().equals("point_order"),"mode selection stays per-player");
        modes.register(mode("future"));check(modes.ids().getLast().equals("release"),"Release Orders remains last after future modes register");
        Files.delete(file);Files.delete(file.getParent());
        System.out.println("Control checks passed: "+count);
    }
}
