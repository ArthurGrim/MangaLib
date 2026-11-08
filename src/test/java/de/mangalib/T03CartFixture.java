package de.mangalib;

import de.mangalib.entity.EinkaufslisteItem;
import de.mangalib.entity.MangaReihe;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Nur Fixtureidentität: bekannte NULL/0-Werte nie durch einen neuen Index ersetzen. */
final class T03CartFixture {
    static EinkaufslisteItem identity(Long requested, MangaReihe found, Supplier<String> title, IntSupplier index) {
        var item = new EinkaufslisteItem();
        if (requested == null) {
            if (found != null) throw new IllegalArgumentException("Widersprüchliche Fixturezuordnung.");
            item.setTitel(title.get());
            item.setMangaIndex(Integer.valueOf(index.getAsInt()));
        } else {
            if (requested <= 0 || found == null || !Objects.equals(requested, found.getId()))
                throw new IllegalArgumentException("Fixture-Reihenreferenz fehlt oder ist widersprüchlich.");
            item.setTitel(found.getTitel());
            item.setMangaIndex(found.getMangaIndex()); // Kein ternärer int/Integer-Ausdruck.
        }
        return item;
    }
}
