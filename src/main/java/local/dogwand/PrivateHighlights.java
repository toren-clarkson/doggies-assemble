package local.dogwand;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;

/** Only the owner's connection receives glow flags. Server entity state never changes. */
public final class PrivateHighlights {
    private record Key(UUID viewer,UUID dog){}
    private final Map<Key,Integer> expiry=new HashMap<>();
    private Bridge bridge;
    private boolean failed;
    public boolean show(Player owner,Collection<Wolf> dogs) {
        if(failed)return false;
        try{if(bridge==null)bridge=new Bridge();for(Wolf dog:dogs)expiry.put(new Key(owner.getUniqueId(),dog.getUniqueId()),Bukkit.getCurrentTick()+200);tick();return true;}
        catch(ReflectiveOperationException|LinkageError ex){failed=true;Bukkit.getLogger().log(java.util.logging.Level.SEVERE,"DogWand private highlight packet support unavailable",ex);return false;}
    }
    public void tick() {
        if(bridge==null)return;
        for(var entry:List.copyOf(expiry.entrySet())){
            var key=entry.getKey();Player owner=Bukkit.getPlayer(key.viewer);Entity found=Bukkit.getEntity(key.dog);
            boolean keep=owner!=null && found instanceof Wolf dog && dog.isValid() && owner.getUniqueId().equals(dog.getOwnerUniqueId()) && Bukkit.getCurrentTick()<entry.getValue();
            try{if(owner!=null && found instanceof Wolf dog && dog.getWorld()==owner.getWorld() && dog.getTrackedBy().contains(owner))bridge.send(owner,dog,keep);}
            catch(ReflectiveOperationException ex){Bukkit.getLogger().warning("DogWand could not refresh private highlight: "+ex.getClass().getSimpleName());expiry.remove(key);continue;}
            if(!keep)expiry.remove(key);
        }
    }
    public void stop(){expiry.replaceAll((k,v)->Integer.MIN_VALUE);tick();expiry.clear();}
    public static byte flags(byte original,boolean highlight){return highlight?(byte)(original|0x40):original;}
    private static final class Bridge {
        final Method playerHandle,entityHandle,entityData,getData,send;
        final Field connection;
        final Object accessor,serializer;
        final Constructor<?> valueCtor,packetCtor;
        Bridge()throws ReflectiveOperationException {
            Class<?> cp=Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer"),ce=Class.forName("org.bukkit.craftbukkit.entity.CraftEntity");
            playerHandle=cp.getMethod("getHandle");entityHandle=ce.getMethod("getHandle");
            entityData=Class.forName("net.minecraft.world.entity.Entity").getMethod("getEntityData");
            Class<?> access=Class.forName("net.minecraft.network.syncher.EntityDataAccessor"),serial=Class.forName("net.minecraft.network.syncher.EntityDataSerializer");
            serializer=Class.forName("net.minecraft.network.syncher.EntityDataSerializers").getField("BYTE").get(null);
            accessor=access.getConstructor(int.class,serial).newInstance(0,serializer);
            getData=Class.forName("net.minecraft.network.syncher.SynchedEntityData").getMethod("get",access);
            valueCtor=Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue").getConstructor(int.class,serial,Object.class);
            packetCtor=Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket").getConstructor(int.class,List.class);
            connection=Class.forName("net.minecraft.server.level.ServerPlayer").getField("connection");
            send=connection.getType().getMethod("send",Class.forName("net.minecraft.network.protocol.Packet"));
        }
        void send(Player owner,Wolf wolf,boolean on)throws ReflectiveOperationException {
            byte original=(Byte)getData.invoke(entityData.invoke(entityHandle.invoke(wolf)),accessor);
            Object value=valueCtor.newInstance(0,serializer,flags(original,on));
            send.invoke(connection.get(playerHandle.invoke(owner)),packetCtor.newInstance(wolf.getEntityId(),List.of(value)));
        }
    }
}
