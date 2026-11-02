package de.mangalib.windowsadapter;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
import java.util.Comparator;

/** Nur eigenes synthetisches Tempverzeichnis; keine rekursive oder fremde Bereinigung. */
final class WorkerTemporaryDirectory {
    private final Path root;
    private final Object key;
    private final FileTime created;
    WorkerTemporaryDirectory(Path root)throws IOException{
        this.root=root;checkPath(root);var attributes=Files.readAttributes(root,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        key=attributes.fileKey();created=attributes.creationTime();
    }
    private static void checkPath(Path path)throws IOException{
        if(!path.isAbsolute() || Files.isSymbolicLink(path) || !path.toRealPath().equals(path)
                || !Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("worker-temp-path");
    }
    boolean close(boolean groupEnded){
        if(!groupEnded)return false;
        try{
            checkPath(root);
            var attributes=Files.readAttributes(root,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            // Windows-JDK liefert hier keinen fileKey. Exakte Dateierstellungszeit
            // zusätzlich zum ursprünglich selbst erzeugten kanonischen Privatpfad
            // vergleichen; dies ist ausdrücklich kein nativer Prozessidentitätsbeleg.
            if(!created.equals(attributes.creationTime()) || (key!=null && !Objects.equals(key,attributes.fileKey())))return false;
            String user=System.getProperty("user.name");
            if(user==null || !user.matches("[A-Za-z0-9._-]{1,128}"))return false;
            Path perf=root.resolve("hsperfdata_"+user);
            if(Files.exists(perf,LinkOption.NOFOLLOW_LINKS)){
                checkPath(perf);
                // JVM kann das leere Perfdata-Verzeichnis nach regulärem Ende hinterlassen.
                // Ein noch vorhandener/gesperrter PID-Eintrag wird niemals gelöscht.
                Files.delete(perf);
            }
            Files.delete(root);return true;
        }catch(IOException | RuntimeException error){return false;}
    }
    /** Nur produktiver, selbst erzeugter Workerbaum NACH nativ bestätigtem Jobende.
     * Vor jeder Löschung zuerst gesamten Baum prüfen; Links/Junctions nie verfolgen. */
    boolean closeOwnedTree(boolean groupEnded,long remaining){
        if(!groupEnded || remaining<=0)return false;
        long started=System.nanoTime();
        try{
            checkPath(root);
            var current=Files.readAttributes(root,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!created.equals(current.creationTime()) || (key!=null && !Objects.equals(key,current.fileKey())))return false;
            java.util.List<Path> paths;
            try(var walk=Files.walk(root)){paths=walk.limit(10_001).toList();}
            if(paths.size()>10_000)return false;
            long bytes=0;
            for(Path path:paths){
                if(System.nanoTime()-started>=remaining || Files.isSymbolicLink(path) || !path.toRealPath().equals(path)
                        || !path.startsWith(root))return false;
                var attributes=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(!attributes.isDirectory() && !attributes.isRegularFile())return false;
                bytes+=attributes.size();if(bytes>256L*1024*1024)return false;
            }
            for(Path path:paths.stream().sorted(Comparator.reverseOrder()).toList()){
                if(System.nanoTime()-started>=remaining || Files.isSymbolicLink(path) || !path.toRealPath().equals(path))return false;
                Files.delete(path);
            }
            return true;
        }catch(IOException | RuntimeException failure){return false;}
    }
}
