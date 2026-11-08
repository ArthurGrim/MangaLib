package de.mangalib;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Nur RAM-Snapshots und synthetische Journale; keine echten Bestandszeilen lesen. */
class T03ContinuationPolicyTest {
    @Test void identifiesTheFiveOriginalRollbackIdsIndependently()throws Exception{
        byte[] bytes=journal().getBytes(StandardCharsets.UTF_8);
        var undone=T03ContinuationPolicy.rolledBackIds(bytes,RUN,hash(bytes));
        assertEquals(Set.of(2L),undone.get("mangareihe"));
        assertEquals(Set.of(4L,5L,6L),undone.get("baende"));
        assertEquals(5,undone.values().stream().mapToInt(Set::size).sum());
    }
    private static final String RUN="11111111-1111-4111-8111-111111111111";

    @Test void extractsOnlyCommittedIdsAndKeepsRollbackIdsSeparate() throws Exception {
        var ids=parse(journal());
        assertEquals(Set.of(1L,3L),ids.get("mangareihe"));
        assertEquals(Set.of(1L,2L,3L,7L,8L,9L,10L),ids.get("baende"));
        assertFalse(ids.get("mangareihe").contains(2L));
        assertThrows(UnsupportedOperationException.class,()->ids.get("baende").add(90L));
        assertThrows(UnsupportedOperationException.class,()->ids.clear());
    }
    @Test void bindsExactJournalBytesAndRunIdentity() throws Exception {
        byte[] bytes=journal().getBytes(StandardCharsets.UTF_8);
        assertThrows(AssertionError.class,()->T03ContinuationPolicy.parse(bytes,RUN,"0".repeat(64)));
        assertThrows(AssertionError.class,()->T03ContinuationPolicy.parse(bytes,UUID.randomUUID().toString(),hash(bytes)));
    }
    @ParameterizedTest @ValueSource(strings={"stop","unfinished","duplicate","foreignTable","zero","extra","missingPrepared"})
    void rejectsIncompleteAmbiguousOrForeignJournals(String fault) {
        String text=journal();
        text=switch(fault){
            case "stop"->text+RUN+" STOP\n";
            case "unfinished"->text.substring(0,text.lastIndexOf(RUN+" STAGE_CONFIRMED"));
            case "duplicate"->text.replace("ID baende 10","ID baende 9");
            case "foreignTable"->text.replace("ID baende 10","ID other_table 10");
            case "zero"->text.replace("ID baende 10","ID baende 0");
            case "missingPrepared"->text.replace(RUN+" PREPARED\n","");
            default->text+RUN+" ID baende 11\n";
        };
        String invalid=text;
        assertThrows(AssertionError.class,()->parse(invalid));
    }
    @Test void verifiesHistoricalSchemaAndMastersWithoutMutatingTheSnapshot() throws Exception {
        var ids=parse(journal());var empty=synthetic();var current=withRows(empty,ids);
        String unchanged=current.fingerprint();
        T03ContinuationPolicy.verify(current,ids,empty.fingerprint());
        assertEquals(unchanged,current.fingerprint());
        assertEquals(12,ids.values().stream().mapToInt(Set::size).sum());
        assertThrows(AssertionError.class,()->T03ReadOnlyPolicy.verify(current,empty.fingerprint()));
        assertThrows(AssertionError.class,()->T03ContinuationPolicy.verify(current,ids));
    }
    @ParameterizedTest @ValueSource(strings={"newRow","missingRow","master","schema"})
    void refusesUnknownRowsOrChangedReferenceState(String fault) throws Exception {
        var ids=parse(journal());var empty=synthetic();var data=new TreeMap<>(withRows(empty,ids).values());
        switch(fault){
            case "newRow"->data.put("baende",List.of(List.of(99L,"foreign")));
            case "missingRow"->data.put("mangareihe",List.of(List.of(1L,"synthetic")));
            case "master"->data.put("typen",List.of(List.of(2L)));
            default->data.put("schema.keyColumns",List.of(List.of("different")));
        }
        assertThrows(AssertionError.class,()->T03ContinuationPolicy.verify(new TestDatabaseSnapshot.Snapshot(data),ids,empty.fingerprint()));
    }
    @Test void protectsAllOldRowsAcrossMultipleNewRunsIncludingRollback() throws Exception {
        var ids=parse(journal());var empty=synthetic();var old=withRows(empty,ids);
        var first=new TreeMap<>(old.values());var rows=new ArrayList<>(first.get("mangareihe"));rows.add(List.of(50L,"first"));first.put("mangareihe",rows);
        var one=new TestDatabaseSnapshot.Snapshot(first);
        T03Preservation.verify(old,one,Map.of("mangareihe",Set.of(50L,51L))); // 51 wurde zurückgerollt.
        var second=new TreeMap<>(one.values());var more=new ArrayList<>(second.get("mangareihe"));more.add(List.of(60L,"second"));second.put("mangareihe",more);
        var two=new TestDatabaseSnapshot.Snapshot(second);
        T03Preservation.verify(one,two,Map.of("mangareihe",Set.of(60L)));
        assertThrows(AssertionError.class,()->T03Preservation.verify(one,two,Map.of("mangareihe",Set.of(50L,60L))));
        second.put("mangareihe",List.of(List.of(1L,"changed"),List.of(3L,"synthetic"),List.of(50L,"first"),List.of(60L,"second")));
        assertThrows(AssertionError.class,()->T03Preservation.verify(one,new TestDatabaseSnapshot.Snapshot(second),Map.of("mangareihe",Set.of(60L))));
    }
    @Test void continuationTaskRemainsReadOnlyAndSeparateFromMatrix() throws Exception {
        var build=Files.readString(Path.of("build.gradle.kts"));
        assertTrue(build.contains("include(\"**/T03ContinuationReadOnlyTest.class\")"));
        assertTrue(build.contains("include(\"**/T03MatrixAcceptanceTest.class\")"));
        var source=Files.readString(Path.of("src/t03Integration/java/de/mangalib/T03ContinuationReadOnlyTest.java"));
        for(String forbidden:List.of("em.persist(","em.merge(","em.remove(","em.flush(","coordinator.execute(","purchases.purchase(","new T03RunJournal("))
            assertFalse(source.contains(forbidden),forbidden);
        assertTrue(source.contains("tx.setReadOnly(true)"));
        assertTrue(source.contains("T03ContinuationPolicy.verify(before,prior)"));
    }
    private static Map<String,Set<Long>> parse(String text)throws Exception {
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);return T03ContinuationPolicy.parse(bytes,RUN,hash(bytes));
    }
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String journal(){
        var lines=new ArrayList<String>();lines.add("PREPARED");
        for(int stage=1;stage<=3;stage++){
            lines.add("ID mangareihe "+stage);lines.add("ID mangadetails "+stage);
            for(int b=1;b<=(stage==3?4:3);b++)lines.add("ID baende "+((stage-1)*3+b));
            if(stage==3)lines.add("ID einkaufsliste 1");lines.add("STAGE_CONFIRMED");
        }
        return lines.stream().map(line->RUN+" "+line+"\n").collect(java.util.stream.Collectors.joining());
    }
    private static TestDatabaseSnapshot.Snapshot withRows(TestDatabaseSnapshot.Snapshot empty,Map<String,Set<Long>> ids){
        var data=new TreeMap<>(empty.values());ids.forEach((table,values)->data.put(table,values.stream().sorted().map(id->List.<Object>of(id,"synthetic")).toList()));
        return new TestDatabaseSnapshot.Snapshot(data);
    }
    private static TestDatabaseSnapshot.Snapshot synthetic(){
        var data=new TreeMap<String,List<List<Object>>>();
        T03RunJournal.BUSINESS.forEach(table->data.put(table,List.of()));
        T03ReadOnlyPolicy.REFERENCES.forEach(table->data.put(table,List.of(List.of(1L))));
        var status=new ArrayList<List<Object>>();for(long id=1;id<=74;id++)status.add(List.of(id));data.put("status",status);
        var names=new ArrayList<>(data.keySet());
        data.put("schema.tables",names.stream().map(table->List.<Object>of(table,"InnoDB","synthetic")).toList());
        data.put("schema.columns",T03RunJournal.BUSINESS.stream().sorted().map(table->List.<Object>of(table,"id",1)).toList());
        data.put("schema.foreignKeys",List.of(List.of("synthetic-fk")));data.put("schema.keyColumns",List.of(List.of("synthetic-key")));
        return new TestDatabaseSnapshot.Snapshot(data);
    }
}
