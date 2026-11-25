package local.dogwand;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import io.papermc.paper.persistence.PersistentDataContainerView;

/** Match the stored primitive type before extracting; JSON/SNBT can choose different numeric types. */
public final class WandIdentity {
    private WandIdentity() {}
    public static boolean matches(PersistentDataContainerView data, NamespacedKey key) {
        if(data.has(key,PersistentDataType.BYTE)) return Byte.valueOf((byte)1).equals(data.get(key,PersistentDataType.BYTE));
        if(data.has(key,PersistentDataType.INTEGER)) return Integer.valueOf(1).equals(data.get(key,PersistentDataType.INTEGER));
        return false;
    }
}
