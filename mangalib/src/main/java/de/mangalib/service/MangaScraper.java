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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MangaScraper {

    // Preis steht bei Manga-Passion typischerweise als "€ 7,00" (NBSP möglich)
    private static final Pattern PRICE_PATTERN = Pattern.compile("€\\s*(\\d{1,3}(?:[\\.,]\\d{2})?)");

    public static Map<String, String> scrapeMangaData(String mangaIndex) {
        String baseUrl = "https://www.manga-passion.de";
        String url = baseUrl + "/editions/" + mangaIndex;

        System.out.println("Scraping URL: " + url);

        Map<String, String> mangaData = new HashMap<>();
        WebDriver driver = setupWebDriver();

        try {
            driver.get(url);

            // Einzelband braucht manchmal länger -> 20s ist sinnvoll
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("h1")));

            // Cookie-Banner schließen (falls vorhanden)
            try {
                WebElement denyAllButton = wait
                        .until(ExpectedConditions.elementToBeClickable(By.id("consent-deny-all")));
                denyAllButton.click();
                System.out.println("Cookie-Banner geschlossen.");
            } catch (Exception e) {
                System.out.println("Kein Cookie-Banner gefunden.");
            }

            // ✅ Titel auslesen
            try {
                WebElement titleElement = driver.findElement(By.cssSelector("h1"));
                mangaData.put("Titel", safeText(titleElement));
                System.out.println("Titel: " + safeText(titleElement));
            } catch (Exception e) {
                System.out.println("Titel konnte nicht gefunden werden.");
            }

            // ✅ Info-Blöcke (Deutsche Ausgabe / Erstveröffentlichung / ggf. weitere) robust
            // auslesen
            // Problemfall Einzelband: "Externe Links" hat KEIN Value-Span -> darf nicht
            // crashen
            By infoBlockSel = By.cssSelector("div[class*='title-item-page_sidebarInfoBlock']");
            wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(infoBlockSel));

            List<WebElement> infoBlocks = driver.findElements(infoBlockSel);

            for (WebElement block : infoBlocks) {

                // h2 kann in manchen Fällen tricky sein -> textContent ist robuster
                String blockTitle;
                try {
                    WebElement h2 = block.findElement(By.cssSelector("h2"));
                    blockTitle = h2.getDomProperty("textContent");
                    blockTitle = (blockTitle == null ? "" : blockTitle.replace('\u00A0', ' ').trim());
                } catch (Exception e) {
                    continue;
                }

                if (blockTitle.isBlank())
                    continue;

                // ✅ "Externe Links" ist für Fachdaten irrelevant und hat oft keinen Value-Span
                if (blockTitle.startsWith("Externe Links")) {
                    continue;
                }

                List<WebElement> infoItems = block
                        .findElements(By.cssSelector("div[class*='title-item-page_mangaInfoItem']"));

                for (WebElement item : infoItems) {
                    try {
                        // Label
                        WebElement labelEl = item
                                .findElement(By.cssSelector("span[class*='title-item-page_mangaInfoLabel']"));
                        String label = safeText(labelEl);
                        if (label.isBlank())
                            continue;

                        // Value (optional!)
                        List<WebElement> valueCandidates = item
                                .findElements(By.cssSelector("span[class*='title-item-page_mangaInfoValue']"));
                        if (valueCandidates.isEmpty()) {
                            // bei Einzelband/Links/sonderfällen existiert kein Value-Span -> überspringen
                            continue;
                        }

                        WebElement valueEl = valueCandidates.get(0);

                        // textContent ist in headless/React stabiler als getText
                        String value = valueEl.getDomProperty("textContent");
                        if (value == null || value.isBlank())
                            value = safeText(valueEl);

                        value = value.replace('\u00A0', ' ').trim();
                        if (value.isBlank())
                            value = "Unbekannt";

                        String key = blockTitle + " " + label;
                        mangaData.put(key, value);

                        System.out.println(key + ": " + value);

                    } catch (Exception ignored) {
                        // einzelne Zeilen überspringen, Scrape nicht abbrechen
                    }
                }
            }

            // ✅ Danach Bände, Bilder, Preise extrahieren (nur wenn es eine Bänderliste
            // gibt)
            JavascriptExecutor js = (JavascriptExecutor) driver;

            By bandTileSel = By.cssSelector("div[class*='manga-list_tileItemWrapper']");
            List<WebElement> bands = driver.findElements(bandTileSel);

            int bandNummer = 1;

            for (WebElement band : bands) {
                try {
                    // Tile in Viewport bringen (Lazy Load trigger)
                    js.executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", band);
                    TimeUnit.MILLISECONDS.sleep(150);

                    // Link (Volume URL)
                    WebElement linkEl = band.findElement(By.cssSelector("a[href*='/volumes/']"));
                    String href = linkEl.getDomProperty("href");
                    if (href == null || href.isBlank()) {
                        href = linkEl.getDomAttribute("href");
                        if (href != null && href.startsWith("/"))
                            href = baseUrl + href;
                    }

                    // Bild-URL: currentSrc/src/srcset via JS
                    WebElement imgEl = band.findElement(By.cssSelector("img"));
                    String bildUrl = (String) js.executeScript(
                            "const i=arguments[0]; return (i.currentSrc || i.src || i.getAttribute('data-src') || i.getAttribute('srcset') || '');",
                            imgEl);
                    if (bildUrl != null && bildUrl.contains(",")) {
                        bildUrl = bildUrl.split(",")[0].trim().split("\\s+")[0];
                    }
                    if (bildUrl == null || bildUrl.isBlank())
                        bildUrl = "Unbekannt";

                    // ✅ Preis: aus div[class*='manga-list_top'] via textContent (funktioniert
                    // zuverlässig)
                    String preis = "Unbekannt";
                    try {
                        WebElement priceEl = band.findElement(By.cssSelector("div[class*='manga-list_top']"));

                        String priceText = priceEl.getDomProperty("textContent");
                        if (priceText == null || priceText.isBlank())
                            priceText = priceEl.getText();

                        priceText = (priceText == null ? "" : priceText.replace('\u00A0', ' ').trim());

                        Matcher pm = PRICE_PATTERN.matcher(priceText);
                        if (pm.find()) {
                            preis = pm.group(1).replace(".", ",");
                        }
                    } catch (NoSuchElementException ignored) {
                    }

                    // Optional speichern (falls du es nutzen willst)
                    mangaData.put("Band " + bandNummer + " href", href);
                    mangaData.put("Band " + bandNummer + " Bild Url", bildUrl);
                    mangaData.put("Band " + bandNummer + " Preis", preis);

                    System.out.println("Band " + bandNummer + ": " + href + ", Bild: " + bildUrl + ", Preis: " + preis);
                    bandNummer++;

                } catch (Exception e) {
                    System.out.println("Fehler beim Lesen eines Bandes (Band " + bandNummer + "): "
                            + e.getClass().getSimpleName() + " - " + e.getMessage());
                }
            }

            // ✅ Falls keine Bände gefunden wurden → Einzelband-Edition (Cover robust
            // extrahieren)
            if (bands.isEmpty()) {

                try {
                    // 1) Versuche gezielt das Cover-Image (Next/Image) zu finden
                    // (in der Regel hat das img eine CSS-Module Klasse wie "img_img__....")
                    List<WebElement> imgs = driver.findElements(By.cssSelector("img[class*='img_img']"));

                    // Fallback: falls Manga-Passion das mal anders nennt
                    if (imgs.isEmpty()) {
                        imgs = driver.findElements(By.cssSelector("img"));
                    }

                    String coverUrl = null;

                    for (WebElement img : imgs) {
                        // currentSrc/src/srcset/data-src sind bei Next/Image relevant
                        String candidate = img.getDomProperty("currentSrc");
                        if (candidate == null || candidate.isBlank()) {
                            candidate = (String) js.executeScript(
                                    "const i=arguments[0]; return (i.currentSrc || i.src || i.getAttribute('data-src') || i.getAttribute('srcset') || '');",
                                    img);
                        }

                        if (candidate == null)
                            continue;
                        candidate = candidate.trim();

                        // srcset -> erste URL nehmen
                        if (candidate.contains(",")) {
                            candidate = candidate.split(",")[0].trim().split("\\s+")[0];
                        }

                        // ✅ Placeholder aussortieren
                        if (candidate.isBlank())
                            continue;
                        if (candidate.startsWith("data:image"))
                            continue;

                        // ✅ Nur “echte” Manga-Passion Cover nehmen
                        // (meist _next/image oder media.manga-passion.de)
                        if (candidate.contains("_next/image") || candidate.contains("media.manga-passion.de")) {
                            coverUrl = candidate;
                            break;
                        }
                    }

                    if (coverUrl != null) {
                        mangaData.put("Cover", coverUrl);
                    }

                } catch (Exception ignored) {
                }
            }

        } catch (Exception e) {
            System.out.println("FEHLER: Konnte die Webseite nicht korrekt laden!");
            e.printStackTrace();
        } finally {
            try {
                driver.quit();
            } catch (Exception ignored) {
            }
        }

        System.out.println("Finale Manga-Daten: " + mangaData);
        return mangaData;
    }

    private static String safeText(WebElement el) {
        try {
            return el == null ? "" : el.getText().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static WebDriver setupWebDriver() {
        // ggf. Pfad anpassen
        System.setProperty("webdriver.gecko.driver", "E:\\Programme\\Test\\geckodriver.exe");

        FirefoxOptions options = new FirefoxOptions();
        options.addArguments("--headless");

        return new FirefoxDriver(options);
    }

    public static void main(String[] args) {
        String mangaIndex = "2502"; // Einzelband-Beispiel
        Map<String, String> mangaData = scrapeMangaData(mangaIndex);
        mangaData.forEach((key, value) -> System.out.println(key + ": " + value));
    }
}
