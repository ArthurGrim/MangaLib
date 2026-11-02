package de.mangalib.service;
import org.junit.jupiter.api.Test;
import java.net.*;
import static org.junit.jupiter.api.Assertions.*;
class ScraperDestinationPolicyTest {
    @Test void indexedTargetsCannotInjectPathsOrQueries(){
        assertEquals("https://www.manga-passion.de/editions/12",ScraperDestinationPolicy.series("12").toString());
        assertEquals("https://www.manga-passion.de/volumes/12",ScraperDestinationPolicy.volume("12").toString());
        for(String s:new String[]{"0","-1","../1","1?x=y","2147483648","01","","1/2"})
            assertThrows(IllegalArgumentException.class,()->ScraperDestinationPolicy.series(s));
    }
    @Test void invalidTargetsAreRejectedBeforeResolverIsCalled(){
        for(String s:new String[]{"http://www.manga-passion.de/","https://localhost/","https://www.manga-passion.de.evil/",
                "https://user@www.manga-passion.de/","https://www.manga-passion.de:444/","/relative","file:///x"})
            assertThrows(IllegalArgumentException.class,()->ScraperDestinationPolicy.resolveAllowed(URI.create(s),host->{fail("DNS called");return null;}));
    }
    @Test void mixedPublicAndPrivateAnswersFailClosed()throws Exception{
        var uri=ScraperDestinationPolicy.series("1");
        var publicIp=InetAddress.getByAddress(new byte[]{8,8,8,8});
        for(byte[] bytes:new byte[][]{{127,0,0,1},{10,1,1,1},{(byte)169,(byte)254,1,1},{(byte)192,(byte)168,1,1},{100,64,0,1},new byte[16]})
            assertThrows(IllegalArgumentException.class,()->ScraperDestinationPolicy.resolveAllowed(uri,host->new InetAddress[]{publicIp,InetAddress.getByAddress(bytes)}));
        assertEquals(publicIp,ScraperDestinationPolicy.resolveAllowed(uri,host->new InetAddress[]{publicIp}).getFirst());
    }
}
