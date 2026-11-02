package de.mangalib.windowsadapter;

import java.util.UUID;

/** Begrenzter lokaler Endbericht; Statuswerte stammen ausschließlich aus vorhandenen Endbarrieren. */
final class ScRunEvidence {
    private ScRunEvidence() { }
    static String format(UUID session,boolean ready,EmptyCustodianBootstrap.Result result,
                         ScStartProvider.Snapshot provider,KernelCalls.Identity child,int exit) {
        return format(session,ready,result,provider,child,exit,true,0);
    }
    static String format(UUID session,boolean ready,EmptyCustodianBootstrap.Result result,
                         ScStartProvider.Snapshot provider,KernelCalls.Identity child,int exit,boolean controlClosed,int leaseOutstanding) {
        boolean complete=controlClosed && leaseOutstanding==0 && result!=null && result.technicalCompletion() && !result.operationOutstanding();
        boolean exited=provider!=null && provider.ended() && exit!=Integer.MIN_VALUE;
        return "profile=SCN2\nsession="+session+"\nready="+ready+"\ncomplete="+complete
                +"\ngroupEnded="+(result!=null && result.groupEnded())
                +"\ncResources="+(result!=null && result.custodianResourcesClosed())
                +"\nsResources="+(result!=null && result.supervisorResourcesClosed())
                +"\njournalTerminal="+(result!=null && result.journalTerminal())
                +"\noperationOutstanding="+(leaseOutstanding!=0 || result==null || result.operationOutstanding())
                +"\ncEnd="+(provider!=null && provider.ended())
                +"\ncHandlesClosed="+(provider!=null && provider.handlesClosed())
                +"\ncExit="+(exited?Integer.toString(exit):"unknown")
                +"\ncPid="+(child==null?"unknown":Long.toString(child.pid()))
                +"\ncBirth="+(child==null?"unknown":Long.toString(child.creationTime()))+"\n";
    }
}
