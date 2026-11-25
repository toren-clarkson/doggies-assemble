package local.dogwand;

import java.util.EnumSet;
import com.destroystokyo.paper.entity.ai.GoalType;

public final class CommandGoalTypes {
    private CommandGoalTypes() {}
    // Paper routes ANY goal containing TARGET into targetSelector, even with MOVE present.
    public static EnumSet<GoalType> movement(){return EnumSet.of(GoalType.MOVE,GoalType.LOOK,GoalType.JUMP);}
    public static EnumSet<GoalType> targeting(){return EnumSet.of(GoalType.TARGET);}
}
