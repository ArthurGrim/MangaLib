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
import java.util.Map;

public class BandScraper {

    private static final String BASE_URL = "https://www.manga-passion.de";

    public static Map<String, String> scrapeBandData(String bandIndex) {
        Map<String, String> bandData = new HashMap<>();
        String fullUrl = BASE_URL + "/volumes/" + bandIndex;

        System.out.println("Scraping Band-URL: " + fullUrl);

        try {
            WebDriver driver = setupWebDriver();
            driver.get(fullUrl);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // Warte auf das Cover Bild
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".img_img__jkdIh")));

            // Bild-URL extrahieren
            WebElement imgElement = driver.findElement(By.cssSelector(".img_img__jkdIh"));
            String bildUrl = imgElement.getAttribute("src");
            bandData.put("BildUrl", bildUrl);
            System.out.println("BildUrl: " + bildUrl);

            // HTML-Quelltext holen
            Document doc = Jsoup.parse(driver.getPageSource());

            // Infos extrahieren
            Elements infoBlocks = doc.select("div.manga_sidebarInfoBlock__rJiuy");
            for (Element block : infoBlocks) {
                Elements infoItems = block.select("div.manga_mangaInfoItem___pa9z");

                for (Element infoItem : infoItems) {
                    try {
                        String label = infoItem.selectFirst(".manga_mangaInfoLabel__bhH_Z").text().trim();
                        String value = infoItem.selectFirst(".manga_mangaInfoValue__WPSmh").text().trim();
                        bandData.put(label, value);
                    } catch (Exception e) {
                        System.out.println("Fehler beim Auslesen eines Info-Items.");
                    }
                }
            }

            driver.quit();
        } catch (Exception e) {
            System.out.println("Fehler beim Scrapen des Bandes: " + e.getMessage());
            e.printStackTrace();
        }

        return bandData;
    }

    private static WebDriver setupWebDriver() {
        System.setProperty("webdriver.gecko.driver", "E:\\Programme\\Test\\geckodriver.exe");
        FirefoxOptions options = new FirefoxOptions();
        options.addArguments("--headless");
        return new FirefoxDriver(options);
    }

    public static void main(String[] args) {
        String bandIndex = "15295"; // Beispiel ID
        Map<String, String> bandData = scrapeBandData(bandIndex);

        bandData.forEach((key, value) -> System.out.println(key + ": " + value));
    }
}
