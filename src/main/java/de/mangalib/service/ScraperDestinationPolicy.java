package de.mangalib.service;

import java.net.*;
import java.util.*;

/** Gemeinsame Zielprüfung. Ein späterer Transport muss die geprüften Adressen
 * direkt verwenden und jede Weiterleitung erneut prüfen (kein zweites DNS).
 * Diese Klasse allein isoliert keine Browser-Unterressourcen. */
public final class ScraperDestinationPolicy {
    private ScraperDestinationPolicy() { }
    public static URI series(String index) { return indexed("editions",index); }
    public static URI volume(String index) { return indexed("volumes",index); }
    private static URI indexed(String path,String index) {
        if(index==null || !index.matches("[1-9][0-9]{0,9}") || Long.parseLong(index)>Integer.MAX_VALUE)
            throw rejected();
        return URI.create("https://www.manga-passion.de/"+path+"/"+index);
    }
    public static URI requireAllowed(URI uri) {
        if(uri==null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo()!=null || uri.getRawFragment()!=null
                || uri.getHost()==null || !Set.of("www.manga-passion.de","media.manga-passion.de").contains(uri.getHost().toLowerCase(Locale.ROOT))
                || (uri.getPort()!=-1 && uri.getPort()!=443))throw rejected();
        return uri;
    }
    @FunctionalInterface public interface Resolver { InetAddress[] resolve(String host)throws UnknownHostException; }
    public static List<InetAddress> resolveAllowed(URI uri,Resolver resolver)throws UnknownHostException {
        requireAllowed(uri);
        InetAddress[] addresses=resolver.resolve(uri.getHost());
        if(addresses==null || addresses.length==0 || addresses.length>64)throw rejected();
        for(InetAddress address:addresses)if(address==null || !isPublic(address.getAddress()))throw rejected();
        return List.copyOf(Arrays.asList(addresses));
    }
    private static boolean isPublic(byte[] bytes) {
        int a=bytes[0]&255,b=bytes[1]&255;
        if(bytes.length==4){
            int c=bytes[2]&255;
            return !(a==0 || a==10 || a==127 || a>=224 || (a==100 && b>=64 && b<=127)
                    || (a==169 && b==254) || (a==172 && b>=16 && b<=31)
                    || (a==192 && (b==168 || (b==88 && c==99) || (b==0 && (c==0 || c==2))))
                    || (a==198 && (b==18 || b==19 || (b==51 && c==100))) || (a==203 && b==0 && c==113));
        }
        // Nur globale IPv6-Unicast-Adressen; Tunnel-/Dokumentationsnetze sperren.
        return bytes.length==16 && (a&0xe0)==0x20 && !(a==0x20 && b==0x02)
                && !(a==0x20 && b==0x01 && ((bytes[2]&255)<2 || ((bytes[2]&255)==0x0d && (bytes[3]&255)==0xb8)))
                && !(a==0x3f && b==0xff);
    }
    private static IllegalArgumentException rejected(){return new IllegalArgumentException("Scraperziel nicht freigegeben.");}
}
