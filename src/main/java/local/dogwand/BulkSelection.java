package local.dogwand;

/** One pending selection, confirmed only by releasing sneak. */
public final class BulkSelection {
    private BulkSelection(){}
    public enum Scope { FREE, ALL, UNBIND, CANCEL }
    public record Choice(Scope scope,int percent){public String label(){return switch(scope){case FREE->percent+"% of free dogs";case ALL->percent+"% of all your tamed dogs";case UNBIND->"UNBIND ALL from this wand";case CANCEL->"CANCEL";};}}
    public static Choice choice(int clicks){if(clicks>=1&&clicks<=5)return new Choice(Scope.FREE,120-clicks*20);if(clicks>=6&&clicks<=10)return new Choice(Scope.ALL,220-clicks*20);return new Choice(clicks==11?Scope.UNBIND:Scope.CANCEL,0);}
    public static int percent(int clicks){return choice(clicks).percent();}
    public static int count(int free,int percent){return percent<=0?0:(int)Math.ceil(free*Math.min(percent,100)/100.0);}
}
