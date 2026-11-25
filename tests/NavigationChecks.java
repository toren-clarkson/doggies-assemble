package local.dogwand;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.craftbukkit.attribute.CraftAttributeInstance;

/** Reproduce the reported exception using actual Minecraft/Paper attribute code. */
public final class NavigationChecks {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    public static void main(String[] args){
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var nativeRange=new net.minecraft.world.entity.ai.attributes.AttributeInstance(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE,attribute->{});
        AttributeInstance range=new CraftAttributeInstance(nativeRange,Attribute.FOLLOW_RANGE);range.setBaseValue(16);
        var key=new NamespacedKey("dogwand","command_navigation_range");var external=new NamespacedKey("fixture","spawn_range_bonus");
        range.addTransientModifier(new AttributeModifier(external,-.05,AttributeModifier.Operation.ADD_SCALAR));
        range.addTransientModifier(new AttributeModifier(key,48-range.getValue(),AttributeModifier.Operation.ADD_NUMBER));
        check(range.getValue()<48,"negative spawn-style multiplier keeps effective range below requested 48");
        boolean reproduced=false;try{if(range.getValue()<48)range.addTransientModifier(new AttributeModifier(key,48-range.getValue(),AttributeModifier.Operation.ADD_NUMBER));}catch(IllegalArgumentException ex){reproduced=ex.getMessage().contains("already applied");}
        check(reproduced,"reproduced server log: old update adds same modifier twice");
        range.removeModifier(key);for(int i=0;i<100;i++)NavigationRange.update(range,key,true);
        check(range.getModifier(key)!=null&&range.getModifiers().size()==2,"repeated production updates keep exactly one navigation modifier");
        check(range.getModifier(external)!=null,"navigation updates preserve another modifier");
        NavigationRange.update(range,key,false);check(range.getModifier(key)==null&&range.getModifier(external)!=null,"idle or release removes only the command modifier");
        NavigationRange.update(range,key,false);NavigationRange.update(range,key,true);check(range.getModifier(key)!=null,"reactivation after repeated release adds modifier safely");
        range.setBaseValue(12);for(int i=0;i<100;i++)NavigationRange.update(range,key,true);check(range.getModifiers().size()==2,"base attribute changes cannot reintroduce duplicate modifier error");
        NavigationRange.update(range,key,false);range.setBaseValue(100);NavigationRange.update(range,key,true);check(range.getModifier(key)==null,"naturally sufficient range receives no unnecessary modifier");
        System.out.println("Navigation checks passed: "+checks);org.apache.logging.log4j.LogManager.shutdown();
    }
}
