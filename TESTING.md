# Sichere Tests für MangaLib

## Voraussetzungen und Grenzen

- Java 21 und der mitgelieferte Gradle Wrapper.
- Bereits eingerichtetes MySQL auf `localhost:3306`, Datenbank `mangalib_test`.
- Ausschließlich Benutzer `mangalib_test_user`; Passwort nur über die Prozess-Umgebungsvariable `MANGALIB_TEST_DB_PASSWORD`.
- Bestehendes Schema und die fünf Stammdatentabellen bleiben erhalten. Der Sammelbandtyp mit ID 1 wird vom aktuellen Anwendungscode vorausgesetzt.
- Kein Produktivpasswort erforderlich. Keine produktiven Konfigurationsdateien importieren.
- Der Testbenutzer muss serverseitig ausschließlich auf `mangalib_test` berechtigt sein, ohne globale Rechte oder Rechte auf `mangalib`. Keine CREATE-, ALTER- oder DROP-Rechte für Tests vergeben. Diese Berechtigungen wurden durch die Codeänderung nicht geprüft oder verändert.

## Schutz vor dem ersten Datenbankzugriff

`src/test/resources/META-INF/spring.factories` registriert eine `ContextCustomizerFactory` für Spring-TestContext. Alle aktuellen datenbankabhängigen Tests verwenden zusätzlich `@ActiveProfiles("test")`.

Die Factory registriert vor dem Kontextstart eine ausschließlich für Tests vorhandene `GuardedTestDataSource`. Spring Boots DataSource-Autokonfiguration weicht dadurch dieser Bean. Ein BeanFactoryPostProcessor prüft die effektive Konfiguration vor der regulären Singleton-Erzeugung, also auch vor der Hibernate-Schemavalidierung. Ein fehlendes oder zusätzliches Profil führt zum Abbruch; neue Spring-Tests ohne Testprofil werden nicht stillschweigend gegen die Standarddatenbank gestartet.

Erlaubt sind exakt:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/mangalib_test?useSSL=false
spring.datasource.username=mangalib_test_user
spring.datasource.password=${MANGALIB_TEST_DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.generate-ddl=false
spring.sql.init.mode=never
spring.flyway.enabled=false
spring.liquibase.enabled=false
```

Abweichende URLs einschließlich anderer Hosts, zusätzlicher JDBC-Parameter oder anderer Datenbanknamen werden abgewiesen. Zusätzliche Pool-/JNDI-/DataSource-Einstellungen und direkte `spring.jpa.properties` sind bewusst nicht erlaubt. Das betrifft auch Overrides aus Umgebungsvariablen, JVM-Properties oder Testkonfigurationen. Alternative DataSource-Beans werden abgewiesen.

Unmittelbar vor jedem Verbindungsaufbau wird erneut geprüft. An JDBC gehen ausschließlich die feste Test-URL, der feste Testbenutzer und das direkt aus `MANGALIB_TEST_DB_PASSWORD` gelesene Passwort. Die Überladung mit anderen Zugangsdaten ist gesperrt. Die ungepoolte DataSource vermeidet weitere überschreibbare Pool-Verbindungsparameter und genügt für die kleine Testsuite.

Diese Schutzschicht ist keine Sandbox für beliebigen Java-/SQL-Code. Neue Tests dürfen keine eigenen DriverManager-/DataSource-/JNDI-Verbindungen, manuelle SpringApplication-Starts, Konfigurationsimporte mit Nebenwirkungen oder qualifizierten Zugriffe auf andere Schemas hinzufügen. Die eingeschränkten MySQL-Rechte bleiben die unabhängige letzte Schutzgrenze. Eine lokale DNS-/Port-Weiterleitung oder falsch vergebene Serverrechte kann die Konfigurationsprüfung nicht erkennen.

## Testklassen und Nebenwirkungen

| Test | Verhalten |
|---|---|
| `TestDatabaseGuardTest` | Reine Konfigurations- und Lifecycle-Prüfungen mit synthetischen Werten; keine JDBC-Verbindung, kein Spring-Boot-Start, kein Passwort nötig. |
| `MangalibApplicationTests` | Testprofil, Kontext-/Hibernate-Start mit Schemavalidierung; prüft den geschützten DataSource-Typ. |
| `DemoApplicationTests` | Testprofil, Kontext-/Hibernate-Start; prüft Repository-Verdrahtung. |
| `ServiceTest` | Liest bestehende Stammdaten, legt ausschließlich neue Reihe/Details/Bände in einer Testtransaktion an; expliziter Rollback. Flush/Clear erzwingt einen echten Reload. Kein Seeding, Cleanup oder Tabellenreset. |
| `MangaScraperTest`, `BandScraperTest` | Reguläre JUnit-Methoden mit Assertions, aber per `@Disabled` deaktiviert und zusätzlich durch Gradle-Tag `external-network` ausgeschlossen. Kein Spring-Kontext. |

Rollback setzt eine transaktionale Storage Engine (InnoDB) voraus. AUTO_INCREMENT-Zähler können trotz Rollback steigen; vorhandene Datensätze werden dadurch nicht verändert. Neue Tests dürfen keine bestehenden Stammdaten verändern, kein `@Commit`, `REQUIRES_NEW`, asynchrones Schreiben oder unkontrollierte HTTP-Schreibtests verwenden. Solche Erweiterungen benötigen ein gesondertes Isolationskonzept.

## Erster kontrollierter Lauf – erst nach Prüfung und Freigabe

In diesem Änderungsauftrag wurden keine Tests, Builds, Migrationen oder Datenbankverbindungen ausgeführt. Auch die Kompilierung der neuen Tests ist noch nicht bestätigt.

1. Diff prüfen; ein Administrator bestätigt Zielinstanz, ausschließlich eingeschränkte Testrechte und InnoDB. Keine Schema-/Seed-Skripte ausführen.
2. Zunächst nur die Schutzprüfungen ohne Datenbank ausführen:

   ```powershell
   .\gradlew.bat --no-daemon test --tests de.mangalib.testsupport.TestDatabaseGuardTest
   ```

3. Das Testpasswort in derselben PowerShell-Sitzung ohne Klartexteingabe in der Befehlshistorie setzen:

   ```powershell
   $testCredential = Get-Credential -UserName mangalib_test_user -Message 'Passwort der MangaLib-Testdatenbank'
   $env:MANGALIB_TEST_DB_PASSWORD = $testCredential.GetNetworkCredential().Password
   ```

4. Nur den Kontext-/Schemavalidierungstest starten:

   ```powershell
   .\gradlew.bat --no-daemon test --tests de.mangalib.MangalibApplicationTests
   ```

5. Erst nach dessen Prüfung den transaktionalen Service-Test und anschließend die automatische Suite ausführen:

   ```powershell
   .\gradlew.bat --no-daemon test --tests de.mangalib.ServiceTest
   .\gradlew.bat --no-daemon test
   ```

6. Testberichte unter `build/reports/tests/test/index.html` prüfen und die Variable anschließend entfernen:

   ```powershell
   Remove-Item Env:MANGALIB_TEST_DB_PASSWORD
   Remove-Variable testCredential
   ```

Gradle kann beim ersten Lauf Dependencies herunterladen. Das ist vom gesperrten automatischen Scraping zu unterscheiden. Die Scraper bleiben auch bei IDE-Ausführung deaktiviert; ihre manuelle Aktivierung erfordert eine separate Freigabe und eine bewusste Code-/Task-Anpassung.

`check` und `build` können ebenfalls Tests starten. `bootRun` startet hingegen die normale Anwendung und verwendet diese ausschließlich im Test-Classpath liegende Schutzschicht **nicht**. Es ist kein Befehl zur Testvalidierung.

Bei einem Schutzfehler nicht die Schutzregeln lockern: widersprüchliche `SPRING_*`-/JPA-/DataSource-Einstellungen entfernen beziehungsweise das freigegebene Testziel wiederherstellen. Passwörter und vollständige Environment-Dumps gehören weder in Logs noch in Git.
