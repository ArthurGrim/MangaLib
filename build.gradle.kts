import java.security.MessageDigest
import java.util.UUID
import java.nio.file.Files
import java.nio.file.Path
import java.net.URLClassLoader

plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.4"
}

group = "de.mangalib"
version = "0.0.1-SNAPSHOT"

// Gemeinsamer Sicherheitspatch für alle eingebetteten Tomcat-Module; Boot bleibt 4.1.1.
extra["tomcat.version"] = "11.0.27"

java {
	sourceCompatibility = JavaVersion.VERSION_21
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-security")
	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("com.mysql:mysql-connector-j")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.security:spring-security-test")
	// Nur Firefox und explizite Driverpfade; keine HTML-Sanitizer-/Driverdownload-Bibliotheken.
	// Versionen gemeinsam aus der Boot-BOM statt gemischtem altem Selenium-Aggregator.
	implementation("org.seleniumhq.selenium:selenium-firefox-driver")
	implementation("org.seleniumhq.selenium:selenium-support")
}

tasks.withType<Test> {
	useJUnitPlatform {
		excludeTags("external-network")
	}
}

// Bewusst nicht an test/check/build gekoppelt. Nur nach eigener DB-/Commitfreigabe.
val t03Integration by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += output + compileClasspath
}
configurations[t03Integration.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[t03Integration.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
val t03CommitTest by tasks.registering(Test::class) {
    description = "Opt-in T03-Commitabnahme; hinterlässt registrierte synthetische Zeilen, kein Cleanup."
    group = "verification"
    testClassesDirs = t03Integration.output.classesDirs
    include("**/T03CommitAcceptanceTest.class")
    // Source-Set-Classpath erst bei Auflösung lesen: productionOwner wird später ergänzt.
    classpath = files(providers.provider { t03Integration.runtimeClasspath })
    maxParallelForks = 1
    failFast = true
    outputs.upToDateWhen { false }
    doFirst {
        require(providers.gradleProperty("t03Approval").orNull == "COMMIT_NEW_SYNTHETIC_ROWS_ONLY") {
            "Separate Eigentümerfreigabe und -Pt03Approval=COMMIT_NEW_SYNTHETIC_ROWS_ONLY erforderlich."
        }
        val runId = UUID.fromString(providers.gradleProperty("t03RunId").get()).toString()
        val directory = layout.projectDirectory.dir(".gradle/t03-integration/$runId").asFile.toPath()
        Files.createDirectories(directory.parent)
        Files.createDirectory(directory) // Atomar: keine Wiederverwendung eines Laufs.
        systemProperty("t03.run.id", runId)
        systemProperty("t03.approved", "true")
        systemProperty("t03.evidence.directory", directory.toString())
    }
}

val t03ReadOnlyTest by tasks.registering(Test::class) {
    description = "Opt-in: ausschließlich lesende T03-Datenbankvorprüfung, keine Fixtures."
    group = "verification"
    testClassesDirs = t03Integration.output.classesDirs
    include("**/T03ReadOnlyPreflightTest.class")
    classpath = files(providers.provider { t03Integration.runtimeClasspath })
    maxParallelForks = 1
    failFast = true
    outputs.upToDateWhen { false }
    doFirst {
        require(providers.gradleProperty("t03ReadOnlyApproval").orNull == "READ_ONLY_MANGALIB_TEST") {
            "Separate Eigentümerfreigabe für lesenden DB-Zugriff erforderlich."
        }
        require(!System.getenv("MANGALIB_TEST_DB_PASSWORD").isNullOrBlank()) {
            "MANGALIB_TEST_DB_PASSWORD fehlt oder ist leer."
        }
        systemProperty("t03.readOnly.approved", "true")
    }
}

// Getrennt vom normalen JAR und von Spring TestContext: kein JUnit-Guard im Browserbetrieb.
val t03RightsDiagnosisTest by tasks.registering(Test::class) {
    description = "Opt-in: ausschließlich vier lesende Identitäts-/Rechteabfragen."
    testClassesDirs = t03Integration.output.classesDirs
    include("**/T03RightsDiagnosisTest.class")
    classpath = files(providers.provider { t03Integration.runtimeClasspath })
    maxParallelForks = 1
    failFast = true
    outputs.upToDateWhen { false }
    doFirst {
        require(providers.gradleProperty("t03RightsApproval").orNull == "DIAGNOSE_TEST_RIGHTS_ONCE") {
            "Separate Eigentümerfreigabe für lesende Rechte-Diagnose erforderlich."
        }
        require(!System.getenv("MANGALIB_TEST_DB_PASSWORD").isNullOrBlank()) { "Testpasswort fehlt." }
        systemProperty("t03.rightsDiagnosis.approved", "true")
    }
}

// Fortsetzung nach der ersten Commitstufe: historische Leerbaseline bleibt separat.
val t03ContinuationReadOnlyTest by tasks.registering(Test::class) {
    description = "Opt-in: lesende Identitäts-/Bestandsprüfung der zwölf akzeptierten T03-Zeilen."
    testClassesDirs = t03Integration.output.classesDirs
    include("**/T03ContinuationReadOnlyTest.class")
    classpath = files(providers.provider { t03Integration.runtimeClasspath })
    maxParallelForks = 1
    failFast = true
    outputs.upToDateWhen { false }
    doFirst {
        require(providers.gradleProperty("t03ContinuationApproval").orNull == "VERIFY_EXISTING_T03_FIXTURES") {
            "Separate Freigabe für die lesende Fortsetzungsprüfung erforderlich."
        }
        require(!System.getenv("MANGALIB_TEST_DB_PASSWORD").isNullOrBlank()) { "Testpasswort fehlt." }
        systemProperty("t03.continuation.approved", "true")
    }
}

val t03MatrixTest by tasks.registering(Test::class) {
    description = "Opt-in: E1–E6 mit eigenen synthetischen Commit-/Rollbackfixtures; kein Cleanup."
    testClassesDirs = t03Integration.output.classesDirs
    include("**/T03MatrixAcceptanceTest.class")
    classpath = files(providers.provider { t03Integration.runtimeClasspath })
    maxParallelForks = 1
    failFast = true
    outputs.upToDateWhen { false }
    doFirst {
        require(providers.gradleProperty("t03MatrixApproval").orNull == "E1_E6_NEW_SYNTHETIC_ROWS_ONLY") {
            "Separate Eigentümerfreigabe für die Matrix erforderlich."
        }
        require(!System.getenv("MANGALIB_TEST_DB_PASSWORD").isNullOrBlank()) { "Testpasswort fehlt." }
        val baseline = providers.gradleProperty("t03MatrixBaselineFingerprint").get()
        require(baseline.matches(Regex("[a-f0-9]{64}"))) { "Vorher separat bestätigter aktueller Fingerabdruck erforderlich." }
        val runId = UUID.fromString(providers.gradleProperty("t03MatrixRunId").get()).toString()
        val directory = layout.projectDirectory.dir(".gradle/t03-integration/$runId").asFile.toPath()
        Files.createDirectories(directory.parent)
        Files.createDirectory(directory)
        systemProperty("t03.matrix.approved", "true")
        systemProperty("t03.matrix.baseline", baseline)
        systemProperty("t03.run.id", runId)
        systemProperty("t03.evidence.directory", directory.toString())
    }
}

val t03SeventyReadOnlyTest by tasks.registering(Test::class) {
    description = "Opt-in: ausschließlich lesende Prüfung der 12+58 bestehenden T03-Zeilen."
    testClassesDirs = t03Integration.output.classesDirs
    include("**/T03SeventyReadOnlyTest.class")
    classpath = files(providers.provider { t03Integration.runtimeClasspath })
    maxParallelForks = 1
    failFast = true
    outputs.upToDateWhen { false }
    doFirst {
        require(providers.gradleProperty("t03SeventyApproval").orNull == "READ_ONLY_SEVENTY_ROWS") { "Separate Lesefreigabe erforderlich." }
        require(!System.getenv("MANGALIB_TEST_DB_PASSWORD").isNullOrBlank()) { "Testpasswort fehlt." }
        systemProperty("t03.seventy.approved", "true")
    }
}
val remainingMethods = setOf(
    "de.mangalib.T03RemainingAcceptanceTest.controlledRejectionsUseFullyRolledBackFixtures",
    "de.mangalib.T03RemainingAcceptanceTest.beforeCommitFailureRollsBackAndQuarantinesFurtherWrites"
)
val t03RemainingTest by tasks.registering(Test::class) {
    description = "Opt-in: nur sechs offene Rollbackvarianten und letzter beforeCommit-Fall."
    testClassesDirs = t03Integration.output.classesDirs
    include("**/T03RemainingAcceptanceTest.class")
    filter { remainingMethods.forEach { includeTestsMatching(it) } }
    classpath = files(providers.provider { t03Integration.runtimeClasspath })
    maxParallelForks = 1
    failFast = true
    outputs.upToDateWhen { false }
    doFirst {
        require(providers.gradleProperty("t03RemainingApproval").orNull == "SEVEN_OPEN_CASES_TWO_DURABLE_ROWS") { "Separate Restmatrixfreigabe erforderlich." }
        require(filter.includePatterns == remainingMethods && filter.excludePatterns.isEmpty()
            && includes == setOf("**/T03RemainingAcceptanceTest.class") && excludes.isEmpty()) { "Restmatrix-Allowlist verändert: STOP" }
        require(!System.getenv("MANGALIB_TEST_DB_PASSWORD").isNullOrBlank()) { "Testpasswort fehlt." }
        val baseline = providers.gradleProperty("t03RemainingBaselineFingerprint").get()
        require(baseline == "0ffdb7dafe18a88c00de8a4feb6d432f442d7f6f72526842aa114b8acbdbe991") { "Separat bestätigter aktueller 70-Zeilen-Hash erforderlich." }
        val runId = UUID.fromString(providers.gradleProperty("t03RemainingRunId").get()).toString()
        require(runId !in setOf("264bc431-7c43-4fc2-b2d4-3efd35d8bb1c", "7e2a4fd0-19c3-4e78-a5b4-62d1a8c90f3e",
            "6db42e71-8fc3-4a96-b2d5-0e719c43f8ab", "11a8a5f8-82a4-4655-98eb-c15e8edfa224")) { "Historische UUID gesperrt." }
        val directory = layout.projectDirectory.dir(".gradle/t03-integration/$runId").asFile.toPath()
        Files.createDirectories(directory.parent)
        Files.createDirectory(directory)
        systemProperty("t03.remaining.approved", "true")
        systemProperty("t03.matrix.approved", "true") // Zusätzliche geerbte Kontextbarriere bleibt aktiv.
        systemProperty("t03.matrix.baseline", baseline)
        systemProperty("t03.run.id", runId)
        systemProperty("t03.evidence.directory", directory.toString())
    }
}

val manualTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}
val manualCheck by sourceSets.creating {
    compileClasspath += manualTest.output + sourceSets.main.get().output + configurations.testCompileClasspath.get()
    runtimeClasspath += output + manualTest.output + sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
}

tasks.register<Test>("manualSafetyTest") {
    description = "Datenbankfreie Prüfungen des manuellen Startwegs; kein Boot-Start."
    testClassesDirs = manualCheck.output.classesDirs
    classpath = manualCheck.runtimeClasspath
}

val manualBootJar by tasks.registering(org.springframework.boot.gradle.tasks.bundling.BootJar::class) {
    description = "Isoliertes Browser-Abnahme-JAR ohne Live-Scraper, Selenium oder DevTools."
    archiveFileName.set("mangalib-manual-test.jar")
    targetJavaVersion.set(JavaVersion.VERSION_21)
    mainClass.set("de.mangalibmanual.ManualTestLauncher")
    classpath = files(
        manualTest.output,
        configurations.runtimeClasspath.get().filter {
            !it.name.startsWith("selenium-") && !it.name.startsWith("webdrivermanager-")
                && !it.name.startsWith("spring-boot-devtools-")
        }
    )
    from(sourceSets.main.get().output) {
        into("BOOT-INF/classes")
        exclude("application.properties", "de/mangalib/MangalibApplication.class")
        exclude("de/mangalib/service/MangaScraper*.class", "de/mangalib/service/BandScraper*.class")
        exclude("de/mangalib/service/LiveScraperGateway*.class")
        exclude("de/mangalib/service/ScraperBrowser*.class", "de/mangalib/service/ScraperEgressProxy*.class")
        exclude("de/mangalib/windowsadapter/**")
    }
}
tasks.register("assembleManualTest") {
    dependsOn(manualBootJar)
}
tasks.named<Test>("manualSafetyTest") {
    dependsOn(manualBootJar)
    systemProperty("manualArtifact", manualBootJar.get().archiveFile.get().asFile.absolutePath)
}

// B-ADAPTER-2: ausschließlich explizit aufrufbarer Experimentpfad, keine
// extendsFrom-Beziehung zu main/manualTest und kein zusätzliches Anwendungs-JAR.
val jobAdapter by sourceSets.creating {
    java.srcDir("src/test/java")
    java.include("de/mangalib/windowsadapter/KernelCalls.java",
        "de/mangalib/windowsadapter/OwnedJobAdapter.java",
        "de/mangalib/windowsadapter/Win64*.java")
}
val jobAdapterTest by sourceSets.creating {
    java.srcDir("src/test/java")
    java.include("de/mangalib/windowsadapter/Win64BindingsTest.java",
        "de/mangalib/windowsadapter/Win64ProcessBindingsTest.java",
        "de/mangalib/windowsadapter/Win64NativeBindingsTest.java",
        "de/mangalib/windowsadapter/Win64ConcurrencyTest.java",
        "de/mangalib/windowsadapter/FakeKernelCalls.java")
    compileClasspath += jobAdapter.output
    runtimeClasspath += jobAdapter.output
}
// Das JAR ist lokal vorhanden, Maven-Modulmetadaten fehlen aber offline.
// Nur im Experiment: eindeutige Cachedatei + Hashpin statt globalem Repositoryfall­back.
val localJna517 = files(providers.provider {
    val jar = fileTree(gradle.gradleUserHomeDir.resolve(
        "caches/modules-2/files-2.1/net.java.dev.jna/jna/5.17.0")) {
        include("*/jna-5.17.0.jar")
    }.singleFile
    val hash = MessageDigest.getInstance("SHA-256")
        .digest(jar.readBytes()).joinToString("") { "%02x".format(it) }
    require(hash == "b3a9408e7c51e08ef0e3bfcc08f443f6ec0f6191ba8cd7c18d53d2b22e5bdbc0") {
        "Lokales JNA-Core 5.17.0 fehlt oder stimmt nicht mit dem freigegebenen Stand überein."
    }
    jar
})
dependencies {
    add(jobAdapter.implementationConfigurationName, localJna517)
    add(jobAdapterTest.implementationConfigurationName, localJna517)
    add(jobAdapterTest.implementationConfigurationName, "org.junit.jupiter:junit-jupiter:6.0.3")
    add(jobAdapterTest.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher:6.0.3")
}
tasks.register("jobAdapterDependencyReport") {
    doLast {
        val jars = configurations[jobAdapter.runtimeClasspathConfigurationName].files
        require(jars == localJna517.files) { "Unerwartete Adapter-Runtimeabhängigkeit" }
        println("Isolierte Adapter-Runtime: " + jars.joinToString { it.name })
        println("JNA-Core 5.17.0, SHA-256 b3a9408e7c51e08ef0e3bfcc08f443f6ec0f6191ba8cd7c18d53d2b22e5bdbc0")
        require(configurations[jobAdapter.implementationConfigurationName].extendsFrom.isEmpty())
        println("Keine main/manualTest-Konfigurationsvererbung; kein jna-platform im Adapterpfad.")
    }
}
tasks.register<Test>("jobAdapterUnitTest") {
    description = "Isolierte Layout-/Fake-API-Tests; kein Kernel32-Job und kein Prozessstart."
    testClassesDirs = jobAdapterTest.output.classesDirs
    classpath = jobAdapterTest.runtimeClasspath
    systemProperty("jna.nosys", "true")
}
tasks.register<JavaExec>("jobAdapterEmptyJob") {
    description = "Einmalig freigegebene leere Jobgruppe; dauerhafte Verbrauchssperre."
    classpath = jobAdapter.runtimeClasspath
    mainClass.set("de.mangalib.windowsadapter.Win64EmptyJobProbe")
    args(layout.projectDirectory.dir(".gradle/t02-adapter2-empty-77808c3d").asFile.absolutePath)
    systemProperty("jna.nosys", "true")
}

// B-WATCHDOG-3B-2: kein extendsFrom zu main/manualTest/jobAdapter. Der Host
// enthält ausschließlich JDK-Code und diese explizit freigegebenen Codec-Typen.
val communicationHost by sourceSets.creating {
    java.srcDir("src/test/java")
    java.include("de/mangalib/watchdogmodel/BinaryIpcCodec.java",
        "de/mangalib/watchdogmodel/BufferBudget.java",
        "de/mangalib/watchdogmodel/WatchdogModel.java",
        "de/mangalib/watchdogmodel/FakeJobKernel.java",
        "de/mangalib/watchdogmodel/FakeRecoveryJournal.java",
        "de/mangalib/watchdogmodel/SyntheticProtocol.java",
        "de/mangalib/watchdogmodel/SyntheticHostMain.java")
}
val communicationCheck by sourceSets.creating {
    compileClasspath += communicationHost.output
    runtimeClasspath += communicationHost.output
}
dependencies {
    // Bereits lokal verwendete JUnit-Version; keine neue Bibliothek.
    add(communicationCheck.implementationConfigurationName, "org.junit.jupiter:junit-jupiter:6.0.3")
    add(communicationCheck.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher:6.0.3")
}
tasks.register<Test>("communicationHostUnitTest") {
    description = "Nur Speicher-/Fake-Tests, KEIN Prozessstart."
    testClassesDirs = communicationCheck.output.classesDirs
    classpath = communicationCheck.runtimeClasspath
    filter {
        includeTestsMatching("de.mangalib.watchdogmodel.SyntheticProtocolTest")
        includeTestsMatching("de.mangalib.watchdogmodel.OwnedHostAttemptTest")
        includeTestsMatching("de.mangalib.watchdogmodel.CommunicationLaunchPlanTest")
    }
}
tasks.register<Test>("communicationProbeOnce") {
    description = "Einmalfreigabe B-WATCHDOG-3B-2: genau ein eigener synthetischer Java-Host."
    testClassesDirs = communicationCheck.output.classesDirs
    classpath = communicationCheck.runtimeClasspath
    filter { includeTestsMatching("de.mangalib.watchdogmodel.CommunicationProbeOnceTest") }
    systemProperty("communicationWorkspace", layout.projectDirectory.asFile.absolutePath)
    systemProperty("communicationHostClasses", communicationHost.output.classesDirs.singleFile.absolutePath)
    outputs.upToDateWhen { false }
    maxParallelForks = 1
}

// B-WATCHDOG-4A: reine Speicher-/Lifecycleintegration. Kein Probe-Harness und
// keine echte Start-/JNA-Implementierung im Klassenpfad dieses Prüfsatzes.
val lifecycleCheck by sourceSets.creating {
    java.srcDir("src/test/java")
    java.include("de/mangalib/watchdogmodel/WatchdogWireBridge.java",
        "de/mangalib/watchdogmodel/MemoryIpcLink.java",
        "de/mangalib/watchdogmodel/WireIdentityRegistry.java",
        "de/mangalib/watchdogmodel/LifecycleCoordinator.java",
        "de/mangalib/watchdogmodel/LifecycleCoordinatorTest.java")
    compileClasspath += communicationHost.output
    runtimeClasspath += communicationHost.output
}
dependencies {
    add(lifecycleCheck.implementationConfigurationName, "org.junit.jupiter:junit-jupiter:6.0.3")
    add(lifecycleCheck.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher:6.0.3")
}
tasks.register<Test>("watchdogLifecycleTest") {
    description = "K/H/J und Wire-Kommunikation ausschließlich mit deterministischen Fakes."
    testClassesDirs = lifecycleCheck.output.classesDirs
    classpath = lifecycleCheck.runtimeClasspath
    filter { includeTestsMatching("de.mangalib.watchdogmodel.LifecycleCoordinatorTest") }
}

// B-WATCHDOG-4B-1: ausschließlich JDK-/Fakeport-Verdrahtung. Kein Win64-Port,
// JNA-Loader, JavaExec oder Probe-Harness in diesem Klassenpfad.
val hostStartCheck by sourceSets.creating {
    java.srcDir("src/test/java")
    java.include("de/mangalib/windowsadapter/KernelCalls.java",
        "de/mangalib/windowsadapter/OwnedJobAdapter.java",
        "de/mangalib/windowsadapter/FakeKernelCalls.java",
        "de/mangalib/windowsadapter/ControlHostStartAdapter.java",
        "de/mangalib/windowsadapter/ControlHostStartAdapterTest.java",
        "de/mangalib/watchdogmodel/HostProbeLifecycle.java")
    compileClasspath += communicationHost.output + lifecycleCheck.output
    runtimeClasspath += communicationHost.output + lifecycleCheck.output
}
dependencies {
    add(hostStartCheck.implementationConfigurationName, "org.junit.jupiter:junit-jupiter:6.0.3")
    add(hostStartCheck.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher:6.0.3")
}
tasks.register<Test>("controlHostStartUnitTest") {
    description = "H-in-K-Vorbereitung nur mit Fakes; kein Win32 und kein Hoststart."
    testClassesDirs = hostStartCheck.output.classesDirs
    classpath = hostStartCheck.runtimeClasspath
    filter { includeTestsMatching("de.mangalib.windowsadapter.ControlHostStartAdapterTest") }
}

// B-WATCHDOG-4B-PREP: neue API-/Speicherfakes, keine historischen JNA-Tests,
// keine Loader-/JavaExec-/OS-Probetask. JNA ausschließlich aus bestehendem Hashpin.
val hostPrepCheck by sourceSets.creating {
    compileClasspath += jobAdapter.output
    runtimeClasspath += jobAdapter.output
}
dependencies {
    add(hostPrepCheck.implementationConfigurationName, localJna517)
    add(hostPrepCheck.implementationConfigurationName, "org.junit.jupiter:junit-jupiter:6.0.3")
    add(hostPrepCheck.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher:6.0.3")
}
tasks.register<Test>("hostPreparationUnitTest") {
    description = "Nur neue Win32-API-Fakes und temporäre Intentdateien; keine OS-Versuche."
    testClassesDirs = hostPrepCheck.output.classesDirs
    classpath = hostPrepCheck.runtimeClasspath
    systemProperty("jna.nosys", "true")
    filter {
        includeTestsMatching("de.mangalib.windowsadapter.Win64StdioPreparationTest")
        includeTestsMatching("de.mangalib.windowsadapter.StartIntentStoreTest")
        includeTestsMatching("de.mangalib.windowsadapter.StartLeaseGuardTest")
    }
}

// B-WATCHDOG-4C: JDK-Kontrollkern und Fakeport; kein JNA-JAR im Laufzeitpfad,
// kein main/JavaExec und keine Anbindung an build/check/Produkt/Manual.
val custodianCheck by sourceSets.creating {
    compileClasspath += hostPrepCheck.output + jobAdapter.output + hostStartCheck.output + lifecycleCheck.output + communicationHost.output
    runtimeClasspath += hostPrepCheck.output + jobAdapter.output + hostStartCheck.output + lifecycleCheck.output + communicationHost.output
}
dependencies {
    add(custodianCheck.implementationConfigurationName, "org.junit.jupiter:junit-jupiter:6.0.3")
    add(custodianCheck.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher:6.0.3")
}
tasks.register<Test>("custodianUnitTest") {
    description = "Isolierter Custodian-Kern ausschließlich mit JDK-/Port-Fakes; kein OS-Prozess."
    testClassesDirs = custodianCheck.output.classesDirs
    classpath = custodianCheck.runtimeClasspath
    filter {
        includeTestsMatching("de.mangalib.windowsadapter.OuterJobCustodianTest")
        includeTestsMatching("de.mangalib.windowsadapter.EmptyCustodianBootstrapTest")
        includeTestsMatching("de.mangalib.windowsadapter.HandleTransferPortTest")
    }
}

// B-WATCHDOG-4D-3: kalte S/C-Einstiege; keine JavaExec-/build-/check-Verknüpfung.
val custodianExecutable by sourceSets.creating {
    // Laufzeitkern ohne JUnit-Testklassen; bestehende Quellen statt Testoutputs übernehmen.
    java.srcDir("src/custodianCheck/java")
    java.srcDir("src/hostPrepCheck/java")
    java.exclude("**/*Test.java")
    compileClasspath += jobAdapter.output + communicationHost.output
    runtimeClasspath += jobAdapter.output + communicationHost.output
}
val custodianExecutableCheck by sourceSets.creating {
    compileClasspath += custodianExecutable.output + custodianExecutable.compileClasspath + custodianCheck.output + custodianCheck.compileClasspath
    runtimeClasspath += custodianExecutable.output + custodianExecutable.runtimeClasspath + custodianCheck.output + custodianCheck.runtimeClasspath
}
dependencies {
    add(custodianExecutableCheck.implementationConfigurationName, "org.junit.jupiter:junit-jupiter:6.0.3")
    add(custodianExecutableCheck.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher:6.0.3")
}
tasks.register<Test>("custodianBootstrapUnitTest") {
    description = "S/C-Einstiege und Binärkanal ausschließlich prozessintern mit Fakes, ohne JNA."
    testClassesDirs = custodianExecutableCheck.output.classesDirs
    classpath = custodianExecutableCheck.runtimeClasspath
    filter { includeTestsMatching("de.mangalib.windowsadapter.ScBootstrapTest") }
}

// Native S/C-Brücke nur kompilierbar; kein JavaExec und keine automatische Ausführung.
val scNative by sourceSets.creating {
    compileClasspath += custodianExecutable.output + custodianExecutable.compileClasspath
    runtimeClasspath += custodianExecutable.output + custodianExecutable.runtimeClasspath
}
dependencies { add(scNative.implementationConfigurationName, localJna517) }
tasks.named<Test>("custodianBootstrapUnitTest") {
    filter { includeTestsMatching("de.mangalib.windowsadapter.ScStartProviderTest") }
    filter { includeTestsMatching("de.mangalib.windowsadapter.ScLoadPreparationTest") }
    filter { includeTestsMatching("de.mangalib.windowsadapter.ScAttemptPreparationTest") }
    filter { includeTestsMatching("de.mangalib.windowsadapter.ScOuterStopTest") }
}

// Vereinfachte Variante B: nur explizite Compile-/Fake-Prüfungen, kein OS-/JavaExec-Task.
val workerOwner by sourceSets.creating {
    java.srcDir("src/test/java")
    java.include("de/mangalib/windowsadapter/KernelCalls.java", "de/mangalib/windowsadapter/Worker*.java",
        "de/mangalib/windowsadapter/SyntheticWorkerMain.java")
}
val workerOwnerCheck by sourceSets.creating {
    java.srcDir("src/test/java")
    java.include("de/mangalib/windowsadapter/Worker*Test.java", "de/mangalib/windowsadapter/FakeKernelCalls.java")
    compileClasspath += workerOwner.output
    runtimeClasspath += workerOwner.output
}
val workerOwnerNative by sourceSets.creating {
    compileClasspath += workerOwner.output + jobAdapter.output + jobAdapter.compileClasspath
    runtimeClasspath += workerOwner.output + jobAdapter.output + jobAdapter.runtimeClasspath
}
dependencies {
    add(workerOwnerCheck.implementationConfigurationName, "org.junit.jupiter:junit-jupiter:6.0.3")
    add(workerOwnerCheck.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher:6.0.3")
    add(workerOwnerNative.implementationConfigurationName, localJna517)
}
tasks.register<Test>("workerOwnerUnitTest") {
    description = "Vereinfachter Job-Owner und echter Speicher-IPC-Pfad; keine JNA/OS-/DB-Aufrufe."
    testClassesDirs = workerOwnerCheck.output.classesDirs
    classpath = workerOwnerCheck.runtimeClasspath
}

// Reine Owner-Provenienz: weder JNA noch Worker-/Job-/Spring-Klassen im Prüfprozess.
val ownerProbe by sourceSets.creating
val ownerProbeCheck by sourceSets.creating {
    compileClasspath += ownerProbe.output
    runtimeClasspath += ownerProbe.output
}
dependencies {
    add(ownerProbeCheck.implementationConfigurationName, "org.junit.jupiter:junit-jupiter:6.0.3")
    add(ownerProbeCheck.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher:6.0.3")
}
tasks.register<Test>("ownerProbeUnitTest") {
    description = "Owner-Provenienz nur Dateiprotokoll-/Hash-/Clock-Fakes; kein OS-Start."
    testClassesDirs = ownerProbeCheck.output.classesDirs
    classpath = ownerProbeCheck.runtimeClasspath
}

// Reine Ziel-/Adressentscheidungen; kein Spring-Kontext und kein DNS/HTTP.
val scraperSecurityCheck by sourceSets.creating {
    java.srcDirs("src/main/java", "src/scraperSecurityCheck/java")
    java.include("de/mangalib/service/ScraperDestinationPolicy*.java")
}
dependencies {
    add(scraperSecurityCheck.implementationConfigurationName, "org.junit.jupiter:junit-jupiter:6.0.3")
    add(scraperSecurityCheck.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher:6.0.3")
}
tasks.register<Test>("scraperSecurityUnitTest") {
    testClassesDirs = scraperSecurityCheck.output.classesDirs
    classpath = scraperSecurityCheck.runtimeClasspath
}

// Produktpfad: exakt die wiederverwendeten Job-/IPC-Komponenten, keine Versuchsmains.
val productionOwner by sourceSets.creating {
    java.srcDirs("src/test/java", "src/jobAdapter/java", "src/workerOwner/java", "src/workerOwnerNative/java")
    java.include("de/mangalib/windowsadapter/KernelCalls.java", "de/mangalib/windowsadapter/Win64*.java",
        "de/mangalib/windowsadapter/WorkerJobOwner.java", "de/mangalib/windowsadapter/WorkerJournal.java",
        "de/mangalib/windowsadapter/WorkerWire.java", "de/mangalib/windowsadapter/WorkerTemporaryDirectory.java",
        "de/mangalib/windowsadapter/WorkerNativePipes.java", "de/mangalib/windowsadapter/SimpleWorkerNative.java",
        "de/mangalib/windowsadapter/ProductionScraperClient.java")
    java.exclude("**/*Test.java", "**/Win64EmptyJobProbe.java")
}
dependencies { add(productionOwner.implementationConfigurationName, localJna517) }
sourceSets.main {
    compileClasspath += productionOwner.output
    runtimeClasspath = runtimeClasspath.filter { !it.name.startsWith("jna-") } + productionOwner.output + productionOwner.runtimeClasspath
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    // Anwendung besitzt den Job-Owner, Selenium und Extraktoren ausschließlich der Worker.
    classpath = sourceSets.main.get().runtimeClasspath.filter {
        !it.name.startsWith("selenium-") && !it.name.startsWith("webdrivermanager-") && !it.name.startsWith("jna-platform-")
    }
    exclude("**/de/mangalib/service/MangaScraper*.class", "**/de/mangalib/service/BandScraper*.class",
        "**/de/mangalib/service/ScraperBrowser*.class", "**/de/mangalib/service/ScraperEgressProxy*.class")
}
sourceSets.test {
    compileClasspath += productionOwner.output
    runtimeClasspath = runtimeClasspath.filter { !it.name.startsWith("jna-") } + productionOwner.output + productionOwner.runtimeClasspath
}
t03Integration.compileClasspath += productionOwner.output
t03Integration.runtimeClasspath = sourceSets.test.get().runtimeClasspath + t03Integration.output

// Prüft die effektiven Task-Classpaths ohne Test-/Springstart oder Klasseninitialisierung.
// Absichtlich kein Bestandteil von test/check/build und keine Abhängigkeit zu DB-Tasks.
tasks.register("t03ClasspathCheck") {
    group = "verification"
    description = "Datenbankfreie Runtime-/Tasktrennungsprüfung der T03-Opt-in-Tests."
    dependsOn(tasks.named(t03Integration.compileJavaTaskName))
    doLast {
        val expected = t03Integration.runtimeClasspath.files
        val optIn = mapOf(
            t03ReadOnlyTest.get() to "**/T03ReadOnlyPreflightTest.class",
            t03CommitTest.get() to "**/T03CommitAcceptanceTest.class",
            t03RightsDiagnosisTest.get() to "**/T03RightsDiagnosisTest.class",
            t03ContinuationReadOnlyTest.get() to "**/T03ContinuationReadOnlyTest.class",
            t03MatrixTest.get() to "**/T03MatrixAcceptanceTest.class",
            t03SeventyReadOnlyTest.get() to "**/T03SeventyReadOnlyTest.class",
            t03RemainingTest.get() to "**/T03RemainingAcceptanceTest.class"
        )
        val required = listOf(
            "de.mangalib.service.LiveScraperGateway",
            "de.mangalib.windowsadapter.ProductionScraperClient",
            "de.mangalib.windowsadapter.WorkerJobOwner",
            "de.mangalib.windowsadapter.WorkerWire",
            "de.mangalib.windowsadapter.SimpleWorkerNative",
            "de.mangalib.windowsadapter.Win64NativeBindings",
            "com.sun.jna.Native", "com.sun.jna.Pointer",
            "de.mangalib.testsupport.GuardedTestDataSource"
        )
        check(t03RemainingTest.get().filter.includePatterns == remainingMethods)
        check(t03RemainingTest.get().filter.excludePatterns.isEmpty())
        optIn.forEach { (task, include) ->
            val actual = task.classpath.files
            val missing = expected - actual
            logger.lifecycle("${task.name}: runtime entries=${actual.size}, missing=${missing.map { it.name }}")
            check(actual == expected) { "${task.name}: abweichende Runtime-Classpath" }
            check(task.includes == setOf(include) && task.excludes.isEmpty()) { "Falsche Klassen-Allowlist" }
            check(task.testClassesDirs.files == t03Integration.output.classesDirs.files) { "Falsche Testklassen" }
            URLClassLoader(actual.map { it.toURI().toURL() }.toTypedArray(), ClassLoader.getPlatformClassLoader()).use { loader ->
                required.forEach { name ->
                    // false verhindert statische Initialisierung, insbesondere von JNA Native.
                    val type = Class.forName(name, false, loader)
                    type.declaredMethods
                    type.declaredConstructors
                    type.declaredFields
                }
                check(loader.getResource("META-INF/spring.factories") != null) { "Test-Guard-Ressource fehlt" }
                if (task == t03RemainingTest.get()) {
                    val remaining = Class.forName("de.mangalib.T03RemainingAcceptanceTest", false, loader)
                    check(remaining.superclass.name == "de.mangalib.T03MatrixAcceptanceTest")
                    val rejection = remaining.getDeclaredMethod("controlledRejectionsUseFullyRolledBackFixtures", String::class.java)
                    val values = rejection.annotations.single { it.annotationClass.java.name == "org.junit.jupiter.params.provider.ValueSource" }
                    val cases = values.annotationClass.java.getMethod("strings").invoke(values) as Array<*>
                    check(cases.toList() == listOf("stale", "details", "duplicate", "unknownPrice", "cartDetails", "edition"))
                    val last = remaining.superclass.getDeclaredMethod("beforeCommitFailureRollsBackAndQuarantinesFurtherWrites")
                    val order = last.annotations.single { it.annotationClass.java.name == "org.junit.jupiter.api.Order" }
                    check(order.annotationClass.java.getMethod("value").invoke(order) == 10)
                    logger.lifecycle("Restmatrix: feste Methodenfilter, sechs Parameter und geerbter letzter Order-10-Fall korrekt")
                }
            }
            // Negativkontrolle: Ohne Owner-Output muss genau der ursprüngliche
            // Gateway-Signaturfehler erkannt werden, ohne einen Bean zu erzeugen.
            val withoutOwner = actual - productionOwner.output.files
            URLClassLoader(withoutOwner.map { it.toURI().toURL() }.toTypedArray(), ClassLoader.getPlatformClassLoader()).use { loader ->
                val failure = runCatching {
                    Class.forName("de.mangalib.service.LiveScraperGateway", false, loader).declaredMethods
                }.exceptionOrNull()
                check(failure is NoClassDefFoundError && failure.message?.contains("ProductionScraperClient") == true) {
                    "Negativkontrolle erkennt fehlenden Owner nicht"
                }
            }
            logger.lifecycle("${task.name}: ${required.size} Klassen ohne Initialisierung auflösbar; Allowlist korrekt")
        }
        val regular = tasks.named<Test>("test").get()
        if (providers.gradleProperty("t03HistoryCheck").orNull == "true") {
            URLClassLoader(expected.map { it.toURI().toURL() }.toTypedArray(), ClassLoader.getPlatformClassLoader()).use { loader ->
                val type = Class.forName("de.mangalib.T03MatrixHistory", true, loader)
                val method = type.getDeclaredMethod("local", Path::class.java)
                method.isAccessible = true
                val root = layout.projectDirectory.dir(".gradle/t03-integration/6db42e71-8fc3-4a96-b2d5-0e719c43f8ab").asFile.toPath()
                val history = method.invoke(null, root)
                fun count(name: String): Int {
                    val accessor = history.javaClass.getDeclaredMethod(name)
                    accessor.isAccessible = true
                    val ids = accessor.invoke(history) as Map<*, *>
                    return ids.values.sumOf { (it as Set<*>).size }
                }
                check(count("committed") == 58 && count("rolledBack") == 29)
                logger.lifecycle("T03_HISTORY_CHECK_PASS: gepinnte lokale Evidenzen, 58 Commitzeilen / 29 Rollback-IDs; keine DB")
            }
        }
        check(regular.testClassesDirs.files == sourceSets.test.get().output.classesDirs.files)
        check(regular.classpath.files.intersect(t03Integration.output.files).isEmpty()) { "Opt-in-Output im Standardtest" }
        fun closure(task: Task, seen: MutableSet<Task> = mutableSetOf()): Set<Task> {
            if (seen.add(task)) {
                task.taskDependencies.getDependencies(task).forEach { closure(it, seen) }
                task.finalizedBy.getDependencies(task).forEach { closure(it, seen) }
            }
            return seen
        }
        listOf("test", "check", "build").forEach { name ->
            check(closure(tasks.named(name).get()).intersect(optIn.keys).isEmpty()) { "Opt-in-Task an $name gekoppelt" }
        }
        check(closure(this).intersect(optIn.keys).isEmpty()) { "Diagnose würde DB-Task ausführen" }
        check(sourceSets.main.get().output.files.intersect(t03Integration.output.files).isEmpty())
        val manualClasspath = requireNotNull(manualBootJar.get().classpath).files
        check(manualClasspath.intersect(productionOwner.output.files).isEmpty())
        check(manualClasspath.none { it.name.startsWith("jna-") || it.name.startsWith("selenium-") })
        logger.lifecycle("T03_CLASSPATH_CHECK_PASS: Runtime, Klassenauflösung, Task-/Source-Set-Trennung und Manual-Classpath")
    }
}
manualCheck.compileClasspath += productionOwner.output
manualCheck.runtimeClasspath += productionOwner.output

val liveScraperWorker by sourceSets.creating {
    java.srcDirs("src/main/java", "src/workerOwner/java")
    java.include("de/mangalib/service/MangaScraper.java", "de/mangalib/service/BandScraper.java",
        "de/mangalib/service/ScraperBrowser.java", "de/mangalib/service/ScraperDestinationPolicy.java",
        "de/mangalib/service/ScraperEgressProxy.java", "de/mangalib/windowsadapter/WorkerImportRunner.java",
        "de/mangalib/windowsadapter/WorkerWire.java", "de/mangalib/windowsadapter/LiveScraperWorkerMain.java")
}
dependencies {
    add(liveScraperWorker.implementationConfigurationName, "org.seleniumhq.selenium:selenium-firefox-driver")
    add(liveScraperWorker.implementationConfigurationName, "org.seleniumhq.selenium:selenium-support")
}
val liveScraperWorkerJar by tasks.registering(Jar::class) {
    description = "Isolierter Live-Worker ohne Spring, JPA, DB-Konfiguration oder Versuchsharness. Kein Start."
    archiveFileName.set("mangalib-scraper-worker.jar")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "de.mangalib.windowsadapter.LiveScraperWorkerMain" }
    from(liveScraperWorker.output)
    from({ liveScraperWorker.runtimeClasspath.filter { it.extension == "jar" }.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class", "META-INF/versions/**/module-info.class")
}
// Kein automatischer Live-Start und kein Umbau des Manual-JARs.

val productionScraperCheck by sourceSets.creating {
    compileClasspath += liveScraperWorker.output + liveScraperWorker.compileClasspath + productionOwner.output + sourceSets.main.get().output + configurations.testCompileClasspath.get()
    runtimeClasspath += liveScraperWorker.output + liveScraperWorker.runtimeClasspath + productionOwner.output + sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
}
dependencies {
    add(productionScraperCheck.implementationConfigurationName, "org.junit.jupiter:junit-jupiter:6.0.3")
    add(productionScraperCheck.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher:6.0.3")
}
tasks.register<Test>("productionScraperUnitTest") {
    description = "Nur Parser-/Konfigurations-/Dateifakes; kein Proxy-/Browser-/Jobstart."
    testClassesDirs = productionScraperCheck.output.classesDirs
    classpath = productionScraperCheck.runtimeClasspath
    dependsOn(liveScraperWorkerJar, tasks.named("bootJar"))
    systemProperty("scraperWorkerArtifact", liveScraperWorkerJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("scraperApplicationArtifact", tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar").get().archiveFile.get().asFile.absolutePath)
}

// Abnahmeartefakte werden nur gebaut, niemals automatisch ausgeführt.
val scraperAcceptance by sourceSets.creating {
    compileClasspath += productionOwner.output + productionOwner.compileClasspath
    runtimeClasspath += productionOwner.output + productionOwner.runtimeClasspath
}
val scraperBoundaryWorker by sourceSets.creating {
    compileClasspath += liveScraperWorker.output + liveScraperWorker.compileClasspath
    runtimeClasspath += liveScraperWorker.output + liveScraperWorker.runtimeClasspath
}
val scraperAcceptanceJar by tasks.registering(Jar::class) {
    archiveFileName.set("mangalib-scraper-acceptance.jar")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "de.mangalib.windowsadapter.ScraperAcceptanceMain" }
    from(scraperAcceptance.output, productionOwner.output)
    from({ productionOwner.runtimeClasspath.filter { it.extension == "jar" }.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class", "META-INF/versions/**/module-info.class")
}
val scraperBoundaryWorkerJar by tasks.registering(Jar::class) {
    dependsOn(liveScraperWorkerJar)
    archiveFileName.set("mangalib-scraper-boundary-worker.jar")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "de.mangalib.windowsadapter.BoundaryWorkerMain" }
    from(scraperBoundaryWorker.output)
    from({zipTree(liveScraperWorkerJar.get().archiveFile)}) { exclude("META-INF/MANIFEST.MF") }
}
val scraperAcceptanceCheck by sourceSets.creating {
    java.srcDir("src/test/java")
    java.include("de/mangalib/windowsadapter/FakeKernelCalls.java", "de/mangalib/windowsadapter/Acceptance*Test.java",
        "de/mangalib/service/BoundaryFixtureTest.java")
    compileClasspath += scraperAcceptance.output + scraperBoundaryWorker.output + productionOwner.output + liveScraperWorker.output
    runtimeClasspath += scraperAcceptance.output + scraperBoundaryWorker.output + productionOwner.output + liveScraperWorker.output
    compileClasspath += liveScraperWorker.compileClasspath + productionOwner.compileClasspath
    runtimeClasspath += liveScraperWorker.runtimeClasspath
}
dependencies {
    add(scraperAcceptanceCheck.implementationConfigurationName,"org.junit.jupiter:junit-jupiter:6.0.3")
    add(scraperAcceptanceCheck.runtimeOnlyConfigurationName,"org.junit.platform:junit-platform-launcher:6.0.3")
}
tasks.register<Test>("scraperAcceptanceUnitTest") {
    description = "Nur Manifest-, Datei-, Ressourcen- und Fixture-Fakes; keine Sockets, Browser oder Nativecalls."
    testClassesDirs = scraperAcceptanceCheck.output.classesDirs
    classpath = scraperAcceptanceCheck.runtimeClasspath
    dependsOn(scraperAcceptanceJar,scraperBoundaryWorkerJar)
    systemProperty("acceptanceJar",scraperAcceptanceJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("boundaryJar",scraperBoundaryWorkerJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("productionWorkerJar",liveScraperWorkerJar.get().archiveFile.get().asFile.absolutePath)
}
