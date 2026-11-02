package de.mangalib.ownerprobe;

/** Eigener enger Messumfang: nur synthetischer Ownerabschluss, keine globale Prozessprovenienz. */
public final class OwnerCompletionMain {
    private OwnerCompletionMain(){ }
    public static void main(String[] args){
        OwnerProvenanceMain.run(args,"--owner-completion-v1","OWNER_COMPLETION_V1");
    }
}
