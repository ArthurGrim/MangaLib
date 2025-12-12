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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BandScraper {

    private static final String BASE_URL = "https://www.manga-passion.de";

    // matcht z.B. "€ 7,00" oder "€7.00"
    private static final Pattern PRICE_PATTERN =
            Pattern.compile("€\\s*(\\d{1,3}(?:[\\.,]\\d{2})?)");

    public static Map<String, String> scrapeBandData(String bandIndex) {
        Map<String, String> bandData = new HashMap<>();
        String fullUrl = BASE_URL + "/volumes/" + bandIndex;

        System.out.println("Scraping Band-URL: " + fullUrl);

        WebDriver driver = setupWebDriver();
        try {
            driver.get(fullUrl);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Cookie-Banner schließen (falls vorhanden)
            try {
                WebElement denyAllButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("consent-deny-all")));
                denyAllButton.click();
                System.out.println("Cookie-Banner geschlossen.");
            } catch (Exception e) {
                System.out.println("Kein Cookie-Banner gefunden.");
            }

            // Warten bis Seite “inhaltlich” geladen ist
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("h1")));

            // Titel (Band-Detailseite hat normalerweise ein h1)
            try {
                WebElement h1 = driver.findElement(By.cssSelector("h1"));
                String title = safeText(h1);
                if (!title.isBlank()) {
                    bandData.put("Titel", title);
                    System.out.println("Titel: " + title);
                }
            } catch (Exception ignored) { }

            // ✅ Cover extrahieren: img[class*='img_img'] ist stabiler als kompletter Hash
            try {
                By coverSel = By.cssSelector("img[class*='img_img']");
                wait.until(ExpectedConditions.presenceOfElementLocated(coverSel));

                WebElement img = driver.findElement(coverSel);

                // currentSrc ist bei Next/Image am zuverlässigsten
                String bildUrl = img.getDomProperty("currentSrc");
                if (bildUrl == null || bildUrl.isBlank()) {
                    bildUrl = (String) js.executeScript(
                            "const i=arguments[0]; return (i.currentSrc || i.src || i.getAttribute('data-src') || i.getAttribute('srcset') || '');",
                            img
                    );
                }

                if (bildUrl != null && bildUrl.contains(",")) {
                    // srcset -> erste URL nehmen
                    bildUrl = bildUrl.split(",")[0].trim().split("\\s+")[0];
                }

                if (bildUrl != null && !bildUrl.isBlank()) {
                    bandData.put("BildUrl", bildUrl);
                    System.out.println("BildUrl: " + bildUrl);
                }
            } catch (Exception e) {
                System.out.println("Cover/Bild konnte nicht extrahiert werden: " + e.getMessage());
            }

            // ✅ Info-Blöcke robust auslesen (wie bei MangaScraper)
            // Viele Seiten nutzen dieses title-item-page_* Muster (CSS Modules).
            // Wir lesen daraus Label->Value Paare.
            extractSidebarInfoBlocks(driver, wait, bandData);

            // ✅ Preis-Fallback (falls nicht über Info-Blöcke gekommen)
            // Einige Seiten haben "Preis" außerhalb der Items oder anders strukturiert.
            if (!bandData.containsKey("Preis") || isBlank(bandData.get("Preis"))) {
                String fallbackPreis = tryExtractPriceFallback(driver, wait);
                if (!isBlank(fallbackPreis)) {
                    bandData.put("Preis", fallbackPreis);
                    System.out.println("Preis (Fallback): " + fallbackPreis);
                }
            }

        } catch (Exception e) {
            System.out.println("Fehler beim Scrapen des Bandes: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { driver.quit(); } catch (Exception ignored) {}
        }

        return bandData;
    }

    private static void extractSidebarInfoBlocks(WebDriver driver, WebDriverWait wait, Map<String, String> out) {
        try {
            // Nicht hart warten: manche Volume-Seiten haben evtl. andere Layouts.
            List<WebElement> blocks = driver.findElements(
                    By.cssSelector("div[class*='title-item-page_sidebarInfoBlock']")
            );

            // Falls der Selector mal nicht greift, versuchen wir zusätzlich eine alternative (falls Manga-Passion umstellt)
            if (blocks.isEmpty()) {
                blocks = driver.findElements(By.cssSelector("div[class*='sidebarInfoBlock']"));
            }

            for (WebElement block : blocks) {
                String blockTitle = "";
                try { blockTitle = safeText(block.findElement(By.cssSelector("h2"))); } catch (Exception ignored) {}
                if (blockTitle.isBlank()) blockTitle = "Infos";

                List<WebElement> items = block.findElements(By.cssSelector("div[class*='title-item-page_mangaInfoItem']"));
                if (items.isEmpty()) {
                    items = block.findElements(By.cssSelector("div[class*='mangaInfoItem']"));
                }

                for (WebElement item : items) {
                    try {
                        WebElement labelEl = findFirst(item,
                                By.cssSelector("span[class*='title-item-page_mangaInfoLabel']"),
                                By.cssSelector("span[class*='mangaInfoLabel']")
                        );
                        WebElement valueEl = findFirst(item,
                                By.cssSelector("span[class*='title-item-page_mangaInfoValue']"),
                                By.cssSelector("span[class*='mangaInfoValue']")
                        );

                        if (labelEl == null || valueEl == null) continue;

                        String label = safeText(labelEl);
                        if (label.isBlank()) continue;

                        // value kann Text oder Liste/Links enthalten -> getText() reicht hier i.d.R.
                        String value = safeText(valueEl);

                        // Manche Labels sind wie "Preis" in einem anderen Format -> textContent ist sicherer:
                        if (value.isBlank()) {
                            String tc = valueEl.getDomProperty("textContent");
                            if (tc != null) value = tc.replace('\u00A0', ' ').trim();
                        }

                        // Wenn es wirklich ein Preis ist, normalisieren wir ihn optional
                        if ("Preis".equalsIgnoreCase(label)) {
                            String normalized = normalizePrice(value);
                            if (!isBlank(normalized)) value = normalized;
                        }

                        out.put(label, value);

                        // Optional: auch BlockTitle+Label speichern, wenn du das im Projekt brauchst
                        // out.put(blockTitle + " " + label, value);

                    } catch (Exception ignored) {
                        // einzelne Zeilen überspringen
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Info-Blöcke konnten nicht extrahiert werden: " + e.getMessage());
        }
    }

    private static String tryExtractPriceFallback(WebDriver driver, WebDriverWait wait) {
        try {
            // 1) Versuch: Label "Preis" und direktes Nachbar-Element
            By sel = By.xpath(
                    "//*[self::span or self::div][normalize-space()='Preis']/following-sibling::*[1]"
            );
            WebElement el = wait.until(ExpectedConditions.presenceOfElementLocated(sel));

            String txt = el.getDomProperty("textContent");
            if (txt == null || txt.isBlank()) txt = el.getText();
            txt = (txt == null ? "" : txt.replace('\u00A0', ' ').trim());

            String normalized = normalizePrice(txt);
            return !isBlank(normalized) ? normalized : txt;
        } catch (Exception ignored) {
            // 2) Versuch: irgendwo auf der Seite steht "€ X,YY" -> nimm den ersten Treffer
            try {
                List<WebElement> euroEls = driver.findElements(By.xpath("//*[contains(., '€')]"));
                for (WebElement el : euroEls) {
                    String txt = el.getDomProperty("textContent");
                    if (txt == null || txt.isBlank()) txt = el.getText();
                    txt = (txt == null ? "" : txt.replace('\u00A0', ' ').trim());

                    String normalized = normalizePrice(txt);
                    if (!isBlank(normalized)) return normalized;
                }
            } catch (Exception ignored2) { }
        }
        return null;
    }

    private static String normalizePrice(String text) {
        if (text == null) return null;
        String t = text.replace('\u00A0', ' ').trim();
        Matcher m = PRICE_PATTERN.matcher(t);
        if (m.find()) {
            return m.group(1).replace(".", ",");
        }
        return null;
    }

    private static WebElement findFirst(WebElement root, By... selectors) {
        for (By sel : selectors) {
            try {
                return root.findElement(sel);
            } catch (NoSuchElementException ignored) { }
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safeText(WebElement el) {
        try {
            return el == null ? "" : el.getText().trim();
        } catch (Exception ignored) {
            return "";
        }
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
