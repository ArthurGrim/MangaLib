package de.mangalib;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fest gebundene Auswertung des Teilversuchs; keine automatische Übernahme
 * anderer Läufe. STAGE_CONFIRMED ist ein Fallabschluss, kein TX-Commitmarker. */
final class T03MatrixHistory {
    static final String RUN="6db42e71-8fc3-4a96-b2d5-0e719c43f8ab";
    static final String JOURNAL_SHA="50cd7c13f7acc9911fe4dcf474711ed5406c7c68bc63bf9c5d23f21d1d843efe";
    static final String REPORT_SHA="52b4255157894d8bc56d376e73e81c1f6d19671c90899d70291eecd6e663bef7";
    static final String TWELVE_SHA="b223c47368c2e83f4a11de2199c6f8dd31e63434a46a587762fca3e54f6ddb4a";
    // Reihen, Details, Bände, Einkäufe, Einkaufsdetails je ursprünglicher Fallphase.
    static final List<String> TABLES=List.of("mangareihe","mangadetails","baende","einkaufsliste","einkaufslistedetails");
    private static final int[][] COUNTS={{1,1,6,1,1},{3,3,9,1,1},{1,1,5,2,2},{1,1,2,2,2},
            {1,1,4,1,1},{1,1,3,0,0},{1,1,3,0,0},{1,1,4,0,0},{2,2,6,1,1},{1,1,3,0,0}};
    record History(Map<String,Set<Long>> committed,Map<String,Set<Long>> rolledBack,
                   List<Map<String,Set<Long>>> committedGroups) { }
    static History local()throws Exception{
        return local(Path.of(".gradle/t03-integration",RUN));
    }
    static History local(Path root)throws Exception{
        assertEquals(REPORT_SHA,sha(Files.readAllBytes(root.resolve("matrix-report/test-results/TEST-de.mangalib.T03MatrixAcceptanceTest.xml"))),"Historischer Bericht verändert: STOP");
        return parse(Files.readAllBytes(root.resolve("ids.txt")),RUN,JOURNAL_SHA);
    }
    static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static History parse(byte[] bytes,String run,String expectedHash)throws Exception{
        assertEquals(expectedHash,sha(bytes),"Historisches Matrixjournal verändert: STOP");
        var stages=new ArrayList<Map<String,List<Long>>>();
        for(int i=0;i<10;i++){var stage=new TreeMap<String,List<Long>>();TABLES.forEach(t->stage.put(t,new ArrayList<>()));stages.add(stage);}
        var all=new TreeMap<String,Set<Long>>();TABLES.forEach(t->all.put(t,new TreeSet<>()));
        int phase=0,confirmed=0,stop=0;
        for(String line:new String(bytes,StandardCharsets.UTF_8).lines().toList()){
            var fields=line.split(" ",-1);assertTrue(fields.length>=2&&fields[0].equals(run),"Fremde Laufkennung: STOP");
            if(fields.length==2&&fields[1].equals("PREPARED")){assertEquals(0,phase);phase=1;}
            else if(fields.length==2&&fields[1].equals("STAGE_CONFIRMED")){assertTrue(phase>=1&&phase<=9&&stop==0);confirmed++;phase++;}
            else if(fields.length==2&&fields[1].equals("STOP")){assertEquals(10,phase);stop++;}
            else{
                assertTrue(fields.length==4&&fields[1].equals("ID")&&phase>=1&&phase<=10&&stop==0
                        &&TABLES.contains(fields[2])&&fields[3].matches("[1-9][0-9]*"),"Unbekannte Journalphase: STOP");
                long id=Long.parseLong(fields[3]);assertTrue(all.get(fields[2]).add(id),"Doppelte Versuch-ID: STOP");
                stages.get(phase-1).get(fields[2]).add(id);
            }
        }
        assertEquals(9,confirmed);assertEquals(9,stop);assertEquals(10,phase);
        for(int stage=0;stage<10;stage++)for(int table=0;table<TABLES.size();table++)
            assertEquals(COUNTS[stage][table],stages.get(stage).get(TABLES.get(table)).size(),"Phasenbudget verändert: STOP");
        var groups=new ArrayList<Map<String,Set<Long>>>();
        for(int stage=0;stage<9;stage++){
            var group=new TreeMap<String,Set<Long>>();
            for(String table:TABLES){
                var ids=stages.get(stage).get(table);var kept=new TreeSet<Long>();
                if(stage==0&&table.equals("baende")){kept.addAll(ids.subList(0,3));kept.add(ids.getLast());}
                else if(stage==1&&(table.equals("mangareihe")||table.equals("mangadetails")))kept.add(ids.getLast());
                else if(stage==1&&table.equals("baende"))kept.addAll(ids.subList(6,9));
                else if(stage<8)kept.addAll(ids);
                group.put(table,Set.copyOf(kept));
            }
            groups.add(Collections.unmodifiableMap(group));
        }
        var committed=new TreeMap<String,Set<Long>>();var rolled=new TreeMap<String,Set<Long>>();
        for(String table:TABLES){var kept=new TreeSet<Long>();groups.forEach(g->kept.addAll(g.get(table)));
            var undone=new TreeSet<>(all.get(table));undone.removeAll(kept);
            committed.put(table,Set.copyOf(kept));rolled.put(table,Set.copyOf(undone));}
        assertEquals(List.of(8,8,28,7,7),TABLES.stream().map(t->committed.get(t).size()).toList());
        assertEquals(29,rolled.values().stream().mapToInt(Set::size).sum());
        return new History(Collections.unmodifiableMap(committed),Collections.unmodifiableMap(rolled),List.copyOf(groups));
    }
    static void verifySeventy(TestDatabaseSnapshot.Snapshot snapshot,Map<String,Set<Long>> prior,History history,
            String expectedTwelveHash)throws Exception{
        var original=T03Preservation.retain(snapshot,prior);
        assertEquals(expectedTwelveHash,original.fingerprint(),"Feldwerte des zwölfzeiligen Vorbestands verändert: STOP");
        for(String table:TABLES){
            var expected=new TreeSet<>(prior.getOrDefault(table,Set.of()));
            for(long id:history.committed().get(table))assertTrue(expected.add(id),"Historische Gruppen überlappen: STOP");
            assertEquals(expected,T03Preservation.ids(snapshot,table),"Ungeklärte Geschäftszeilen: STOP");
            assertTrue(Collections.disjoint(expected,history.rolledBack().get(table)),"Rollback-ID vorhanden: STOP");
        }
    }
    static void verifySeventy(TestDatabaseSnapshot.Snapshot snapshot)throws Exception{
        var prior=T03ContinuationPolicy.priorIds();
        var original=T03Preservation.retain(snapshot,prior);
        T03ContinuationPolicy.verify(original,prior); // Unveränderte historische Schema-/Stammdatenpolicy.
        verifySeventy(snapshot,prior,local(),TWELVE_SHA);
        var rolled=T03ContinuationPolicy.priorRolledBackIds();
        for(String table:TABLES)assertTrue(Collections.disjoint(T03Preservation.ids(snapshot,table),rolled.getOrDefault(table,Set.of())),"Ursprüngliche Rollback-ID vorhanden: STOP");
    }
}
