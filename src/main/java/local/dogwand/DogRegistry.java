package local.dogwand;

import java.util.*;
import java.io.IOException;
import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Wolf;

/** Persistent known ownership and death tombstones. Unloaded never means dead. */
public final class DogRegistry {
    private final Path file;
    private final Map<UUID,UUID> owners=new HashMap<>();
    private final Set<UUID> dead=new HashSet<>();
    private boolean dirty;
    public DogRegistry(Path file){
        this.file=file;var y=YamlConfiguration.loadConfiguration(file.toFile());
        var saved=y.getConfigurationSection("owners");if(saved!=null)for(String key:saved.getKeys(false))try{owners.put(UUID.fromString(key),UUID.fromString(saved.getString(key)));}catch(RuntimeException ignored){}
        for(String id:y.getStringList("dead"))try{dead.add(UUID.fromString(id));}catch(IllegalArgumentException ignored){}
        owners.keySet().removeAll(dead);
    }
    public void observe(Wolf wolf){if(wolf.isTamed()&&wolf.getOwnerUniqueId()!=null&&!wolf.isDead())observe(wolf.getUniqueId(),wolf.getOwnerUniqueId());}
    public void observe(UUID dog,UUID owner){if(dead.contains(dog))return;if(!owner.equals(owners.put(dog,owner)))dirty=true;}
    public boolean isDead(UUID dog){return dead.contains(dog);}
    public boolean known(UUID dog){return owners.containsKey(dog)||dead.contains(dog);}
    public Set<UUID> owned(UUID owner){Set<UUID> ids=new LinkedHashSet<>();owners.forEach((dog,who)->{if(owner.equals(who)&&!dead.contains(dog))ids.add(dog);});return ids;}
    public void died(UUID dog)throws IOException{dirty|=dead.add(dog);dirty|=owners.remove(dog)!=null;flush();}
    public Set<UUID> living(Collection<UUID> ids){Set<UUID> result=new LinkedHashSet<>(ids);result.removeAll(dead);return result;}
    public void flush()throws IOException{
        if(!dirty)return;var y=new YamlConfiguration();owners.forEach((dog,owner)->y.set("owners."+dog,owner.toString()));y.set("dead",dead.stream().map(UUID::toString).toList());
        Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(tmp,y.saveToString());
        try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
        dirty=false;
    }
}
