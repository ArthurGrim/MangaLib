package de.mangalib.ownerprobe;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.io.IOException;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class OwnerProbeTest {
    @TempDir Path temp;
    final String id=UUID.randomUUID().toString(),artifact="a".repeat(64),birth="134376986442231462";
    String key(){return "OP1|"+id+"|"+artifact+"|51|"+birth;}
    void binding()throws IOException{OwnerProbeFiles.write(temp,"binding",key()+"\n");}
    @ParameterizedTest @ValueSource(strings={"normal","stop","stop-before-ready","both"})
    void ownBoundLifecycleClosesWithoutAnyWorkerOrNativeFactory(String mode)throws Exception{
        binding();if(mode.equals("stop-before-ready"))OwnerProbeFiles.write(temp,"stop","STOP|"+key()+"\n");
        var clock=new AtomicLong();var wrote=new boolean[1];
        int exit=OwnerProbeLifecycle.run(temp,id,artifact,51,clock::get,()->{
            clock.addAndGet(100);
            if(!wrote[0]){wrote[0]=true;try{
                if(!mode.equals("normal"))OwnerProbeFiles.write(temp,"stop","STOP|"+key()+"\n");
                if(mode.equals("normal") || mode.equals("both"))OwnerProbeFiles.write(temp,"finish","FINISH|"+key()+"\n");
            }catch(IOException e){throw new RuntimeException(e);}}
        });
        boolean normal=mode.equals("normal");assertEquals(normal?0:78,exit);
        assertEquals("CLOSED|"+key()+"|"+(normal?"NORMAL":"STOP")+"\n",OwnerProbeFiles.message(temp,"closed"));
        if(mode.equals("stop-before-ready"))assertFalse(Files.exists(temp.resolve("ready")));
    }
    @ParameterizedTest @ValueSource(strings={"id","artifact","pid","birth","partial","extra"})
    void wrongBindingCannotBecomeReady(String fault)throws Exception{
        String bad=key()+"\n";
        bad=switch(fault){case "id"->bad.replace(id,UUID.randomUUID().toString());case "artifact"->bad.replace(artifact,"b".repeat(64));
            case "pid"->bad.replace("|51|","|52|");case "birth"->bad.replace(birth,"9223372036854775808");
            case "partial"->bad.stripTrailing();default->bad+"x\n";};
        OwnerProbeFiles.write(temp,"binding",bad);var clock=new AtomicLong();
        if(fault.equals("partial"))assertEquals(78,OwnerProbeLifecycle.run(temp,id,artifact,51,clock::get,()->clock.addAndGet(1000)));
        else assertThrows(IOException.class,()->OwnerProbeLifecycle.run(temp,id,artifact,51,clock::get,()->clock.addAndGet(1000)));
        assertFalse(Files.exists(temp.resolve("ready")));assertFalse(Files.exists(temp.resolve("closed")));
    }
    @ParameterizedTest @ValueSource(strings={"unbound","bound"})
    void timeoutNeverPretendsNormalClosure(String mode)throws Exception{
        if(mode.equals("bound"))binding();var clock=new AtomicLong();
        assertEquals(78,OwnerProbeLifecycle.run(temp,id,artifact,51,clock::get,()->clock.addAndGet(1000)));
        assertFalse(Files.exists(temp.resolve("closed")));assertTrue(clock.get()<=15000);
    }
    @Test void mismatchedStopCannotClaimAValidAcknowledgment()throws Exception{
        binding();OwnerProbeFiles.write(temp,"stop","STOP|"+key().replace(birth,"134376986442231460")+"\n");
        assertThrows(IOException.class,()->OwnerProbeLifecycle.run(temp,id,artifact,51,()->0,()->fail("unexpected-wait")));
        assertFalse(Files.exists(temp.resolve("closed")));assertFalse(Files.exists(temp.resolve("ready")));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"../x","00000000-0000-0000-0000-000000000000","BA92A0E4-3857-4394-804F-B7EF1EEF46E1"})
    void attemptIdsCannotEscapeOrReuseNoncanonicalText(String value){assertThrows(IOException.class,()->OwnerProbeFiles.id(value));}
    @ParameterizedTest @ValueSource(strings={"manifest","approval","consumed","old-session","valid"})
    void manifestApprovalAndConsumptionAreExactlyBound(String fault)throws Exception{
        String dll="d".repeat(64),root="C:\\synthetic";
        String manifest="OWNER_PROBE_V1\n"+id+"\n"+artifact+"\n"+OwnerProbeFiles.JAVA_HASH+"\n"+dll+"\n"+root+"\n";
        String approval="OWNER_APPROVED_V1\n"+id+"\n"+artifact+"\n"+OwnerProbeFiles.sha(manifest.getBytes(StandardCharsets.UTF_8))+"\n";
        String consumed="OWNER_CONSUMED_V1\n"+id+"\n"+artifact+"\n"+OwnerProbeFiles.sha(approval.getBytes(StandardCharsets.UTF_8))+"\n";
        if(fault.equals("manifest"))manifest+="x";if(fault.equals("approval"))approval=approval.replace(id,UUID.randomUUID().toString());
        if(fault.equals("consumed"))consumed=consumed.stripTrailing();if(fault.equals("old-session"))consumed="SC_CONSUMED_V2\n"+consumed;
        String m=manifest,a=approval,c=consumed;
        if(fault.equals("valid"))assertDoesNotThrow(()->OwnerProbeFiles.validateTuple(m,a,c,id,artifact,dll,root));
        else assertThrows(IOException.class,()->OwnerProbeFiles.validateTuple(m,a,c,id,artifact,dll,root));
    }
    @Test void boundedMessagesRejectOverflowUnicodeAndOverwrite()throws Exception{
        OwnerProbeFiles.write(temp,"ready","READY\n");assertThrows(IOException.class,()->OwnerProbeFiles.write(temp,"ready","READY\n"));
        Files.writeString(temp.resolve("bad"),"x".repeat(513));assertThrows(IOException.class,()->OwnerProbeFiles.message(temp,"bad"));
        Files.write(temp.resolve("bad"),new byte[]{(byte)255,10});assertThrows(IOException.class,()->OwnerProbeFiles.message(temp,"bad"));
    }
    @Test void artifactFingerprintTracksByteChangesAndRejectsWorkerClasses()throws Exception{
        Path java=temp.resolve("jdk/bin/java.exe"),dll=temp.resolve("kernel32.dll"),classes=temp.resolve("classes"),scripts=temp.resolve("scripts");
        Files.createDirectories(temp.resolve("jdk/bin/server"));Files.writeString(temp.resolve("jdk/bin/jli.dll"),"launcher");
        Files.writeString(temp.resolve("jdk/bin/server/jvm.dll"),"VM");Files.writeString(temp.resolve("jdk/release"),"release");
        Files.createDirectories(classes.resolve("de/mangalib/ownerprobe"));Files.createDirectory(scripts);
        Files.write(java,new byte[]{1});Files.write(dll,new byte[]{2});for(String s:OwnerProbeFiles.SCRIPTS)Files.writeString(scripts.resolve(s),s);
        Path file=classes.resolve("de/mangalib/ownerprobe/OwnerProvenanceMain.class");
        Files.write(file,ByteBuffer.allocate(8).putInt(0xcafebabe).putShort((short)0).putShort((short)65).array());
        String before=OwnerProbeFiles.fingerprint(temp,java,dll,classes,scripts);Files.write(java,new byte[]{3});
        assertNotEquals(before,OwnerProbeFiles.fingerprint(temp,java,dll,classes,scripts));
        Files.write(classes.resolve("de/mangalib/ownerprobe/Worker.class"),new byte[]{1});
        assertThrows(IOException.class,()->OwnerProbeFiles.fingerprint(temp,java,dll,classes,scripts));
    }
    @Test void probeClasspathContainsNoJnaWorkerJobOrSpring()throws Exception{
        for(String type:List.of("com.sun.jna.Native","de.mangalib.windowsadapter.WorkerJobOwner",
                "de.mangalib.windowsadapter.Win64KernelCalls","org.springframework.boot.SpringApplication"))
            assertThrows(ClassNotFoundException.class,()->Class.forName(type));
        assertNotNull(Class.forName("de.mangalib.ownerprobe.OwnerProvenanceMain"));
    }
}
