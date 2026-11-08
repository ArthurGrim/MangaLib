package de.mangalib;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetische Journale und Snapshots, keine Abhängigkeit zu privaten lokalen Evidenzen. */
class T03MatrixHistoryTest {
    @Test void optInContinuationPinsMethodsAndReadOnlySourceHasNoFixturePath()throws Exception{
        var build=java.nio.file.Files.readString(java.nio.file.Path.of("build.gradle.kts"));
        assertTrue(build.contains("include(\"**/T03RemainingAcceptanceTest.class\")"));
        assertTrue(build.contains("filter.includePatterns == remainingMethods"));
        assertTrue(build.contains("SEVEN_OPEN_CASES_TWO_DURABLE_ROWS"));
        assertFalse(build.contains("EIGHT_OPEN_CASES_TWO_DURABLE_ROWS"));
        assertTrue(build.contains("11a8a5f8-82a4-4655-98eb-c15e8edfa224"));
        var source=java.nio.file.Files.readString(java.nio.file.Path.of("src/t03Integration/java/de/mangalib/T03SeventyReadOnlyTest.java"));
        for(String forbidden:List.of("em.persist(","em.merge(","em.remove(","em.flush(","coordinator.execute(","new T03RunJournal("))assertFalse(source.contains(forbidden));
        assertTrue(source.contains("tx.setReadOnly(true)"));assertTrue(source.contains("T03MatrixHistory.verifySeventy(before)"));
    }
    static final String RUN="11111111-1111-4111-8111-111111111111";
    @Test void reconstructsNineGroupsAndSeparatesAllRollbackIds()throws Exception{
        var history=parse(journal());
        assertEquals(9,history.committedGroups().size());
        assertEquals(List.of(8,7,11,8,8,5,5,6,0),history.committedGroups().stream().map(g->g.values().stream().mapToInt(Set::size).sum()).toList());
        assertEquals(58,history.committed().values().stream().mapToInt(Set::size).sum());
        assertEquals(29,history.rolledBack().values().stream().mapToInt(Set::size).sum());
        for(String table:T03MatrixHistory.TABLES)assertTrue(Collections.disjoint(history.committed().get(table),history.rolledBack().get(table)));
        assertThrows(UnsupportedOperationException.class,()->history.committed().get("baende").clear());
    }
    @Test void pinsBytesAndRunIdentity()throws Exception{
        byte[] bytes=journal().getBytes(StandardCharsets.UTF_8);
        assertThrows(AssertionError.class,()->T03MatrixHistory.parse(bytes,RUN,"0".repeat(64)));
        assertThrows(AssertionError.class,()->T03MatrixHistory.parse(bytes,UUID.randomUUID().toString(),T03MatrixHistory.sha(bytes)));
    }
    @ParameterizedTest @ValueSource(strings={"missingStop","extraStop","duplicateId","missingConfirm","extraId","wrongTable","wrongBudget"})
    void rejectsChangedPhasesEvenWithASyntheticMatchingHash(String fault){
        String text=journal();text=switch(fault){
            case "missingStop"->text.substring(0,text.lastIndexOf(RUN+" STOP"));
            case "extraStop"->text+RUN+" STOP\n";
            case "duplicateId"->text.replace("ID baende 2\n","ID baende 1\n");
            case "missingConfirm"->text.replaceFirst("STAGE_CONFIRMED","STOP");
            case "extraId"->text+RUN+" ID baende 999\n";
            case "wrongTable"->text.replaceFirst("ID baende","ID other");
            default->text.replaceFirst(RUN+" ID baende 1\n","");
        };String invalid=text;assertThrows(AssertionError.class,()->parse(invalid));
    }
    @Test void protectsTwelveFieldValuesAndSeparatesUnknownCurrentFiftyEight()throws Exception{
        var history=parse(journal());var prior=prior();var old=snapshot(prior);
        var merged=merge(prior,history.committed());var current=snapshot(merged);String oldHash=old.fingerprint();
        T03MatrixHistory.verifySeventy(current,prior,history,oldHash);
        assertNotEquals(oldHash,current.fingerprint());String unchanged=current.fingerprint();
        assertEquals(oldHash,T03Preservation.retain(current,prior).fingerprint());assertEquals(unchanged,current.fingerprint());
        var changedOld=change(current,"baende",prior.get("baende").iterator().next());
        assertThrows(AssertionError.class,()->T03MatrixHistory.verifySeventy(changedOld,prior,history,oldHash));
        // Für neue 58 existiert noch kein historischer Feldwerthash. Nicht erfinden.
        var changedNew=change(current,"baende",history.committed().get("baende").iterator().next());
        T03MatrixHistory.verifySeventy(changedNew,prior,history,oldHash);
        assertNotEquals(current.fingerprint(),changedNew.fingerprint());
    }
    @ParameterizedTest @ValueSource(strings={"foreign","rolledBack","missing","schema","master"})
    void refusesUnattributedRowsAndReferenceChanges(String fault)throws Exception{
        var history=parse(journal());var prior=prior();var old=snapshot(prior);var current=snapshot(merge(prior,history.committed()));
        var values=new TreeMap<>(current.values());
        if(fault.equals("schema"))values.put("schema.tables",List.of(List.of("changed")));
        else if(fault.equals("master"))values.put("status",List.of(List.of(99L)));
        else{
            var rows=new ArrayList<>(values.get("baende"));
            if(fault.equals("missing"))rows.removeFirst();
            else rows.add(List.of(fault.equals("rolledBack")?history.rolledBack().get("baende").iterator().next():999999L,"synthetic"));
            values.put("baende",rows);
        }
        assertThrows(AssertionError.class,()->T03MatrixHistory.verifySeventy(new TestDatabaseSnapshot.Snapshot(values),prior,history,old.fingerprint()));
    }
    @Test void cannotClaimPriorRowsAsNewAndCannotProjectMissingRows()throws Exception{
        var history=parse(journal());var prior=prior();var current=snapshot(merge(prior,history.committed()));
        assertThrows(AssertionError.class,()->T03Preservation.verify(current,current,history.committed()));
        assertThrows(AssertionError.class,()->T03Preservation.retain(current,Map.of("baende",Set.of(999999L))));
    }
    private static TestDatabaseSnapshot.Snapshot change(TestDatabaseSnapshot.Snapshot current,String table,long id){
        var data=new TreeMap<>(current.values());data.put(table,current.values().get(table).stream()
                .map(row->Objects.equals(row.getFirst(),id)?List.<Object>of(id,"changed"):row).toList());return new TestDatabaseSnapshot.Snapshot(data);
    }
    private static Map<String,Set<Long>> prior(){return Map.of("mangareihe",Set.of(100001L,100002L),"mangadetails",Set.of(100001L,100002L),
            "baende",Set.of(100001L,100002L,100003L,100004L,100005L,100006L,100007L),"einkaufsliste",Set.of(100001L));}
    private static Map<String,Set<Long>> merge(Map<String,Set<Long>> a,Map<String,Set<Long>> b){var data=new TreeMap<String,Set<Long>>();
        for(String table:T03MatrixHistory.TABLES){var ids=new TreeSet<>(a.getOrDefault(table,Set.of()));ids.addAll(b.getOrDefault(table,Set.of()));data.put(table,ids);}return data;}
    private static TestDatabaseSnapshot.Snapshot snapshot(Map<String,Set<Long>> ids){
        var data=new TreeMap<String,List<List<Object>>>();for(String table:T03MatrixHistory.TABLES)
            data.put(table,ids.getOrDefault(table,Set.of()).stream().sorted().map(id->List.<Object>of(id,"synthetic")).toList());
        data.put("schema.columns",T03MatrixHistory.TABLES.stream().map(t->List.<Object>of(t,"id",1)).toList());
        data.put("schema.tables",List.of(List.of("synthetic-schema")));data.put("status",List.of(List.of(1L,"synthetic-master")));
        return new TestDatabaseSnapshot.Snapshot(data);
    }
    private static T03MatrixHistory.History parse(String text)throws Exception{byte[] bytes=text.getBytes(StandardCharsets.UTF_8);return T03MatrixHistory.parse(bytes,RUN,T03MatrixHistory.sha(bytes));}
    private static String journal(){
        int[][] counts={{1,1,6,1,1},{3,3,9,1,1},{1,1,5,2,2},{1,1,2,2,2},{1,1,4,1,1},{1,1,3,0,0},{1,1,3,0,0},{1,1,4,0,0},{2,2,6,1,1},{1,1,3,0,0}};
        var next=new int[5];var out=new StringBuilder(RUN+" PREPARED\n");
        for(int stage=0;stage<10;stage++){
            for(int table=0;table<5;table++)for(int i=0;i<counts[stage][table];i++)out.append(RUN+" ID "+T03MatrixHistory.TABLES.get(table)+" "+(++next[table])+"\n");
            if(stage<9)out.append(RUN+" STAGE_CONFIRMED\n");
        }for(int i=0;i<9;i++)out.append(RUN+" STOP\n");return out.toString();
    }
}
