package de.mangalib.windowsadapter;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static de.mangalib.windowsadapter.KernelCalls.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkerJobProofTest {
    @TempDir Path root;
    final String id="58195e20-5465-4330-871b-5df814cbf11a",artifact="a".repeat(64);
    String hash(String s){return WorkerJobProof.sha(s.getBytes(StandardCharsets.UTF_8));}
    @ParameterizedTest @ValueSource(strings={"valid","old-profile","old-approval","old-consumed","other-id","changed-root","changed-artifact"})
    void exactTupleRequiredBeforeAnyNativeLoader(String fault)throws Exception{
        String manifest=WorkerJobProof.PROFILE+"\n"+id+"\n"+artifact+"\n"+root+"\n";
        if(fault.equals("old-profile"))manifest=manifest.replace(WorkerJobProof.PROFILE,"OWNER_COMPLETION_V1");
        String approval="WORKER_JOB_APPROVED_V1\n"+id+"\n"+artifact+"\n"+hash(manifest)+"\n";
        if(fault.equals("old-approval"))approval=approval.replace("WORKER_JOB_APPROVED_V1","OWNER_COMPLETION_APPROVED_V1");
        String consumed="WORKER_JOB_CONSUMED_V1\n"+id+"\n"+artifact+"\n"+hash(approval)+"\n";
        if(fault.equals("old-consumed"))consumed=consumed.replace("WORKER_JOB_CONSUMED_V1","OWNER_COMPLETION_CONSUMED_V1");
        String m=manifest,a=approval,c=consumed;
        if(fault.equals("valid"))WorkerJobProof.tuple(m,a,c,id,artifact,root.toString());
        else assertThrows(IOException.class,()->WorkerJobProof.tuple(m,a,c,
                fault.equals("other-id")?UUID.randomUUID().toString():id,
                fault.equals("changed-artifact")?"b".repeat(64):artifact,
                fault.equals("changed-root")?root.resolve("other").toString():root.toString()));
    }
    @ParameterizedTest @ValueSource(strings={"valid","missing","error","group","resources","channel","exit","identity","image","ticket","zero-ticket","total","payload","state","journal","factories"})
    void noPassWithoutEveryIndependentJobAndResourceProof(String fault){
        var worker=new Identity(51,134378472263984761L,fault.equals("image")?"C:\\foreign.exe":WorkerJobProof.JAVA);
        var r=fault.equals("missing")?null:new WorkerJobOwner.Result(fault.equals("error")?WorkerJobOwner.Status.ERROR:WorkerJobOwner.Status.SUCCESS,
                fault.equals("payload")?Map.of("synthetic","false"):Map.of("synthetic","true"),!fault.equals("group"),!fault.equals("resources"),
                !fault.equals("channel"),fault.equals("exit")?259:0,fault.equals("ticket")?null:fault.equals("zero-ticket")?new UUID(0,0):UUID.randomUUID(),fault.equals("identity")?null:worker,fault.equals("total")?0:1);
        assertEquals(fault.equals("valid"),WorkerJobProof.accepted(r,fault.equals("state")?WorkerJobOwner.State.QUARANTINED:WorkerJobOwner.State.IDLE,
                !fault.equals("journal"),fault.equals("factories")?2:1));
    }
    @Test void multipleHistoricalMembersAreAllowedOnlyWithFullGroupAndResourceProof(){
        var worker=new Identity(51,134378472263984761L,WorkerJobProof.JAVA);
        var result=new WorkerJobOwner.Result(WorkerJobOwner.Status.SUCCESS,Map.of("synthetic","true"),true,true,true,0,UUID.randomUUID(),worker,2);
        assertTrue(WorkerJobProof.accepted(result,WorkerJobOwner.State.IDLE,true,1));
        var unclosed=new WorkerJobOwner.Result(WorkerJobOwner.Status.SUCCESS,Map.of("synthetic","true"),false,true,true,0,UUID.randomUUID(),worker,2);
        assertFalse(WorkerJobProof.accepted(unclosed,WorkerJobOwner.State.IDLE,true,1));
    }
    @Test void ownMarkerAndBoundedFilesCannotBeOverwritten()throws Exception{
        WorkerJobProof.write(root,"consumed","OWN\n");assertThrows(FileAlreadyExistsException.class,()->WorkerJobProof.write(root,"consumed","OTHER\n"));
        assertEquals("OWN\n",WorkerJobProof.read(root.resolve("consumed"),4));
        assertThrows(IOException.class,()->WorkerJobProof.read(root.resolve("consumed"),3));
        assertThrows(IOException.class,()->WorkerJobProof.write(root,"large","x".repeat(4097)));
    }
    @Test void separateNamespaceAndNoSpringOrBrowserClasspath()throws Exception{
        assertEquals(root.resolve(".gradle/t02-worker-job-proof").resolve(id),WorkerJobProof.directory(root,id));
        assertThrows(IOException.class,()->WorkerJobProof.directory(root,"00000000-0000-0000-0000-000000000000"));
        assertEquals(4,WorkerJobProof.classpath(root).split(";").length);
        assertFalse(WorkerJobProof.classpath(root).contains("main"));
        assertFalse(WorkerJobProof.classpath(root).contains("manual"));
    }
    @Test void fingerprintReadsOnlyCompiledBytesAndPinnedLocalFiles()throws Exception{
        Path workspace=Path.of("").toAbsolutePath();
        String fingerprint=WorkerJobProof.fingerprint(workspace);
        assertTrue(fingerprint.matches("[a-f0-9]{64}"));
        // Einziger Output ist ein nicht vertraulicher Hash für den unabhängigen PS-Abgleich.
        System.out.println("WORKER_JOB_PROOF_FINGERPRINT="+fingerprint);
    }
    @Test void boundedDiagnosticsRetainKernelCodeWithoutRawExceptionData(){
        assertEquals("JOB_PROOF_FAILURE_V1\nstartSuspended\nde.mangalib.windowsadapter.KernelCalls$Failure\n5\n",
                WorkerJobProof.diagnostic("startSuspended",new Failure("SECRET",5)));
        String text=WorkerJobProof.diagnostic("SECRET",new IllegalStateException("SECRET"));
        assertFalse(text.contains("SECRET"));assertTrue(text.contains("UNCLASSIFIED"));
    }
}
