package de.mangalib.windowsadapter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Nur Text-/Pfad-/Endberichtverträge, kein Manifest.read/main/JNA oder Prozess. */
class ScAttemptPreparationTest {
    static final UUID ID=UUID.fromString("12345678-1234-4234-8234-123456789abc");
    static final String HASH="a".repeat(64),APPROVAL_HASH="b".repeat(64);
    @Test void freshAttemptIsDisjointFromHistoricalGlobalApprovalAndSessionDirectory() throws Exception {
        Path root=Path.of("C:\\synthetic");
        Path directory=ScAttemptFiles.directory(root,ID.toString());
        assertEquals(root.resolve(".gradle/t02-sc-attempts/"+ID),directory);
        assertNotEquals(root.resolve(".gradle/t02-sc-native-approval.txt"),directory.resolve("approval.txt"));
        assertNotEquals(root.resolve(".gradle/t02-sc-native-"+ID),directory);
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"","..","../approval.txt","00000000-0000-0000-0000-000000000000","12345678-1234-4234-8234-123456789ABC","12345678-1234-4234-8234-123456789abc/../old"})
    void malformedOrMissingAttemptCannotSelectAnApproval(String id) {
        assertThrows(IOException.class,()->ScAttemptFiles.directory(Path.of("synthetic"),id));
    }
    @Test void outerConsumedRecordHasExactCrossLanguageBindingAndNeedsBothHashes() throws Exception {
        String expected="SC_CONSUMED_V2\n"+ID+"\n"+HASH+"\n"+APPROVAL_HASH+"\n";
        assertEquals(expected,ScAttemptFiles.marker(ID,HASH,APPROVAL_HASH));
        ScAttemptFiles.verify(ID+"\n"+HASH+"\n",expected,ID,HASH,APPROVAL_HASH);
    }
    @ParameterizedTest @ValueSource(strings={"hash","approval-hash","session","old-marker","extra","empty"})
    void wrongOrHistoricalConsumptionCannotAuthorizeNativeBootstrap(String fault) throws Exception {
        String marker=ScAttemptFiles.marker(ID,HASH,APPROVAL_HASH);
        switch(fault) {
            case "hash" -> marker=marker.replace(HASH,"c".repeat(64));
            case "approval-hash" -> marker=marker.replace(APPROVAL_HASH,"c".repeat(64));
            case "session" -> marker=marker.replace(ID.toString(),UUID.randomUUID().toString());
            case "old-marker" -> marker=HASH;
            case "extra" -> marker+="extra";
            default -> marker="";
        }
        String bad=marker;
        assertThrows(IOException.class,()->ScAttemptFiles.verify(ID+"\n"+HASH,bad,ID,HASH,APPROVAL_HASH));
    }
    @ParameterizedTest @ValueSource(strings={"","A","aabb","z"})
    void missingOrMalformedIntegrityHashIsRejected(String hash) {
        assertThrows(IOException.class,()->ScAttemptFiles.marker(ID,hash,APPROVAL_HASH));
        assertThrows(IOException.class,()->ScAttemptFiles.marker(ID,HASH,hash));
    }
    @Test void noBootstrapEvidenceNeverInventsCustodianOrJobCompletion() {
        String report=ScRunEvidence.format(ID,false,null,null,null,Integer.MIN_VALUE);
        assertTrue(report.contains("complete=false\ngroupEnded=false"));
        assertTrue(report.contains("operationOutstanding=true"));
        assertTrue(report.contains("cExit=unknown\ncPid=unknown\ncBirth=unknown"));
        assertTrue(report.length()<1024);
    }
    @Test void knownChildEndDoesNotImplyHandlesOrGroupEnded() {
        var snapshot=new ScStartProvider.Snapshot(true,false,true,true,true,false,true);
        String report=ScRunEvidence.format(ID,true,null,snapshot,new KernelCalls.Identity(12,123,"synthetic"),0);
        assertTrue(report.contains("complete=false"));assertTrue(report.contains("cEnd=true\ncHandlesClosed=false"));
        assertTrue(report.contains("cExit=0\ncPid=12\ncBirth=123"));assertFalse(report.contains("synthetic"));
    }
    @Test void outstandingOperationPreventsCompleteEvenWhenOtherEndFlagsAreTrue() {
        var result=new EmptyCustodianBootstrap.Result(EmptyCustodianBootstrap.Phase.FINISHED,false,
                EmptyCustodianBootstrap.Ownership.CLOSED,true,true,true,true,true,false,100,true);
        String report=ScRunEvidence.format(ID,true,result,null,null,Integer.MIN_VALUE);
        assertTrue(report.contains("complete=false"));assertTrue(report.contains("operationOutstanding=true"));
    }
}
