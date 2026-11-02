package de.mangalib.windowsadapter;

/** Fester isolierter S-Einstieg; kein produktiver Anwendungs- oder Probe-Task. */
public final class ScSupervisorMain {
    private ScSupervisorMain() { }
    public static void main(String[] args) {
        if(args.length==1 && "--sc-native-empty-v1".equals(args[0])) {
            System.exit(ScExecutablePlatform.nativeRun(ScExecutablePlatform.Role.SUPERVISOR,args));return;
        }
        int result=ScExecutablePlatform.run(ScExecutablePlatform.Role.SUPERVISOR,args,ScExecutablePlatform.nativeDriver());
        if(result!=0) throw new IllegalStateException("sc-supervisor-blocked");
    }
}
