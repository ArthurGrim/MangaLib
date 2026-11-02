package de.mangalib.service;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundaryFixtureTest {
    @Test void websocketEvidenceSupportsBothProxyFormsWithoutHttpsCaseOverlap(){
        var requests=BrowserBoundaryProbe.requests("ws",12345);
        assertTrue(requests.contains("CONNECT localhost:12345 HTTP/1.1"));
        assertTrue(requests.contains("GET http://localhost:12345/ws HTTP/1.1"));
        assertTrue(requests.contains("GET ws://localhost:12345/ws HTTP/1.1"));
        assertFalse(requests.contains("CONNECT 127.0.0.1:12345 HTTP/1.1"));
    }
    @Test void fixturesAreReservedAndNeverForwardArbitraryRequests(){
        for(String request:new String[]{"CONNECT www.manga-passion.de:443 HTTP/1.1","GET http://localhost/image HTTP/1.1",
                "GET http://mangalib-acceptance.invalid.evil/image HTTP/1.1","GET http://mangalib-acceptance.invalid/unknown HTTP/1.1"})
            assertNull(BrowserBoundaryProbe.fixture(request+"\r\n\r\n",12345));
    }
    @Test void realHttpRedirectFixturePointsOnlyToOwnedLoopbackCanary(){
        String response=new String(BrowserBoundaryProbe.fixture("GET http://mangalib-acceptance.invalid/redirect HTTP/1.1\r\n\r\n",12345),StandardCharsets.US_ASCII);
        assertTrue(response.startsWith("HTTP/1.1 302 Found\r\n"));assertTrue(response.contains("Location: http://127.0.0.1:12345/redirect\r\n"));
    }
    @Test void resourcesAndRedirectsUseDistinctCorrelatablePaths(){
        for(String name:new String[]{"image","iframe","fetch","ws","navigation"}){
            String response=new String(BrowserBoundaryProbe.fixture("GET http://mangalib-acceptance.invalid/"+name+" HTTP/1.1\r\n\r\n",12345),StandardCharsets.US_ASCII);
            assertTrue(response.startsWith("HTTP/1.1 200 OK"));assertTrue(response.contains("/"+name));
        }
    }
}
