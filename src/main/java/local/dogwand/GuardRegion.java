package local.dogwand;

import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/** Inclusive corner blocks, with exclusive outer faces. No world access or forced chunk loads. */
public record GuardRegion(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
    public GuardRegion {if(minX>maxX||minY>maxY||minZ>maxZ)throw new IllegalArgumentException("Inverted region");}
    public static GuardRegion between(int x,int y,int z,int a,int b,int c){return new GuardRegion(Math.min(x,a),Math.min(y,b),Math.min(z,c),Math.max(x,a),Math.max(y,b),Math.max(z,c));}
    public boolean contains(double x,double y,double z){return x>=minX&&x<maxX+1.0&&y>=minY&&y<maxY+1.0&&z>=minZ&&z<maxZ+1.0;}
    public boolean contains(Location p){return contains(p.getX(),p.getY(),p.getZ());}
    public boolean bodyFits(Location p,double width,double height){double r=width/2;return p.getX()-r>=minX&&p.getX()+r<=maxX+1.0&&p.getY()>=minY&&p.getY()+height<=maxY+1.0&&p.getZ()-r>=minZ&&p.getZ()+r<=maxZ+1.0;}
    public Vector closest(Vector p){return new Vector(Math.clamp(p.getX(),minX+.5,maxX+.5),Math.clamp(p.getY(),minY,maxY+.05),Math.clamp(p.getZ(),minZ+.5,maxZ+.5));}
    public Vector sample(RandomGenerator random){return new Vector(random.nextDouble(minX+.35,maxX+.65),random.nextDouble(minY,maxY+1.0),random.nextDouble(minZ+.35,maxZ+.65));}
    public String encode(){return minX+","+minY+","+minZ+","+maxX+","+maxY+","+maxZ;}
    public static GuardRegion decode(String value){String[] p=value.split(",");if(p.length!=6)throw new IllegalArgumentException("Region format");return new GuardRegion(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]),Integer.parseInt(p[4]),Integer.parseInt(p[5]));}
}
