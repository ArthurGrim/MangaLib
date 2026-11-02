package de.mangalib.service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Privater, begrenzter CONNECT-Proxy pro Worker. Kein TLS-MITM: Zertifikatsprüfung
 * bleibt beim Browser. Jedes neue Ziel (auch Redirect/Unterressource) muss durch
 * die Allowlist und die DNS-Prüfung; connect verwendet exakt die geprüfte IP.
 * Kein generischer HTTP-Proxy und kein Direct-Fallback. */
final class ScraperEgressProxy implements AutoCloseable {
    private final java.util.function.Function<String,byte[]> fixture;
    private final AtomicInteger dns=new AtomicInteger(),connected=new AtomicInteger();
    private final java.util.List<String> denied=new java.util.ArrayList<>();
    record Evidence(int accepted,int dns,int connected,java.util.List<String> denied){
        Evidence{denied=java.util.List.copyOf(denied);}
    }
    synchronized Evidence evidence(){return new Evidence(accepted.get(),dns.get(),connected.get(),denied);}
    static String requestHash(String line){
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(line.getBytes(StandardCharsets.US_ASCII)));}
        catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
    private synchronized void rejected(String header){if(denied.size()<256)denied.add(requestHash(header.split("\r\n",2)[0]));}
    private final ServerSocket server;
    private final ExecutorService tasks=Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
    private final Semaphore slots=new Semaphore(8);
    private final AtomicLong transferred=new AtomicLong();
    private final AtomicInteger accepted=new AtomicInteger();
    private volatile boolean closed;

    ScraperEgressProxy()throws IOException{
        this(null);
    }
    /** Nur der separat verpackte Abnahmeworker liefert feste lokale Fixtureantworten.
     * Der produktive Worker verwendet ausschließlich den parameterlosen Konstruktor. */
    ScraperEgressProxy(java.util.function.Function<String,byte[]> fixture)throws IOException{
        this.fixture=fixture;
        server=new ServerSocket();
        server.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}),0),8);
        tasks.submit(this::accept);
    }
    int port(){return server.getLocalPort();}
    private void accept(){
        while(!closed)try{
            Socket client=server.accept();
            if(accepted.incrementAndGet()>256 || !slots.tryAcquire()){
                try{failureResponse(client.getOutputStream(),429);}catch(IOException ignored){}
                finally{try{client.close();}catch(IOException ignored){}}continue;
            }
            sockets.add(client);tasks.submit(()->{try{tunnel(client);}finally{slots.release();}});
        }catch(IOException failure){if(!closed)close();return;}
    }
    /** Terminale HTTP-Antwort statt kommentarlosem TCP-Abbruch: Browser sollen eine
     * Policyablehnung nicht für einen kurzzeitigen Transportfehler halten. Keine Rohdaten. */
    static void failureResponse(OutputStream out,int status)throws IOException{
        String reason=switch(status){case 403->"Forbidden";case 429->"Too Many Requests";case 502->"Bad Gateway";
            default->throw new IllegalArgumentException("scraper-proxy-status");};
        out.write(("HTTP/1.1 "+status+" "+reason+"\r\nContent-Length: 0\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }
    /** Separat testbar: vor DNS/socket keinerlei freie URI-/Headerinterpretation. */
    static URI target(String header){
        if(header==null || header.length()>8192 || !header.endsWith("\r\n\r\n"))throw new IllegalArgumentException("scraper-proxy-request");
        String[] lines=header.split("\r\n");
        if(!lines[0].matches("CONNECT (www\\.manga-passion\\.de|media\\.manga-passion\\.de):443 HTTP/1\\.[01]"))
            throw new IllegalArgumentException("scraper-proxy-target");
        for(int i=1;i<lines.length;i++)if(!lines[i].matches("[A-Za-z0-9-]+: [\\x20-\\x7e]*"))
            throw new IllegalArgumentException("scraper-proxy-header");
        return ScraperDestinationPolicy.requireAllowed(URI.create("https://"+lines[0].split(" ")[1]+"/"));
    }
    private void tunnel(Socket client){
        Socket remote=null;boolean established=false;String raw=null;
        try{
            client.setSoTimeout(15_000);
            var header=new ByteArrayOutputStream();int tail=0;
            while(header.size()<8192){
                int c=client.getInputStream().read();if(c<0)throw new EOFException();header.write(c);tail=(tail<<8)|c;
                if(tail==0x0d0a0d0a)break;
            }
            raw=header.toString(StandardCharsets.US_ASCII);
            if(fixture!=null){byte[] response=fixture.apply(raw);if(response!=null){client.getOutputStream().write(response);client.getOutputStream().flush();return;}}
            URI uri=target(raw);
            dns.incrementAndGet();
            var addresses=ScraperDestinationPolicy.resolveAllowed(uri,InetAddress::getAllByName);
            // Kein Hostname im connect: verhindert ein zweites DNS/Rebinding-Fenster.
            remote=connect(addresses);
            connected.incrementAndGet();
            remote.setSoTimeout(15_000);
            client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            client.getOutputStream().flush();
            established=true;
            final Socket upstream=remote;
            Future<?> upload=tasks.submit(()->pump(client,upstream));
            pump(upstream,client);
            client.close();upstream.close();
            upload.get(2,TimeUnit.SECONDS);
        }catch(IllegalArgumentException denied){
            if(raw!=null)rejected(raw);
            if(!established)try{failureResponse(client.getOutputStream(),403);}catch(IOException ignored){}
        }catch(Exception failure){
            if(!established)try{failureResponse(client.getOutputStream(),502);}catch(IOException ignored){}
            // Ziel-/Netzwerkfehler enthalten keine ungefilterten URLs oder Rohantworten im Log.
        }finally{
            try{client.close();}catch(IOException ignored){}
            sockets.remove(client);
            if(remote!=null){sockets.remove(remote);try{remote.close();}catch(IOException ignored){}}
        }
    }
    private Socket connect(java.util.List<InetAddress> addresses)throws IOException{
        long start=System.nanoTime();
        for(InetAddress address:addresses){
            long left=10_000-(System.nanoTime()-start)/1_000_000;
            if(closed || left<=0 || transferred.get()>64L*1024*1024)break;
            Socket socket=new Socket();sockets.add(socket);
            try{socket.connect(new InetSocketAddress(address,443),(int)left);return socket;}
            catch(IOException failure){sockets.remove(socket);try{socket.close();}catch(IOException ignored){}}
        }
        throw new IOException("scraper-connect");
    }
    private void pump(Socket from,Socket to){
        try{
            byte[] buffer=new byte[16*1024];int n;
            while(!closed && (n=from.getInputStream().read(buffer))!=-1){
                if(transferred.addAndGet(n)>64L*1024*1024)throw new IOException("scraper-transfer-limit");
                to.getOutputStream().write(buffer,0,n);to.getOutputStream().flush();
            }
        }catch(IOException ignored){}finally{try{to.shutdownOutput();}catch(IOException ignored){}}
    }
    @Override public void close(){
        closed=true;try{server.close();}catch(IOException ignored){}
        for(Socket socket:sockets)try{socket.close();}catch(IOException ignored){}
        tasks.shutdownNow();
    }
}
