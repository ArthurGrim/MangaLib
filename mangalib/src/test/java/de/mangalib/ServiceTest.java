package de.mangalib;

import org.springframework.boot.test.context.SpringBootTest;

import de.mangalib.service.VerlagService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

@SpringBootTest
public class ServiceTest {

    @Autowired
    private VerlagService verlagService;

    @Test
    public void testAddAndRetrieveVerlag() {
        // Erstellen eines neuen Verlags
        Verlag verlag = new Verlag();
        verlag.setName("IntegrationTestVerlag");
        Verlag gespeicherterVerlag = verlagService.addVerlag(verlag);

        // Überprüfen, ob der Verlag gespeichert wurde
        assertNotNull(gespeicherterVerlag);
        assertNotNull(gespeicherterVerlag.getVerlagID());
        assertEquals("IntegrationTestVerlag", gespeicherterVerlag.getName());

        // Abrufen aller Verlage und Überprüfen, ob der neue Verlag vorhanden ist
        List<Verlag> alleVerlage = verlagService.findAll();
        assertTrue(alleVerlage.stream().anyMatch(v -> "IntegrationTestVerlag".equals(v.getName())));

        // Aktualisieren des Verlagsnamens
        verlagService.updateVerlagBezeichnung(gespeicherterVerlag.getVerlagID(), "IntegrationTestVerlag2");

        // Abrufen des aktualisierten Verlags und Überprüfen der Änderungen
        Verlag aktualisierterVerlag = verlagService.getVerlagById(gespeicherterVerlag.getVerlagID()).orElse(null);
        assertNotNull(aktualisierterVerlag);
        assertEquals("IntegrationTestVerlag2", aktualisierterVerlag.getName());

        // Ausgeben aller Verlage mit ID und Name
        System.out.println("Alle Verlage:");
        for (Verlag v : alleVerlage) {
            System.out.println("ID: " + v.getVerlagID() + ", Name: " + v.getName());
        }
    }
}