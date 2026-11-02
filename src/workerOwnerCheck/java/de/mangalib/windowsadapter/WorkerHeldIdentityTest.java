package de.mangalib.windowsadapter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static de.mangalib.windowsadapter.KernelCalls.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reine Entscheidungslogik, kein JNA, Handleerwerb, Prozess oder Win32-Aufruf. */
class WorkerHeldIdentityTest {
    @ParameterizedTest @ValueSource(strings={"valid","first-missing","pid","birth","zero-pid","zero-birth","alive","exit-unknown","image-null","image-blank"})
    void cachedImageRequiresExactOriginallyMeasuredEndedInstance(String fault){
        long pid=fault.equals("zero-pid")?0:51,birth=fault.equals("zero-birth")?0:134378485914679271L;
        Identity first=fault.equals("first-missing")?null:new Identity(fault.equals("pid")?52:pid,
                fault.equals("birth")?birth+1:birth,fault.equals("image-null")?null:fault.equals("image-blank")?" ":"C:\\Trusted\\java.exe");
        if(fault.equals("valid"))assertSame(first,KernelCalls.retainEndedImage(first,pid,birth,true,true));
        else assertThrows(Failure.class,()->KernelCalls.retainEndedImage(first,pid,birth,!fault.equals("alive"),!fault.equals("exit-unknown")));
    }
}
