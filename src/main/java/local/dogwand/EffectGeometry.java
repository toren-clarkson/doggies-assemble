package local.dogwand;
import java.util.*;
import org.bukkit.util.Vector;
public final class EffectGeometry {
    private EffectGeometry(){}
    public static List<Vector> beam(Vector start,Vector end){
        int n=Math.max(1,Math.min(160,(int)Math.ceil(start.distance(end)*2)));List<Vector> points=new ArrayList<>();
        Vector delta=end.clone().subtract(start);for(int i=0;i<=n;i++)points.add(start.clone().add(delta.clone().multiply((double)i/n)));return points;
    }
    public static List<Vector> box(GuardRegion r){return box(r,new Vector((r.minX()+r.maxX()+1.0)/2,(r.minY()+r.maxY()+1.0)/2,(r.minZ()+r.maxZ()+1.0)/2),96);}
    /** One point per block on visible edges. Clip distant edges, never dilute spacing. */
    public static List<Vector> box(GuardRegion r,Vector viewer,double range){
        Set<Vector> out=new LinkedHashSet<>();double[] lo={r.minX(),r.minY(),r.minZ()},hi={r.maxX()+1.0,r.maxY()+1.0,r.maxZ()+1.0},view={viewer.getX(),viewer.getY(),viewer.getZ()};
        for(int axis=0;axis<3;axis++)for(int a=0;a<2;a++)for(int b=0;b<2;b++){
            int j=(axis+1)%3,k=(axis+2)%3;double u=a==0?lo[j]:hi[j],v=b==0?lo[k]:hi[k];
            if(Math.abs(u-view[j])>range||Math.abs(v-view[k])>range)continue;
            double start=Math.max(lo[axis],Math.ceil(view[axis]-range)),end=Math.min(hi[axis],Math.floor(view[axis]+range));
            for(double pos=start;pos<=end;pos++){double[] p=lo.clone();p[axis]=pos;p[j]=u;p[k]=v;var point=new Vector(p[0],p[1],p[2]);if(point.distanceSquared(viewer)<=range*range)out.add(point);}
        }return List.copyOf(out);
    }
}
