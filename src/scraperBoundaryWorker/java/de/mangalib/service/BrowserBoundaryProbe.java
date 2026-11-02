package de.mangalib.service;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.openqa.selenium.*;

/** Nur im separaten Abnahmeartefakt. Feste .invalid-Fixtures werden im Proxy beantwortet,
 * nie aufgelöst/verbunden. Verbotene Folgeziele durchlaufen unverändert den Produktfilter. */
public final class BrowserBoundaryProbe {
    private BrowserBoundaryProbe(){}
    static byte[] fixture(String header,int port){
        String line=header.split("\r\n",2)[0];
        if(!line.matches("GET http://mangalib-acceptance\\.invalid/(warmup|image|iframe|fetch|ws|redirect|redirectForeign|navigation) HTTP/1\\.[01]"))return null;
        String name=line.split(" ")[1].substring("http://mangalib-acceptance.invalid/".length());
        String destination="http://127.0.0.1:"+port+"/"+name;
        if(name.startsWith("redirect")){
            String location=name.equals("redirect")?destination:"https://mangalib-forbidden.invalid/blocked";
            return ("HTTP/1.1 302 Found\r\nLocation: "+location+"\r\nContent-Length: 0\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        }
        String html="<!doctype html><meta charset=utf-8><title>LOCAL BOUNDARY FIXTURE</title>"+switch(name){
            case "image"->"<img src='"+destination+"'>";
            case "iframe"->"<iframe src='"+destination+"'></iframe>";
            case "fetch"->"<script>fetch('"+destination+"',{mode:'no-cors'}).catch(()=>{});</script>";
            case "ws"->"<script>try{new WebSocket('ws://localhost:"+port+"/ws')}catch(e){}</script>";
            case "warmup"->"<p>Nur lokaler Ursprung, keine Unterressourcen.</p>";
            default->"<meta http-equiv='refresh' content='0;url="+destination+"'>";
        };
        byte[] body=html.getBytes(StandardCharsets.UTF_8);
        byte[] head=("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+body.length+"\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] response=Arrays.copyOf(head,head.length+body.length);System.arraycopy(body,0,response,head.length,body.length);return response;
    }
    public static Map<String,String> run(String[] args){
        var result=new LinkedHashMap<String,String>();boolean pass=true;WebDriver browser=null;
        try(var canary=new ServerSocket()){
            canary.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}),0),8);
            var hits=new AtomicInteger();var pool=Executors.newSingleThreadExecutor();
            Future<?> listener=pool.submit(()->{while(!canary.isClosed())try(var socket=canary.accept()){hits.incrementAndGet();}catch(java.io.IOException ignored){break;}});
            try{
                int port=canary.getLocalPort();ScraperBrowser.initialize(args,header->fixture(header,port));
                browser=ScraperBrowser.open();browser.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(8));
                ScraperEgressProxy proxy=ScraperBrowser.proxy();
                // Zuerst kontrollierter Ursprung: beweist, dass Fixtures wirklich im Browser geladen werden.
                browser.get("http://mangalib-acceptance.invalid/warmup");
                if(!browser.getTitle().equals("LOCAL BOUNDARY FIXTURE"))throw new IllegalStateException("fixture-not-loaded");
                for(String name:List.of("image","iframe","fetch","ws","redirect","redirectForeign","navigation")){
                    var requests=requests(name,port);
                    int before=count(proxy,requests);
                    try{browser.get("http://mangalib-acceptance.invalid/"+name);}catch(WebDriverException expected){}
                    long until=System.nanoTime()+Duration.ofSeconds(3).toNanos();
                    while(count(proxy,requests)==before && System.nanoTime()<until && hits.get()==0)Thread.sleep(20);
                    boolean rejected=count(proxy,requests)>before && hits.get()==0;
                    result.put(name,rejected?"DENIED_AT_PROXY":hits.get()>0?"FAIL_CANARY_CONTACT":"NOT_MEASURED_CLIENT_BLOCK_OR_NO_REQUEST");
                    pass &= rejected;
                    browser.get("about:blank");
                    if(hits.get()>0)break;
                }
                if(hits.get()==0){
                    String target="https://127.0.0.1:"+port+"/direct";
                    String key="CONNECT 127.0.0.1:"+port+" HTTP/1.1";int before=count(proxy,key);
                    try{browser.get(target);}catch(WebDriverException expected){}
                    boolean denied=count(proxy,key)>before;result.put("httpsLoopback",denied?"DENIED_AT_PROXY":"NOT_MEASURED");pass &=denied;
                    browser.get("about:blank");
                    Object disabled=((JavascriptExecutor)browser).executeScript("return typeof RTCPeerConnection==='undefined' && typeof WebTransport==='undefined';");
                    result.put("directApisDisabled",Boolean.TRUE.equals(disabled)?"PASS":"FAIL");pass &=Boolean.TRUE.equals(disabled);
                    // Ausfall des eigenen Proxys darf keinen direkten Canaryzugriff verursachen.
                    proxy.close();boolean failed=false;
                    try{browser.get("http://127.0.0.1:"+port+"/proxy-down");}catch(WebDriverException expected){failed=true;}
                    Thread.sleep(500);boolean closed=failed && hits.get()==0;
                    result.put("proxyDown",closed?"REQUEST_FAILED_NO_CANARY_CONTACT":"FAIL_OR_NOT_MEASURED");pass &=closed;
                }
                var evidence=proxy.evidence();
                result.put("dnsQueries",Integer.toString(evidence.dns()));result.put("upstreamConnects",Integer.toString(evidence.connected()));
                result.put("canaryContacts",Integer.toString(hits.get()));
                pass &= hits.get()==0 && evidence.dns()==0 && evidence.connected()==0;
            }finally{
                if(browser!=null)try{browser.quit();}catch(RuntimeException failure){pass=false;}
                canary.close();pool.shutdownNow();
                try{listener.get(2,TimeUnit.SECONDS);}catch(Exception failure){pass=false;}
            }
        }catch(Exception failure){pass=false;result.put("FailureType",failure.getClass().getSimpleName());}
        finally{ScraperBrowser.close();}
        result.put("BoundaryResult",pass?"PASS":"PARTIAL_STOP");return result;
    }
    /** WebSockets können am HTTP-Proxy als Upgrade oder CONNECT erscheinen.
     * Eigenes localhost-Ziel verhindert Verwechslung mit der späteren HTTPS-Prüfung. */
    static List<String> requests(String name,int port){
        if(name.equals("ws"))return List.of("GET http://localhost:"+port+"/ws HTTP/1.1",
                "GET ws://localhost:"+port+"/ws HTTP/1.1","CONNECT localhost:"+port+" HTTP/1.1");
        return List.of(name.equals("redirectForeign")?"CONNECT mangalib-forbidden.invalid:443 HTTP/1.1":
                "GET http://127.0.0.1:"+port+"/"+name+" HTTP/1.1");
    }
    private static int count(ScraperEgressProxy proxy,List<String> requests){return requests.stream().mapToInt(r->count(proxy,r)).sum();}
    private static int count(ScraperEgressProxy proxy,String request){String hash=ScraperEgressProxy.requestHash(request);return (int)proxy.evidence().denied().stream().filter(hash::equals).count();}
}
