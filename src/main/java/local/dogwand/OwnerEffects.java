package local.dogwand;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;

/** All new cosmetics use Player APIs; none broadcast to the world. */
public final class OwnerEffects {
    private OwnerEffects(){}
    public static void fed(Player owner,Wolf dog){owner.spawnParticle(Particle.HEART,dog.getLocation().add(0,dog.getHeight()+.2,0),4,.25,.15,.25,0);owner.playSound(dog.getLocation(),Sound.ENTITY_GENERIC_EAT,.4f,1.3f);}
    public static void beam(Player owner,Vector hit){for(Vector p:EffectGeometry.beam(owner.getEyeLocation().toVector(),hit))owner.spawnParticle(Particle.ELECTRIC_SPARK,p.getX(),p.getY(),p.getZ(),1,0,0,0,0);owner.spawnParticle(Particle.HAPPY_VILLAGER,hit.getX(),hit.getY()+.1,hit.getZ(),5,.15,.15,.15,0);}
    public static void corner(Player owner,Location point){owner.spawnParticle(Particle.END_ROD,point.clone().add(.5,.7,.5),8,.2,.3,.2,0);}
    public static void box(Player owner,WandBindings.RegionSelection selected){
        if(selected==null||!owner.getWorld().getUID().equals(selected.world()))return;
        var dust=new Particle.DustOptions(Color.fromRGB(65,220,135),.8f);
        for(Vector p:EffectGeometry.box(selected.region(),owner.getLocation().toVector(),96))owner.spawnParticle(Particle.DUST,p.getX(),p.getY(),p.getZ(),1,0,0,0,0,dust);
    }
    public static void action(Player owner,String mode){
        Sound sound=switch(mode){case "point_order"->Sound.ENTITY_ARROW_SHOOT;case "sit_stand"->Sound.BLOCK_NOTE_BLOCK_BASS;case "highlight"->Sound.BLOCK_AMETHYST_BLOCK_CHIME;case "auto_target"->Sound.BLOCK_NOTE_BLOCK_HAT;case "feed"->Sound.ENTITY_GENERIC_EAT;case "guard"->Sound.BLOCK_NOTE_BLOCK_PLING;case "fetch"->Sound.ENTITY_ITEM_PICKUP;default->Sound.ENTITY_EXPERIENCE_ORB_PICKUP;};
        owner.playSound(owner.getLocation(),sound,.35f,mode.equals("release")?1.5f:1.15f);
    }
}
