package de.mangalib.windowsadapter;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static de.mangalib.windowsadapter.KernelCalls.*;
import static org.junit.jupiter.api.Assertions.*;

class AcceptanceTimeoutTest {
    WorkerJobOwner.Result result(boolean group,boolean resources,boolean protocol,Integer exit){
        return new WorkerJobOwner.Result(WorkerJobOwner.Status.ERROR,Map.of(),group,resources,protocol,exit,
                UUID.randomUUID(),new Identity(1,2,"C:/java.exe"),4);
    }
    WorkerJobOwner.Diagnostic diagnostic(WorkerJobOwner.State state,String out,String err,int active){
        return new WorkerJobOwner.Diagnostic(state,"RESOURCE_CLOSE",List.of(),
                new Observation(true,true,active,active==0?List.of():List.of(1L),active,4),Map.of("StdoutEof",out,"StderrEof",err));
    }
    boolean accept(WorkerJobOwner.Result r,WorkerJobOwner.Diagnostic d,int starts,boolean deadline,boolean live,boolean pending,int terminates,boolean clear,int failures){
        return TimeoutAcceptance.accepted(r,d,starts,deadline,live,pending,terminates,clear,failures);
    }
    @Test void expectedTimeoutCanPassOnlyWithIndependentPositiveEndEvidence(){
        assertTrue(accept(result(true,true,false,1),diagnostic(WorkerJobOwner.State.IDLE,"true","true",0),1,true,true,true,1,true,0));
        assertFalse(result(true,true,false,1).channelEnded()); // Kein erfundener Protokollerfolg.
    }
    @ParameterizedTest @ValueSource(strings={"group","resource","protocol","exit","quarantine","stdout","stderr","active","starts","early","live","pending","terminates","journal","sampling"})
    void everyMissingEndOrDeadlineConditionStops(String fault){
        var r=result(!fault.equals("group"),!fault.equals("resource"),fault.equals("protocol"),fault.equals("exit")?0:1);
        var d=diagnostic(fault.equals("quarantine")?WorkerJobOwner.State.QUARANTINED:WorkerJobOwner.State.IDLE,
                fault.equals("stdout")?"false":"true",fault.equals("stderr")?"false":"true",fault.equals("active")?1:0);
        assertFalse(accept(r,d,fault.equals("starts")?2:1,!fault.equals("early"),!fault.equals("live"),!fault.equals("pending"),
                fault.equals("terminates")?0:1,!fault.equals("journal"),fault.equals("sampling")?1:0));
    }
    @Test void witnessRequiresTwoDistinctCurrentFirefoxInstancesAndGecko(){
        var w=new AcceptanceWitness();w.required=List.of("C:/firefox.exe","C:/geckodriver.exe");w.minimumFirefox=2;
        var f=new Identity(1,2,"C:/firefox.exe");var child=new Identity(3,4,"C:/firefox.exe");var g=new Identity(5,6,"C:/geckodriver.exe");
        assertFalse(w.ready(List.of(f,g)));assertTrue(w.ready(List.of(f,child,g)));
        assertFalse(w.ready(List.of(f,child)));assertFalse(w.ready(List.of()));
        assertFalse(w.ready(List.of(f,f,g)));
    }
}
