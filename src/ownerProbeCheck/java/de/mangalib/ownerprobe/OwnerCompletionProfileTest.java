package de.mangalib.ownerprobe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OwnerCompletionProfileTest {
    @ParameterizedTest @ValueSource(strings={"valid","old-manifest","old-approval","old-consumed","ticket","root","profile"})
    void completionPermissionCannotBeConfusedWithGlobalProvenance(String fault)throws Exception{
        String id=UUID.randomUUID().toString(),hash="a".repeat(64),dll="d".repeat(64),root="C:\\synthetic";
        String m="OWNER_COMPLETION_V1\n"+id+"\n"+hash+"\n"+OwnerProbeFiles.JAVA_HASH+"\n"+dll+"\n"+root+"\n";
        String a="OWNER_COMPLETION_APPROVED_V1\n"+id+"\n"+hash+"\n"+sha(m)+"\n";
        String c="OWNER_COMPLETION_CONSUMED_V1\n"+id+"\n"+hash+"\n"+sha(a)+"\n";
        if(fault.equals("old-manifest"))m=m.replace("OWNER_COMPLETION_V1","OWNER_PROBE_V1");
        if(fault.equals("old-approval"))a=a.replace("OWNER_COMPLETION_APPROVED_V1","OWNER_APPROVED_V1");
        if(fault.equals("old-consumed"))c=c.replace("OWNER_COMPLETION_CONSUMED_V1","OWNER_CONSUMED_V1");
        if(fault.equals("ticket"))c=c.replace(id,UUID.randomUUID().toString());
        String manifest=m,approval=a,consumed=c;
        if(fault.equals("valid"))assertDoesNotThrow(()->OwnerProbeFiles.validateTuple(manifest,approval,consumed,id,hash,dll,root,"OWNER_COMPLETION_V1"));
        else assertThrows(IOException.class,()->OwnerProbeFiles.validateTuple(manifest,approval,consumed,id,hash,dll,
                fault.equals("root")?"C:\\foreign":root,fault.equals("profile")?"UNKNOWN":"OWNER_COMPLETION_V1"));
    }
    @Test void namespacesAreDistinctAndUnknownScopesAreRejected()throws Exception{
        Path root=Path.of("C:\\synthetic");String id=UUID.randomUUID().toString();
        assertNotEquals(OwnerProbeFiles.directory(root,id),OwnerProbeFiles.directory(root,id,"OWNER_COMPLETION_V1"));
        assertThrows(IOException.class,()->OwnerProbeFiles.directory(root,id,"UNKNOWN"));
        assertNotNull(Class.forName("de.mangalib.ownerprobe.OwnerCompletionMain")); // Kein main-Aufruf.
    }
    String sha(String s){return OwnerProbeFiles.sha(s.getBytes(StandardCharsets.UTF_8));}
}
