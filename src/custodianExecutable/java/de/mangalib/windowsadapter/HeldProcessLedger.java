package de.mangalib.windowsadapter;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import static de.mangalib.windowsadapter.Win64TransferContract.*;
import static de.mangalib.windowsadapter.KernelCalls.*;

/** Registriert NUR Ergebnisse des vertrauenswürdigen lokalen Erwerbspfads, niemals IPC/PIDs. */
final class HeldProcessLedger implements ProcessLedger {
    interface Close { boolean close(long value); }
    private static final class Entry {
        final Object token=new Object();
        final long value;
        final int access;
        final Identity identity;
        int uses;
        boolean closing, closed, uncertain;
        Entry(long value,int access,Identity identity) { this.value=value; this.access=access; this.identity=identity; }
    }
    private final Map<Object,Entry> entries=new IdentityHashMap<>();
    private final Raw raw;
    private final Close close;
    HeldProcessLedger(Raw raw,Close close) { this.raw=Objects.requireNonNull(raw); this.close=Objects.requireNonNull(close); }
    /** access stammt aus Create-/Duplicate-Provenienz; GetHandleInformation liefert KEINE Accessmaske. */
    Object registerAcquired(long value,int access,Identity identity) {
        if(value==0 || value==-1 || identity==null || identity.pid()<=0 || identity.creationTime()<=0
                || identity.image()==null || identity.image().isBlank() || identity.image().length()>512
                || (access!=PROCESS_ACCESS && access!=OuterJobCustodian.PROCESS_RIGHTS)) throw new IllegalArgumentException("held-process-acquisition");
        var e=new Entry(value,access,identity);
        synchronized(this) {
            if(entries.values().stream().anyMatch(x->x.value==value)) throw new IllegalArgumentException("held-process-duplicate");
            // Registrierung vor Abfrage: bei unbekanntem Ergebnis geht die erworbene Ressource nicht verloren.
            entries.put(e.token,e); e.uses++;
        }
        boolean valid=false;
        try {
            ProcessState state=raw.inspectProcess(value);
            valid=state!=null && state.alive() && state.nonInherited() && identity.equals(state.identity());
            if(!valid) throw new IllegalStateException("held-process-unverified");
            return e.token;
        } finally { synchronized(this) { e.uses--; if(!valid) e.uncertain=true; } }
    }
    @Override public synchronized ProcessUse borrow(Object token) {
        Entry e=entries.get(token);
        if(e==null || e.closed || e.closing || e.uncertain) throw new IllegalStateException("held-process-unavailable");
        e.uses++;
        return new ProcessUse() {
            boolean released;
            public Object capability() { return token; }
            public long value() { return e.value; }
            public int access() { return e.access; }
            public void close() { synchronized(HeldProcessLedger.this) { if(!released) { released=true; e.uses--; } } }
        };
    }
    OuterJobCustodian.ProcessEnd inspect(OuterJobCustodian.Owner owner) {
        try(ProcessUse use=borrow(owner.handle())) {
            ProcessState state=raw.inspectProcess(use.value());
            if(state==null || !state.nonInherited() || !owner.identity().equals(state.identity()))
                return new OuterJobCustodian.ProcessEnd(owner.handle(),owner.identity(),OuterJobCustodian.Signal.UNKNOWN);
            return new OuterJobCustodian.ProcessEnd(owner.handle(),state.identity(),state.alive()?OuterJobCustodian.Signal.ALIVE:OuterJobCustodian.Signal.ENDED);
        }
    }
    boolean close(Object token) {
        Entry e;
        synchronized(this) {
            e=entries.get(token);
            if(e==null || e.closed || e.closing || e.uncertain || e.uses!=0) return false;
            e.closing=true;
        }
        boolean ok=false;
        try { ok=close.close(e.value); return ok; }
        finally { synchronized(this) { e.closing=false; e.closed=ok; e.uncertain=!ok; } }
    }
    synchronized boolean complete() { return entries.values().stream().allMatch(e->e.closed && !e.uncertain && e.uses==0); }
}
