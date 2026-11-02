package de.mangalib.service;

import java.util.concurrent.locks.LockSupport;

/** Ausschließlich Abnahmeworker: keine Seitenabrufe, kein Ergebnis, kein quit.
 * Der produktive Owner muss die gesamte eigene Jobgruppe nach Frist beenden. */
public final class BrowserTimeoutProbe {
    private BrowserTimeoutProbe() { }
    public static void hold(String[] args)throws Exception {
        // Engerer Abnahmeumfang: jeder Proxyrequest wird vor DNS/Connect abgewiesen.
        ScraperBrowser.initialize(args,header->{throw new IllegalArgumentException("timeout-no-upstream");});
        var browser=ScraperBrowser.open();
        browser.get("about:blank");
        // Bewusst kein Shutdownhook: dieser Grenzfall prüft erzwungenen Jobabschluss.
        while(true){LockSupport.parkNanos(1_000_000_000L);java.lang.ref.Reference.reachabilityFence(browser);}
    }
}
