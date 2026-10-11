package de.mangalib.service;

/** Erwartete Ablehnung durch einen Scraper; keine technischen Fehler oder Live-Fallbacks. */
public final class ScraperRequestRejectedException extends IllegalArgumentException {
    public ScraperRequestRejectedException() {
        super("Scraperanfrage nicht freigegeben.");
    }
}
