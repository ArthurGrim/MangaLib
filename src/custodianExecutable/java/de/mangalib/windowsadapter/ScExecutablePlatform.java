package de.mangalib.windowsadapter;

/**
 * Einziger Einstieg zur späteren lokalen OS-Verdrahtung. Keine ServiceLoader-,
 * Reflection-, Kommando-, PID-, Pfad- oder Umgebungsprovider aus Benutzereingaben.
 * Die vorliegende Freigabe erlaubt nur Fakes; offene Startprovenienz wird NICHT
 * durch ProcessBuilder oder den historischen H-in-K-Startpfad ersetzt.
 */
final class ScExecutablePlatform {
    static final String PROFILE="--sc-empty-v1";
    enum Role { SUPERVISOR, CUSTODIAN }
    interface Driver { int runSupervisor(); int runCustodian(); }
    private ScExecutablePlatform() { }
    static int run(Role role,String[] args,Driver driver) {
        if(args==null || args.length!=1 || !PROFILE.equals(args[0])) return 64;
        if(driver==null) return 78;
        return role==Role.SUPERVISOR ? driver.runSupervisor() : driver.runCustodian();
    }
    static Driver nativeDriver() {
        // OS-GATE: startgebundene stdio-Enden + C-lokale native Acquisition fehlen.
        // Explizit geschlossen, keine heimliche Initialisierung oder Fake-Erfolgsmeldung.
        return null;
    }
    /** Neuer expliziter Native-Einstieg; feste Klasse, keine aus IPC/Umgebung gelieferte Factory. */
    static int nativeRun(Role role,String[] args) {
        return nativeRun(role,args,()->(Driver)Class.forName("de.mangalib.windowsadapter.ScNativeDriver")
                .getMethod("create").invoke(null),System.err::println);
    }
    interface Factory { Driver create() throws ReflectiveOperationException; }
    /** Injizierbarer, JNA-freier Einstiegstest; die echte Factory bleibt fest verdrahtet. */
    static int nativeRun(Role role,String[] args,Factory factory,java.util.function.Consumer<String> diagnostics) {
        if(args==null || args.length!=1 || !"--sc-native-empty-v1".equals(args[0]))return 64;
        try {
            Driver driver=java.util.Objects.requireNonNull(factory.create());
            return role==Role.SUPERVISOR?driver.runSupervisor():driver.runCustodian();
        }catch(ReflectiveOperationException | RuntimeException | LinkageError error){
            ScBootstrapDiagnostics.emit(diagnostics,role,ScBootstrapDiagnostics.Phase.ENTRY,error);return 78;
        }
    }
}
