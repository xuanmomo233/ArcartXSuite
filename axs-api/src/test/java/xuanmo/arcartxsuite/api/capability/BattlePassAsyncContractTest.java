package xuanmo.arcartxsuite.api.capability;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class BattlePassAsyncContractTest {
    @Test void everyBusinessMutationUsesAnAsyncResultAndOnlyTheSeasonFlagIsSynchronous() {
        int mutations=0;
        for(var method:BattlePassAccess.class.getDeclaredMethods()) {
            if(method.getName().equals("isSeasonActive"))assertEquals(boolean.class,method.getReturnType());
            else {assertEquals(CompletionStage.class,method.getReturnType(),method.getName());mutations++;}
        }
        assertEquals(8,mutations);
    }
}
