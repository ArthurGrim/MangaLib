plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.4"
}

group = "de.mangalib"
version = "0.0.1-SNAPSHOT"

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
	implementation("org.jsoup:jsoup:1.17.2")
	implementation("org.seleniumhq.selenium:selenium-java:4.18.1")
	implementation("io.github.bonigarcia:webdrivermanager:5.7.0")
}

tasks.withType<Test> {
	useJUnitPlatform {
		excludeTags("external-network")
	}
}

// Getrennt vom normalen JAR und von Spring TestContext: kein JUnit-Guard im Browserbetrieb.
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
    }
}
tasks.register("assembleManualTest") {
    dependsOn(manualBootJar)
}
tasks.named<Test>("manualSafetyTest") {
    dependsOn(manualBootJar)
    systemProperty("manualArtifact", manualBootJar.get().archiveFile.get().asFile.absolutePath)
}
