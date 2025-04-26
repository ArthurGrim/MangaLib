package de.mangalib.service;

import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MangaScraper {

    public static Map<String, String> scrapeMangaData(String mangaIndex) {
        String baseUrl = "https://www.manga-passion.de";
        String url = baseUrl + "/editions/" + mangaIndex;
        System.out.println("Scraping URL: " + url);

        Map<String, String> mangaData = new HashMap<>();
        WebDriver driver = setupWebDriver();

        try {
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // Warten bis die Seite geladen ist (Titel vorhanden)
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("h1")));

            // Cookie-Banner schließen (falls vorhanden)
            try {
                WebElement denyAllButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("consent-deny-all")));
                denyAllButton.click();
                System.out.println("Cookie-Banner geschlossen.");
            } catch (Exception e) {
                System.out.println("Kein Cookie-Banner gefunden.");
            }

            // ✅ Titel auslesen
            try {
                WebElement titleElement = driver.findElement(By.cssSelector("h1"));
                mangaData.put("Titel", titleElement.getText());
                System.out.println("Titel: " + titleElement.getText());
            } catch (Exception e) {
                System.out.println("Titel konnte nicht gefunden werden.");
            }

            // ✅ Alle Info-Blöcke durchgehen
            List<WebElement> infoBlocks = driver.findElements(By.cssSelector("div.manga_sidebarInfoBlock__rJiuy"));

            for (WebElement block : infoBlocks) {
                String blockTitle = block.findElement(By.tagName("h2")).getText().trim();
                List<WebElement> infoItems = block.findElements(By.cssSelector("div.manga_mangaInfoItem___pa9z"));

                for (WebElement item : infoItems) {
                    String label = item.findElement(By.cssSelector("span.manga_mangaInfoLabel__bhH_Z")).getText().trim();
                    String value;

                    // Manchmal sind Werte als <li> (Liste) enthalten, manchmal als normaler Text
                    try {
                        List<WebElement> listItems = item.findElements(By.cssSelector("span.manga_mangaInfoValue__WPSmh li"));
                        if (!listItems.isEmpty()) {
                            value = listItems.get(0).getText().trim(); // Erster Eintrag
                        } else {
                            value = item.findElement(By.cssSelector("span.manga_mangaInfoValue__WPSmh")).getText().trim();
                        }
                    } catch (Exception ex) {
                        value = "Unbekannt";
                    }

                    mangaData.put(blockTitle + " " + label, value);
                    System.out.println(blockTitle + " " + label + ": " + value);
                }
            }

            // Scrollen Sie die Seite langsam bis nach ganz unten
            JavascriptExecutor js = (JavascriptExecutor) driver;
            long lastHeight = (long) js.executeScript("return document.body.scrollHeight");

            for(int i = 500; lastHeight-i >= 500; i += 500) {
                js.executeScript("window.scrollBy(0, 500);");
                TimeUnit.MILLISECONDS.sleep(500); // Kurze Pause zwischen den Scroll-Schritten
            }

            // ✅ Danach Bände, Bilder, Preise extrahieren
            List<WebElement> bands = driver.findElements(By.className("manga-list_tileItemWrapper__qR2Dl"));
            int bandNummer = 1;

            for (WebElement band : bands) {
                try {
                    String href = band.findElement(By.tagName("a")).getAttribute("href");
                    String bildUrl = band.findElement(By.className("img_img__jkdIh")).getAttribute("src");
                    String preis = band.findElement(By.className("manga-list_top__S1J_8")).getText();

                    if (preis.contains("€")) {
                        preis = preis.substring(preis.indexOf("€") + 1).trim();
                    }

                    //mangaData.put("Band " + bandNummer + " href", href);
                    //mangaData.put("Band " + bandNummer + " Bild Url", bildUrl);
                    //mangaData.put("Band " + bandNummer + " Preis", preis);

                    System.out.println("Band " + bandNummer + ": " + href + ", Bild: " + bildUrl + ", Preis: " + preis);
                    bandNummer++;
                } catch (Exception e) {
                    System.out.println("Fehler beim Lesen eines Bandes");
                }
            }

            // Falls keine Bände gefunden wurden → Einzelband
            if (bands.isEmpty()) {
                mangaData.put("Deutsche Ausgabe Bände", "1");
                mangaData.put("Band 1 href", url);
                try {
                    WebElement imgElement = driver.findElement(By.cssSelector(".img_img__jkdIh"));
                    mangaData.put("Band 1 Bild Url", imgElement.getAttribute("src"));
                } catch (Exception e) {
                    System.out.println("Einzelband-Bild konnte nicht extrahiert werden.");
                }
            }

        } catch (Exception e) {
            System.out.println("FEHLER: Konnte die Webseite nicht korrekt laden!");
            e.printStackTrace();
        } finally {
            driver.quit();
        }

        System.out.println("Finale Manga-Daten: " + mangaData);
        return mangaData;
    }

    private static WebDriver setupWebDriver() {
        System.setProperty("webdriver.gecko.driver", "E:\\Programme\\Test\\geckodriver.exe");
        FirefoxOptions options = new FirefoxOptions();
        options.addArguments("--headless");
        return new FirefoxDriver(options);
    }

    public static void main(String[] args) {
        String mangaIndex = "137"; // Beispiel
        Map<String, String> mangaData = scrapeMangaData(mangaIndex);
        mangaData.forEach((key, value) -> System.out.println(key + ": " + value));
    }
}
