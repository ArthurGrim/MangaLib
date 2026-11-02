package de.mangalib.windowsadapter;

import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Gemeinsame Startlease. Kein Monitor wird über einen fremden/native Aufruf gehalten. */
final class ScRevocationFence {
    record Snapshot(boolean admitted,boolean revoked,int outstanding,boolean ready) { }
    private final LongSupplier clock;
    private final long deadline;
    private boolean admitted,revoked,ready;
    private int outstanding;
    ScRevocationFence(LongSupplier clock,long deadline){this.clock=Objects.requireNonNull(clock);this.deadline=deadline;}
    synchronized void admit(){if(!revoked && clock.getAsLong()<deadline)admitted=true;}
    synchronized boolean allowed(){if(clock.getAsLong()>=deadline)revoke();return admitted && !revoked;}
    synchronized void revoke(){revoked=true;ready=false;}
    synchronized boolean publishReady(){if(!allowed())return false;ready=true;return true;}
    synchronized Snapshot snapshot(){return new Snapshot(admitted,revoked,outstanding,ready);}
    synchronized Permit reserve(){if(!allowed())throw new IllegalStateException("sc-lease-revoked");return new Permit();}
    final class Permit {
        private boolean committed,returned;
        /** Linearisation: ab hier ist der Aufruf verbindlich begonnen, auch bei OS-/JNI-Hänger. */
        void commit(){synchronized(ScRevocationFence.this){if(committed || returned || !allowed())throw new IllegalStateException("sc-lease-revoked");committed=true;outstanding++;}}
        void returned(){synchronized(ScRevocationFence.this){if(!committed || returned)throw new IllegalStateException("sc-lease-return");returned=true;outstanding--;}}
    }
    <T> T call(Supplier<T> operation){
        Permit permit=reserve();permit.commit();
        // Exception ist keine bestätigte Native-Rückkehr: Reservierung bleibt UNKNOWN.
        T result=operation.get();permit.returned();return result;
    }
    void run(Runnable operation){call(()->{operation.run();return null;});}
}
