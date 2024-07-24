package de.mangalib.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MangaScraper {

    public static Map<String, String> scrapeMangaData(String mangaIndex) {
        String baseUrl = "https://www.manga-passion.de";
        String url = baseUrl + "/editions/" + mangaIndex;
        Map<String, String> mangaData = new HashMap<>();

        try {
            // Jsoup zum Extrahieren der meisten Daten
            Document document = Jsoup.connect(url).get();

            // Überprüfen, ob die Seite existiert
            if (!document.select("div.manga_sidebarInfoBlock__rJiuy:nth-child(2)").isEmpty()) {
                // Titel extrahieren
                String title = document.select(".manga_mainHeadingWrapper__sGPUj > h1:nth-child(1)").text();
                mangaData.put("Titel", title);

                // Extraktion von Verlag, Status, Format, Bänden und Preis
                Elements infoBlocks = document.select("div.manga_sidebarInfoBlock__rJiuy");
                for (int i = 0; i < infoBlocks.size(); i++) {
                    Element block = infoBlocks.get(i);
                    Elements items = block.children();
                    String blockType = (i == 0) ? "Deutsche Ausgabe" : "Erstveröffentlichung";

                    for (Element item : items) {
                        if (item.children().size() >= 2) {
                            String key = item.child(0).text().trim();
                            String value = item.child(1).text().trim();
                            String finalKey = blockType + " " + key; // Z.B. "Deutsche Ausgabe Verlag"
                            mangaData.put(finalKey, value);
                        }
                    }
                }

                try {
                    // Überprüfen, ob es sich um einen Einzelband handelt
                    if (!mangaData.containsKey("Deutsche Ausgabe Preis")) {
                        // Selenium für dynamische Inhalte
                        WebDriver driver = setupWebDriver();
                        driver.get(url);
                        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(4));

                        // Warten bis das Cookie-Pop-up sichtbar ist und dann auf "Alle ablehnen"
                        // klicken
                        try {
                            WebElement denyAllButton = wait
                                    .until(ExpectedConditions.elementToBeClickable(By.id("consent-deny-all")));
                            denyAllButton.click();
                        } catch (Exception e) {
                            System.out.println(
                                    "Kein Cookie-Pop-up gefunden oder es gab ein Problem beim Klicken darauf.");
                        }
                        wait.until(
                                ExpectedConditions
                                        .visibilityOfElementLocated(By.cssSelector("div.manga-list_top__S1J_8")));

                        // Versuchen, die manga-list_tileItemWrapper__qR2Dl Elemente zu finden
                        List<WebElement> tileItems = driver
                                .findElements(By.className("manga-list_tileItemWrapper__qR2Dl"));

                        // Überprüfen, ob tileItems leer ist
                        if (!tileItems.isEmpty()) {
                            // Wenn Elemente vorhanden sind, verarbeiten wir sie
                            int aktuellerBand = 0;
                            for (WebElement tileItem : tileItems) {
                                aktuellerBand++;

                                // Extrahieren des Href
                                String href = tileItem.findElement(By.tagName("a")).getAttribute("href");
                                mangaData.put("Band " + aktuellerBand + " href", href);

                                if (aktuellerBand <= 5) {
                                    // Extrahieren der Bild-URL
                                    String bildUrl = tileItem.findElement(By.className("img_img__jkdIh"))
                                            .getAttribute("src");
                                    mangaData.put("Band " + aktuellerBand + " Bild Url", bildUrl);
                                }
                                // Extrahieren des Preises des Bandes
                                String bandPreis = tileItem.findElement(By.className("manga-list_top__S1J_8"))
                                        .getText();
                                if (bandPreis.contains("€")) {
                                    bandPreis = bandPreis.substring(bandPreis.indexOf("€") + 1).trim();
                                }
                                mangaData.put("Band " + aktuellerBand + " Preis", bandPreis);
                            }
                        } else {
                            // Wenn keine manga-list_tileItemWrapper__qR2Dl Elemente vorhanden sind, handelt
                            // es sich um einen Einzelband
                            // Hier können Sie den Code für die Behandlung von Einzelbänden hinzufügen
                        }

                        driver.quit();
                    } else {
                        mangaData.put("Deutsche Ausgabe Bände", "1");
                        mangaData.put("Band 1 href", url);

                        WebDriver driver = setupWebDriver();
                        driver.get(url);
                        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(4));
                        wait.until(
                                ExpectedConditions
                                        .visibilityOfElementLocated(By.cssSelector("div.manga_mangaCover__WdSrA")));
                        // Bild-Element finden und src-Attribut extrahieren
                        WebElement imgElement = driver.findElement(By.cssSelector(".img_img__jkdIh"));
                        String bildUrl = imgElement.getAttribute("src");
                        mangaData.put("Band 1 Bild Url", bildUrl);
                        System.out.println("Band 1 Bild Url: " + bildUrl);
                        mangaData.put("Band 1 Preis",
                                (mangaData.get("Deutsche Ausgabe Preis").trim().replace("€", "")));
                        driver.quit();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (

        Exception e) {
            e.printStackTrace();
        }

        return mangaData;
    }

    private static WebDriver setupWebDriver() {
        System.setProperty("webdriver.gecko.driver", "E:\\Programme\\Test\\geckodriver.exe");
        FirefoxOptions options = new FirefoxOptions();
        options.addArguments("--headless");
        return new FirefoxDriver(options);
    }

    public static void main(String[] args) {
        String mangaIndex = "123"; // Beispiel Manga Index
        Map<String, String> mangaData = scrapeMangaData(mangaIndex);
        mangaData.forEach((key, value) -> System.out.println(key + ": " + value));
    }
}
