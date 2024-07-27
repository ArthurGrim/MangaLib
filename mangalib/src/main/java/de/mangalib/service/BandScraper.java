package de.mangalib.service;

import org.springframework.stereotype.Component;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class BandScraper {

    private static final String BASE_URL = "https://www.manga-passion.de";

    public Map<String, String> scrapeBandData(String bandIndex) {
        Map<String, String> bandData = new HashMap<>();
        String url = BASE_URL + "/volumes/" + bandIndex;

        try {
            // Jsoup zum Extrahieren der meisten Daten
            Document document = Jsoup.connect(url).get();

            // Überprüfen, ob die Seite existiert
            if (!document.select("div.manga_sidebarInfoBlock__rJiuy:nth-child(2)").isEmpty()) {

                // Extraktion von Verlag, Status, Format, Bänden und Preis
                Elements infoBlocks = document.select("div.manga_sidebarInfoBlock__rJiuy");
                for (Element block : infoBlocks) {
                    Elements items = block.children();
                    for (Element item : items) {
                        if (item.children().size() >= 2) {
                            String key = item.child(0).text().trim();
                            String value = item.child(1).text().trim();
                            bandData.put(key, value);
                        }
                    }
                }
            }

            // Selenium zum Extrahieren des Bild-URLs
            WebDriver driver = setupWebDriver();
                        driver.get(url);

            WebElement tileItem = driver.findElement(By.cssSelector("div.manga_mangaCover__WdSrA span.lightbox_lightbox__y2RzP img.img_img__jkdIh"));
            String bildUrl = tileItem.getAttribute("src");
            bandData.put("BildUrl", bildUrl);

            driver.quit();

        } catch (IOException e) {
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
}
