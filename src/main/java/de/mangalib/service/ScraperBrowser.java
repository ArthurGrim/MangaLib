package de.mangalib.service;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.*;

/** Nur im isolierten Worker initialisierbar. Kein Selenium-Manager/Download-Fallback. */
public final class ScraperBrowser {
    private static Path gecko,firefox;
    private static ScraperEgressProxy proxy;
    private ScraperBrowser(){}
    public static synchronized void initialize(String[] args)throws Exception{
        initialize(args,null);
    }
    static synchronized void initialize(String[] args,java.util.function.Function<String,byte[]> fixture)throws Exception{
        if(proxy!=null || args.length!=5 || !args[0].equals("--live-scraper-v1"))throw new IllegalArgumentException("scraper-worker-arguments");
        gecko=checked(args[1],args[2]);firefox=checked(args[3],args[4]);
        proxy=new ScraperEgressProxy(fixture);
    }
    private static Path checked(String file,String hash)throws Exception{
        Path path=Path.of(file);
        if(!path.isAbsolute() || !path.toRealPath().equals(path) || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)
                || !hash.matches("[a-f0-9]{64}") || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path))).equals(hash))throw new IOException("scraper-browser-artifact");
        return path;
    }
    static synchronized FirefoxOptions options(int port,Path binary){
        var options=new FirefoxOptions();options.setAcceptInsecureCerts(false);options.setBinary(binary.toString());options.addArguments("--headless");
        options.addPreference("network.proxy.type",1);
        options.addPreference("network.proxy.http","127.0.0.1");options.addPreference("network.proxy.http_port",port);
        options.addPreference("network.proxy.ssl","127.0.0.1");options.addPreference("network.proxy.ssl_port",port);
        options.addPreference("network.proxy.no_proxies_on","");
        options.addPreference("network.proxy.allow_hijacking_localhost",true);
        options.addPreference("network.proxy.failover_direct",false);
        options.addPreference("network.http.http3.enable",false);
        options.addPreference("network.webtransport.enabled",false);
        options.addPreference("network.trr.mode",5);
        options.addPreference("media.peerconnection.enabled",false);
        options.addPreference("network.dns.disablePrefetch",true);
        options.addPreference("network.prefetch-next",false);
        options.addPreference("network.predictor.enabled",false);
        options.addPreference("network.captive-portal-service.enabled",false);
        options.addPreference("network.connectivity-service.enabled",false);
        options.addPreference("app.update.auto",false);
        options.addPreference("datareporting.healthreport.uploadEnabled",false);
        options.addPreference("toolkit.telemetry.enabled",false);
        options.addPreference("app.normandy.enabled",false);
        options.addPreference("network.protocol-handler.external-default",false);
        options.addPreference("network.protocol-handler.warn-external-default",true);
        options.addPreference("xpinstall.enabled",false);
        options.addPreference("extensions.enabledScopes",0);
        options.addPreference("extensions.systemAddon.update.enabled",false);
        options.addPreference("security.fileuri.strict_origin_policy",true);
        options.addPreference("browser.helperApps.alwaysAsk.force",true);
        options.addPreference("dom.serviceWorkers.enabled",false);
        options.addPreference("browser.safebrowsing.downloads.enabled",false);
        options.addPreference("browser.download.folderList",2);
        options.addPreference("browser.download.dir",Path.of(System.getProperty("java.io.tmpdir"),"downloads").toString());
        options.addPreference("browser.download.always_ask_before_handling_new_types",true);
        return options;
    }
    static synchronized WebDriver open(){
        if(proxy==null)throw new IllegalStateException("Live-Scraper nur im kontrollierten Worker verfügbar.");
        var service=new GeckoDriverService.Builder().usingDriverExecutable(gecko.toFile())
                .withLogOutput(OutputStream.nullOutputStream()).build();
        var driver=new FirefoxDriver(service,options(proxy.port(),firefox));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(40)).scriptTimeout(Duration.ofSeconds(15));
        return driver;
    }
    static synchronized ScraperEgressProxy proxy(){if(proxy==null)throw new IllegalStateException("scraper-proxy");return proxy;}
    public static synchronized void close(){if(proxy!=null){
        proxy.close();var e=proxy.evidence();
        try{Files.writeString(Path.of(System.getProperty("java.io.tmpdir"),"scraper-proxy.evidence"),
                "SCRAPER_PROXY_V1\n"+e.accepted()+"\n"+e.dns()+"\n"+e.connected()+"\n"+e.denied().size()+"\n",StandardOpenOption.CREATE_NEW);}
        catch(IOException ignored){/* Fehlender Diagnosebeleg ist niemals ein Abnahme-PASS. */}
        proxy=null;
    }}
}
