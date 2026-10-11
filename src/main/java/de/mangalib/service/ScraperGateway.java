package de.mangalib.service;

import java.util.Map;

/** Austauschbare Scraper-Grenze; die manuelle Abnahme verwendet ausschließlich synthetische Daten. */
public interface ScraperGateway {
    Map<String, String> scrapeSeries(String index);
    Map<String, String> scrapeVolume(String index);
}
