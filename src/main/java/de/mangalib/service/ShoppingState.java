package de.mangalib.service;

import de.mangalib.entity.EinkaufslisteItem;
import de.mangalib.entity.EinkaufslisteItemDetails;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

final class ShoppingState {
    private ShoppingState() {}

    static Map<String, Object> fields(EinkaufslisteItem item, EinkaufslisteItemDetails detail) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", item.getId().toString());
        values.put("gekauft", item.getGekauft());
        values.put("titel", item.getTitel());
        values.put("mangaIndex", item.getMangaIndex());
        values.put("verlagId", item.getVerlagId() == null ? null : item.getVerlagId().getVerlagId());
        values.put("typId", item.getTypId() == null ? null : item.getTypId().getTypId());
        values.put("formatId", item.getFormatId() == null ? null : item.getFormatId().getFormatId());
        values.put("anzahlBaende", item.getAnzahlBaende());
        values.put("preisProBand", money(item.getPreis()));
        values.put("gesamtpreisAenderung", money(item.getAenderungGesamtpreis()));
        values.put("gesamtpreis", money(item.getGesamtpreis()));
        values.put("erscheinungsdatum", item.getErscheinungsdatum());
        values.put("istEbayPreis", item.getIstEbayPreis());
        values.put("istVergriffen", item.getIstVergriffen());
        values.put("detailId", detail == null ? null : detail.getId());
        values.put("detailParentId", detail == null || detail.getEinkaufslisteItem() == null
                ? null : detail.getEinkaufslisteItem().getId());
        values.put("sammelbandTypId", detail == null || detail.getSammelbaendeId() == null
                ? null : detail.getSammelbaendeId().getId());
        values.put("anilistUrl", detail == null ? null : detail.getAnilistUrl());
        values.put("coverUrl", detail == null ? null : detail.getCoverUrl());
        values.put("statusDe", detail == null ? null : detail.getStatusDe());
        values.put("anzahlBaendeDe", detail == null ? null : detail.getAnzahlBaendeDe());
        values.put("statusErstv", detail == null ? null : detail.getStatusErstv());
        values.put("herkunft", detail == null ? null : detail.getHerkunft());
        values.put("startJahr", detail == null ? null : detail.getStartJahr());
        values.put("anzahlBaendeErstv", detail == null ? null : detail.getAnzahlBaendeErstv());
        return values;
    }

    static String token(EinkaufslisteItem item, EinkaufslisteItemDetails detail, boolean purchase) {
        var canonical = new StringBuilder(purchase ? "purchase:v1:" : "edit:v1:");
        fields(item, detail).forEach((key, value) -> {
            if (!purchase || !key.equals("erscheinungsdatum")) {
                String text = value == null ? "" : value.toString();
                canonical.append(key).append(':').append(value == null ? -1 : text.length())
                        .append(':').append(text);
            }
        });
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String money(BigDecimal amount) {
        return amount == null ? null : amount.stripTrailingZeros().toPlainString();
    }
}
