package local.dogwand;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Cancellation belongs to owner + dog, so independent wand groups cannot cancel one another. */
public final class RevisionLedger {
    private final Path file;
    private final Map<String,Long> revisions=new HashMap<>();
    public RevisionLedger(Path file) {
        this.file=file;var saved=YamlConfiguration.loadConfiguration(file.toFile());
        for(String key:saved.getKeys(false)) revisions.put(key,saved.getLong(key));
    }
    private static String key(UUID owner,UUID dog) {return owner+"_"+dog;}
    public long get(UUID owner,UUID dog) {return revisions.getOrDefault(key(owner,dog),0L);}
    public void advance(UUID owner,Collection<UUID> dogs) throws IOException {
        Map<String,Long> next=new HashMap<>(revisions);
        for(UUID dog:new HashSet<>(dogs)) next.put(key(owner,dog),Math.addExact(get(owner,dog),1));
        var saved=new YamlConfiguration();next.forEach(saved::set);
        Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");
        Files.writeString(tmp,saved.saveToString());
        try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
        revisions.clear();revisions.putAll(next);
    }
}
