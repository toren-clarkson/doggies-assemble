package local.dogwand;

import java.util.*;
import org.bukkit.util.Vector;

/** Bounded packet counts, independent of targeting distance and region volume. */
public final class EffectGeometry {
    private EffectGeometry(){}
    public static List<Vector> beam(Vector start,Vector end){
        int n=Math.max(1,Math.min(160,(int)Math.ceil(start.distance(end)*2)));List<Vector> points=new ArrayList<>();
        Vector delta=end.clone().subtract(start);for(int i=0;i<=n;i++)points.add(start.clone().add(delta.clone().multiply((double)i/n)));return points;
    }
    public static List<Vector> box(GuardRegion r){
        List<Vector> out=new ArrayList<>();double[] lo={r.minX(),r.minY(),r.minZ()},hi={r.maxX()+1.0,r.maxY()+1.0,r.maxZ()+1.0};
        for(int axis=0;axis<3;axis++)for(int a=0;a<2;a++)for(int b=0;b<2;b++){
            int n=Math.max(1,Math.min(19,(int)Math.ceil(hi[axis]-lo[axis])));
            for(int i=0;i<=n;i++){double[] p=lo.clone();p[axis]=lo[axis]+(hi[axis]-lo[axis])*i/n;p[(axis+1)%3]=a==0?lo[(axis+1)%3]:hi[(axis+1)%3];p[(axis+2)%3]=b==0?lo[(axis+2)%3]:hi[(axis+2)%3];out.add(new Vector(p[0],p[1],p[2]));}
        }return out;
    }
}
