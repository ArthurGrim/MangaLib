package de.mangalib.windowsadapter;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import static de.mangalib.windowsadapter.Win64Layouts.OwnedStructure;

/** LLP64, keine DLL-Initialisierung. SIZE_T und Handles sind hier jeweils 8 Byte. */
final class Win64ProcessLayouts {
    static final long JOB_LIST = 0x2000d;
    static final int SUSPENDED = 4, EXTENDED = 0x80000, UNICODE_ENV = 0x400, NO_WINDOW = 0x08000000;
    static final int START_FLAGS = SUSPENDED | EXTENDED | UNICODE_ENV | NO_WINDOW;
    static final int QUERY_AND_SYNCHRONIZE = 0x101000;
    static final int WAIT_OBJECT = 0, WAIT_TIMEOUT = 258, WAIT_FAILED = -1;

    @Structure.FieldOrder({"cb", "reserved", "desktop", "title", "x", "y", "xSize", "ySize",
            "xChars", "yChars", "fill", "flags", "show", "reservedBytes", "reservedPointer",
            "stdin", "stdout", "stderr"})
    public static class Startup extends OwnedStructure {
        public int cb;
        public Pointer reserved, desktop, title;
        public int x, y, xSize, ySize, xChars, yChars, fill, flags;
        public short show, reservedBytes;
        public Pointer reservedPointer, stdin, stdout, stderr;
        int offset(String name) { return fieldOffset(name); }
    }

    @Structure.FieldOrder({"startup", "attributes"})
    public static class StartupEx extends OwnedStructure {
        public Startup startup = new Startup();
        public Pointer attributes;
        int offset(String name) { return fieldOffset(name); }
        @Override public void close() { startup.close(); super.close(); }
    }

    @Structure.FieldOrder({"process", "thread", "pid", "tid"})
    public static class ProcessInfo extends OwnedStructure {
        public Pointer process, thread;
        public int pid, tid;
        int offset(String name) { return fieldOffset(name); }
    }

    @Structure.FieldOrder({"low", "high"})
    public static class FileTime extends OwnedStructure {
        public int low, high;
        long value() { return (Integer.toUnsignedLong(high) << 32) | Integer.toUnsignedLong(low); }
    }

    static void verify() {
        try (var s = new Startup(); var ex = new StartupEx(); var pi = new ProcessInfo(); var t = new FileTime()) {
            if (s.size() != 104 || s.offset("reserved") != 8 || s.offset("flags") != 60
                    || s.offset("show") != 64 || s.offset("reservedPointer") != 72
                    || s.offset("stdin") != 80 || ex.size() != 112 || ex.offset("attributes") != 104
                    || pi.size() != 24 || pi.offset("thread") != 8 || pi.offset("pid") != 16
                    || pi.offset("tid") != 20 || t.size() != 8) throw new IllegalStateException("Process ABI mismatch");
        }
    }
}
