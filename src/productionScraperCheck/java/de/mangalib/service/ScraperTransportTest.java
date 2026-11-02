package de.mangalib.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Keine Instanz des Proxys/Drivers: ausschließlich Parser und Browserkonfiguration. */
class ScraperTransportTest {
    @Test void policyAndResourceFailuresHaveTerminalBoundedHttpResponses()throws Exception{
        for(int status:new int[]{403,429,502}){
            var output=new java.io.ByteArrayOutputStream();ScraperEgressProxy.failureResponse(output,status);
            String response=output.toString(java.nio.charset.StandardCharsets.US_ASCII);
            assertTrue(response.startsWith("HTTP/1.1 "+status+" "));assertTrue(response.endsWith("\r\n\r\n"));
            assertTrue(response.contains("Content-Length: 0\r\n"));assertTrue(response.contains("Connection: close\r\n"));
            assertTrue(response.contains("Cache-Control: no-store\r\n"));assertTrue(output.size()<160);
        }
    }
    @Test void unsupportedFailureStatusCannotProduceSuccessfulProxyResponse(){
        var output=new java.io.ByteArrayOutputStream();
        assertThrows(IllegalArgumentException.class,()->ScraperEgressProxy.failureResponse(output,200));assertEquals(0,output.size());
    }
    @Test void permitsOnlyFixedHttpsConnectTargets(){
        for(String host:new String[]{"www.manga-passion.de","media.manga-passion.de"})
            assertEquals(host,ScraperEgressProxy.target("CONNECT "+host+":443 HTTP/1.1\r\nHost: "+host+":443\r\n\r\n").getHost());
    }
    @Test void rejectsRedirectAndSubresourceDestinationsOutsideBoundary(){
        for(String target:new String[]{"localhost:443","127.0.0.1:443","[::1]:443","169.254.169.254:443",
                "www.manga-passion.de.evil:443","user@www.manga-passion.de:443","www.manga-passion.de:80","example.com:443"})
            assertThrows(IllegalArgumentException.class,()->ScraperEgressProxy.target("CONNECT "+target+" HTTP/1.1\r\n\r\n"));
    }
    @Test void rejectsHttpAndMalformedOrOversizedHeaders(){
        for(String value:new String[]{"GET http://www.manga-passion.de/ HTTP/1.1\r\n\r\n",
                "CONNECT www.manga-passion.de:443 HTTP/1.1\r\n folded\r\n\r\n",
                "CONNECT www.manga-passion.de:443 HTTP/1.1\r\nHost: a\u0000b\r\n\r\n","x".repeat(8193),""})
            assertThrows(IllegalArgumentException.class,()->ScraperEgressProxy.target(value));
    }
    @Test void browserCannotBeOpenedOutsideInitializedWorker(){
        assertThrows(IllegalStateException.class,ScraperBrowser::open);
    }
    @Test void browserConfigurationUsesProxyForLoopbackAndDisablesDirectTransports(){
        var options=ScraperBrowser.options(12345,Path.of("C:/test/firefox.exe"));
        assertEquals(Boolean.FALSE,options.getCapability("acceptInsecureCerts"),"Auch Selenium-Defaults dürfen TLS-Prüfung nicht ausschalten.");
        System.out.println("Selenium FirefoxOptions default acceptInsecureCerts="+new org.openqa.selenium.firefox.FirefoxOptions().getCapability("acceptInsecureCerts")+"; productive=false");
        var firefox=(Map<?,?>)options.asMap().get("moz:firefoxOptions");
        var prefs=(Map<?,?>)firefox.get("prefs");
        assertEquals(1,prefs.get("network.proxy.type"));
        assertEquals(12345,prefs.get("network.proxy.ssl_port"));
        assertEquals("",prefs.get("network.proxy.no_proxies_on"));
        assertEquals(true,prefs.get("network.proxy.allow_hijacking_localhost"));
        for(String key:new String[]{"network.proxy.failover_direct","network.http.http3.enable","media.peerconnection.enabled",
                "network.dns.disablePrefetch","dom.serviceWorkers.enabled"})
            assertEquals(key.equals("network.dns.disablePrefetch"),prefs.get(key));
    }
    @Test void invalidWorkerArgumentsDoNotOpenProxy(){
        assertThrows(IllegalArgumentException.class,()->ScraperBrowser.initialize(new String[]{"--synthetic-worker-v1"}));
        assertThrows(IllegalStateException.class,ScraperBrowser::open);
    }
}
