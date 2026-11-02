package de.mangalib.windowsadapter;

/** Fester isolierter C-Einstieg. Meldet bei fehlendem OS-Provider weder ACK noch READY. */
public final class ScCustodianMain {
    private ScCustodianMain() { }
    public static void main(String[] args) {
        if(args.length==1 && "--sc-native-empty-v1".equals(args[0])) {
            System.exit(ScExecutablePlatform.nativeRun(ScExecutablePlatform.Role.CUSTODIAN,args));return;
        }
        int result=ScExecutablePlatform.run(ScExecutablePlatform.Role.CUSTODIAN,args,ScExecutablePlatform.nativeDriver());
        if(result!=0) throw new IllegalStateException("sc-custodian-blocked");
    }
}
