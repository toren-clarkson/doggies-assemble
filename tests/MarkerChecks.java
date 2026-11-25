import local.dogwand.WandIdentity;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry;

/** Uses Paper's real NBT-backed container, including the exact reported ByteTag failure. */
public final class MarkerChecks {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    public static void main(String[] args){
        var key=new NamespacedKey("dogwand","wand");
        var data=new CraftPersistentDataContainer(new CraftPersistentDataTypeRegistry());
        check(!WandIdentity.matches(data,key),"absent marker rejected");
        data.set(key,PersistentDataType.BYTE,(byte)1);
        boolean reproduced=false;
        try{data.get(key,PersistentDataType.INTEGER);}catch(IllegalArgumentException ex){reproduced=ex.getMessage().contains("ByteTag");}
        check(reproduced,"reproduced original ByteTag/Integer exception");
        check(WandIdentity.matches(data,key),"existing byte wand accepted without exception");
        data.set(key,PersistentDataType.INTEGER,1);
        check(WandIdentity.matches(data,key),"existing integer wand accepted");
        data.set(key,PersistentDataType.BYTE,(byte)0);
        check(!WandIdentity.matches(data,key),"zero byte rejected");
        data.set(key,PersistentDataType.INTEGER,2);
        check(!WandIdentity.matches(data,key),"wrong integer rejected");
        data.set(key,PersistentDataType.STRING,"1");
        check(!WandIdentity.matches(data,key),"string marker rejected safely");
        data.set(key,PersistentDataType.LONG,1L);
        check(!WandIdentity.matches(data,key),"unsupported numeric type rejected safely");
        System.out.println("Marker checks passed: "+checks);
    }
}
