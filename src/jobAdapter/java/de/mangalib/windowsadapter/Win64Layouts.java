package de.mangalib.windowsadapter;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** LLP64-Datenabbildung; keine Kernel32-Initialisierung in Layoutklassen. */
final class Win64Layouts {
    static final int BASIC_ACCOUNTING = 1, PROCESS_IDS = 3, EXTENDED_LIMITS = 9;
    static final int KILL_ON_CLOSE = 0x2000, BREAKAWAY = 0x0800, SILENT_BREAKAWAY = 0x1000;
    static final int INSUFFICIENT_BUFFER = 122, MORE_DATA = 234;

    public abstract static class OwnedStructure extends Structure implements AutoCloseable {
        @Override public void close() {
            if (getPointer() instanceof Memory memory) memory.close();
        }
    }
    @Structure.FieldOrder({"processTime", "jobTime", "flags", "minWorking", "maxWorking",
            "activeLimit", "affinity", "priority", "scheduling"})
    public static class Basic extends OwnedStructure {
        public long processTime, jobTime;
        public int flags;
        public long minWorking, maxWorking;
        public int activeLimit;
        public long affinity;
        public int priority, scheduling;
        int offset(String name) { return fieldOffset(name); }
    }

    @Structure.FieldOrder({"readOps", "writeOps", "otherOps", "readBytes", "writeBytes", "otherBytes"})
    public static class Io extends OwnedStructure {
        public long readOps, writeOps, otherOps, readBytes, writeBytes, otherBytes;
    }

    @Structure.FieldOrder({"basic", "io", "processMemory", "jobMemory", "peakProcessMemory", "peakJobMemory"})
    public static class Extended extends OwnedStructure {
        public Basic basic = new Basic();
        public Io io = new Io();
        public long processMemory, jobMemory, peakProcessMemory, peakJobMemory;
        int offset(String name) { return fieldOffset(name); }
        @Override public void close() { basic.close(); io.close(); super.close(); }
    }

    @Structure.FieldOrder({"userTime", "kernelTime", "periodUserTime", "periodKernelTime",
            "pageFaults", "total", "active", "terminated"})
    public static class Accounting extends OwnedStructure {
        public long userTime, kernelTime, periodUserTime, periodKernelTime;
        public int pageFaults, total, active, terminated;
        int offset(String name) { return fieldOffset(name); }
    }

    @Structure.FieldOrder({"assigned", "listed", "firstPid"})
    public static class PidList extends OwnedStructure {
        public int assigned, listed;
        public long firstPid;
        int offset(String name) { return fieldOffset(name); }
    }

    static void verify() {
        try (var b = new Basic(); var e = new Extended(); var a = new Accounting(); var p = new PidList()) {
        if (b.size() != 64 || b.offset("flags") != 16 || b.offset("activeLimit") != 40
                || b.offset("affinity") != 48 || e.size() != 144 || e.offset("io") != 64
                || e.offset("jobMemory") != 120 || a.size() != 48 || a.offset("total") != 36
                || a.offset("active") != 40 || a.offset("terminated") != 44
                || p.size() != 16 || p.offset("firstPid") != 8) throw new IllegalStateException("Win64 ABI mismatch");
        }
    }

    static List<Long> decodePids(Memory buffer, int returned, int capacity) {
        if (buffer.size() < 8 || capacity < 1 || returned < 8) throw new KernelCalls.Failure("pid-list-short-header", 0);
        long assigned = Integer.toUnsignedLong(buffer.getInt(0));
        long listed = Integer.toUnsignedLong(buffer.getInt(4));
        if (assigned != listed || listed > capacity || returned < 8 + 8 * listed
                || returned > buffer.size()) throw new KernelCalls.Failure("pid-list-incomplete", 0);
        var ids = new ArrayList<Long>();
        for (int i = 0; i < listed; i++) {
            long pid = buffer.getLong(8L + 8L * i);
            if (pid <= 0 || pid > 0xffff_ffffL) throw new KernelCalls.Failure("pid-list-invalid-id", 0);
            ids.add(pid);
        }
        if (new HashSet<>(ids).size() != ids.size()) throw new KernelCalls.Failure("pid-list-duplicate", 0);
        return List.copyOf(ids);
    }
}
