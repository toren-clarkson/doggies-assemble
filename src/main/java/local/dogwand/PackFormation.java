package local.dogwand;

import java.util.*;
import org.bukkit.util.Vector;

/** Compact stay destinations and attack approaches for every allocated dog. */
public final class PackFormation {
    private PackFormation(){}
    public static List<Vector> stay(int count){
        if(count<=0)return List.of();int side=(int)Math.ceil(Math.sqrt(count));List<Vector> cells=new ArrayList<>();
        for(int x=-side;x<=side;x++)for(int z=-side;z<=side;z++)cells.add(new Vector(x*1.4,0,z*1.4));
        cells.sort(Comparator.comparingDouble(Vector::lengthSquared).thenComparingDouble(Vector::getX).thenComparingDouble(Vector::getZ));return List.copyOf(cells.subList(0,count));
    }
    public static double radius(int count){return stay(Math.max(1,count)).stream().mapToDouble(Vector::length).max().orElse(0)+.55;}
    public static Vector combat(int slot){
        double angle=slot*2.399963229728653,radius=1.0+.15*Math.floorMod(slot,3);return new Vector(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
    }
}
