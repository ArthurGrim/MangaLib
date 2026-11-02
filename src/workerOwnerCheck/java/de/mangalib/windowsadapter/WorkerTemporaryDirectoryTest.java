package de.mangalib.windowsadapter;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkerTemporaryDirectoryTest {
    @TempDir Path parent;
    Path create()throws Exception{return Files.createDirectory(parent.resolve("synthetic-worker"));}
    Path perf(Path root){return root.resolve("hsperfdata_"+System.getProperty("user.name"));}
    @Test void emptyOwnDirectoryClosesOnlyAfterConfirmedGroupEnd()throws Exception{
        Path root=create();var token=new WorkerTemporaryDirectory(root);
        assertFalse(token.close(false));assertTrue(Files.exists(root));assertTrue(token.close(true));assertFalse(Files.exists(root));
    }
    @Test void leftoverEmptyPerfDirectoryReproducesOldFlatDeleteFailure()throws Exception{
        Path root=create();var token=new WorkerTemporaryDirectory(root);Files.createDirectory(perf(root));
        assertThrows(DirectoryNotEmptyException.class,()->Files.delete(root));
        assertTrue(token.close(true));assertFalse(Files.exists(root));
    }
    @Test void existingPerfEntryIsPreservedEvenIfGroupEndWasReported()throws Exception{
        Path root=create();var token=new WorkerTemporaryDirectory(root);Files.createDirectory(perf(root));
        Path entry=Files.writeString(perf(root).resolve("51"),"SYNTHETIC");
        assertFalse(token.close(true));assertEquals("SYNTHETIC",Files.readString(entry));
    }
    @Test void unrelatedFilesAreNeverDeleted()throws Exception{
        Path root=create();var token=new WorkerTemporaryDirectory(root);Path file=Files.writeString(root.resolve("unknown"),"SYNTHETIC");
        assertFalse(token.close(true));assertEquals("SYNTHETIC",Files.readString(file));
    }
    @Test void relativePathIsNotAnOwnershipToken(){assertThrows(Exception.class,()->new WorkerTemporaryDirectory(Path.of("relative")));}
    @Test void productiveProfileTreeIsDeletedOnlyAfterConfirmedEnd()throws Exception{
        Path root=create();var token=new WorkerTemporaryDirectory(root);
        Path profile=Files.createDirectory(root.resolve("profile"));Files.writeString(profile.resolve("synthetic.sqlite"),"SYNTHETIC");
        Path unrelated=Files.writeString(parent.resolve("unrelated"),"PRESERVE");
        assertFalse(token.closeOwnedTree(false,1_000_000_000));assertTrue(Files.exists(profile));
        assertTrue(token.closeOwnedTree(true,1_000_000_000));assertFalse(Files.exists(root));
        assertEquals("PRESERVE",Files.readString(unrelated));
    }
    @Test void expiredCleanupLeavesProfileUntouched()throws Exception{
        Path root=create();var token=new WorkerTemporaryDirectory(root);Path file=Files.writeString(root.resolve("test"),"PRESERVE");
        assertFalse(token.closeOwnedTree(true,0));assertEquals("PRESERVE",Files.readString(file));
    }
}
