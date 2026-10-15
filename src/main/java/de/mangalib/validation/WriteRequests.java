package de.mangalib.validation;

import de.mangalib.entity.Band;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Kleine Requestmodelle mit expliziten JSON-Typen statt stiller Jackson-Koerzierung. */
public final class WriteRequests {
    private WriteRequests() { }

    public record Series(Long id, Integer index, Long status, Long publisher, Long type, Long format,
            String title, Integer volumes, BigDecimal price, Boolean read, Integer reread,
            Boolean outOfPrint, Boolean adjustedPrice, String anilist, String cover, Long omnibus,
            BigDecimal adjustment, Map<String, String> scraped, boolean edit) {
        public static Series from(Map<String, Object> data) {
            Map<String, String> scraped = WriteValidation.scrapedData(data.get("scrapedData"));
            String editValue = scraped.get("istEdit");
            WriteValidation.require(editValue == null || editValue.equals("true") || editValue.equals("false"));
            boolean edit = Boolean.parseBoolean(editValue);
            Long id = stringId(data, "mangaReiheId", edit);
            Integer index = integer(data, "mangaIndex", false);
            WriteValidation.index(index);
            int count = integer(data, "anzahlBaende", true);
            WriteValidation.volumes(count);
            Integer reread = integer(data, "reread", false);
            WriteValidation.reread(reread);
            String title = string(data, "titel", true);
            WriteValidation.title(title);
            Boolean read = bool(data, "istGelesen", edit);
            return new Series(id, index, stringId(data, "statusId", true), stringId(data, "verlagId", true),
                    stringId(data, "typId", true), stringId(data, "formatId", true), title, count,
                    absentZeroMoney(data, "preisProBand"), read, reread == null ? 0 : reread,
                    bool(data, "istVergriffen", false), bool(data, "istEbayPreis", false),
                    WriteValidation.optionalUrl(string(data, "anilistUrl", false)),
                    WriteValidation.optionalUrl(string(data, "coverUrl", false)),
                    stringId(data, "sammelbandTypId", false), absentZeroMoney(data, "gesamtpreisAenderung"),
                    scraped, edit);
        }
    }

    public record Shopping(Integer index, Long publisher, Long type, Long format, String title,
            int volumes, BigDecimal price, Boolean outOfPrint, Boolean adjustedPrice, String anilist,
            Long omnibus, BigDecimal adjustment, LocalDate date, Map<String, String> scraped) {
        public static Shopping from(Map<String, Object> data) {
            Integer index = integer(data, "mangaIndex", false);
            WriteValidation.index(index);
            int count = integer(data, "anzahlBaende", true);
            WriteValidation.volumes(count);
            String title = string(data, "titel", true);
            WriteValidation.title(title);
            String date = string(data, "erscheinungsdatum", false);
            return new Shopping(index, stringId(data, "verlagId", true), stringId(data, "typId", true),
                    stringId(data, "formatId", true), title, count, WriteValidation.money(string(data, "preisProBand", true)),
                    bool(data, "istVergriffen", false), bool(data, "istEbayPreis", false),
                    WriteValidation.optionalUrl(string(data, "anilistUrl", false)), stringId(data, "sammelbandTypId", false),
                    WriteValidation.money(string(data, "gesamtpreisAenderung", true)),
                    date == null ? null : LocalDate.parse(date), WriteValidation.scrapedData(data.get("scrapedData")));
        }
    }

    public static Band band(Map<String, Object> data) {
        Band band = new Band();
        band.setId(stringId(data, "id", true));
        band.setBandNr(positiveInt(string(data, "bandNr", true)));
        String index = string(data, "bandIndex", false);
        band.setBandIndex(index == null || index.isEmpty() ? null : positiveInt(index));
        band.setPreis(optionalMoney(data, "preis"));
        band.setAenderungPreis(optionalMoney(data, "aenderungPreis"));
        band.setBildUrl(WriteValidation.toUrl(string(data, "bildUrl", false)));
        band.setMpUrl(WriteValidation.toUrl(string(data, "mpUrl", false)));
        String reread = string(data, "reread", false);
        band.setReread(reread == null || reread.isEmpty() ? 0 : Integer.valueOf(reread));
        WriteValidation.reread(band.getReread());
        band.setIstGelesen(bool(data, "istGelesen", true));
        band.setIstSpecial(bool(data, "istSpecial", false));
        return band;
    }

    public static String string(Map<String, ?> data, String name, boolean required) {
        Object value = data.get(name);
        WriteValidation.require(value == null ? !required : value instanceof String);
        return (String) value;
    }

    private static Boolean bool(Map<String, ?> data, String name, boolean required) {
        Object value = data.get(name);
        WriteValidation.require(value == null ? !required : value instanceof Boolean);
        return (Boolean) value;
    }

    private static Integer integer(Map<String, ?> data, String name, boolean required) {
        Object value = data.get(name);
        WriteValidation.require(value == null ? !required : value instanceof Integer);
        return (Integer) value;
    }

    private static Long stringId(Map<String, ?> data, String name, boolean required) {
        String value = string(data, name, required);
        if (value == null) return null;
        return id(value, false);
    }

    /** Kauf-IDs wurden bisher sowohl als JSON-Strings als auch Ganzzahlen angenommen. */
    public static Long purchaseId(Map<String, ?> data, String name) {
        Object value = data.get(name);
        WriteValidation.require(value instanceof String || value instanceof Integer || value instanceof Long);
        return id(value.toString(), true);
    }

    private static Long id(String value, boolean allowNonpositive) {
        WriteValidation.require(value.matches("-?[0-9]{1,19}"));
        long id = Long.parseLong(value);
        WriteValidation.require(allowNonpositive || id > 0);
        return id;
    }

    public static int positiveInt(String value) {
        WriteValidation.require(value != null && value.matches("[0-9]{1,10}"));
        int index = Integer.parseInt(value);
        WriteValidation.index(index);
        return index;
    }

    private static BigDecimal absentZeroMoney(Map<String, ?> data, String name) {
        return data.containsKey(name) ? WriteValidation.money(string(data, name, true)) : BigDecimal.ZERO;
    }

    private static BigDecimal optionalMoney(Map<String, ?> data, String name) {
        String value = string(data, name, false);
        return value == null || value.isEmpty() ? null : WriteValidation.money(value);
    }
}
