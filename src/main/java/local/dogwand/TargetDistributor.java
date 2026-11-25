package local.dogwand;

import java.util.*;
import java.util.function.*;

/** Assign each dog to a least-occupied eligible threat, then minimize travel. */
public final class TargetDistributor {
    private TargetDistributor(){}
    public static <D,T> Map<D,T> split(List<D> dogs,List<T> targets,ToDoubleBiFunction<D,T> distance){
        Map<D,T> result=new LinkedHashMap<>();Map<T,Integer> counts=new HashMap<>();
        for(D dog:dogs){T best=targets.stream().min(Comparator.<T>comparingInt(t->counts.getOrDefault(t,0)).thenComparingDouble(t->distance.applyAsDouble(dog,t))).orElse(null);
            if(best!=null){result.put(dog,best);counts.merge(best,1,Integer::sum);}}
        return result;
    }
}
