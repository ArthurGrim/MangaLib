package de.mangalib;

import java.util.Map;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.*;
import org.springframework.test.context.ContextConfiguration;

/** Der Task wählt sechs offene Parameterfälle und den letzten beforeCommit-Fall. */
@ContextConfiguration(initializers=T03RemainingAcceptanceTest.Approval.class)
class T03RemainingAcceptanceTest extends T03MatrixAcceptanceTest {
    public static class Approval implements ApplicationContextInitializer<ConfigurableApplicationContext>{
        public void initialize(ConfigurableApplicationContext context){
            if(!"true".equals(System.getProperty("t03.remaining.approved")))throw new IllegalStateException("Restmatrix nicht freigegeben.");
        }
    }
    @Override void verifyBaseline(TestDatabaseSnapshot.Snapshot snapshot)throws Exception{T03MatrixHistory.verifySeventy(snapshot);}
    @Override Map<String,Integer> durableBudget(){return Map.of("mangareihe",0,"mangadetails",0,"baende",0,"einkaufsliste",1,"einkaufslistedetails",1);}
    @Override @ParameterizedTest @Order(9)
    @ValueSource(strings={"stale","details","duplicate","unknownPrice","cartDetails","edition"})
    void controlledRejectionsUseFullyRolledBackFixtures(String scenario){super.controlledRejectionsUseFullyRolledBackFixtures(scenario);}
}
